package lk.sliit.ems.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import lk.sliit.ems.dao.EMSRepository;
import lk.sliit.ems.model.UserSession;
import java.sql.*;import java.nio.file.*;import java.time.*;import java.math.BigDecimal;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: resets only the explicitly named disposable EMSRegression database. */
@EnabledIfEnvironmentVariable(named="EMS_INTEGRATION_TESTS",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqlServerWorkflowTest {
 static EMSRepository repo=new EMSRepository();static Map<String,UserSession> users=new HashMap<>();static int employeeId,userId,leaveId,reviewId,goalId,runId,recordId;
 static LocalDate day=LocalDate.now().plusDays(5);
 static Connection connection()throws SQLException{return DriverManager.getConnection(System.getProperty("db.url"),System.getProperty("db.username"),System.getProperty("db.password"));}
 static void sql(String query)throws SQLException{try(Connection c=connection();Statement s=c.createStatement()){s.execute(query);}}
 static int number(String query)throws SQLException{try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(query)){r.next();return r.getInt(1);}}
 static String string(String query)throws SQLException{try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(query)){r.next();return r.getString(1);}}
 static UserSession u(String name){return users.get(name);}
 @BeforeAll static void setup()throws Exception{
  String url=System.getenv("EMS_TEST_DB_URL");assertNotNull(url,"Set EMS_TEST_DB_URL to your dedicated EMSRegression database.");
  System.setProperty("db.url",url);System.setProperty("db.username",System.getenv("EMS_TEST_DB_USER"));System.setProperty("db.password",System.getenv("EMS_TEST_DB_PASSWORD"));
  try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT DB_NAME()")){r.next();assertEquals("EMSRegression",r.getString(1),"Refusing to reset any database other than EMSRegression.");}
  sql("DROP TABLE IF EXISTS employee_tasks,employee_photos"); // Only after the EMSRegression guard above.
  for(String file:List.of("database/EmployeeManagementDB.sql","database/001_scope_upgrade.sql","database/002_people_workflow.sql")){
   String script=Files.readString(Path.of(file)).replace("EmployeeManagementDB","EMSRegression");
   for(String batch:script.split("(?im)^GO\\s*$"))if(!batch.isBlank())sql(batch);
  }
 }
 @Test @Order(1) void loginForSevenRoles()throws Exception{
  for(String name:List.of("employee","hrmanager","attendance","supervisor","itadmin","payroll","operations")){UserSession user=repo.authenticate(name,"Demo@123");assertNotNull(user,name);users.put(name,user);assertTrue(repo.sessionValid(user));}
  assertNull(repo.authenticate("employee","wrong"));assertNull(repo.authenticate("' OR 1=1--","Demo@123"));
 }
 @Test @Order(2) void employeeCreateReadUpdateFilter()throws Exception{
  Map<String,String> v=new HashMap<>(Map.of("employeeCode","REG001","fullName","Regression Employee","nic","200011112222","dob","2000-01-01","email","regression@example.lk","phone","0779999999","address","Colombo","departmentId","2","designationId","2"));v.put("employmentDate",LocalDate.now().minusYears(1).toString());
  v.put("username","regression");v.put("password","Initial@123");
  repo.saveEmployee(u("hrmanager"),v);employeeId=number("SELECT employee_id FROM employees WHERE employee_code='REG001'");assertTrue(employeeId>0);
  repo.updateEmployee(u("hrmanager"),employeeId,"updated@example.lk","0779999999",2,2);
  assertEquals(1,repo.filteredEmployees("Regression","Operations","Software Engineer","ACTIVE").size());
  assertEquals(4,number("SELECT COUNT(*) FROM leave_balances WHERE employee_id="+employeeId));
  assertThrows(SQLException.class,()->repo.saveEmployee(u("hrmanager"),v));
 }
 @Test @Order(3) void userCreateUpdateResetUnlockExpiry()throws Exception{
  assertEquals("EMPLOYEE",string("SELECT r.role_code FROM user_accounts u JOIN roles r ON r.role_id=u.role_id WHERE u.employee_id="+employeeId));
  assertThrows(SQLException.class,()->repo.createUser(u("itadmin"),employeeId,"duplicate","account@example.lk","Initial@123",1));
  userId=number("SELECT user_id FROM user_accounts WHERE username='regression'");UserSession user=repo.authenticate("regression","Initial@123");assertTrue(user.mustChangePassword());
  repo.changePassword(user,"Initial@123","Changed@123");users.put("regression",repo.authenticate("regression","Changed@123"));assertFalse(repo.sessionValid(user));
  repo.updateUser(u("itadmin"),userId,1,"changed@example.lk");assertFalse(repo.sessionValid(u("regression")));users.put("regression",repo.authenticate("regression","Changed@123"));
  for(int i=0;i<5;i++)assertNull(repo.authenticate("regression","wrong"));assertNull(repo.authenticate("regression","Changed@123"));repo.accountAction(u("itadmin"),userId,"unlock","");assertNotNull(repo.authenticate("regression","Changed@123"));
  repo.accountAction(u("itadmin"),userId,"reset","Reset@123");assertNotNull(repo.authenticate("regression","Reset@123"));sql("UPDATE user_accounts SET temporary_password_expires_at=DATEADD(hour,-1,SYSDATETIME()) WHERE user_id="+userId);assertNull(repo.authenticate("regression","Reset@123"));
  repo.accountAction(u("itadmin"),userId,"reset","Reset@456");UserSession fresh=repo.authenticate("regression","Reset@456");repo.changePassword(fresh,"Reset@456","Changed@456");users.put("regression",repo.authenticate("regression","Changed@456"));
  assertThrows(IllegalArgumentException.class,()->repo.accountAction(u("itadmin"),u("itadmin").id(),"deactivate",""));assertEquals(1,repo.filteredUsers("regression","EMPLOYEE","ACTIVE").size());
 }
 @Test @Order(4) void shiftsAndCoordinatorAttendanceCrud()throws Exception{
  repo.assignShift(u("attendance"),employeeId,1,LocalDate.now().minusDays(4),LocalDate.now().plusDays(60));
  assertThrows(IllegalArgumentException.class,()->repo.assignShift(u("attendance"),employeeId,1,LocalDate.now(),null));
  LocalDate yesterday=LocalDate.now().minusDays(1);repo.recordAttendance(u("attendance"),null,employeeId,yesterday,yesterday.atTime(8,30),yesterday.atTime(17,0),"Manual record");int id=number("SELECT attendance_id FROM attendance_records WHERE employee_id="+employeeId+" AND work_date='"+yesterday+"'");
  assertEquals("7.50",string("SELECT hours_worked FROM attendance_records WHERE attendance_id="+id));
  repo.recordAttendance(u("attendance"),id,employeeId,yesterday,yesterday.atTime(9,0),yesterday.atTime(17,0),"Correct late clock-in");assertEquals("LATE",string("SELECT exception_type FROM attendance_records WHERE attendance_id="+id));
  repo.voidAttendance(u("attendance"),id,"Wrong imported record");assertEquals("VOID",string("SELECT status FROM attendance_records WHERE attendance_id="+id));assertEquals(2,number("SELECT COUNT(*) FROM attendance_adjustments WHERE attendance_id="+id));
 }
 @Test @Order(5) void absenceAndPeriodSummary()throws Exception{
  repo.detectAbsences();int before=number("SELECT COUNT(*) FROM attendance_records WHERE status='ABSENT'");assertTrue(before>0);repo.detectAbsences();assertEquals(before,number("SELECT COUNT(*) FROM attendance_records WHERE status='ABSENT'"));
  assertFalse(repo.attendanceSummary(u("attendance"),LocalDate.now().minusDays(6),LocalDate.now()).isEmpty());
  assertEquals(0,number("SELECT COUNT(*) FROM attendance_records WHERE work_date>CAST(GETDATE() AS DATE) AND status='ABSENT'"));
 }
 @Test @Order(6) void leaveApplyBalanceAndSupervisorApproval()throws Exception{
  if(day.getYear()!=LocalDate.now().getYear())sql("INSERT leave_balances SELECT employee_id,leave_type_id,"+day.getYear()+",default_entitlement,0 FROM employees CROSS JOIN leave_types WHERE employee_id="+employeeId);
  repo.applyLeave(u("regression"),1,day,day,"Family commitment");leaveId=number("SELECT MAX(leave_request_id) FROM leave_requests WHERE employee_id="+employeeId);
  assertThrows(IllegalArgumentException.class,()->repo.applyLeave(u("regression"),1,day,day,"Overlap"));
  repo.saveLeave(u("regression"),leaveId,1,day,day.plusDays(1),"Updated two days");
  assertTrue(repo.leaveRequests(u("supervisor")).stream().anyMatch(r->((Number)r.get("employee_id")).intValue()==employeeId));assertTrue(repo.leaveBalances(u("supervisor")).stream().anyMatch(r->((Number)r.get("employee_id")).intValue()==employeeId));
  repo.decideLeave(u("supervisor"),leaveId,"APPROVE","Coverage arranged");assertEquals("PENDING_HR",string("SELECT status FROM leave_requests WHERE leave_request_id="+leaveId));
 }
 @Test @Order(7) void hrApprovalDeductsExactlyOnce()throws Exception{
  repo.decideLeave(u("hrmanager"),leaveId,"APPROVE","Approved within entitlement");assertEquals("APPROVED",string("SELECT status FROM leave_requests WHERE leave_request_id="+leaveId));
  assertThrows(Exception.class,()->repo.decideLeave(u("hrmanager"),leaveId,"APPROVE","Repeat"));assertEquals(2,number("SELECT used_days FROM leave_balances WHERE employee_id="+employeeId+" AND leave_type_id=1 AND year="+day.getYear()));assertFalse(repo.notifications(u("regression")).isEmpty());
 }
 @Test @Order(8) void pendingCancellationAndDepartmentIsolation()throws Exception{
  repo.applyLeave(u("regression"),2,day.plusDays(3),day.plusDays(3),"Cancel this");int id=number("SELECT MAX(leave_request_id) FROM leave_requests WHERE employee_id="+employeeId);repo.cancelLeave(u("regression"),id);assertEquals("CANCELLED",string("SELECT status FROM leave_requests WHERE leave_request_id="+id));
  assertTrue(repo.leaveBalances(u("supervisor")).stream().noneMatch(r->((Number)r.get("employee_id")).intValue()==2));
  UserSession fakeSelf=new UserSession(u("regression").id(),employeeId,"regression","Regression","DEPARTMENT_SUPERVISOR",false);
  assertThrows(IllegalArgumentException.class,()->repo.decideLeave(fakeSelf,leaveId,"APPROVE","Self approval"));
 }
 @Test @Order(9) void reviewDraftLifecycleAndHistory()throws Exception{
  repo.saveReview(u("operations"),employeeId,2,new BigDecimal("4.25"),"Good progress");reviewId=number("SELECT MAX(review_id) FROM performance_reviews");
  repo.editReview(u("operations"),reviewId,new BigDecimal("4.50"),"Reviewed evidence","update");repo.editReview(u("operations"),reviewId,new BigDecimal("4.50"),"Final feedback","finalize");
  assertThrows(IllegalArgumentException.class,()->repo.editReview(u("operations"),reviewId,BigDecimal.ONE,"Change","delete"));assertEquals(1,repo.performanceHistory(u("operations"),employeeId).size());
  repo.saveReview(u("operations"),employeeId,1,new BigDecimal("3.00"),"Draft mistake");int draft=number("SELECT MAX(review_id) FROM performance_reviews");repo.editReview(u("operations"),draft,BigDecimal.ONE,"","delete");assertEquals(0,number("SELECT COUNT(*) FROM performance_reviews WHERE review_id="+draft));
 }
 @Test @Order(10) void goalProgressAndDepartmentSummary()throws Exception{
  repo.saveGoal(u("operations"),employeeId,"Complete onboarding",new BigDecimal("10"),LocalDate.now().plusDays(20),new BigDecimal("30"));goalId=number("SELECT MAX(goal_id) FROM employee_goals");
  assertThrows(IllegalArgumentException.class,()->repo.progressGoal(u("operations"),goalId,new BigDecimal("11"),"Too high"));repo.progressGoal(u("operations"),goalId,new BigDecimal("10"),"Complete");
  assertThrows(IllegalArgumentException.class,()->repo.progressGoal(u("operations"),goalId,new BigDecimal("10"),"Repeat"));assertFalse(repo.performanceSummary().isEmpty());
 }
 @Test @Order(11) void payrollDraftCalculationDuplicateAndUpdate()throws Exception{
  LocalDate start=LocalDate.now().withDayOfMonth(1),end=start.withDayOfMonth(start.lengthOfMonth());
  repo.saveSalaryStructure(u("payroll"),employeeId,new BigDecimal("90000"),start);
  repo.createPayrollDraft(u("payroll"),"Regression Month",start,end);runId=number("SELECT MAX(payroll_run_id) FROM payroll_runs");recordId=number("SELECT MIN(payroll_record_id) FROM payroll_records WHERE payroll_run_id="+runId);
  assertThrows(SQLException.class,()->repo.createPayrollDraft(u("payroll"),"Different label same dates",start,end));
  repo.editPayroll(u("payroll"),recordId,new BigDecimal("100000"),new BigDecimal("10000"),new BigDecimal("5000"));assertEquals("96200.00",string("SELECT net_pay FROM payroll_records WHERE payroll_record_id="+recordId));assertThrows(SQLException.class,()->repo.payslip(u("payroll"),recordId));
  assertFalse(repo.payrollReport(u("payroll"),start,end).isEmpty());
 }
 @Test @Order(12) void finalizePayslipAndImmutablePayroll()throws Exception{
  repo.payrollLifecycle(u("payroll"),runId,"finalize");assertEquals("CLOSED",string("SELECT status FROM payroll_runs WHERE payroll_run_id="+runId));assertNotNull(repo.payslip(u("payroll"),recordId));
  assertThrows(IllegalArgumentException.class,()->repo.editPayroll(u("payroll"),recordId,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
  assertThrows(SQLException.class,()->repo.payslip(u("regression"),recordId));
 }
 @Test @Order(13) void cancellationPreservesPayrollHistory()throws Exception{
  LocalDate start=LocalDate.now().plusMonths(1).withDayOfMonth(1),end=start.withDayOfMonth(start.lengthOfMonth());repo.createPayrollDraft(u("payroll"),"Cancelled regression draft",start,end);int id=number("SELECT MAX(payroll_run_id) FROM payroll_runs");repo.payrollLifecycle(u("payroll"),id,"cancel");assertEquals("CANCELLED",string("SELECT status FROM payroll_runs WHERE payroll_run_id="+id));assertTrue(number("SELECT COUNT(*) FROM payroll_records WHERE payroll_run_id="+id)>0);
 }
 @Test @Order(14) void employeeDeactivationRevokesSessionsAndOperationalWrites()throws Exception{
  UserSession old=u("regression");repo.deactivateEmployee(u("hrmanager"),employeeId);assertFalse(repo.sessionValid(old));assertNull(repo.authenticate("regression","Changed@456"));assertThrows(IllegalArgumentException.class,()->repo.saveGoal(u("operations"),employeeId,"Invalid",BigDecimal.ONE,day,new BigDecimal("1")));assertEquals(0,repo.filteredEmployees("Regression","","","ACTIVE").size());assertFalse(repo.employeeHistory().isEmpty());assertFalse(repo.auditLogs().isEmpty());
 }
 @Test @Order(15) void upgradeIsRepeatableAndPreservesRecords()throws Exception{
  int before=number("SELECT COUNT(*) FROM employees");for(String file:List.of("database/001_scope_upgrade.sql","database/002_people_workflow.sql"))for(String batch:Files.readString(Path.of(file)).replace("EmployeeManagementDB","EMSRegression").split("(?im)^GO\\s*$"))if(!batch.isBlank())sql(batch);assertEquals(before,number("SELECT COUNT(*) FROM employees"));
 }
 @Test @Order(5) void employeeClockingDuplicateAndClockoutRules()throws Exception{
  sql("INSERT shifts(shift_name,start_time,end_time,break_minutes) VALUES('Regression 24h','00:00','00:00',0)");int shift=number("SELECT shift_id FROM shifts WHERE shift_name='Regression 24h'");
  sql("UPDATE shift_assignments SET shift_id="+shift+" WHERE employee_id=1");
  repo.clock(u("employee"),"in");assertThrows(IllegalArgumentException.class,()->repo.clock(u("employee"),"in"));repo.clock(u("employee"),"out");assertThrows(IllegalArgumentException.class,()->repo.clock(u("employee"),"out"));
 }
 @Test @Order(8) void concurrentApprovalIsSingleDeduction()throws Exception{
  repo.applyLeave(u("regression"),2,day.plusDays(4),day.plusDays(4),"Concurrency check");int id=number("SELECT MAX(leave_request_id) FROM leave_requests WHERE employee_id="+employeeId);
  java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{java.util.concurrent.Callable<Boolean> approve=()->{try{repo.decideLeave(u("supervisor"),id,"APPROVE","Coverage confirmed");return true;}catch(Exception e){return false;}};
   var results=pool.invokeAll(List.of(approve,approve));assertEquals(1,(results.get(0).get()?1:0)+(results.get(1).get()?1:0));
   assertEquals(1,number("SELECT used_days FROM leave_balances WHERE employee_id="+employeeId+" AND leave_type_id=2 AND year="+day.plusDays(4).getYear()));
  }finally{pool.shutdownNow();}
 }

 @Test @Order(16) void reactivateRetainsIdentityHistoryAndAccount()throws Exception{
  int before=number("SELECT COUNT(*) FROM leave_requests WHERE employee_id="+employeeId);
  repo.updateEmployee(u("hrmanager"),employeeId,"updated@example.lk","0779999999",2,2,"ACTIVE");
  assertEquals(employeeId,repo.authenticate("regression","Changed@456").employeeId());
  assertEquals(userId,repo.authenticate("regression","Changed@456").id());
  assertEquals(before,number("SELECT COUNT(*) FROM leave_requests WHERE employee_id="+employeeId));
  repo.accountAction(u("itadmin"),userId,"deactivate","");repo.deactivateEmployee(u("hrmanager"),employeeId);
  repo.updateEmployee(u("hrmanager"),employeeId,"updated@example.lk","0779999999",2,2,"ACTIVE");
  assertNull(repo.authenticate("regression","Changed@456"),"IT suspension must survive employment reactivation");
  repo.accountAction(u("itadmin"),userId,"activate","");users.put("regression",repo.authenticate("regression","Changed@456"));
 }
 @Test @Order(8) void unassignedDailyAttendanceLeaveAndApprovedLeaveBlock()throws Exception{
  Map<String,String> v=new HashMap<>(Map.of("employeeCode","REG002","fullName","Unscheduled Employee","nic","200022223333","dob","2000-01-01","email","unassigned@example.lk","phone","0778888888","address","Colombo","departmentId","2","designationId","2"));
  v.put("employmentDate",LocalDate.now().toString());v.put("username","unassigned");v.put("password","Initial@123");
  repo.saveEmployee(u("hrmanager"),v);var employee=repo.authenticate("unassigned","Initial@123");int id=employee.employeeId();
  assertEquals(0,number("SELECT COUNT(*) FROM shift_assignments WHERE employee_id="+id));
  repo.clock(employee,"in");assertThrows(IllegalArgumentException.class,()->repo.clock(employee,"in"));repo.clock(employee,"out");assertThrows(IllegalArgumentException.class,()->repo.clock(employee,"out"));
  assertEquals(1,repo.attendance(employee).size());assertEquals(1,number("SELECT COUNT(*) FROM attendance_records WHERE employee_id="+id+" AND shift_id IS NULL"));
  LocalDate future=LocalDate.now().plusDays(2);
  if(future.getYear()!=LocalDate.now().getYear())sql("INSERT leave_balances SELECT employee_id,leave_type_id,"+future.getYear()+",default_entitlement,0 FROM employees CROSS JOIN leave_types WHERE employee_id="+id);
  repo.applyLeave(employee,1,future,future,"Without a shift");int request=number("SELECT MAX(leave_request_id) FROM leave_requests WHERE employee_id="+id);
  repo.decideLeave(u("supervisor"),request,"APPROVE","Coverage confirmed");repo.decideLeave(u("hrmanager"),request,"APPROVE","Approved");
  assertEquals("APPROVED",string("SELECT status FROM leave_requests WHERE leave_request_id="+request));
  // Another profile has no attendance today, so approved leave is the reason clock-in fails.
  v.put("employeeCode","REG003");v.put("nic","200033334444");v.put("email","onleave@example.lk");v.put("phone","0777777777");v.put("username","onleave");repo.saveEmployee(u("hrmanager"),v);
  var onLeave=repo.authenticate("onleave","Initial@123");repo.applyLeave(onLeave,1,LocalDate.now(),LocalDate.now(),"Approved leave today");
  int todayRequest=number("SELECT MAX(leave_request_id) FROM leave_requests WHERE employee_id="+onLeave.employeeId());repo.decideLeave(u("supervisor"),todayRequest,"APPROVE","Coverage");repo.decideLeave(u("hrmanager"),todayRequest,"APPROVE","Approved");
  assertThrows(IllegalArgumentException.class,()->repo.clock(onLeave,"in"));assertTrue(repo.attendance(onLeave).isEmpty());
 }
 @Test @Order(18) void taskWorkflowOwnershipAndSoftDelete()throws Exception{
  var employee=u("regression");repo.assignTask(u("operations"),employeeId,"Verify stock","Check stock records",LocalDate.now().plusDays(4),"Report discrepancies");
  int task=number("SELECT MAX(task_id) FROM employee_tasks");
  assertTrue(repo.tasks(employee).stream().anyMatch(r->((Number)r.get("task_id")).intValue()==task));
  assertTrue(repo.tasks(u("employee")).stream().noneMatch(r->((Number)r.get("task_id")).intValue()==task));
  assertThrows(SecurityException.class,()->repo.completeTask(u("employee"),task,"Forged completion"));
  assertThrows(SecurityException.class,()->repo.assignTask(employee,1,"Unauthorized","No",LocalDate.now(),""));
  repo.completeTask(employee,task,"Stock verified");assertThrows(SecurityException.class,()->repo.completeTask(employee,task,"Repeat"));
  repo.reviewTask(u("operations"),task,new BigDecimal("4.50"),"Accurate work");
  assertEquals("REVIEWED",string("SELECT status FROM employee_tasks WHERE task_id="+task));
  repo.deleteTask(u("operations"),task,"No longer needed");assertEquals(1,number("SELECT COUNT(*) FROM employee_tasks WHERE task_id="+task));
  assertTrue(repo.tasks(employee).stream().noneMatch(r->((Number)r.get("task_id")).intValue()==task));
 }
 @Test @Order(19) void profilePhotoIsolationAndUnicode()throws Exception{
  var employee=u("regression");assertEquals(employeeId,repo.ownProfile(employee).get("employee_id"));
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  byte[] png=lk.sliit.ems.util.ProfileImage.normalize(bytes.toByteArray());repo.saveOwnPhoto(employee,png);assertArrayEquals(png,repo.ownPhoto(employee));assertNull(repo.ownPhoto(u("employee")));
  sql("UPDATE employees SET full_name=N'அருண் — Chloé',address=N'කොළඹ' WHERE employee_id="+employeeId);
  assertEquals("அருண் — Chloé",repo.ownProfile(employee).get("full_name"));assertEquals("කොළඹ",repo.ownProfile(employee).get("address"));
 }
 @Test @Order(20) void salaryStaleConfirmationAndFinalizedHistory()throws Exception{
  LocalDate next=LocalDate.now().plusMonths(2).withDayOfMonth(1);
  BigDecimal old=(BigDecimal)repo.salaryPreview(u("payroll"),employeeId,false).get("old_salary");
  assertThrows(IllegalArgumentException.class,()->repo.saveSalaryStructure(u("payroll"),employeeId,new BigDecimal("95000"),next,old.add(BigDecimal.ONE)));
  repo.saveSalaryStructure(u("payroll"),employeeId,new BigDecimal("95000"),next,old);
  assertEquals("95000.00",string("SELECT TOP 1 base_salary FROM salary_structures WHERE employee_id="+employeeId+" ORDER BY effective_from DESC"));
  assertTrue(number("SELECT COUNT(*) FROM salary_structures WHERE employee_id="+employeeId)>=2);
 }
}
