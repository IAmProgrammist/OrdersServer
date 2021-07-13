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
import pvapersonal.ru.wallet.TransactionItem;
import pvapersonal.ru.wallet.TransactionTypes;
import pvapersonal.ru.wallet.Wallet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/transhist")
public class GetTransactionHistory extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        String accessKey = req.getParameter("key");
        try {
            if (accessKey != null) {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    int userId = AccessKeyStore.getUserIdByKey(accessKey);
                    List<TransactionItem> items = Wallet.getPayHistory(userId);
                    response.put("budget", Wallet.getWalletCount(userId));
                    JSONArray arr = new JSONArray();
                    for(TransactionItem it : items){
                        arr.put(it.getJSONObject());
                    }
                    response.put("data", arr);
                    response.put("msg", "Success!");
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
