# Effective role access

Server-side `AppServlet`/`ExportServlet` permissions are authoritative. Repository queries additionally scope employees and supervisors. Menu visibility alone grants no permission.

| Function | Employee | HR Manager | Attendance Coordinator | Department Supervisor | IT Administrator | Payroll Specialist | Operations Manager |
|---|---|---|---|---|---|---|---|
| Dashboard/account/own alerts | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Employee profile module | No | Manage | No | No | Read | No | Read |
| Clock in/out | Own | No | No | No | No | No | No |
| Attendance records/summaries | Own read | Read | Create/correct/void | Department read | No | Read | No |
| Shift assignments | No | No | Assign/end | No | No | No | No |
| Leave request | Own apply/edit pending/cancel | HR decision/read | No | Department decision/read | No | No | No |
| Leave balances/history | Own | Read all | No | Department | No | No | No |
| Reviews/goals | Own read | Read | No | Department read | No | No | Manage across departments |
| Department performance summary | No | No | No | No | No | No | Read/export |
| Payroll/payslips | Own finalized | No | No | No | No | Salary/drafts/finalize | Read |
| Accounts and audit | No | No | No | No | Manage | No | No |
| CSV exports | No | Employee/attendance/leave/performance | Attendance | Team attendance/leave/performance | Audit | Payroll | Employee/performance/payroll |

Performance writes moved from Supervisor to Operations Manager to match PBI-17/18 and the explicit upgrade scope. Supervisor team read access remains. Pending leave is employee-editable only before the supervisor decision; self-approval is prohibited. Account administrators cannot change their own administrative role/status through these controls.
