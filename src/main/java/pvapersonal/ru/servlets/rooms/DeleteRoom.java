package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.queues.QueuesManager;
import pvapersonal.ru.utils.RoomUtils;
import pvapersonal.ru.utils.UsersUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/deleteroom")
public class DeleteRoom extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        if (req.getParameter("key") == null || req.getParameter("roomId") == null) {
            resp.setStatus(422);
            response.put("msg", "Missing parameter 'key' or 'roomId'");
        } else {
            String accessKey = req.getParameter("key");
            Integer roomId = Integer.valueOf(req.getParameter("roomId"));
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    Integer userId = AccessKeyStore.getUserIdByKey(accessKey);
                    UsersUtils usersUtils = new UsersUtils(userId);
                    if (AccessKeyStore.isAdmin(userId) && usersUtils.isAdmin(roomId)) {
                        String query = String.format("SELECT * FROM rooms WHERE id=%d AND admin=%d", roomId, userId);
                        if (conn.createStatement().executeQuery(query).next()) {
                            EventManager.dispatchEvent(new JSONObject(), roomId, userId, EventTypes.ROOM_DELETED);
                            query = String.format("DELETE FROM rooms WHERE id=%d", roomId);
                            conn.createStatement().executeUpdate(query);
                            QueuesManager.roomDeleted(roomId);
                            response.put("msg", "Success!");
                        } else {
                            resp.setStatus(401);
                            response.put("msg", "You are not an admin of room or room doesnt exists");
                        }
                    }else{
                        resp.setStatus(401);
                        response.put("msg", "You are not an admin!");
                    }
                } else {
                    resp.setStatus(403);
                    response.put("msg", "Access key expired");
                }

            } catch (SQLException | ClassNotFoundException throwables) {
                resp.setStatus(500);
                response.put("msg", "Database is not available, try later");
            }
        }
        printWriter.println(response.toString());
        printWriter.close();
    }
}
