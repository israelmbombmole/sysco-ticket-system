# Oracle Multi-PC Rollout (SYSCO)

Use this checklist when this machine is the Oracle server and 20+ clients connect to it.

## 1) Server one-time setup

1. Ensure Oracle XE is running (`XEPDB1`, listener port `1521`).
2. Open inbound firewall rule: TCP `1521`.
3. Create app schema user (SQL*Plus as SYSTEM):

```sql
CREATE USER SYSCO_APP IDENTIFIED BY "ChangeThisStrongPassword"
  DEFAULT TABLESPACE USERS
  QUOTA UNLIMITED ON USERS;

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER TO SYSCO_APP;
```

4. Run the app once with Oracle config to bootstrap schema.

## 2) Server health check script

From PowerShell on server:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\oracle-server-check.ps1
```

## 3) Client rollout steps (repeat per PC)

1. Copy/install SYSCO app.
2. Create `db.properties` in app working folder (or in `sysco.data.dir`).
3. Use server host/IP in JDBC URL (never localhost on clients).

Template:

```properties
db.vendor=oracle
oracle.url=jdbc:oracle:thin:@//SERVER_HOST_OR_IP:1521/XEPDB1
oracle.user=SYSCO_APP
oracle.password=ChangeThisStrongPassword
```

4. Run client test script:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\oracle-client-test.ps1 -ServerHost SERVER_HOST_OR_IP -AppUser SYSCO_APP
```

5. Launch app and verify login.

## 4) Post-rollout checks

- Two different PCs can log in at same time.
- Creating a ticket on PC-A appears on PC-B after refresh.
- Confirm all clients point to same URL host/IP.

## 5) Operations recommendations

- Assign server a static IP or DNS name.
- Configure Oracle services to auto-start.
- Back up Oracle regularly.
- Restrict firewall to office subnet where possible.
