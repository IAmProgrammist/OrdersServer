package pvapersonal.ru.utils;

import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RoomUtils {
    public static List<Integer> getAllUsersBesidesAdmin(int roomId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            List<Integer> res = new ArrayList<>();
            String query = "SELECT admin FROM rooms WHERE id=" + roomId + " LIMIT 1";
            ResultSet s = conn.createStatement().executeQuery(query);
            if(s.next()) {
                int adm = s.getInt("admin");
                query = "SELECT userId FROM roomsusers WHERE roomId=" + roomId + " AND userId!=" + adm + ";";
                ResultSet set = conn.createStatement().executeQuery(query);
                while (set.next()) {
                    res.add(set.getInt("userId"));
                }
            }
            return res;
        }
    }

    public static String getRoomName(int roomId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = "SELECT name FROM rooms WHERE id=" + roomId + " LIMIT 1;";
            ResultSet rs = conn.createStatement().executeQuery(query);
            rs.next();
            return rs.getString("name");
        }
    }

    public static byte participiantType(Integer roomId, Integer userId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = String.format("SELECT id FROM rooms WHERE admin=%d AND id=%d LIMIT 1;", userId, roomId);
            if(conn.createStatement().executeQuery(query).next()){
                return 2;
            }else if(conn.createStatement().executeQuery(String.format("SELECT id FROM roomsusers WHERE roomId=%d AND" +
                    " userId=%d LIMIT 1;", roomId, userId)).next()){
                return 1;
            }
        }
        return 0;
    }

    public static boolean canJoinExit(int roomId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = "SELECT start FROM `roomtimeinfo` WHERE roomId=" + roomId + " LIMIT 1;";
            ResultSet set = conn.createStatement().executeQuery(query);
            if(set.next()){
                long start = set.getLong("start");
                return TimeManager.now() < start;
            }
        }
        return false;
    }

    public static int maxMembers(int roomId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = String.format("SELECT maxMembers FROM rooms WHERE id=%d LIMIT 1;", roomId);
            ResultSet s = conn.createStatement().executeQuery(query);
            if(s.next()){
                return s.getInt("maxMembers");
            }
        }
        return -1;
    }

    public static int getAdmin(Integer roomId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = String.format("SELECT admin FROM rooms WHERE id=%d LIMIT 1;", roomId);
            ResultSet s = conn.createStatement().executeQuery(query);
            if(s.next()){
                return s.getInt("admin");
            }
        }
        return -1;
    }
}
