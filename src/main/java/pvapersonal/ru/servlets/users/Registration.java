package pvapersonal.ru.servlets.users;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.mysql.cj.x.protobuf.Mysqlx;
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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/register")
public class Registration extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        JSONObject response = new JSONObject();
        req.setCharacterEncoding("UTF-8");
        try (Connection conn = MySQLConnector.getMySQLConnection()){
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
            String password = requestBody.getString("password");
            String name = requestBody.getString("name");
            String surName = requestBody.getString("surname");
            String middleName = requestBody.has("middlename") ? requestBody.getString("middlename") : null;
            String query = "SELECT * FROM users WHERE telNumber='" + telNumber + "';";
            ResultSet set = conn.createStatement().executeQuery(query);
            if(set.next()){
                resp.setStatus(401);
                response.put("msg", "User with such number already exists!");
            }else{
                if(middleName == null){
                    query = String.format("INSERT INTO users (name, surname, password, telNumber)" +
                                    " VALUES ('%s', '%s', '%s', '%s');", name, surName, password ,telNumber);
                }else{
                    query = String.format("INSERT INTO users (name, surname, middlename, password, telNumber)" +
                                    " VALUES ('%s', '%s', '%s', '%s', '%s');",
                            name, surName, middleName, password ,telNumber);
                }
                System.out.println(query);
                conn.createStatement().execute(query);
                query = "SELECT LAST_INSERT_ID();";
                set = conn.createStatement().executeQuery(query);
                set.next();
                int id = set.getInt(1);
                String generatedKey = AccessKeyStore.generateAccessKey(id);
                response.put("key", generatedKey);
                response.put("msg", "Success!");
            }
        }catch (JSONException e){
            resp.setStatus(422);
            response.put("msg", "Missing parameters 'telNumber', 'password', 'name' or 'surname'");
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
