package lk.sliit.ems.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sliit.ems.dao.EMSRepository;
import lk.sliit.ems.model.UserSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@WebServlet("/export") public class ExportServlet extends HttpServlet {
 private final EMSRepository repo=new EMSRepository();
 protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
  UserSession u=(UserSession)req.getSession().getAttribute("user");
  String report=req.getParameter("report")==null?"employees":req.getParameter("report");
  Set<String> allowed=switch(u.role()){
   case "HR_MANAGER"->Set.of("employees","leave","performance");
   case "ATTENDANCE_COORDINATOR"->Set.of("attendance","attendance-summary");
   case "DEPARTMENT_SUPERVISOR"->Set.of("attendance","attendance-summary","leave","performance");
   case "IT_SYSTEM_ADMINISTRATOR"->Set.of("audit");
   case "PAYROLL_SPECIALIST"->Set.of("payroll");
   case "OPERATIONS_MANAGER"->Set.of("employees","performance","performance-summary","payroll");
   default->Set.of();};
  if(!allowed.contains(report)){res.sendError(403);return;}
  try{
   LocalDate from=date(req,"from",LocalDate.now().withDayOfYear(1));
   LocalDate to=date(req,"to",LocalDate.now().withMonth(12).withDayOfMonth(31));
   var rows=switch(report){
    case "attendance"->repo.attendance(u);
    case "attendance-summary"->repo.attendanceSummary(u,from,to);
    case "leave"->repo.leaveRequests(u);
    case "performance"->repo.performanceHistory(u,req.getParameter("employeeId")==null?0:Integer.parseInt(req.getParameter("employeeId")));
    case "performance-summary"->repo.performanceSummary();
    case "payroll"->repo.payrollReport(u,from,to);
    case "audit"->repo.auditLogs();
    default->repo.filteredEmployees("","","","");};
   res.setContentType("text/csv;charset=UTF-8");res.setHeader("Content-Disposition","attachment; filename=ems-"+report+".csv");
   if(rows.isEmpty()){res.getWriter().println("No records");return;}
   res.getWriter().println(String.join(",",rows.get(0).keySet()));
   for(Map<String,Object> row:rows)res.getWriter().println(row.values().stream().map(v->csv(v==null?"":v.toString())).reduce((a,b)->a+","+b).orElse(""));
  }catch(IllegalArgumentException|java.time.DateTimeException e){res.sendError(400,"Invalid report filters.");}
  catch(Exception e){getServletContext().log("Report generation failed",e);res.sendError(500,"Report generation failed.");}
 }
 private LocalDate date(HttpServletRequest r,String key,LocalDate fallback){String value=r.getParameter(key);return value==null||value.isBlank()?fallback:LocalDate.parse(value);}
 public static String csv(String s){
  s=s.replace("\r"," ").replace("\n"," ");String t=s.stripLeading();
  if(!t.isEmpty()&&"=+-@".indexOf(t.charAt(0))>=0)s="'"+s;
  return "\""+s.replace("\"","\"\"")+"\"";
 }
}
