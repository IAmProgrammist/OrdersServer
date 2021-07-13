package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.queues.QueuesManager;
import pvapersonal.ru.utils.RoomUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/createroom")
public class CreateRoom extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        JSONObject response = new JSONObject();
        req.setCharacterEncoding("UTF-8");
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            BufferedReader readerReq = req.getReader();
            String res = "";
            String tmp = readerReq.readLine();
            while (tmp != null) {
                res += tmp;
                tmp = readerReq.readLine();
            }
            JSONObject request = new JSONObject(res);
            String accessKey = request.getString("accessKey");
            String name = request.getString("name");
            String password = request.has("password") ? request.getString("password") : null;
            int maxMembers = request.getInt("maxMembers");
            if(maxMembers < 1){
                throw new NumberFormatException();
            }
            if (AccessKeyStore.isKeyValid(accessKey)) {
                AccessKeyStore.updateTimeForKey(accessKey);
                int userId = AccessKeyStore.getUserIdByKey(accessKey);
                if(AccessKeyStore.isAdmin(userId)) {
                    String query = "";
                    Long creationDate = TimeManager.now();
                    if (password == null) {
                        query = String.format("INSERT INTO rooms (name, creationDate, admin, maxMembers) VALUES ('%s', %d, %d, %d)",
                                name, creationDate, userId, maxMembers);
                    } else {
                        query = String.format("INSERT INTO rooms (name, creationDate, password, admin, maxMembers) VALUES ('%s', %d, '%s', %d, %d)",
                                name, creationDate, password, userId, maxMembers);
                    }
                    conn.createStatement().execute(query);
                    query = "SELECT LAST_INSERT_ID();";
                    ResultSet set = conn.createStatement().executeQuery(query);
                    if (set.next()) {
                        int roomId = set.getInt(1);
                        response.put("msg", "Success!");
                        JSONObject data = new JSONObject();
                        data.put("name", name);
                        //Yes, 0, because yes
                        data.put("members", 0);
                        data.put("creationDate", creationDate);
                        data.put("id", roomId);
                        data.put("passworded", password != null);
                        data.put("creatorId", userId);
                        data.put("maxMembers", maxMembers);
                        data.put("queryDate", TimeManager.now());
                        String creator = "Неизвестно";
                        query = "SELECT name, surname, middlename FROM users WHERE id=" + userId + " LIMIT 1;";
                        ResultSet pre = conn.createStatement().executeQuery(query);
                        if(pre.next()){
                            creator = pre.getString("middlename") != null ?
                                    String.format("%s %s %s", pre.getString("surname"),
                                            pre.getString("name"),
                                            pre.getString("middlename")) : String.format("%s %s",
                                    pre.getString("surname"),
                                    pre.getString("name"));
                        }
                        data.put("creator", creator);
                        data.put("status", RoomState.NOT_SET_UP);
                        EventManager.dispatchEvent(data, roomId, userId, EventTypes.ROOM_CREATED);
                        query = String.format("INSERT INTO roomsusers (userId, roomId, lastUpdateId) " +
                                "VALUES (%d, %d, %d)", userId, roomId, 1);
                        conn.createStatement().execute(query);
                    } else {
                        resp.setStatus(500);
                        response.put("msg", "Database is not available, try later");
                    }
                }else{
                    resp.setStatus(403);
                    response.put("msg", "You are not admin!");
                }
            } else {
                resp.setStatus(401);
                response.put("msg", "Access key expired");
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            resp.setStatus(500);
            response.put("msg", "Database is not available, try later");
        } catch (JSONException | NumberFormatException e) {
            resp.setStatus(422);
            response.put("msg", "Missing parameters 'telNumber', 'password', 'name', 'maxMembers' or 'surname'");
        }
        out.write(response.toString());
        out.flush();
        out.close();
    }
}
