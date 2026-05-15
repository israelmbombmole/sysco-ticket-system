# SYSCO Web — User Manual Appendix: Procedures, Scenarios, and Training

**Audience:** Trainers, supervisors, help-desks, and **end users** who want cookbook-style **playbooks**.  
**Use together with:** Parts 1–5 of the user manual.

This appendix adds **length**, **repetition in different words** (useful for non-IT readers), and **day-in-the-life scenarios**. It does **not** replace your institution’s **official SOPs** — align wording with your legal and HR policies.

---

## Part A — Day-in-the-life scenarios

### Scenario A1: Verifier starts the morning

**Persona:** `VERIFICATEUR` with ticket and data-entry access.

1. **Sign in** (Part 1).  
2. Open **Tableau de bord** — scan counts / alerts (Part 1).  
3. Open **Mon travail** — treat **oldest** assigned ticket first (Part 2).  
4. For each ticket: read **description**, check **attachments**, add **comment** with your decision (Part 2).  
5. If blocked: **escalate** per SOP (Part 2).  
6. Before lunch: clear **notifications** bell or mark as read (Part 5).

**Success criteria:** No assigned ticket sits **without comment** beyond your local SLA.

---

### Scenario A2: Secretary registers incoming courier packets

**Persona:** `SECRETAIRE` with courier access.

1. Receive physical packet at desk.  
2. Open **Portail courrier** (Part 3).  
3. **Register** packet with label references (Part 3).  
4. **Assign** to internal route (Part 3).  
5. Hand physically to **next** person only after digital handover shows correct assignee (Part 3).

**Success criteria:** 100% of packets received before noon are **in the system** before routing.

---

### Scenario A3: Director reviews escalations

**Persona:** `DIRECTEUR`.

1. Open **Suivi des tickets** with filter **escalated** (Part 2).  
2. Sort by **age** (if column exists).  
3. Open top **three** dossiers.  
4. For each: decide **reassign**, **close request**, or **further escalation** (Part 2).  
5. Document decision in **comment** (non-legal summary) and official channel if required.

**Success criteria:** No escalation older than **N days** without director decision (set N locally).

---

### Scenario A4: Planner officer uses scheduled jobs

**Persona:** officer with **JOB_SCHEDULER** permission.

1. Open **Planificateur de tâches** (Part 4).  
2. Create jobs for **known deadlines** (court dates, treaty deadlines, partner SLAs) (Part 4).  
3. Confirm **reminder** is **one working day before** due (example).  
4. When notification fires: open linked **ticket** and advance status (Parts 2 & 4).

**Success criteria:** Zero “surprise” missed deadlines attributable to lack of reminder.

---

## Part B — Playbooks (step-by-step, verbose)

### Playbook B1: “I cannot see the menu my colleague sees”

1. Confirm you are logged in as **yourself** (check name in header).  
2. Ask colleague for their **role name** (exact spelling).  
3. If roles differ: **expected** difference — request access via **administrator**.  
4. If roles **match**: administrator should verify **permission rows** and **direction scope**.  
5. **Do not** share accounts to bypass this — **forbidden** in most institutions.

---

### Playbook B2: “Upload keeps failing”

1. Note **exact error text** (screenshot).  
2. Check **file size** — compress PDF if allowed.  
3. Try **another browser** once.  
4. If still failing: IT checks **server disk** and **`sysco.uploads.directory`** permissions.

---

### Playbook B3: “Ticket status wrong after my action”

1. Open ticket **history** / **timeline** (Part 2).  
2. Identify **last** status change and **who** performed it.  
3. If mistake: use **permitted** transition to correct (may require supervisor).  
4. If system bug: capture **reference**, **time**, **user** → IT ticket.

---

### Playbook B4: “Sensitive document shared by mistake”

1. **Revoke** share in **Partage de données** if you own the share (Part 3).  
2. Notify **security** immediately with **what**, **whom**, **when**.  
3. Do **not** delete audit logs — investigators need them (Part 5).

---

## Part C — Training agenda (one day, non-IT audience)

| Time | Topic | Hands-on exercise |
|------|-------|-------------------|
| 09:00 | What is SYSCO Web | Login on training laptops |
| 09:30 | Shell tour | Locate header, menu, logout |
| 10:30 | Tickets | Open sample ticket, add comment |
| 12:00 | Lunch | — |
| 13:00 | Courier | Register dummy packet |
| 14:00 | Data share | Request share with trainer as recipient |
| 15:00 | Notifications & chat | Send test message |
| 16:00 | Q&A | Playbooks B1–B4 |

**Trainer checklist:** test accounts, sample data, projector showing **Figure 1** (`fig-01-application-shell.png`) from `docs/figures/`.

---

## Part D — Glossary expansion (French ↔ plain French)

| Term | Explain like I’m new |
|------|------------------------|
| **Session** | Your logged-in period; ends on logout or timeout |
| **Fil d’Ariane** | Breadcrumb trail showing where you are (if shown) |
| **OTP** | One-time password, often numeric, valid briefly |
| **Scope direction** | Data limited to your organisational branch |

---

## Part E — Accessibility drills

1. **Keyboard only:** navigate login → dashboard → logout using Tab/Enter.  
2. **200% zoom:** ensure tables still readable; if not, report to IT (template issue).  
3. **Colour-blind:** read **text** on status badges, not only colour.

---

## Part F — Figures index (for trainers inserting slides)

| Figure file | Use in training |
|-------------|-----------------|
| `figures/fig-01-application-shell.png` | First hour — anatomy of screen |
| `figures/fig-02-general-navigation-flow.png` | Explain login → module |
| `figures/fig-03-ticket-lifecycle.png` | Ticket module week |
| `figures/fig-04-courier-flow.png` | Courier module week |
| `figures/fig-05-job-scheduler-flow.png` | Planning module |

If figures are missing from your checkout, regenerate or copy from the documentation maintainer; the manual **references** these paths so PDF export includes them when Pandoc/ImageMagick pipelines are configured.

---

## Part G — Handover checklist (shift change)

- [ ] All **assigned** tickets have **status** reflecting reality  
- [ ] **Courier** packets in desk match **digital** assignee  
- [ ] **Notifications** triaged or noted in **shift log**  
- [ ] **Logged out** on shared PC  

---

*End of user manual appendix.*
