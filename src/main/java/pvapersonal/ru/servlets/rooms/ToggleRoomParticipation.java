package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.queues.QueuesManager;
import pvapersonal.ru.utils.RoomUtils;
import pvapersonal.ru.utils.UsersUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

@WebServlet("/toggleroompart")
public class ToggleRoomParticipation extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        RequestDispatcher dispatcher;
        String key = req.getParameter("key");
        boolean forwarded = false;
        int roomId = Integer.parseInt(req.getParameter("roomId"));
        if(key == null || roomId == 0){
            resp.setStatus(422);
            response.put("msg", "Missing parameter 'roomId' or 'key'");
        }else{
            try {
                if (AccessKeyStore.isKeyValid(key)) {
                    int userId = AccessKeyStore.getUserIdByKey(key);
                    UsersUtils usersUtils = new UsersUtils(userId);
                    AccessKeyStore.updateTimeForKey(key);
                    if(RoomUtils.canJoinExit(roomId) && !usersUtils.isAdmin(roomId)) {
                        QueuesManager.keyboardAffected(key, roomId);
                        if (usersUtils.isMember(roomId)) {
                            dispatcher = getServletContext().getRequestDispatcher("/exitroom");
                        } else {
                            dispatcher = getServletContext().getRequestDispatcher("/joinroom");
                        }
                        forwarded = true;
                        dispatcher.forward(req, resp);
                    }else{
                        resp.setStatus(422);
                        response.put("msg", "Room status is not WAIT - can't join");
                    }
                }else{
                    resp.setStatus(403);
                    response.put("msg", "Access key expired");
                }
            } catch (SQLException | ClassNotFoundException throwables) {
                resp.setStatus(500);
                response.put("msg", "Couldn't access database");
            }
        }
        if(!forwarded) {
            printWriter.println(response.toString());
            printWriter.close();
        }
    }
}
