# SYSCO Web — Scenario Library (training cases for non-IT staff)

**Purpose:** Provide **many short stories** you can use in **role-playing**, **acceptance testing**, or **help-desk scripts**. Each scenario states **starting state**, **steps**, and **expected outcome**. Adapt names and references to your training database.

**Conventions:** “User” means the trainee. “System” means SYSCO Web. “SOP” means your local written procedure.

---

## A. Verifier / contrôleur scenarios (1–15)

### Scenario A1 — Morning triage

**Starting state:** User has five tickets in **Mon travail**, all “assigned”.  
**Steps:** Open oldest; read description; if complete, add comment “Contrôle OK — pièces conformes”; if incomplete, request missing document via comment and set status per SOP.  
**Expected:** Each ticket has **dated** comment; statuses reflect truth.

### Scenario A2 — Priority inversion

**Starting state:** Two tickets: one **normal**, one **urgent** but newer.  
**Steps:** User explains aloud why **urgent** is treated first; processes urgent within session.  
**Expected:** Supervisor agrees with ordering rationale in debrief.

### Scenario A3 — Ambiguous attachment

**Starting state:** Ticket has PDF titled `scan.pdf`.  
**Steps:** User opens PDF; if content unclear, asks author via **comment** referencing page.  
**Expected:** No silent approval.

### Scenario A4 — Escalation threshold

**Starting state:** Ticket blocked > 48h per local rule.  
**Steps:** User escalates with **summary** of attempts.  
**Expected:** Escalation record shows **continuity**.

### Scenario A5 — Duplicate suspicion

**Starting state:** Two tickets with similar subject.  
**Steps:** User checks **genealogy** / references; if duplicate, follows **merge** SOP (if permitted).  
**Expected:** Single source of truth.

### Scenario A6 — Language barrier in description

**Starting state:** Description in non-official language.  
**Steps:** User requests translation via comment; does not guess legal meaning.  
**Expected:** Supervisor provides translation channel.

### Scenario A7 — After lunch continuity

**Starting state:** User half-finished comment before break.  
**Steps:** User reopens ticket; completes or deletes draft comment; saves.  
**Expected:** No half-sentence published.

### Scenario A8 — Phone call instruction

**Starting state:** Supervisor phones: “Pause ticket 123”.  
**Steps:** User adds comment “Pause demandée par [nom] — [heure]”; adjusts status if allowed.  
**Expected:** Audit trail shows **phone order** captured.

### Scenario A9 — Wrong assignee visible

**Starting state:** Ticket shows colleague assignee but work landed on user’s desk physically.  
**Steps:** User does not silently reassign; notifies supervisor.  
**Expected:** Correct assignment after admin fix.

### Scenario A10 — Sensitive mention in comment

**Starting state:** User about to paste national ID in comment.  
**Steps:** User stops; attaches redacted PDF per policy instead.  
**Expected:** Comment contains **no** excessive PII.

### Scenario A11 — SLA colour coding

**Starting state:** Dashboard shows red badge.  
**Steps:** User opens underlying list; treats red rows first.  
**Expected:** Red count decreases by end of day or documented escalation.

### Scenario A12 — Batch of similar tickets

**Starting state:** Ten tickets same category.  
**Steps:** User uses **template comment** fragments (externally approved) sparingly; personalises each.  
**Expected:** No identical comments if policy forbids.

### Scenario A13 — Return from leave

**Starting state:** User back after one week.  
**Steps:** User filters **Mon travail** by date; processes backlog in order.  
**Expected:** No ticket untouched > N days post-return.

### Scenario A14 — Printer failure

**Starting state:** User needs paper printout for signature.  
**Steps:** User exports/prints alternate route; notes in ticket “signature papier suivra”.  
**Expected:** Traceability maintained.

### Scenario A15 — End of day

**Starting state:** Two tickets mid-edit.  
**Steps:** User saves or cancels consciously; logs out.  
**Expected:** No orphan dialogs next morning.

---

## B. Secretary scenarios (16–28)

### Scenario B1 — Front desk rush

**Starting state:** Three couriers arrive together.  
**Steps:** Register **in arrival order**; handover sequentially.  
**Expected:** Digital order matches physical order.

### Scenario B2 — Missing label

**Starting state:** Packet has damaged label.  
**Steps:** Create record with **best-effort** reference + photo + supervisor flag.  
**Expected:** Packet quarantined until identified.

### Scenario B3 — VIP packet

**Starting state:** Verbal “urgent” without written mark.  
**Steps:** Secretary follows SOP for VIP; does not invent priority codes.  
**Expected:** Documented authorisation.

### Scenario B4 — Wrong recipient signature

**Starting state:** Signer not in system.  
**Steps:** Do not force match; record actual name in comment; supervisor decides.  
**Expected:** Integrity preserved.

### Scenario B5 — End-of-day inventory

**Starting state:** Desk must be clear.  
**Steps:** Compare physical pile to **in-transit** digital list.  
**Expected:** Zero unexplained deltas.

### Scenario B6 — Duplicate registration fear

**Starting state:** User thinks packet already registered.  
**Steps:** Search by reference; if found, update; if not, register.  
**Expected:** No double barcode.

### Scenario B7 — Inter-unit feud

**Starting state:** Unit A refuses receipt.  
**Steps:** Secretary logs dispute comment; does not delete record.  
**Expected:** Escalation path triggered.

### Scenario B8 — Training observer

**Starting state:** Trainee watches.  
**Steps:** Secretary narrates each click; trainee repeats on dummy packet.  
**Expected:** Trainee performs solo next day.

### Scenario B9 — Power cut mid-save

**Starting state:** Browser closed abruptly.  
**Steps:** Reopen; verify if row saved; redo if needed.  
**Expected:** Consistency restored.

### Scenario B10 — Holiday cover

**Starting state:** Substitute secretary unfamiliar.  
**Steps:** Use **checklist** from User Manual Appendix Part G.  
**Expected:** Handover note signed.

### Scenario B11 — Bulk delivery manifest

**Starting state:** Paper manifest with 20 lines.  
**Steps:** Register **each** or batch import if tool exists — follow trainer.  
**Expected:** Manifest matches system.

### Scenario B12 — Refused dangerous goods

**Starting state:** Suspicious content.  
**Steps:** **Stop**; security protocol; no heroics.  
**Expected:** Incident number filed.

### Scenario B13 — Archive request

**Starting state:** Old packets to archive.  
**Steps:** Follow records SOP; may require status **closed** digitally first.  
**Expected:** Legal retention satisfied.

---

## C. Courier / courrier scenarios (29–38)

### Scenario C1 — First solo route

**Starting state:** New courier account.  
**Steps:** Login; open portal; accept assigned route list.  
**Expected:** All pickups acknowledged.

### Scenario C2 — Broken vehicle

**Starting state:** Cannot complete route.  
**Steps:** Notify supervisor from phone; update statuses **honestly**.  
**Expected:** No silent “delivered”.

### Scenario C3 — Recipient absent

**Starting state:** No signature possible.  
**Steps:** Record attempted delivery per SOP; return packet trace.  
**Expected:** Chain intact.

### Scenario C4 — Badge forgotten

**Starting state:** Cannot login at facility gate.  
**Steps:** Physical security first; IT second.  
**Expected:** No borrowed credentials.

### Scenario C5 — Lost device

**Starting state:** Phone with app bookmarks lost.  
**Steps:** Report loss; passwords rotated if policy says.  
**Expected:** Risk logged.

### Scenario C6 — Weather delay

**Starting state:** Storm delays.  
**Steps:** Update ETAs in comments if feature exists; else comment on ticket.  
**Expected:** Stakeholders informed.

### Scenario C7 — Friendly recipient offers coffee

**Starting state:** Social pause risks delay.  
**Steps:** Politely defer; complete scan first.  
**Expected:** Professional boundary.

### Scenario C8 — Package swap suspicion

**Starting state:** Two similar boxes.  
**Steps:** Re-scan barcodes; do not assume.  
**Expected:** Correct mapping.

### Scenario C9 — Night shift handover

**Starting state:** Digital shows pending; physical shows empty.  
**Steps:** **Stop**; reconcile before driving.  
**Expected:** Investigation note.

### Scenario C10 — Celebratory day high volume

**Starting state:** Double packets.  
**Steps:** Prioritise **time-critical** per manifest tags.  
**Expected:** SLA hits maintained.

---

## D. Director / sous-directeur scenarios (39–48)

### Scenario D1 — Monday council prep

**Starting state:** Need ticket backlog summary.  
**Steps:** Export or screenshot dashboard; redact personal data for council pack.  
**Expected:** Pack approved by comms.

### Scenario D2 — Media inquiry

**Starting state:** Journalist asks for case status.  
**Steps:** **No** ad-hoc export; route to **communications**.  
**Expected:** Legal reply only.

### Scenario D3 — HR disciplinary needing logs

**Starting state:** HR requests user activity.  
**Steps:** Formal request letter; run audits under supervision.  
**Expected:** GDPR trail.

### Scenario D4 — Strategic pivot

**Starting state:** New priority programme announced.  
**Steps:** Director adjusts **filters** and **assignments** with leads.  
**Expected:** Team understands why.

### Scenario D5 — Cross-direction dispute

**Starting state:** Two directions claim same ticket.  
**Steps:** Director uses **escalation** tools; documents decision.  
**Expected:** Single owner.

### Scenario D6 — Budget question

**Starting state:** Finance asks “how many closures this quarter?”.  
**Steps:** Use reporting module if present; else define query with IT.  
**Expected:** Repeatable metric.

### Scenario D7 — whistleblower hint

**Starting state:** Anonymous note on desk.  
**Steps:** **Do not** enter PII casually; follow whistleblower protocol.  
**Expected:** Protected channel.

### Scenario D8 — IT migration weekend

**Starting state:** System down.  
**Steps:** Use **paper fallback** SOP; back-enter later if commanded.  
**Expected:** No data loss by negligence.

### Scenario D9 — Excellence visit

**Starting state:** External auditors arriving.  
**Steps:** Demo **login audit** and **ticket history** on dummy data.  
**Expected:** Confidence gained.

### Scenario D10 — Emotional case

**Starting state:** Staff upset by graphic attachment.  
**Steps:** Wellness support; blur policy for previews if available.  
**Expected:** Duty of care.

---

## E. IT / admin scenarios (49–55)

### Scenario E1 — New hire onboarding

**Starting state:** HR sends ticket with start date.  
**Steps:** Create user; least privilege; test login on staging.  
**Expected:** Day-one access works.

### Scenario E2 — Leaver offboarding

**Starting state:** Last day.  
**Steps:** Deactivate; preserve historical references; rotate shared aliases.  
**Expected:** No orphan admin.

### Scenario E3 — Permission mistake

**Starting state:** User sees admin module wrongly.  
**Steps:** Revoke; document root cause; patch process.  
**Expected:** Incident closed with lesson.

### Scenario E4 — DB backup drill

**Starting state:** Scheduled test.  
**Steps:** Restore copy; smoke login; discard copy securely.  
**Expected:** RTO/RPO recorded.

### Scenario E5 — Log flood

**Starting state:** Disk alert.  
**Steps:** Rotate logs; tune noisy logger; schedule fix.  
**Expected:** Service stable.

### Scenario E6 — Certificate expiry

**Starting state:** HTTPS warning.  
**Steps:** Renew cert at reverse proxy; verify chain.  
**Expected:** Users stop bypass warnings.

### Scenario E7 — Migration Flyway fail

**Starting state:** Deploy aborts.  
**Steps:** Stop traffic; repair script; never edit shipped migration.  
**Expected:** Clean `flyway_schema_history`.

---

## F. Mixed / edge scenarios (56–60)

### Scenario F1 — Joint task force

**Starting state:** External officers need read-only.  
**Steps:** Issue accounts with narrow scope; time-bound.  
**Expected:** Auto expiry calendar entry.

### Scenario F2 — Litigation hold

**Starting state:** Legal says “do not delete X”.  
**Steps:** Flag records; block destructive ops.  
**Expected:** Compliance sign-off.

### Scenario F3 — Pandemic remote

**Starting state:** All remote.  
**Steps:** VPN discipline; no local downloads of bulk PII.  
**Expected:** DLP logs clean.

### Scenario F4 — Fire drill

**Starting state:** Building evacuated mid-ticket.  
**Steps:** Safety first; reconcile digital state after return.  
**Expected:** Honest timestamps.

### Scenario F5 — National holiday partial crew

**Starting state:** Skeleton staff.  
**Steps:** Prioritise **safety-critical** queues only.  
**Expected:** Public communication aligned.

---

## Annex — How trainers pick scenarios

| If training… | Use block… | Duration |
|--------------|------------|----------|
| New verifiers | A | 2 h |
| Secretaries + couriers | B + C | 3 h |
| Leadership | D | 1 h |
| IT | E | 2 h |

---

*End of scenario library.*
