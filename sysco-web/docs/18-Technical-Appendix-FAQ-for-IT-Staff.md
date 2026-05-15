# SYSCO Web — Technical FAQ for IT staff

**Audience:** Support engineers integrating or operating SYSCO Web.

---

## Build & run

**Q: Which Java version?**  
A: Java **17** (see `pom.xml`).

**Q: How do I run locally?**  
A: From the multi-module repo root, use Maven to run the `sysco-web` module per parent POM conventions (`mvn -pl sysco-web spring-boot:run` or your team’s wrapper script).

---

## Database

**Q: H2 vs Oracle?**  
A: Developer default often uses **H2 file**; production profile may enable **Oracle** JDBC settings in `application.yml`.

**Q: Flyway failed mid-migration.**  
A: Do **not** delete rows from `flyway_schema_history` without a **DBA** decision. Restore snapshot or apply a repair migration forward.

---

## Security

**Q: User sees empty sidebar except Chat.**  
A: Check **authorities**: missing `ROLE_*` or missing module `*_READ` permissions; compare with `WebSyscoPermissions` switch cases.

**Q: Admin cannot see File Share Management.**  
A: Nav path `/app/file-share-management` requires **`ADMIN`** or **`SUPER_ADMIN`** role specifically.

---

## WebSockets

**Q: Notifications work locally but not behind NGINX.**  
A: Verify **WebSocket upgrade** headers and **sticky sessions** if multiple nodes.

---

## Files

**Q: Uploads disappear after restart.**  
A: Ensure `sysco.uploads.directory` points to **persistent** volume, not ephemeral container layer.

---

## Performance

**Q: Ticket list slow.**  
A: Check DB indexes; narrow default filters; avoid loading huge lazy collections in Thymeleaf — profile Hibernate SQL in staging.

---

## Logs & PII

**Q: Are passwords logged?**  
A: They must **not** be; if custom logging added, scrub immediately.

---

## Upgrades

**Q: Spring Boot minor bump broke something.**  
A: Read **release notes**; check Jakarta namespace packages; rerun full integration tests.

---

## Handover minimum

**Q: What docs should ops read first?**  
A: `01`, `07`, `10`, `15` in this folder — then environment-specific runbooks.

---

## Internationalisation

**Q: Keys appear instead of French labels.**  
A: Ensure `messages_fr.properties` is on the classpath and `MessageSource` basename includes `messages`; verify user locale resolver.

---

## Guided tour

**Q: Tour does not start.**  
A: Check `GuidedTourService` payload and that Driver.js CDN is reachable from client networks; ad blockers may interfere.

---

## Scheduled jobs

**Q: Reminders fire late.**  
A: Validate server clock (NTP), poll interval `sysco.scheduler.jobs-poll-ms`, and transaction boundaries in `AutomatedJobProcessingService`.

---

## Ticket merge

**Q: Users report merge dialog confusing.**  
A: Training issue first; if UX bug, capture browser console errors and steps to reproduce.

---

## CSV exports

**Q: Excel mangles accented characters.**  
A: Instruct users to import CSV via Excel **Data → From Text** with UTF-8; or export BOM-enabled CSV if implemented.

---

## Container deployment

**Q: Health check passes but users see 502.**  
A: Check reverse proxy upstream timeouts; WebSocket routes may need longer read timeout.

---

## Contact for escalations

Route **security incidents** to the institution’s **CERT**; route **availability** to operations; route **permission logic bugs** to application maintainers with `WebSyscoPermissions` reproduction matrix.

---

*End of IT FAQ appendix.*

