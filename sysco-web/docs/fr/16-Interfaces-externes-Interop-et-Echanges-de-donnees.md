# Chapitre 16 — Interfaces externes, interopérabilité et échanges de données

SYSCO Web ne fonctionne pas en vase clos : il **s’appuie** sur une base de données, éventuellement sur un **annuaire** ou un fournisseur d’identité, sur un **stockage fichiers**, et parfois sur des **services tiers** (courriel, SMS, bus d’entreprise). Ce chapitre cadre ces interactions du point de vue **architecture**, **sécurité** et **exploitation**, sans prétendre documenter chaque connecteur spécifique à un site client.

---

## 16.1 Vue d’ensemble des flux

Les flux sortants typiques incluent :

- **JDBC** vers Oracle (ou moteur alternatif en développement) ;
- **SMTP** ou API de messagerie pour les notifications ;
- accès **fichiers** (partage SMB/NFS, objet S3) pour les pièces jointes ;
- **WebSocket** vers les navigateurs clients via le broker intégré ou externe.

Les flux entrants incluent :

- **HTTP/HTTPS** depuis utilisateurs et reverse proxy ;
- **webhooks** ou callbacks si l’application en expose ;
- éventuellement **messages** asynchrones si un module d’intégration est branché sur une file.

Un **schéma d’architecture logique** (voir chapitres 1 et 10) doit lister chaque flèche avec protocole, authentification et sensibilité des données.

---

## 16.2 Base de données relationnelle

### 16.2.1 Rôle

La base centralise l’**état métier** : utilisateurs, permissions, tickets, journaux d’audit, paramètres. Les **transactions** garantissent la cohérence lors des opérations multi-tables.

### 16.2.2 Comptes techniques

Le compte JDBC de l’application doit suivre le **principe du moindre privilège** : pas de `DBA` en production, droits limités aux **schémas** nécessaires. Les migrations Flyway peuvent utiliser un compte **dédié** avec droits DDL si séparé du runtime applicatif.

### 16.2.3 Haute disponibilité

Selon l’infrastructure : **Oracle RAC**, **Data Guard**, réplication logique, ou bascule manuelle. Le runbook doit préciser la **procédure** de reprise et les **tests** de bascule annuels.

---

## 16.3 Annuaire et fédération d’identité

Si l’authentification est **externalisée** (LDAP, Active Directory, OIDC/SAML), documenter :

- les **attributs** mappés vers le profil applicatif ;
- la **fréquence** de synchronisation ou le mode just-in-time ;
- le comportement en cas d’**indisponibilité** de l’IdP (mode dégradé ou page d’erreur explicite).

Les **groupes** annuaire peuvent alimenter les **rôles** Spring ; veiller à la **cohérence** avec les permissions fines stockées en base.

---

## 16.4 Messagerie et notifications

Les envois **email** doivent respecter les **politiques** anti-spoofing (SPF, DKIM, DMARC) côté infrastructure messagerie. L’application ne doit pas **énumérer** des listes d’emails sensibles dans les logs.

Pour le **temps réel** navigateur, le protocole WebSocket traverse souvent des **proxies** : configuration des **timeouts** et du **upgrade** HTTP indispensable.

---

## 16.5 Stockage de fichiers

### 16.5.1 Modèle d’accès

Les fichiers peuvent être stockés sur **volume local** (simple mais peu scalable), **SAN**, ou **objet cloud**. Chaque choix impacte **sauvegarde**, **reprise** et **performance**.

### 16.5.2 Sécurité

Contrôler les **extensions** et types MIME, limiter la **taille**, scanner les **malwares** selon la politique. Les URL de téléchargement doivent être **autorisées** par la même logique que l’écran métier (pas de liens devinables non protégés).

---

## 16.6 API et intégrations batch

Si SYSCO Web expose des **endpoints REST** pour des systèmes externes, documenter :

- **authentification** (mutual TLS, API keys, OAuth2) ;
- **pagination** et limites de débit ;
- **versioning** (`/v1/`) pour éviter de casser les consommateurs.

Les **jobs** batch qui **alimentent** ou **extraient** des données doivent être **idempotents** lorsque possible et **fenêtrés** pour ne pas saturer la base aux heures ouvrées.

---

## 16.7 Horloge et fuseaux horaires

Les horodatages **audit** doivent être **cohérents** : stocker en **UTC** en base et convertir à l’affichage, ou documenter le fuseau unique si l’organisation est mono-site. Les décalages créent des **incohérences** dans les rapports de connexion et les SLA.

---

## 16.8 Résilience et timeouts

Tout appel réseau externe doit avoir des **timeouts** et une **politique de retry** prudente (éviter les tempêtes de réessais). Pour les **circuits ouverts** (pattern circuit breaker), surveiller les métriques de rejet.

---

## 16.9 Contrats de données

Maintenir un **dictionnaire** des champs échangés avec les systèmes voisins : nom logique, type, sensibilité (donnée personnelle, secret métier), fréquence de mise à jour. Ce dictionnaire facilite les **analyses d’impact RGPD** et les migrations.

---

## 16.10 Environnements multiples

Séparer au minimum **développement**, **recette**, **préproduction** et **production**. Les **secrets** et URLs ne doivent pas être **recyclés** entre environnements de manière incontrôlée. La recette peut contenir des données anonymisées ; la production contient les **vraies** données sous contrôle d’accès strict.

---

## 16.11 Observabilité des intégrations

Pour chaque interface externe, définir :

- **SLI** (indicateur de niveau de service), par exemple taux d’erreur SMTP ;
- **SLO** (objectif), par exemple moins de 0,1 % d’échecs sur une fenêtre glissante ;
- **alertes** lorsque le SLO est menacé.

---

## 16.12 Évolution et décommissionnement

Lorsqu’une interface est **remplacée**, planifier une **période de chevauchement**, mettre à jour la documentation et **retirer** le code mort. Les **endpoints dépréciés** doivent annoncer une **date de fin** aux consommateurs.

---

## 16.13 Synthèse

Les interfaces externes sont souvent le **point de rupture** des incidents « tout est lent » ou « erreur intermittente ». Une documentation **à jour**, des **tests de contrat** et une **supervision** dédiée réduisent le temps de diagnostic et renforcent la confiance des partenaires SI.

---

*Fin du chapitre 16.*
