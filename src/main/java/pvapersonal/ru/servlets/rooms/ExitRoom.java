package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.RequestDispatcher;
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
import pvapersonal.ru.utils.RoomUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/exitroom")
public class ExitRoom extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        String originalRequestURI = (String) req.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (originalRequestURI != null) {
            if (req.getParameter("key") == null || req.getParameter("roomId") == null) {
                resp.setStatus(422);
                response.put("msg", "Missing parameter 'key' or 'roomId'");
            } else {
                String accessKey = req.getParameter("key");
                int roomId = Integer.parseInt(req.getParameter("roomId"));
                try (Connection conn = MySQLConnector.getMySQLConnection()) {
                    if (AccessKeyStore.isKeyValid(accessKey)) {
                        AccessKeyStore.updateTimeForKey(accessKey);
                        int userId = AccessKeyStore.getUserIdByKey(accessKey);
                        String query = String.format("DELETE FROM roomsusers WHERE roomId=%d AND userId=%d", roomId, userId);
                        conn.createStatement().executeUpdate(query);
                        EventManager.dispatchEvent(new JSONObject(), roomId, userId, EventTypes.USER_EXITED);
                        response.put("msg", "Success!");
                    } else {
                        resp.setStatus(403);
                        response.put("msg", "Access key expired");
                    }

                } catch (SQLException | ClassNotFoundException throwables) {
                    resp.setStatus(500);
                    response.put("msg", "Database is not available, try later");
                }
            }
        }else{
            resp.setStatus(502);
            response.put("msg", "Do not use method directly, use /toggleroompart instead");
        }
        printWriter.println(response.toString());
        printWriter.close();
    }
}
