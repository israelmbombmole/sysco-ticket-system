# Chapitre 10 — Architecture détaillée, flux de données et intégration SI

Ce chapitre approfondit la **vision d’ensemble** de SYSCO Web dans un **système d’information institutionnel** : flux de données entre couches, responsabilités des composants, considérations d’**urbanisation**, et **points d’extension** pour les projets d’intégration.

---

## 10.1 Positionnement dans le paysage SI

SYSCO Web n’est généralement **pas** le seul composant du système d’information. Il coexiste avec :

- un **annuaire** ou un **IAM** (identité centralisée) dans les organisations matures ;  
- des **référentiels** métiers (nomenclatures, codes pays, bureaux) parfois alimentés par d’autres applications ;  
- des **outils de messagerie** institutionnels (échange SMTP ou API) ;  
- des **plateformes de gestion documentaire** (GED) lorsque les pièces doivent être archivées sur le long terme avec valeur probante ;  
- des **outils de BI** (entrepôts de données, tableaux de bord décisionnels) alimentés par **exports** contrôlés depuis la base opérationnelle.

Dans l’état **standard** du module `sysco-web` décrit par ce dépôt, l’application est **relativement autonome** : la persistance JDBC/JPA et les fichiers locaux couvrent la majorité des besoins. Toute **intégration** avec un bus (ESB, Kafka, API REST externes) relève de **développements complémentaires** ou de **branches** spécifiques — à documenter dans l’**architecture cible** de votre ministère ou direction.

---

## 10.2 Flux logiques de données (niveau métier)

### 10.2.1 Création d’un dossier ticket

Du point de vue métier, la création suit souvent la séquence : un agent saisit les **éléments minimaux** (objet, catégorie, priorité, direction, pièces), le système **persiste** l’entité ticket et les **événements** de création, puis **notifie** les superviseurs ou met en file d’attente l’affectation. Techniquement, le contrôleur valide les entrées, le service applique les **invariants** (unicité partielle, champs obligatoires selon type), et la transaction commit les écritures. Les **identifiants techniques** (clé primaire) sont opaques pour l’utilisateur final ; la **référence métier** affichée peut être une concaténation ou un numéro séquentiel géré par la base ou le service.

### 10.2.2 Mise à jour et historisation

Chaque modification significative peut générer une **ligne d’historique** ou un **événement** dans une table dédiée. L’intérêt pour l’audit est double : reconstituer la **chronologie** pour un contrôle interne, et prouver **qui** a modifié **quoi**. Les performances imposent parfois une **stratégie d’archivage** : au-delà de N années, les événements sont déplacés vers une base froide ou compressés — décision **métier + juridique**, pas seulement technique.

### 10.2.3 Courrier physique

Le flux courrier relie **objet physique** et **enregistrement numérique**. La qualité du processus dépend de la **discipline des agents** (scanner à chaque passage de mains) et de la **formation**. Un écart entre réalité et système peut entraîner des **pertes** ou des **retards judiciaires** dans les contextes où la chaîne de custody a valeur probante.

---

## 10.3 Flux techniques entre couches

### 10.3.1 De la requête HTTP à la base

Le filtre de sécurité **intercepte** la requête avant le contrôleur. Une fois l’utilisateur authentifié, le **DispatcherServlet** Spring MVC sélectionne la méthode du contrôleur en fonction du verbe HTTP, du chemin et des paramètres. Le contrôleur peut :

- lier automatiquement les paramètres à un **DTO** ou à des types simples ;  
- invoquer un ou plusieurs **services** ;  
- capturer des exceptions métier et les traduire en messages utilisateur ;  
- retourner soit un **nom de vue** Thymeleaf, soit un corps JSON/XML dans les cas API.

Le service ouvre une **transaction** (souvent par défaut en lecture/écriture). Hibernate génère les **SQL** ; le pool JDBC exécute sur la base. Les **lazy loads** déclenchés après la fin de transaction provoquent des erreurs classiques : d’où l’importance des **transactions** bien bornées et des **fetch** explicites pour les écrans de liste.

### 10.3.2 Du domaine métier au template

Le contrôleur prépare un **modèle** : map clé → objet. Thymeleaf accède aux propriétés via **expressions** `${...}` et aux messages via `#{...}`. Les fragments `th:replace` composent la page finale. Les attributs `th:object` facilitent le **binding** de formulaires avec gestion d’erreurs de validation (`BindingResult`).

---

## 10.4 Stratégie de cache (potentielle)

Le code de référence peut **ne pas** activer de cache distribué. Pour des déploiements à **forte volumétrie**, on peut envisager :

- cache **local** (Caffeine) pour référentiels peu volatils ;  
- cache **distribué** (Redis) pour sessions ou métadonnées partagées — avec attention à la **cohérence** et à l’**invalidation**.

Toute introduction de cache doit être **justifiée par des mesures** (profiling) pour éviter la complexité inutile.

---

## 10.5 Gestion des erreurs et résilience

### 10.5.1 Erreurs utilisateur vs erreurs système

Les erreurs de **validation** (champ manquant) doivent retourner un message clair sur le même formulaire. Les erreurs **système** (panne DB) doivent être **journalisées** avec stack trace côté serveur et afficher un message **générique** côté client pour ne pas exposer d’informations sensibles.

### 10.5.2 Timeouts et files d’attente

Les appels vers des **services externes** (si ajoutés) doivent avoir des **timeouts** et un comportement de **repli** (circuit breaker) pour ne pas bloquer les threads du serveur d’application.

---

## 10.6 Séparation des environnements

| Environnement | Objectif | Données |
|---------------|----------|---------|
| Développement | Itération rapide | Souvent H2, jeu réduit |
| Intégration | Tests automatisés | Base jetable ou conteneur |
| Recette / UAT | Validation métier | Données anonymisées |
| Préproduction | Miroir prod | Sous-ensemble ou copie masquée |
| Production | Utilisateurs réels | Données réelles, sauvegardes |

Les **secrets** diffèrent obligatoirement entre environnements. Interdire la réutilisation des mots de passe **prod** sur la recette.

---

## 10.7 Observabilité avancée

Au-delà des logs fichiers, les organisations matures déploient :

- **métriques** JVM (Micrometer + Prometheus) ;  
- **traces distribuées** (OpenTelemetry) si plusieurs services ;  
- **tableaux de bord** (Grafana) pour corréler latence, erreurs 5xx, saturation pool JDBC.

SYSCO Web en **monolithe** tire avantage d’une observabilité **simple** : un processus, une base, des indicateurs lisibles.

---

## 10.8 Urbanisation : scénarios de scission future

Si le SI décide plus tard de **scinder** des domaines (ex. « Courrier » en microservice), les frontières naturelles à préparer seraient :

- API **REST** ou **événements** pour remplacer les appels de service internes ;  
- **synchronisation** des données référentielles ;  
- **authentification** fédérée (tokens) entre services.

Sans besoin immédiat, il suffit de **garder les services métier cohérents** et d’éviter les dépendances circulaires entre packages pour faciliter une extraction future.

---

## 10.9 Accessibilité RGPD / protection des données (rappel technique)

Du point de vue **implémentation** :

- minimiser les champs **personnels** dans les entités ;  
- prévoir mécanismes d’**export** et d’**effacement** si le droit à l’effacement s’applique (souvent limité par obligations légales de conservation en douane) ;  
- **chiffrer** les sauvegardes et les canaux ;  
- **journaliser** les accès aux fonctions sensibles (audit).

Le **juridique** tranche le fond ; l’**IT** met en œuvre.

---

## 10.10 Synthèse du chapitre

Ce chapitre a replacé SYSCO Web dans une **vision SI** : flux métier et techniques, environnements, observabilité, et trajectoires d’évolution. Il doit être maintenu à chaque **changement d’architecture** majeur (nouvelle intégration, nouveau déploiement cloud, etc.).

---

*Fin du chapitre 10.*
