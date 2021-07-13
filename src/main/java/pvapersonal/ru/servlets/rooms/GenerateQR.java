package pvapersonal.ru.servlets.rooms;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;

@WebServlet("/generateroomqr")
public class GenerateQR extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("image/jpg");
        try (Connection conn = MySQLConnector.getMySQLConnection()){
            Integer roomId = Integer.parseInt(req.getParameter("roomId"));
            String key = req.getParameter("key");
            if (AccessKeyStore.isKeyValid(key)) {
                AccessKeyStore.updateTimeForKey(key);
                int userId = AccessKeyStore.getUserIdByKey(key);
                String query = String.format("SELECT roomsusers.roomId, roomsusers.userId, roomsusers.id, rooms.name, " +
                                "rooms.password FROM roomsusers LEFT JOIN rooms ON rooms.id = roomsusers.roomId " +
                                "WHERE roomId=%d AND userId=%d LIMIT 1;",
                        roomId, userId);
                ResultSet set = conn.createStatement().executeQuery(query);
                if(set.next()){
                    JSONObject result = new JSONObject();
                    result.put("id", roomId);
                    String QRInfo = result.toString();
                    BitMatrix matrix = generateQRCodeImage(QRInfo);
                    MatrixToImageWriter.writeToStream(matrix, "jpg", resp.getOutputStream());
                }else{
                    //Not a member or rooms doesnt exist
                    resp.setStatus(403);
                }
            }else{
                //Access is expired
                resp.setStatus(401);
            }
        } catch (Exception throwables) {
            resp.setStatus(500);
        }
    }

    public static BitMatrix generateQRCodeImage(String barcodeText) throws Exception {
        QRCodeWriter barcodeWriter = new QRCodeWriter();

        return barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, 200, 200);
    }
}
