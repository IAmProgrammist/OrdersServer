package pvapersonal.ru.servlets.users;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.FilesHandler;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.utils.Utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;

@WebServlet("/setuserimage")
@MultipartConfig
public class SetUserImage extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        JSONObject response = new JSONObject();
        try (Connection conn = MySQLConnector.getMySQLConnection()){
            Part requestPart = req.getPart("avatar");
            Part requestPartKey = req.getPart("key");
            if (requestPart != null && requestPartKey != null) {
                String accessKey = new String(requestPartKey.getInputStream().readAllBytes());
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    String fileName = Utils.getFileName(requestPart);
                    if (fileName == null) {
                        resp.setStatus(422);
                        response.put("msg", "Missing image file 'avatar'");
                    } else {
                        String ext = fileName.split("\\.")[fileName.split("\\.").length - 1];
                        AbstractMap.SimpleEntry<Integer, String> ans = FilesHandler.loadFile(requestPart.getInputStream(), ext);

                        String sql = String.format("SELECT * FROM accesskeys WHERE accessKey='%s'", accessKey);
                        ResultSet set = conn.createStatement().executeQuery(sql);
                        if(set.next()){
                            int userId = set.getInt("userId");
                            sql = String.format("UPDATE users SET avatar=%d WHERE id=%d", ans.getKey(), userId);
                            conn.createStatement().executeUpdate(sql);
                            AccessKeyStore.updateTimeForKey(accessKey);
                            response.put("msg", "Success!");
                        }else{
                            throw new SQLException();
                        }
                    }
                } else {
                    resp.setStatus(403);
                    response.put("msg", "Access key expired");
                }
            }else{
                resp.setStatus(422);
                response.put("msg", "Missing image file 'avatar' or 'key'");
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            resp.setStatus(500);
            response.put("msg", "Database is not available, try later");
        }
        out.println(response.toString());
        out.close();
    }
}
