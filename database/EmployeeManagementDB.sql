/*
  Employee Management System - Group 2026-Y2-S1-KU-51
  DEVELOPMENT SETUP SCRIPT
  WARNING: This script resets the EMS project tables and their demonstration data.
  Do not rerun it after entering information you need to keep.
*/
IF DB_ID('EmployeeManagementDB') IS NULL CREATE DATABASE EmployeeManagementDB;
GO
USE EmployeeManagementDB;
GO

IF OBJECT_ID('sp_ProcessPayroll','P') IS NOT NULL DROP PROCEDURE sp_ProcessPayroll;
DROP TABLE IF EXISTS audit_logs,notifications,payroll_components,payroll_records,payroll_runs,statutory_rates,employee_deductions,deduction_types,employee_allowances,allowance_types,salary_structures,goal_progress_history,employee_goals,performance_ratings,performance_reviews,rating_categories,review_periods,leave_approval_history,leave_requests,leave_balances,leave_types,attendance_adjustments,attendance_records,shift_assignments,shifts,password_reset_tokens,email_verification_tokens,employee_profile_history,role_permissions,permissions,user_accounts,roles,employees,designations,departments;
GO

CREATE TABLE departments(
 department_id INT IDENTITY PRIMARY KEY, department_name VARCHAR(100) NOT NULL UNIQUE,
 department_code VARCHAR(20) NOT NULL UNIQUE, status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE'))
);
CREATE TABLE designations(
 designation_id INT IDENTITY PRIMARY KEY, designation_name VARCHAR(100) NOT NULL UNIQUE,
 status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE'))
);
CREATE TABLE employees(
 employee_id INT IDENTITY PRIMARY KEY, employee_code VARCHAR(20) NOT NULL UNIQUE, full_name VARCHAR(120) NOT NULL,
 nic VARCHAR(20) NOT NULL UNIQUE, date_of_birth DATE NOT NULL, email VARCHAR(150) NOT NULL UNIQUE, phone VARCHAR(20) NOT NULL UNIQUE,
 address VARCHAR(250) NOT NULL, department_id INT NOT NULL REFERENCES departments(department_id),
 designation_id INT NOT NULL REFERENCES designations(designation_id), employment_date DATE NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),
 created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
CREATE TABLE roles(
 role_id INT IDENTITY PRIMARY KEY, role_code VARCHAR(40) NOT NULL UNIQUE, role_name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE permissions(permission_id INT IDENTITY PRIMARY KEY,permission_code VARCHAR(60) NOT NULL UNIQUE,description VARCHAR(200));
CREATE TABLE role_permissions(role_id INT NOT NULL REFERENCES roles(role_id),permission_id INT NOT NULL REFERENCES permissions(permission_id),PRIMARY KEY(role_id,permission_id));
CREATE TABLE user_accounts(
 user_id INT IDENTITY PRIMARY KEY,employee_id INT NULL UNIQUE REFERENCES employees(employee_id),role_id INT NOT NULL REFERENCES roles(role_id),
 username VARCHAR(60) NOT NULL UNIQUE,email VARCHAR(150) NOT NULL UNIQUE,password_hash VARCHAR(500) NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE','LOCKED')),failed_attempts INT NOT NULL DEFAULT 0 CHECK(failed_attempts>=0),
 must_change_password BIT NOT NULL DEFAULT 1,last_login_at DATETIME2 NULL,created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
CREATE TABLE employee_profile_history(
 history_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),changed_by INT NOT NULL REFERENCES user_accounts(user_id),
 action_name VARCHAR(30) NOT NULL,details VARCHAR(500),changed_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
CREATE TABLE password_reset_tokens(token_id BIGINT IDENTITY PRIMARY KEY,user_id INT NOT NULL REFERENCES user_accounts(user_id),token_hash VARCHAR(128) NOT NULL UNIQUE,expires_at DATETIME2 NOT NULL,used_at DATETIME2 NULL);
CREATE TABLE email_verification_tokens(token_id BIGINT IDENTITY PRIMARY KEY,user_id INT NOT NULL REFERENCES user_accounts(user_id),token_hash VARCHAR(128) NOT NULL UNIQUE,expires_at DATETIME2 NOT NULL,verified_at DATETIME2 NULL);

CREATE TABLE shifts(
 shift_id INT IDENTITY PRIMARY KEY,shift_name VARCHAR(80) NOT NULL UNIQUE,start_time TIME NOT NULL,end_time TIME NOT NULL,
 break_minutes INT NOT NULL DEFAULT 60 CHECK(break_minutes BETWEEN 0 AND 240),status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE'))
);
CREATE TABLE shift_assignments(
 assignment_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),shift_id INT NOT NULL REFERENCES shifts(shift_id),
 start_date DATE NOT NULL,end_date DATE NULL,assigned_by INT NOT NULL REFERENCES user_accounts(user_id),created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
 CHECK(end_date IS NULL OR end_date>=start_date)
);
CREATE TABLE attendance_records(
 attendance_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),work_date DATE NOT NULL,shift_id INT NULL REFERENCES shifts(shift_id),
 clock_in DATETIME2 NULL,clock_out DATETIME2 NULL,hours_worked DECIMAL(6,2) NULL,exception_type VARCHAR(30) NULL,
 status VARCHAR(15) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','FINALIZED','ABSENT','CORRECTED')),
 created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),CONSTRAINT UQ_attendance_employee_date UNIQUE(employee_id,work_date),
 CHECK(clock_out IS NULL OR clock_in IS NOT NULL),CHECK(hours_worked IS NULL OR hours_worked>=0)
);
CREATE TABLE attendance_adjustments(
 adjustment_id BIGINT IDENTITY PRIMARY KEY,attendance_id BIGINT NOT NULL REFERENCES attendance_records(attendance_id),changed_by INT NOT NULL REFERENCES user_accounts(user_id),
 reason VARCHAR(300) NOT NULL,old_clock_in DATETIME2 NULL,old_clock_out DATETIME2 NULL,new_clock_in DATETIME2 NULL,new_clock_out DATETIME2 NULL,created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE leave_types(
 leave_type_id INT IDENTITY PRIMARY KEY,leave_type_name VARCHAR(80) NOT NULL UNIQUE,default_entitlement DECIMAL(5,1) NOT NULL CHECK(default_entitlement>=0),
 requires_hr_approval BIT NOT NULL DEFAULT 0,status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE'))
);
CREATE TABLE leave_balances(
 employee_id INT NOT NULL REFERENCES employees(employee_id),leave_type_id INT NOT NULL REFERENCES leave_types(leave_type_id),year INT NOT NULL,
 entitled_days DECIMAL(5,1) NOT NULL,used_days DECIMAL(5,1) NOT NULL DEFAULT 0,PRIMARY KEY(employee_id,leave_type_id,year),CHECK(entitled_days>=0 AND used_days>=0 AND used_days<=entitled_days)
);
CREATE TABLE leave_requests(
 leave_request_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),leave_type_id INT NOT NULL REFERENCES leave_types(leave_type_id),
 start_date DATE NOT NULL,end_date DATE NOT NULL,days_requested INT NOT NULL CHECK(days_requested>0),reason VARCHAR(500) NOT NULL,
 status VARCHAR(25) NOT NULL DEFAULT 'PENDING_SUPERVISOR' CHECK(status IN('PENDING_SUPERVISOR','PENDING_HR','APPROVED','REJECTED','CANCELLED')),
 submitted_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),CHECK(end_date>=start_date)
);
CREATE TABLE leave_approval_history(
 approval_id BIGINT IDENTITY PRIMARY KEY,leave_request_id BIGINT NOT NULL REFERENCES leave_requests(leave_request_id),reviewer_user_id INT NOT NULL REFERENCES user_accounts(user_id),
 decision VARCHAR(25) NOT NULL,reason VARCHAR(500),decided_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE review_periods(
 review_period_id INT IDENTITY PRIMARY KEY,period_name VARCHAR(80) NOT NULL UNIQUE,start_date DATE NOT NULL,end_date DATE NOT NULL,
 status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','CLOSED')),CHECK(end_date>=start_date)
);
CREATE TABLE rating_categories(category_id INT IDENTITY PRIMARY KEY,category_name VARCHAR(80) NOT NULL UNIQUE,weight_percent DECIMAL(5,2) NOT NULL CHECK(weight_percent BETWEEN 0 AND 100));
CREATE TABLE performance_reviews(
 review_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),review_period_id INT NOT NULL REFERENCES review_periods(review_period_id),
 reviewer_user_id INT NOT NULL REFERENCES user_accounts(user_id),review_date DATE NOT NULL,overall_rating DECIMAL(3,2) NOT NULL CHECK(overall_rating BETWEEN 1 AND 5),
 comments VARCHAR(1000) NOT NULL,status VARCHAR(12) NOT NULL DEFAULT 'FINALIZED' CHECK(status IN('DRAFT','FINALIZED')),
 created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),CONSTRAINT UQ_review_employee_period UNIQUE(employee_id,review_period_id)
);
CREATE TABLE performance_ratings(review_id BIGINT NOT NULL REFERENCES performance_reviews(review_id),category_id INT NOT NULL REFERENCES rating_categories(category_id),rating DECIMAL(3,2) NOT NULL CHECK(rating BETWEEN 1 AND 5),PRIMARY KEY(review_id,category_id));
CREATE TABLE employee_goals(
 goal_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),title VARCHAR(180) NOT NULL,target_value DECIMAL(12,2) NOT NULL CHECK(target_value>0),
 current_value DECIMAL(12,2) NOT NULL DEFAULT 0,due_date DATE NOT NULL,weight_percent DECIMAL(5,2) NOT NULL CHECK(weight_percent BETWEEN 0 AND 100),
 status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','COMPLETED','CANCELLED')),created_by INT NOT NULL REFERENCES user_accounts(user_id),
 created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
CREATE TABLE goal_progress_history(progress_id BIGINT IDENTITY PRIMARY KEY,goal_id BIGINT NOT NULL REFERENCES employee_goals(goal_id),recorded_by INT NOT NULL REFERENCES user_accounts(user_id),progress_value DECIMAL(12,2) NOT NULL,note VARCHAR(500) NOT NULL,recorded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME());

CREATE TABLE salary_structures(
 salary_structure_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),base_salary DECIMAL(14,2) NOT NULL CHECK(base_salary>=0),
 effective_from DATE NOT NULL,effective_to DATE NULL,status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','INACTIVE')),CHECK(effective_to IS NULL OR effective_to>=effective_from)
);
CREATE TABLE allowance_types(allowance_type_id INT IDENTITY PRIMARY KEY,allowance_name VARCHAR(80) NOT NULL UNIQUE);
CREATE TABLE employee_allowances(employee_allowance_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),allowance_type_id INT NOT NULL REFERENCES allowance_types(allowance_type_id),amount DECIMAL(14,2) NOT NULL CHECK(amount>=0),status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE deduction_types(deduction_type_id INT IDENTITY PRIMARY KEY,deduction_name VARCHAR(80) NOT NULL UNIQUE);
CREATE TABLE employee_deductions(employee_deduction_id BIGINT IDENTITY PRIMARY KEY,employee_id INT NOT NULL REFERENCES employees(employee_id),deduction_type_id INT NOT NULL REFERENCES deduction_types(deduction_type_id),amount DECIMAL(14,2) NOT NULL CHECK(amount>=0),status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE statutory_rates(rate_id INT IDENTITY PRIMARY KEY,rate_code VARCHAR(30) NOT NULL,effective_from DATE NOT NULL,rate_percent DECIMAL(6,3) NOT NULL CHECK(rate_percent BETWEEN 0 AND 100),description VARCHAR(200),UNIQUE(rate_code,effective_from));
CREATE TABLE payroll_runs(
 payroll_run_id BIGINT IDENTITY PRIMARY KEY,period_label VARCHAR(80) NOT NULL UNIQUE,period_start DATE NOT NULL,period_end DATE NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','CLOSED','CANCELLED')),created_by INT NOT NULL REFERENCES user_accounts(user_id),
 created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),closed_at DATETIME2 NULL,CHECK(period_end>=period_start)
);
CREATE TABLE payroll_records(
 payroll_record_id BIGINT IDENTITY PRIMARY KEY,payroll_run_id BIGINT NOT NULL REFERENCES payroll_runs(payroll_run_id),employee_id INT NOT NULL REFERENCES employees(employee_id),
 base_salary DECIMAL(14,2) NOT NULL,gross_pay DECIMAL(14,2) NOT NULL,employee_epf DECIMAL(14,2) NOT NULL,employer_epf DECIMAL(14,2) NOT NULL,etf DECIMAL(14,2) NOT NULL,
 total_deductions DECIMAL(14,2) NOT NULL,net_pay DECIMAL(14,2) NOT NULL,created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),UNIQUE(payroll_run_id,employee_id)
);
CREATE TABLE payroll_components(component_id BIGINT IDENTITY PRIMARY KEY,payroll_record_id BIGINT NOT NULL REFERENCES payroll_records(payroll_record_id),component_type VARCHAR(20) NOT NULL,component_name VARCHAR(100) NOT NULL,amount DECIMAL(14,2) NOT NULL);
CREATE TABLE notifications(notification_id BIGINT IDENTITY PRIMARY KEY,user_id INT NOT NULL REFERENCES user_accounts(user_id),title VARCHAR(150) NOT NULL,message VARCHAR(500) NOT NULL,notification_type VARCHAR(30) NOT NULL,is_read BIT NOT NULL DEFAULT 0,created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME());
CREATE TABLE audit_logs(audit_id BIGINT IDENTITY PRIMARY KEY,actor_user_id INT NOT NULL REFERENCES user_accounts(user_id),action_name VARCHAR(40) NOT NULL,entity_type VARCHAR(50) NOT NULL,entity_id BIGINT NULL,details VARCHAR(500),created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME());
GO

CREATE INDEX IX_employees_department_status ON employees(department_id,status);
CREATE INDEX IX_attendance_date ON attendance_records(work_date,employee_id);
CREATE INDEX IX_leave_status_department ON leave_requests(status,employee_id);
CREATE INDEX IX_notifications_user_unread ON notifications(user_id,is_read,created_at);
CREATE INDEX IX_audit_created ON audit_logs(created_at,actor_user_id);
GO

INSERT departments(department_name,department_code) VALUES('Human Resources','HR'),('Operations','OPS'),('Finance','FIN'),('Information Technology','IT');
INSERT designations(designation_name) VALUES('HR Manager'),('Software Engineer'),('Attendance Coordinator'),('Department Supervisor'),('IT System Administrator'),('Payroll Specialist'),('Operations Manager');
INSERT employees(employee_code,full_name,nic,date_of_birth,email,phone,address,department_id,designation_id,employment_date) VALUES
('EMP001','Amaya Fernando','200012345678','2000-04-12','employee@ems.lk','0771000001','Colombo',2,2,'2025-01-05'),
('EMP002','Nimal Perera','198812345678','1988-08-19','hr@ems.lk','0771000002','Kandy',1,1,'2020-02-01'),
('EMP003','Dilshan Silva','199312345678','1993-03-16','attendance@ems.lk','0771000003','Galle',1,3,'2021-06-12'),
('EMP004','Kasun Wickramasinghe','199012345678','1990-11-21','supervisor@ems.lk','0771000004','Kurunegala',2,4,'2019-04-10'),
('EMP005','Ruwan Jayawardena','198912345678','1989-07-02','itadmin@ems.lk','0771000005','Colombo',4,5,'2018-09-17'),
('EMP006','Nimali Perera','199112345678','1991-09-28','payroll@ems.lk','0771000006','Kandy',3,6,'2020-10-08'),
('EMP007','Sahan Ranasinghe','198512345678','1985-02-25','operations@ems.lk','0771000007','Colombo',2,7,'2017-01-15'),
('EMP008','Tharushi Wijesinghe','200112345678','2001-05-14','tharushi@ems.lk','0771000008','Matale',2,2,'2026-08-01');
INSERT roles(role_code,role_name) VALUES('EMPLOYEE','Employee'),('HR_MANAGER','HR Manager / Admin'),('ATTENDANCE_COORDINATOR','Attendance Coordinator'),('DEPARTMENT_SUPERVISOR','Department Supervisor'),('IT_SYSTEM_ADMINISTRATOR','IT System Administrator'),('PAYROLL_SPECIALIST','Payroll Specialist'),('OPERATIONS_MANAGER','Operations Manager');
INSERT permissions(permission_code,description) VALUES('PROFILE_MANAGE','Maintain employee profiles'),('ATTENDANCE_MANAGE','Manage attendance and shifts'),('LEAVE_APPROVE','Approve leave requests'),('PERFORMANCE_REVIEW','Record performance reviews'),('PAYROLL_PROCESS','Process payroll'),('ACCESS_MANAGE','Manage accounts'),('REPORT_VIEW','View authorized reports');
INSERT role_permissions SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p WHERE
 (r.role_code='HR_MANAGER' AND p.permission_code IN('PROFILE_MANAGE','LEAVE_APPROVE','REPORT_VIEW')) OR
 (r.role_code='ATTENDANCE_COORDINATOR' AND p.permission_code IN('ATTENDANCE_MANAGE','REPORT_VIEW')) OR
 (r.role_code='DEPARTMENT_SUPERVISOR' AND p.permission_code IN('LEAVE_APPROVE','PERFORMANCE_REVIEW','REPORT_VIEW')) OR
 (r.role_code='IT_SYSTEM_ADMINISTRATOR' AND p.permission_code IN('ACCESS_MANAGE','REPORT_VIEW')) OR
 (r.role_code='PAYROLL_SPECIALIST' AND p.permission_code IN('PAYROLL_PROCESS','REPORT_VIEW')) OR
 (r.role_code='OPERATIONS_MANAGER' AND p.permission_code='REPORT_VIEW');

-- PBKDF2-HMAC-SHA256 hash for local demonstration password Demo@123.
DECLARE @DemoHash VARCHAR(500)='120000:4f70656e41495f47726f75703439:a74e1a5a5838ba1f838bb595c5469f779ef706758001c0fb479288f113cb1ba6';
INSERT user_accounts(employee_id,role_id,username,email,password_hash,status,must_change_password) SELECT e.employee_id,r.role_id,x.username,e.email,@DemoHash,'ACTIVE',0 FROM (VALUES
('EMP001','EMPLOYEE','employee'),('EMP002','HR_MANAGER','hrmanager'),('EMP003','ATTENDANCE_COORDINATOR','attendance'),('EMP004','DEPARTMENT_SUPERVISOR','supervisor'),('EMP005','IT_SYSTEM_ADMINISTRATOR','itadmin'),('EMP006','PAYROLL_SPECIALIST','payroll'),('EMP007','OPERATIONS_MANAGER','operations'))x(employee_code,role_code,username) JOIN employees e ON e.employee_code=x.employee_code JOIN roles r ON r.role_code=x.role_code;

INSERT shifts(shift_name,start_time,end_time,break_minutes) VALUES('Standard Day','08:30','17:00',60),('Early Shift','06:00','14:00',45),('Late Shift','14:00','22:00',45);
INSERT shift_assignments(employee_id,shift_id,start_date,end_date,assigned_by) SELECT e.employee_id,1,'2026-01-01',NULL,u.user_id FROM employees e CROSS JOIN user_accounts u WHERE u.username='attendance';
INSERT attendance_records(employee_id,work_date,shift_id,clock_in,clock_out,hours_worked,status,exception_type) SELECT employee_id,DATEADD(day,-1,CAST(GETDATE() AS DATE)),1,DATEADD(minute,510,CAST(DATEADD(day,-1,CAST(GETDATE() AS DATE)) AS DATETIME2)),DATEADD(minute,1020,CAST(DATEADD(day,-1,CAST(GETDATE() AS DATE)) AS DATETIME2)),8.50,'FINALIZED',NULL FROM employees WHERE status='ACTIVE';

INSERT leave_types(leave_type_name,default_entitlement,requires_hr_approval) VALUES('Annual Leave',14,1),('Casual Leave',7,0),('Medical Leave',14,1),('Unpaid Leave',30,1);
INSERT leave_balances(employee_id,leave_type_id,year,entitled_days,used_days) SELECT e.employee_id,t.leave_type_id,YEAR(GETDATE()),t.default_entitlement,0 FROM employees e CROSS JOIN leave_types t;
INSERT leave_requests(employee_id,leave_type_id,start_date,end_date,days_requested,reason,status) SELECT e.employee_id,t.leave_type_id,DATEADD(day,10,CAST(GETDATE() AS DATE)),DATEADD(day,11,CAST(GETDATE() AS DATE)),2,'Family commitment','PENDING_SUPERVISOR' FROM employees e JOIN leave_types t ON t.leave_type_name='Annual Leave' WHERE e.employee_code='EMP001';

INSERT review_periods(period_name,start_date,end_date,status) VALUES('2026 Mid-Year Review','2026-01-01','2026-06-30','OPEN'),('2026 Annual Review','2026-01-01','2026-12-31','OPEN');
INSERT rating_categories(category_name,weight_percent) VALUES('Quality of Work',30),('Teamwork',25),('Reliability',25),('Growth',20);
INSERT employee_goals(employee_id,title,target_value,current_value,due_date,weight_percent,status,created_by) SELECT e.employee_id,'Complete service reliability improvements',100,60,'2026-12-15',40,'ACTIVE',u.user_id FROM employees e CROSS JOIN user_accounts u WHERE e.employee_code='EMP001' AND u.username='supervisor';

INSERT salary_structures(employee_id,base_salary,effective_from,status) SELECT employee_id,CASE employee_code WHEN 'EMP001' THEN 120000 WHEN 'EMP002' THEN 210000 WHEN 'EMP003' THEN 145000 WHEN 'EMP004' THEN 185000 WHEN 'EMP005' THEN 200000 WHEN 'EMP006' THEN 175000 ELSE 260000 END,'2026-01-01','ACTIVE' FROM employees;
INSERT allowance_types(allowance_name) VALUES('Transport'),('Communication'),('Performance');
INSERT employee_allowances(employee_id,allowance_type_id,amount,status) SELECT employee_id,1,10000,'ACTIVE' FROM employees;
INSERT deduction_types(deduction_name) VALUES('Loan Repayment'),('Other Approved Deduction');
INSERT employee_deductions(employee_id,deduction_type_id,amount,status) SELECT employee_id,1,5000,'ACTIVE' FROM employees WHERE employee_code IN('EMP001','EMP004');
-- Demonstration rates only. Authorized users must verify rates before real use.
INSERT statutory_rates(rate_code,effective_from,rate_percent,description) VALUES('EMPLOYEE_EPF','2026-01-01',8,'Demo employee EPF rate'),('EMPLOYER_EPF','2026-01-01',12,'Demo employer EPF rate'),('ETF','2026-01-01',3,'Demo ETF rate');
INSERT notifications(user_id,title,message,notification_type) SELECT user_id,'Welcome to PeopleSphere','Your secure EMS workspace is ready.','ACCOUNT' FROM user_accounts;
INSERT audit_logs(actor_user_id,action_name,entity_type,details) SELECT user_id,'SEED','SYSTEM','Demonstration account initialized' FROM user_accounts WHERE username='itadmin';
GO

CREATE PROCEDURE sp_ProcessPayroll @PeriodLabel VARCHAR(80),@PeriodStart DATE,@PeriodEnd DATE,@CreatedBy INT
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @PeriodEnd<@PeriodStart THROW 50001,'Payroll end date must be after start date',1;
 IF EXISTS(SELECT 1 FROM payroll_runs WHERE period_label=@PeriodLabel) THROW 50002,'This payroll period already exists',1;
 BEGIN TRANSACTION;
 BEGIN TRY
  DECLARE @RunId BIGINT,@EmployeeEPF DECIMAL(6,3),@EmployerEPF DECIMAL(6,3),@ETF DECIMAL(6,3);
  SELECT TOP 1 @EmployeeEPF=rate_percent FROM statutory_rates WHERE rate_code='EMPLOYEE_EPF' AND effective_from<=@PeriodEnd ORDER BY effective_from DESC;
  SELECT TOP 1 @EmployerEPF=rate_percent FROM statutory_rates WHERE rate_code='EMPLOYER_EPF' AND effective_from<=@PeriodEnd ORDER BY effective_from DESC;
  SELECT TOP 1 @ETF=rate_percent FROM statutory_rates WHERE rate_code='ETF' AND effective_from<=@PeriodEnd ORDER BY effective_from DESC;
  IF @EmployeeEPF IS NULL OR @EmployerEPF IS NULL OR @ETF IS NULL THROW 50003,'Required statutory rates are missing',1;
  INSERT payroll_runs(period_label,period_start,period_end,status,created_by) VALUES(@PeriodLabel,@PeriodStart,@PeriodEnd,'DRAFT',@CreatedBy); SET @RunId=SCOPE_IDENTITY();
  INSERT payroll_records(payroll_run_id,employee_id,base_salary,gross_pay,employee_epf,employer_epf,etf,total_deductions,net_pay)
  SELECT @RunId,e.employee_id,s.base_salary,s.base_salary+a.allowances,
   ROUND((s.base_salary+a.allowances)*@EmployeeEPF/100,2),ROUND((s.base_salary+a.allowances)*@EmployerEPF/100,2),ROUND((s.base_salary+a.allowances)*@ETF/100,2),d.deductions,
   ROUND((s.base_salary+a.allowances)-((s.base_salary+a.allowances)*@EmployeeEPF/100)-d.deductions,2)
  FROM employees e JOIN salary_structures s ON s.employee_id=e.employee_id AND s.status='ACTIVE' AND s.effective_from<=@PeriodEnd AND (s.effective_to IS NULL OR s.effective_to>=@PeriodStart)
  OUTER APPLY(SELECT COALESCE(SUM(amount),0) allowances FROM employee_allowances WHERE employee_id=e.employee_id AND status='ACTIVE')a
  OUTER APPLY(SELECT COALESCE(SUM(amount),0) deductions FROM employee_deductions WHERE employee_id=e.employee_id AND status='ACTIVE')d
  WHERE e.status='ACTIVE';
  IF NOT EXISTS(SELECT 1 FROM payroll_records WHERE payroll_run_id=@RunId) THROW 50004,'No eligible employee salary structures were found',1;
  INSERT payroll_components(payroll_record_id,component_type,component_name,amount) SELECT payroll_record_id,'EARNING','Base Salary',base_salary FROM payroll_records WHERE payroll_run_id=@RunId;
  INSERT payroll_components(payroll_record_id,component_type,component_name,amount) SELECT payroll_record_id,'DEDUCTION','Employee EPF',employee_epf FROM payroll_records WHERE payroll_run_id=@RunId;
  UPDATE payroll_runs SET status='CLOSED',closed_at=SYSDATETIME() WHERE payroll_run_id=@RunId;
  INSERT notifications(user_id,title,message,notification_type) SELECT u.user_id,'Payslip available',@PeriodLabel+' payroll has been finalized.','PAYROLL' FROM user_accounts u JOIN payroll_records p ON p.employee_id=u.employee_id WHERE p.payroll_run_id=@RunId;
  INSERT audit_logs(actor_user_id,action_name,entity_type,entity_id,details) VALUES(@CreatedBy,'FINALIZE','PAYROLL_RUN',@RunId,@PeriodLabel);
  COMMIT;
 END TRY
 BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH
END;
GO

PRINT 'EmployeeManagementDB created successfully.';
PRINT 'Demo usernames: employee, hrmanager, attendance, supervisor, itadmin, payroll, operations';
PRINT 'Demo password for every account: Demo@123';
