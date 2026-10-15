# Group 51 EMS — targeted upgrade and verification report

## Delivery status

The existing Java/JSP/SQL Server application was extended in place. Its source compiles, the WAR builds, and the executed non-database regression tests pass. **This is not a fully database-verified release.** SQL Server failed during native initialization in this execution environment, before accepting connections. Database setup, migration execution, successful logins and persisted business workflows therefore remain unverified. No PBI is marked fully passed merely because code exists.

**Executed result: 75 passed, 0 failed; 17 SQL workflow tests skipped.** The original baseline build also passed both existing tests.

Read `verification/TEST_MATRIX.csv`, `PBI_COMPLIANCE.md`, `TESTING.md` and `RUN_FIRST.md`. The ZIP includes source, the built WAR, tests, an additive upgrade script and run instructions.

## Existing functionality preserved

- Java 17, Maven WAR, Jakarta Servlet 6, Tomcat 10.1, JSP/JSTL, JDBC and SQL Server.
- Existing `EMSRepository`, controllers, services, model and utility classes; original routes and database entities.
- All six modules, seven seeded login roles, sidebar/layout, CSS, assets and original demo accounts.
- Employee create/contact-assignment update/deactivation/history; employee clocking and shift assignment; two-stage leave approvals; reviews/KPIs; one-step payroll processing; CSV, PDF payslips, audit and in-app notifications.
- PBKDF2 passwords, lockout, mandatory password changes, session rotation on login and existing POST CSRF protection.
- Both original payroll unit tests, which passed before and after the changes.
- Original setup SQL remains available for new development databases. It must not be rerun over data you want to preserve.

A source baseline audit and original SHA-256 manifest are included under `verification/`. No original source path was removed.

## Added functionality

1. Automatic, idempotent scheduled absence generation after a shift's end, including overnight end times; runs every five minutes, with catch-up on attendance/summary reads.
2. Weekly (Monday–Sunday) and calendar-month attendance summaries and separate summary CSV export, retaining raw attendance export.
3. Coordinator attendance entry, correction and reversible voiding with adjustment/audit history; scheduled break deduction; late and duplicate checks.
4. Shift assignment listing and controlled ending of an assignment before scheduling its replacement.
5. Supervisor-visible departmental leave entitlements, used/remaining balances and requested days; pending employee edit/cancel actions.
6. Operations Manager review drafts, draft edit/delete/finalize, KPI updates, employee-history filtering/export and consolidated department summaries.
7. Account search/role/status filters, role/email updates, session revocation and temporary-password expiry.
8. Payroll draft generation, amount changes, cancellation and finalization; period-filtered reports; salary-structure entry for new employees/effective months. The original process-and-finalize action remains.
9. Regression tests for validation, hashing, output encoding, unauthorized mutations, JSP rendering, application boot and opt-in SQL workflows.

## Bugs addressed

| Area | Baseline problem | Targeted change |
|---|---|---|
| Profile editing | Department/designation selects defaulted to their first option | Select existing values |
| Profile setup | New profiles had no leave entitlement rows | Initialize current-year rows from active leave types |
| Inactive users | Failed logins could change INACTIVE into LOCKED | Reject inactive accounts without changing their status |
| Sessions | Deactivation/role changes/resets did not invalidate existing sessions | Database-backed status/version check per protected request |
| Leave | Past dates, cross-year balance ambiguity, no reservation for pending requests | Validate dates; split cross-year requests; include pending commitments in balance checks |
| Approval | Concurrent repeated deduction; current year used instead of request year; self-approval | Serializable transaction and update locks, request year, self-approval guard |
| Scope | Supervisors could see other departments' goals, balances or attendance | Department-scoped queries |
| Performance | Supervisor owned writes; Operations Manager could only view | Operations Manager owns writes; supervisor keeps team read access |
| KPI | Negative/excess/backward or completed-goal progress accepted | Bounds and active-state checks |
| Payroll | Duplicate dates accepted under a different label; multiple salary rows could join | Locked overlapping-period check and single full-period salary selection |
| Payroll lifecycle | Every new run immediately finalized | Additional draft lifecycle; final records immutable |
| Exports | Stored spreadsheet formulas could execute when opened | Neutralize formula prefixes and quote CSV cells |
| JSP | Names, reasons and review text rendered without escaping | Escape data in HTML text and attributes |
| Errors | Database/internal messages could reach the page | Generic errors; allowlisted business-validation messages; server-side logging |
| Configuration | Missing properties file stopped initialization | Properties, environment and system-property configuration; bundled password cleared (set your own locally) |

## Validation and policy assumptions

These are application rules, not a claim about statutory HR policy:

- Employee code: 3–20 letters/digits/hyphens. Required fields and maximum lengths; valid email/phone; existing uniqueness constraints retained. Employee age at joining is at least 16; joining date cannot be in the future. Department/designation must be active.
- An assignment interval means **every date in that interval is a working date**, including weekends. There is no holiday table or weekday-pattern model in the supplied schema. No absence is created without a schedule, during approved leave, before employment, for inactive employees, before the shift ends, or for a date locked by finalized payroll.
- Clock-in opens two hours before shift start and closes at shift end. A ten-minute grace period defines lateness. Overnight clock-ins use the shift's starting date. Clock-out must follow an open clock-in within 24 hours. Working hours deduct the scheduled break once and never fall below zero. Original stored historical hours are not rewritten.
- Corrections preserve employee/date identity, record old/new values, require a reason and cannot alter finalized-payroll dates. Voiding retains both the record and its history; it suppresses re-generation of that erroneous date. A coordinator can correct the retained row later.
- Leave is counted in whole scheduled calendar days. Past dates, reversed ranges, cross-year requests, unscheduled days, overlap, inactive employees and insufficient entitlement including pending requests are rejected. New-year entitlements/carry-forward require administrator provisioning; no policy was invented. Only supervisor-pending requests may be edited; pending requests may be cancelled. Finalized approvals are retained. Both approve and reject require a reason. If approval follows automatic absence detection, those automatic rows are voided with adjustment history; approval across finalized payroll is blocked.
- Ratings are 1–5. Reviews require an open, already-started period; open past periods are permitted for delayed reviews. Finalized reviews are immutable; employees see only their finalized feedback. Goals have positive targets, 0–100% weight and nonpast due dates; progress must increase toward the target and stops on completion.
- Department performance averages are the mean of each active employee's finalized-review average; employees without finalized reviews do not contribute a rating. Goal counts are aggregated independently, avoiding multiplication by the review join. History reports retain past/inactive employees.
- Payroll is a **fixed monthly salary model**, using the supplied configurable rates on gross pay. It does not invent unpaid-leave deductions, overtime, proration or attendance-to-salary formulas. Open attendance blocks finalization, and finalized periods lock attendance corrections. Only active employees employed by period start with one salary structure covering the entire month are eligible. Mid-month joiners require an agreed proration policy, not automatic full-month pay.
- Draft amounts are nonnegative and net pay cannot be negative. Overlapping noncancelled runs are blocked. Cancelled payroll is retained but excluded from expenditure lists. Only closed runs issue payslips. Closed payroll cannot be edited/cancelled by these controls.
- Base-salary changes start on a month boundary and preserve preceding salary history. They cannot modify any period already used in draft/closed payroll. Allowances/deductions can be adjusted in a draft; existing configured recurring amounts remain in their original tables.
- The existing password policy is applied consistently: 8–128 characters, uppercase letter and digit. Temporary credentials expire in 24 hours. Passwords are not silently trimmed. The existing reset-token tables remain intact but are unused: this application uses administrator-issued temporary passwords, not emailed reset links.
- An IT administrator cannot change their own role/status through account-management controls. Another administrator is required. Deactivating a profile also deactivates linked accounts. Unlocking is only for LOCKED accounts. Reset does not activate an INACTIVE account.

## Database changes

Apply `database/001_scope_upgrade.sql` after backing up an existing database:

- Add `user_accounts.security_version` (default 0) and nullable `temporary_password_expires_at`.
- Extend attendance status to include `VOID`; add timestamp ordering constraint. Existing reversed timestamps stop the upgrade for correction instead of being silently rewritten.
- Align `PERFORMANCE_REVIEW` role-permission mapping with Operations Manager while retaining the permission and roles themselves.
- `CREATE OR ALTER sp_ProcessPayroll` retains its name and original four parameters; adds optional `@Finalize BIT=1`. Existing callers continue to finalize. Adds full-month checks, overlapping-run locking, salary selection, nonnegative-net and open-attendance guards, and draft support.

No employee/user/business table or column is dropped; no business data is deleted. The old attendance status constraint is replaced to extend the existing lifecycle. The upgrade needs sufficient DDL/procedure permissions. SQL execution and repeatability tests are supplied but were not executable here.

## Security verification limits

Executed: invalid-CSRF rejection, unauthenticated protected-route redirects, 18 role/action denial combinations, password hashing/seed verification, CSV formula neutralization, stored-text escaping in 15 rendered JSP cases, and actual login-page/403/404 behavior.

Not executed with SQL Server: successful logins for all roles, stale-session revocation against real data, successful password resets, row-scope enforcement against persisted records, SQL transactional races, or authorized CRUD through the complete browser/database stack. Automated fixtures are explicitly isolated in test code and are not packaged into the WAR.

## Remaining limitations and acceptance gates

- All SQL-backed PBIs need the local integration suite plus the interactive role checklist before acceptance. The built WAR is not evidence of database correctness.
- Both tested native SQL Server 2022 installations failed in SQLPAL initialization (`0xc0000142`) in this environment. No supplied or production database was accessed.
- No end-to-end browser session against a working database, load test, external penetration test or backup/restore exercise was possible here.
- Absence scheduling runs while the web application is running and catches up after downtime; it is not an external SQL Agent job.
- The supplied model has no holidays/weekday patterns, entitlement rollover, payroll proration, tax rules or overtime policy. These were not guessed. EPF/ETF demo rates were preserved, not independently certified for real payroll.
- The PDF renderer retains the original simple Helvetica/Latin-1 design; Unicode names and unusually long fields need a font-capable PDF library if required. CSV and web pages use UTF-8.
- All-history attendance/leave/audit exports avoid the original silent row caps; very large installations should add pagination/streaming. Existing recent-review and payroll-run UI caps remain, while performance history and period payroll reports are untruncated.
- No email integration was added; existing in-app notifications remain.

## Run and acceptance

Use `RUN_FIRST.md` for exact SQL/configuration/build/deployment instructions and demo roles. Use `TESTING.md` to run the SQL suite **only against `EMSRegression`**, then complete the interactive checklist. Archive the resulting test evidence before marking PBI-01–PBI-24 accepted.
