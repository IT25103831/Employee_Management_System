package lk.sliit.ems.listener;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import lk.sliit.ems.config.Database;
import lk.sliit.ems.dao.EMSRepository;
import java.util.concurrent.*;

@WebListener
public class AppListener implements ServletContextListener {
    private ScheduledExecutorService scheduler;
    public void contextInitialized(ServletContextEvent event) {
        boolean available=Database.canConnect();
        event.getServletContext().setAttribute("databaseAvailable",available);
        event.getServletContext().log("EmployeeManagementDB connectivity: "+available);
        scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"ems-absence-check");t.setDaemon(true);return t;});
        scheduler.scheduleWithFixedDelay(()->{try{new EMSRepository().detectAbsences();}catch(Exception e){event.getServletContext().log("Absence detection failed; will retry",e);}},15,300,TimeUnit.SECONDS);
    }
    public void contextDestroyed(ServletContextEvent e){if(scheduler!=null)scheduler.shutdownNow();}
}
