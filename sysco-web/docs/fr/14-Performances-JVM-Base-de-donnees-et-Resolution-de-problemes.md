# Chapitre 14 — Performances, JVM, base de données et résolution de problèmes

Ce chapitre complète le runbook du chapitre 6 en approfondissant les **réglages de performance**, le **comportement sous charge** des composants SYSCO Web, et une **méthodologie de diagnostic** structurée pour l’exploitation. Il vise à réduire le temps moyen de résolution (MTTR) des incidents liés à la lenteur ou à la saturation des ressources.

---

## 14.1 Méthodologie générale de diagnostic

Face à une plainte « l’application est lente », éviter de modifier au hasard la configuration. Adopter la séquence suivante :

1. **Qualifier** : lenteur générale, page unique, heure de pointe uniquement, certains utilisateurs seulement ?
2. **Mesurer** : latence réseau, temps serveur (logs d’accès), temps base de données (traces SQL, AWR Oracle si disponible), CPU et mémoire JVM.
3. **Isoler** : le goulet est-il **application**, **SGBD**, **stockage**, **réseau** ou **client navigateur** ?
4. **Corriger** : une fois la couche identifiée, appliquer un changement **unique** et mesurer à nouveau.
5. **Documenter** : ajouter l’incident à la base de connaissance avec symptômes, cause racine et correctif.

---

## 14.2 JVM : mémoire et ramasse-miettes

### 14.2.1 Heap

Une heap trop petite provoque des **GC fréquents** et des `OutOfMemoryError`. Une heap trop grande sur une machine contrainte allonge les **pauses GC**. Commencer par des valeurs alignées sur la **charge attendue** et le profil d’objets (sessions utilisateur, caches).

### 14.2.2 Métaspace

Les applications Spring chargent de nombreuses classes. Surveiller l’utilisation du **metaspace** ; une fuite de class loaders (rechargement dynamique non nettoyé) peut le faire croître jusqu’à l’erreur.

### 14.2.3 Options de diagnostic

En environnement de **préproduction** ou lors d’un incident critique autorisé, les flags suivants peuvent aider (à retirer ensuite) :

- `-XX:+HeapDumpOnOutOfMemoryError` avec chemin de dump ;
- journaux GC (`-Xlog:gc*` sur JDK moderne) ;
- **flight recordings** Java Mission Control pour corrélation CPU / allocations.

**Attention :** les dumps heap contiennent des **données sensibles** ; les stocker chiffrés et les purger après analyse.

---

## 14.3 Pool de connexions JDBC

Le pool (HikariCP est le défaut Spring Boot) limite le nombre de connexions simultanées vers Oracle. Un pool **sous-dimensionné** crée de l’attente côté application ; **sur-dimensionné** peut saturer le SGBD.

**Indicateurs :**

- temps d’attente pour obtenir une connexion ;
- nombre de connexions actives vs maximum ;
- timeouts fréquents dans les logs.

Ajuster `maximum-pool-size`, `connection-timeout` et valider avec la **capacité** réelle du serveur de base de données (sessions max, autres consommateurs).

---

## 14.4 Requêtes et index

Les écrans qui listent des milliers d’enregistrements sans **pagination** ni **filtre** pénalisent le SGBD. Vérifier :

- présence d’**index** sur les colonnes de jointure et de filtre fréquent ;
- absence de **N+1** JPA (fetch join, `@EntityGraph`, DTO projections) ;
- plans d’exécution stables (statistiques à jour sur Oracle).

Pour les rapports lourds, envisager des **vues matérialisées** ou des **jobs** asynchrones plutôt que des requêtes synchrones dans la requête HTTP utilisateur.

---

## 14.5 Cache applicatif et Thymeleaf

Spring peut mettre en cache les **templates** compilés en production. Vérifier que le mode **développeur** Thymeleaf n’est pas activé par erreur en prod (impact performance et parfois fuite d’informations).

Tout cache métier (si introduit) doit avoir une **politique d’invalidation** claire lors des mises à jour de données partagées.

---

## 14.6 WebSocket et charge

Les connexions **STOMP** WebSocket consomment des **descripteurs de fichiers** et de la mémoire par canal. Sous forte affluence, dimensionner :

- les limites OS (`ulimit`) ;
- le **broker** message (si externe) ;
- les **heartbeats** pour détecter les clients morts.

Un storm de reconnexions après une coupure réseau peut provoquer un **thundering herd** : prévoir du **backoff** côté client si configurable.

---

## 14.7 Frontal HTTP et TLS

Un **reverse proxy** (nginx, Apache, load balancer applicatif) termine souvent TLS et compresse les réponses. Vérifier :

- versions de **cipher** acceptables par la politique de sécurité ;
- **HTTP/2** si activé (multiplexage) ;
- **timeouts** alignés avec l’application pour éviter les 502 prématurés ou les connexions pendantes.

---

## 14.8 Stockage des fichiers partagés

Si les pièces jointes sont sur **disque partagé** ou **S3-compatible**, la latence I/O impacte les uploads/downloads. Surveiller l’**espace libre**, la **fragmentation**, et les quotas. Les antivirus en **accès temps réel** sur le répertoire d’upload peuvent dégrader fortement les écritures : exclure avec prudence ou reporter le scan.

---

## 14.9 Scénarios d’incident et pistes

### 14.9.1 « Tout le monde a une page blanche après déploiement »

Vérifier les logs au démarrage : **échec Flyway**, bean manquant, port déjà utilisé. Contrôler la **compatibilité** du package déployé avec la version Java runtime.

### 14.9.2 « Erreurs 403 aléatoires après login »

CSRF : formulaire sans token, session expirée, **load balancing** sans **affinité de session** alors que la session est mémoire locale. Vérifier la **réplication de session** ou le store **Redis** si plusieurs instances.

### 14.9.3 « Lenteur uniquement le matin »

Jobs planifiés, **sauvegardes** base, ou **batch** externes sur la même fenêtre. Corréler avec les métriques Oracle (AWR) et l’ordonnanceur OS.

### 14.9.4 « Mémoire JVM qui monte sans redescendre »

Fuite objet ou **cache** sans borne. Prendre des heap dumps comparatifs, analyser les dominators. Vérifier les **listeners** non désinscrits et les **ThreadLocal**.

---

## 14.10 Capacité et planification

Établir un **modèle simple** : utilisateurs simultanés × requêtes par minute × coût moyen par requête (CPU + I/O). Réévaluer après chaque **grosse fonctionnalité** (nouveaux rapports, nouveaux flux temps réel).

---

## 14.11 Checklist post-incident

Après résolution :

- mettre à jour le **runbook** si la cause était nouvelle ;
- ajouter un **test de non-régression** ou une alerte ;
- communiquer aux **parties prenantes** le résumé et les actions préventives.

---

## 14.12 Alignement avec les objectifs métier

Les optimisations techniques doivent servir des **objectifs mesurables** : réduction du temps de traitement d’un dossier, diminution des abandons sur un formulaire long, respect des **engagements de service** vers les citoyens ou partenaires institutionnels selon le contexte SYSCO.

---

*Fin du chapitre 14.*
