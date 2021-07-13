package pvapersonal.ru.servlets.queue;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.queues.QueuesManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/getqueue")
public class GetQueue extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try(PrintWriter out = resp.getWriter()) {
            JSONObject response = new JSONObject();
            String key = req.getParameter("key");
            if(key != null) {
                try (Connection conn = MySQLConnector.getMySQLConnection()) {
                    if(AccessKeyStore.isKeyValid(key)){
                        int userId = AccessKeyStore.getUserIdByKey(key);
                        JSONArray queue = QueuesManager.getQueue(userId);
                        EventManager.updateGeneralEvents(userId);
                        response.put("msg", "Success!");
                        response.put("queryDate", TimeManager.now());
                        response.put("isAdmin", AccessKeyStore.isAdmin(userId));
                        response.put("data", queue);
                    }else{
                        resp.setStatus(401);
                        response.put("msg", "Access key is expired");
                    }
                } catch (SQLException | ClassNotFoundException throwables) {
                    resp.setStatus(500);
                    response.put("msg", "Database are not available yet");
                }
            }else{
                resp.setStatus(422);
                response.put("msg", "Missing parameter 'key'");
            }
            out.write(response.toString());
        }
    }
}
