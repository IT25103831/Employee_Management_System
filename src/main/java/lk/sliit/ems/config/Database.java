package lk.sliit.ems.config;
import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

public final class Database {
    private static final Properties PROPERTIES = new Properties();
    static {
        try (InputStream in=Database.class.getClassLoader().getResourceAsStream("database.properties")) {
            if(in!=null) PROPERTIES.load(in);
            Class.forName(setting("db.driver","EMS_DB_DRIVER","com.microsoft.sqlserver.jdbc.SQLServerDriver"));
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private Database() {}
    private static String setting(String key,String env,String fallback) {
        return System.getProperty(key,System.getenv().getOrDefault(env,PROPERTIES.getProperty(key,fallback)));
    }
    public static Connection getConnection() throws SQLException {
        String url=setting("db.url","EMS_DB_URL","jdbc:sqlserver://localhost:1433;databaseName=EmployeeManagementDB;encrypt=true;trustServerCertificate=true;loginTimeout=5");
        return DriverManager.getConnection(url,setting("db.username","EMS_DB_USER","sa"),setting("db.password","EMS_DB_PASSWORD",""));
    }
    public static boolean canConnect() {try(Connection c=getConnection()){return true;}catch(SQLException e){return false;}}
}
