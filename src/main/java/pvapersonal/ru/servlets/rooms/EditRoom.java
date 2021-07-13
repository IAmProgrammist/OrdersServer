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

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/editroom")
public class EditRoom extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        if (req.getParameter("key") == null || req.getParameter("roomId") == null ||
                req.getParameter("name") == null)  {
            resp.setStatus(422);
            response.put("msg", "Missing parameter 'key', 'roomId' or 'name'");
        } else {
            String accessKey = req.getParameter("key");
            Integer roomId = Integer.valueOf(req.getParameter("roomId"));
            String name = req.getParameter("name");
            String password = req.getParameter("password");
            String comment = req.getParameter("comment");
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    Integer userId = AccessKeyStore.getUserIdByKey(accessKey);
                    String query = String.format("SELECT * FROM rooms WHERE id=%d AND admin=%d", roomId, userId);
                    if (conn.createStatement().executeQuery(query).next()) {
                        query = String.format("UPDATE rooms SET name='%s', password=%s WHERE id=%d;", name,
                                password == null ? "null" : "'" + password + "'",
                                roomId);
                        conn.createStatement().executeUpdate(query);
                        query = String.format("UPDATE roomtimeinfo SET comment=%s WHERE roomId=%d;",
                                comment == null ? "null" : "'" + comment + "'", roomId);
                        conn.createStatement().executeUpdate(query);
                        JSONObject updateData = new JSONObject();
                        updateData.put("name", name);
                        if(comment!= null) {
                            updateData.put("comment", comment);
                        }
                        updateData.put("isLocked", password != null);
                        EventManager.dispatchEvent(updateData, roomId, userId, EventTypes.ROOM_INFO_EDITED);
                        response.put("msg", "Success!");
                    } else {
                        resp.setStatus(401);
                        response.put("msg", "You are not an admin of room or room doesnt exists");
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
        printWriter.flush();
        printWriter.close();
    }
}
