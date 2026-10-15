/* Non-destructive PeopleSphere update. Apply after 001_scope_upgrade.sql.
   Existing databases: NEVER rerun EmployeeManagementDB.sql (the original reset/seed script).
   This migration can be rerun. No existing business records are removed. */
USE EmployeeManagementDB;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('employee_photos','U') IS NULL
 CREATE TABLE employee_photos(
  employee_id INT NOT NULL PRIMARY KEY REFERENCES employees(employee_id),
  photo_data VARBINARY(MAX) NOT NULL,
  updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
  CONSTRAINT CK_employee_photo_size CHECK(DATALENGTH(photo_data) BETWEEN 1 AND 2097152)
 );
IF OBJECT_ID('employee_tasks','U') IS NULL
BEGIN
 CREATE TABLE employee_tasks(
  task_id INT IDENTITY PRIMARY KEY, employee_id INT NOT NULL REFERENCES employees(employee_id),
  title NVARCHAR(180) NOT NULL, details NVARCHAR(2000) NOT NULL, instructions NVARCHAR(2000) NOT NULL,
  deadline DATE NOT NULL, assigned_by INT NOT NULL REFERENCES user_accounts(user_id),
  status VARCHAR(15) NOT NULL DEFAULT 'ASSIGNED' CHECK(status IN('ASSIGNED','COMPLETED','REVIEWED','DELETED')),
  completion_comments NVARCHAR(2000), completed_at DATETIME2,
  feedback NVARCHAR(2000), rating DECIMAL(3,2) CHECK(rating BETWEEN 1 AND 5),
  reviewed_by INT REFERENCES user_accounts(user_id), reviewed_at DATETIME2,
  deleted_by INT REFERENCES user_accounts(user_id), deleted_at DATETIME2, deletion_reason NVARCHAR(500),
  created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
  CHECK(status NOT IN('COMPLETED','REVIEWED') OR (completion_comments IS NOT NULL AND completed_at IS NOT NULL)),
  CHECK(status<>'REVIEWED' OR (feedback IS NOT NULL AND rating IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CHECK(status<>'DELETED' OR (deleted_by IS NOT NULL AND deleted_at IS NOT NULL AND deletion_reason IS NOT NULL))
 );
 CREATE INDEX IX_tasks_employee_status ON employee_tasks(employee_id,status,deadline);
END;
-- Preserve all values while enabling Unicode names/addresses (JDBC sends Unicode parameters).
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('employees') AND name='full_name' AND system_type_id=167)
 ALTER TABLE employees ALTER COLUMN full_name NVARCHAR(120) NOT NULL;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('employees') AND name='address' AND system_type_id=167)
 ALTER TABLE employees ALTER COLUMN address NVARCHAR(250) NOT NULL;
COMMIT;
GO
