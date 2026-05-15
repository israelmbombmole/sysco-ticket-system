# Chapitre 5 — Temps réel, notifications, internationalisation et visite guidée

Ce chapitre couvre les mécanismes **non strictement synchrones** de l’interface : **WebSocket/STOMP**, **notifications**, **chat**, **fichiers JavaScript** associés, ainsi que l’**internationalisation** (i18n) et la **visite guidée** (Driver.js).

---

## 5.1 Pourquoi du temps réel dans une application « classique » ?

Même si SYSCO Web reste une application **centrée sur le rendu serveur**, certains événements bénéficient d’une **notification quasi immédiate** :

- arrivée d’un **message chat** ;  
- nouvelle **notification métier** (ticket, rappel planificateur, etc.) ;  
- mise à jour de **badges** (nombre de non lus) dans l’en-tête.

Sans WebSocket, l’utilisateur devrait **rafraîchir** la page pour voir ces changements. Le canal temps réel améliore la **réactivité perçue** tout en conservant la persistance en base comme **source de vérité**.

---

## 5.2 Stack temps réel côté navigateur

Le gabarit charge typiquement :

1. **SockJS** — couche de compatibilité transport (fallback si WebSocket brut indisponible).  
2. **STOMP** (via `@stomp/stompjs`) — protocole de messages au-dessus du socket.  
3. **`realtime-hub.js`** — script applicatif qui établit la session, souscrit aux destinations, et met à jour l’UI (badges, toasts éventuels).

Les détails d’URL du **broker**, du **point de terminaison** WebSocket et du **préfixe** d’application relèvent de la **configuration Spring** (`@EnableWebSocketMessageBroker`, `StompEndpointRegistry`, etc.) — à lire dans les classes `config` du projet pour la version déployée.

---

## 5.3 Modèle de publication / abonnement

En STOMP, le serveur **publie** sur des destinations (topics ou files) et le client **s’abonne**. Pour les messages **utilisateur-spécifiques**, Spring Security permet souvent des destinations de type `/user/{username}/queue/...` afin qu’un utilisateur ne reçoive **pas** les notifications d’un autre.

**Bonnes pratiques :**

- ne jamais faire confiance au client pour **filtrer** des données sensibles : le serveur ne doit publier que ce que l’utilisateur a le droit de voir ;  
- prévoir une **reconnexion** si le socket tombe (réseau instable) ;  
- limiter le **bruit** (trop de messages) pour ne pas saturer l’UI.

---

## 5.4 Notifications persistantes

Les notifications sont en général :

1. **persistées** en base (`NotificationItem` ou table équivalente) pour pouvoir les lister sur `/app/notifications` ;  
2. **poussées** sur le canal temps réel pour mise à jour immédiate du badge.

Le service de notifications (`NotificationService`) centralise la création et la pagination. Les contrôleurs exposent les vues de liste et les actions « marquer comme lu », « supprimer », etc.

---

## 5.5 Chat interne

Le chat permet des **conversations** entre utilisateurs authentifiés. Les messages sont stockés en base (tables dédiées, voir migrations Flyway). Le compteur de **non lus** alimente le badge en en-tête et sur le bouton flottant.

**Point d’attention :** le contenu du chat peut constituer des **données personnelles** ou opérationnelles sensibles — la politique de **rétention** et d’**accès** doit être définie par l’organisme.

Visiter `/app/chat` peut **réinitialiser** le compteur de non lus (marquer comme vus) selon la logique du `ChatService` et du `NavigationAdvice`.

---

## 5.6 Internationalisation (i18n)

### 5.6.1 Fichiers de messages

Les libellés sont externalisés dans :

- `messages.properties` (souvent clés en anglais par défaut) ;  
- `messages_fr.properties` (traduction française).

Thymeleaf accède aux messages via `#{cle}` ou via `MessageSource` injecté côté Java.

### 5.6.2 Locale fixe

Une configuration peut forcer une **locale institutionnelle** (par exemple `Locale.FRENCH`) pour que l’interface soit homogène sur tout le parc — utile lorsque l’anglais n’est pas la langue de travail.

### 5.6.3 Ajout de clés

Toute nouvelle étiquette d’écran doit avoir :

- une entrée dans `messages.properties` ;  
- une entrée **symétrique** dans `messages_fr.properties` ;  
- une relecture métier pour la teneur exacte (terminologie douanière / interne).

---

## 5.7 Visite guidée (Driver.js)

### 5.7.1 Objectif

La visite guidée présente les zones de l’interface (menu, en-tête, modules) pour l’**onboarding**. Les étapes sont décrites par un **JSON** généré côté serveur (`GuidedTourService.buildTourPayloadJson`).

### 5.7.2 Construction des étapes

Le service :

1. ajoute des étapes génériques (bienvenue, en-tête, menu) ;  
2. pour chaque `NavItem` **visible** pour l’utilisateur, ajoute une étape pointant vers `#tour-nav-{index}` ;  
3. termine par une étape sur le bouton d’aide.

Les textes des étapes modules proviennent des clés `tour.mod.*` dans les fichiers de messages.

### 5.7.3 Chargement de Driver.js

Driver.js est chargé depuis un **CDN** (fichier IIFE). Le script applicatif `sysco-guided-tour.js` :

- résout le **global** correct (`window.driver.js.driver` pour la version 1.x) ;  
- lit le JSON depuis un **textarea** (éviter la coupure par `</script>` dans les contenus) ;  
- attache le clic sur le bouton d’aide ;  
- envoie éventuellement un **POST** `/app/help/tutorial-completed` à la fin (CSRF).

### 5.7.4 Dépendance CDN

Si le réseau institutionnel **bloque** le CDN, la visite guidée ne démarrera pas : prévoir un **hébergement interne** des fichiers `driver.js` et `driver.css` en production critique.

---

## 5.8 Cohérence fonctionnelle temps réel / HTTP

Les actions **métier** (clôturer un ticket, enregistrer un colis) passent par **HTTP + service + transaction**. Le temps réel **annonce** un changement ; il ne remplace pas la **validation serveur**. En cas de désaccord (message reçu mais état obsolète), l’utilisateur rafraîchit la page ou rouvre le dossier.

---

## 5.9 Observabilité

Pour diagnostiquer les problèmes WebSocket :

- activer les **logs** Spring WebSocket / messaging en préproduction ;  
- vérifier les **timeouts** du reverse proxy (certains proxies ferment les connexions longues trop tôt) ;  
- valider le support **sticky sessions** si plusieurs nœuds d’application (selon configuration du broker).

---

## 5.10 Synthèse

| Composant | Rôle |
|-----------|------|
| SockJS + STOMP | Transport et cadrage des messages |
| `realtime-hub.js` | Côté client applicatif |
| `NotificationService` | Persistance et logique métier des alertes |
| `ChatService` | Conversations et non lus |
| `GuidedTourService` | Payload JSON pour Driver.js |
| Fichiers `messages_*.properties` | Libellés et textes de la visite |

---

*Fin du chapitre 5.*
