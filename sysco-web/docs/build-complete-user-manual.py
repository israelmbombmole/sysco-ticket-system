"""Assemble docs/00-User-Manual-Complete-Book.md from split user-manual sources."""
from __future__ import annotations

from pathlib import Path


def strip_part_trailers(text: str) -> str:
    lines = text.splitlines()
    out_lines: list[str] = []
    skip = False
    for line in lines:
        if line.startswith("# SYSCO Web — User Manual (Part"):
            continue
        if line.startswith("**Audience:**") or line.startswith("**Focus:**"):
            continue
        if line.startswith("**Prerequisite:**"):
            continue
        if line.startswith("## Document set complete"):
            break
        if line.startswith("## Next manual part"):
            skip = True
            continue
        if skip:
            if line.startswith("---") or line.startswith("*SYSCO Web User Manual"):
                skip = False
            continue
        if line.startswith("*SYSCO Web User Manual Part"):
            continue
        out_lines.append(line)
    return "\n".join(out_lines)


def drop_first_h1(text: str) -> str:
    lines = text.splitlines()
    if lines and lines[0].startswith("# "):
        return "\n".join(lines[1:]).strip()
    return text.strip()


def append_doc(heading: str, path: Path) -> str:
    t = path.read_text(encoding="utf-8")
    return f"\n\n---\n\n# {heading}\n\n" + drop_first_h1(t) + "\n"


def main() -> None:
    root = Path(__file__).resolve().parent
    chunks: list[str] = []

    chunks.append(
        """# SYSCO Web — Complete User Manual (single book)

**Audience:** Customs officers, secretaries, verifiers, directors, administrators — **no IT background required.**  
**Language note:** The live application interface is primarily **French**; this manual uses French **menu labels** where they appear on screen.

This document **combines** the former multi-file user manual (Parts 1–5), visual module guide, appendices, FAQ, scenario library, screen reference, print supplement, and rollout templates into **one** export-friendly book. **Figures** live in `docs/figures/` — replace **placeholder** PNGs with your own annotated screenshots for production use.

---

## How to use this manual

| You want… | Go to… |
|-----------|--------|
| Login, shell, tour | [Part 1 — Getting started](#part-1--getting-started) |
| Tickets, monitoring, Mon travail | [Part 2 — Tickets](#part-2--tickets-and-daily-operations) |
| Courrier, données, partage | [Part 3 — Courier and data](#part-3--courier-and-data) |
| Agenda, missions, planificateur | [Part 4 — Planning and missions](#part-4--planning-and-missions) |
| Chat, audits, admin, glossary | [Part 5 — Reference](#part-5--reference-chat-audits-administration) |
| Every menu module + projector script | [Module-by-module visual guide](#module-by-module-visual-guide) |
| Day-in-the-life & playbooks | [Appendix A — Procedures and training](#appendix-a--procedures-scenarios-and-training) |
| Long FAQ & compliance | [Extended FAQ and compliance](#extended-faq-incidents-and-compliance-orientation) |
| Role-play training cases | [Scenario library](#scenario-library-training-cases) |
| What to photograph per HTML screen | [Screen reference (templates)](#screen-reference-templates-to-html) |
| Monthly checklists & glossary | [Print supplement](#print-supplement-yearbook-checklists) |
| Rollout emails & posters | [Stakeholder rollout](#stakeholder-rollout-and-communication-templates) |

---

## Master figure index (images with arrows)

| Fig | File | What to show |
|-----|------|--------------|
| 1 | `figures/fig-01-application-shell.png` | Full shell: header, sidebar, content |
| 2 | `figures/fig-02-general-navigation-flow.png` | Login → dashboard → module |
| 3 | `figures/fig-03-ticket-lifecycle.png` | Ticket states and transitions |
| 4 | `figures/fig-04-courier-flow.png` | Courier handovers |
| 5 | `figures/fig-05-job-scheduler-flow.png` | Scheduled jobs / reminders |
| 6 | `figures/fig-06-login-page.png` | Username / password / Connexion |
| 7 | `figures/fig-07-password-change.png` | First-time password form |
| 8 | `figures/fig-08-guided-tour-step.png` | Tour popover + highlight |
| 9 | `figures/fig-09-ticket-monitoring.png` | Suivi: filters + table |
| 10 | `figures/fig-10-ticket-detail.png` | Ticket detail: comments, tasks |
| 11 | `figures/fig-11-create-ticket.png` | Create ticket form |
| 12 | `figures/fig-12-my-work-inbox.png` | Mon travail |
| 13 | `figures/fig-13-courier-portal.png` | Portail courrier |
| 14 | `figures/fig-14-data-entry-grid.png` | Saisie des données |
| 15 | `figures/fig-15-data-share-form.png` | Partage de données |
| 16 | `figures/fig-16-notifications-inbox.png` | Notifications |
| 17 | `figures/fig-17-chat-thread.png` | Chat |
| 18 | `figures/fig-18-user-management.png` | Gestion utilisateurs |
| 19 | `figures/fig-19-login-audit-export.png` | Audit connexions + export |

**Trainer tip:** add **numbered red arrows** on each PNG matching steps in the text.

"""
    )

    p1 = strip_part_trailers((root / "02-User-Manual-Part-1-Getting-Started.md").read_text(encoding="utf-8"))
    p1 = drop_first_h1(p1)
    chunks.append("\n\n---\n\n# Part 1 — Getting started\n\n" + p1)

    for title, fname in [
        ("Part 2 — Tickets and daily operations", "03-User-Manual-Part-2-Tickets-and-Operations.md"),
        ("Part 3 — Courier and data", "04-User-Manual-Part-3-Courier-and-Data.md"),
        ("Part 4 — Planning and missions", "05-User-Manual-Part-4-Planning-and-Missions.md"),
        ("Part 5 — Reference (chat, audits, administration)", "06-User-Manual-Part-5-Reference.md"),
    ]:
        body = strip_part_trailers((root / fname).read_text(encoding="utf-8"))
        body = drop_first_h1(body)
        chunks.append(f"\n\n---\n\n# {title}\n\n" + body)

    text = "".join(chunks)

    login_extra = """

### Illustrated: login page

![Figure 6 — Login page (identifiant, mot de passe, Connexion)](figures/fig-06-login-page.png)

### Illustrated: password change (first login)

![Figure 7 — Password change screen](figures/fig-07-password-change.png)

### Illustrated: guided tour popover

![Figure 8 — Guided tour highlighting a menu area](figures/fig-08-guided-tour-step.png)

"""
    needle = "7. **If login fails:** read the message on screen."
    if needle in text:
        text = text.replace(needle, needle + login_extra, 1)

    p2_extra = """

![Figure 9 — Suivi des tickets: filters and list](figures/fig-09-ticket-monitoring.png)

![Figure 10 — Ticket detail: comments and tasks](figures/fig-10-ticket-detail.png)

![Figure 11 — Create ticket form](figures/fig-11-create-ticket.png)

![Figure 12 — Mon travail inbox](figures/fig-12-my-work-inbox.png)

"""
    m2 = "## 3. Module: Suivi des tickets (monitoring)"
    if m2 in text:
        text = text.replace(m2, m2 + "\n\n" + p2_extra.strip() + "\n", 1)

    p3_extra = """

![Figure 13 — Portail courrier list and New](figures/fig-13-courier-portal.png)

![Figure 14 — Saisie des données grid](figures/fig-14-data-entry-grid.png)

![Figure 15 — Partage de données: create and revoke](figures/fig-15-data-share-form.png)

"""
    m3 = "## 5. Saisie des données"
    if m3 in text:
        text = text.replace(m3, p3_extra.strip() + "\n\n" + m3, 1)

    p5_extra = """

![Figure 16 — Notifications inbox](figures/fig-16-notifications-inbox.png)

![Figure 17 — Chat thread](figures/fig-17-chat-thread.png)

![Figure 18 — User management and permissions](figures/fig-18-user-management.png)

![Figure 19 — Login audit and export](figures/fig-19-login-audit-export.png)

"""
    m5 = "## 1. Chat (messagerie)"
    if m5 in text:
        text = text.replace(m5, p5_extra.strip() + "\n\n" + m5, 1)

    text += append_doc(
        "Appendix A — Procedures, scenarios, and training",
        root / "08-User-Manual-Appendix-Procedures-Scenarios-and-Training.md",
    )
    text += append_doc(
        "Module-by-module visual guide",
        root / "09-User-Manual-Module-by-Module-Visual-Guide.md",
    )
    text += append_doc(
        "Extended FAQ, incidents, and compliance orientation",
        root / "11-User-Manual-Extended-FAQ-Incidents-and-Compliance.md",
    )
    text += append_doc(
        "Scenario library (training cases)",
        root / "12-User-Manual-Scenario-Library-Training-Cases.md",
    )
    text += append_doc(
        "Screen reference (templates to HTML)",
        root / "14-User-Manual-Template-Screen-Reference.md",
    )
    text += append_doc(
        "Print supplement: yearbook checklists",
        root / "16-Print-Supplement-Yearbook-Checklists-and-Extended-Glossary.md",
    )
    text += append_doc(
        "Stakeholder rollout and communication templates",
        root / "17-User-Manual-Stakeholder-Rollout-Communication-Templates.md",
    )

    text += """
---

# End of complete user manual

**Maintained sources:** split files `02-…` through `17-…` remain the chapter originals; re-run `python docs/build-complete-user-manual.py` after editing them to refresh this book.

**Export PDF (Pandoc example, from `sysco-web`):**

```text
pandoc docs/00-User-Manual-Complete-Book.md -o SYSCO-Web-User-Manual-Complete.pdf --pdf-engine=xelatex -V geometry:margin=2.5cm
```

"""

    out_path = root / "00-User-Manual-Complete-Book.md"
    out_path.write_text(text, encoding="utf-8")
    print(f"Wrote {out_path} ({len(text):,} chars)")


if __name__ == "__main__":
    main()
