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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/updateuser")
public class UpdateUserInfo extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        JSONObject response = new JSONObject();
        req.setCharacterEncoding("UTF-8");
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            BufferedReader readerReq = req.getReader();
            String res = "";
            String tmp = readerReq.readLine();
            while (tmp != null) {
                res += tmp;
                tmp = readerReq.readLine();
            }
            JSONObject requestBody = new JSONObject(res);
            String telNumber = requestBody.getString("telNumber");
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phonenumber = phoneNumberUtil.parse(telNumber, "RU");
            telNumber = phoneNumberUtil.format(phonenumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            String name = requestBody.getString("name");
            String surName = requestBody.getString("surname");
            String middleName = requestBody.has("middlename") ? requestBody.getString("middlename") : null;
            String accessKey = requestBody.getString("key");
            if (AccessKeyStore.isKeyValid(accessKey)) {
                int userId = AccessKeyStore.getUserIdByKey(accessKey);
                AccessKeyStore.updateTimeForKey(accessKey);
                String query = String.format("SELECT * FROM users WHERE telNumber=%s AND id!=%d LIMIT 1;", telNumber, userId);
                if (!conn.createStatement().executeQuery(query).next()) {
                    if (middleName == null) {
                        query = String.format("UPDATE users SET name='%s', surname='%s', middlename=null, " +
                                "telNumber='%s' WHERE id=%d", name, surName, telNumber, userId);
                    } else {
                        query = String.format("UPDATE users SET name='%s', surname='%s', middlename='%s', " +
                                "telNumber='%s' WHERE id=%d", name, surName, middleName, telNumber, userId);
                    }
                    conn.createStatement().executeUpdate(query);
                    response.put("msg", "Success!");
                } else {
                    resp.setStatus(403);
                    response.put("msg", "User with such number already exists!");
                }
            } else {
                resp.setStatus(401);
                response.put("msg", "Access key expired");
            }
        } catch (JSONException e) {
            resp.setStatus(422);
            response.put("msg", "Missing parameters 'telNumber', 'key', 'name' or 'surname'");
        } catch (NumberParseException e) {
            resp.setStatus(422);
            response.put("msg", "Got error while parsing telephone number");
        } catch (SQLException | ClassNotFoundException throwables) {
            resp.setStatus(500);
            response.put("msg", "Database is not available, try later");
        }
        out.println(response.toString());
        out.close();
    }
}
