# PeopleSphere — targeted fix report (2026-10-02)

Modified the supplied project in place. No original files were removed. The Java 17 / Maven WAR / Jakarta Servlet+JSP / Tomcat 10.1 / SQL Server structure is preserved. The original SQL setup and migration 001, payroll stored procedure and PayrollService calculations are unchanged. No working database was connected to, reset or modified in this environment.

## Deployment

1. Back up the existing SQL Server database. If migration 001 is already applied, run **only `database/002_people_workflow.sql`** in SSMS. Otherwise apply 001 followed by 002. **Do not run `EmployeeManagementDB.sql` on an existing database: it is the original destructive setup/seed script.**
2. Keep the existing database configuration. Deploy the rebuilt `dist/EmployeeManagementSystem.war` to the existing Tomcat 10.1 / Java 17 setup. Existing configuration files were retained byte-for-byte.
3. Restart Tomcat. Run the local acceptance checklist in `TESTING.md`, including actual login and SQL-backed workflows.
4. Build from source with `mvn clean package`. Test SQL workflows only in the explicitly guarded disposable `EMSRegression` database, using the commands in `TESTING.md`.

## Features fixed

| Area | Changes |
|---|---|
| Employee status | HR edit has Active/Inactive selection. Atomic update, profile history, audit and session revocation preserve IDs and relationships. New deactivation uses employment status as the login block, retaining independent IT account status. Legacy profile-driven account deactivation is restored only where audit evidence identifies it and no later IT deactivation overrides it. Independently inactive/locked accounts still require IT activation/unlock. |
| HR creation | Profile, entitlements and linked temporary login are created in one transaction. Role is selected by the server as EMPLOYEE. Forged role parameters are rejected. Role changes remain exclusively in IT User Access Management. |
| HR Attendance | Removed navigation, page access, mutations, CSV exports and dashboard attendance summaries. Authorized employee/coordinator/supervisor/payroll attendance access is preserved. |
| Optional shifts | No-shift daily clock-in/out works, with duplicate, approved-leave, active-status and finalized-payroll checks. Assigned shifts retain their existing time windows, overnight dates and breaks. Manual corrections also allow an unassigned day. Leave no longer requires a shift; balance, dates, overlap, ownership and Supervisor -> HR decisions are retained. Shift tables and scheduled absence detection remain intact. |
| My Account | Own full name, code, NIC, birth date, email, phone, address, department, designation, employment date/status. Photo upload/change persists in SQL Server with a foreign key to the logged-in employee. Own-only endpoint, CSRF, decoded image validation, 2 MB/4096px limits, metadata stripping and PNG re-encoding. No user-selected file path. |
| Performance | Employee selector hidden; foreign employeeId requests rejected, own finalized history/goals retained, manager-only writes enforced. Employee exports of staff performance remain forbidden. |
| Payroll access | Employee finalized records and own payslips only. No access to payroll run lists or salary/processing/draft/lifecycle writes. Existing record-level SQL scope retained. |
| Salary edits | Existing draft edits and effective-dated salary structures retained. Salary form is available outside payslip rows, including before a payroll exists. Server-generated confirmation popup shows employee, old/new salary, difference, Increase/Decrease, Confirm/Cancel. Save rechecks the old salary under transaction locks. Audit now records before/after amounts. Finalized history remains immutable. |
| Tasks | Added within Performance, alongside existing Goals/KPIs/reviews. Operations assigns title/details/deadline/instructions to one active employee. Employee sees only own assignments, completes with comments. Operations reviews with feedback/rating, receives completion notification and may delete with a reason. Deletion is soft, preserving history and audit. |
| UI | Existing layout/colors retained. Consistent system-ui sans-serif headings/forms/navigation. Project/demo identifiers and marketing phrases removed from user-facing pages and payslips. Session/database identity remains dynamic; legitimate stored names are retained. Draft salary labels use employee code/name. |
| Encoding | Explicit UTF-8 request, response and JSP source decoding; redundant double escaping removed. Employee name/address storage widened to NVARCHAR. Payslip WinAnsi punctuation is encoded correctly; Tamil/Sinhala names use JDK-shaped vector outlines with Unicode ActualText and bundled licensed Noto fonts. |

## Database changes

`database/002_people_workflow.sql` is repeatable and transactional:

- Adds `employee_photos` (employee foreign key, bounded PNG bytes, updated timestamp).
- Adds `employee_tasks` (employee/user foreign keys, lifecycle timestamps, comments, evaluation, soft-deletion reason) and an employee/status/deadline index.
- Widens `employees.full_name` VARCHAR(120) to NVARCHAR(120), and `employees.address` VARCHAR(250) to NVARCHAR(250), preserving values.
- Does not delete records, reset tables, modify salary calculations, or alter existing shift/payroll/attendance relationships.

## Verification performed

- Original baseline: 92 discovered, 75 passed, 17 SQL tests skipped; no failures.
- Final `mvn clean package`: **BUILD SUCCESS; 132 discovered, 110 passed, 22 SQL tests skipped; zero failures/errors.**
- Embedded Tomcat 10.1: real annotated application startup/login page, protected-route redirects, logout CSRF, and error routing.
- 20 Jasper page scenarios: existing pages, all task states, profile, HR dashboard, salary confirmation; HTML escaping and Unicode content assertions.
- 23 real HTTP authorization tests with injected authenticated sessions: HR Attendance page/exports, employee forged profile/performance IDs, restricted task/performance/payroll writes and forged HR role assignment.
- Existing 20 authorization/CSRF tests, 29 business-validation tests, payroll math tests, password/CSV/PDF tests, plus image decoding/size/type tests.
- Unicode payslip smoke check: generated a fixture PDF, rendered it with Poppler, visually checked Tamil/Sinhala/Latin names and verified text extraction. This was a fixture check, not live payroll data.
- Original-file hash comparison: no original files removed. Original setup SQL, migration 001, database configuration and PayrollService unchanged. `dist` WAR rebuilt from the final sources.
- Evidence: `docs/verification/2026-10-02/` includes baseline/final logs, per-suite results, changed file manifest and source patch. Older verification documents are retained as historical records.

## Remaining verification / limitations

- **SQL Server was unavailable. The 22 database integration cases are compiled but skipped, not passed.** Migration execution, real credentials/login, transactions/concurrency, photo persistence, clocking/leave workflow, task lifecycle and salary writes need the supplied SQL suite and actual Tomcat/SQL Server acceptance run. No claim of full production runtime verification is made.
- Existing already-corrupted database text cannot be reconstructed safely by changing encoding. Restore such values from their authoritative source if present; this update does not rewrite legitimate employee names.
- New temporary accounts retain the existing 24-hour expiry and forced password-change policy. New-year leave entitlement provisioning remains the existing administrative responsibility.
- Unassigned clocking has no scheduled break deduction or automatic shift-based absence creation; scheduled behavior is unchanged. A formally assigned shift retains its existing time-window requirements.

## Files changed

- `CHANGELOG.md`
- `README.md`
- `RUN_FIRST.md`
- `TESTING.md`
- `dist/EmployeeManagementSystem.war`
- `src/main/java/lk/sliit/ems/controller/AppServlet.java`
- `src/main/java/lk/sliit/ems/controller/ExportServlet.java`
- `src/main/java/lk/sliit/ems/dao/EMSRepository.java`
- `src/main/java/lk/sliit/ems/filter/SecurityFilter.java`
- `src/main/java/lk/sliit/ems/util/PdfUtil.java`
- `src/main/webapp/WEB-INF/views/dashboard.jsp`
- `src/main/webapp/WEB-INF/views/includes/foot.jsp`
- `src/main/webapp/WEB-INF/views/includes/sidebar.jsp`
- `src/main/webapp/WEB-INF/views/includes/topbar.jsp`
- `src/main/webapp/WEB-INF/views/login.jsp`
- `src/main/webapp/WEB-INF/views/module.jsp`
- `src/main/webapp/WEB-INF/web.xml`
- `src/main/webapp/assets/css/app.css`
- `src/main/webapp/index.jsp`
- `src/test/java/lk/sliit/ems/integration/JspRenderingTest.java`
- `src/test/java/lk/sliit/ems/integration/SqlServerWorkflowTest.java`
- `src/test/java/lk/sliit/ems/util/SecurityUtilitiesTest.java`

## Files added

- `database/002_people_workflow.sql`
- `src/main/java/lk/sliit/ems/controller/ProfilePhotoServlet.java`
- `src/main/java/lk/sliit/ems/util/PdfUnicode.java`
- `src/main/java/lk/sliit/ems/util/ProfileImage.java`
- `src/main/resources/fonts/NotoSansSinhala.ttf`
- `src/main/resources/fonts/NotoSansTamil.ttf`
- `src/main/resources/fonts/OFL-Sinhala.txt`
- `src/main/resources/fonts/OFL-Tamil.txt`
- `src/main/resources/fonts/README.txt`
- `src/main/webapp/WEB-INF/views/includes/profile.jsp`
- `src/main/webapp/WEB-INF/views/includes/tasks.jsp`
- `src/main/webapp/WEB-INF/views/salary-confirm.jsp`
- `src/test/java/lk/sliit/ems/controller/HttpAccessRegressionTest.java`
- `src/test/java/lk/sliit/ems/util/ProfileImageTest.java`
- `FIX_REPORT.md`
- `docs/verification/2026-10-02/*` (new evidence)
