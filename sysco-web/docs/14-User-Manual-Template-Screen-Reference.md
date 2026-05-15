# SYSCO Web — Screen Reference (maps to HTML templates)

**Purpose:** Give trainers a **checklist of screens** that exist in the codebase (`templates/app/*.html`). For each screen: **what it is for**, **what to photograph** for your institution’s PDF manual, and **what can go wrong** in plain language.

**How to illustrate:** Capture a screenshot from **staging**, draw **numbered arrows** in an image editor, save as `figures/screen-{name}.png`, and insert into your Word/PDF export beside the matching section below.

---

## `dashboard.html` — Tableau de bord

**For:** Landing summary after login.  
**Photograph:** Full page with **header + sidebar + first row of cards**.  
**Arrows:** (1) welcome metrics, (2) quick link to tickets, (3) notifications badge in header.  
**Typical problems:** Empty widgets → permissions or no data in scope.

---

## `data-entry.html` — Saisie des données

**For:** Grid data capture.  
**Photograph:** Table with **one highlighted row** and **Save** control.  
**Arrows:** Add row, validation message area, filter bar.  
**Typical problems:** Paste from Excel with wrong columns.

---

## `courier.html` — Portail courrier

**For:** Courier portal operations.  
**Photograph:** List view + **New** button.  
**Arrows:** Search box, status column, open detail.  
**Typical problems:** Duplicate barcode entries.

---

## `courier-management.html` — Gestion courrier

**For:** Supervisory courier overview.  
**Photograph:** Filtered “in transit” list.  
**Arrows:** Bulk filter, row action menu.  
**Typical problems:** Role confusion — verifier expects screen but lacks role.

---

## `data-management.html` — Gestion des données

**For:** Dataset administration.  
**Photograph:** Import section + table preview.  
**Arrows:** Choose file, validate, commit.  
**Typical problems:** Large import timeout — split file.

---

## `data-share.html` — Partage de données

**For:** Secure sharing workflow.  
**Photograph:** Create share form + OTP hint text.  
**Arrows:** Recipient field, expiry, submit.  
**Typical problems:** Email typo → wrong recipient.

---

## `my-activity.html` — Mon activité

**For:** Personal activity timeline.  
**Photograph:** Week range filter + first three events.  
**Arrows:** Date picker, event link, export if present.  
**Typical problems:** User expects other people’s activity — wrong module.

---

## `my-work.html` — Mon travail

**For:** Personal assigned work queue.  
**Photograph:** Inbox table with assignment column.  
**Arrows:** Open ticket, bulk nothing (usually single-action).  
**Typical problems:** Stale assignments — supervisor fix.

---

## `my-work-merge-ticket.html` — Fusion (Mon travail)

**For:** Merge flow from **Mon travail** context.  
**Photograph:** Two ticket references visible + confirm.  
**Arrows:** Source, target, confirm.  
**Typical problems:** Irreversible merge — training stress test on dummy data.

---

## `my-activity-merge-ticket.html` — Fusion (Mon activité)

**For:** Merge initiated from activity view.  
**Photograph:** Similar to above but breadcrumb differs.  
**Arrows:** Back link, confirm.  
**Typical problems:** Users lost navigation — teach breadcrumb.

---

## `ticket-monitoring.html` — Suivi des tickets

**For:** Supervision list.  
**Photograph:** Wide table with filters expanded.  
**Arrows:** Status filter, escalate button if visible, sort column.  
**Typical problems:** Wrong filter saved in bookmark.

---

## `ticket-management.html` — Gestion des tickets (list)

**For:** Ticket management entry list.  
**Photograph:** Row with SLA badge.  
**Arrows:** Open detail, filter, export.  
**Typical problems:** Performance on huge lists — narrow filters.

---

## `ticket-management-detail.html` — Détail billet

**For:** Deep ticket work.  
**Photograph:** Header + comments + tasks panels.  
**Arrows:** Modifier, add comment, add attachment, change status.  
**Typical problems:** Long pages — teach collapse sections if UI has them.

---

## `create-ticket.html` — Création de ticket

**For:** Creation wizard/form.  
**Photograph:** First and last step side-by-side in two images if multi-step.  
**Arrows:** Required fields star, submit.  
**Typical problems:** Attachment size limits.

---

## `file-share-management-access.html` / `file-share-management.html`

**For:** Administrative file share tooling (split templates).  
**Photograph:** Pending approvals queue.  
**Arrows:** Approve, deny, audit link.  
**Typical problems:** Highly sensitive — restrict training recordings.

---

## `file-share-audit.html` — Audit partage fichiers

**For:** Forensic audit view.  
**Photograph:** Redacted example rows.  
**Arrows:** Date filter, export.  
**Typical problems:** Huge exports — narrow window.

---

## `user-management.html` — Gestion utilisateurs

**For:** Accounts and permissions.  
**Photograph:** User list + edit drawer/modal.  
**Arrows:** Create user, permissions toggle, save.  
**Typical problems:** Accidental admin grant — double-check role.

---

## `agenda.html` — Agenda / congés

**For:** Calendar / leave.  
**Photograph:** Month grid + side panel.  
**Arrows:** New request, day cell, legend.  
**Typical problems:** Time zone misinterpretation for deadlines.

---

## `login-audit.html` — Audit connexions

**For:** Login history reporting.  
**Photograph:** Table + export button.  
**Arrows:** Start date, end date, download CSV.  
**Typical problems:** CSV opened in Excel garbles dates — use import wizard.

---

## `missions.html` — Missions (list)

**For:** Mission list.  
**Photograph:** Filters + mission rows.  
**Arrows:** Create mission, open detail.  
**Typical problems:** Confusion between mission and ticket — clarify SOP.

---

## `mission-detail.html` — Détail mission

**For:** Single mission logistics.  
**Photograph:** Participants section + documents.  
**Arrows:** Add participant, generate document.  
**Typical problems:** Missing mandatory logistics fields.

---

## `my-shift.html` — Mon poste / équipe

**For:** Shift/roster views.  
**Photograph:** Today strip + team list.  
**Arrows:** Mark attendance if present, request swap if present.  
**Typical problems:** HR policy not aligned with UI fields.

---

## `job-scheduler.html` — Planificateur

**For:** Scheduled jobs UI.  
**Photograph:** List + modal for new job.  
**Arrows:** Due datetime picker, reminder offset, active toggle.  
**Typical problems:** Time confusion AM/PM — use 24h display if possible.

---

## `notifications.html` — Notifications

**For:** Notification inbox page.  
**Photograph:** Unread vs read rows.  
**Arrows:** Mark read, click through.  
**Typical problems:** Users ignore bell — teach triage cadence.

---

## `chat.html` — Chat

**For:** Messaging UI.  
**Photograph:** Conversation list + message thread.  
**Arrows:** New chat, send, attach if present.  
**Typical problems:** Sensitive content — policy reminder.

---

## `task-detail.html` — Détail tâche

**For:** Task drill-down associated with planner/ticket contexts.  
**Photograph:** Task fields + linkage to parent ticket if shown.  
**Arrows:** Complete task, reassign.  
**Typical problems:** Orphan tasks when parent ticket reassigned.

---

## `module-placeholder.html` — Écran placeholder

**For:** Reserved / upcoming module shell.  
**Photograph:** Usually simple message page.  
**Arrows:** N/A — explain “under deployment” to users.  
**Typical problems:** Users think system broken — comms required.

---

## Suggested figure numbering for your institution

| Your PNG filename | Suggested caption |
|-------------------|-------------------|
| `screen-dashboard.png` | “Figure D1 — Dashboard” |
| `screen-ticket-detail.png` | “Figure T1 — Ticket detail” |
| … | … |

---

*End of template screen reference.*
