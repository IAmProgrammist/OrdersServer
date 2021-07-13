package pvapersonal.ru.servlets.users;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.utils.UsersUtils;
import pvapersonal.ru.utils.Utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/getactiveusers")
public class GetActiveUsers extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        String accessKey = req.getParameter("key");
        try {
            if (accessKey != null) {
                if (AccessKeyStore.isKeyValid(accessKey) ) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    int userId = AccessKeyStore.getUserIdByKey(accessKey);
                    if(AccessKeyStore.isAdmin(userId)){
                        String query = String.format("SELECT users.id, accesskeys.lastAction FROM accesskeys LEFT JOIN users ON accesskeys.userId=users.id WHERE accesskeys.lastAction+%d>%d;", Utils.KEY_WORK_TIME, TimeManager.now());
                        JSONArray array = new JSONArray();
                        try(Connection conn = MySQLConnector.getMySQLConnection()){
                            ResultSet set = conn.createStatement().executeQuery(query);
                            while (set.next()){
                                JSONObject object = new JSONObject();
                                UsersUtils usersUtils = new UsersUtils(set.getInt("users.id"));
                                if(set.getInt("users.id")!=userId) {
                                    object.put("name", usersUtils.getUserName());
                                }
                                object.put("image", usersUtils.getAvatar());
                                object.put("userId", set.getInt("users.id"));
                                object.put("phone", usersUtils.getUserPhone());
                                object.put("lastAction", set.getLong("accesskeys.lastAction"));
                                object.put("isAdmin", AccessKeyStore.isAdmin(set.getInt("users.id")));
                                array.put(object);
                            }
                        }
                        response.put("data", array);
                        response.put("queryTime", TimeManager.now());
                        response.put("msg", "Success!");
                    }else{
                        resp.setStatus(401);
                        response.put("msg", "You should be admin for this method");
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
