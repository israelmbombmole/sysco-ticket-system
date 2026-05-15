# Chapitre 17 — Sécurité approfondie, conformité et continuité d’activité

Ce chapitre rassemble les **exigences transverses** de sécurité et de résilience qui dépassent la simple authentification applicative : protection des données, journalisation d’audit, conformité réglementaire et **plans de continuité**. Il sert de support aux **dossiers d’homologation** et aux échanges avec les RSSI.

---

## 17.1 Classification des données

Avant de durcir les systèmes, **classifier** les données manipulées par SYSCO Web :

- **Publiques** — diffusion sans risque pour l’organisation ;
- **Internes** — réservées au personnel ;
- **Confidentielles** — atteinte à l’organisation ou aux personnes si divulguées ;
- **Secrets** — mots de passe, clés cryptographiques, données d’authentification forte.

Les mesures techniques (chiffrement au repos, masquage, rétention) **découlent** de cette classification.

---

## 17.2 Chiffrement en transit

Toute communication **navigateur ↔ serveur** et **serveur ↔ base** en production doit utiliser **TLS** (ou équivalent) avec des versions et algorithmes **conformes** à la politique de l’organisation. Les certificats doivent être **renouvelés** avant expiration (automatisation ACME ou processus manuel traçable).

---

## 17.3 Chiffrement au repos

Selon la sensibilité : **TDE** base de données, chiffrement de volume, ou **champs applicatifs** chiffrés pour les attributs ultra-sensibles. La **gestion des clés** (HSM, coffre de secrets) est critique : une clé exposée invalide toute la protection.

---

## 17.4 Contrôle d’accès logique

Au-delà de Spring Security :

- **segmenter** les comptes administrateurs OS/DB des comptes métier ;
- appliquer le **moindre privilège** sur les partages fichiers ;
- révoquer **rapidement** les accès des départs ou des mutations (process RH + SI).

---

## 17.5 Journalisation et audit

Les journaux d’**audit métier** (connexions, modifications sensibles, exports) doivent être :

- **horodatés** de façon fiable ;
- **immuable** ou append-only du point de vue opérationnel (copie vers SIEM) ;
- **conservés** selon la durée légale ou contractuelle.

Les administrateurs ne doivent pas pouvoir **effacer** silencieusement leur propre trace sans procédure encadrée.

---

## 17.6 Protection contre les abus applicatifs

- **Limitation de débit** sur les endpoints sensibles (login, API) ;
- **CAPTCHA** ou équivalent si le risque de bot est élevé ;
- validation **serveur** de toutes les entrées (ne jamais faire confiance au client).

---

## 17.7 Gestion des vulnérabilités

Processus en **cinq étapes** : inventaire des composants, veille (CVE), évaluation de criticité, **patch** ou contournement, vérification post-correctif. Les **0-day** nécessitent une **cellule de crise** avec communication aux directions concernées.

---

## 17.8 Conformité RGPD (rappel opérationnel)

Si SYSCO Web traite des **données personnelles** :

- identifier **bases légales** et finalités ;
- tenir le **registre** des traitements ;
- permettre **exercice des droits** (accès, rectification, effacement lorsque applicable) via procédures définies ;
- analyser les **transferts hors UE** ;
- documenter les **sous-traitants** (hébergeur, SaaS).

La **privacy by design** impose de minimiser les données collectées et de **pseudonymiser** lorsque possible.

---

## 17.9 Continuité d’activité (PCA)

Le **Plan de Continuité d’Activité** couvre :

- scénarios de **sinistre** (incendie, cyber-attaque, panne longue) ;
- **sites de repli** ou bascule géographique ;
- **priorisation** des applications (SYSCO Web peut être critique métier) ;
- **communication** interne et externe.

---

## 17.10 Reprise d’activité (PRI)

Le **Plan de Reprise Informatique** détaille les **RPO/RTO** pour la base et les fichiers. Les **sauvegardes** sont testées par **restauration partielle** régulière ; une sauvegarde jamais restaurée est une **illusion** de sécurité.

---

## 17.11 Gestion de crise cyber

En cas d’**intrusion** suspectée :

1. **Isoler** les systèmes compromis sans détruire les preuves ;
2. **Préserver** les journaux ;
3. **Notifier** selon la réglementation (ex. autorité de protection des données sous 72 h si données personnelles impactées en UE) ;
4. **Corriger** la cause racine avant toute remise en ligne ;
5. **Post-mortem** sans recherche de coupable individuel — culture **blameless** pour améliorer le système.

---

## 17.12 Sensibilisation des utilisateurs

La sécurité technique échoue si les utilisateurs **partagent** leurs mots de passe ou **contournent** les procédures. Des **campagnes** courtes et régulières (phishing simulé, rappels sur les pièces jointes) renforcent la **culture** de sécurité.

---

## 17.13 Revue périodique

Au moins **annuellement**, réviser :

- la **matrice** des rôles et permissions ;
- les **comptes privilégiés** ;
- la **documentation** d’exploitation ;
- les **résultats** des tests d’intrusion ou scans.

---

## 17.14 Conclusion du chapitre

La sécurité et la conformité sont des **processus continus**, non des cases à cocher en fin de projet. SYSCO Web s’inscrit dans un **écosystème** : la présente documentation technique doit être **jointe** aux politiques organisationnelles pour former une vision complète acceptable par les auditeurs.

---

*Fin du chapitre 17.*
