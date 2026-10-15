package lk.sliit.ems.model;

import java.io.Serializable;

public record UserSession(int id, Integer employeeId, String username, String fullName, String role, boolean mustChangePassword, int securityVersion) implements Serializable {
    public UserSession(int id,Integer employeeId,String username,String fullName,String role,boolean mustChangePassword){this(id,employeeId,username,fullName,role,mustChangePassword,0);}
    public int getId(){return id;} public Integer getEmployeeId(){return employeeId;} public String getUsername(){return username;}
    public String getFullName(){return fullName;} public String getRole(){return role;} public boolean isMustChangePassword(){return mustChangePassword;}
    public boolean hasRole(String... allowed) {
        for (String value : allowed) if (role.equals(value)) return true;
        return false;
    }
}
