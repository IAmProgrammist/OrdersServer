package pvapersonal.ru.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.HttpConstraint;
import jakarta.servlet.annotation.HttpMethodConstraint;
import jakarta.servlet.annotation.ServletSecurity;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.queues.QueuesManager;
import pvapersonal.ru.utils.UsersUtils;
import pvapersonal.ru.wallet.TransactionItem;
import pvapersonal.ru.wallet.Wallet;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

@WebServlet("/config")
public class Configuration extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        resp.setContentType("text/html");
        String currentURL = req.getRequestURL().toString();
        StringBuilder ans = new StringBuilder();
        String query = "";
        String adminId = req.getParameter("admin");
        String payHist = req.getParameter("payhist");
        if (payHist != null) {
            Integer userId = Integer.valueOf(payHist);
            Long payout = null;
            try {
                payout = Long.parseLong(req.getParameter("payout"));
            } catch (NumberFormatException e) {
            }
            if (payout != null) {
                try {
                    Wallet.payout(userId, payout);
                } catch (Exception ignore) {
                    ans = new StringBuilder();
                    ans.append("<html><head><title>Мы всё сломали</title></head><body>Тут и фиксики не помогут</body></html>");
                }
            }
            try {
                UsersUtils usersUtils = new UsersUtils(userId);
                ans.append("<!DOCTYPE html>\n" +
                        "<html charset=\"utf-8\">\n" +
                        "<head>\n" +
                        "<title>Администрирование пользователей</title>\n" +
                        "<style type=\"text/css\">\n" +
                        "\t.bold {\n" +
                        "\tfont-weight: bold;\n" +
                        "}\n" +
                        "td {\n" +
                        "padding: 8px;\n" +
                        "}\n" +
                        "</style>\n" +
                        "<script>\n" +
                        "var url = window.location.href;       \n" +
                        "var urlSplit = url.split( \"?\" );       \n" +
                        "var obj = { Title : \"New title\", Url: urlSplit[0] = \"?payhist=" + userId + "\"};  "+
                        "history.pushState(obj, obj.Title, obj.Url);    "+
                        "   function setPasswordEnabled(element) {\n" +
                        "    element.classList.toggle(\"password-visible\");\n" +
                        "   }\n" +
                        "\n" +
                        "function submited(event) {\n" +
                        "event.preventDefault();\n" +
                        "console.log(document.querySelector(\".submit-value\").value * 100);\n" +
                        String.format("   window.open(('%s?payhist=%d&payout=' + (document.querySelector(\".submit-value\").value * 100)),'_self');", currentURL, userId) + "\n" +
                        "}\n" +
                        "  </script>\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<h1>История выплат пользователя " + usersUtils.getUserName() + " </h1>\n" +
                        "<p>Общий баланс: " + Wallet.getWalletCount(userId)/100 + "." + Wallet.getWalletCount(userId) % 100 + " Р</p>\n" +
                        "<p>Произвести выплату (в рублях):</p>\n" +
                        "<form onsubmit=\"submited(event)\">\n" +
                        "<input type=\"number\" step=\"0.01\" min=\"0\" class=\"submit-value\"/>\n" +
                        "<input type=\"submit\"/>\n" +
                        "</form>\n" +
                        "<p>История выплат:</p>\n" +
                        "<table border=\"1\">\n" +
                        "<tr class=\"bold\">\n" +
                        "<td>Тип транзакции</td>\n" +
                        "<td>Источник</td>\n" +
                        "<td>Количество</td>\n" +
                        "<td>Время транзакции</td>" +
                        "</tr>");
                List<TransactionItem> transactionItems = Wallet.getPayHistory(userId);
                for (TransactionItem it : transactionItems) {
                    ans.append("<tr>");
                    switch (it.transType.paymentType) {
                        case 0:
                            ans.append("<td>Выплата почасовой комнаты</td>");
                            break;
                        case 1:
                            ans.append("<td>Выплата полного бюджета комнаты</td>");
                            break;
                        case 2:
                            ans.append("<td>Вывод средств администратором</td>");
                            break;
                    }
                    if (it.initiatorName == null) {
                        ans.append("<td>Администратор</td>");
                    } else {
                        ans.append("<td>" + it.initiatorName + "</td>");
                    }
                    ans.append("<td>" + it.transVal / 100 + "." + it.transVal % 100 + "</td>");
                    ans.append("<td>" + new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", new Locale("RU"))
                            .format(it.transDate) + "</td>");
                    ans.append("</tr>");
                }
                ans.append(
                        "</table>\n" +
                                "</body>\n" +
                                "</html>");
            } catch (Exception ignore) {
                ans = new StringBuilder();
                ans.append("<html><head><title>Мы всё сломали</title></head><body>Тут и фиксики не помогут</body></html>");
            }
        } else {
            ans.append("<!DOCTYPE html>\n" +
                    "<html charset=\"utf-8\">\n" +
                    "<head>\n" +
                    "<title>Администрирование пользователей</title>\n" +
                    "<style type=\"text/css\">\n" +
                    "\t.bold {\n" +
                    "\tfont-weight: bold;\n" +
                    "}\n" +
                    "td {\n" +
                    "padding: 8px;\n" +
                    "}\n" +
                    "\n" +
                    ".password-visible .password{\n" +
                    "display: block;\n" +
                    "}\n" +
                    "\n" +
                    ".password-visible .dots {\n" +
                    "display: none;\n" +
                    "}\n" +
                    "\n" +
                    ".password {\n" +
                    "display: none;\n" +
                    "}\n" +
                    "\n" +
                    ".dots {\n" +
                    "display: block;\n" +
                    "}\n" +
                    "</style>\n" +
                    "<script>\n" +
                    "var url = window.location.href;       \n" +
                    "var urlSplit = url.split( \"?\" );       \n" +
                    "var obj = { Title : \"New title\", Url: urlSplit[0]};  "+
                    "history.pushState(obj, obj.Title, obj.Url);    "+
                    "   function setPasswordEnabled(element) {\n" +
                    "    element.classList.toggle(\"password-visible\");\n" +
                    "   }\n" +
                    "function openPayHist(id){" +
                    String.format("   window.open(('%s?payhist=' + id),'_self');", currentURL) +
                    "}" +
                    " function updateAdmin(id) {" +
                    String.format("   window.open(('%s?admin=' + id),'_self');", currentURL) +
                    "}" +
                    "  </script>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<h1>Пользователи:</h1>\n" +
                    "<table border=\"1\">\n" +
                    "<tr class=\"bold\">\n" +
                    "<td>ID</td>\n" +
                    "<td>Имя</td>\n" +
                    "<td>Фамилия</td>\n" +
                    "<td>Отчество</td>\n" +
                    "<td>Пароль (нажмите на пароль, чтобы отобразить его)</td>\n" +
                    "<td>Номер телефона</td>\n" +
                    "<td>Администратор</td>\n" +
                    "<td>Кошелёк</td>\n" +
                    "<td>Последнее действие</td>\n" +
                    "</tr>");
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                if (adminId != null) {
                    int id = Integer.parseInt(adminId);
                    query = "SELECT isAdmin FROM users WHERE id=" + id + " LIMIT 1;";
                    ResultSet sett = conn.createStatement().executeQuery(query);
                    if (sett.next()) {
                        int isAdmin = sett.getInt("isAdmin");
                        query = "UPDATE users SET isAdmin=" + Math.abs(isAdmin - 1) + " WHERE id=" + id + ";";
                        conn.createStatement().executeUpdate(query);
                        QueuesManager.cleanUser(id);
                    }
                }
                query = "SELECT * FROM users";
                ResultSet set = conn.createStatement().executeQuery(query);
                while (set.next()) {
                    int id = set.getInt("id");
                    ans.append("<tr>");
                    ans.append(String.format("<td>%d</td>", id));
                    ans.append(String.format("<td>%s</td>", set.getString("name")));
                    ans.append(String.format("<td>%s</td>", set.getString("surname")));
                    ans.append(String.format("<td>%s</td>", set.getString("middlename")));
                    ans.append(String.format("<td onClick='setPasswordEnabled(this)'><span class='password'>%s</span> <span class='dots'>••••••</span></td>", set.getString("password")));
                    ans.append(String.format("<td>%s</td>", set.getString("telNumber")));
                    ans.append("<td><input type='checkbox' onClick='updateAdmin(" + id + ")' name='isAdmin' " + (set.getInt("isAdmin") == 1 ? "checked" : "") + "></td>");
                    ans.append(String.format("<td><input type=\"button\" value=\"История платежей\" onclick=\"openPayHist(%d)\"/></td>", id));
                    ans.append("<td>" + new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", new Locale("RU"))
                            .format(set.getLong("lastAction")) + "</td>");
                }
            } catch (ClassNotFoundException | SQLException e) {

            }
            ans.append("\n" +
                    "</table>\n" +
                    "</body>\n" +
                    "</html>");
        }
        out.write(ans.toString());
        out.flush();
    }
}
