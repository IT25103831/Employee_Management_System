# Member File Ownership

The system is already integrated and runnable. To reduce merge conflicts, each member should change only their assigned module files and avoid formatting or rewriting shared files.

| Member | Branch | Owned frontend file | Main operation area |
|---|---|---|---|
| IT25103826 | `feature/employee-profiles` | `assets/css/modules/employee-profile.css` | Employee profile CRUD and history |
| IT25103827 | `feature/attendance-shifts` | `assets/css/modules/attendance-shift.css` | Clocking, shifts and exceptions |
| IT25103828 | `feature/user-access` | `assets/css/modules/user-access.css` | Accounts, roles and security actions |
| IT25103829 | `feature/leave-workflow` | `assets/css/modules/leave-absence.css` | Leave balances and approvals |
| IT25103830 | `feature/performance-kpis` | `assets/css/modules/performance-evaluation.css` | Reviews, goals and KPI progress |
| IT25103831 | `feature/payroll-payslips` | `assets/css/modules/payroll-salary.css` | Payroll processing and payslips |

Paths above are relative to `src/main/webapp/`.

## Shared files requiring coordination

These files affect several modules and must not be edited simultaneously:

- `pom.xml`
- `database/EmployeeManagementDB.sql`
- `src/main/java/lk/sliit/ems/controller/AppServlet.java`
- `src/main/java/lk/sliit/ems/dao/EMSRepository.java`
- `src/main/webapp/WEB-INF/views/module.jsp`
- `src/main/webapp/assets/css/app.css`

If a member needs a shared-file change, create an issue describing the exact change. The integration coordinator assigns one person, merges that pull request first, and then tells the other members to update their branches.

Never invent commits for another member. GitHub history used during the viva should show genuine work by the relevant member.
