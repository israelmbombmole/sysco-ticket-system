# SYSCO Web — Documentation Package

This folder contains **two complementary documentation streams** for the SYSCO Web application (`sysco-web`, Spring Boot), plus a **single-book** user manual export.

| Document | File(s) | Audience |
|----------|---------|----------|
| **Complete user manual (single file)** — Parts 1–5, module guide, FAQ, scenarios, screen reference, print supplement, rollout; **19 figure slots** + placeholders | **[`00-User-Manual-Complete-Book.md`](00-User-Manual-Complete-Book.md)** (refresh with [`build-complete-user-manual.py`](build-complete-user-manual.py)); images in [`figures/`](figures/) | End users, supervisors, trainers, help-desk — **default PDF source** |
| **Application & technical documentation** (architecture, security, diagrams) | [`01-Technical-Documentation.md`](01-Technical-Documentation.md) + [`07-Technical-Reference-Navigation-Permissions-and-Integration.md`](07-Technical-Reference-Navigation-Permissions-and-Integration.md) + [`10-Technical-Controller-Service-and-Template-Inventory.md`](10-Technical-Controller-Service-and-Template-Inventory.md) + [`13-Technical-Business-Workflows-and-Narrative-Specification.md`](13-Technical-Business-Workflows-and-Narrative-Specification.md) + [`15-Technical-Runbook-Configuration-Environments-and-Release.md`](15-Technical-Runbook-Configuration-Environments-and-Release.md) + [`18-Technical-Appendix-FAQ-for-IT-Staff.md`](18-Technical-Appendix-FAQ-for-IT-Staff.md) | Project sponsors, business owners, IT integrators, developers, auditors |
| **User manual (split chapters)** — same content as the complete book, editable in smaller files | [`02-User-Manual-Part-1-Getting-Started.md`](02-User-Manual-Part-1-Getting-Started.md) through [`06-User-Manual-Part-5-Reference.md`](06-User-Manual-Part-5-Reference.md) + [`08-User-Manual-Appendix-Procedures-Scenarios-and-Training.md`](08-User-Manual-Appendix-Procedures-Scenarios-and-Training.md) + [`09-User-Manual-Module-by-Module-Visual-Guide.md`](09-User-Manual-Module-by-Module-Visual-Guide.md) + [`11-User-Manual-Extended-FAQ-Incidents-and-Compliance.md`](11-User-Manual-Extended-FAQ-Incidents-and-Compliance.md) + [`12-User-Manual-Scenario-Library-Training-Cases.md`](12-User-Manual-Scenario-Library-Training-Cases.md) + [`14-User-Manual-Template-Screen-Reference.md`](14-User-Manual-Template-Screen-Reference.md) + [`16-Print-Supplement-Yearbook-Checklists-and-Extended-Glossary.md`](16-Print-Supplement-Yearbook-Checklists-and-Extended-Glossary.md) + [`17-User-Manual-Stakeholder-Rollout-Communication-Templates.md`](17-User-Manual-Stakeholder-Rollout-Communication-Templates.md) | Editors maintaining one chapter at a time |
| **Technical documentation (French)** — rebuilt multi-chapter set, 50+ pages when exported | [`fr/00-LISEZMOI-Documentation-Technique.md`](fr/00-LISEZMOI-Documentation-Technique.md) (index) + [`fr/01-Introduction-et-Architecture-generale.md`](fr/01-Introduction-et-Architecture-generale.md) through [`fr/19-Annexe-References-Techniques-et-Lectures-complementaires.md`](fr/19-Annexe-References-Techniques-et-Lectures-complementaires.md) | Francophone integrators, developers, auditors, operations |

**Illustrations (with arrows and numbered steps)** belong under [`figures/`](figures/). Placeholder PNGs can be generated with `python docs/figures/generate_placeholder_pngs.py`. See [`figures/README.txt`](figures/README.txt) for the full figure list and how to replace placeholders with real screenshots.

---

## Reaching 50+ pages when printing

Page count depends on font, margins, and whether diagrams print on full pages. As a rule of thumb:

- **A typical A4 PDF** uses about **400–550 words per page** for body text (11–12 pt, standard margins).
- **Target** for a ~50-page printout is roughly **20,000–28,000 words** including tables. After export, check the PDF page count; if you are short, add **institution-specific SOPs**, **screenshots** (one image ≈ half to a full page), and extra scenarios in Part 8.

### Recommended export workflow (Pandoc)

From the `sysco-web` folder (with [Pandoc](https://pandoc.org/) installed):

```bash
# Technical (01 + 07 + 10 + 13 + 15 + 18)
pandoc docs/01-Technical-Documentation.md docs/07-Technical-Reference-Navigation-Permissions-and-Integration.md docs/10-Technical-Controller-Service-and-Template-Inventory.md docs/13-Technical-Business-Workflows-and-Narrative-Specification.md docs/15-Technical-Runbook-Configuration-Environments-and-Release.md docs/18-Technical-Appendix-FAQ-for-IT-Staff.md -o SYSCO-Web-Technical.pdf --pdf-engine=xelatex -V geometry:margin=2.5cm

# User manual — single complete book (recommended); PNGs under docs/figures/
pandoc docs/00-User-Manual-Complete-Book.md -o SYSCO-Web-User-Manual-Complete.pdf --pdf-engine=xelatex -V geometry:margin=2.5cm

# User manual — split chapters (legacy assembly)
pandoc docs/02-User-Manual-Part-1-Getting-Started.md docs/03-User-Manual-Part-2-Tickets-and-Operations.md docs/04-User-Manual-Part-3-Courier-and-Data.md docs/05-User-Manual-Part-4-Planning-and-Missions.md docs/06-User-Manual-Part-5-Reference.md docs/08-User-Manual-Appendix-Procedures-Scenarios-and-Training.md docs/09-User-Manual-Module-by-Module-Visual-Guide.md docs/11-User-Manual-Extended-FAQ-Incidents-and-Compliance.md docs/12-User-Manual-Scenario-Library-Training-Cases.md docs/14-User-Manual-Template-Screen-Reference.md docs/16-Print-Supplement-Yearbook-Checklists-and-Extended-Glossary.md docs/17-User-Manual-Stakeholder-Rollout-Communication-Templates.md -o SYSCO-Web-User-Manual.pdf --pdf-engine=xelatex -V geometry:margin=2.5cm
```

If Mermaid diagrams must render in PDF, use a Mermaid filter or export diagrams as PNG first.

### Alternative: Microsoft Word

1. Open each Markdown file in VS Code / Cursor and paste into Word, **or** use Pandoc to produce `.docx`.
2. Insert the PNG figures from `docs/figures/` where the manual says “Figure …”.
3. Enable **Table of Contents** (References → Table of Contents).
4. Print double-sided if required by your institution.

---

## Conventions in this package

- **Non-IT readers:** sections marked *Plain language* avoid jargon; technical terms link to the glossary in the technical document.
- **French UI:** the live application defaults to **French** labels in many places; English keys exist for internationalisation. The manual gives **French screen names** where they match the product, with English glosses when useful.
- **Roles and permissions:** what you see depends on your **role** and **permissions**; not every user has every menu entry.

---

## Version

- **Product:** SYSCO Web (`com.sysco:sysco-web`, Spring Boot 3.2.x, Java 17).
- **Documentation package generated:** May 2026 (align with repository state at generation time).

For the exact behaviour of a specific screen, always verify against the running application in your environment (test, staging, production).
