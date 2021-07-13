package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventDispatchedListener;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.utils.RoomUtils;
import pvapersonal.ru.utils.UsersUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/joinroom")
public class JoinRoom extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //Gotta remake this one
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        req.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        String originalRequestURI = (String) req.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (originalRequestURI != null) {
            // It was forwarded. Now get the query string as follows.
            String originalQueryString = (String) req.getAttribute(RequestDispatcher.FORWARD_QUERY_STRING);
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                String roomIdParameter = req.getParameter("roomId");
                String password = req.getParameter("password");
                String key = req.getParameter("key");
                String query = "";
                if (AccessKeyStore.isKeyValid(key)) {
                    AccessKeyStore.updateTimeForKey(key);
                    int userId = AccessKeyStore.getUserIdByKey(key);
                    if (!AccessKeyStore.isAdmin(userId)) {
                        int roomId = Integer.parseInt(roomIdParameter);
                        query = String.format("SELECT id FROM roomsusers WHERE userId=%d AND roomId=%d LIMIT 1;", userId, roomId);
                        if (!conn.createStatement().executeQuery(query).next()) {
                            query = String.format("SELECT creationDate FROM rooms WHERE id=%d LIMIT 1;", roomId);
                            ResultSet setDate = conn.createStatement().executeQuery(query);
                            if (!setDate.next()) {
                                resp.setStatus(403);
                                response.put("msg", "Room doesn't exist");
                            } else {
                                if (password == null) {
                                    query = String.format("SELECT id FROM rooms WHERE id=%d AND password IS NULL LIMIT 1;",
                                            roomId);
                                } else {
                                    query = String.format("SELECT id FROM rooms WHERE id=%d AND password='%s' 1;",
                                            roomId, password);
                                }
                                if (conn.createStatement().executeQuery(query).next()) {
                                    int members = RoomUtils.getAllUsersBesidesAdmin(roomId).size();
                                    int maxMembers = RoomUtils.maxMembers(roomId);
                                    if(maxMembers!=-1 && members+1 <= maxMembers) {
                                        JSONObject data = new JSONObject();
                                        UsersUtils usersUtils = new UsersUtils(userId);
                                        data.put("name", usersUtils.getUserName());
                                        data.put("shortname", usersUtils.getShortUserName());
                                        data.put("phone", usersUtils.getUserPhone());
                                        if(usersUtils.getAvatar() != null) {
                                            data.put("image", usersUtils.getAvatar());
                                        }
                                        data.put("userId", userId);
                                        String file = usersUtils.getAvatar();
                                        if (file != null) {
                                            data.put("image", file);
                                        }
                                        EventManager.dispatchEvent(data, roomId, userId, EventTypes.USER_JOINED, new EventDispatchedListener() {
                                            @Override
                                            public void dispatched(long eventcount) {
                                                try (Connection conn = MySQLConnector.getMySQLConnection()) {
                                                    String query = String.format("INSERT INTO roomsusers (userId, roomId, lastUpdateId) " +
                                                            "VALUES (%d, %d, %d)", userId, roomId, eventcount);
                                                    conn.createStatement().execute(query);
                                                } catch (SQLException | ClassNotFoundException throwables) {
                                                    Logger.getLogger("Orders").log(Level.WARNING, "Coludn't join user");
                                                }
                                            }

                                            @Override
                                            public void error(Throwable err) {

                                            }
                                        });
                                        response.put("msg", "Success");
                                    }else{
                                        resp.setStatus(403);
                                        response.put("msg", "Room is full");
                                    }
                                } else {
                                    resp.setStatus(422);
                                    response.put("msg", "Room doesn't exists");
                                }
                            }
                        } else {
                            resp.setStatus(201);
                            response.put("msg", "Already logged in");
                        }
                    } else {
                        resp.setStatus(403);
                        response.put("msg", "You are admin - you can't join rooms");
                    }

                } else {
                    resp.setStatus(401);
                    response.put("msg", "Access key expired");
                }
            } catch (NumberFormatException e) {
                resp.setStatus(422);
                response.put("msg", "Invalid 'roomId'");
            } catch (SQLException | ClassNotFoundException throwables) {
                resp.setStatus(500);
                response.put("msg", "Database are not available");
            }
        }else{
            resp.setStatus(502);
            response.put("msg", "Do not use method directly, use /toggleroompart instead");
        }
        out.write(response.toString());
        out.flush();
        out.close();
    }
}
