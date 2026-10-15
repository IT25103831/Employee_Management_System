package lk.sliit.ems.dao;

import lk.sliit.ems.config.Database;
import lk.sliit.ems.model.UserSession;
import lk.sliit.ems.util.PasswordUtil;
import lk.sliit.ems.service.Validation;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EMSRepository {
    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }

    public UserSession authenticate(String username, String password) throws SQLException {
        String sql="SELECT u.user_id,u.employee_id,u.username,u.password_hash,u.status,u.failed_attempts,u.must_change_password,u.security_version,u.temporary_password_expires_at,e.status employee_status,r.role_code,COALESCE(e.full_name,u.username) full_name " +
                "FROM user_accounts u JOIN roles r ON r.role_id=u.role_id LEFT JOIN employees e ON e.employee_id=u.employee_id WHERE u.username=?";
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,username);
            try(ResultSet r=p.executeQuery()){
                if(!r.next()) return null;
                int id=r.getInt("user_id");
                if(!"ACTIVE".equals(r.getString("status")) || "INACTIVE".equals(r.getString("employee_status"))) return null;
                if(r.getTimestamp("temporary_password_expires_at")!=null && r.getTimestamp("temporary_password_expires_at").toLocalDateTime().isBefore(LocalDateTime.now())) return null;
                if(!PasswordUtil.verify(password,r.getString("password_hash"))){
                    try(PreparedStatement f=c.prepareStatement("UPDATE user_accounts SET failed_attempts=failed_attempts+1,status=CASE WHEN failed_attempts>=4 THEN 'LOCKED' ELSE status END WHERE user_id=?")){f.setInt(1,id);f.executeUpdate();}
                    return null;
                }
                try(PreparedStatement ok=c.prepareStatement("UPDATE user_accounts SET failed_attempts=0,last_login_at=SYSDATETIME() WHERE user_id=?")){ok.setInt(1,id);ok.executeUpdate();}
                Integer employeeId=(Integer)r.getObject("employee_id");
                audit(c,id,"LOGIN","USER_ACCOUNT",id,"Successful login");
                return new UserSession(id,employeeId,r.getString("username"),r.getString("full_name"),r.getString("role_code"),r.getBoolean("must_change_password"),r.getInt("security_version"));
            }
        }
    }

    public Map<String,Object> dashboard(UserSession user) throws SQLException {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("activeEmployees",scalar("SELECT COUNT(*) FROM employees WHERE status='ACTIVE'"));
        if(!user.hasRole("HR_MANAGER"))out.put("presentToday",scalar("SELECT COUNT(*) FROM attendance_records WHERE work_date=CAST(GETDATE() AS DATE) AND clock_in IS NOT NULL"));
        out.put("pendingLeave",scalar("SELECT COUNT(*) FROM leave_requests WHERE status IN ('PENDING_SUPERVISOR','PENDING_HR')"));
        out.put("openPayroll",scalar("SELECT COUNT(*) FROM payroll_runs WHERE status<>'CLOSED'"));
        out.put("reviews",scalar("SELECT COUNT(*) FROM performance_reviews WHERE review_date>=DATEADD(month,-6,GETDATE())"));
        out.put("unread",scalar("SELECT COUNT(*) FROM notifications WHERE user_id="+user.id()+" AND is_read=0"));
        out.put("totalUsers",scalar("SELECT COUNT(*) FROM user_accounts"));
        if(user.employeeId()!=null){out.put("ownAttendance",scalar("SELECT COUNT(*) FROM attendance_records WHERE employee_id="+user.employeeId()));out.put("ownLeave",scalar("SELECT COUNT(*) FROM leave_requests WHERE employee_id="+user.employeeId()+" AND status IN ('PENDING_SUPERVISOR','PENDING_HR')"));out.put("ownGoals",scalar("SELECT COUNT(*) FROM employee_goals WHERE employee_id="+user.employeeId()+" AND status='ACTIVE'"));out.put("ownPayslips",scalar("SELECT COUNT(*) FROM payroll_records pr JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id WHERE r.status='CLOSED' AND employee_id="+user.employeeId()));}
        return out;
    }

    private int scalar(String sql) throws SQLException {
        try(Connection c=Database.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)){r.next();return r.getInt(1);}
    }

    public List<Map<String,Object>> employees(String search) throws SQLException {
        String sql="SELECT TOP 200 e.employee_id,e.department_id,e.designation_id,e.employee_code,e.full_name,e.nic,e.email,e.phone,d.department_name,g.designation_name,e.employment_date,e.status " +
                "FROM employees e JOIN departments d ON d.department_id=e.department_id JOIN designations g ON g.designation_id=e.designation_id " +
                "WHERE (?='' OR e.full_name LIKE ? OR e.employee_code LIKE ? OR d.department_name LIKE ? OR g.designation_name LIKE ? OR e.status=?) ORDER BY e.full_name";
        return query(sql,p->{String q="%"+search+"%";p.setString(1,search);p.setString(2,q);p.setString(3,q);p.setString(4,q);p.setString(5,q);p.setString(6,search);});
    }
    public List<Map<String,Object>> employeeHistory()throws SQLException{return query("SELECT TOP 100 h.history_id,e.employee_code,e.full_name,u.username,h.action_name,h.details,h.changed_at FROM employee_profile_history h JOIN employees e ON e.employee_id=h.employee_id JOIN user_accounts u ON u.user_id=h.changed_by ORDER BY h.changed_at DESC",p->{});}
    public void saveEmployee(UserSession actor, Map<String,String> v) throws SQLException {
        requireRole(actor,"HR_MANAGER");
        if(v.containsKey("roleId") || v.containsKey("role"))throw new SecurityException("Role assignment is managed by IT.");
        Validation.text(v.get("username"),"Username",60);
        Validation.require(v.get("username").matches("[A-Za-z0-9_.-]{3,60}"),"Enter a valid username (3–60 characters).");
        Validation.password(v.get("password"));
        Validation.text(v.get("employeeCode"),"Employee code",20);
        Validation.require(v.get("employeeCode").matches("[A-Za-z0-9-]{3,20}"),"Employee code must contain 3–20 letters, numbers or hyphens.");
        Validation.text(v.get("fullName"),"Full name",120); Validation.text(v.get("nic"),"NIC",20);
        Validation.text(v.get("address"),"Address",250); Validation.email(v.get("email")); Validation.phone(v.get("phone"));
        Validation.employment(LocalDate.parse(v.get("dob")),LocalDate.parse(v.get("employmentDate")),LocalDate.now());
        transaction(c->{
            check(c,"SELECT 1 FROM departments WHERE department_id=? AND status='ACTIVE'","Select an active department.",Integer.parseInt(v.get("departmentId")));
            check(c,"SELECT 1 FROM designations WHERE designation_id=? AND status='ACTIVE'","Select an active designation.",Integer.parseInt(v.get("designationId")));
            String sql="INSERT INTO employees(employee_code,full_name,nic,date_of_birth,email,phone,address,department_id,designation_id,employment_date,status) VALUES(?,?,?,?,?,?,?,?,?,?,'ACTIVE')";
            try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                p.setString(1,v.get("employeeCode"));p.setString(2,v.get("fullName"));p.setString(3,v.get("nic"));p.setDate(4,Date.valueOf(v.get("dob")));
                p.setString(5,v.get("email"));p.setString(6,v.get("phone"));p.setString(7,v.get("address"));p.setInt(8,Integer.parseInt(v.get("departmentId")));
                p.setInt(9,Integer.parseInt(v.get("designationId")));p.setDate(10,Date.valueOf(v.get("employmentDate")));p.executeUpdate();
                try(ResultSet keys=p.getGeneratedKeys()){keys.next();int id=keys.getInt(1);
                    int accounts=execute(c,"INSERT INTO user_accounts(employee_id,role_id,username,email,password_hash,status,must_change_password,temporary_password_expires_at) SELECT ?,role_id,?,?,?,'ACTIVE',1,DATEADD(hour,24,SYSDATETIME()) FROM roles WHERE role_code='EMPLOYEE'",id,v.get("username"),v.get("email"),PasswordUtil.hash(v.get("password")));
                    Validation.require(accounts==1,"Employee role is not configured.");
                    audit(c,actor.id(),"CREATE","USER_ACCOUNT",null,"Employee account created for employee "+id);
                    execute(c,"INSERT INTO leave_balances(employee_id,leave_type_id,year,entitled_days) SELECT ?,leave_type_id,YEAR(GETDATE()),default_entitlement FROM leave_types WHERE status='ACTIVE'",id);history(c,id,actor.id(),"CREATED","Employee profile created");audit(c,actor.id(),"CREATE","EMPLOYEE",id,v.get("employeeCode"));}
            }
            return null;
        });
    }
    public void updateEmployee(UserSession actor,int id,String email,String phone,int departmentId,int designationId) throws SQLException {
        updateEmployee(actor,id,email,phone,departmentId,designationId,null);
    }
    public void updateEmployee(UserSession actor,int id,String email,String phone,int departmentId,int designationId,String status) throws SQLException {
        requireRole(actor,"HR_MANAGER");
        Validation.require(status==null || java.util.Set.of("ACTIVE","INACTIVE").contains(status),"Select Active or Inactive.");
        Validation.email(email);Validation.phone(phone);
        transaction(c->{
            check(c,"SELECT 1 FROM departments WHERE department_id=? AND status='ACTIVE'","Select an active department.",departmentId);
            check(c,"SELECT 1 FROM designations WHERE designation_id=? AND status='ACTIVE'","Select an active designation.",designationId);
            try(PreparedStatement p=c.prepareStatement("UPDATE employees SET email=?,phone=?,department_id=?,designation_id=?,updated_at=SYSDATETIME() WHERE employee_id=?")){
                p.setString(1,email);p.setString(2,phone);p.setInt(3,departmentId);p.setInt(4,designationId);p.setInt(5,id);if(p.executeUpdate()!=1)throw new SQLException("Employee not found");
            }
            if(status!=null) employeeStatus(c,actor,id,status);
            history(c,id,actor.id(),"UPDATED","Contact or assignment updated");audit(c,actor.id(),"UPDATE","EMPLOYEE",id,"Profile updated");return null;
        });
    }
    public void deactivateEmployee(UserSession actor,int id) throws SQLException {
        requireRole(actor,"HR_MANAGER");
        transaction(c->{employeeStatus(c,actor,id,"INACTIVE");return null;});
    }
    private void employeeStatus(Connection c,UserSession actor,int id,String status)throws SQLException{
        String previous;
        try(PreparedStatement p=c.prepareStatement("SELECT status FROM employees WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=?")){
            bind(p,id);try(ResultSet r=p.executeQuery()){Validation.require(r.next(),"Employee not found.");previous=r.getString(1);}}
        if(previous.equals(status))return;
        Validation.require(!java.util.Objects.equals(actor.employeeId(),id),"Use another HR manager to change your employment status.");
        execute(c,"UPDATE employees SET status=?,updated_at=SYSDATETIME() WHERE employee_id=?",status,id);
        // Restore only legacy account suspensions attributable to employee deactivation;
        // a later explicit IT deactivation/lock remains under IT control.
        if(status.equals("ACTIVE"))execute(c,"UPDATE u SET status='ACTIVE' FROM user_accounts u WHERE employee_id=? AND u.status='INACTIVE' AND EXISTS(SELECT 1 FROM audit_logs a WHERE a.entity_type='EMPLOYEE' AND a.entity_id=? AND a.action_name='DEACTIVATE' AND NOT EXISTS(SELECT 1 FROM audit_logs x WHERE x.entity_type='USER_ACCOUNT' AND x.entity_id=u.user_id AND x.action_name IN('DEACTIVATE','LOCK') AND x.created_at>=a.created_at))",id,id);
        // Employee inactivity independently blocks authentication. Keep IT-managed account locks/status intact.
        execute(c,"UPDATE user_accounts SET security_version=security_version+1 WHERE employee_id=?",id);
        history(c,id,actor.id(),status.equals("ACTIVE")?"REACTIVATED":"DEACTIVATED",previous+" -> "+status);
        audit(c,actor.id(),"STATUS","EMPLOYEE",id,previous+" -> "+status+"; existing account and history retained");
    }
    private void history(Connection c,int employeeId,int actorId,String action,String details)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_profile_history(employee_id,changed_by,action_name,details) VALUES(?,?,?,?)")){p.setInt(1,employeeId);p.setInt(2,actorId);p.setString(3,action);p.setString(4,details);p.executeUpdate();}}

    public List<Map<String,Object>> departments() throws SQLException {return query("SELECT department_id,department_name FROM departments WHERE status='ACTIVE' ORDER BY department_name",p->{});}
    public List<Map<String,Object>> designations() throws SQLException {return query("SELECT designation_id,designation_name FROM designations WHERE status='ACTIVE' ORDER BY designation_name",p->{});}

    public List<Map<String,Object>> attendance(UserSession user) throws SQLException {
        requireRole(user,"EMPLOYEE","ATTENDANCE_COORDINATOR","DEPARTMENT_SUPERVISOR","PAYROLL_SPECIALIST");
        String scope=user.hasRole("EMPLOYEE")?" WHERE e.employee_id=?":user.hasRole("DEPARTMENT_SUPERVISOR")?" WHERE e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT a.attendance_id,e.employee_id,e.employee_code,e.full_name,a.work_date,s.shift_name,a.clock_in,a.clock_out,a.hours_worked,a.exception_type,a.status FROM attendance_records a JOIN employees e ON e.employee_id=a.employee_id LEFT JOIN shifts s ON s.shift_id=a.shift_id"+scope+" ORDER BY a.work_date DESC,a.attendance_id DESC",p->{if(!scope.isEmpty())p.setObject(1,user.employeeId());});
    }
    public void clock(UserSession user,String kind)throws SQLException{
        requireRole(user,"EMPLOYEE");
        Validation.require(user.employeeId()!=null,"No employee profile is linked.");
        Validation.require(java.util.Set.of("in","out").contains(kind),"Invalid clock action.");
        transaction(c->{activeEmployee(c,user.employeeId());
            if(kind.equals("in")){
                check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM attendance_records WHERE employee_id=? AND clock_in IS NOT NULL AND clock_out IS NULL AND status<>'VOID')","An open clock-in already exists.",user.employeeId());
                int n=execute(c,";WITH candidates AS (SELECT sa.employee_id,s.shift_id,d.work_date,DATEADD(second,DATEDIFF(second,CAST('00:00' AS TIME),s.start_time),CAST(d.work_date AS DATETIME2)) begins,DATEADD(day,CASE WHEN s.end_time<=s.start_time THEN 1 ELSE 0 END,DATEADD(second,DATEDIFF(second,CAST('00:00' AS TIME),s.end_time),CAST(d.work_date AS DATETIME2))) ends FROM (VALUES(CAST(GETDATE() AS DATE)),(CAST(DATEADD(day,-1,GETDATE()) AS DATE))) d(work_date) JOIN shift_assignments sa ON d.work_date BETWEEN sa.start_date AND ISNULL(sa.end_date,'9999-12-31') JOIN shifts s ON s.shift_id=sa.shift_id WHERE sa.employee_id=? AND s.status='ACTIVE') INSERT INTO attendance_records(employee_id,work_date,shift_id,clock_in,status,exception_type) SELECT TOP 1 employee_id,work_date,shift_id,SYSDATETIME(),'OPEN',CASE WHEN SYSDATETIME()>DATEADD(minute,10,begins) THEN 'LATE' END FROM candidates c WHERE SYSDATETIME()>=DATEADD(hour,-2,begins) AND SYSDATETIME()<ends AND NOT EXISTS(SELECT 1 FROM attendance_records a WHERE a.employee_id=c.employee_id AND a.work_date=c.work_date) AND NOT EXISTS(SELECT 1 FROM leave_requests l WHERE l.employee_id=c.employee_id AND l.status='APPROVED' AND c.work_date BETWEEN l.start_date AND l.end_date) AND NOT EXISTS(SELECT 1 FROM payroll_runs WHERE status='CLOSED' AND c.work_date BETWEEN period_start AND period_end) ORDER BY begins DESC",user.employeeId());
                if(n==0)n=execute(c,"INSERT INTO attendance_records(employee_id,work_date,shift_id,clock_in,status) SELECT ?,CAST(GETDATE() AS DATE),NULL,SYSDATETIME(),'OPEN' WHERE NOT EXISTS(SELECT 1 FROM shift_assignments sa JOIN shifts s ON s.shift_id=sa.shift_id WHERE sa.employee_id=? AND s.status='ACTIVE' AND CAST(GETDATE() AS DATE) BETWEEN sa.start_date AND ISNULL(sa.end_date,'9999-12-31')) AND NOT EXISTS(SELECT 1 FROM attendance_records WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND work_date=CAST(GETDATE() AS DATE)) AND NOT EXISTS(SELECT 1 FROM leave_requests WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND status='APPROVED' AND CAST(GETDATE() AS DATE) BETWEEN start_date AND end_date) AND NOT EXISTS(SELECT 1 FROM payroll_runs WHERE status='CLOSED' AND CAST(GETDATE() AS DATE) BETWEEN period_start AND period_end)",user.employeeId(),user.employeeId(),user.employeeId(),user.employeeId());
                Validation.require(n==1,"Already recorded, on approved leave, outside your assigned shift or locked by finalized payroll.");
            }else{
                int n=execute(c,"UPDATE a SET clock_out=SYSDATETIME(),hours_worked=CAST(CASE WHEN DATEDIFF(minute,clock_in,SYSDATETIME())>ISNULL(s.break_minutes,0) THEN (DATEDIFF(minute,clock_in,SYSDATETIME())-ISNULL(s.break_minutes,0))/60.0 ELSE 0 END AS DECIMAL(6,2)),status='FINALIZED' FROM attendance_records a LEFT JOIN shifts s ON s.shift_id=a.shift_id WHERE employee_id=? AND clock_in IS NOT NULL AND clock_out IS NULL AND a.status='OPEN' AND clock_in<=SYSDATETIME() AND DATEADD(hour,24,clock_in)>=SYSDATETIME() AND NOT EXISTS(SELECT 1 FROM payroll_runs WHERE status='CLOSED' AND a.work_date BETWEEN period_start AND period_end)",user.employeeId());
                Validation.require(n==1,"No valid open clock-in (within 24 hours). Contact the attendance coordinator.");
            }audit(c,user.id(),"CLOCK_"+kind.toUpperCase(),"ATTENDANCE",null,"Employee clock action");return null;});
    }
    public void assignShift(UserSession actor,int employeeId,int shiftId,LocalDate start,LocalDate end)throws SQLException{
        Validation.require(end==null||!end.isBefore(start),"Shift end date must not precede start date.");
        transaction(c->{activeEmployee(c,employeeId);check(c,"SELECT 1 FROM shifts WHERE shift_id=? AND status='ACTIVE'","Select an active shift.",shiftId);
            int n=execute(c,"INSERT INTO shift_assignments(employee_id,shift_id,start_date,end_date,assigned_by) SELECT ?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM shift_assignments WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND ?<=ISNULL(end_date,'9999-12-31') AND ISNULL(?, '9999-12-31')>=start_date)",employeeId,shiftId,start,end,actor.id(),employeeId,start,end);
            Validation.require(n==1,"This employee already has an overlapping shift assignment.");audit(c,actor.id(),"ASSIGN","SHIFT",employeeId,"Shift assigned");return null;});
    }
    public List<Map<String,Object>> shifts()throws SQLException{return query("SELECT shift_id,shift_name,start_time,end_time,break_minutes,status FROM shifts ORDER BY shift_name",p->{});}

    public List<Map<String,Object>> leaveRequests(UserSession user)throws SQLException{
        String where=user.hasRole("EMPLOYEE")?" WHERE l.employee_id=?":user.hasRole("DEPARTMENT_SUPERVISOR")?" WHERE e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT l.leave_request_id,l.leave_type_id,e.employee_id,e.employee_code,e.full_name,t.leave_type_name,l.start_date,l.end_date,l.days_requested,l.reason,l.status,l.submitted_at FROM leave_requests l JOIN employees e ON e.employee_id=l.employee_id JOIN leave_types t ON t.leave_type_id=l.leave_type_id"+where+" ORDER BY l.submitted_at DESC",p->{if(!where.isEmpty())p.setInt(1,user.employeeId());});
    }
    public List<Map<String,Object>> leaveTypes()throws SQLException{return query("SELECT leave_type_id,leave_type_name,requires_hr_approval FROM leave_types WHERE status='ACTIVE' ORDER BY leave_type_name",p->{});}
    public List<Map<String,Object>> leaveBalances(UserSession user)throws SQLException{
        String where=user.hasRole("EMPLOYEE")?" WHERE b.employee_id=?":user.hasRole("DEPARTMENT_SUPERVISOR")?" WHERE e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT b.employee_id,e.full_name,t.leave_type_name,b.year,b.entitled_days,b.used_days,(b.entitled_days-b.used_days) available_days FROM leave_balances b JOIN employees e ON e.employee_id=b.employee_id JOIN leave_types t ON t.leave_type_id=b.leave_type_id"+where+" ORDER BY e.full_name,t.leave_type_name",p->{if(!where.isEmpty())p.setObject(1,user.employeeId());});
    }
    public void applyLeave(UserSession user,int typeId,LocalDate start,LocalDate end,String reason)throws SQLException{
        saveLeave(user,null,typeId,start,end,reason);
    }
    public void saveLeave(UserSession user,Integer id,int typeId,LocalDate start,LocalDate end,String reason)throws SQLException{
        requireRole(user,"EMPLOYEE");
        Validation.require(user.employeeId()!=null,"An employee profile is required.");
        int days=Validation.leaveDays(start,end,LocalDate.now());Validation.text(reason,"Reason",500);
        transaction(c->{
            activeEmployee(c,user.employeeId());
            if(id!=null) check(c,"SELECT 1 FROM leave_requests WITH (UPDLOCK,HOLDLOCK) WHERE leave_request_id=? AND employee_id=? AND status='PENDING_SUPERVISOR'", "Only your pending supervisor request can be edited.",id,user.employeeId());
            check(c,"SELECT 1 FROM leave_types WHERE leave_type_id=? AND status='ACTIVE'","Select an active leave type.",typeId);
            check(c,"SELECT 1 FROM leave_balances b WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND leave_type_id=? AND year=? AND entitled_days-used_days-COALESCE((SELECT SUM(days_requested) FROM leave_requests WHERE employee_id=b.employee_id AND leave_type_id=b.leave_type_id AND YEAR(start_date)=b.year AND status IN ('PENDING_SUPERVISOR','PENDING_HR') AND leave_request_id<>?),0)>=?", "Insufficient available balance, including pending requests.",user.employeeId(),typeId,start.getYear(),id==null?-1:id,days);
            check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM leave_requests WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND leave_request_id<>? AND status NOT IN ('REJECTED','CANCELLED') AND start_date<=? AND end_date>=?)","Leave overlaps another request.",user.employeeId(),id==null?-1:id,end,start);
            if(id==null)execute(c,"INSERT INTO leave_requests(employee_id,leave_type_id,start_date,end_date,days_requested,reason,status) VALUES(?,?,?,?,?,?,'PENDING_SUPERVISOR')",user.employeeId(),typeId,start,end,days,reason);
            else execute(c,"UPDATE leave_requests SET leave_type_id=?,start_date=?,end_date=?,days_requested=?,reason=?,updated_at=SYSDATETIME() WHERE leave_request_id=?",typeId,start,end,days,reason,id);
            audit(c,user.id(),id==null?"CREATE":"UPDATE","LEAVE_REQUEST",id,"Leave request saved");return null;
        });
    }
    public void cancelLeave(UserSession user,int id)throws SQLException{
        transaction(c->{int n=execute(c,"UPDATE leave_requests SET status='CANCELLED',updated_at=SYSDATETIME() WHERE leave_request_id=? AND employee_id=? AND status IN ('PENDING_SUPERVISOR','PENDING_HR')",id,user.employeeId());
            Validation.require(n==1,"Only your pending leave can be cancelled.");audit(c,user.id(),"CANCEL","LEAVE_REQUEST",id,"Pending request cancelled");return null;});
    }
    public void decideLeave(UserSession actor,int requestId,String decision,String reason)throws SQLException{
        Validation.require(actor.hasRole("DEPARTMENT_SUPERVISOR","HR_MANAGER"),"Not authorized to approve leave.");
        Validation.require(java.util.Set.of("APPROVE","REJECT").contains(decision),"Invalid decision.");Validation.text(reason,"Decision reason",500);
        transaction(c->{
            String current;int employeeId,typeId,days,year;boolean needsHr;
            String lookup="SELECT l.status,l.employee_id,l.leave_type_id,l.days_requested,t.requires_hr_approval,YEAR(l.start_date) leave_year FROM leave_requests l WITH (UPDLOCK,HOLDLOCK) JOIN leave_types t ON t.leave_type_id=l.leave_type_id JOIN employees e ON e.employee_id=l.employee_id WHERE l.leave_request_id=?"+(actor.hasRole("DEPARTMENT_SUPERVISOR")?" AND e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"");
            try(PreparedStatement p=c.prepareStatement(lookup)){p.setInt(1,requestId);if(actor.hasRole("DEPARTMENT_SUPERVISOR"))p.setInt(2,actor.employeeId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Leave request not found or outside your department");current=r.getString(1);employeeId=r.getInt(2);typeId=r.getInt(3);days=r.getInt(4);needsHr=r.getBoolean(5);year=r.getInt(6);}}
            Validation.require(!java.util.Objects.equals(actor.employeeId(),employeeId),"You cannot approve your own leave.");
            activeEmployee(c,employeeId);
            String next;
            if(actor.hasRole("DEPARTMENT_SUPERVISOR") && "PENDING_SUPERVISOR".equals(current)) next="APPROVE".equals(decision)&&needsHr?"PENDING_HR":"APPROVE".equals(decision)?"APPROVED":"REJECTED";
            else if(actor.hasRole("HR_MANAGER") && "PENDING_HR".equals(current)) next="APPROVE".equals(decision)?"APPROVED":"REJECTED";
            else throw new SQLException("This request is not awaiting your role");
            if("REJECTED".equals(next)&&reason.isBlank())throw new SQLException("A rejection reason is required");
            try(PreparedStatement p=c.prepareStatement("UPDATE leave_requests SET status=?,updated_at=SYSDATETIME() WHERE leave_request_id=?")){p.setString(1,next);p.setInt(2,requestId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO leave_approval_history(leave_request_id,reviewer_user_id,decision,reason) VALUES(?,?,?,?)")){p.setInt(1,requestId);p.setInt(2,actor.id());p.setString(3,next);p.setString(4,reason);p.executeUpdate();}
            if("APPROVED".equals(next)){
                check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM payroll_runs pr JOIN leave_requests l ON l.start_date<=pr.period_end AND l.end_date>=pr.period_start WHERE l.leave_request_id=? AND pr.status='CLOSED')","This leave overlaps finalized payroll; approval requires an authorized historical adjustment process.",requestId);
                execute(c,"INSERT INTO attendance_adjustments(attendance_id,changed_by,reason,old_clock_in,old_clock_out) SELECT a.attendance_id,?,'Approved leave supersedes automatic absence',a.clock_in,a.clock_out FROM attendance_records a JOIN leave_requests l ON l.employee_id=a.employee_id AND a.work_date BETWEEN l.start_date AND l.end_date WHERE l.leave_request_id=? AND a.status='ABSENT'",actor.id(),requestId);
                execute(c,"UPDATE a SET status='VOID',exception_type='APPROVED_LEAVE' FROM attendance_records a JOIN leave_requests l ON l.employee_id=a.employee_id AND a.work_date BETWEEN l.start_date AND l.end_date WHERE l.leave_request_id=? AND a.status='ABSENT'",requestId);
                try(PreparedStatement p=c.prepareStatement("UPDATE leave_balances SET used_days=used_days+? WHERE employee_id=? AND leave_type_id=? AND year=? AND entitled_days-used_days>=?")){p.setInt(1,days);p.setInt(2,employeeId);p.setInt(3,typeId);p.setInt(4,year);p.setInt(5,days);if(p.executeUpdate()!=1)throw new SQLException("Leave balance changed; approval rolled back");}}
            notifyEmployee(c,employeeId,"Leave request updated","Request #"+requestId+" is now "+next,"LEAVE");audit(c,actor.id(),"DECIDE","LEAVE_REQUEST",requestId,next);return null;
        });
    }

    public List<Map<String,Object>> reviews(UserSession user)throws SQLException{
        String where=user.hasRole("EMPLOYEE")?" WHERE r.employee_id=? AND r.status='FINALIZED'":user.hasRole("DEPARTMENT_SUPERVISOR")?" WHERE e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT TOP 100 r.review_id,e.employee_id,e.full_name,p.period_name,r.review_date,r.overall_rating,r.comments,r.status,rv.full_name reviewer_name FROM performance_reviews r JOIN employees e ON e.employee_id=r.employee_id JOIN review_periods p ON p.review_period_id=r.review_period_id JOIN user_accounts u ON u.user_id=r.reviewer_user_id LEFT JOIN employees rv ON rv.employee_id=u.employee_id"+where+" ORDER BY r.review_date DESC",p->{if(!where.isEmpty())p.setInt(1,user.employeeId());});
    }
    public List<Map<String,Object>> goals(UserSession user)throws SQLException{
        String where=user.hasRole("EMPLOYEE")?" WHERE g.employee_id=?":user.hasRole("DEPARTMENT_SUPERVISOR")?" WHERE e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT TOP 100 g.goal_id,e.full_name,g.title,g.target_value,g.current_value,g.due_date,g.weight_percent,g.status FROM employee_goals g JOIN employees e ON e.employee_id=g.employee_id"+where+" ORDER BY g.due_date",p->{if(!where.isEmpty())p.setObject(1,user.employeeId());});
    }
    public List<Map<String,Object>> reviewPeriods()throws SQLException{return query("SELECT review_period_id,period_name FROM review_periods WHERE status='OPEN' ORDER BY start_date DESC",p->{});}
    public void saveReview(UserSession actor,int employeeId,int periodId,BigDecimal rating,String comments)throws SQLException{
        Validation.require(actor.hasRole("OPERATIONS_MANAGER"),"Only the Operations Manager can record reviews.");Validation.rating(rating);Validation.text(comments,"Feedback",1000);
        transaction(c->{activeEmployee(c,employeeId);check(c,"SELECT 1 FROM review_periods WHERE review_period_id=? AND status='OPEN' AND start_date<=CAST(GETDATE() AS DATE)","Review period is closed, future or missing.",periodId);
            execute(c,"INSERT INTO performance_reviews(employee_id,review_period_id,reviewer_user_id,review_date,overall_rating,comments,status) VALUES(?,?,?,CAST(GETDATE() AS DATE),?,?,'DRAFT')",employeeId,periodId,actor.id(),rating,comments);
            audit(c,actor.id(),"CREATE","PERFORMANCE_REVIEW",null,"Draft review for employee "+employeeId);return null;});
    }
    public void saveGoal(UserSession actor,int employeeId,String title,BigDecimal target,LocalDate due,BigDecimal weight)throws SQLException{
        Validation.require(actor.hasRole("OPERATIONS_MANAGER"),"Only the Operations Manager can set goals.");Validation.text(title,"Goal title",180);Validation.decimal(target,"Target",new BigDecimal("0.01"),new BigDecimal("9999999999.99"));Validation.decimal(weight,"Weight",BigDecimal.ZERO,new BigDecimal("100"));Validation.require(!due.isBefore(LocalDate.now()),"Goal due date cannot be in the past.");
        transaction(c->{activeEmployee(c,employeeId);execute(c,"INSERT INTO employee_goals(employee_id,title,target_value,current_value,due_date,weight_percent,status,created_by) VALUES(?,?,?,0,?,?,'ACTIVE',?)",employeeId,title,target,due,weight,actor.id());audit(c,actor.id(),"CREATE","GOAL",null,title);return null;});
    }
    public void progressGoal(UserSession actor,int goalId,BigDecimal value,String note)throws SQLException{
        Validation.require(actor.hasRole("OPERATIONS_MANAGER"),"Only the Operations Manager can update goals.");Validation.text(note,"Progress note",500);Validation.decimal(value,"Progress",BigDecimal.ZERO,new BigDecimal("9999999999.99"));
        transaction(c->{int n=execute(c,"UPDATE g SET current_value=?,status=CASE WHEN ?=target_value THEN 'COMPLETED' ELSE 'ACTIVE' END,updated_at=SYSDATETIME() FROM employee_goals g JOIN employees e ON e.employee_id=g.employee_id WHERE goal_id=? AND g.status='ACTIVE' AND e.status='ACTIVE' AND ?>=current_value AND ?<=target_value",value,value,goalId,value,value);Validation.require(n==1,"Goal must be active, and progress must be between its current value and target.");execute(c,"INSERT INTO goal_progress_history(goal_id,recorded_by,progress_value,note) VALUES(?,?,?,?)",goalId,actor.id(),value,note);audit(c,actor.id(),"PROGRESS","GOAL",goalId,note);return null;});
    }
    public List<Map<String,Object>> payroll(UserSession user)throws SQLException{
        requireRole(user,"EMPLOYEE","PAYROLL_SPECIALIST","OPERATIONS_MANAGER");
        String where=user.hasRole("EMPLOYEE")?" WHERE e.employee_id=? AND r.status='CLOSED'":"";
        return query("SELECT TOP 100 pr.payroll_record_id,e.employee_code,e.full_name,r.period_label,pr.base_salary,pr.gross_pay,pr.employee_epf,pr.employer_epf,pr.etf,pr.total_deductions,pr.net_pay,r.status FROM payroll_records pr JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id JOIN employees e ON e.employee_id=pr.employee_id"+where+" ORDER BY r.period_start DESC,e.full_name",p->{if(user.hasRole("EMPLOYEE"))p.setInt(1,user.employeeId());});
    }
    public Map<String,Object> payslip(UserSession user,long recordId)throws SQLException{
        requireRole(user,"EMPLOYEE","PAYROLL_SPECIALIST","OPERATIONS_MANAGER");
        String scope=user.hasRole("EMPLOYEE")?" AND e.employee_id=?":"";
        List<Map<String,Object>> rows=query("SELECT pr.payroll_record_id,e.employee_code,e.full_name,r.period_label,r.period_start,r.period_end,pr.base_salary,pr.gross_pay,pr.employee_epf,pr.employer_epf,pr.etf,pr.total_deductions,pr.net_pay FROM payroll_records pr JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id JOIN employees e ON e.employee_id=pr.employee_id WHERE pr.payroll_record_id=? AND r.status='CLOSED'"+scope,p->{p.setLong(1,recordId);if(user.hasRole("EMPLOYEE"))p.setInt(2,user.employeeId());});
        if(rows.isEmpty())throw new SQLException("Payslip not found or access denied");return rows.get(0);
    }
    public List<Map<String,Object>> payrollRuns()throws SQLException{return query("SELECT TOP 24 payroll_run_id,period_label,period_start,period_end,status,created_at,closed_at FROM payroll_runs ORDER BY period_start DESC",p->{});}
    public void processPayroll(UserSession actor,String label,LocalDate start,LocalDate end)throws SQLException{
        requireRole(actor,"PAYROLL_SPECIALIST");
        Validation.text(label,"Period label",80);Validation.range(start,end);detectAbsences();
        try(Connection c=Database.getConnection();CallableStatement p=c.prepareCall("{call sp_ProcessPayroll(?,?,?,?)}")){p.setString(1,label);p.setDate(2,Date.valueOf(start));p.setDate(3,Date.valueOf(end));p.setInt(4,actor.id());p.execute();}
    }

    public List<Map<String,Object>> users()throws SQLException{return query("SELECT u.user_id,u.username,u.email,u.status,u.last_login_at,u.failed_attempts,r.role_code,COALESCE(e.full_name,'System Account') full_name FROM user_accounts u JOIN roles r ON r.role_id=u.role_id LEFT JOIN employees e ON e.employee_id=u.employee_id ORDER BY u.username",p->{});}
    public List<Map<String,Object>> roles()throws SQLException{return query("SELECT role_id,role_code,role_name FROM roles ORDER BY role_name",p->{});}
    public void createUser(UserSession actor,int employeeId,String username,String email,String password,int roleId)throws SQLException{
        Validation.require(actor.hasRole("IT_SYSTEM_ADMINISTRATOR"),"Not authorized.");Validation.text(username,"Username",60);Validation.require(username.matches("[A-Za-z0-9_.-]{3,60}"),"Username must contain 3–60 letters, digits, dots, hyphens or underscores.");Validation.email(email);Validation.password(password);
        transaction(c->{activeEmployee(c,employeeId);execute(c,"INSERT INTO user_accounts(employee_id,role_id,username,email,password_hash,status,must_change_password,temporary_password_expires_at) VALUES(?,?,?,?,?,'ACTIVE',1,DATEADD(hour,24,SYSDATETIME()))",employeeId,roleId,username,email,PasswordUtil.hash(password));audit(c,actor.id(),"CREATE","USER_ACCOUNT",null,"Account "+username+" created");return null;});
    }
    public void accountAction(UserSession actor,int userId,String action,String temporaryPassword)throws SQLException{
        Validation.require(actor.hasRole("IT_SYSTEM_ADMINISTRATOR"),"Not authorized.");Validation.require(actor.id()!=userId,"Use another administrator for sensitive changes to your account.");
        if(action.equals("reset"))Validation.password(temporaryPassword);
        transaction(c->{
            if(java.util.Set.of("activate","unlock","reset").contains(action))check(c,"SELECT 1 FROM user_accounts u LEFT JOIN employees e ON e.employee_id=u.employee_id WHERE user_id=? AND (e.employee_id IS NULL OR e.status='ACTIVE')","Linked employee is inactive.",userId);
            String sql=switch(action){case "activate"->"UPDATE user_accounts SET status='ACTIVE',failed_attempts=0,security_version=security_version+1 WHERE user_id=?";case "deactivate"->"UPDATE user_accounts SET status='INACTIVE',security_version=security_version+1 WHERE user_id=?";case "unlock"->"UPDATE user_accounts SET status='ACTIVE',failed_attempts=0,security_version=security_version+1 WHERE user_id=? AND status='LOCKED'";case "reset"->"UPDATE user_accounts SET status=CASE WHEN status='LOCKED' THEN 'ACTIVE' ELSE status END,password_hash=?,must_change_password=1,temporary_password_expires_at=DATEADD(hour,24,SYSDATETIME()),security_version=security_version+1,failed_attempts=0 WHERE user_id=? AND status<>'INACTIVE'";default->throw new IllegalArgumentException("Unsupported account action.");};
            int n=action.equals("reset")?execute(c,sql,PasswordUtil.hash(temporaryPassword),userId):execute(c,sql,userId);Validation.require(n==1,"Account is missing or its status does not allow this action.");audit(c,actor.id(),action.toUpperCase(),"USER_ACCOUNT",userId,"Account action; sessions revoked");return null;});
    }
    public void changePassword(UserSession user,String currentPassword,String newPassword)throws SQLException{
        Validation.password(newPassword);Validation.require(!newPassword.equals(currentPassword),"Choose a different password.");
        transaction(c->{String stored;try(PreparedStatement p=c.prepareStatement("SELECT password_hash FROM user_accounts WHERE user_id=?")){p.setInt(1,user.id());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Account not found");stored=r.getString(1);}}Validation.require(PasswordUtil.verify(currentPassword,stored),"Current password is incorrect.");try(PreparedStatement p=c.prepareStatement("UPDATE user_accounts SET password_hash=?,must_change_password=0,temporary_password_expires_at=NULL,security_version=security_version+1 WHERE user_id=?")){p.setString(1,PasswordUtil.hash(newPassword));p.setInt(2,user.id());p.executeUpdate();}audit(c,user.id(),"CHANGE_PASSWORD","USER_ACCOUNT",user.id(),"Password changed");return null;});
    }

    public List<Map<String,Object>> notifications(UserSession user)throws SQLException{return query("SELECT TOP 100 notification_id,title,message,notification_type,is_read,created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC",p->p.setInt(1,user.id()));}
    public void notificationAction(UserSession user,String action,Integer id)throws SQLException{
        String sql=switch(action){case"read"->"UPDATE notifications SET is_read=1 WHERE notification_id=? AND user_id=?";case"all"->"UPDATE notifications SET is_read=1 WHERE user_id=?";case"delete"->"DELETE FROM notifications WHERE notification_id=? AND user_id=?";default->throw new SQLException("Unsupported notification action");};
        update(sql,p->{if("all".equals(action))p.setInt(1,user.id());else{p.setInt(1,id);p.setInt(2,user.id());}});
    }
    public List<Map<String,Object>> auditLogs()throws SQLException{return query("SELECT a.audit_id,u.username,a.action_name,a.entity_type,a.entity_id,a.details,a.created_at FROM audit_logs a JOIN user_accounts u ON u.user_id=a.actor_user_id ORDER BY a.created_at DESC",p->{});}

    private void notifyEmployee(Connection c,int employeeId,String title,String message,String type)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO notifications(user_id,title,message,notification_type) SELECT user_id,?,?,? FROM user_accounts WHERE employee_id=?")){p.setString(1,title);p.setString(2,message);p.setString(3,type);p.setInt(4,employeeId);p.executeUpdate();}}
    private void audit(Connection c,int actor,String action,String entity,Integer entityId,String details)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(actor_user_id,action_name,entity_type,entity_id,details) VALUES(?,?,?,?,?)")){p.setInt(1,actor);p.setString(2,action);p.setString(3,entity);if(entityId==null)p.setNull(4,Types.INTEGER);else p.setInt(4,entityId);p.setString(5,details);p.executeUpdate();}}
    private int update(String sql,Binder binder)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){binder.bind(p);return p.executeUpdate();}}
    private List<Map<String,Object>> query(String sql,Binder binder)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){binder.bind(p);try(ResultSet r=p.executeQuery()){List<Map<String,Object>> list=new ArrayList<>();ResultSetMetaData m=r.getMetaData();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=m.getColumnCount();i++)row.put(m.getColumnLabel(i),r.getObject(i));list.add(row);}return list;}}}
    @FunctionalInterface private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work)throws SQLException{try(Connection c=Database.getConnection()){c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);c.setAutoCommit(false);try{T value=work.run(c);c.commit();return value;}catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;if(e instanceof RuntimeException r)throw r;throw new SQLException(e);}}}
    private int execute(Connection c,String sql,Object... args)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql)){bind(p,args);return p.executeUpdate();}
    }
    private void bind(PreparedStatement p,Object...args)throws SQLException{
        for(int i=0;i<args.length;i++){Object a=args[i];if(a instanceof LocalDate d)p.setDate(i+1,Date.valueOf(d));else if(a instanceof LocalDateTime t)p.setTimestamp(i+1,Timestamp.valueOf(t));else p.setObject(i+1,a);}
    }
    private void check(Connection c,String sql,String message,Object...args)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql)){bind(p,args);try(ResultSet r=p.executeQuery()){Validation.require(r.next(),message);}}
    }
    private void activeEmployee(Connection c,int id)throws SQLException{
        check(c,"SELECT 1 FROM employees WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND status='ACTIVE'","Employee is inactive or missing.",id);
    }
    public boolean sessionValid(UserSession u)throws SQLException{
        return !query("SELECT 1 valid FROM user_accounts u LEFT JOIN employees e ON e.employee_id=u.employee_id WHERE user_id=? AND u.status='ACTIVE' AND (e.employee_id IS NULL OR e.status='ACTIVE') AND security_version=? AND (temporary_password_expires_at IS NULL OR temporary_password_expires_at>SYSDATETIME())",p->{p.setInt(1,u.id());p.setInt(2,u.securityVersion());}).isEmpty();
    }
    public List<Map<String,Object>> filteredEmployees(String q,String department,String designation,String status)throws SQLException{
        return query("SELECT e.employee_id,e.department_id,e.designation_id,e.employee_code,e.full_name,e.nic,e.email,e.phone,d.department_name,g.designation_name,e.employment_date,e.status FROM employees e JOIN departments d ON d.department_id=e.department_id JOIN designations g ON g.designation_id=e.designation_id WHERE (?='' OR e.full_name LIKE ? OR e.employee_code LIKE ?) AND (?='' OR d.department_name=?) AND (?='' OR g.designation_name=?) AND (?='' OR e.status=?) ORDER BY e.full_name",p->bind(p,q,"%"+q+"%","%"+q+"%",department,department,designation,designation,status,status));
    }
    public void recordAttendance(UserSession actor,Integer id,int employeeId,LocalDate day,LocalDateTime in,LocalDateTime out,String reason)throws SQLException{
        Validation.require(actor.hasRole("ATTENDANCE_COORDINATOR"),"Only the attendance coordinator can correct attendance.");
        Validation.attendance(day,in,out,LocalDateTime.now());Validation.text(reason,"Correction reason",300);
        transaction(c->{activeEmployee(c,employeeId);
            Integer shiftId=null;int breakMinutes=0;LocalTime startTime=null,endTime=null;
            try(PreparedStatement p=c.prepareStatement("SELECT s.shift_id,s.start_time,s.end_time,s.break_minutes FROM shift_assignments sa JOIN shifts s ON s.shift_id=sa.shift_id WHERE sa.employee_id=? AND ? BETWEEN sa.start_date AND ISNULL(sa.end_date,'9999-12-31')")){
                bind(p,employeeId,day);try(ResultSet r=p.executeQuery()){if(r.next()){shiftId=r.getInt(1);startTime=r.getTime(2).toLocalTime();endTime=r.getTime(3).toLocalTime();breakMinutes=r.getInt(4);}}}
            check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM leave_requests WHERE employee_id=? AND status='APPROVED' AND ? BETWEEN start_date AND end_date)","Employee is on approved leave.",employeeId,day);
            check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM payroll_runs WHERE status='CLOSED' AND ? BETWEEN period_start AND period_end)","Attendance is locked by finalized payroll.",day);
            String exception=null;
            if(shiftId!=null){LocalDateTime shiftStart=day.atTime(startTime),shiftEnd=day.atTime(endTime);if(!endTime.isAfter(startTime))shiftEnd=shiftEnd.plusDays(1);
            Validation.require(!in.isBefore(shiftStart.minusHours(2)) && in.isBefore(shiftEnd),"Clock-in must be within two hours before the shift and before its end.");
            exception=in.isAfter(shiftStart.plusMinutes(10))?"LATE":null;}
            else Validation.require(in.toLocalDate().equals(day),"Clock-in must match the working date.");
            BigDecimal hours=out==null?null:BigDecimal.valueOf(Math.max(0,ChronoUnit.MINUTES.between(in,out)-breakMinutes)).divide(BigDecimal.valueOf(60),2,java.math.RoundingMode.HALF_UP);
            if(id==null){execute(c,"INSERT INTO attendance_records(employee_id,work_date,shift_id,clock_in,clock_out,hours_worked,exception_type,status) VALUES(?,?,?,?,?,?,?,?)",employeeId,day,shiftId,in,out,hours,exception,out==null?"OPEN":"CORRECTED");}
            else{
                check(c,"SELECT 1 FROM attendance_records WITH (UPDLOCK,HOLDLOCK) WHERE attendance_id=? AND employee_id=? AND work_date=?","Attendance employee and date cannot be reassigned.",id,employeeId,day);
                execute(c,"INSERT INTO attendance_adjustments(attendance_id,changed_by,reason,old_clock_in,old_clock_out,new_clock_in,new_clock_out) SELECT attendance_id,?,?,clock_in,clock_out,?,? FROM attendance_records WHERE attendance_id=?",actor.id(),reason,in,out,id);
                execute(c,"UPDATE attendance_records SET shift_id=?,clock_in=?,clock_out=?,hours_worked=?,exception_type=?,status=? WHERE attendance_id=?",shiftId,in,out,hours,exception,out==null?"OPEN":"CORRECTED",id);
            }
            audit(c,actor.id(),id==null?"CREATE":"CORRECT","ATTENDANCE",id,reason);return null;});
    }
    public void voidAttendance(UserSession actor,int id,String reason)throws SQLException{
        Validation.require(actor.hasRole("ATTENDANCE_COORDINATOR"),"Not authorized.");Validation.text(reason,"Reason",300);
        transaction(c->{check(c,"SELECT 1 FROM attendance_records a WITH (UPDLOCK,HOLDLOCK) WHERE attendance_id=? AND status<>'VOID' AND NOT EXISTS(SELECT 1 FROM payroll_runs r WHERE r.status='CLOSED' AND a.work_date BETWEEN r.period_start AND r.period_end)","Record is missing, void or locked by payroll.",id);
            execute(c,"INSERT INTO attendance_adjustments(attendance_id,changed_by,reason,old_clock_in,old_clock_out) SELECT attendance_id,?,?,clock_in,clock_out FROM attendance_records WHERE attendance_id=?",actor.id(),reason,id);
            execute(c,"UPDATE attendance_records SET status='VOID',exception_type='VOID',clock_in=NULL,clock_out=NULL,hours_worked=NULL WHERE attendance_id=?",id);
            audit(c,actor.id(),"VOID","ATTENDANCE",id,reason);return null;});
    }
    /** Idempotent scheduled detection. A shift is absent only after its end (including overnight). */
    public void detectAbsences()throws SQLException{
        transaction(c->{execute(c,";WITH dates AS (SELECT MIN(start_date) day FROM shift_assignments UNION ALL SELECT DATEADD(day,1,day) FROM dates WHERE day<CAST(GETDATE() AS DATE)) INSERT INTO attendance_records(employee_id,work_date,shift_id,status,exception_type,hours_worked) SELECT e.employee_id,d.day,s.shift_id,'ABSENT','ABSENT',0 FROM dates d JOIN shift_assignments sa ON d.day BETWEEN sa.start_date AND ISNULL(sa.end_date,'9999-12-31') JOIN employees e ON e.employee_id=sa.employee_id AND e.status='ACTIVE' AND e.employment_date<=d.day JOIN shifts s ON s.shift_id=sa.shift_id WHERE DATEADD(day,CASE WHEN s.end_time<=s.start_time THEN 1 ELSE 0 END,DATEADD(second,DATEDIFF(second,CAST('00:00' AS TIME),s.end_time),CAST(d.day AS DATETIME2)))<SYSDATETIME() AND NOT EXISTS(SELECT 1 FROM attendance_records a WITH (UPDLOCK,HOLDLOCK) WHERE a.employee_id=e.employee_id AND a.work_date=d.day) AND NOT EXISTS(SELECT 1 FROM leave_requests l WHERE l.employee_id=e.employee_id AND l.status='APPROVED' AND d.day BETWEEN l.start_date AND l.end_date) AND NOT EXISTS(SELECT 1 FROM payroll_runs pr WHERE pr.status='CLOSED' AND d.day BETWEEN pr.period_start AND pr.period_end) OPTION(MAXRECURSION 0)");return null;});
    }
    public List<Map<String,Object>> attendanceSummary(UserSession u,LocalDate from,LocalDate to)throws SQLException{
        requireRole(u,"EMPLOYEE","ATTENDANCE_COORDINATOR","DEPARTMENT_SUPERVISOR","PAYROLL_SPECIALIST");
        Validation.range(from,to);detectAbsences();String scope=u.hasRole("EMPLOYEE")?" AND e.employee_id=?":u.hasRole("DEPARTMENT_SUPERVISOR")?" AND e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT e.employee_code,e.full_name,d.department_name,SUM(CASE WHEN a.clock_in IS NOT NULL AND ISNULL(a.exception_type,'')<>'LATE' THEN 1 ELSE 0 END) present_days,SUM(CASE WHEN a.exception_type='LATE' THEN 1 ELSE 0 END) late_days,SUM(CASE WHEN a.status='ABSENT' THEN 1 ELSE 0 END) absent_days,COALESCE(SUM(a.hours_worked),0) total_hours FROM employees e JOIN departments d ON d.department_id=e.department_id LEFT JOIN attendance_records a ON a.employee_id=e.employee_id AND a.work_date BETWEEN ? AND ? AND a.status<>'VOID' WHERE 1=1"+scope+" GROUP BY e.employee_code,e.full_name,d.department_name ORDER BY d.department_name,e.full_name",p->{bind(p,from,to);if(!scope.isEmpty())p.setObject(3,u.employeeId());});
    }
    public List<Map<String,Object>> performanceSummary()throws SQLException{
        return query("SELECT d.department_name,COUNT(e.employee_id) employees,AVG(rv.rating) average_rating,COALESCE(SUM(g.completed),0) goals_completed,COALESCE(SUM(g.pending),0) goals_pending FROM departments d LEFT JOIN employees e ON e.department_id=d.department_id AND e.status='ACTIVE' OUTER APPLY(SELECT AVG(overall_rating) rating FROM performance_reviews WHERE employee_id=e.employee_id AND status='FINALIZED') rv OUTER APPLY(SELECT SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END) completed,SUM(CASE WHEN status='ACTIVE' THEN 1 ELSE 0 END) pending FROM employee_goals WHERE employee_id=e.employee_id) g GROUP BY d.department_name ORDER BY d.department_name",p->{});
    }
    public List<Map<String,Object>> performanceHistory(UserSession u,int employeeId)throws SQLException{
        requireOwnEmployee(u,employeeId);
        String scope=u.hasRole("EMPLOYEE")?" AND e.employee_id=? AND r.status='FINALIZED'":u.hasRole("DEPARTMENT_SUPERVISOR")?" AND e.department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT r.review_id,e.employee_code,e.full_name,p.period_name,r.review_date,r.overall_rating,r.comments,r.status FROM performance_reviews r JOIN employees e ON e.employee_id=r.employee_id JOIN review_periods p ON p.review_period_id=r.review_period_id WHERE (?=0 OR e.employee_id=?)"+scope+" ORDER BY e.full_name,r.review_date DESC",p->{bind(p,employeeId,employeeId);if(!scope.isEmpty())p.setObject(3,u.employeeId());});
    }
    public void editReview(UserSession actor,int id,BigDecimal rating,String comments,String action)throws SQLException{
        Validation.require(actor.hasRole("OPERATIONS_MANAGER"),"Only the Operations Manager can manage reviews.");
        Validation.require(java.util.Set.of("update","delete","finalize").contains(action),"Invalid review action.");
        if(!action.equals("delete")){Validation.rating(rating);Validation.text(comments,"Feedback",1000);}
        transaction(c->{check(c,"SELECT 1 FROM performance_reviews r WITH (UPDLOCK,HOLDLOCK) JOIN review_periods p ON p.review_period_id=r.review_period_id WHERE review_id=? AND r.status='DRAFT' AND p.status='OPEN'","Only a draft review in an open period can be changed.",id);
            if(action.equals("delete")){execute(c,"DELETE FROM performance_ratings WHERE review_id=?",id);execute(c,"DELETE FROM performance_reviews WHERE review_id=?",id);}
            else execute(c,"UPDATE performance_reviews SET overall_rating=?,comments=?,status=? WHERE review_id=?",rating,comments,action.equals("finalize")?"FINALIZED":"DRAFT",id);
            audit(c,actor.id(),action.toUpperCase(),"PERFORMANCE_REVIEW",id,"Draft review lifecycle");return null;});
    }
    public List<Map<String,Object>> filteredUsers(String search,String role,String status)throws SQLException{
        return query("SELECT u.user_id,u.username,u.email,u.status,u.last_login_at,u.failed_attempts,u.role_id,r.role_code,COALESCE(e.full_name,'System Account') full_name FROM user_accounts u JOIN roles r ON r.role_id=u.role_id LEFT JOIN employees e ON e.employee_id=u.employee_id WHERE (?='' OR u.username LIKE ? OR e.full_name LIKE ?) AND (?='' OR r.role_code=?) AND (?='' OR u.status=?) ORDER BY u.username",p->bind(p,search,"%"+search+"%","%"+search+"%",role,role,status,status));
    }
    public void updateUser(UserSession actor,int id,int roleId,String email)throws SQLException{
        Validation.require(actor.hasRole("IT_SYSTEM_ADMINISTRATOR"),"Not authorized.");Validation.require(actor.id()!=id,"Use another administrator to change your role or account details.");Validation.email(email);
        transaction(c->{int n=execute(c,"UPDATE user_accounts SET role_id=?,email=?,security_version=security_version+1 WHERE user_id=?",roleId,email,id);Validation.require(n==1,"Account not found.");audit(c,actor.id(),"UPDATE","USER_ACCOUNT",id,"Role/contact updated; sessions revoked");return null;});
    }
    public List<Map<String,Object>> payrollReport(UserSession u,LocalDate from,LocalDate to)throws SQLException{
        requireRole(u,"EMPLOYEE","PAYROLL_SPECIALIST","OPERATIONS_MANAGER");
        Validation.range(from,to);String scope=u.hasRole("EMPLOYEE")?" AND pr.employee_id=? AND r.status='CLOSED'":"";
        return query("SELECT pr.payroll_record_id,e.employee_code,e.full_name,r.period_label,r.period_start,r.period_end,r.status,pr.base_salary,pr.gross_pay,pr.employee_epf,pr.employer_epf,pr.etf,pr.total_deductions,pr.net_pay FROM payroll_records pr JOIN employees e ON e.employee_id=pr.employee_id JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id WHERE r.status<>'CANCELLED' AND r.period_start>=? AND r.period_end<=?"+scope+" ORDER BY r.period_start,e.full_name",p->{bind(p,from,to);if(!scope.isEmpty())p.setObject(3,u.employeeId());});
    }
    public void createPayrollDraft(UserSession actor,String label,LocalDate start,LocalDate end)throws SQLException{
        Validation.text(label,"Period label",80);Validation.range(start,end);Validation.require(actor.hasRole("PAYROLL_SPECIALIST"),"Not authorized.");detectAbsences();
        try(Connection c=Database.getConnection();CallableStatement p=c.prepareCall("{call sp_ProcessPayroll(?,?,?,?,?)}")){bind(p,label,start,end,actor.id(),false);p.execute();}
    }
    public void editPayroll(UserSession actor,int id,BigDecimal base,BigDecimal allowances,BigDecimal deductions)throws SQLException{
        editPayroll(actor,id,base,allowances,deductions,null);
    }
    public void editPayroll(UserSession actor,int id,BigDecimal base,BigDecimal allowances,BigDecimal deductions,BigDecimal expectedBase)throws SQLException{
        Validation.require(actor.hasRole("PAYROLL_SPECIALIST"),"Not authorized.");
        for(BigDecimal v:new BigDecimal[]{base,allowances,deductions})Validation.decimal(v,"Payroll amount",BigDecimal.ZERO,new BigDecimal("99999999999.99"));
        transaction(c->{check(c,"SELECT 1 FROM payroll_records pr JOIN payroll_runs r WITH (UPDLOCK,HOLDLOCK) ON r.payroll_run_id=pr.payroll_run_id WHERE pr.payroll_record_id=? AND r.status='DRAFT'","Only draft payroll can be updated.",id);
            BigDecimal old=salaryBase(c,id,true);
            Validation.require(expectedBase==null || old.compareTo(expectedBase)==0,"Salary changed since confirmation. Review and confirm again.");
            String before;
            try(PreparedStatement p=c.prepareStatement("SELECT CONCAT('base=',base_salary,', gross=',gross_pay,', deductions=',total_deductions,', net=',net_pay) FROM payroll_records WHERE payroll_record_id=?")){bind(p,id);try(ResultSet r=p.executeQuery()){r.next();before=r.getString(1);}}
            BigDecimal gross=base.add(allowances);
            int n=execute(c,"UPDATE pr SET base_salary=?,gross_pay=?,employee_epf=ROUND(?*ee.rate_percent/100,2),employer_epf=ROUND(?*er.rate_percent/100,2),etf=ROUND(?*et.rate_percent/100,2),total_deductions=?,net_pay=?-ROUND(?*ee.rate_percent/100,2)-? FROM payroll_records pr JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id CROSS APPLY(SELECT TOP 1 rate_percent FROM statutory_rates WHERE rate_code='EMPLOYEE_EPF' AND effective_from<=r.period_end ORDER BY effective_from DESC) ee CROSS APPLY(SELECT TOP 1 rate_percent FROM statutory_rates WHERE rate_code='EMPLOYER_EPF' AND effective_from<=r.period_end ORDER BY effective_from DESC) er CROSS APPLY(SELECT TOP 1 rate_percent FROM statutory_rates WHERE rate_code='ETF' AND effective_from<=r.period_end ORDER BY effective_from DESC) et WHERE pr.payroll_record_id=? AND ?-ROUND(?*ee.rate_percent/100,2)-?>=0",base,gross,gross,gross,gross,deductions,gross,gross,deductions,id,gross,gross,deductions);
            Validation.require(n==1,"Missing statutory rates or deductions exceed gross pay.");
            execute(c,"UPDATE pc SET amount=CASE WHEN pc.component_name='Base Salary' THEN pr.base_salary ELSE pr.employee_epf END FROM payroll_components pc JOIN payroll_records pr ON pr.payroll_record_id=pc.payroll_record_id WHERE pr.payroll_record_id=? AND pc.component_name IN ('Base Salary','Employee EPF')",id);
            audit(c,actor.id(),"UPDATE_DRAFT","PAYROLL_RECORD",id,"Before: "+before+"; after base="+base+", allowances="+allowances+", deductions="+deductions);return null;});
    }
    public void payrollLifecycle(UserSession actor,int id,String action)throws SQLException{
        Validation.require(actor.hasRole("PAYROLL_SPECIALIST"),"Not authorized.");Validation.require(java.util.Set.of("finalize","cancel").contains(action),"Invalid payroll action.");if(action.equals("finalize"))detectAbsences();
        transaction(c->{check(c,"SELECT 1 FROM payroll_runs WITH (UPDLOCK,HOLDLOCK) WHERE payroll_run_id=? AND status='DRAFT'","Only a draft run can be finalized or cancelled.",id);
            if(action.equals("finalize")){
                check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM attendance_records a JOIN payroll_runs r ON a.work_date BETWEEN r.period_start AND r.period_end WHERE r.payroll_run_id=? AND a.status='OPEN')","Resolve open attendance before finalizing.",id);
                check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM payroll_records pr JOIN employees e ON e.employee_id=pr.employee_id WHERE pr.payroll_run_id=? AND e.status<>'ACTIVE')","Draft includes an inactive employee; cancel and regenerate.",id);
                execute(c,"UPDATE payroll_runs SET status='CLOSED',closed_at=SYSDATETIME() WHERE payroll_run_id=?",id);
                execute(c,"INSERT INTO notifications(user_id,title,message,notification_type) SELECT u.user_id,'Payslip available',r.period_label+' payroll has been finalized.','PAYROLL' FROM user_accounts u JOIN payroll_records pr ON pr.employee_id=u.employee_id JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id WHERE r.payroll_run_id=?",id);
            }else execute(c,"UPDATE payroll_runs SET status='CANCELLED' WHERE payroll_run_id=?",id);
            audit(c,actor.id(),action.toUpperCase(),"PAYROLL_RUN",id,"Draft lifecycle");return null;});
    }
    public List<Map<String,Object>> employeeOptions(UserSession u)throws SQLException{
        String where=u.hasRole("EMPLOYEE")?" WHERE employee_id=?":u.hasRole("DEPARTMENT_SUPERVISOR")?" WHERE department_id=(SELECT department_id FROM employees WHERE employee_id=?)":"";
        return query("SELECT employee_id,employee_code,full_name,status FROM employees"+where+" ORDER BY full_name",p->{if(!where.isEmpty())p.setObject(1,u.employeeId());});
    }
    public List<Map<String,Object>> shiftAssignments()throws SQLException{
        return query("SELECT sa.assignment_id,e.employee_code,e.full_name,s.shift_name,sa.start_date,sa.end_date FROM shift_assignments sa JOIN employees e ON e.employee_id=sa.employee_id JOIN shifts s ON s.shift_id=sa.shift_id ORDER BY sa.start_date DESC,e.full_name",p->{});
    }
    public void endShiftAssignment(UserSession actor,int id,LocalDate end)throws SQLException{
        Validation.require(actor.hasRole("ATTENDANCE_COORDINATOR"),"Not authorized.");Validation.require(!end.isBefore(LocalDate.now()),"End a current assignment today or later; past history is retained.");
        transaction(c->{int n=execute(c,"UPDATE shift_assignments SET end_date=? WHERE assignment_id=? AND start_date<=? AND (end_date IS NULL OR end_date>=?)",end,id,end,end);Validation.require(n==1,"Assignment is missing or this would extend/reverse its dates.");audit(c,actor.id(),"END_ASSIGNMENT","SHIFT",id,"Ends "+end);return null;});
    }
    public void saveSalaryStructure(UserSession actor,int employeeId,BigDecimal base,LocalDate effective)throws SQLException{
        saveSalaryStructure(actor,employeeId,base,effective,null);
    }
    public void saveSalaryStructure(UserSession actor,int employeeId,BigDecimal base,LocalDate effective,BigDecimal expectedBase)throws SQLException{
        Validation.require(actor.hasRole("PAYROLL_SPECIALIST"),"Not authorized.");Validation.decimal(base,"Base salary",BigDecimal.ZERO,new BigDecimal("99999999999.99"));
        Validation.require(effective.getDayOfMonth()==1,"Salary changes start on the first day of a month.");
        transaction(c->{activeEmployee(c,employeeId);
            BigDecimal old=salaryBase(c,employeeId,false);
            Validation.require(expectedBase==null || old.compareTo(expectedBase)==0,"Salary changed since confirmation. Review and confirm again.");
            check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM payroll_records pr JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id WHERE pr.employee_id=? AND r.status IN ('DRAFT','CLOSED') AND r.period_end>=?)","Salary is already used in a draft/finalized period. Cancel a draft first; finalized history cannot be changed.",employeeId,effective);
            check(c,"SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM salary_structures WHERE employee_id=? AND effective_from>=?)","A salary structure already starts on or after that date.",employeeId,effective);
            execute(c,"UPDATE salary_structures SET effective_to=DATEADD(day,-1,?) WHERE employee_id=? AND effective_from<? AND (effective_to IS NULL OR effective_to>=?)",effective,employeeId,effective,effective);
            execute(c,"INSERT INTO salary_structures(employee_id,base_salary,effective_from,status) VALUES(?,?,?,'ACTIVE')",employeeId,base,effective);
            audit(c,actor.id(),"SALARY_STRUCTURE","EMPLOYEE",employeeId,"Base salary "+old+" -> "+base+"; difference "+base.subtract(old)+"; effective "+effective);return null;});
    }
    private static void requireRole(UserSession user,String... roles){
        if(user==null || !user.hasRole(roles))throw new SecurityException("Not authorized.");
    }
    public static void requireOwnEmployee(UserSession user,int requested){
        if(user.hasRole("EMPLOYEE") && requested!=0 && !java.util.Objects.equals(user.employeeId(),requested))
            throw new SecurityException("You can only access your own employee information.");
    }
    public Map<String,Object> ownProfile(UserSession user)throws SQLException{
        Validation.require(user.employeeId()!=null,"No employee profile is linked to this account.");
        var rows=query("SELECT e.employee_id,e.employee_code,e.full_name,e.nic,e.date_of_birth,e.email,e.phone,e.address,d.department_name,g.designation_name,e.employment_date,e.status,CASE WHEN p.employee_id IS NULL THEN 0 ELSE 1 END has_photo FROM employees e JOIN departments d ON d.department_id=e.department_id JOIN designations g ON g.designation_id=e.designation_id LEFT JOIN employee_photos p ON p.employee_id=e.employee_id WHERE e.employee_id=?",p->p.setInt(1,user.employeeId()));
        Validation.require(!rows.isEmpty(),"Employee profile not found.");return rows.get(0);
    }
    public byte[] ownPhoto(UserSession user)throws SQLException{
        Validation.require(user.employeeId()!=null,"No employee profile is linked.");
        var rows=query("SELECT photo_data FROM employee_photos WHERE employee_id=?",p->p.setInt(1,user.employeeId()));
        return rows.isEmpty()?null:(byte[])rows.get(0).get("photo_data");
    }
    public void saveOwnPhoto(UserSession user,byte[] png)throws SQLException{
        Validation.require(user.employeeId()!=null,"No employee profile is linked.");
        Validation.require(png!=null && png.length>0 && png.length<=2*1024*1024,"Photo must be at most 2 MB.");
        transaction(c->{activeEmployee(c,user.employeeId());
            if(execute(c,"UPDATE employee_photos SET photo_data=?,updated_at=SYSDATETIME() WHERE employee_id=?",png,user.employeeId())==0)
                execute(c,"INSERT INTO employee_photos(employee_id,photo_data) VALUES(?,?)",user.employeeId(),png);
            audit(c,user.id(),"PHOTO","EMPLOYEE",user.employeeId(),"Profile picture updated");return null;});
    }
    public List<Map<String,Object>> tasks(UserSession user)throws SQLException{
        requireRole(user,"EMPLOYEE","OPERATIONS_MANAGER");
        String scope=user.hasRole("EMPLOYEE")?" AND t.employee_id=?":"";
        return query("SELECT t.*,e.employee_code,e.full_name FROM employee_tasks t JOIN employees e ON e.employee_id=t.employee_id WHERE t.status<>'DELETED'"+scope+" ORDER BY t.deadline,t.task_id",p->{if(!scope.isEmpty())p.setObject(1,user.employeeId());});
    }
    public List<Map<String,Object>> taskEmployeeOptions(UserSession user)throws SQLException{
        requireRole(user,"OPERATIONS_MANAGER");
        return query("SELECT e.employee_id,e.employee_code,e.full_name,e.status FROM employees e JOIN user_accounts u ON u.employee_id=e.employee_id JOIN roles r ON r.role_id=u.role_id WHERE e.status='ACTIVE' AND u.status='ACTIVE' AND r.role_code='EMPLOYEE' ORDER BY e.full_name",p->{});
    }
    public void assignTask(UserSession actor,int employeeId,String title,String details,LocalDate deadline,String notes)throws SQLException{
        requireRole(actor,"OPERATIONS_MANAGER");Validation.text(title,"Task title",180);Validation.text(details,"Task details",2000);
        Validation.require(notes!=null && notes.length()<=2000,"Instructions must be at most 2000 characters.");
        Validation.require(deadline!=null && !deadline.isBefore(LocalDate.now()),"Deadline cannot be in the past.");
        transaction(c->{activeEmployee(c,employeeId);
            check(c,"SELECT 1 FROM user_accounts u JOIN roles r ON r.role_id=u.role_id WHERE employee_id=? AND u.status='ACTIVE' AND r.role_code='EMPLOYEE'","Select an employee with an active employee account.",employeeId);
            execute(c,"INSERT INTO employee_tasks(employee_id,title,details,deadline,instructions,assigned_by) VALUES(?,?,?,?,?,?)",employeeId,title,details,deadline,notes,actor.id());
            notifyEmployee(c,employeeId,"Task assigned",title+"; due "+deadline,"TASK");audit(c,actor.id(),"ASSIGN","TASK",null,"Employee "+employeeId+": "+title);return null;});
    }
    public void completeTask(UserSession actor,int id,String comments)throws SQLException{
        requireRole(actor,"EMPLOYEE");Validation.text(comments,"Completion comments",2000);
        transaction(c->{Validation.require(actor.employeeId()!=null,"No employee profile is linked.");activeEmployee(c,actor.employeeId());
            int n=execute(c,"UPDATE employee_tasks SET status='COMPLETED',completion_comments=?,completed_at=SYSDATETIME() WHERE task_id=? AND employee_id=? AND status='ASSIGNED'",comments,id,actor.employeeId());
            if(n!=1)throw new SecurityException("Task is not assigned to you or is already closed.");
            execute(c,"INSERT INTO notifications(user_id,title,message,notification_type) SELECT assigned_by,'Task completed',title,'TASK' FROM employee_tasks WHERE task_id=?",id);
            audit(c,actor.id(),"COMPLETE","TASK",id,comments.substring(0,Math.min(comments.length(),500)));return null;});
    }
    public void reviewTask(UserSession actor,int id,BigDecimal rating,String feedback)throws SQLException{
        requireRole(actor,"OPERATIONS_MANAGER");Validation.rating(rating);Validation.text(feedback,"Feedback",2000);
        transaction(c->{int n=execute(c,"UPDATE employee_tasks SET status='REVIEWED',rating=?,feedback=?,reviewed_by=?,reviewed_at=SYSDATETIME() WHERE task_id=? AND status='COMPLETED'",rating,feedback,actor.id(),id);
            Validation.require(n==1,"Only a completed task can be reviewed.");
            execute(c,"INSERT INTO notifications(user_id,title,message,notification_type) SELECT u.user_id,'Task reviewed',t.title,'TASK' FROM employee_tasks t JOIN user_accounts u ON u.employee_id=t.employee_id WHERE task_id=?",id);
            audit(c,actor.id(),"REVIEW","TASK",id,"Rating "+rating+": "+feedback.substring(0,Math.min(feedback.length(),450)));return null;});
    }
    public void deleteTask(UserSession actor,int id,String reason)throws SQLException{
        requireRole(actor,"OPERATIONS_MANAGER");Validation.text(reason,"Deletion reason",500);
        transaction(c->{int n=execute(c,"UPDATE employee_tasks SET status='DELETED',deleted_by=?,deleted_at=SYSDATETIME(),deletion_reason=? WHERE task_id=? AND status<>'DELETED'",actor.id(),reason,id);
            Validation.require(n==1,"Task not found or already deleted.");audit(c,actor.id(),"DELETE","TASK",id,reason);return null;});
    }
    private BigDecimal salaryBase(Connection c,int id,boolean draft)throws SQLException{
        String sql=draft?"SELECT base_salary FROM payroll_records WITH (UPDLOCK,HOLDLOCK) WHERE payroll_record_id=?":"SELECT TOP 1 base_salary FROM salary_structures WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=? AND status='ACTIVE' ORDER BY effective_from DESC,salary_structure_id DESC";
        try(PreparedStatement p=c.prepareStatement(sql)){bind(p,id);try(ResultSet r=p.executeQuery()){return r.next()?r.getBigDecimal(1):BigDecimal.ZERO;}}
    }
    public Map<String,Object> salaryPreview(UserSession actor,int id,boolean draft)throws SQLException{
        requireRole(actor,"PAYROLL_SPECIALIST");
        String sql=draft?"SELECT e.employee_code,e.full_name,pr.base_salary old_salary FROM payroll_records pr JOIN payroll_runs r ON r.payroll_run_id=pr.payroll_run_id JOIN employees e ON e.employee_id=pr.employee_id WHERE pr.payroll_record_id=? AND r.status='DRAFT'":"SELECT e.employee_code,e.full_name,COALESCE(s.base_salary,0) old_salary FROM employees e OUTER APPLY(SELECT TOP 1 base_salary FROM salary_structures WHERE employee_id=e.employee_id AND status='ACTIVE' ORDER BY effective_from DESC,salary_structure_id DESC)s WHERE e.employee_id=? AND e.status='ACTIVE'";
        var rows=query(sql,p->p.setInt(1,id));Validation.require(!rows.isEmpty(),"Employee or editable draft not found.");return rows.get(0);
    }
}
