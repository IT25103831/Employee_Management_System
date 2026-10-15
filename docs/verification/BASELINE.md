# Baseline audit (before changes)
Source: supplied Group51_Complete_Integrated_Runnable.zip. Original retained separately while working.

Java 17, Maven WAR, Jakarta Servlet 6 / Tomcat 10.1, JSP/JSTL, JDBC SQL Server.
One repository, five servlets, security filter and startup listener. No ORM or framework migration proposed.
SQL defines 35 linked tables, a payroll procedure, indexes, demo accounts and data; no triggers. Setup script is destructive and must not be used for an existing database.

| Feature | Existing implementation | Audit finding (not a runtime PASS) |
|---|---|---|
| Authentication | PBKDF2, lockout, session rotation, forced password change | Inactive login increments can change INACTIVE to LOCKED; existing sessions never revalidated |
| Profile | Create, contact/assignment update, deactivate, name search/history | Edit select defaults can silently change department; no server format/date checks; designation/status filters missing |
| Attendance | Employee clocking, shift assignment, recent records | Coordinator cannot record/correct; no absence detection or period summaries; supervisor scope missing; no break deduction |
| Leave | Application, two-stage approval, notification/history, balances | Past/cross-year requests allowed; self-approval possible; balance update uses current year; concurrent decisions unsafe; balance UI hidden from supervisor |
| Performance | Finalized reviews, KPI creation/progress, recent list/export | Wrong write role; arbitrary goal progress/department access; no draft lifecycle or department summary |
| Payroll | Procedure calculates and immediately closes run; PDF, CSV | Different labels allow same period twice; no draft lifecycle; no date filters; no attendance calculation despite UI claim |
| Accounts | Create, activate/deactivate/unlock/reset | No role/email update/search; password rules inconsistent; resets reactivate inactive users; no temporary-credential expiry |
| Reports | Role checks, CSV | TOP limits silently truncate; no period filters; spreadsheet formulas not neutralized |
| UI/security | POST CSRF; mixed escaped/unescaped JSP output | Stored XSS possible in feedback/reasons/names; errors expose database messages |
| Tests | PayrollServiceTest (2 cases) | No integration/security regression suite |

Routes preserved: /login, /logout, /app/{dashboard,employees,attendance,leave,performance,payroll,access,reports,notifications,account}, /export, /payslip.
Existing user, employee, audit and lifecycle history records must be retained. No production connection or credentials were supplied.
