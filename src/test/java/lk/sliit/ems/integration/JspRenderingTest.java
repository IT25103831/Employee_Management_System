package lk.sliit.ems.integration;

import org.junit.jupiter.api.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lk.sliit.ems.model.UserSession;
import java.nio.file.*;import java.net.*;import java.net.http.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Tomcat/Jasper rendering with fixture data. Does not claim database or login coverage. */
class JspRenderingTest {
 static Tomcat tomcat;static int port;
 @BeforeAll static void start()throws Exception{
  tomcat=new Tomcat();tomcat.setBaseDir(Files.createTempDirectory("ems-tomcat").toString());tomcat.setPort(0);tomcat.getConnector();
  Context ctx=tomcat.addContext("",Path.of("src/main/webapp").toAbsolutePath().toString());ctx.setParentClassLoader(JspRenderingTest.class.getClassLoader());
  ctx.addServletContainerInitializer(new JasperInitializer(),null);
  Tomcat.addServlet(ctx,"jsp",new JspServlet());ctx.addServletMappingDecoded("*.jsp","jsp");
  Tomcat.addServlet(ctx,"fixture",new HttpServlet(){protected void doGet(HttpServletRequest r,HttpServletResponse s)throws java.io.IOException,ServletException{
   String module=r.getParameter("module"),role=r.getParameter("role");
   r.setAttribute("currentUser",new UserSession(7,7,"test","Test User",role,false));r.getSession().setAttribute("csrf","token");r.setAttribute("module",module);r.setAttribute("title","Test "+module);
   Map<String,Object> row=new HashMap<>();
   for(String k:List.of("employee_id","department_id","designation_id","attendance_id","leave_request_id","leave_type_id","review_id","goal_id","payroll_record_id","payroll_run_id","user_id","role_id","shift_id","review_period_id","notification_id","history_id","task_id","audit_id","entity_id"))row.put(k,1);
   for(String k:List.of("full_name","employee_code","nic","email","phone","department_name","designation_name","shift_name","leave_type_name","reason","comments","reviewer_name","title","message","details","username","role_name","period_name","period_label","action_name","entity_type","notification_type","instructions","completion_comments","feedback","address"))row.put(k,"<script>alert(1)</script> Chloé — அருண் — කොළඹ");
   for(String k:List.of("overall_rating","target_value","current_value","weight_percent","base_salary","gross_pay","employee_epf","employer_epf","etf","total_deductions","net_pay","hours_worked","days_requested","entitled_days","used_days","available_days","failed_attempts"))row.put(k,new java.math.BigDecimal("3.00"));
   for(String k:List.of("work_date","start_date","end_date","employment_date","review_date","due_date","period_start","period_end"))row.put(k,"2026-09-30");
   row.put("status",module.equals("leave")?"PENDING_SUPERVISOR":module.equals("performance")||module.equals("payroll")?"DRAFT":"ACTIVE");row.put("is_read",false);row.put("role_code","EMPLOYEE");row.put("year",2026);
   for(String k:List.of("records","history","goals","periods","runs","roles","leaveTypes","balances","performanceHistory","tasks"))r.setAttribute(k,List.of(row));
   Map<String,Object> employee=new HashMap<>(row);employee.put("status","ACTIVE");for(String k:List.of("employees","taskEmployees","departments","designations","shifts"))r.setAttribute(k,List.of(employee));
   if(r.getParameter("taskStatus")!=null)row.put("status",r.getParameter("taskStatus"));
   r.setAttribute("profile",row);row.put("has_photo",1);row.put("deadline","2026-10-20");row.put("rating",new java.math.BigDecimal("4.00"));
   r.setAttribute("change",Map.of("employee_code","EMP001","full_name","Test User","old_salary",new java.math.BigDecimal("150000")));r.setAttribute("newSalary",new java.math.BigDecimal("190000"));r.setAttribute("difference",new java.math.BigDecimal("40000"));
   r.setAttribute("summary",List.of(Map.of("department","Operations","employees",2)));r.setAttribute("from","2026-09-01");r.setAttribute("to","2026-09-30");r.setAttribute("reportNames",List.of("audit"));
   r.setAttribute("stats",Map.of("activeEmployees",8,"presentToday",7,"pendingLeave",1,"openPayroll",0,"reviews",1,"unread",1,"totalUsers",7,"ownAttendance",2,"ownLeave",1,"ownGoals",1));
   r.getRequestDispatcher(module.equals("salary-confirm")?"/WEB-INF/views/salary-confirm.jsp":module.equals("login")?"/WEB-INF/views/login.jsp":module.equals("dashboard")?"/WEB-INF/views/dashboard.jsp":"/WEB-INF/views/module.jsp").forward(r,s);
  }});ctx.addServletMappingDecoded("/fixture","fixture");tomcat.start();port=tomcat.getConnector().getLocalPort();
 }
 @AfterAll static void stop()throws Exception{if(tomcat!=null){tomcat.stop();tomcat.destroy();}}
 @TestFactory Collection<DynamicTest> rendersViews(){List<DynamicTest> tests=new ArrayList<>();
  for(String pair:List.of("login:EMPLOYEE","dashboard:EMPLOYEE","employees:HR_MANAGER","attendance:ATTENDANCE_COORDINATOR","attendance:EMPLOYEE","leave:EMPLOYEE","leave:DEPARTMENT_SUPERVISOR","performance:OPERATIONS_MANAGER","performance:EMPLOYEE","payroll:PAYROLL_SPECIALIST","payroll:EMPLOYEE","access:IT_SYSTEM_ADMINISTRATOR","reports:IT_SYSTEM_ADMINISTRATOR","notifications:EMPLOYEE","account:EMPLOYEE","salary-confirm:PAYROLL_SPECIALIST","dashboard:HR_MANAGER","performance:EMPLOYEE:ASSIGNED","performance:EMPLOYEE:REVIEWED","performance:OPERATIONS_MANAGER:COMPLETED"))tests.add(DynamicTest.dynamicTest(pair,()->{
   String[] parts=pair.split(":");var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/fixture?module="+parts[0]+"&role="+parts[1]+(parts.length>2?"&taskStatus="+parts[2]:""))).build(),HttpResponse.BodyHandlers.ofString());
   assertEquals(200,response.statusCode(),response.body());assertFalse(response.body().contains("<%@"));assertFalse(response.body().contains("${"));assertTrue(response.body().contains("<!doctype html>"));assertFalse(response.body().contains("<script>alert(1)</script>"),"Unescaped stored data in "+pair);
   if(parts.length>2 && parts[2].equals("ASSIGNED"))assertTrue(response.body().contains("Complete task"));
   if(parts.length>2 && parts[2].equals("COMPLETED"))assertTrue(response.body().contains("Save evaluation"));
   if(parts.length>2 && parts[2].equals("REVIEWED"))assertTrue(response.body().contains("Evaluation:"));
   assertFalse(response.body().contains("2026-Y2-S1-KU-51"));
   if(parts[1].equals("EMPLOYEE")){assertFalse(response.body().contains("All authorized employees"));assertFalse(response.body().contains("name=\"action\" value=\"taskAssign\""));assertFalse(response.body().contains("Recalculate draft record"));}
   if(parts[1].equals("HR_MANAGER")){assertFalse(response.body().contains("/app/attendance"));assertFalse(response.body().contains("Present today"));}
   if(parts[0].equals("account"))assertTrue(response.body().contains("Chloé — அருண் — කොළඹ"));
   if(parts[0].equals("account")){assertTrue(response.body().contains("My Profile"));assertTrue(response.body().contains("multipart/form-data"));}
   if(parts[0].equals("salary-confirm")){assertTrue(response.body().contains("Confirm salary change"));assertTrue(response.body().contains("Increase"));assertTrue(response.body().contains("40000"));}
   if(parts[0].equals("employees")){assertTrue(response.body().contains("name=\"username\""));assertTrue(response.body().contains("Employment status"));assertFalse(response.body().contains("name=\"roleId\""));}
   if(parts[0].equals("performance")&&parts[1].equals("OPERATIONS_MANAGER"))assertTrue(response.body().contains("Save draft review"));
   if(parts[0].equals("leave")&&parts[1].equals("DEPARTMENT_SUPERVISOR"))assertTrue(response.body().contains("Team leave balances"));
  }));return tests;}
}
