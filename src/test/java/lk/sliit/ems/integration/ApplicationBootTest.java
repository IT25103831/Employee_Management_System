package lk.sliit.ems.integration;
import org.junit.jupiter.api.*;
import org.apache.catalina.startup.Tomcat;
import java.nio.file.*;import java.net.*;import java.net.http.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Starts the real annotated application without a database; checks public page and auth boundary. */
class ApplicationBootTest {
 static Tomcat tomcat;static int port;
 static void copyTree(Path from,Path to)throws Exception{try(var files=Files.walk(from)){for(Path p:files.toList()){Path target=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target,StandardCopyOption.REPLACE_EXISTING);}}}
 @BeforeAll static void start()throws Exception{
  Path base=Files.createTempDirectory("ems-real-app"),web=base.resolve("web");copyTree(Path.of("src/main/webapp"),web);copyTree(Path.of("target/classes"),web.resolve("WEB-INF/classes"));Files.createDirectories(web.resolve("WEB-INF/lib"));
  String classpath=System.getProperty("java.class.path");for(String s:classpath.split(java.io.File.pathSeparator))if(s.endsWith(".jar")&&(s.contains("jstl")||s.contains("mssql-jdbc")))Files.copy(Path.of(s),web.resolve("WEB-INF/lib").resolve(Path.of(s).getFileName()),StandardCopyOption.REPLACE_EXISTING);
  tomcat=new Tomcat();tomcat.setBaseDir(base.toString());tomcat.setPort(0);tomcat.getConnector();var context=tomcat.addWebapp("/ems",web.toString());context.setParentClassLoader(ApplicationBootTest.class.getClassLoader());tomcat.start();port=tomcat.getConnector().getLocalPort();
 }
 @AfterAll static void stop()throws Exception{if(tomcat!=null){tomcat.stop();tomcat.destroy();}}
 HttpResponse<String> get(String p)throws Exception{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/ems"+p)).build(),HttpResponse.BodyHandlers.ofString());}
 @Test void realLoginPage()throws Exception{var r=get("/login");assertEquals(200,r.statusCode(),r.body());assertTrue(r.body().contains("name=\"password\""));assertFalse(r.body().contains("<%@"));}
 @Test void unauthenticatedProtectedEndpoints()throws Exception{for(String p:List.of("/app/access","/app/payroll","/export","/payslip?id=1")){var r=get(p);assertEquals(302,r.statusCode(),p+" "+r.body());assertTrue(r.headers().firstValue("location").orElse("").endsWith("/ems/login"));}}
 @Test void logoutRequiresCsrf()throws Exception{var r=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/ems/logout")).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertEquals(403,r.statusCode());}
 @Test void missingPage()throws Exception{assertEquals(404,get("/not-a-real-route").statusCode());}
}
