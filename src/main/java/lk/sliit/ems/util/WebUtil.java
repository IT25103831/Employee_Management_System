package lk.sliit.ems.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.security.MessageDigest;

public final class WebUtil {
    private WebUtil() {}
    public static String value(HttpServletRequest request, String name) {
        String value = request.getParameter(name); return value == null ? "" : value.trim();
    }
    public static String secret(HttpServletRequest request,String name){String s=request.getParameter(name);return s==null?"":s;}
    public static int integer(HttpServletRequest request, String name) { return Integer.parseInt(value(request, name)); }
    public static BigDecimal decimal(HttpServletRequest request, String name) { return new BigDecimal(value(request, name)); }
    public static void flash(HttpServletRequest request, String type, String message) {
        request.getSession().setAttribute("flashType", type); request.getSession().setAttribute("flashMessage", message);
    }
    public static boolean validCsrf(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute("csrf") != null && MessageDigest.isEqual(String.valueOf(s.getAttribute("csrf")).getBytes(), value(request,"csrf").getBytes());
    }
}
