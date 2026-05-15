# Manuel utilisateur SYSCO

**Version document :** à associer à la version logicielle déployée  
**Langue :** français  

Ce guide décrit l’utilisation de l’application **SYSCO** (bureau JavaFX) : navigation, écrans principaux et bonnes pratiques. Les **captures d’écran** sont attendues dans le dossier `images/` (voir `images/LISEZMOI_CAPTURES.md`) : remplacez ou ajoutez les fichiers PNG pour illustrer chaque section.

---

## Table des matières

1. [À quoi sert SYSCO ?](#1-à-quoi-sert-sysco-)
2. [Rôles, permissions et ce que vous voyez à l’écran](#2-rôles-permissions-et-ce-que-vous-voyez-à-lécran)
3. [Démarrage et connexion](#3-démarrage-et-connexion)
4. [Présentation de la fenêtre principale](#4-présentation-de-la-fenêtre-principale)
5. [Tableau de bord](#5-tableau-de-bord)
6. [Saisie et gestion des données](#6-saisie-et-gestion-des-données)
7. [Partage de données (DataShare)](#7-partage-de-données-datashare)
8. [Mon activité](#8-mon-activité)
9. [Mon travail](#9-mon-travail)
10. [Tickets : suivi, gestion et création](#10-tickets--suivi-gestion-et-création)
11. [Gestion du partage de fichiers](#11-gestion-du-partage-de-fichiers)
12. [Gestion des utilisateurs et congés](#12-gestion-des-utilisateurs-et-congés)
13. [Journal de connexion et audit du partage](#13-journal-de-connexion-et-audit-du-partage)
14. [Planificateur de tâches (Job Scheduler)](#14-planificateur-de-tâches-job-scheduler)
15. [Missions de terrain et compte rendu](#15-missions-de-terrain-et-compte-rendu)
16. [Notifications, langue et fin de session](#16-notifications-langue-et-fin-de-session)
17. [Foire aux questions et dépannage](#17-foire-aux-questions-et-dépannage)
18. [Annexe : index des captures recommandées](#18-annexe--index-des-captures-recommandées)

---

## 1. À quoi sert SYSCO ?

SYSCO est un outil métier pour :

- suivre des **tickets** et des **tâches** ;
- gérer des **données** et des **partages de fichiers** ;
- consulter **l’activité** et **Mon travail** (tickets et missions qui vous concernent) ;
- pour les profils habilités : **utilisateurs**, **congés**, **audits**, **missions de terrain**, **automatisation** (planificateur).

L’**administrateur** configure les **permissions** de chaque compte : vous ne verrez dans le menu latéral **que** les fonctions auxquelles vous avez droit.

---

## 2. Rôles, permissions et ce que vous voyez à l’écran

### 2.1 Rôle et permissions

- Votre **rôle** (ex. DIRECTEUR, INSPECTEUR, VÉRIFICATEUR…) détermine le **tableau de bord** par défaut et certaines règles métier.
- Les **permissions** (cases cochées dans votre fiche utilisateur) contrôlent chaque entrée du menu : *Saisie des données*, *Gestion des tickets*, *Missions*, etc.
- Le compte **ADMIN** dispose de l’ensemble des écrans sans restriction de permissions.

### 2.2 Si un bouton du menu manque

C’est normal : demandez à un administrateur l’ajout de la permission correspondante si votre métier l’exige.

---

## 3. Démarrage et connexion

### 3.1 Lancer l’application

Démarrez l’application selon le mode prévu par votre organisation (raccourci, script ou ligne de commande fournie par l’équipe technique).

### 3.2 Écran de connexion

![Page de connexion SYSCO](images/01-connexion.png)

**À faire :**

1. Saisissez votre **identifiant** et votre **mot de passe**.
2. Cliquez sur le bouton de connexion.
3. En cas d’erreur, vérifiez le clavier (majuscules), l’identifiant, puis contactez l’administrateur si le problème persiste.

**Bonnes pratiques :** ne partagez jamais votre mot de passe ; changez-le régulièrement via la fonction *Changer le mot de passe* lorsqu’elle est disponible.

---

## 4. Présentation de la fenêtre principale

![Vue d’ensemble : en-tête, menu latéral, zone centrale](images/02-layout.png)

### 4.1 En-tête (barre du haut)

| Zone | Usage |
|------|--------|
| Titre / logo | Identification de l’application |
| **Utilisateur : …** | Compte connecté |
| **Cloche** | Ouverture du volet **notifications** |
| **Drapeaux** | Bascule **français** / **anglais** (libellés de l’interface) |

### 4.2 Menu latéral (barre bleue)

Liste des modules : **Tableau de bord**, **Saisie des données**, **Tickets**, **Missions**, etc. Seuls les boutons autorisés par vos permissions sont visibles.

### 4.3 Zone centrale

Affiche l’écran du module choisi (tableaux, formulaires, onglets).

### 4.4 Déconnexion

Le bouton **Déconnexion** (souvent en bas du menu) termine la session. Utilisez-le lorsque vous quittez votre poste.

---

## 5. Tableau de bord

![Exemple de tableau de bord administrateur ou directeur](images/03-dashboard-admin.png)

**Accès :** bouton **Tableau de bord** (si votre profil a l’accès « dashboard »).

**À faire :**

1. Cliquez sur **Tableau de bord** pour afficher les indicateurs et raccourcis prévus pour votre rôle.
2. Utilisez les liens ou boutons proposés pour ouvrir un ticket, une liste ou une action métier.

*Remarque :* le contenu exact varie selon le rôle (directeur, agent, utilisateur interne, assistant, etc.).

---

## 6. Saisie et gestion des données

### 6.1 Saisie des données

![Saisie des données](images/04-saisie-donnees.png)

**Accès :** **Saisie des données** (permission `DATA_ENTRY`).

**Guide :**

1. Ouvrez le module depuis le menu.
2. Renseignez les champs demandés selon les règles de votre entité.
3. Enregistrez. En cas de message d’erreur, lisez le texte affiché (champ obligatoire, format, règle métier).

### 6.2 Gestion des données

![Gestion des données](images/05-gestion-donnees.png)

**Accès :** **Gestion des données** (permission `DATA_MANAGEMENT`).

**Guide :** consultation, modification ou actions de gestion sur les enregistrements selon les boutons disponibles à l’écran. Respectez la procédure interne avant toute suppression ou modification massive.

---

## 7. Partage de données (DataShare)

![Partage de données](images/06-datashare.png)

**Accès :** **Partage de données** (permission `DATASHARE`).

**À faire :**

1. Ouvrez **Partage de données**.
2. Suivez les étapes proposées (sélection de fichiers ou de jeux de données, destinataires, validation).
3. Conservez une trace des références (nom, date) si votre organisation l’exige.

---

## 8. Mon activité

![Mon activité](images/07-mon-activite.png)

**Accès :** **Mon activité** (permission `MY_ACTIVITY`).

**Usage :** consulter l’historique ou la synthèse d’actions vous concernant (selon paramétrage). Utilisez les filtres ou onglets s’ils sont présents pour restreindre la période ou le type d’événement.

---

## 9. Mon travail

![Mon travail — tickets et missions](images/08-mon-travail.png)

**Accès :** **Mon travail** (permissions `MY_WORK` et/ou `MY_ACTIVITY` selon configuration).

**À faire :**

1. Ouvrez **Mon travail** pour voir les **tickets** et **missions** où vous êtes impliqué (responsable, participant, etc.).
2. Cliquez sur une ligne ou sur **Voir** / **Ouvrir** pour accéder au détail.
3. Pour une **mission**, un double-clic ou l’action prévue peut ouvrir l’écran **Missions** avec la mission sélectionnée.

---

## 10. Tickets : suivi, gestion et création

### 10.1 Suivi des tickets (monitoring)

![Suivi des tickets](images/09-suivi-tickets.png)

**Accès :** **Suivi des tickets** (`TICKET_MONITORING`).

**Guide :** parcourez la liste, appliquez les filtres disponibles, ouvrez un ticket pour voir le détail, l’historique et les pièces jointes éventuelles.

### 10.2 Gestion des tickets

![Gestion des tickets](images/10-gestion-tickets.png)

**Accès :** **Gestion des tickets** (`TICKET_MANAGEMENT`).

**Guide :** traitement des tickets (assignation, changement de statut, édition selon vos droits). Les champs modifiables dépendent du type de ticket et de votre rôle.

### 10.3 Création de ticket

![Création de ticket](images/11-creation-ticket.png)

**Accès :** **Créer un ticket** (`CREATE_TICKET`).

**À faire :**

1. Renseignez le **titre**, la **description**, la **priorité** et les champs obligatoires.
2. Joignez des fichiers si nécessaire.
3. Validez la création et notez le **numéro** ou le **code** du ticket pour le suivi.

### 10.4 Détail d’un ticket

![Fiche détail ticket](images/12-detail-ticket.png)

**Usage :** lecture des informations, commentaires, pièces jointes, chronologie. Certaines actions (fermer, réassigner) peuvent être réservées à des rôles spécifiques.

---

## 11. Gestion du partage de fichiers

![Gestion du partage de fichiers](images/13-gestion-partage-fichiers.png)

**Accès :** **Gestion du partage de fichiers** (`FILE_SHARE_MANAGEMENT`).

**Guide :** création ou gestion des partages, droits d’accès, et suivi des dossiers partagés selon l’interface. En cas de **code OTP** ou de validation à deux étapes, suivez les instructions affichées à l’écran.

---

## 12. Gestion des utilisateurs et congés

### 12.1 Utilisateurs

![Gestion des utilisateurs](images/14-gestion-utilisateurs.png)

**Accès :** **Gestion des utilisateurs** (`USER_MANAGEMENT`).

**À faire :**

1. Recherchez un utilisateur ou créez un compte selon la procédure interne.
2. Renseignez rôle, direction, e-mail (obligatoire pour certains rôles) et **permissions** (cases à cocher).
3. Enregistrez. Communiquez les identifiants par un canal sécurisé.

### 12.2 Congés et vacances

![Congés et vacances](images/15-conges-vacances.png)

**Accès :** **Congés et vacances** (lié à la gestion des utilisateurs pour les profils autorisés).

**Guide :** déclarez des absences (congés, vacances, mission liée le cas échéant), consultez le calendrier ou les listes. Les règles d’**indisponibilité** peuvent impacter l’assignation de tickets : respectez les consignes RH.

---

## 13. Journal de connexion et audit du partage

### 13.1 Journal de connexion

![Journal de connexion](images/16-journal-connexion.png)

**Accès :** **Journal de connexion** (`LOGIN_AUDIT`).

**Usage :** consultation des connexions (qui, quand). Utile pour l’audit de sécurité ; ne modifiez pas ces journaux.

### 13.2 Audit du partage de fichiers

![Audit partage de fichiers](images/17-audit-partage.png)

**Accès :** **Audit du partage de fichiers** (`FILE_SHARE_AUDIT`).

**Usage :** tracer les opérations sur les partages (consultation, téléchargement, etc., selon implémentation).

---

## 14. Planificateur de tâches (Job Scheduler)

![Planificateur de tâches](images/18-job-scheduler.png)

**Accès :** **Planificateur de tâches** (`JOB_SCHEDULER`, ou compte **ADMIN**).

**À faire :**

1. Ouvrez le module ; si un message *Accès refusé* apparaît, vous n’avez pas la permission.
2. Consultez la liste des tâches planifiées et leur statut.
3. Ne créez ou ne modifiez des planifications **qu’** en accord avec l’administrateur métier ou technique.

---

## 15. Missions de terrain et compte rendu

![Missions — liste et formulaire](images/19-missions.png)

**Accès :** **Missions** (`MISSIONS`).

Cet écran regroupe la **liste des missions** à gauche (ou en haut selon la taille de fenêtre) et un panneau de détail avec **trois onglets**.

### 15.1 Liste et filtres

**À faire :**

1. Utilisez le **filtre de statut**, la **recherche** et les **dates** si disponibles.
2. Cliquez sur **Actualiser** pour recharger la liste.
3. Sélectionnez une ligne pour afficher le détail à droite.
4. **Double-clic** sur une ligne : ouverture pratique sur l’onglet **Mission** (selon version).

### 15.2 Onglet « Mission » (détail de la mission)

**Contenu typique :** code, titre, lieu, dates, statut, **responsable**, description, objectifs, **participants** (sélection multiple avec Ctrl/Cmd + clic).

**Pièces jointes (mission) :** section **Pièces jointes (mission)** — Ajouter / Ouvrir / Retirer.

**Qui peut modifier ?**  
**Créateur** de la mission ou **administrateur** : enregistrement, suppression de la mission, participants, pièces jointes « mission ».

**Nouvelle mission :** bouton **Nouvelle mission** (visible seulement pour les rôles autorisés à créer : typiquement admin, directeur, sous-directeur, inspecteur). Saisissez au minimum un **titre**, puis **Enregistrer**.

### 15.3 Onglet « Ordre de mission »

**Contenu :** référence, date d’émission, signataire, texte de l’ordre, **pièces jointes (ordre de mission)**.

**Qui peut modifier ?**  
Même règle que l’onglet Mission : **créateur** ou **administrateur**.

### 15.4 Onglet « Compte rendu »

**Contenu :** texte du rapport, date de transmission éventuelle, bouton pour **marquer le rapport comme transmis**, **pièces jointes** du compte rendu.

**Qui peut modifier le texte et les PJ du rapport ?**

- **Administrateur** : toujours.
- Sinon : l’**auteur désigné** du compte rendu (enregistré automatiquement à la **première sauvegarde** d’un texte non vide).
- **Tant qu’aucun auteur n’est enregistré** : le **créateur** de la mission ou le **responsable (lead)** peut commencer le compte rendu ; la première sauvegarde **fixe** l’auteur.
- Les **participants** qui ne sont ni créateur ni responsable ne peuvent **pas** modifier le compte rendu une fois l’auteur défini.

**Marquer « rapport transmis » :**  
**Auteur** du compte rendu (ou **créateur** / **responsable** tant qu’aucun auteur n’est défini), ou **administrateur**.

### 15.5 Bonnes pratiques missions

- Enregistrez la mission avant d’ajouter des pièces jointes.
- Gardez une cohérence entre **ordre de mission** (officiel) et **compte rendu** (retour de terrain).
- Si vous ne voyez pas un bouton **Enregistrer** actif, c’est que votre rôle est en **lecture seule** sur cette partie : contactez le créateur ou l’administrateur.

---

## 16. Notifications, langue et fin de session

### 16.1 Notifications

![Liste des notifications](images/20-notifications.png)

**À faire :** cliquez sur la **cloche** ; ouvrez une notification ; certaines ouvrent directement l’écran concerné (ticket, mission, etc.).

### 16.2 Langue

Cliquez sur **FR** ou **EN** dans la barre supérieure pour changer la langue de l’interface (les données saisies par les utilisateurs ne sont pas traduites automatiquement).

### 16.3 Mot de passe

Si votre organisation propose **Changer le mot de passe** depuis le tableau de bord ou le menu profil, utilisez un mot de passe long et unique.

### 16.4 Déconnexion

Cliquez sur **Déconnexion** avant de quitter le poste partagé.

---

## 17. Foire aux questions et dépannage

| Problème | Piste de solution |
|----------|-------------------|
| Je ne vois pas un menu | Vérifier vos **permissions** avec l’administrateur |
| Accès refusé au planificateur | Demander la permission **JOB_SCHEDULER** |
| Impossible d’enregistrer une mission | Vérifier que vous êtes **créateur** ou **admin** ; titre obligatoire |
| Compte rendu en lecture seule | Vous n’êtes pas l’**auteur** désigné ; contacter l’auteur ou l’admin |
| Fichier joint introuvable | Le fichier a pu être déplacé ou supprimé sur le serveur / poste |
| Erreur base de données | Noter le message et contacter le support technique |

---

## 18. Annexe : index des captures recommandées

Complétez le dossier `images/` avec les fichiers suivants pour un manuel entièrement illustré.

| Fichier | Description de la capture |
|---------|---------------------------|
| `01-connexion.png` | Écran de connexion SYSCO |
| `02-layout.png` | Fenêtre principale avec menu et zone centrale vide ou tableau de bord |
| `03-dashboard-admin.png` | Tableau de bord (ex. admin ou directeur) |
| `04-saisie-donnees.png` | Module Saisie des données |
| `05-gestion-donnees.png` | Module Gestion des données |
| `06-datashare.png` | Partage de données |
| `07-mon-activite.png` | Mon activité |
| `08-mon-travail.png` | Mon travail |
| `09-suivi-tickets.png` | Suivi des tickets |
| `10-gestion-tickets.png` | Gestion des tickets |
| `11-creation-ticket.png` | Création de ticket |
| `12-detail-ticket.png` | Détail d’un ticket |
| `13-gestion-partage-fichiers.png` | Gestion du partage de fichiers |
| `14-gestion-utilisateurs.png` | Gestion des utilisateurs |
| `15-conges-vacances.png` | Congés et vacances |
| `16-journal-connexion.png` | Journal de connexion |
| `17-audit-partage.png` | Audit du partage de fichiers |
| `18-job-scheduler.png` | Planificateur de tâches |
| `19-missions.png` | Missions (liste + onglets Mission / Ordre / Compte rendu) |
| `20-notifications.png` | Volet ou liste des notifications |

---

*Fin du manuel utilisateur SYSCO (français). Pour la documentation technique (architecture, base de données, déploiement), voir `DOCUMENTATION_TECHNIQUE_SYSCO_FR.md` à la racine du dossier `docs/` si elle est présente dans votre livrable.*
