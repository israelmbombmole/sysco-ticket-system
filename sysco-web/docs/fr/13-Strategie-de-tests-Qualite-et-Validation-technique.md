# Chapitre 13 — Stratégie de tests, qualité et validation technique

Ce chapitre rassemble les **pratiques recommandées** pour valider SYSCO Web avant mise en production : tests automatisés, tests manuels structurés, critères d’acceptation non fonctionnels et **traçabilité** des exigences. Il s’adresse aux équipes QA, aux développeurs qui maintiennent la chaîne CI/CD et aux responsables métier qui signent les livraisons.

---

## 13.1 Pyramide de tests appliquée au module `sysco-web`

La pyramide classique distingue trois niveaux : **unitaire**, **intégration**, **bout en bout (E2E)**. Pour une application Spring Boot riche en sécurité et en persistance, la répartition pragmatique est la suivante.

### 13.1.1 Tests unitaires

Les tests unitaires ciblent des **classes pures** ou des services **mockés** : calculs, transformations, validateurs, helpers (`WebSyscoPermissions` peut être couvert par des tests paramétrés sur `canAccessNavPath` avec des `Authentication` de façade). Les avantages sont la **rapidité** d’exécution et la **localisation fine** des régressions.

**Bonnes pratiques :**

- isoler la logique métier des **beans** infrastructure (DataSource, `EntityManager`) ;
- utiliser des jeux de données **minimaux** mais représentatifs des rôles SYSCO ;
- nommer les tests selon la convention *Given_When_Then* ou équivalent lisible en français dans les rapports CI.

### 13.1.2 Tests d’intégration Spring

`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest` permettent de monter un **contexte partiel** et de vérifier :

- la **résolution des contrôleurs** et la sérialisation JSON ou le rendu Thymeleaf ;
- les **requêtes JPA** contre une base **H2** ou **Testcontainers** Oracle ;
- la **chaîne de sécurité** (mock `UserDetails`, rôles) sur des endpoints sensibles.

Ces tests sont plus lents mais **indispensables** pour détecter les erreurs de configuration (`@Transactional` manquant, mauvais profil `application-test.yml`).

### 13.1.3 Tests E2E

Les scénarios E2E (Selenium, Playwright, Cypress selon l’outillage retenu) valident le **parcours utilisateur** : login, navigation menu, soumission de formulaire avec jeton CSRF, réception d’une notification temps réel si l’environnement de test le permet.

**Contraintes :**

- nécessitent une **stack complète** (ou conteneurs) ;
- sont **fragiles** aux changements de sélecteurs CSS — privilégier des attributs `data-testid` stables dans les templates ;
- doivent être **peu nombreux** mais couvrir les **flux critiques** (création ticket, partage fichier, audit connexion).

---

## 13.2 Matrice de couverture par domaine fonctionnel

Le tableau ci-dessous propose une **cartographie** entre modules métier et types de tests prioritaires. Les cellules sont indicatives ; chaque organisation ajuste les priorités selon son registre des risques.

| Domaine | Unitaire | Intégration | E2E | Commentaire |
|---------|----------|-------------|-----|-------------|
| Authentification / lockout | ○ | ● | ● | Vérifier verrouillage et messages i18n |
| Autorisation menu | ● | ○ | ○ | Jeux `WebSyscoPermissions` exhaustifs |
| Saisie données / tickets | ○ | ● | ● | CSRF + validation formulaire |
| Partage fichiers | ○ | ● | ● | Quotas, types MIME, audit |
| Notifications temps réel | ○ | ● | ○ | Mock broker ou broker embarqué |
| Migrations Flyway | — | ● | — | Smoke sur schéma vide → dernier script |
| i18n FR/EN | ○ | ○ | ○ | Snapshots de clés manquantes |

Légende : ● fortement recommandé, ○ souhaitable, — hors périmètre direct.

---

## 13.3 Données de test et anonymisation

Les environnements de **recette** ne doivent pas utiliser des **jeux de production bruts** sans anonymisation. Les fiches personnes, identifiants et chemins de fichiers réels exposent l’organisation à des **sanctions RGPD** et à des **fuites** en cas de compromission du bac de test.

**Procédure type :**

1. Extraire un **sous-échantillon** statistiquement représentatif.
2. Remplacer noms, emails, téléphones par des **valeurs synthétiques** cohérentes (même format).
3. Réinitialiser les **mots de passe** vers des secrets de démonstration rotatifs.
4. Documenter la **version** du jeu anonymisé et sa **date de fabrication**.

---

## 13.4 Qualité non fonctionnelle (NFR)

### 13.4.1 Performance

Définir des **objectifs** mesurables : temps de réponse p95 sur les pages chaudes, consommation mémoire sous charge, nombre de sessions simultanées. Utiliser des outils de charge (JMeter, Gatling, k6) contre un environnement **isolé** pour ne pas perturber la production.

### 13.4.2 Disponibilité

Les SLAs internes précisent la **fenêtre de maintenance** et le **RTO/RPO** en cas d’incident base de données. Les procédures de **bascule** (DNS, équilibreur) doivent être **répétées** au moins annuellement.

### 13.4.3 Observabilité

Centraliser les **logs** (JSON structuré idéalement), les **métriques** JVM et applicatives, et les **traces** distribuées si plusieurs services sont en jeu. Corréler `X-Request-Id` ou équivalent du point d’entrée HTTP jusqu’aux requêtes SQL lentes.

### 13.4.4 Sécurité

Scanner les **dépendances** (OWASP Dependency-Check, Snyk), analyser les en-têtes HTTP (`Content-Security-Policy`, `X-Frame-Options`), vérifier la **rotation** des secrets et la **désactivation** des comptes partants.

---

## 13.5 Definition of Done (DoD) pour une livraison

Une **user story** ou un correctif n’est considéré comme terminé que si :

1. le code est **revu** par un pair ;
2. les tests automatisés pertinents sont **verts** ;
3. la **documentation** (changelog, ce paquet technique si impact architectural) est mise à jour ;
4. une **démo** ou une capture d’écran est disponible pour le métier lorsque l’UI change ;
5. les **flags de feature** éventuels sont documentés pour l’exploitation.

---

## 13.6 Gestion des défauts et gravité

Classifier les anomalies :

- **Bloquante** : indisponibilité majeure, fuite de données, contournement d’authentification ;
- **Majeure** : fonctionnalité clé dégradée sans contournement raisonnable ;
- **Mineure** : gêne UI, message d’erreur peu clair ;
- **Cosmétique** : alignement pixel, typo sans impact métier.

Les **SLA de correction** dérivent de cette classification et du calendrier de release.

---

## 13.7 Intégration continue

Le pipeline CI devrait au minimum :

1. compiler avec la **même version JDK** que la production ;
2. exécuter **tests + lint** (Checkstyle, SpotBugs selon configuration) ;
3. publier les **rapports** de couverture ;
4. **signer** ou **pousser** l’artefact versionné vers un registre binaire.

Les branches **protégées** exigent un build vert avant fusion.

---

## 13.8 Revue de risques avant release

Une **checklist** de release peut inclure :

- migrations Flyway **idempotentes** et testées sur copie de schéma prod ;
- variables d’environnement **documentées** et présentes sur tous les nœuds ;
- **rollback** défini (rétablir version N-1, scripts SQL inverse si nécessaire) ;
- communication aux **utilisateurs** pour les changements visibles.

---

## 13.9 Formation et transfert de compétences

La documentation technique en français sert de support aux **sessions de formation** développeurs et support niveau 2. Prévoir des **exercices guidés** : lecture d’un log d’erreur Flyway, interprétation d’une matrice de permissions, reproduction d’un bug avec H2 local.

---

## 13.10 Perspectives d’évolution de la stratégie de tests

À moyen terme, l’équipe peut :

- industrialiser **Testcontainers** pour rapprocher les tests du moteur Oracle réel ;
- ajouter des **contrats d’API** si des microservices externes sont consommés ;
- mesurer la **couverture de mutation** sur les modules les plus sensibles (paiements, audit).

Ces investissements se justifient lorsque le **coût d’un incident** production dépasse le coût de maintenance de la suite de tests.

---

*Fin du chapitre 13.*
