package pvapersonal.ru.wallet;

import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TransactionItem {
    int id;
    public TransactionTypes transType;
    public long transVal;
    public int roomId;
    public String initiatorName;
    public Long transDate;

    public TransactionItem(ResultSet set) throws SQLException {
        id = set.getInt("id");
        transType = TransactionTypes.valueOf(set.getInt("transType"));
        transVal = set.getLong("transVal");
        roomId = set.getInt("roomId");
        initiatorName = set.getString("initiatorName");
        transDate = set.getLong("transDate");
    }

    public JSONObject getJSONObject() {
        JSONObject res = new JSONObject();
        res.put("id", id);
        res.put("transType", transType.paymentType);
        res.put("transVal", transVal);
        res.put("roomId", roomId);
        if (initiatorName != null) {
            res.put("initiatorName", initiatorName);
        }
        res.put("transDate", transDate);
        return res;
    }
}
