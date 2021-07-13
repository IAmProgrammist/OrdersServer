package pvapersonal.ru.servlets.users;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/login")
public class Login extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        String telNumber = req.getParameter("telNumber");
        String password = req.getParameter("password");
        JSONObject response = new JSONObject();
        if (telNumber != null && password != null) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                Phonenumber.PhoneNumber number = phoneUtil.parse(telNumber, "RU");
                String stringNumber = phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
                String query = "SELECT * FROM users WHERE telNumber='" + stringNumber + "' AND password='"
                        + password + "';";
                ResultSet set = conn.createStatement().executeQuery(query);
                if(set.next()){
                    int userId = set.getInt("id");
                    String generatedKey = AccessKeyStore.generateAccessKey(userId);
                    response.put("key", generatedKey);
                    response.put("msg", "Success!");
                }else{
                    resp.setStatus(401);
                    response.put("msg", "Haven't find user with such phone number and password");
                }
            } catch (SQLException | ClassNotFoundException e) {
                resp.setStatus(500);
                response.put("msg", "Database is not available, try later");
            } catch (NumberParseException e) {
                resp.setStatus(422);
                response.put("msg", "Got error while parsing telephone number");
            }
        } else {
            resp.setStatus(422);
            response.put("msg", "Missing parameters 'login', 'password', 'name' or 'surname'");
        }
        printWriter.println(response.toString());
        printWriter.close();
    }
}
