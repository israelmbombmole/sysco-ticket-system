# Chapitre 15 — Gouvernance du code, conventions et cycle de vie logiciel

Ce chapitre décrit comment **structurer le travail collaboratif** autour du dépôt `sysco-web` : conventions de nommage, gestion des branches, revue de code, gestion des dépendances et **alignement** avec le client JavaFX historique. Il ne remplace pas le guide interne de l’organisation mais fournit une **base** cohérente pour les équipes techniques francophones.

---

## 15.1 Principes directeurs

Trois principes guident l’évolution du code :

1. **Clarté avant astuce** — le prochain mainteneur doit comprendre l’intention sans déboguer mentalement pendant une heure.
2. **Compatibilité contrôlée** — les changements de schéma base ou de contrat d’API doivent être **versionnés** et **annoncés** aux consommateurs (bureau, intégrations batch).
3. **Sécurité par défaut** — toute nouvelle surface d’attaque (endpoint, upload, export) passe par une **revue** explicite des risques.

---

## 15.2 Organisation du dépôt Maven

Le module `sysco-web` suit l’arborescence standard Maven : `src/main/java` pour le code compilé, `src/main/resources` pour les ressources (templates Thymeleaf, fichiers `application*.yml`, scripts statiques, messages i18n), `src/test` pour les tests. Les **profils** Maven éventuels doivent être documentés dans le `pom.xml` avec des commentaires succincts ou dans ce paquet documentaire.

Éviter d’introduire des **ressources binaires lourdes** (vidéos, dumps) dans Git ; utiliser un **artefact store** ou un lien versionné.

---

## 15.3 Conventions Java

### 15.3.1 Style et formatage

Uniformiser le formatage avec **Spotless**, **Google Java Format** ou la configuration **EditorConfig** adoptée par le projet. Les diffs de « whitespace only » nuisent à la lisibilité de l’historique.

### 15.3.2 Nommage

- **Packages** en minuscules, sans underscore sauf exception historique.
- **Classes** en `UpperCamelCase` ; **méthodes et variables** en `lowerCamelCase`.
- Les **constantes** en `UPPER_SNAKE_CASE`.
- Préférer des noms **métier** (`TicketMonitoringController`) aux abréviations obscures.

### 15.3.3 Immuabilité et nullabilité

Lorsque possible, favoriser des objets **immuables** pour les DTO exposés aux couches supérieures. Documenter le contrat **null** (Optional, annotations JSR-305 ou JetBrains) pour réduire les `NullPointerException` en production.

---

## 15.4 Couches et dépendances

Respecter une direction de dépendance **contrôleur → service → dépôt** ; éviter que les contrôleurs accèdent directement aux **repositories** pour des opérations complexes transactionnelles — centraliser la logique dans un **service** pour faciliter les tests et la réutilisation.

Les **aspects** transverses (audit, sécurité) doivent rester **minces** : une annotation qui déclenche cinquante lignes de logique métier est un signal d’**anomalie architecturale**.

---

## 15.5 Gestion des branches Git

Un modèle courant est **Gitflow allégé** ou **trunk-based** avec **feature branches** courtes. Quel que soit le modèle :

- la branche `main` (ou `master`) reste **potentiellement livrable** ;
- les **fusions** passent par pull request avec CI verte ;
- les **hotfix** production partent d’un tag stable et sont **rétroportés** sur la ligne de développement principale.

Les messages de commit en **français ou anglais** doivent être **cohérents** au sein d’un dépôt ; éviter le mélange anarchique qui complique les recherches.

---

## 15.6 Revue de code : grille de lecture

Le relecteur vérifie au minimum :

- **Exactitude** : la logique répond-elle à la spécification ?
- **Sécurité** : injection, exposition de données, CSRF, autorisations ?
- **Performance** : requêtes dans des boucles, absence de pagination ?
- **Tests** : couverture des cas limites et des régressions connues ?
- **Documentation** : commentaires utiles (pas de paraphrase du code), mise à jour des guides si nécessaire.

Une revue **approuvée** n’efface pas la **responsabilité** du auteur : celui-ci reste garant du correctif.

---

## 15.7 Gestion des dépendances

### 15.7.1 Mise à jour

Planifier des **montées de version** régulières Spring Boot et bibliothèques transitoires pour recevoir les **correctifs de sécurité**. Tester en environnement isolé : certaines montées majeures cassent les APIs dépréciées.

### 15.7.2 Licence

Le responsable juridique ou open source de l’organisation valide les **licences** des nouvelles bibliothèques (GPL, AGPL, etc.) avant adoption.

---

## 15.8 Internationalisation

Toute chaîne visible par l’utilisateur final doit transiter par **`messages.properties` / `messages_fr.properties`** (ou équivalent) avec des clés **stables**. Éviter la concaténation de fragments traduits dans le code Java : l’ordre des mots varie selon les langues.

Les textes longs (visite guidée) peuvent contenir du **HTML limité** (`<br>`, `<strong>`) ; valider le rendu dans les deux langues après modification.

---

## 15.9 Journalisation

Utiliser **SLF4J** avec niveaux cohérents :

- `ERROR` : incident nécessitant une action ;
- `WARN` : situation anormale mais gérée ;
- `INFO` : jalons métier utiles en production (avec parcimonie) ;
- `DEBUG` : diagnostic développeur, souvent désactivé en prod.

Ne jamais logger de **secrets** (mots de passe, jetons) ni de **données personnelles** complètes sans pseudonymisation.

---

## 15.10 Feature flags et bascules

Si l’équipe introduit des **feature flags**, centraliser leur lecture (service de configuration, base) et **documenter** le comportement par défaut. Prévoir le **retrait** du code mort après stabilisation pour limiter la dette technique.

---

## 15.11 Alignement JavaFX / Web

Lorsqu’une règle métier existe déjà sur le bureau, **factoriser** la logique dans une bibliothèque partagée si l’architecture le permet, ou **dupliquer en documentant** l’écart et la raison (délais, dépendances circulaires). L’**écart silencieux** entre clients est la principale source de **tickets de support** « ça marche sur l’un et pas sur l’autre ».

---

## 15.12 Documentation vivante

Ce paquet `docs/fr/` doit être **mis à jour** dans la même pull request qu’un changement structurel majeur (nouveau module, nouvelle règle de sécurité, nouveau profil de déploiement). La documentation obsolète **nuis** davantage que son absence : elle induit des actions incorrectes en incident.

---

## 15.13 Accessibilité et ergonomie

Même pour une application **interne**, l’accessibilité (contraste, focus clavier, labels ARIA sur composants critiques) améliore l’usage pour tous et prépare d’éventuelles **obligations légales**. Les évolutions UI doivent être **testées** avec lecteur d’écran sur un échantillon de pages.

---

## 15.14 Dette technique : inventaire

Maintenir une liste priorisée des **refactorings** reportés (fichiers « god class », duplication, tests manquants). Lors de chaque sprint, allouer une **capacité** fixe à la réduction de dette pour éviter l’effet **big bang** irréaliste.

---

## 15.15 Clôture

La gouvernance n’est efficace que si elle est **vécue** : rituels courts, outils qui ne frictionnent pas, et **responsabilisation** des équipes. Ce chapitre fournit un cadre ; l’équipe locale l’adapte aux contraintes de taille, de réglementation et de culture d’entreprise.

---

*Fin du chapitre 15.*
