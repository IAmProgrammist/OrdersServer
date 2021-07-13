package pvapersonal.ru.other;

import pvapersonal.ru.utils.Utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AccessKeyStore {

    public static void init() throws SQLException, ClassNotFoundException {
        Long now = TimeManager.now();
        Connection conn = MySQLConnector.getMySQLConnection();
        String query = "SELECT * FROM accesskeys";
        ResultSet resultSet = conn.createStatement().executeQuery(query);
        while (resultSet.next()) {
            Long date = resultSet.getLong("lastAction");
            if (date + Utils.KEY_WORK_TIME < now) {
                query = String.format("DELETE FROM accesskeys WHERE id='%d';", resultSet.getInt("id"));
                conn.createStatement().execute(query);
            }
        }
        conn.close();
    }

    public static boolean isKeyValid(String key) throws SQLException, ClassNotFoundException {
        Long now = TimeManager.now();
        Connection conn = MySQLConnector.getMySQLConnection();
        String query = String.format("SELECT * FROM accesskeys WHERE accessKey='%s'", key);
        ResultSet set = conn.createStatement().executeQuery(query);
        boolean found = false;
        while (set.next()) {
            found = true;
            Long db = set.getLong("lastAction");
            if (db + Utils.KEY_WORK_TIME < now) {
                found = false;
                query = String.format("DELETE FROM accesskeys WHERE accessKey='%s';",
                        set.getString("accessKey"));
                conn.createStatement().execute(query);
                break;
            }
        }
        conn.close();
        return found;
    }

    public static void updateTimeForKey(String key) throws SQLException, ClassNotFoundException {
        Connection conn = MySQLConnector.getMySQLConnection();
        Long now = TimeManager.now();
        String query = String.format("UPDATE accesskeys SET lastAction='%d' WHERE accessKey='%s'", now, key);
        int affected = conn.createStatement().executeUpdate(query);
        if(affected == 0){
            throw new SQLException();
        }
        query = String.format("UPDATE users SET lastAction=%d WHERE id=%d;", now, getUserIdByKey(key));
        conn.createStatement().executeUpdate(query);
        conn.close();
    }

    public static String generateAccessKey(int userId) throws SQLException, ClassNotFoundException {
        Connection conn = MySQLConnector.getMySQLConnection();
        String query = String.format("SELECT users.id, accesskeys.accessKey, accesskeys.lastAction, accesskeys.id" +
                " FROM users INNER JOIN accesskeys ON accesskeys.userId = users.id WHERE users.id='%d';", userId);
        ResultSet set = conn.createStatement().executeQuery(query);
        String minDateKey = null;
        Integer accessKeyId = null;
        long minDate = TimeManager.now();
        while (set.next()){
            if(set.getLong("lastAction") + Utils.KEY_WORK_TIME > minDate){
                minDateKey = set.getString("accessKey");
                minDate = set.getLong("lastAction");
                accessKeyId = set.getInt(4);
            }
        }
        if(minDateKey == null){
            while (true) {
                try {
                    String key = Utils.generateRandomString(10);
                    query = String.format("INSERT INTO accesskeys (accessKey, lastAction, userId) VALUES ('%s', '%d', '%d')",
                            key, TimeManager.now(), userId);
                    conn.createStatement().execute(query);
                    minDateKey = key;
                }catch (SQLException e){
                    continue;
                }
                break;
            }
        }else{
            query = String.format("UPDATE accesskeys SET lastAction='%d' WHERE id='%d';", TimeManager.now(),
                    accessKeyId);
            conn.createStatement().executeUpdate(query);
        }
        conn.close();
        return minDateKey;
    }

    public static int getUserIdByKey(String key) throws SQLException, ClassNotFoundException {
        String query = String.format("SELECT userId FROM accesskeys WHERE accessKey='%s' LIMIT 1;", key);
        Connection conn = MySQLConnector.getMySQLConnection();
        ResultSet set = conn.createStatement().executeQuery(query);
        if(set.next()){
            return set.getInt("userId");
        }else{
            throw new SQLException();
        }
    }

    public static boolean isAdmin(int userId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = String.format("SELECT isAdmin FROM users WHERE id=%d LIMIT 1;", userId);
            ResultSet set = conn.createStatement().executeQuery(query);
            if(set.next()){
                return set.getInt("isAdmin") == 1;
            }else{
                throw new SQLException("Haven't found user with id " + userId);
            }
        }
    }
}
