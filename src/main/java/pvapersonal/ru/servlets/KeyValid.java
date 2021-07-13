package pvapersonal.ru.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/iskeyactive")
public class KeyValid extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String accessKey = req.getParameter("key");
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        JSONObject response = new JSONObject();
        if(accessKey != null){
            try {
                boolean isValid = AccessKeyStore.isKeyValid(accessKey);
                response.put("data", isValid);
                response.put("msg", "Success!");
            }  catch (SQLException | ClassNotFoundException e) {
                resp.setStatus(500);
                response.put("msg", "Database is not available, try later");
            }
        }else{
            resp.setStatus(422);
            //What should I check duh
            response.put("msg", "Missing parameter 'key'");
        }
        out.println(response.toString());
        out.close();
    }
}
