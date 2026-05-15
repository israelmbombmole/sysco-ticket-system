# Annexe A — Description technique par module métier (vue code / SI)

Ce document **annexe** détaille, pour **chaque entrée du menu principal** (`NavigationRegistry`), ce que le module représente **fonctionnellement** et **techniquement** : ressources Web typiques, services associés (indicatifs), et articulation avec le reste du système. Il complète le chapitre 3 et la matrice de sécurité du chapitre 2.

---

## A.1 Tableau de bord — `/app`

**Fonction métier :** synthèse post-authentification ; indicateurs et liens d’accès rapide.  
**Technique :** `AppController`, vue `dashboard.html`. S’appuie sur des services de métriques (ex. `DashboardMetricsService` selon version).  
**Données :** agrégations lecture seule sur tickets, courrier, notifications — selon implémentation.  
**Risques :** sur-consommation SQL si chaque tuile déclenche des requêtes lourdes ; optimiser par vues matérialisées ou cache court si nécessaire.

---

## A.2 Saisie des données — `/app/data-entry`

**Fonction métier :** capture structurée de données opérationnelles (grilles).  
**Technique :** `DataEntryController`, `DataEntryService`, template `data-entry.html`.  
**Persistance :** tables dédiées aux campagnes / jeux de données (voir migrations).  
**Sécurité :** permission `DATA_ENTRY_*`.  
**Intégration :** imports Excel éventuels — valider encodage et colonnes.

---

## A.3 Portail courrier — `/app/courier`

**Fonction métier :** opérations terrain sur objets physiques (réception, transfert, livraison).  
**Technique :** `CourierPortalController`, `CourierPortalService`, `courier.html`.  
**Persistance :** entités colis / mouvements (noms exacts dans le paquet `domain`).  
**Sécurité :** `PHYSICAL_COURIER` ou rôles listés dans `WebSyscoPermissions`.  
**Point clé :** cohérence **numérique / physique**.

---

## A.4 Gestion courrier — `/app/courier-management`

**Fonction métier :** supervision des flux, désengorgement, pilotage.  
**Technique :** `CourierManagementController`, `courier-management.html`.  
**Sécurité :** basée sur rôle (directeur, inspecteur, secrétaire, sous-directeur, admin).  
**Différence portail :** vues plus « macro », filtres directionnels.

---

## A.5 Gestion des données — `/app/data-management`

**Fonction métier :** maintenance de jeux de données, imports de masse, gouvernance.  
**Technique :** `DataManagementController`, `data-management.html`, services d’administration données.  
**Risque :** erreurs massives — privilégier environnement de **préprod** pour tests d’import.

---

## A.6 Partage des données — `/app/data-share`

**Fonction métier :** partage contrôlé de fichiers / extraits.  
**Technique :** `DataShareController`, `DataShareService`, `data-share.html`.  
**Sécurité :** `DATASHARE_*`.  
**Audit :** corrélation possible avec `file-share-audit` pour investigations.

---

## A.7 Mon activité — `/app/my-activity`

**Fonction métier :** historique personnel des actions récentes.  
**Technique :** `MyActivityController`, `my-activity.html`, fusions `my-activity-merge-ticket.html`.  
**Performance :** pagination obligatoire sur grands historiques.

---

## A.8 Mon travail — `/app/my-work`

**Fonction métier :** file d’attente personnelle (tickets / tâches).  
**Technique :** `MyWorkController`, `my-work.html`, `my-work-merge-ticket.html`.  
**Sécurité :** `MY_WORK` ou `MY_ACTIVITY` pour l’entrée menu.

---

## A.9 Suivi des tickets — `/app/ticket-monitoring`

**Fonction métier :** supervision multi-dossiers, filtres management.  
**Technique :** `TicketMonitoringController`, `ticket-monitoring.html`.  
**Utilisateurs :** chefs de service, contrôleurs — selon droits.

---

## A.10 Gestion des tickets — `/app/ticket-management`

**Fonction métier :** cycle de vie complet d’un dossier.  
**Technique :** `TicketManagementController`, listes + `ticket-management-detail.html`.  
**Services :** `TicketManagementService`, timeline, pièces jointes, clôture guidée (étapes internes au template).  
**Complexité :** cœur métier — tests de non-régression prioritaires.

---

## A.11 Gestion du partage de fichiers — `/app/file-share-management`

**Fonction métier :** file d’attente administrative des partages sensibles.  
**Technique :** `FileShareManagementController`, filtres d’accès dédiés.  
**Sécurité :** réservé **ADMIN** / **SUPER_ADMIN** pour le **menu** ; vérifier aussi méthodes.

---

## A.12 Gestion des utilisateurs — `/app/user-management`

**Fonction métier :** comptes, rôles, permissions.  
**Technique :** `UserManagementController`, `user-management.html`.  
**Sécurité :** `USER_MANAGEMENT_*`.  
**Bonnes pratiques :** journaliser les changements sensibles (hors périmètre code documenté ici si non implémenté).

---

## A.13 Agenda — `/app/agenda` (et congés `/app/leave-management`)

**Fonction métier :** planning, absences, validations.  
**Technique :** `AgendaController`, `agenda.html`.  
**Sécurité :** `LEAVE_MANAGEMENT` ou `USER_MANAGEMENT`.

---

## A.14 Journal des connexions — `/app/login-audit`

**Fonction métier :** audit sécurité des authentifications.  
**Technique :** `LoginAuditController`, `login-audit.html`.  
**Données :** table(s) d’événements — export CSV possible.

---

## A.15 Audit partage fichiers — `/app/file-share-audit`

**Fonction métier :** traçabilité des partages.  
**Technique :** `FileShareAuditController`, `file-share-audit.html`.

---

## A.16 Créer un ticket — `/app/create-ticket`

**Fonction métier :** assistant de création de dossier.  
**Technique :** `CreateTicketController`, `create-ticket.html`.

---

## A.17 Planificateur de tâches — `/app/job-scheduler`

**Fonction métier :** rappels et échéances.  
**Technique :** `JobSchedulerController`, `job-scheduler.html`, traitement `AutomatedJobProcessingService`, déclencheur planifié.

---

## A.18 Missions — `/app/missions`

**Fonction métier :** missions terrain, ordres, logistique.  
**Technique :** `MissionsController`, `missions.html`, `mission-detail.html`, génération documentaire éventuelle.

---

## A.19 Ma garde — `/app/my-shift`

**Fonction métier :** présence / gardes.  
**Technique :** `MyShiftController`, `my-shift.html`.

---

## A.20 Modules hors menu principal (rappel)

- **Chat** — `/app/chat` — `ChatController`.  
- **Notifications** — `/app/notifications` — `NotificationsController`.  
- **Aide** — POST `/app/help/tutorial-completed` — `HelpController`.

---

*Fin de l’annexe A.*
