# Annexe 19 — Références techniques et lectures complémentaires

Cette annexe oriente le lecteur vers des **ressources externes** et internes utiles pour approfondir les sujets abordés dans la documentation technique française de SYSCO Web. Les URL et versions doivent être **vérifiées** au moment de la consultation, car l’écosystème Java et Spring évolue rapidement.

---

## 19.1 Documentation Spring

Le projet SYSCO Web s’appuie sur **Spring Boot 3** et **Spring Security**. La documentation officielle couvre la configuration de la **chaîne de sécurité**, l’intégration **OAuth2** si utilisée, et les bonnes pratiques **REST**. Les guides « Spring Security Reference » détaillent le modèle `Authentication`, les `GrantedAuthority`, et la protection **CSRF** pour les applications servlet classiques.

Pour la couche **Web**, la documentation **Thymeleaf** explique le moteur de templates, l’intégration Spring, et les fragments réutilisables utilisés dans les layouts (`layout/base.html` et dérivés).

---

## 19.2 Persistance et migrations

La spécification **Jakarta Persistence (JPA)** et le manuel **Hibernate** documentent le mapping objet-relationnel, les stratégies de fetch, et le **cache** de second niveau. **Flyway** publie les conventions de nommage des scripts et les options de **baseline** pour bases existantes.

Pour **Oracle Database**, la documentation « SQL Language Reference » et les guides de **performance** (optimiseur, statistiques, AWR) complètent les chapitres 14 et 18 de ce paquet.

---

## 19.3 Temps réel et messaging

Les applications utilisant **STOMP** sur **WebSocket** peuvent se référer au guide Spring « Web on Servlet Stack » section messaging. La compréhension des **en-têtes** STOMP (`ack`, `heart-beat`) aide au diagnostic des déconnexions intermittentes derrière proxy.

---

## 19.4 Sécurité applicative et réglementation

L’**OWASP** publie le Top 10, des fiches sur le **CSRF**, la **validation des entrées**, et les **Cheat Sheets** pour la configuration des en-têtes HTTP. Pour le cadre européen, le texte du **RGPD** et les lignes directrices des autorités de protection des données précisent les obligations en matière de **journalisation**, de **minimisation** et de **notification** d’incident.

---

## 19.5 Observabilité et exploitation

Les guides sur **Prometheus**, **Grafana**, ou les solutions **ELK** aident à structurer les **logs** et **métriques**. Pour la JVM, **Java Mission Control** et les articles sur le **GC** (G1, ZGC selon versions) éclairent les chapitres sur les performances.

---

## 19.6 Qualité et tests

Le guide **JUnit 5**, la documentation **Testcontainers**, et les bonnes pratiques **REST Assured** ou **MockMvc** pour les tests d’intégration HTTP complètent le chapitre 13. Les équipes peuvent aussi consulter les catalogues de **test patterns** pour éviter les tests **fragiles** liés au temps ou à l’ordre d’exécution.

---

## 19.7 Sources internes au dépôt

Outre les fichiers `docs/fr/*.md`, les lecteurs doivent consulter :

- le code source sous `src/main/java/com/sysco/web/**` pour le comportement **exact** ;
- les fichiers `application*.yml` et profils pour les **paramètres** de déploiement ;
- les ressources `messages.properties` et `messages_fr.properties` pour les **textes** interface et la visite guidée ;
- les scripts statiques sous `src/main/resources/static/**` pour le comportement **client** (tour guidé, thème).

En cas de contradiction entre un document et le code, **le code et la configuration effective en production** font foi jusqu’à correction documentaire.

---

## 19.8 Maintenance de cette annexe

Lors des montées de version majeures (Spring Boot, Java LTS), relire cette annexe et ajuster les **liens** et **versions** mentionnés dans les autres chapitres. Une documentation à jour réduit le temps passé à chercher des informations obsolètes sur les forums.

---

## 19.9 Glossaire rapide des sigles utilisés dans le paquet

| Sigle | Signification courte |
|-------|----------------------|
| API | Interface de programmation applicative |
| CI/CD | Intégration et déploiement continus |
| CSRF | Falsification de requête inter-sites |
| DDL | Langage de définition de données |
| DTO | Objet de transfert de données |
| E2E | Test bout en bout |
| GC | Ramasse-miettes (garbage collector) |
| HTTP | Protocole de transfert hypertexte |
| IAM | Gestion des identités et accès |
| IdP | Fournisseur d’identité |
| JDBC | Connectivité base Java |
| JPA | API de persistance Java |
| JWT | Jeton JSON (si utilisé ailleurs) |
| LDAP | Protocole d’accès annuaire léger |
| MTTR | Temps moyen de résolution |
| NFR | Exigence non fonctionnelle |
| OIDC | OpenID Connect |
| PCA | Plan de continuité d’activité |
| PRI | Plan de reprise informatique |
| RGPD | Règlement général sur la protection des données |
| RPO / RTO | Perte de données maximale admise / délai de rétablissement |
| SIEM | Gestion des informations et événements de sécurité |
| SLA / SLO / SLI | Accord / objectif / indicateur de niveau de service |
| SMTP | Protocole courrier sortant |
| SQL | Langage de requête structurée |
| TLS | Sécurité de la couche transport |
| UTC | Temps universel coordonné |

---

## 19.10 Clôture du paquet documentaire

Les chapitres **01 à 18** et les annexes **00** (lisez-moi) et **19** forment un ensemble **cohérent** en français pour l’architecture, la sécurité, l’exploitation et la qualité de SYSCO Web. Pour une impression ou un PDF unique, assembler les fichiers dans l’ordre numérique indiqué dans le fichier `00-LISEZMOI-Documentation-Technique.md`.

---

## 19.11 Utilisation pédagogique de ce paquet

Les formateurs peuvent découper la lecture en **quatre blocs** d’environ deux heures : (1) vision d’ensemble et sécurité (chapitres 01–02, 12) ; (2) web, persistance et données (03–04, 18) ; (3) temps réel, i18n, déploiement (05–06) ; (4) qualité, exploitation, performances et gouvernance (07, 11, 13–17, 19). Chaque bloc se conclut par une **liste de questions** ouvertes pour vérifier la compréhension : par exemple, décrire le chemin d’une requête authentifiée jusqu’au filtrage du menu, ou expliquer pourquoi le tableau de bord reste visible pour certains rôles sans permission `*_DASHBOARD` explicite. Les apprenants avancés peuvent enchaîner sur la lecture du code source des classes citées (`WebSyscoPermissions`, gestionnaires d’authentification, configuration WebSocket) pour ancrer les concepts dans l’implémentation réelle. Enfin, pour les **audits** ou revues tierces, joindre à ce paquet les extraits de configuration effectivement déployés (sans secrets en clair) : profils Spring actifs, paramètres de pool JDBC, et politique de sauvegarde validée par l’exploitation.

---

*Fin de l’annexe 19.*
