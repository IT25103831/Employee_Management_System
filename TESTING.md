# Reproducible verification

## Executed in this delivery

For this update, see `FIX_REPORT.md` and `docs/verification/2026-10-02/`. The following references describe the earlier baseline: `docs/verification/TEST_MATRIX.csv` for individual results, `docs/verification/final-tests.log` for build output and `docs/PBI_COMPLIANCE.md` for scope traceability. Unit, authorization, JSP-rendering and application-boot tests ran. SQL-backed tests were skipped because no SQL Server instance was available in this environment.

```bash
mvn clean test package
```

Requires Java 17 and Maven with access to Maven Central. Embedded Tomcat/Jasper dependencies are **test scope only**; they are not included in the deployed WAR. Tests use ephemeral localhost ports.

`JspRenderingTest` supplies fixture data to real Jasper-rendered JSPs. It verifies rendering/escaping, not database results. `ApplicationBootTest` starts the real annotated application, verifies login/error pages and unauthenticated redirects, and allows database connectivity to be unavailable.

## SQL Server regression suite (not executed here)

Use a dedicated disposable test database, never the group's working database. In SSMS:

```sql
CREATE DATABASE EMSRegression;
```

Set environment variables in the same PowerShell that will run Maven:

```powershell
$env:EMS_INTEGRATION_TESTS = "true"
$env:EMS_TEST_DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=EMSRegression;encrypt=true;trustServerCertificate=true;loginTimeout=5"
$env:EMS_TEST_DB_USER = "your_test_sql_login"
$env:EMS_TEST_DB_PASSWORD = "your_test_sql_password"
mvn '-Dtest=SqlServerWorkflowTest' test
```

Linux/macOS equivalent:

```bash
export EMS_INTEGRATION_TESTS=true
export EMS_TEST_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=EMSRegression;encrypt=true;trustServerCertificate=true;loginTimeout=5'
export EMS_TEST_DB_USER='your_test_sql_login'
export EMS_TEST_DB_PASSWORD='your_test_sql_password'
mvn -Dtest=SqlServerWorkflowTest test
```

The suite verifies `DB_NAME()` is exactly `EMSRegression` before resetting tables. It applies the original setup and upgrade scripts inside this disposable database. Credentials need permissions to reset that test schema. It exercises seeded 2026 review periods/rates; before using it in a different year update the disposable review-period fixtures to match the intended open period.

The test methods run in order because they model linked workflows. A failed setup/early step can cause later dependency failures; fix the earliest failure and rerun from a fresh test database. Success means **no failed or skipped SQL workflow tests**. Unset `EMS_INTEGRATION_TESTS` after the run.

Covered SQL scenarios: seven-role login, wrong credentials/SQL-injection-like username, employee CRUD/filter/entitlements, user create/change/reset/expiry/unlock and revoked sessions, shift overlap, coordinator attendance correction/void, employee clocking duplicates, absence idempotency and summaries, leave apply/update/overlap/cancel, two-stage approval and single deduction, concurrent approval, department isolation, self-approval rejection, review draft/finalize/delete, goal bounds/completion, salary setup, payroll duplicate periods/draft updates/finalization/cancellation/payslip ownership, deactivation, audit, and migration repeatability.

## Interactive acceptance checklist — must be performed locally

- [ ] Back up a copy of the group's database; apply only the non-destructive migrations (001 if needed, then 002) and verify existing records remain.
- [ ] Deploy the generated WAR to Tomcat 10.1 and log in with all seven roles.
- [ ] HR: create a valid profile and Employee account; verify invalid dates/email/phone/duplicates; edit contact/assignment without unintended selection changes; filter department/designation/status; deactivate and reactivate with the same IDs.
- [ ] IT: verify the HR-created account; complete first login/password change; filter accounts; change role; deactivate/reset/unlock; verify old sessions are revoked and audit records exist.
- [ ] Attendance: assign a new employee; reject overlap; end an old assignment and assign its replacement; record/correct/void attendance with reasons; inspect adjustment history in SQL.
- [ ] Employee: clock with no assigned shift and within the scheduled window when assigned; reject double clock-in/clock-out without clock-in; test an overnight shift with the correct working date.
- [ ] Absence: past scheduled day without attendance becomes ABSENT; approved leave, unscheduled dates and future/unended shifts do not. Confirm weekly/monthly counts and break-adjusted hours against source records.
- [ ] Leave: reject past/reversed/cross-year/overlapping/over-balance requests; edit/cancel pending requests. Supervisor sees only the team, balances and days; approve/reject with reason. HR completes escalated approvals; used days change once.
- [ ] Operations: create/edit/delete a draft review, finalize it and reject subsequent editing; create/progress/complete KPI; verify history and department averages manually. Supervisor must not create reviews via a direct POST.
- [ ] Payroll: set salary for a new eligible employee; generate a full-month draft; recalculate allowances/deductions; reject negative net and duplicate period under another label; cancel one draft; finalize another; confirm immutable history and owner-only payslips.
- [ ] Compare a payslip PDF and selected-period CSV with the actual payroll row and configured rates.
- [ ] Check raw attendance CSV, weekly/monthly CSV, employee history and performance summary filters.
- [ ] Check unauthorized direct URLs/actions, CSRF, stored script text and spreadsheet formula payloads.
- [ ] Check notification read/read-all/delete, logout, keyboard access, mobile sidebar/tables, empty lists and error pages.
- [ ] Record actual outputs; mark PBI acceptance only after successful workflows.

## Added acceptance checks for this update

- HR has no Attendance link, page, dashboard attendance figure, or attendance CSV access.
- HR create with forged roleId/role is HTTP 403; basic Employee account is created atomically with its profile. Duplicate account details roll back the profile too.
- Employee status changes retain employee/account IDs, leave and attendance history. IT account locks or independent deactivation stay under IT control.
- With no shift: clock in/out, duplicate rejection, approved-leave rejection, apply leave, Supervisor -> HR approval and balance deduction.
- My Account shows every requested personal field. Try another employee ID, invalid CSRF, PNG/JPEG replacement, oversized and disguised uploads. Restart Tomcat and verify photo persistence.
- Operations assigns a task to a specific active Employee account; employee completes with comments; Operations reviews with feedback/rating; deletion removes it from active views while retaining the record/audit.
- Another employee cannot see or complete the task. Employees cannot assign/review/delete tasks.
- Employees cannot use another employeeId on Performance, export employee reports, or view someone else's payslip; no employee payroll mutations are allowed.
- Salary edit opens Confirm/Cancel with database-derived old/new/difference/type. Cancel makes no change. Test increase/decrease, stale confirmation, invalid amounts, finalized-record rejection, and future effective salary history.
- Check Unicode names/punctuation in login/session/profile/UI/CSV/payslip. Existing corrupt stored text requires correction from an authoritative original; the migration does not guess or overwrite names.

New SQL tests are part of `SqlServerWorkflowTest` (22 cases total), including reactivation, no-shift attendance/leave, approved-leave blocking, task ownership/lifecycle, profile/photo isolation, Unicode and stale salary confirmation. They were not executed here. The HTTP role suite injects test sessions and exercises real servlets; it does not simulate a successful SQL-backed login.
