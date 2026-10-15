package lk.sliit.ems.controller;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.CsvSource;
import jakarta.servlet.http.*;import lk.sliit.ems.model.UserSession;import lk.sliit.ems.filter.SecurityFilter;
import java.lang.reflect.Proxy;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AuthorizationTest {
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c,java.lang.reflect.InvocationHandler h){return(T)Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},h);}
 HttpServletRequest request(String role,String module,String action,String token){
  Map<String,Object> values=new HashMap<>();values.put("user",new UserSession(1,1,"test","Test",role,false));values.put("csrf","token");
  HttpSession session=proxy(HttpSession.class,(o,m,a)->m.getName().equals("getAttribute")?values.get(a[0]):null);
  return proxy(HttpServletRequest.class,(o,m,a)->switch(m.getName()){case "getSession"->session;case "getPathInfo"->"/"+module;case "getParameter"->a[0].equals("csrf")?token:a[0].equals("action")?action:"";case "getContextPath"->"";default->null;});
 }
 @ParameterizedTest @CsvSource({"EMPLOYEE,access,create","DEPARTMENT_SUPERVISOR,payroll,process","PAYROLL_SPECIALIST,access,create","HR_MANAGER,access,account","OPERATIONS_MANAGER,attendance,assign","ATTENDANCE_COORDINATOR,employees,create","DEPARTMENT_SUPERVISOR,performance,review","HR_MANAGER,performance,goal","EMPLOYEE,performance,progress","OPERATIONS_MANAGER,payroll,process","IT_SYSTEM_ADMINISTRATOR,employees,deactivate","EMPLOYEE,attendance,record","HR_MANAGER,attendance,void","EMPLOYEE,leave,decide","DEPARTMENT_SUPERVISOR,leave,cancel","OPERATIONS_MANAGER,employees,update","EMPLOYEE,attendance,endAssignment","OPERATIONS_MANAGER,payroll,salary"})
 void forbiddenMutations(String role,String module,String action)throws Exception{AtomicInteger status=new AtomicInteger();HttpServletResponse response=proxy(HttpServletResponse.class,(o,m,a)->{if(m.getName().equals("sendError"))status.set((int)a[0]);return null;});new AppServlet().doPost(request(role,module,action,"token"),response);assertEquals(403,status.get());}
 @Test void invalidCsrfDenied()throws Exception{AtomicInteger status=new AtomicInteger();HttpServletResponse res=proxy(HttpServletResponse.class,(o,m,a)->{if(m.getName().equals("sendError"))status.set((int)a[0]);return null;});new AppServlet().doPost(request("IT_SYSTEM_ADMINISTRATOR","access","create","wrong"),res);assertEquals(403,status.get());}
 @Test void unauthenticatedFilterRedirect()throws Exception{List<String> redirects=new ArrayList<>();HttpServletRequest req=proxy(HttpServletRequest.class,(o,m,a)->m.getName().equals("getContextPath")?"/ems":null);HttpServletResponse res=proxy(HttpServletResponse.class,(o,m,a)->{if(m.getName().equals("sendRedirect"))redirects.add((String)a[0]);return null;});new SecurityFilter().doFilter(req,res,(a,b)->fail("Unauthenticated request passed"));assertEquals(List.of("/ems/login"),redirects);}
}
