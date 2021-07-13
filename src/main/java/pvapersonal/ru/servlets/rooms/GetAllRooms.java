package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.utils.RoomUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/allrooms")
public class GetAllRooms extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        PrintWriter out = resp.getWriter();
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String accessKey = req.getParameter("key");
            if (accessKey != null) {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    int userId = AccessKeyStore.getUserIdByKey(accessKey);
                    String query = "";
                    EventManager.updateGeneralEvents(userId);
                    //Edit:
                    /*
                    * query = String.format("SELECT roomsusers.roomId, rooms.name, rooms.admin, " +
                                "rooms.creationDate, rooms.id, rooms.password FROM roomsusers " +
                                "LEFT JOIN rooms ON roomsusers.roomId = rooms.id WHERE roomsusers.userId=%d;", userId);
                    */
                    boolean isAdmin = AccessKeyStore.isAdmin(userId);
                    long now = TimeManager.now();
                    if (isAdmin) {
                        query = String.format("SELECT rooms.*, users.name, users.surname, users.middlename FROM " +
                                "rooms LEFT JOIN users ON users.id=rooms.admin WHERE rooms.admin=%d;", userId);
                    } else {
                        query = String.format("SELECT rooms.*, users.name, users.surname, users.middlename FROM " +
                                "rooms LEFT JOIN users ON users.id=rooms.admin WHERE rooms.admin!=%d;", userId);
                    }
                    JSONArray roomsArray = new JSONArray();
                    ResultSet set = conn.createStatement().executeQuery(query);
                    while (set.next()) {
                        JSONObject roomData = new JSONObject();
                        int roomId = set.getInt("rooms.id");
                        roomData.put("name", set.getString("name"));
                        roomData.put("isAdmin", set.getInt("admin") == userId);
                        roomData.put("creationDate", set.getLong("creationDate"));
                        roomData.put("partitionType", RoomUtils.participiantType(roomId, userId));
                        roomData.put("id", roomId);
                        query = String.format("SELECT id FROM roomsusers WHERE roomId=%d", roomId);
                        //-1 because of admin
                        int count = -1;
                        ResultSet resultSet = conn.createStatement().executeQuery(query);
                        while (resultSet.next()) {
                            count++;
                        }
                        roomData.put("passworded", set.getString("password") != null);
                        roomData.put("members", count);
                        roomData.put("maxMembers", set.getString("maxMembers"));
                        roomData.put("creatorId", set.getInt("admin"));
                        roomData.put("creator", set.getString("middlename") != null ?
                                String.format("%s %s %s", set.getString("surname"),
                                        set.getString("users.name"),
                                        set.getString("middlename")) : String.format("%s %s",
                                set.getString("surname"),
                                set.getString("users.name")));
                        query = "SELECT * FROM roomtimeinfo WHERE roomId=" + roomId + " LIMIT 1;";
                        ResultSet newResultSet = conn.createStatement().executeQuery(query);
                        if(newResultSet.next()){
                            long start = newResultSet.getLong("start");
                            long end = newResultSet.getLong("end");
                            if(now < start){
                                roomData.put("status", RoomState.WAIT);
                            }else if(now < end){
                                roomData.put("status", RoomState.EXECUTING);
                            }else {
                                roomData.put("status", RoomState.EXECUTED);
                            }
                            roomData.put("start", start);
                            roomData.put("end", end);
                        }else{
                            roomData.put("status", RoomState.NOT_SET_UP);
                        }
                        roomsArray.put(roomData);
                    }
                    response.put("msg", "Success!");
                    response.put("isAdmin", isAdmin);
                    response.put("queryDate", now);
                    response.put("data", roomsArray);
                } else {
                    resp.setStatus(401);
                    response.put("msg", "Access key expired");
                }
            } else {
                resp.setStatus(422);
                response.put("msg", "Missing parameter 'key'");
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            resp.setStatus(500);
            response.put("msg", "Database are not available");
        }
        out.write(response.toString());
        out.flush();
        out.close();
    }
}
