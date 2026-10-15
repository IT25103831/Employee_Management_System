> **2026-10-02 update:** Read `FIX_REPORT.md` first. For an existing database already on migration 001, apply only `database/002_people_workflow.sql`, then deploy the rebuilt `dist/EmployeeManagementSystem.war`. Do not run the original reset/seed script. Older verification reports describe the previous delivery.

# PeopleSphere - Web-Based Employee Management System

SE2030 group project for **2026-Y2-S1-KU-51**. This Maven WAR application uses JSP, Jakarta Servlets, JDBC, Microsoft SQL Server, JDK 17, and Apache Tomcat 10.1.

## Six modules and ownership

| Member | Student ID | Module | Traceability |
|---|---|---|---|
| Dasanayake W. G. A. N. | IT25103826 | Employee Profile Management | EP01-EP05, PBI-01-PBI-04 |
| Medawala M. M. G. U. S. | IT25103827 | Attendance & Shift Management | AT01-AT05 / AS01, PBI-05-PBI-08 |
| Pradeesh M. | IT25103828 | Role & User Access Management | RA01-RA05 / UA01, PBI-09-PBI-12 |
| Nandasena M. B. | IT25103829 | Leave & Absence Management | LA01-LA05, PBI-13-PBI-16 |
| Ineeshiya W. K. M. M. | IT25103830 | Performance & Evaluation Management | PE01-PE04, PBI-17-PBI-20 |
| Wimalasara A. G. H. L. | IT25103831 | Payroll & Salary Management | PR01-PR05, PBI-21-PBI-24 |

## Requirements

- JDK 17
- Maven 3.9+
- Apache Tomcat 10.1.x
- Microsoft SQL Server / SQL Express and SSMS
- TCP/IP enabled on port 1433 when using the supplied JDBC URL

## Verification status

Read `docs/UPGRADE_REPORT.md` and `docs/PBI_COMPLIANCE.md`. The WAR builds and the non-database regression suite passes. SQL Server workflows require local verification; they are not certified as passing.

## 1. Create or upgrade the database

1. Open SSMS and connect to `localhost\SQLEXPRESS` using Windows Authentication or the configured SQL login.
2. Open `database/EmployeeManagementDB.sql`.
3. Read the reset warning at the top, then execute the complete script once.
4. Run `database/001_scope_upgrade.sql` against that database.
5. Refresh Databases and confirm `EmployeeManagementDB` exists.

For an existing database, back it up and run **only** `database/001_scope_upgrade.sql`. Do not run the original reset script.

The script is intended for initial development setup and resets the project tables whenever it is rerun.

## 2. Configure the connection

Copy `src/main/resources/database.properties.example` to `src/main/resources/database.properties`, then set your SQL Server credentials:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=EmployeeManagementDB;encrypt=true;trustServerCertificate=true
db.username=sa
db.password=CHANGE_ME
db.driver=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

If SQL Express uses another port, update the URL. For GitHub, do not commit the real file; commit only `database.properties.example`.

## 3. Build

```bash
mvn clean package
```

Expected output:

```text
BUILD SUCCESS
target/EmployeeManagementSystem.war
```

## 4A. IntelliJ IDEA Ultimate

1. **File > Open** and select this folder (the one containing `pom.xml`).
2. Select JDK 17 and reload Maven.
3. **Settings > Build, Execution, Deployment > Application Servers**, add Tomcat 10.1.
4. **Run > Edit Configurations > Tomcat Server > Local**.
5. Add `EmployeeManagementSystem:war exploded` under Deployment.
6. Set application context to `/EmployeeManagementSystem`.
7. Run and open `http://localhost:8080/EmployeeManagementSystem/`.

## 4B. IntelliJ Community with Smart Tomcat

1. Install the Smart Tomcat plugin.
2. Add your Tomcat 10.1 directory in **Settings > Tomcat Server**.
3. Create a Smart Tomcat run configuration.
4. Set deployment directory to `src/main/webapp`.
5. Set context path to `/EmployeeManagementSystem`, port `8080`, JDK 17, and module classpath `employee-management-system`.
6. Run and open `http://localhost:8080/EmployeeManagementSystem/`.

## 4C. Eclipse

1. **File > Import > Existing Maven Projects** and select this folder.
2. Select JDK 17.
3. Add Apache Tomcat 10.1 in the Servers view.
4. Add this project to the server and run it.
5. Open `http://localhost:8080/EmployeeManagementSystem/`.

## Demonstration accounts

All local demo accounts use password **Demo@123**.

| Username | Role |
|---|---|
| `employee` | Employee |
| `hrmanager` | HR Manager / Admin |
| `attendance` | Attendance Coordinator |
| `supervisor` | Department Supervisor |
| `itadmin` | IT System Administrator |
| `payroll` | Payroll Specialist |
| `operations` | Operations Manager |

Passwords are stored as adaptive PBKDF2-HMAC-SHA256 hashes, never plain text. Change demonstration passwords before any non-classroom use.

## Important behavior

- Profile, access, leave, performance, attendance, notification and payroll screens use SQL Server data.
- Leave approval and balance deduction are transactional.
- Profile and account security changes write audit records.
- Attendance uses server timestamps; keep the JVM and SQL Server in the same business timezone (Asia/Colombo).
- Operations Manager owns performance writes. Supervisor retains department-scoped read access.
- Automatic absence checks run every five minutes and when opening attendance reports.
- Read `docs/UPGRADE_REPORT.md` for shift, leave, payroll and credential assumptions.
- Payroll uses `DECIMAL`, configurable effective-dated statutory rates, salary structures, allowances, and deductions.
- CSV reports are available to authorized roles.
- Direct URL access is blocked by role checks.

## Troubleshooting

- **Login/database error:** ensure SQL Server is running, the script completed, TCP/IP is enabled, and credentials match `database.properties`.
- **404:** verify the context is `/EmployeeManagementSystem`.
- **Class not found / javax error:** use Tomcat 10.1 and JDK 17, then reload Maven.
- **Port 8080 busy:** change Tomcat to 8081 and open the matching URL.
- **No Maven command:** install Maven or use IntelliJ's bundled Maven runner.

See `TESTING.md`, `docs/ROLE_ACCESS_MATRIX.md`, and `TEAM_CONTRIBUTIONS.md` before the viva.
