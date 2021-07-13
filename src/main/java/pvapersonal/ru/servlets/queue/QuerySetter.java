package pvapersonal.ru.servlets.queue;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.queues.QueuesManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

@WebServlet("/querysetter")
public class QuerySetter extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try (PrintWriter out = resp.getWriter()) {
            JSONObject response = new JSONObject();
            String key = req.getParameter("key");
            if (key != null) {
                try {
                    if (AccessKeyStore.isKeyValid(key) && !AccessKeyStore.isAdmin(AccessKeyStore.getUserIdByKey(key))) {
                        int userId = AccessKeyStore.getUserIdByKey(key);
                        boolean toggle = QueuesManager.toggleQueue(key);
                        JSONObject toggleData = new JSONObject();
                        toggleData.put("toggle", toggle);
                        EventManager.dispatchEvent(toggleData, 0, userId, EventTypes.QUEUE_TOGGLE);
                    } else {
                        resp.setStatus(403);
                        response.put("msg", "Access key is expired or you are an admin!");
                    }
                } catch (SQLException | ClassNotFoundException throwables) {
                    resp.setStatus(500);
                    response.put("msg", "Database are not available yet");
                }
            } else {
                resp.setStatus(422);
                response.put("msg", "Missing parameter 'key'");
            }
            out.write(response.toString());
        }
    }
}
