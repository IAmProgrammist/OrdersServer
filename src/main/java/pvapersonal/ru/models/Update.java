package pvapersonal.ru.models;

import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.MySQLConnector;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Update {
    public int id;
    public long eventcount;
    public EventTypes eventType;
    public JSONObject additionalData;
    public Long eventDate;
    public int roomId;
    public int initiator;
    public String roomName;
    public String userName;

    public Update(ResultSet set) throws SQLException {
        id = set.getInt("id");
        eventcount = set.getLong("eventcount");
        eventType = EventTypes.valueOf(set.getString("eventName"));
        additionalData = new JSONObject(set.getString("eventData"));
        eventDate = set.getLong("eventDate");
        roomId = set.getInt("roomId");
        if(MySQLConnector.resultSetContainsColumn(set, "initiator")) {
            initiator = set.getInt("initiator");
        }else if(MySQLConnector.resultSetContainsColumn(set, "target")){
            initiator = set.getInt("target");
        }else{
            throw new SQLException();
        }
        if(MySQLConnector.resultSetContainsColumn(set, "name")) {
            roomName = set.getString("name");
            if(roomName == null){
                try(Connection conn = MySQLConnector.getMySQLConnection()) {
                    String query = String.format("SELECT name FROM rooms WHERE id=%d;", roomId);
                    ResultSet afdjod = conn.createStatement().executeQuery(query);
                    if(afdjod.next()) {
                        roomName = afdjod.getString("name");
                    }else{
                        roomName = "Неизвестная комната";
                    }
                } catch (ClassNotFoundException e) {
                    roomName = "Неизвестная комната";
                }
            }
        }else{
            try(Connection conn = MySQLConnector.getMySQLConnection()) {
                String query = String.format("SELECT name FROM rooms WHERE id=%d;", roomId);
                ResultSet afdjod = conn.createStatement().executeQuery(query);
                if(afdjod.next()) {
                    roomName = afdjod.getString("name");
                }else{
                    roomName = "Неизвестная комната";
                }
            } catch (ClassNotFoundException e) {
                roomName = "Неизвестная комната";
            }
        }
        userName = set.getString("middlename") == null ?
        String.format("%s %s", set.getString("surname"), set.getString(11)) :
                String.format("%s %s %s", set.getString("surname"), set.getString(11),
                        set.getString("middlename"));
    }

    public Update(int id, long eventcount, EventTypes eventType, JSONObject additionalData, Long eventDate, int roomId, int initiator, String roomName, String userName) {
        this.id = id;
        this.eventcount = eventcount;
        this.eventType = eventType;
        this.additionalData = additionalData;
        this.eventDate = eventDate;
        this.roomId = roomId;
        this.initiator = initiator;
        this.roomName = roomName;
        this.userName = userName;
    }

    public JSONObject toJSONString(int userId){
        JSONObject root = new JSONObject();
        root.put("id", id);
        root.put("eventcount", eventcount);
        root.put("eventName", eventType.toString());
        root.put("eventData", additionalData);
        root.put("eventDate", eventDate);
        root.put("roomId", roomId);
        root.put("userId", initiator);
        root.put("name", roomName);
        root.put("self", initiator == userId);
        root.put("userName", userName);
        return root;
    }
}
