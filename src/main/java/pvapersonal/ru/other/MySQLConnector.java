package pvapersonal.ru.other;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MySQLConnector {

    private static String hostName;
    private static String dbName;
    private static String userName;
    private static String password;

    public static void init(String hostName, String dbName, String userName,
                            String password) throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        MySQLConnector.hostName = hostName;
        MySQLConnector.dbName = dbName;
        MySQLConnector.userName = userName;
        MySQLConnector.password = password;
        Connection conn = getMySQLConnection();
        conn.close();
    }

    public static Connection getMySQLConnection() throws SQLException, ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String connectionURL = "jdbc:mysql://" + hostName + ":3306/" + dbName;
        Connection conn = DriverManager.getConnection(connectionURL, userName, password);
        return conn;
    }

    public static boolean resultSetContainsColumn(ResultSet rs, String column){
        try{
            rs.findColumn(column);
            return true;
        } catch (SQLException sqlex){
            return false;
        }
    }

}
