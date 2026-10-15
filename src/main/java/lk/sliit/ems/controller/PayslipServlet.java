package lk.sliit.ems.controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import lk.sliit.ems.dao.EMSRepository;import lk.sliit.ems.model.UserSession;import lk.sliit.ems.util.PdfUtil;import java.io.IOException;
@WebServlet("/payslip") public class PayslipServlet extends HttpServlet{
 private final EMSRepository repo=new EMSRepository();protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{UserSession u=(UserSession)req.getSession().getAttribute("user");if(!u.hasRole("EMPLOYEE","PAYROLL_SPECIALIST","OPERATIONS_MANAGER")){res.sendError(403);return;}try{long id=Long.parseLong(req.getParameter("id"));byte[] pdf=PdfUtil.payslip(repo.payslip(u,id));res.setContentType("application/pdf");res.setHeader("Content-Disposition","attachment; filename=payslip-"+id+".pdf");res.setContentLength(pdf.length);res.getOutputStream().write(pdf);}catch(Exception e){res.sendError(404,"Payslip not found");}}
}
