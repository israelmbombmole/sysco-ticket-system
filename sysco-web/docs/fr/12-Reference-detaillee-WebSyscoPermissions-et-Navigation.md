# Chapitre 12 — Référence détaillée : `WebSyscoPermissions` et visibilité du menu

Ce chapitre constitue une **référence d’implémentation** pour les intégrateurs, les auditeurs de sécurité et les développeurs qui doivent aligner les **données d’autorisation** (rôles et permissions en base ou dans l’annuaire) avec le **comportement réel** de l’interface web. Il complète le chapitre 2 en décrivant **chaque branche** de la logique de filtrage, les **exceptions implicites** (tableau de bord, courrier physique, gestion courrier) et la **normalisation des libellés de rôle** telle qu’elle est codée dans `com.sysco.web.security.WebSyscoPermissions`.

---

## 12.1 Rôle de la classe dans l’architecture

`WebSyscoPermissions` est une classe **finale** utilitaire : elle ne gère ni la persistance des utilisateurs ni la construction de la session Spring Security. Son rôle est **strictement** de répondre à la question : *« Pour un `Authentication` donné et un chemin de servlet (`servletPath`), l’entrée de navigation correspondante doit-elle être affichée ? »*

Cette décision est **symétrique** avec les règles historiques du client JavaFX SYSCO : le commentaire de code indique explicitement le parallèle avec `MainController#applyPermissions()` et les concepts `ModuleAccess` / `DashboardPermissions`. Toute évolution du menu web doit donc être **réconciliée** avec le bureau si l’on souhaite conserver une expérience homogène pour les mêmes profils métier.

La méthode publique d’entrée est `canAccessNavPath(Authentication auth, String servletPath)`. Elle retourne `false` si l’authentification est nulle ou si l’utilisateur n’est pas marqué comme authentifié. Dans le cas contraire, elle extrait :

1. l’ensemble des **chaînes de permission** via `permissionStrings(auth)` — toutes les autorités **sans** préfixe `ROLE_` ;
2. le **rôle** « principal » via `resolveRole(auth)` — la première autorité commençant par `ROLE_`, dont le préfixe est retiré.

---

## 12.2 Court-circuit administrateur

Si le rôle résolu est `ADMIN` ou `SUPER_ADMIN` (comparaison **insensible à la casse**), `canAccessNavPath` retourne **immédiatement** `true` pour **tout** `servletPath`. Autrement dit, ces rôles **contournent** la matrice fine des permissions pour l’affichage du menu latéral.

**Implications opérationnelles :**

- Un compte `ADMIN` mal protégé expose **toutes** les entrées de navigation, même si les tables de permissions ne contiennent aucune ligne pour cet utilisateur.
- Les audits doivent vérifier **qui** détient `ROLE_ADMIN` / `ROLE_SUPER_ADMIN` et **comment** ces comptes sont provisionnés (procédure d’urgence, comptes nominatifs vs génériques).

---

## 12.3 Table de correspondance `servletPath` → règle

Le cœur de la méthode est un `switch` sur `servletPath`. Les chemins ci-dessous sont **exactement** ceux attendus par le code (pas de slash final sauf convention du routeur). Toute nouvelle page protégée doit soit **réutiliser** un de ces chemins, soit **étendre** ce `switch` et mettre à jour la documentation.

### 12.3.1 `/app` — Tableau de bord (accueil)

La règle délègue à `hasDashboardAccess(perms, role)`. L’accès est accordé si :

1. l’ensemble `perms` contient la clé littérale **`DASHBOARD`** ; **ou**
2. `canRead(perms, dashboardKeyForRole(role))` est vrai, où `dashboardKeyForRole` dérive une clé du type `XXX_DASHBOARD` à partir du rôle normalisé ; **ou**
3. `implicitDashboardNavByRole(role)` retourne vrai.

Le troisième point est une **tolérance de déploiement** : de nombreux environnements ne stockent que des autorités fines (`MY_WORK_READ`, etc.) et **omettent** les lignes explicites `INSPECTEUR_DASHBOARD_READ`. Sans cette règle implicite, un utilisateur authentifié pourrait atterrir sur `/app` après le login mais **ne plus voir** le lien « Tableau de bord » dans la barre latérale après la première redirection — comportement réputé régressif par rapport au client de bureau.

**Rôles bénéficiant du tableau de bord implicite** (après normalisation pour le scope, voir section 12.6) : `ADMIN`, `SUPER_ADMIN`, `DIRECTEUR`, `SOUS-DIRECTEUR`, `INSPECTEUR`, `CONTROLEUR`, `VERIFICATEUR`, `VERIFICATEUR-ASSISTANT`, `SECRETAIRE`, `COURIER`, `COURRIER`, `USER`, `AGENT`.

### 12.3.2 `/app/data-entry`

Visible si `canRead(perms, "DATA_ENTRY")`.

### 12.3.3 `/app/courier`

Visible selon `isCourierModuleVisible(role, perms)` :

- si `canRead(perms, "PHYSICAL_COURIER")` ; **ou**
- si le rôle normalisé est parmi : `COURIER`, `SECRETAIRE`, `DIRECTEUR`, `SOUS-DIRECTEUR`, `INSPECTEUR`, `CONTROLEUR`, `VERIFICATEUR`, `VERIFICATEUR-ASSISTANT`.

Remarque : la permission explicite s’appelle **`PHYSICAL_COURIER`** dans le code, tandis que le segment d’URL est `courier`. Cette dissymétrie est un point de vigilance lors du mapping LDAP / base de données.

### 12.3.4 `/app/courier-management`

Visible uniquement si `isCourierManagementVisible(role)` : rôles `ADMIN`, `DIRECTEUR`, `SECRETAIRE`, `SOUS-DIRECTEUR`, `INSPECTEUR` après normalisation. **Aucune** vérification `canRead` sur une base de permission textuelle dans cette branche : c’est une **liste blanche par rôle**.

### 12.3.5 `/app/data-management`

`canRead(perms, "DATA_MANAGEMENT")`.

### 12.3.6 `/app/data-share`

`canRead(perms, "DATASHARE")` — attention à l’absence de soulignement entre `DATA` et `SHARE` dans la clé `base`.

### 12.3.7 `/app/my-activity`

`canRead(perms, "MY_ACTIVITY")`.

### 12.3.8 `/app/my-work`

`canRead(perms, "MY_WORK")` **ou** `canRead(perms, "MY_ACTIVITY")`. La seconde condition permet d’afficher le module « Mon travail » aux profils qui n’ont que l’activité sans une permission `MY_WORK` distincte.

### 12.3.9 `/app/ticket-monitoring`

`canRead(perms, "TICKET_MONITORING")`.

### 12.3.10 `/app/ticket-management`

`canRead(perms, "TICKET_MANAGEMENT")`.

### 12.3.11 `/app/file-share-management`

Réservé aux rôles `SUPER_ADMIN` ou `ADMIN` (comparaison insensible à la casse). Même logique « liste blanche » que pour une partie de la gestion courrier.

### 12.3.12 `/app/user-management`

`canRead(perms, "USER_MANAGEMENT")`.

### 12.3.13 `/app/agenda` et `/app/leave-management`

Les **deux** chemins partagent la même règle : `canRead(perms, "LEAVE_MANAGEMENT")` **ou** `canRead(perms, "USER_MANAGEMENT")`. Un gestionnaire d’utilisateurs peut donc voir les écrans de congés même sans permission `LEAVE_MANAGEMENT` explicite.

### 12.3.14 `/app/login-audit`

`canRead(perms, "LOGIN_AUDIT")`.

### 12.3.15 `/app/file-share-audit`

`canRead(perms, "FILE_SHARE_AUDIT")`.

### 12.3.16 `/app/create-ticket`

`canRead(perms, "CREATE_TICKET")`.

### 12.3.17 `/app/job-scheduler`

`canRead(perms, "JOB_SCHEDULER")`.

### 12.3.18 `/app/missions`

`canRead(perms, "MISSIONS")`.

### 12.3.19 `/app/my-shift`

`isMyShiftModuleVisible(role, perms)` :

- `canRead(perms, "MY_SHIFT")` ; **ou**
- rôle parmi `ADMIN`, `DIRECTEUR`, `SOUS-DIRECTEUR` après normalisation.

### 12.3.20 `/app/chat` et `/app/notifications`

Retournent **toujours** `true` pour un utilisateur authentifié (hors court-circuit déjà traité admin). Ces modules sont donc **visibles pour tous les profils** au niveau du menu, sous réserve que d’autres garde-fous (contrôleurs, WebSocket) n’ajoutent pas de restrictions complémentaires.

### 12.3.21 Chemins non reconnus

Tout autre `servletPath` tombe dans `default -> false` : le lien **ne doit pas** apparaître via le mécanisme standard de filtrage des `NavItem`.

---

## 12.4 Dérivation des clés `*_DASHBOARD`

La méthode `normalizeRoleForDashboard` prépare le rôle avant le `switch` de `dashboardKeyForRole` :

- rôle vide → traité comme `VERIFICATEUR` ;
- `ADMIN` → mappé sur `DIRECTEUR` pour la clé de tableau de bord ;
- `USER` → `VERIFICATEUR` ;
- `AGENT` → `CONTROLEUR` ;
- sinon, le rôle est passé en **majuscules** (`Locale.ROOT`).

Ensuite, `dashboardKeyForRole` retourne :

| Rôle normalisé | Clé attendue pour `canRead` |
|----------------|----------------------------|
| `DIRECTEUR` | `DIRECTEUR_DASHBOARD` |
| `SOUS-DIRECTEUR` ou `SOUS_DIRECTEUR` | `SOUS_DIRECTEUR_DASHBOARD` |
| `INSPECTEUR` | `INSPECTEUR_DASHBOARD` |
| `CONTROLEUR` | `CONTROLEUR_DASHBOARD` |
| `VERIFICATEUR` | `VERIFICATEUR_DASHBOARD` |
| `VERIFICATEUR-ASSISTANT` ou `VERIFICATEUR_ASSISTANT` | `VERIFICATEUR_ASSISTANT_DASHBOARD` |
| `COURIER` ou `COURRIER` | `COURIER_DASHBOARD` |
| `SECRETAIRE` | `SECRETAIRE_DASHBOARD` |
| tout autre cas | `VERIFICATEUR_DASHBOARD` (repli par défaut) |

Les équipes de données doivent **aligner** les valeurs de rôle stockées côté annuaire avec ces formes, ou s’appuyer sur l’**accès implicite** décrit en 12.3.1 lorsque les permissions fines ne sont pas présentes.

---

## 12.5 Sémantique de `canRead`

Pour une base `base` non nulle et un ensemble `perms` non nul :

- si `perms.contains(base)` → accès ;
- sinon si `perms.contains(base + "_READ")` → accès ;
- sinon si `perms.contains(base + "_WRITE")` → accès ;
- sinon → pas d’accès.

Les valeurs `null` pour `perms` ou `base` entraînent un refus. Cette convention permet de **simplifier** le modèle : une permission d’écriture implique la visibilité lecture pour le menu, ce qui correspond à l’usage métier « si je peux saisir, je dois voir l’écran ».

---

## 12.6 Normalisation `normalizeForScope`

La méthode privée `normalizeForScope` aligne les variantes orthographiques et typographiques des rôles « vue » sur des clés stables pour les `switch` métier. Étapes principales :

1. Rejet des entrées nulles ou blanches.
2. Normalisation Unicode **NFD** puis suppression des **marques diacritiques** (`\p{M}`), ce qui rapproche `ÉDITEUR` et `EDITEUR` si besoin.
3. Conversion en majuscules, remplacement des espaces insécables, suppression des traits d’union « soft ».
4. Remplacement de certaines apostrophes courantes par `'` ASCII.
5. Règles **composites** :
   - si la chaîne contient à la fois `VERIFICATEUR` et `ASSISTANT` → `VERIFICATEUR-ASSISTANT` ;
   - si elle contient `SOUS` et `DIRECTEUR` → `SOUS-DIRECTEUR` ;
   - `COURRIER` → `COURIER` ;
   - `DIRECTEUR`, `DIRECTRICE` ou préfixe `DIRECTRICE ` → `DIRECTEUR` ;
   - `SECRETAIRE` ou préfixe `SECRETAIRE ` → `SECRETAIRE`.

Cette logique doit être **maintenue en cohérence** avec `RoleKeyUtil#normalizeForScope` du client bureau mentionné dans le commentaire Java. Un écart entre les deux implémentations produirait des **écarts de menu** entre web et desktop pour les mêmes comptes.

---

## 12.7 Synthèse pour l’exploitation

Lors du **diagnostic** « un utilisateur ne voit pas le module X » :

1. Vérifier le **rôle** `ROLE_*` et les **autorités non préfixées** réellement présentes dans la session (logs de debug Spring, traceur d’autorités).
2. Identifier le **`servletPath`** de la page dans `NavigationRegistry` ou dans le template.
3. Appliquer mentalement (ou par test unitaire) la règle du `switch` correspondante.
4. Pour le tableau de bord, tester successivement `DASHBOARD`, la clé `*_DASHBOARD`, puis l’**implicit** par rôle.
5. Pour le courrier, distinguer **courrier physique** (`PHYSICAL_COURIER` + liste de rôles) et **gestion courrier** (liste de rôles restreinte).

---

## 12.8 Limites documentaires

Ce chapitre décrit **uniquement** la visibilité **menu** telle que codée dans `WebSyscoPermissions`. Il ne remplace pas :

- les annotations `@PreAuthorize` ou les règles `SecurityFilterChain` qui peuvent **bloquer** l’URL même si le lien était visible (incohérence à éviter) ;
- les contrôles **métier** dans les services (validation de périmètre territorial, confidentialité) ;
- les règles **WebSocket** d’abonnement aux topics de notification.

Toute revue de sécurité doit croiser **les trois niveaux** : réseau / HTTP, menu, et logique domaine.

---

*Fin du chapitre 12.*
