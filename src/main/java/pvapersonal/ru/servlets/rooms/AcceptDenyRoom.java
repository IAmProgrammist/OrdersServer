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
import pvapersonal.ru.queues.QueuesManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/acceptdeny")
public class AcceptDenyRoom extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        boolean forwarded = false;
        if (req.getParameter("key") == null || req.getParameter("method") == null ||
                req.getParameter("roomId") == null)  {
            resp.setStatus(422);
            response.put("msg", "Missing parameter 'key', 'roomId' or 'method'");
        } else {
            String accessKey = req.getParameter("key");
            Integer roomId = Integer.valueOf(req.getParameter("roomId"));
            //0 is accept; 1 is deny
            Integer method = Integer.valueOf(req.getParameter("method"));
            try  {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    Integer userId = AccessKeyStore.getUserIdByKey(accessKey);
                    if(method == 0){
                        QueuesManager.keyboardAffected(accessKey, roomId);
                        forwarded = true;
                        RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/joinroom");
                        dispatcher.forward(req, resp);
                    }else if(method == 1){
                        QueuesManager.cancelled(accessKey, roomId);
                        response.put("msg", "Success!");
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
        if(!forwarded) {
            printWriter.println(response.toString());
        }
        printWriter.flush();
        printWriter.close();
    }
}
