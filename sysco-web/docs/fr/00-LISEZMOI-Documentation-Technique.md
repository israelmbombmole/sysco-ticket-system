# Documentation technique SYSCO Web (français)

**Produit :** SYSCO Web — module Maven `sysco-web` (Spring Boot 3.2.x, Java 17)  
**Emplacement des sources :** `c:\sqlite\javafx-audit-system\sysco-web`  
**Langue :** français (ensemble documentaire ci-dessous)

---

## Contenu du paquet

| Fichier | Thème |
|---------|--------|
| [01-Introduction-et-Architecture-generale.md](01-Introduction-et-Architecture-generale.md) | Vision, glossaire, contexte, architecture en couches, pile technologique |
| [02-Securite-Authentification-Autorisation.md](02-Securite-Authentification-Autorisation.md) | Spring Security, rôles, permissions, matrice navigation |
| [03-Couche-Web-Modules-et-Flux.md](03-Couche-Web-Modules-et-Flux.md) | Contrôleurs, Thymeleaf, flux requête, inventaire des modules |
| [04-Persistance-Donnees-et-Flyway.md](04-Persistance-Donnees-et-Flyway.md) | JPA, migrations, évolution du schéma, bonnes pratiques |
| [05-Temps-reel-Notifications-et-Internationalisation.md](05-Temps-reel-Notifications-et-Internationalisation.md) | WebSocket/STOMP, notifications, i18n, visite guidée |
| [06-Deploiement-Configuration-et-Runbook.md](06-Deploiement-Configuration-et-Runbook.md) | Profils, secrets, sauvegardes, mise en production |
| [07-Qualite-Exploitation-et-Annexes.md](07-Qualite-Exploitation-et-Annexes.md) | NFR, audit, matrice de tests, glossaire étendu, index des diagrammes |
| [08-Description-technique-par-Module-metier.md](08-Description-technique-par-Module-metier.md) | Description technique par module métier |
| [09-FAQ-technique-et-Evolutions.md](09-FAQ-technique-et-Evolutions.md) | FAQ technique, évolutions |
| [10-Architecture-detaillee-et-Flux-donnees.md](10-Architecture-detaillee-et-Flux-donnees.md) | Architecture détaillée, flux de données SI |
| [11-Guide-Exploitation-et-Operations-Detaille.md](11-Guide-Exploitation-et-Operations-Detaille.md) | Guide d’exploitation et opérations |
| [12-Reference-detaillee-WebSyscoPermissions-et-Navigation.md](12-Reference-detaillee-WebSyscoPermissions-et-Navigation.md) | Référence `WebSyscoPermissions`, chemins HTTP, rôles |
| [13-Strategie-de-tests-Qualite-et-Validation-technique.md](13-Strategie-de-tests-Qualite-et-Validation-technique.md) | Stratégie de tests, qualité, validation |
| [14-Performances-JVM-Base-de-donnees-et-Resolution-de-problemes.md](14-Performances-JVM-Base-de-donnees-et-Resolution-de-problemes.md) | Performances JVM, SGBD, diagnostic |
| [15-Gouvernance-du-code-Conventions-et-Cycle-de-vie.md](15-Gouvernance-du-code-Conventions-et-Cycle-de-vie.md) | Gouvernance du code, conventions, Git |
| [16-Interfaces-externes-Interop-et-Echanges-de-donnees.md](16-Interfaces-externes-Interop-et-Echanges-de-donnees.md) | Interfaces externes, interopérabilité |
| [17-Securite-approfondie-Conformite-et-Continuite.md](17-Securite-approfondie-Conformite-et-Continuite.md) | Sécurité approfondie, RGPD, PCA/PRI |
| [18-Modele-de-donnees-Conventions-JPA-et-Evolution-du-schema.md](18-Modele-de-donnees-Conventions-JPA-et-Evolution-du-schema.md) | Modèle de données, JPA, Flyway, schéma |
| [19-Annexe-References-Techniques-et-Lectures-complementaires.md](19-Annexe-References-Techniques-et-Lectures-complementaires.md) | Références, sigles, usage pédagogique |
| [20-Guide-pre-deploiement-Serveur-Specifications-et-Schemas.md](20-Guide-pre-deploiement-Serveur-Specifications-et-Schemas.md) | Pré-déploiement serveur : spécifications, schémas Mermaid, capacité |

---

## Volume imprimable (50 pages et plus)

En français, on compte souvent **380 à 480 mots par page A4** (police 11–12 pt, marges standard). L’ensemble des chapitres **01 à 18** et des annexes **19** (plus ce LISEZMOI) totalise **plus de 20 000 mots** de contenu technique, ce qui correspond typiquement à **au moins cinquante pages** après export PDF (selon la densité des titres, tableaux et sauts de page).

### Export PDF avec Pandoc (recommandé)

Depuis le répertoire `sysco-web` (avec [Pandoc](https://pandoc.org/) et un moteur PDF, par exemple XeLaTeX) :

**Invite de commandes (cmd.exe)** — continuations `^` :

```bat
pandoc docs\fr\01-Introduction-et-Architecture-generale.md ^
  docs\fr\02-Securite-Authentification-Autorisation.md ^
  docs\fr\03-Couche-Web-Modules-et-Flux.md ^
  docs\fr\04-Persistance-Donnees-et-Flyway.md ^
  docs\fr\05-Temps-reel-Notifications-et-Internationalisation.md ^
  docs\fr\06-Deploiement-Configuration-et-Runbook.md ^
  docs\fr\07-Qualite-Exploitation-et-Annexes.md ^
  docs\fr\08-Description-technique-par-Module-metier.md ^
  docs\fr\09-FAQ-technique-et-Evolutions.md ^
  docs\fr\10-Architecture-detaillee-et-Flux-donnees.md ^
  docs\fr\11-Guide-Exploitation-et-Operations-Detaille.md ^
  docs\fr\12-Reference-detaillee-WebSyscoPermissions-et-Navigation.md ^
  docs\fr\13-Strategie-de-tests-Qualite-et-Validation-technique.md ^
  docs\fr\14-Performances-JVM-Base-de-donnees-et-Resolution-de-problemes.md ^
  docs\fr\15-Gouvernance-du-code-Conventions-et-Cycle-de-vie.md ^
  docs\fr\16-Interfaces-externes-Interop-et-Echanges-de-donnees.md ^
  docs\fr\17-Securite-approfondie-Conformite-et-Continuite.md ^
  docs\fr\18-Modele-de-donnees-Conventions-JPA-et-Evolution-du-schema.md ^
  docs\fr\19-Annexe-References-Techniques-et-Lectures-complementaires.md ^
  -o SYSCO-Web-Documentation-Technique-FR.pdf ^
  --pdf-engine=xelatex -V lang=fr -V geometry:margin=2.5cm
```

**PowerShell** (chemins relatifs depuis `sysco-web` ; ne pas utiliser le splatting `@p`, passer le tableau en arguments finaux) :

```powershell
$p = 1..19 | ForEach-Object { $n = '{0:D2}' -f $_; (Get-Item "docs/fr/$n-*.md").FullName }
pandoc $p -o SYSCO-Web-Documentation-Technique-FR.pdf --pdf-engine=xelatex -V lang=fr -V geometry:margin=2.5cm
```

Pour inclure aussi ce LISEZMOI en tête du PDF : préfixez le tableau `$p` par `(Resolve-Path 'docs/fr/00-LISEZMOI-Documentation-Technique.md')`.

Pour les diagrammes **Mermaid** dans le PDF, utilisez un filtre Mermaid ou exportez les figures en PNG.

### Alternative Microsoft Word

1. Ouvrir chaque fichier `.md` dans un éditeur compatible ou convertir avec Pandoc en `.docx`.  
2. Fusionner les chapitres dans un seul document.  
3. Insérer une table des matières (Références → Table des matières).  
4. Vérifier le nombre de pages avant impression.

---

## Relation avec la documentation en anglais

Les fichiers à la racine `docs/01-Technical-Documentation.md`, etc., restent la version **anglophone** historique. La présente arborescence `docs/fr/` est la **référence française** pour les équipes, intégrateurs et auditeurs francophones. En cas d’écart, le **code source** (`src/main/java`, `src/main/resources`) fait foi pour le comportement exact.

---

## Version

- **Branche / état du dépôt :** à l’aligner sur la version déployée (tag Git, build CI).  
- **Date de rédaction du paquet :** mai 2026 (à ajuster selon vos releases).

---

*Fin du fichier LISEZMOI.*
