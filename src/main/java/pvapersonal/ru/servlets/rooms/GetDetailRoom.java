package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.queues.QueuesManager;
import pvapersonal.ru.utils.RoomUtils;
import pvapersonal.ru.utils.UsersUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/detailroom")
public class GetDetailRoom extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        if (req.getParameter("key") == null || req.getParameter("roomId") == null ||
                req.getParameter("method") == null)  {
            resp.setStatus(422);
            response.put("msg", "Missing parameter 'key', 'roomId' or 'method'");
        } else {
            //0 stands for full room info, 1 stands for full users info
            String accessKey = req.getParameter("key");
            Integer roomId = Integer.valueOf(req.getParameter("roomId"));
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                int method = Integer.parseInt(req.getParameter("method"));
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    Integer userId = AccessKeyStore.getUserIdByKey(accessKey);
                    EventManager.updateEvents(userId, roomId);
                    QueuesManager.roomVisited(userId, roomId);
                    if(method == 0) {
                        String query = String.format("SELECT rooms.*, roomtimeinfo.*, users.name, users.middlename, " +
                                "users.surname FROM rooms LEFT JOIN roomtimeinfo ON roomtimeinfo.roomId=rooms.id LEFT " +
                                "JOIN users ON rooms.admin=users.id WHERE rooms.id=%d LIMIT 1;", roomId);
                        ResultSet set = conn.createStatement().executeQuery(query);
                        if (set.next()) {
                            response.put("id", roomId);
                            response.put("name", set.getString("rooms.name"));
                            response.put("isAdmin", set.getInt("rooms.admin") == userId);
                            response.put("creationDate", set.getLong("rooms.creationDate"));
                            response.put("members", RoomUtils.getAllUsersBesidesAdmin(roomId).size());
                            response.put("passworded", set.getString("rooms.password") != null);
                            response.put("maxMembers", set.getString("rooms.maxMembers"));
                            response.put("creatorId", set.getInt("rooms.admin"));
                            response.put("shouldShowExpandedKeyboard", QueuesManager.shouldShowExpandedKeyboard(userId, roomId));
                            //0 is not part, 1 is part, 2 is admin.
                            response.put("participantType", RoomUtils.participiantType(roomId, userId));
                            response.put("creator", set.getString("users.middlename") != null ?
                                    String.format("%s %s %s", set.getString("users.surname"),
                                            set.getString("users.name"),
                                            set.getString("users.middlename")) : String.format("%s %s",
                                    set.getString("users.surname"),
                                    set.getString("users.name")));
                            if (set.getString("roomtimeinfo.id") == null) {
                                response.put("status", RoomState.NOT_SET_UP);
                                response.put("queryDate", TimeManager.now());
                                response.put("users", new JSONArray());
                            } else {
                                response.put("comment", set.getString("roomtimeinfo.comment"));
                                response.put("payType", set.getByte("roomtimeinfo.transType"));
                                response.put("payVal", set.getInt("roomtimeinfo.transVal"));
                                long now = TimeManager.now();
                                long start = set.getLong("roomtimeinfo.start");
                                long end = set.getLong("roomtimeinfo.end");
                                if (now < start) {
                                    response.put("status", RoomState.WAIT);
                                } else if (now < end) {
                                    response.put("status", RoomState.EXECUTING);
                                } else {
                                    response.put("status", RoomState.EXECUTED);
                                }
                                response.put("start", start);
                                response.put("end", end);
                                //used for countdown in general
                                response.put("queryDate", now);
                                JSONArray users = new JSONArray();
                                List<Integer> usersList = RoomUtils.getAllUsersBesidesAdmin(roomId);
                                for(Integer user : usersList){
                                    UsersUtils usersUtils = new UsersUtils(user);
                                    JSONObject shortDesc = new JSONObject();
                                    shortDesc.put("userId", user);
                                    if(usersUtils.getAvatar() != null) {
                                        shortDesc.put("avatar", usersUtils.getAvatar());
                                    }
                                    shortDesc.put("name", usersUtils.getShortUserName());
                                    shortDesc.put("self", user.equals(userId));
                                    users.put(shortDesc);
                                }
                                response.put("users", users);
                                response.put("msg", "Success!");
                            }
                        }else{
                            resp.setStatus(404);
                            response.put("msg", "Room with such id doesnt exists");
                        }
                    }else if(method == 1){
                        List<Integer> users = RoomUtils.getAllUsersBesidesAdmin(roomId);
                        JSONArray usersList = new JSONArray();
                        for(int us : users){
                            UsersUtils usersUtils = new UsersUtils(us);
                            JSONObject res = new JSONObject();
                            if(us != userId) {
                                res.put("name", usersUtils.getUserName());
                            }
                            res.put("userId", us);
                            if(usersUtils.getAvatar() != null) {
                                res.put("image", usersUtils.getAvatar());
                            }
                            res.put("phone", usersUtils.getUserPhone());
                            usersList.put(res);
                        }
                        JSONObject admin = new JSONObject();
                        int adminId = RoomUtils.getAdmin(roomId);
                        UsersUtils usersUtils = new UsersUtils(adminId);
                        if(adminId != userId){
                            admin.put("name", usersUtils.getUserName());
                        }
                        admin.put("userId", adminId);
                        if(usersUtils.getAvatar() != null) {
                            admin.put("image", usersUtils.getAvatar());
                        }
                        admin.put("phone", usersUtils.getUserPhone());
                        response.put("msg", "Success!");
                        response.put("admin", admin);
                        response.put("users", usersList);
                        response.put("queryDate", TimeManager.now());
                        response.put("id", roomId);
                    }else{
                        throw new NumberFormatException();
                    }
                } else {
                    resp.setStatus(403);
                    response.put("msg", "Access key expired");
                }

            } catch (SQLException | ClassNotFoundException throwables) {
                resp.setStatus(500);
                response.put("msg", "Database is not available, try later");
            }catch (NumberFormatException r){
                resp.setStatus(422);
                response.put("msg", "Illegal value for 'method' - should be 1 or 0");
            }
        }
        printWriter.println(response.toString());
        printWriter.flush();
        printWriter.close();
    }
}
