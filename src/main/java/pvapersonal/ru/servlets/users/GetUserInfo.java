package pvapersonal.ru.servlets.users;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@MultipartConfig
@WebServlet("/getuserinfo")
public class GetUserInfo extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        String accessKey = req.getParameter("key");
        String userId = req.getParameter("user");
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            if (accessKey != null) {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    if (userId != null) {
                        try {
                            Integer usId = Integer.parseInt(userId);
                            boolean isAdmin = AccessKeyStore.isAdmin(usId);
                            String sql = String.format("SELECT users.name, users.surname, users.middlename," +
                                    " users.telNumber, files.fileName FROM users LEFT JOIN files" +
                                    " ON users.avatar = files.id" +
                                    " WHERE users.id = '%d';", usId);
                            ResultSet set = conn.createStatement().executeQuery(sql);
                            if (set.next()) {
                                response.put("name", set.getString("name"));
                                response.put("surname", set.getString("surname"));
                                if (set.getString("middlename") != null) {
                                    response.put("middlename", set.getString("middlename"));
                                }
                                response.put("isAdmin", isAdmin);
                                response.put("telNumber", set.getString("telNumber"));
                                if (set.getString("fileName") != null) {
                                    response.put("fileName", set.getString("fileName"));
                                }
                                response.put("msg", "Success!");
                            } else {
                                resp.setStatus(404);
                                response.put("msg", "User is not found");
                            }
                        } catch (NumberFormatException e) {
                            resp.setStatus(422);
                            response.put("msg", "'UserId' should be int");
                        }
                    } else {
                        int userIdNum = AccessKeyStore.getUserIdByKey(accessKey);
                        boolean isAdmin = AccessKeyStore.isAdmin(userIdNum);
                        String sql = String.format("SELECT users.name, users.surname, users.middlename," +
                                " users.telNumber, files.fileName FROM accesskeys LEFT JOIN users" +
                                " ON users.id = accesskeys.userId LEFT JOIN files ON files.id = users.avatar" +
                                " WHERE accesskeys.accessKey = '%s';", accessKey);
                        ResultSet set = conn.createStatement().executeQuery(sql);
                        if (set.next()) {
                            response.put("name", set.getString("name"));
                            response.put("surname", set.getString("surname"));
                            if (set.getString("middlename") != null) {
                                response.put("middlename", set.getString("middlename"));
                            }
                            response.put("isAdmin", isAdmin);
                            response.put("telNumber", set.getString("telNumber"));
                            if (set.getString("fileName") != null) {
                                response.put("fileName", set.getString("fileName"));
                            }
                            response.put("msg", "Success!");
                        } else {
                            resp.setStatus(404);
                            response.put("msg", "User is not found");
                        }
                    }
                } else {
                    resp.setStatus(403);
                    response.put("msg", "Access key expired");
                }
            } else {
                resp.setStatus(422);
                response.put("msg", "Missing parameter 'key'");
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            resp.setStatus(500);
            response.put("msg", "Database is not available, try later");
        }
        printWriter.println(response.toString());
        printWriter.close();
    }
}
