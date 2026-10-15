package lk.sliit.ems.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import lk.sliit.ems.model.UserSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

@WebFilter(urlPatterns={"/app/*","/export","/payslip","/profile-photo"})
public class SecurityFilter implements Filter {
    private final SecureRandom random = new SecureRandom();
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request=(HttpServletRequest)req; HttpServletResponse response=(HttpServletResponse)res;
        HttpSession session=request.getSession(false);
        if (session==null || session.getAttribute("user")==null) { response.sendRedirect(request.getContextPath()+"/login"); return; }
        if (session.getAttribute("csrf")==null) { byte[] b=new byte[24]; random.nextBytes(b); session.setAttribute("csrf", Base64.getUrlEncoder().withoutPadding().encodeToString(b)); }
        response.setHeader("X-Content-Type-Options","nosniff"); response.setHeader("X-Frame-Options","DENY"); response.setHeader("Referrer-Policy","same-origin");
        UserSession user=(UserSession)session.getAttribute("user");
        try {if(!new lk.sliit.ems.dao.EMSRepository().sessionValid(user)){session.invalidate();response.sendRedirect(request.getContextPath()+"/login");return;}}
        catch(java.sql.SQLException e){response.sendError(503,"Account verification is temporarily unavailable.");return;}
        response.setHeader("Cache-Control","no-store");request.setCharacterEncoding("UTF-8");request.setAttribute("currentUser", user);
        if(user.mustChangePassword() && !request.getServletPath().concat(request.getPathInfo()==null?"":request.getPathInfo()).endsWith("/app/account")) { response.sendRedirect(request.getContextPath()+"/app/account"); return; }
        chain.doFilter(req,res);
    }
}
