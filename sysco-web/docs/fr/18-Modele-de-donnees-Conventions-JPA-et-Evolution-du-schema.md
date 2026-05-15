# Chapitre 18 — Modèle de données, conventions JPA et évolution du schéma

Ce chapitre fournit un **cadre conceptuel** pour travailler sur la persistance dans SYSCO Web : relations entre entités, stratégie de clés primaires, transactions, migrations **Flyway** et **rétrocompatibilité** lors des montées de version. Il complète le chapitre 4 par une vue orientée **maintenance** et **gouvernance des données**.

---

## 18.1 Principes de modélisation

Le modèle relationnel sous-jacent reflète des **processus métier** (tickets, courrier, utilisateurs, audits). Les principes suivants guident les évolutions :

1. **Intégrité référentielle** — les clés étrangères et contraintes `NOT NULL` appropriées évitent les **orphelins** difficiles à nettoyer.
2. **Normalisation raisonnable** — éviter la **sur-normalisation** qui multiplie les jointures sur les écrans interactifs ; accepter parfois une **dénormalisation** contrôlée documentée pour la performance.
3. **Nommage explicite** — tables et colonnes compréhensibles par un **DBA** qui n’a pas lu le code Java.

---

## 18.2 Mapping objet-relationnel (JPA)

### 18.2.1 Entités et cycle de vie

Les entités JPA sont attachées à un **contexte de persistance**. Les opérations `persist`, `merge`, `remove` doivent s’exécuter dans des **transactions** délimitées pour garantir la cohérence. Un service métier typique est annoté `@Transactional` au bon niveau (lecture seule vs écriture).

### 18.2.2 Relations

Les associations `@OneToMany`, `@ManyToOne`, `@ManyToMany` doivent préciser le **propriétaire** de la relation et la stratégie de **fetch** (`LAZY` par défaut pour les collections volumineuses). Un `EAGER` mal placé provoque des **chargements en cascade** coûteux.

### 18.2.3 Identifiants

Les stratégies `IDENTITY`, `SEQUENCE` ou `UUID` ont des **compromis** : l’`IDENTITY` peut compliquer le batch insert selon le dialecte ; les **UUID** facilitent la fusion de bases mais augmentent la taille des index. Documenter le choix pour chaque **agrégat racine**.

---

## 18.3 Conventions de nommage JPA / base

Souvent, les noms de tables suivent un **préfixe** ou un **schéma** applicatif. Les colonnes d’audit (`created_at`, `updated_at`, `created_by`) facilitent le **support** et les enquêtes post-incident. Aligner les noms Java (`createdAt`) avec la stratégie **ImplicitNamingStrategy** ou des `@Column(name=...)` explicites pour éviter les surprises.

---

## 18.4 Transactions et isolation

Le niveau d’**isolation** par défaut du SGBD peut permettre des **lectures fantômes** ou des **écritures perdues** sur scénarios concurrents rares mais critiques (allocation de numéros, verrous métier). Pour ces cas, envisager :

- verrous **pessimistes** (`SELECT ... FOR UPDATE`) sur une courte durée ;
- contraintes **UNIQUE** pour empêcher les doublons logiques ;
- files **sérialisées** par design.

---

## 18.5 Migrations Flyway : philosophie

Flyway versionne le schéma par des scripts **ordonnés** (`V1__...`, `V2__...`). Chaque script doit être :

- **idempotent** dans la mesure du possible ou **monotone** (jamais modifié après application en prod) ;
- testé sur une **copie** du schéma de production ;
- accompagné d’une **note de version** pour l’exploitation si opération longue (rebuild d’index).

Les scripts **destructifs** (`DROP COLUMN`) exigent une **fenêtre de maintenance** ou une stratégie **expand-contract** : ajouter la nouvelle colonne, double-écrire, basculer les lecteurs, retirer l’ancienne.

---

## 18.6 Données de référence

Les jeux de référence (types de ticket, statuts) peuvent être chargés par **scripts Flyway** `R__` répétables ou par du **code** au démarrage. Éviter la **duplication** de vérité entre fichiers SQL et énumérations Java sans synchronisation.

---

## 18.7 Performance des requêtes JPA

### 18.7.1 Pagination

Les listes utilisateur doivent utiliser `Pageable` ou équivalent pour limiter le **résultat** SQL. Charger dix mille lignes pour n’en afficher que vingt est un **anti-pattern** courant.

### 18.7.2 Projections

Pour les tableaux en lecture seule, les **DTO projections** ou interfaces Spring Data réduisent le **coût** d’hydratation des entités complètes.

### 18.7.3 Cache de second niveau

Hibernate peut activer un cache L2 ; chaque activation doit être **justifiée** (données peu volatiles) et **invalidée** proprement lors des mises à jour.

---

## 18.8 Cohérence multi-modules

Lorsque plusieurs modules métier **partagent** des tables, définir des **frontières** claires : quel module **possède** la table, qui peut seulement **lire**. Les **modifications croisées** non coordonnées génèrent des bugs difficiles à reproduire.

---

## 18.9 Archivage et rétention

Les données **anciennes** peuvent être **archivées** vers des tables d’historique ou un entrepôt pour garder la base opérationnelle **compacte**. La politique de **rétention** (combien d’années conserver les audits, les brouillons) est une décision **métier + juridique**.

---

## 18.10 Environnements et jeux de schéma

Maintenir un **schéma minimal** pour les tests CI (H2 ou conteneur Oracle) qui reflète les **contraintes** importantes. Un écart trop grand entre test et prod masque des erreurs de **dialecte** SQL.

---

## 18.11 Revue des changements de schéma

Toute pull request modifiant des entités ou des scripts Flyway doit être **revue** par au moins une personne familière avec la **production** : impact sur la **taille** des tables, verrous prolongés, besoin de **message aux utilisateurs**.

---

## 18.12 Documentation du modèle

Un **diagramme entité-relation** (même simplifié) dans ce paquet ou dans un outil de modélisation à jour accélère l’**onboarding**. Lorsqu’un champ est **déprécié**, le marquer en commentaire SQL et planifier sa **suppression** en version N+2.

---

## 18.13 Synthèse

Le schéma de données est le **cœur durable** de SYSCO Web : les écrans et API changent plus vite que les tables. Investir dans des **migrations propres**, des **conventions** stables et une **documentation** synchronisée réduit le risque de **régression** et le coût des audits de données.

---

## 18.14 Extension : cycle de vie d’un correctif de données

Lorsqu’un bug applicatif a **corrompu** des lignes (mauvais statut, dates incohérentes), la procédure recommandée est :

1. **Geler** la cause applicative (correctif code déployé avant ou en même temps que la réparation).
2. **Identifier** l’ensemble des lignes impactées par une requête `SELECT` traçable (sauvegardée dans le ticket).
3. **Valider** sur copie l’effet du `UPDATE` ou script de migration de données.
4. **Exécuter** en transaction avec **point de restauration** ou backup.
5. **Vérifier** par échantillonnage et **monitoring** métier post-déploiement.

Les correctifs ad hoc en production sans **billet** ni **revue** sont une source majeure d’**incidents secondaires**.

---

## 18.15 Extension : indexation et maintenance Oracle

Sur Oracle, les index **B-tree** accélèrent les filtres et jointures ; les index **inutiles** ralentissent les `INSERT`/`UPDATE`. Après des chargements massifs, une **reorganisation** ou un `GATHER_STATS` peut être nécessaire pour que l’optimiseur choisisse les bons plans. Les opérations **online** réduisent les blocages utilisateurs mais nécessitent une **surveillance** de l’I/O.

Planifier une **fenêtre** pour les opérations lourdes (`REBUILD`, partitionnement) et communiquer aux **utilisateurs** si une **dégradation** temporaire est attendue.

---

## 18.16 Extension : traçabilité des changements DDL

Chaque script Flyway appliqué doit rester **lisible** dans l’historique Git : éviter les scripts « fourre-tout » qui mélangent dix évolutions non liées. En cas d’audit externe, la **traçabilité** Git + Flyway démontre **qui** a introduit **quelle** structure et **quand**.

---

*Fin du chapitre 18.*
