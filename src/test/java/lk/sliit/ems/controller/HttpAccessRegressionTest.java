package lk.sliit.ems.controller;
import org.junit.jupiter.api.*;
import org.apache.catalina.startup.Tomcat;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lk.sliit.ems.model.UserSession;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/servlet authorization tests with injected authenticated sessions (no SQL Server). */
class HttpAccessRegressionTest {
 static Tomcat tomcat;static int port;
 @BeforeAll static void start()throws Exception{
  tomcat=new Tomcat();tomcat.setBaseDir(Files.createTempDirectory("ems-auth-http").toString());tomcat.setPort(0);tomcat.getConnector();
  var ctx=tomcat.addContext("",Files.createTempDirectory("ems-auth-web").toString());
  Tomcat.addServlet(ctx,"app",new AppServlet());ctx.addServletMappingDecoded("/app/*","app");
  Tomcat.addServlet(ctx,"export",new ExportServlet());ctx.addServletMappingDecoded("/export","export");
  Tomcat.addServlet(ctx,"photo",new ProfilePhotoServlet());ctx.addServletMappingDecoded("/profile-photo","photo");
  Tomcat.addServlet(ctx,"login-fixture",new HttpServlet(){protected void doGet(HttpServletRequest r,HttpServletResponse s)throws java.io.IOException{
   r.getSession().setAttribute("user",new UserSession(1,1,"test","Test User",r.getParameter("role"),false));r.getSession().setAttribute("csrf","token");s.getWriter().write("fixture");
  }});ctx.addServletMappingDecoded("/fixture","login-fixture");tomcat.start();port=tomcat.getConnector().getLocalPort();
 }
 @AfterAll static void stop()throws Exception{tomcat.stop();tomcat.destroy();}
 @TestFactory List<DynamicTest> deniedHttpRequests(){var tests=new ArrayList<DynamicTest>();
  for(String spec:List.of("HR_MANAGER|GET|/app/attendance|","HR_MANAGER|GET|/export?report=attendance|","HR_MANAGER|GET|/export?report=attendance-summary|",
   "HR_MANAGER|POST|/app/attendance|action=clockin","EMPLOYEE|GET|/app/employees|","EMPLOYEE|GET|/app/access|",
   "EMPLOYEE|GET|/app/performance?employeeId=2|","EMPLOYEE|GET|/app/account?employeeId=2|","EMPLOYEE|GET|/profile-photo?employeeId=2|",
   "EMPLOYEE|GET|/export?report=performance&employeeId=2|","EMPLOYEE|GET|/export?report=payroll&employeeId=2|",
   "EMPLOYEE|POST|/app/performance|action=taskAssign","EMPLOYEE|POST|/app/performance|action=taskReview","EMPLOYEE|POST|/app/performance|action=taskDelete",
   "EMPLOYEE|POST|/app/performance|action=review","EMPLOYEE|POST|/app/performance|action=goal","EMPLOYEE|POST|/app/payroll|action=process",
   "EMPLOYEE|POST|/app/payroll|action=draft","EMPLOYEE|POST|/app/payroll|action=update","EMPLOYEE|POST|/app/payroll|action=salary","EMPLOYEE|POST|/app/payroll|action=lifecycle",
   "HR_MANAGER|POST|/app/employees|action=create&roleId=5","OPERATIONS_MANAGER|POST|/app/performance|action=taskComplete")){
   tests.add(DynamicTest.dynamicTest(spec,()->{String[] v=spec.split("\\|",-1);var cm=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var client=HttpClient.newBuilder().cookieHandler(cm).build();
    client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/fixture?role="+v[0])).build(),HttpResponse.BodyHandlers.ofString());
    var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+v[2]));if(v[1].equals("POST"))request.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("csrf=token&"+v[3]));
    var result=client.send(request.build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,result.statusCode(),result.body());
   }));
  }return tests;
 }
}
