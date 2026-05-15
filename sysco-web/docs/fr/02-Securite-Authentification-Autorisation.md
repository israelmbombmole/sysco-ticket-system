# Chapitre 2 — Sécurité : authentification et autorisation

Ce chapitre décrit **comment SYSCO Web protège l’accès** aux ressources : authentification (qui êtes-vous ?), autorisation (que pouvez-vous faire ?), et **correspondance exacte** entre chemins d’URL, entrées de menu et permissions telles qu’implémentées dans le code.

---

## 2.1 Modèle de menaces (niveau introduction)

Dans un déploiement type, les risques principaux sont :

- **Usurpation de compte** (mots de passe faibles, phishing, partage de session) ;
- **élévation de privilèges** (mauvaise attribution de rôles ou permissions) ;
- **fraude à la requête inter-sites (CSRF)** sur les actions modifiant l’état ;
- **fuite de données** via exports non contrôlés ou partages de fichiers mal configurés ;
- **indisponibilité** (déni de service, saturation).

SYSCO Web s’appuie sur **Spring Security** pour l’authentification de base, le **CSRF** sur les formulaires, et des **contrôles d’accès** au niveau HTTP et au niveau du menu. La **défense en profondeur** (pare-feu, TLS en frontal, durcissement OS/DB) reste de la responsabilité de l’**exploitation**.

---

## 2.2 Authentification

### 2.2.1 Mécanisme

L’application utilise une **authentification par formulaire** classique : l’utilisateur saisit identifiant et mot de passe sur une page de connexion dédiée. Spring Security valide les identifiants contre le **UserDetailsService** (ou équivalent) configuré dans le projet, et établit une **session HTTP** authentifiée.

### 2.2.2 Comportement après succès

Un **gestionnaire de succès** personnalisé (`SyscoAuthenticationSuccessHandler`) peut :

- rediriger vers une page de **changement de mot de passe obligatoire** si le compte exige une rotation des secrets ;
- rediriger vers le **tableau de bord** (`/app`) lorsque le mot de passe est à jour ;
- positionner un **indicateur de session** pour lancer la **visite guidée** au premier affichage du shell (selon l’état « tutoriel complété » en base).

### 2.2.3 Échecs et verrouillage

Un **gestionnaire d’échec** (`SyscoAuthenticationFailureHandler`) coopère avec un service de compte (`AuthAccountService`) pour :

- compter les tentatives infructueuses ;
- **verrouiller** temporairement le compte après un seuil (ex. cinq échecs — valeur à vérifier dans votre branche) ;
- afficher des messages d’erreur adaptés (identifiants invalides, compte verrouillé).

Ces règles réduisent le **brute force** naïf sur le formulaire de login.

### 2.2.4 Déconnexion

La déconnexion invalide la session côté serveur et, selon configuration, invalide le cookie de session. L’utilisateur doit utiliser le **bouton de déconnexion** du menu (soumission POST vers `/logout` avec jeton CSRF) plutôt que de fermer seulement l’onglet.

---

## 2.3 Autorisation : rôles et permissions

### 2.3.1 Deux familles d’autorités Spring

Spring Security manipule des **`GrantedAuthority`** :

1. **Rôles** — préfixe conventionnel `ROLE_`, par exemple `ROLE_INSPECTEUR`, `ROLE_ADMIN`.  
2. **Permissions métier** — souvent **sans** préfixe `ROLE_`, par exemple `TICKET_MANAGEMENT_READ`, `DATA_ENTRY_WRITE`.

La classe `WebSyscoPermissions` **filtre** les autorités : pour les permissions fines, on ne retient que les chaînes qui **ne commencent pas** par `ROLE_`. Le **rôle** est déduit de la première autorité commençant par `ROLE_`.

### 2.3.2 Lecture « READ / WRITE »

La méthode `canRead(Set<String> perms, String base)` considère qu’un utilisateur **peut lire** un module si l’ensemble contient :

- la clé exacte `base`, **ou**
- `base + "_READ"`, **ou**
- `base + "_WRITE"`.

Ainsi, un utilisateur avec seulement `TICKET_MANAGEMENT_WRITE` peut toujours être considéré comme ayant accès **lecture** au périmètre `TICKET_MANAGEMENT` pour l’affichage du menu.

---

## 2.4 Matrice menu : `NavigationRegistry` et `WebSyscoPermissions`

### 2.4.1 Ordre canonique des entrées

Le menu latéral est construit à partir de `NavigationRegistry.mainNav()` qui retourne une liste **ordonnée** de `NavItem(chemin, cléMessage)`. L’ordre affiché à l’écran est celui de cette liste, **sous réserve** du filtrage par permissions.

Exemple de chemins (non exhaustif des variations) :

| # | Chemin servlet | Clé i18n menu |
|---|----------------|---------------|
| 1 | `/app` | `nav.dashboard` |
| 2 | `/app/data-entry` | `nav.dataEntry` |
| 3 | `/app/courier` | `nav.courier` |
| 4 | `/app/courier-management` | `nav.courierManagement` |
| … | … | … |

### 2.4.2 Filtrage côté `NavigationAdvice`

Un `@ControllerAdvice` (`NavigationAdvice`) expose un attribut de modèle `navItems` : il s’agit de la liste filtrée des `NavItem` pour lesquels `WebSyscoPermissions.canAccessNavPath(authentication, path)` retourne **vrai**. Ainsi, **le menu visible** et la **décision d’accès** partagent la même logique, ce qui limite les incohérences « lien visible mais 403 » — sauf si une méthode de contrôleur applique des règles **plus strictes** que le menu.

### 2.4.3 Administrateurs

Si le rôle résolu est `ADMIN` ou `SUPER_ADMIN` (comparaison insensible à la casse), `canAccessNavPath` retourne **vrai** pour tous les chemins gérés par le `switch` interne : menu **complet** (sous réserve que l’utilisateur soit authentifié).

---

## 2.5 Règles détaillées par chemin (utilisateurs non administrateurs)

Les sous-sections suivantes résument le comportement **tel que codé** dans `WebSyscoPermissions` (à revalider lors des merges).

### 2.5.1 Tableau de bord `/app`

Accès si **l’une** des conditions suivantes est vraie :

- la permission `DASHBOARD` est présente ;
- `canRead` sur la clé tableau de bord dérivée du rôle (`DIRECTEUR_DASHBOARD`, `INSPECTEUR_DASHBOARD`, etc.) ;
- ou le rôle normalisé fait partie de la liste **implicite** (directeur, inspecteur, vérificateur, secrétaire, courrier, etc.) — voir `implicitDashboardNavByRole`.

**Justification métier :** beaucoup de déploiements n’enregistrent que des permissions fines (`MY_WORK_READ`, …) sans ligne explicite `*_DASHBOARD` ; sans règle implicite, le lien « Tableau de bord » disparaîtrait après la redirection post-login alors que l’utilisateur a le droit d’atterrir sur `/app`.

### 2.5.2 Saisie des données `/app/data-entry`

`canRead(perms, "DATA_ENTRY")`.

### 2.5.3 Portail courrier `/app/courier`

Visible si `canRead(perms, "PHYSICAL_COURIER")` **ou** si le rôle normalisé est dans un ensemble opérationnel (courrier, secrétaire, directeur, sous-directeur, inspecteur, contrôleur, vérificateur, vérificateur-assistant).

### 2.5.4 Gestion courrier `/app/courier-management`

Basé sur le **rôle** : `ADMIN`, `DIRECTEUR`, `SECRETAIRE`, `SOUS-DIRECTEUR`, `INSPECTEUR` (normalisés). Ne repose **pas** sur `PHYSICAL_COURIER_READ` dans le `switch` — attention lors des revues de sécurité.

### 2.5.5 Autres modules « permission »

- **Gestion des données** : `DATA_MANAGEMENT`  
- **Partage des données** : `DATASHARE` (libellé de base sans underscore)  
- **Mon activité** : `MY_ACTIVITY`  
- **Mon travail** : `MY_WORK` **ou** `MY_ACTIVITY`  
- **Suivi des tickets** : `TICKET_MONITORING`  
- **Gestion des tickets** : `TICKET_MANAGEMENT`  
- **Gestion des utilisateurs** : `USER_MANAGEMENT`  
- **Agenda / congés** : `LEAVE_MANAGEMENT` **ou** `USER_MANAGEMENT`  
- **Journal des connexions** : `LOGIN_AUDIT`  
- **Audit partage fichiers** : `FILE_SHARE_AUDIT`  
- **Créer un ticket** : `CREATE_TICKET`  
- **Planificateur** : `JOB_SCHEDULER`  
- **Missions** : `MISSIONS`  

### 2.5.6 Gestion du partage de fichiers `/app/file-share-management`

Réservé aux rôles `SUPER_ADMIN` et `ADMIN` dans la logique de navigation.

### 2.5.7 Ma garde `/app/my-shift`

Si `canRead(perms, "MY_SHIFT")` **ou** rôle parmi `ADMIN`, `DIRECTEUR`, `SOUS-DIRECTEUR`.

### 2.5.8 Chat et notifications

Les chemins `/app/chat` et `/app/notifications` sont autorisés par la fonction de garde pour tout utilisateur **authentifié** (dans l’implémentation actuelle du `switch`). Les liens dans le fragment d’en-tête peuvent néanmoins dépendre du gabarit.

---

## 2.6 Normalisation des rôles

La méthode `normalizeForScope` :

- supprime les **diacritiques** (NFD + suppression des marques combinantes) ;
- met en **majuscules** ;
- normalise les espaces insécables et traits d’union ;
- regroupe des variantes (`COURRIER` → `COURIER`, `VERIFICATEUR` + `ASSISTANT` → `VERIFICATEUR-ASSISTANT`, etc.).

Cette étape évite que de petites différences de saisie en base (`Sous-directeur` vs `SOUS-DIRECTEUR`) cassent les règles de menu.

---

## 2.7 Sécurité des méthodes et des vues

### 2.7.1 `@PreAuthorize` / `@EnableMethodSecurity`

Certaines opérations sensibles peuvent être protégées au niveau **service** ou **contrôleur** avec des annotations Spring Security. La présence d’un lien dans le menu **ne garantit pas** que **toutes** les actions POST à l’intérieur du module soient autorisées au même niveau : vérifier les annotations sur les méthodes.

### 2.7.2 Thymeleaf Security

Le module `thymeleaf-extras-springsecurity6` permet des fragments conditionnels `sec:authorize` dans les templates (affichage conditionnel de boutons). L’**absence** d’un bouton ne remplace **pas** une vérification serveur sur l’action POST correspondante.

---

## 2.8 CSRF

Les formulaires HTML générés par l’application incluent le **jeton CSRF** Spring. Les requêtes **JavaScript** (`fetch`) qui modifient l’état doivent reprendre le jeton depuis les balises `<meta name="_csrf">` et l’en-tête attendu (`_csrf_header`), comme le fait le script de fin de visite guidée (`postTutorialComplete`).

---

## 2.9 Recommandations d’exploitation

1. **TLS** obligatoire en frontal (reverse proxy ou load balancer).  
2. **Cookies** `Secure` et `HttpOnly` selon politique (Spring Security permet la configuration).  
3. **Sessions** : durée d’inactivité conforme à la politique de sécurité.  
4. **Comptes partagés** : interdits ; un compte = une personne physique.  
5. **Revue périodique** des comptes `ADMIN` / `SUPER_ADMIN`.

---

## 2.10 Synthèse pour l’audit

| Question d’audit | Où vérifier |
|------------------|-------------|
| Qui peut voir le menu « Gestion courrier » ? | `WebSyscoPermissions.isCourierManagementVisible` |
| Un vérificateur peut-il ouvrir `/app/file-share-management` ? | Non via menu ; vérifier aussi `@PreAuthorize` sur le contrôleur |
| Comment prouver la cohérence menu / rôle ? | Tests d’intégration + matrice manuelle dérivée du chapitre 2.5 |

---

*Fin du chapitre 2.*
