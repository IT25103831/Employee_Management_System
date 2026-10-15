/* Non-destructive upgrade. Back up the existing database first. Apply after the original setup.
   Safe to rerun. No data/tables/columns are deleted. */
USE EmployeeManagementDB;
GO
IF COL_LENGTH('user_accounts','security_version') IS NULL
 ALTER TABLE user_accounts ADD security_version INT NOT NULL CONSTRAINT DF_user_security_version DEFAULT 0;
IF COL_LENGTH('user_accounts','temporary_password_expires_at') IS NULL
 ALTER TABLE user_accounts ADD temporary_password_expires_at DATETIME2 NULL;
GO
-- Add VOID to the existing attendance lifecycle while preserving erroneous records and their adjustment history.
DECLARE @CheckName sysname;
SELECT @CheckName=cc.name FROM sys.check_constraints cc
 WHERE cc.parent_object_id=OBJECT_ID('attendance_records') AND cc.definition LIKE '%status%';
IF @CheckName IS NOT NULL
BEGIN
 DECLARE @DropSql NVARCHAR(400)=N'ALTER TABLE attendance_records DROP CONSTRAINT '+QUOTENAME(@CheckName);
 EXEC(@DropSql);
END;
ALTER TABLE attendance_records WITH CHECK ADD CONSTRAINT CK_attendance_status_v2 CHECK(status IN('OPEN','FINALIZED','ABSENT','CORRECTED','VOID'));
GO
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_attendance_order_v2')
BEGIN
 IF EXISTS(SELECT 1 FROM attendance_records WHERE clock_out<clock_in)
  THROW 50010,'Correct existing reversed attendance timestamps before upgrading.',1;
 ALTER TABLE attendance_records WITH CHECK ADD CONSTRAINT CK_attendance_order_v2 CHECK(clock_out IS NULL OR clock_out>=clock_in);
END;
GO
-- The product backlog assigns performance writes to Operations Manager.
DELETE rp FROM role_permissions rp JOIN roles r ON r.role_id=rp.role_id JOIN permissions p ON p.permission_id=rp.permission_id
 WHERE r.role_code='DEPARTMENT_SUPERVISOR' AND p.permission_code='PERFORMANCE_REVIEW';
INSERT role_permissions(role_id,permission_id)
 SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
 WHERE r.role_code='OPERATIONS_MANAGER' AND p.permission_code='PERFORMANCE_REVIEW'
 AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.role_id AND rp.permission_id=p.permission_id);
GO

CREATE OR ALTER PROCEDURE sp_ProcessPayroll @PeriodLabel VARCHAR(80),@PeriodStart DATE,@PeriodEnd DATE,@CreatedBy INT,@Finalize BIT=1
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF DAY(@PeriodStart)<>1 OR @PeriodEnd<>EOMONTH(@PeriodStart) THROW 50009,'Select one complete calendar month',1;
 IF NULLIF(LTRIM(RTRIM(@PeriodLabel)),'') IS NULL THROW 50009,'Period label is required',1;
 IF @PeriodEnd<@PeriodStart THROW 50001,'Payroll end date must be after start date',1;
 IF EXISTS(SELECT 1 FROM payroll_runs WHERE period_label=@PeriodLabel) THROW 50002,'This payroll period already exists',1;
 BEGIN TRANSACTION;
 BEGIN TRY
  DECLARE @LockResult INT;
  EXEC @LockResult=sp_getapplock @Resource='EMS_PAYROLL_GENERATION',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
  IF @LockResult<0 THROW 50009,'Payroll is busy; retry shortly',1;
  IF EXISTS(SELECT 1 FROM payroll_runs WHERE status<>'CANCELLED' AND @PeriodStart<=period_end AND @PeriodEnd>=period_start) THROW 50009,'Payroll already exists for an overlapping period',1;
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
  FROM employees e CROSS APPLY(SELECT TOP 1 base_salary FROM salary_structures WHERE employee_id=e.employee_id AND status='ACTIVE' AND effective_from<=@PeriodStart AND (effective_to IS NULL OR effective_to>=@PeriodEnd) ORDER BY effective_from DESC,salary_structure_id DESC) s
  OUTER APPLY(SELECT COALESCE(SUM(amount),0) allowances FROM employee_allowances WHERE employee_id=e.employee_id AND status='ACTIVE')a
  OUTER APPLY(SELECT COALESCE(SUM(amount),0) deductions FROM employee_deductions WHERE employee_id=e.employee_id AND status='ACTIVE')d
  WHERE e.status='ACTIVE' AND e.employment_date<=@PeriodStart;
  IF NOT EXISTS(SELECT 1 FROM payroll_records WHERE payroll_run_id=@RunId) THROW 50004,'No eligible employee salary structures were found',1;
  IF EXISTS(SELECT 1 FROM payroll_records WHERE payroll_run_id=@RunId AND net_pay<0) THROW 50009,'Deductions exceed gross salary',1;
  INSERT payroll_components(payroll_record_id,component_type,component_name,amount) SELECT payroll_record_id,'EARNING','Base Salary',base_salary FROM payroll_records WHERE payroll_run_id=@RunId;
  INSERT payroll_components(payroll_record_id,component_type,component_name,amount) SELECT payroll_record_id,'DEDUCTION','Employee EPF',employee_epf FROM payroll_records WHERE payroll_run_id=@RunId;
  IF @Finalize=1
  BEGIN
  IF EXISTS(SELECT 1 FROM attendance_records WHERE work_date BETWEEN @PeriodStart AND @PeriodEnd AND status='OPEN') THROW 50009,'Resolve open attendance before finalizing payroll',1;
  UPDATE payroll_runs SET status='CLOSED',closed_at=SYSDATETIME() WHERE payroll_run_id=@RunId;
  INSERT notifications(user_id,title,message,notification_type) SELECT u.user_id,'Payslip available',@PeriodLabel+' payroll has been finalized.','PAYROLL' FROM user_accounts u JOIN payroll_records p ON p.employee_id=u.employee_id WHERE p.payroll_run_id=@RunId;
  INSERT audit_logs(actor_user_id,action_name,entity_type,entity_id,details) VALUES(@CreatedBy,'FINALIZE','PAYROLL_RUN',@RunId,@PeriodLabel);
  END
  ELSE INSERT audit_logs(actor_user_id,action_name,entity_type,entity_id,details) VALUES(@CreatedBy,'CREATE_DRAFT','PAYROLL_RUN',@RunId,@PeriodLabel);
  COMMIT;
 END TRY
 BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH
END;
GO