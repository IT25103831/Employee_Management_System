package lk.sliit.ems.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sliit.ems.dao.EMSRepository;
import lk.sliit.ems.model.UserSession;
import lk.sliit.ems.util.WebUtil;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@WebServlet("/app/*")
public class AppServlet extends HttpServlet {
    private final EMSRepository repo=new EMSRepository();
    private static final Map<String,Set<String>> ACCESS=new HashMap<>();
    static{
        ACCESS.put("dashboard",Set.of("EMPLOYEE","HR_MANAGER","ATTENDANCE_COORDINATOR","DEPARTMENT_SUPERVISOR","IT_SYSTEM_ADMINISTRATOR","PAYROLL_SPECIALIST","OPERATIONS_MANAGER"));
        ACCESS.put("employees",Set.of("HR_MANAGER","IT_SYSTEM_ADMINISTRATOR","OPERATIONS_MANAGER"));
        ACCESS.put("attendance",Set.of("EMPLOYEE","ATTENDANCE_COORDINATOR","DEPARTMENT_SUPERVISOR","PAYROLL_SPECIALIST"));
        ACCESS.put("leave",Set.of("EMPLOYEE","DEPARTMENT_SUPERVISOR","HR_MANAGER"));
        ACCESS.put("performance",Set.of("EMPLOYEE","DEPARTMENT_SUPERVISOR","HR_MANAGER","OPERATIONS_MANAGER"));
        ACCESS.put("payroll",Set.of("EMPLOYEE","PAYROLL_SPECIALIST","OPERATIONS_MANAGER"));
        ACCESS.put("access",Set.of("IT_SYSTEM_ADMINISTRATOR"));
        ACCESS.put("reports",Set.of("HR_MANAGER","ATTENDANCE_COORDINATOR","DEPARTMENT_SUPERVISOR","IT_SYSTEM_ADMINISTRATOR","PAYROLL_SPECIALIST","OPERATIONS_MANAGER"));
        ACCESS.put("notifications",ACCESS.get("dashboard")); ACCESS.put("account",ACCESS.get("dashboard"));
    }
    protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        String module=module(request);UserSession user=user(request);
        if(!allowed(module,user)){response.sendError(403);return;}
        try{
            if(user.hasRole("EMPLOYEE") && Set.of("performance","account").contains(module))EMSRepository.requireOwnEmployee(user,WebUtil.value(request,"employeeId").isBlank()?0:WebUtil.integer(request,"employeeId"));
            request.setAttribute("module",module);
            request.setAttribute("title",title(module));
            switch(module){
                case"dashboard"->request.setAttribute("stats",repo.dashboard(user));
                case"employees"->{request.setAttribute("records",repo.filteredEmployees(WebUtil.value(request,"q"),WebUtil.value(request,"department"),WebUtil.value(request,"designation"),WebUtil.value(request,"status")));request.setAttribute("history",repo.employeeHistory());lookups(request,true,false,false);}
                case"attendance"->{if(user.hasRole("ATTENDANCE_COORDINATOR"))request.setAttribute("assignments",repo.shiftAssignments());repo.detectAbsences();request.setAttribute("records",repo.attendance(user));if(user.hasRole("ATTENDANCE_COORDINATOR"))lookups(request,true,true,false);LocalDate day=date(request,"date",LocalDate.now());boolean weekly="week".equals(WebUtil.value(request,"period"));LocalDate from=weekly?day.with(java.time.DayOfWeek.MONDAY):day.withDayOfMonth(1);LocalDate to=weekly?from.plusDays(6):from.withDayOfMonth(from.lengthOfMonth());request.setAttribute("summary",repo.attendanceSummary(user,from,to));request.setAttribute("from",from);request.setAttribute("to",to);}
                case"leave"->{request.setAttribute("records",repo.leaveRequests(user));request.setAttribute("balances",repo.leaveBalances(user));request.setAttribute("leaveTypes",repo.leaveTypes());}
                case"performance"->{if(user.hasRole("OPERATIONS_MANAGER"))request.setAttribute("taskEmployees",repo.taskEmployeeOptions(user));if(user.hasRole("EMPLOYEE","OPERATIONS_MANAGER"))request.setAttribute("tasks",repo.tasks(user));request.setAttribute("records",repo.reviews(user));request.setAttribute("performanceHistory",repo.performanceHistory(user,WebUtil.value(request,"employeeId").isBlank()?0:WebUtil.integer(request,"employeeId")));if(user.hasRole("OPERATIONS_MANAGER"))request.setAttribute("summary",repo.performanceSummary());request.setAttribute("goals",repo.goals(user));request.setAttribute("periods",repo.reviewPeriods());request.setAttribute("employees",repo.employeeOptions(user));}
                case"payroll"->{LocalDate from=date(request,"from",LocalDate.now().withDayOfYear(1)),to=date(request,"to",LocalDate.now().withMonth(12).withDayOfMonth(31));request.setAttribute("from",from);request.setAttribute("to",to);request.setAttribute("records",repo.payrollReport(user,from,to));if(user.hasRole("PAYROLL_SPECIALIST"))request.setAttribute("runs",repo.payrollRuns());if(user.hasRole("PAYROLL_SPECIALIST"))request.setAttribute("employees",repo.employeeOptions(user));}
                case"access"->{request.setAttribute("records",repo.filteredUsers(WebUtil.value(request,"q"),WebUtil.value(request,"role"),WebUtil.value(request,"status")));request.setAttribute("roles",repo.roles());request.setAttribute("employees",repo.employeeOptions(user));}
                case"reports"->{request.setAttribute("records",user.hasRole("IT_SYSTEM_ADMINISTRATOR")?repo.auditLogs():java.util.List.of());String[] names=switch(user.role()){case"HR_MANAGER"->new String[]{"employees","leave","performance"};case"ATTENDANCE_COORDINATOR"->new String[]{"attendance","attendance-summary"};case"DEPARTMENT_SUPERVISOR"->new String[]{"attendance","attendance-summary","leave","performance"};case"IT_SYSTEM_ADMINISTRATOR"->new String[]{"audit"};case"PAYROLL_SPECIALIST"->new String[]{"payroll"};case"OPERATIONS_MANAGER"->new String[]{"employees","performance","payroll"};default->new String[]{};};request.setAttribute("reportNames",names);}
                case"notifications"->request.setAttribute("records",repo.notifications(user));
                case"account"->{if(user.employeeId()!=null){EMSRepository.requireOwnEmployee(user,WebUtil.value(request,"employeeId").isBlank()?0:WebUtil.integer(request,"employeeId"));request.setAttribute("profile",repo.ownProfile(user));}}
                default->{response.sendError(404);return;}
            }
            String flash=(String)request.getSession().getAttribute("flashMessage");if(flash!=null){request.setAttribute("flashMessage",flash);request.setAttribute("flashType",request.getSession().getAttribute("flashType"));request.getSession().removeAttribute("flashMessage");request.getSession().removeAttribute("flashType");}
            request.getRequestDispatcher("dashboard".equals(module)?"/WEB-INF/views/dashboard.jsp":"/WEB-INF/views/module.jsp").forward(request,response);
        }catch(SecurityException e){response.sendError(403);return;}catch(Exception e){getServletContext().log("EMS page failed",e);response.setStatus(500);request.setAttribute("error",friendly(e));request.getRequestDispatcher("/WEB-INF/views/errors/500.jsp").forward(request,response);}
    }
    protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        String module=module(request);UserSession user=user(request);if(!allowed(module,user)){response.sendError(403);return;}if(!WebUtil.validCsrf(request)){response.sendError(403,"Invalid security token");return;}
        try{
            String action=WebUtil.value(request,"action");
            if(module.equals("payroll") && Set.of("salary","update").contains(action)){
                require(user,"PAYROLL_SPECIALIST");
                if(!"yes".equals(WebUtil.value(request,"confirmed"))){
                    var change=repo.salaryPreview(user,WebUtil.integer(request,action.equals("salary")?"employeeId":"id"),action.equals("update"));
                    var base=WebUtil.decimal(request,"base");
                    lk.sliit.ems.service.Validation.decimal(base,"Base salary",java.math.BigDecimal.ZERO,new java.math.BigDecimal("99999999999.99"));
                    request.setAttribute("change",change);request.setAttribute("newSalary",base);
                    request.setAttribute("difference",base.subtract((java.math.BigDecimal)change.get("old_salary")));
                    request.setAttribute("module","payroll");request.setAttribute("title","Confirm Salary Change");
                    request.getRequestDispatcher("/WEB-INF/views/salary-confirm.jsp").forward(request,response);return;
                }
            }
            switch(module+":"+action){
                case"employees:create"->{require(user,"HR_MANAGER");Map<String,String>v=new HashMap<>();for(String k:new String[]{"employeeCode","fullName","nic","dob","email","phone","address","departmentId","designationId","employmentDate","username"})v.put(k,WebUtil.value(request,k));v.put("password",WebUtil.secret(request,"password"));if(!WebUtil.value(request,"roleId").isBlank() || !WebUtil.value(request,"role").isBlank())throw new SecurityException("Role assignment is managed by IT.");repo.saveEmployee(user,v);}
                case"employees:update"->{require(user,"HR_MANAGER");repo.updateEmployee(user,WebUtil.integer(request,"id"),WebUtil.value(request,"email"),WebUtil.value(request,"phone"),WebUtil.integer(request,"departmentId"),WebUtil.integer(request,"designationId"),WebUtil.value(request,"status"));}
                case"employees:deactivate"->{require(user,"HR_MANAGER");repo.deactivateEmployee(user,WebUtil.integer(request,"id"));}
                case"attendance:clockin"->{require(user,"EMPLOYEE");repo.clock(user,"in");}
                case"attendance:clockout"->{require(user,"EMPLOYEE");repo.clock(user,"out");}
                case"attendance:assign"->{require(user,"ATTENDANCE_COORDINATOR");String e=WebUtil.value(request,"endDate");repo.assignShift(user,WebUtil.integer(request,"employeeId"),WebUtil.integer(request,"shiftId"),LocalDate.parse(WebUtil.value(request,"startDate")),e.isBlank()?null:LocalDate.parse(e));}
                case"leave:apply"->{require(user,"EMPLOYEE");repo.applyLeave(user,WebUtil.integer(request,"leaveTypeId"),LocalDate.parse(WebUtil.value(request,"startDate")),LocalDate.parse(WebUtil.value(request,"endDate")),WebUtil.value(request,"reason"));}
                case"leave:decide"->{require(user,"DEPARTMENT_SUPERVISOR","HR_MANAGER");repo.decideLeave(user,WebUtil.integer(request,"id"),WebUtil.value(request,"decision"),WebUtil.value(request,"reason"));}
                case"performance:taskAssign"->{require(user,"OPERATIONS_MANAGER");repo.assignTask(user,WebUtil.integer(request,"employeeId"),WebUtil.value(request,"taskTitle"),WebUtil.value(request,"details"),LocalDate.parse(WebUtil.value(request,"deadline")),WebUtil.value(request,"notes"));}
                case"performance:taskComplete"->{require(user,"EMPLOYEE");repo.completeTask(user,WebUtil.integer(request,"id"),WebUtil.value(request,"comments"));}
                case"performance:taskReview"->{require(user,"OPERATIONS_MANAGER");repo.reviewTask(user,WebUtil.integer(request,"id"),WebUtil.decimal(request,"rating"),WebUtil.value(request,"feedback"));}
                case"performance:taskDelete"->{require(user,"OPERATIONS_MANAGER");repo.deleteTask(user,WebUtil.integer(request,"id"),WebUtil.value(request,"reason"));}
                case"performance:review"->{require(user,"OPERATIONS_MANAGER");repo.saveReview(user,WebUtil.integer(request,"employeeId"),WebUtil.integer(request,"periodId"),WebUtil.decimal(request,"rating"),WebUtil.value(request,"comments"));}
                case"performance:goal"->{require(user,"OPERATIONS_MANAGER");repo.saveGoal(user,WebUtil.integer(request,"employeeId"),WebUtil.value(request,"goalTitle"),WebUtil.decimal(request,"target"),LocalDate.parse(WebUtil.value(request,"dueDate")),WebUtil.decimal(request,"weight"));}
                case"performance:progress"->{require(user,"OPERATIONS_MANAGER");repo.progressGoal(user,WebUtil.integer(request,"goalId"),WebUtil.decimal(request,"progress"),WebUtil.value(request,"note"));}
                case"payroll:process"->{require(user,"PAYROLL_SPECIALIST");repo.processPayroll(user,WebUtil.value(request,"periodLabel"),LocalDate.parse(WebUtil.value(request,"startDate")),LocalDate.parse(WebUtil.value(request,"endDate")));}
                case"access:create"->{require(user,"IT_SYSTEM_ADMINISTRATOR");repo.createUser(user,WebUtil.integer(request,"employeeId"),WebUtil.value(request,"username"),WebUtil.value(request,"email"),WebUtil.secret(request,"password"),WebUtil.integer(request,"roleId"));}
                case"access:account"->{require(user,"IT_SYSTEM_ADMINISTRATOR");repo.accountAction(user,WebUtil.integer(request,"id"),WebUtil.value(request,"accountAction"),WebUtil.secret(request,"temporaryPassword"));}
                case"attendance:endAssignment"->{require(user,"ATTENDANCE_COORDINATOR");repo.endShiftAssignment(user,WebUtil.integer(request,"id"),LocalDate.parse(WebUtil.value(request,"endDate")));}
                case"payroll:salary"->{require(user,"PAYROLL_SPECIALIST");repo.saveSalaryStructure(user,WebUtil.integer(request,"employeeId"),WebUtil.decimal(request,"base"),LocalDate.parse(WebUtil.value(request,"effectiveDate")),WebUtil.decimal(request,"expectedBase"));}
                case"attendance:record"->{require(user,"ATTENDANCE_COORDINATOR");repo.recordAttendance(user,WebUtil.value(request,"id").isBlank()?null:WebUtil.integer(request,"id"),WebUtil.integer(request,"employeeId"),LocalDate.parse(WebUtil.value(request,"workDate")),java.time.LocalDateTime.parse(WebUtil.value(request,"clockIn")),WebUtil.value(request,"clockOut").isBlank()?null:java.time.LocalDateTime.parse(WebUtil.value(request,"clockOut")),WebUtil.value(request,"reason"));}
                case"attendance:void"->{require(user,"ATTENDANCE_COORDINATOR");repo.voidAttendance(user,WebUtil.integer(request,"id"),WebUtil.value(request,"reason"));}
                case"leave:update"->{require(user,"EMPLOYEE");repo.saveLeave(user,WebUtil.integer(request,"id"),WebUtil.integer(request,"leaveTypeId"),LocalDate.parse(WebUtil.value(request,"startDate")),LocalDate.parse(WebUtil.value(request,"endDate")),WebUtil.value(request,"reason"));}
                case"leave:cancel"->{require(user,"EMPLOYEE");repo.cancelLeave(user,WebUtil.integer(request,"id"));}
                case"performance:edit"->{require(user,"OPERATIONS_MANAGER");repo.editReview(user,WebUtil.integer(request,"id"),WebUtil.value(request,"reviewAction").equals("delete")?java.math.BigDecimal.ONE:WebUtil.decimal(request,"rating"),WebUtil.value(request,"comments"),WebUtil.value(request,"reviewAction"));}
                case"payroll:draft"->{require(user,"PAYROLL_SPECIALIST");repo.createPayrollDraft(user,WebUtil.value(request,"periodLabel"),LocalDate.parse(WebUtil.value(request,"startDate")),LocalDate.parse(WebUtil.value(request,"endDate")));}
                case"payroll:update"->{require(user,"PAYROLL_SPECIALIST");repo.editPayroll(user,WebUtil.integer(request,"id"),WebUtil.decimal(request,"base"),WebUtil.decimal(request,"allowances"),WebUtil.decimal(request,"deductions"),WebUtil.decimal(request,"expectedBase"));}
                case"payroll:lifecycle"->{require(user,"PAYROLL_SPECIALIST");repo.payrollLifecycle(user,WebUtil.integer(request,"id"),WebUtil.value(request,"runAction"));}
                case"access:update"->{require(user,"IT_SYSTEM_ADMINISTRATOR");repo.updateUser(user,WebUtil.integer(request,"id"),WebUtil.integer(request,"roleId"),WebUtil.value(request,"email"));}
                case"notifications:read","notifications:delete"->repo.notificationAction(user,action,WebUtil.integer(request,"id"));
                case"notifications:all"->repo.notificationAction(user,action,null);
                case"account:password"->{String next=WebUtil.secret(request,"newPassword");if(next.length()<8||!next.matches(".*[A-Z].*")||!next.matches(".*[0-9].*"))throw new IllegalArgumentException("New password needs 8 characters, one uppercase letter and one number");repo.changePassword(user,WebUtil.secret(request,"currentPassword"),next);request.getSession().setAttribute("user",new UserSession(user.id(),user.employeeId(),user.username(),user.fullName(),user.role(),false,user.securityVersion()+1));}
                default->throw new IllegalArgumentException("Unsupported action");
            }
            WebUtil.flash(request,"success","Operation completed successfully.");
        }catch(SecurityException e){response.sendError(403);return;}catch(Exception e){getServletContext().log("EMS operation failed",e);WebUtil.flash(request,"error",friendly(e));}
        response.sendRedirect(request.getContextPath()+"/app/"+module);
    }
    private void lookups(HttpServletRequest r,boolean employees,boolean shifts,boolean roles)throws Exception{if(employees)r.setAttribute("employees",repo.employees(""));if(shifts)r.setAttribute("shifts",repo.shifts());if(roles)r.setAttribute("roles",repo.roles());r.setAttribute("departments",repo.departments());r.setAttribute("designations",repo.designations());}
    private String module(HttpServletRequest r){String p=r.getPathInfo();return p==null||p.equals("/")?"dashboard":p.substring(1).split("/")[0];}
    private UserSession user(HttpServletRequest r){return(UserSession)r.getSession().getAttribute("user");}
    private boolean allowed(String module,UserSession user){return ACCESS.getOrDefault(module,Set.of()).contains(user.role());}
    private void require(UserSession user,String...roles){if(!user.hasRole(roles))throw new SecurityException("You are not authorized for this operation");}
    private String title(String m){return switch(m){case"employees"->"Employee Profiles";case"attendance"->"Attendance & Shifts";case"leave"->"Leave & Absence";case"performance"->"Performance & Evaluation";case"payroll"->"Payroll & Salary";case"access"->"Role & User Access";case"reports"->"Reports & Audit";case"notifications"->"Notifications";case"account"->"My Account";default->"Dashboard";};}
    private LocalDate date(HttpServletRequest r,String key,LocalDate fallback){String v=WebUtil.value(r,key);return v.isBlank()?fallback:LocalDate.parse(v);}
    private String friendly(Exception e){
        if(e instanceof IllegalArgumentException || e instanceof java.time.DateTimeException)return e instanceof NumberFormatException || e instanceof java.time.DateTimeException?"Enter valid numbers and dates.":e.getMessage();
        if(e instanceof java.sql.SQLException sql){if(sql.getErrorCode()==2601||sql.getErrorCode()==2627)return "A record with the same unique value already exists.";if(sql.getErrorCode()>=50000&&sql.getErrorCode()<50100)return sql.getMessage();}
        return "The operation could not be completed. Check the values and record status, or contact your administrator.";
    }
}
