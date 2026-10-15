package lk.sliit.ems.controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import lk.sliit.ems.util.WebUtil;import java.io.IOException;
@WebServlet("/logout") public class LogoutServlet extends HttpServlet{protected void doPost(HttpServletRequest r,HttpServletResponse s)throws IOException{if(!WebUtil.validCsrf(r)){s.sendError(403);return;}if(r.getSession(false)!=null)r.getSession(false).invalidate();s.sendRedirect(r.getContextPath()+"/login?logout=1");}}
