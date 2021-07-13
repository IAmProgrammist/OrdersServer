package pvapersonal.ru.utils;

import pvapersonal.ru.other.MySQLConnector;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UsersUtils {

    private final int userId;

    public UsersUtils(int userId) {
        this.userId = userId;
    }

    public String getUserName() throws SQLException, ClassNotFoundException {
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = "SELECT name, surname, middlename FROM users WHERE id=" + userId + " LIMIT 1";
            ResultSet set = conn.createStatement().executeQuery(query);
            if (set.next()) {
                return set.getString("middlename") != null ?
                        String.format("%s %s %s", set.getString("surname"),
                                set.getString("users.name"),
                                set.getString("middlename")) : String.format("%s %s",
                        set.getString("surname"),
                        set.getString("users.name"));
            }
        }
        return "";
    }

    public String getShortUserName() throws SQLException, ClassNotFoundException {
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = "SELECT name, surname, middlename FROM users WHERE id=" + userId + " LIMIT 1";
            ResultSet set = conn.createStatement().executeQuery(query);
            if (set.next()) {
                return set.getString("middlename") != null ?
                        String.format("%s %c. %c.", set.getString("surname"),
                                set.getString("users.name").charAt(0),
                                set.getString("middlename").charAt(0)) : String.format("%s %c.",
                        set.getString("surname"),
                        set.getString("users.name").charAt(0));
            }
        }
        return "";
    }

    public String getUserPhone() throws SQLException, ClassNotFoundException {
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = "SELECT telNumber FROM users WHERE id=" + userId + " LIMIT 1";
            ResultSet set = conn.createStatement().executeQuery(query);
            if (set.next()) {
                return set.getString("telNumber");
            }
        }
        return "";
    }

    public String getAvatar() throws SQLException, ClassNotFoundException {
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = "SELECT `files`.fileName FROM users INNER JOIN files ON users.avatar=files.id WHERE users.id=" + userId + " LIMIT 1;";
            ResultSet set = conn.createStatement().executeQuery(query);
            if (set.next()) {
                return set.getString("files.fileName");
            }
        }
        return null;
    }

    public boolean isAdmin(int roomId) throws SQLException, ClassNotFoundException {
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = String.format("SELECT name FROM rooms WHERE admin=%d AND id=%d LIMIT 1;", userId, roomId);
            ResultSet set = conn.createStatement().executeQuery(query);
            if (set.next()) {
                return true;
            }
            query = "SELECT isAdmin FROM users WHERE id=" + userId + " LIMIT 1;";
            set = conn.createStatement().executeQuery(query);
            if(set.next()){
                return set.getByte(1) == 1;
            }
        }
        return false;
    }

    public boolean isMember(int roomId) throws SQLException, ClassNotFoundException {
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = String.format("SELECT lastUpdateId FROM `roomsusers` WHERE roomId=%d AND userId=%d LIMIT 1;", roomId, userId);
            ResultSet set = conn.createStatement().executeQuery(query);
            if (set.next()) {
                return true;
            }
        }
        return false;
    }
}
