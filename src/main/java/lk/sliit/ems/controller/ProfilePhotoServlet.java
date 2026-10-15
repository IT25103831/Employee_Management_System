package lk.sliit.ems.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import lk.sliit.ems.dao.EMSRepository;
import lk.sliit.ems.model.UserSession;
import lk.sliit.ems.util.*;
import java.io.IOException;

@WebServlet("/profile-photo")
@MultipartConfig(maxFileSize=2097152,maxRequestSize=2162688,fileSizeThreshold=0)
public class ProfilePhotoServlet extends HttpServlet {
    private final EMSRepository repo=new EMSRepository();
    private UserSession owner(HttpServletRequest req){
        UserSession user=(UserSession)req.getSession().getAttribute("user");
        if(user==null || user.employeeId()==null)throw new SecurityException();
        // No caller-selected employee ID is accepted, including for privileged accounts.
        if(req.getParameter("employeeId")!=null || req.getParameter("id")!=null)throw new SecurityException();
        return user;
    }
    protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
        try{byte[] png=repo.ownPhoto(owner(req));if(png==null){res.sendError(404);return;}
            res.setContentType("image/png");res.setContentLength(png.length);res.getOutputStream().write(png);
        }catch(SecurityException e){res.sendError(403);}catch(Exception e){getServletContext().log("Profile photo read failed",e);res.sendError(500);}
    }
    protected void doPost(HttpServletRequest req,HttpServletResponse res)throws IOException,ServletException{
        try{UserSession user=owner(req);if(!WebUtil.validCsrf(req)){res.sendError(403);return;}
            Part photo=req.getPart("photo");if(photo==null)throw new IllegalArgumentException("Choose a photo.");
            try(var input=photo.getInputStream()){repo.saveOwnPhoto(user,ProfileImage.normalize(input.readNBytes(2097153)));}finally{photo.delete();}
            WebUtil.flash(req,"success","Profile picture updated.");
        }catch(SecurityException e){res.sendError(403);return;}
        catch(IllegalArgumentException|IllegalStateException e){WebUtil.flash(req,"error","Choose a valid JPEG or PNG image up to 2 MB and 4096 pixels.");}
        catch(Exception e){getServletContext().log("Profile photo upload failed",e);WebUtil.flash(req,"error","Unable to update profile picture.");}
        res.sendRedirect(req.getContextPath()+"/app/account");
    }
}
