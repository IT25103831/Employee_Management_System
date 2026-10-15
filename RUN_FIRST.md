> **2026-10-02 update:** Read `FIX_REPORT.md` first. For an existing database already on migration 001, apply only `database/002_people_workflow.sql`, then deploy the rebuilt `dist/EmployeeManagementSystem.war`. Do not run the original reset/seed script. Older verification reports describe the previous delivery.

# Run the upgraded Group 51 project

Stack preserved: JDK 17, Maven, SQL Server, Tomcat 10.1, JDBC and JSP.

## 1. Database

Use SQL Server / SQL Express with TCP/IP enabled. In SSMS:

- **New disposable development database:** run `database/EmployeeManagementDB.sql`, then `database/001_scope_upgrade.sql`.
- **Existing database with data:** back it up, then run **only** `database/001_scope_upgrade.sql`.

The original setup script deletes and recreates tables. The upgrade adds the required fields and replaces the payroll procedure without deleting business records. It is intended to be repeatable; database execution remains a local verification gate.

## 2. Connection

Copy `src/main/resources/database.properties.example` to `src/main/resources/database.properties`.
Set the actual SQL Server TCP port, SQL username and password. The example password is a placeholder.

Alternatively set these environment variables for Tomcat (useful with the included prebuilt WAR):

```text
EMS_DB_URL=jdbc:sqlserver://localhost:1433;databaseName=EmployeeManagementDB;encrypt=true;trustServerCertificate=true;loginTimeout=5
EMS_DB_USER=your_sql_login
EMS_DB_PASSWORD=your_sql_password
```

The `trustServerCertificate=true` example is for local development. For a hosted deployment use a trusted certificate, HTTPS and an appropriately restricted database login. Keep SQL Server and the JVM on the same timezone; use `-Duser.timezone=Asia/Colombo` in Tomcat's VM options when appropriate.

## 3. Build and deploy

From the folder containing `pom.xml`:

```bash
mvn clean package
```

This runs the non-database tests and builds `target/EmployeeManagementSystem.war`. SQL integration tests are skipped unless explicitly enabled as described in `TESTING.md`.

Copy the WAR to `apache-tomcat-10.1.x/webapps/`, then run Tomcat's `bin/startup.bat` (Windows) or `bin/startup.sh` (Linux/macOS). A compiled WAR is also included in `dist/`; it contains no database password and requires the environment variables above.

Open: http://localhost:8080/EmployeeManagementSystem/login

If using Smart Tomcat in IntelliJ: JDK 17, Tomcat 10.1, deployment directory `src/main/webapp`, module classpath `employee-management-system`, context `/EmployeeManagementSystem`. Use the configured HTTP port in the URL.

## 4. Demo accounts

Password for the original demo accounts: `Demo@123`.

| Username | Main function |
|---|---|
| employee | Own attendance, leave, reviews and finalized payslips |
| hrmanager | Employee profiles and second-stage leave approval |
| attendance | Shift assignment, attendance records/corrections and summaries |
| supervisor | Department leave decisions, balances and history |
| itadmin | Accounts, roles, reset/unlock and audit |
| operations | Reviews, KPIs and department performance summaries |
| payroll | Monthly salary drafts, finalization, reports and payslips |

New accounts and resets use an administrator-entered temporary password, expiring after 24 hours, followed by mandatory password change. Demo accounts retain their original behavior.

## 5. First workflows

1. HR creates a profile and a basic Employee login account together. Current-year balances are created from active leave types.
2. IT handles role changes and account security; Attendance can optionally assign a schedule. Existing seeded staff already have open-ended assignments, so overlapping assignments are rejected.
3. Employee logs in, changes the temporary password, then clocks in/out or applies for leave. A shift is not required.
4. Supervisor sees departmental balances and records a reason for approval/rejection. Annual/Medical/Unpaid leave escalates to HR; Casual leave completes at supervisor level.
5. Operations creates a review draft, updates/finalizes it, and tracks a goal to its target.
6. Payroll creates a complete-calendar-month draft, adjusts amounts, then finalizes it. Only finalized runs issue payslips. The original one-step process/finalize action is retained.
7. Read the verification report before declaring all 24 PBIs complete.

## Troubleshooting

- Login error: check database, TCP port, configuration and both SQL scripts.
- Invalid column/procedure error after upgrade: confirm `001_scope_upgrade.sql` completed and restart Tomcat.
- Blank leave options/balances in a new calendar year: an administrator must provision the new year's entitlements. The application does not invent carry-forward policy.
- Clock-in rejected: check employee status, duplicate attendance, approved leave and finalized payroll. A shift is optional; when assigned, its working date and time window remain enforced. Clock-in opens two hours before the scheduled start and closes at shift end.
- Wrong Tomcat / `javax` errors: use Tomcat 10.1, not 9.
- Windows authentication only: configure a SQL login for the supplied JDBC example or configure your organization's supported JDBC integrated authentication separately.
