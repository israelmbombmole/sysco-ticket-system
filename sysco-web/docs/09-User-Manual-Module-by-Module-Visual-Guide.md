# SYSCO Web — Module-by-Module Visual Guide (for trainers & end users)

**How to use this document:** Each section matches **one main menu entry** from `NavigationRegistry` (same order as the sidebar). For every module you get: **purpose in plain language**, **who typically has access**, **what to click**, **what success looks like**, and **which figure** to show on a projector (if available).

**Figures:** When this guide says “show **Figure 1**”, use `docs/figures/fig-01-application-shell.png`. Add **your own** screenshots with arrows for any sub-screen not covered by the five baseline figures.

---

## Module 1 — Tableau de bord (`/app`)

**Plain purpose:** A **starting page** after login with **summary indicators** (counts, quick links, possibly charts depending on configuration).

**Typical roles:** Most operational roles see the dashboard link; exact **widgets** depend on services backing `AppController` / dashboard templates.

**Step-by-step (first visit):**

1. Sign in (User Manual Part 1).  
2. Confirm you landed on **`/app`** — URL may show `/app` or redirect from `/`.  
3. Read each **card** or **panel** slowly — do not rush; dashboards encode **management attention points**.  
4. If a number looks wrong, **drill down** via the linked module (tickets, courier, etc.) before calling IT.

**Visual aid:** Use **Figure 1** to point at the **centre panel** where dashboard tiles appear.

**Success:** You can **name three** KPIs you are responsible for acting on this week.

**Common confusion:** “Empty dashboard” — may mean **no permission** for underlying data or **no data** in your scope; administrator distinguishes.

---

## Module 2 — Saisie des données (`/app/data-entry`)

**Plain purpose:** **Structured typing** of operational data — often row/column grids resembling spreadsheets.

**Permission:** `DATA_ENTRY_READ` / `DATA_ENTRY_WRITE` (or bare `DATA_ENTRY`).

**Steps:**

1. Open **Saisie des données** from the menu.  
2. Identify the **dataset name** or **campaign** in the page title.  
3. If importing: follow **template** discipline (Part 3).  
4. If typing: add row → fill → **save** row before navigating away.  
5. Use **search** if the table is long.

**Visual:** Capture a **local screenshot** with arrows: (a) add row, (b) save, (c) validation message area.

**Success:** Random **sample of 10 rows** matches source documents.

---

## Module 3 — Portail courrier (`/app/courier`)

**Plain purpose:** **Operational** courier work — register and move **physical packets**.

**Permission:** `PHYSICAL_COURIER_*` **or** role-based visibility (see technical reference §3.3).

**Steps (receive):**

1. Open **Portail courrier**.  
2. **New** registration.  
3. Transcribe **label identifiers** carefully (double-check zeros vs letter O).  
4. Attach **photo** of label if policy allows.  
5. Save → note **system ID**.

**Steps (handover):**

1. Search packet.  
2. Open detail.  
3. **Transfer** to next person.  
4. **Physical** handover.

**Figure:** **Figure 4** (courier flow).

---

## Module 4 — Gestion courrier (`/app/courier-management`)

**Plain purpose:** **Supervisory** courier view — broader than portal.

**Role gate:** Director-level roles and similar (see `WebSyscoPermissions`).

**Steps:**

1. Open module.  
2. Filter **in transit** beyond threshold (e.g. 48h).  
3. Open **oldest** three.  
4. **Reassign** or **escalate** per SOP.

**Visual:** Screenshot **filter bar** with arrow; screenshot **row actions**.

---

## Module 5 — Gestion des données (`/app/data-management`)

**Plain purpose:** **Administrative** dataset operations — imports, corrections, governance.

**Permission:** `DATA_MANAGEMENT_READ/WRITE`.

**Steps:**

1. Confirm you have **written approval** for bulk changes.  
2. Export **backup** if UI offers it.  
3. Perform change **in small batch**.  
4. Validate **spot checks**.

**Figure:** Optional — **data pipeline** diagram from Part 3 (Mermaid) printed on slide.

---

## Module 6 — Partage de données (`/app/data-share`)

**Plain purpose:** **Controlled** sharing with **recipients** outside normal folders.

**Permission:** `DATASHARE_READ/WRITE`.

**Steps:** See User Manual Part 3 §7 (request, OTP, revoke).

**Visual:** Two screenshots: **create share form** and **active shares list** with arrow to **revoke**.

---

## Module 7 — Mon activité (`/app/my-activity`)

**Plain purpose:** **Personal** activity history — what you touched recently.

**Permission:** `MY_ACTIVITY_READ`.

**Steps:**

1. Open module.  
2. Set **date range** to this week.  
3. Click **entries** to jump back to artefacts if links exist.

**Success:** You can **reconstruct** your Tuesday afternoon from the log.

---

## Module 8 — Mon travail (`/app/my-work`)

**Plain purpose:** **Inbox** of assignments.

**Permission:** `MY_WORK_READ` **or** `MY_ACTIVITY_READ` (either grants nav access).

**Steps:** Part 2 §8.

**Figure:** **Figure 3** when explaining that work items often **are** tickets.

---

## Module 9 — Suivi des tickets (`/app/ticket-monitoring`)

**Plain purpose:** **Supervision** of many tickets — filters, operational oversight.

**Permission:** `TICKET_MONITORING_READ`.

**Steps:** Part 2 §3.

**Figure:** **Figure 3**.

---

## Module 10 — Gestion des tickets (`/app/ticket-management`)

**Plain purpose:** **Deep** ticket operations — editing, tasks, closure workflow.

**Permission:** `TICKET_MANAGEMENT_READ`.

**Steps:** Part 2 §4–§7.

**Visual:** Screenshot **Modifier** dialog with arrows: **Save** vs **Cancel**.

---

## Module 11 — Gestion du partage de fichiers (`/app/file-share-management`)

**Plain purpose:** **Admin** oversight of file-share requests.

**Visibility:** `ADMIN` / `SUPER_ADMIN` **only** in navigation gate.

**Steps:** Part 4 §6.

**Security reminder:** Treat every action as **legally sensitive**.

---

## Module 12 — Gestion des utilisateurs (`/app/user-management`)

**Plain purpose:** **Accounts**, **roles**, **permissions**.

**Permission:** `USER_MANAGEMENT_READ`.

**Steps:** Part 5 §4.

**Visual:** **Role/permissions modal** screenshot with arrow to **Save**.

---

## Module 13 — Agenda (`/app/agenda`)

**Plain purpose:** **Calendar** / **leave** (exact screens depend on `AgendaController` templates).

**Permission:** `LEAVE_MANAGEMENT_READ` **or** `USER_MANAGEMENT_READ`.

**Steps:** Part 4 §1.

---

## Module 14 — Audit des connexions (`/app/login-audit`)

**Plain purpose:** **Who logged in when**.

**Permission:** `LOGIN_AUDIT_READ`.

**Steps:** Part 5 §3.1.

**Visual:** Screenshot **export CSV** button.

---

## Module 15 — Audit du partage de fichiers (`/app/file-share-audit`)

**Plain purpose:** **Forensics** on share events.

**Permission:** `FILE_SHARE_AUDIT_READ`.

**Steps:** Part 5 §3.2.

---

## Module 16 — Création de ticket (`/app/create-ticket`)

**Plain purpose:** **Wizard** for new dossiers.

**Permission:** `CREATE_TICKET_READ`.

**Steps:** Part 2 §5.

---

## Module 17 — Planificateur de tâches (`/app/job-scheduler`)

**Plain purpose:** **Scheduled jobs** — reminders and due notifications.

**Permission:** `JOB_SCHEDULER_READ`.

**Steps:** Part 4 §4.

**Figure:** **Figure 5**.

---

## Module 18 — Missions (`/app/missions`)

**Plain purpose:** **Field missions** logistics and documentation.

**Permission:** `MISSIONS_READ`.

**Steps:** Part 4 §2.

---

## Module 19 — Mon poste / équipe (`/app/my-shift`)

**Plain purpose:** **Shift** visibility.

**Permission:** `MY_SHIFT_READ` **or** director-class roles (see technical reference).

**Steps:** Part 4 §3.

---

## Modules not in the main registry (header / shortcuts)

### Chat (`/app/chat`)

**Always** available post-login in permission gate — confirm with your UI (may be header icon).

**Steps:** Part 5 §1.

### Notifications (`/app/notifications`)

**Steps:** Part 5 §2.

---

## Trainer script (60 minutes, module rotation)

1. **0–10 min:** Figure 1 — shell.  
2. **10–20 min:** Dashboard + Mon travail.  
3. **20–35 min:** Tickets (Figure 3).  
4. **35–45 min:** Courrier (Figure 4).  
5. **45–55 min:** Planificateur (Figure 5).  
6. **55–60 min:** Q&A + logout drill.

---

## Accessibility notes per module

- **Tables:** If horizontal scroll appears, use **full screen** or ask IT for **column priority** tuning.  
- **Dialogs:** If **Modifier** traps focus, use **Esc** only if your browser allows — prefer explicit **Cancel**.

---

*End of module-by-module visual guide.*
