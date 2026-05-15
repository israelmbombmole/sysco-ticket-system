# Annexe B — FAQ technique, évolutions et checklist release

---

## B.1 FAQ — développement

**Q1 : Quelle version de Java utiliser ?**  
R1 : **Java 17** (propriété `java.version` du parent Spring Boot dans `pom.xml`).

**Q2 : Comment lancer localement ?**  
R2 : `mvn spring-boot:run` depuis le module `sysco-web`, ou exécuter le JAR après `mvn package`. Vérifier `application.yml` pour le port (souvent 8080).

**Q3 : Où ajouter une migration SQL ?**  
R3 : `src/main/resources/db/migration/V{suivant}__description.sql`. Ne pas réécrire une migration déjà appliquée en production.

**Q4 : Comment ajouter un lien au menu ?**  
R4 : (1) `NavigationRegistry.mainNav()`, (2) clés `nav.*` dans `messages_fr.properties`, (3) cas dans `WebSyscoPermissions`, (4) contrôleur + template, (5) éventuellement clés `tour.mod.*` pour la visite guidée.

**Q5 : Pourquoi mon utilisateur ne voit pas le tableau de bord ?**  
R5 : Vérifier `hasDashboardAccess` et `implicitDashboardNavByRole` ; vérifier aussi que le template sidebar reçoit `navItems` sans exception.

---

## B.2 FAQ — exploitation

**Q6 : Le CDN Driver.js est bloqué.**  
R6 : Héberger `driver.js` et `driver.css` en interne et mettre à jour `base.html` avec des URLs `th:href="@{/libs/driver...}"` après copie dans `static/`.

**Q7 : WebSocket 502 derrière NGINX.**  
R7 : Configurer `proxy_http_version 1.1`, en-têtes `Upgrade` et `Connection`, timeouts adaptés.

**Q8 : Disque plein sur uploads.**  
R8 : Alerter avant saturation ; archiver ou purger selon **politique de rétention** ; ne pas supprimer au hasard sans validation métier.

---

## B.3 Évolutions courantes

| Besoin métier | Zone code typique |
|---------------|---------------------|
| Nouveau champ ticket | Entité + migration + template détail + service |
| Nouveau rapport PDF | Service dédié + contrôleur + template ou vue |
| Nouvelle notification | `NotificationService` + émetteur métier + éventuel STOMP |
| Nouvelle intégration SSO | `SecurityFilterChain`, provider OAuth2/LDAP |

---

## B.4 Checklist release (équipe projet)

1. Version Flyway testée sur clone prod anonymisé.  
2. Tests automatiques verts.  
3. Revue sécurité sur endpoints nouveaux.  
4. Changelog métier rédigé.  
5. Plan de rollback validé.  
6. Communication helpdesk.

---

## B.5 Glossaire anglais → français (SI)

| EN (code / doc) | FR (usage documentaire) |
|-------------------|-------------------------|
| Dashboard | Tableau de bord |
| Ticket | Ticket / dossier |
| Feature | Fonctionnalité |
| Deployment | Déploiement |
| Rollback | Retour arrière |
| UAT | Recette utilisateur |

---

*Fin de l’annexe B.*
