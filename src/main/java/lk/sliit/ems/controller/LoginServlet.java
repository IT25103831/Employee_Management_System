package lk.sliit.ems.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sliit.ems.dao.EMSRepository;
import lk.sliit.ems.model.UserSession;
import java.io.IOException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private final EMSRepository repository=new EMSRepository();
    protected void doGet(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        if(request.getSession(false)!=null&&request.getSession(false).getAttribute("user")!=null){response.sendRedirect(request.getContextPath()+"/app/dashboard");return;}
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request,response);
    }
    protected void doPost(HttpServletRequest request,HttpServletResponse response)throws ServletException,IOException{
        try{
            UserSession user=repository.authenticate(request.getParameter("username")==null?"":request.getParameter("username").trim(),request.getParameter("password")==null?"":request.getParameter("password"));
            if(user==null){request.setAttribute("error","Invalid username/password or the account is not active.");doGet(request,response);return;}
            HttpSession old=request.getSession(false);if(old!=null)old.invalidate();HttpSession session=request.getSession(true);session.setMaxInactiveInterval(30*60);session.setAttribute("user",user);
            response.sendRedirect(request.getContextPath()+(user.mustChangePassword()?"/app/account":"/app/dashboard"));
        }catch(Exception e){request.setAttribute("error","Database connection failed. Check SQL Server and database.properties.");doGet(request,response);}
    }
}
