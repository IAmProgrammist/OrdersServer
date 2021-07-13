package pvapersonal.ru.quartzjobs;

import org.json.JSONObject;
import org.quartz.*;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.utils.Utils;
import pvapersonal.ru.utils.RoomUtils;
import pvapersonal.ru.wallet.TransactionTypes;
import pvapersonal.ru.wallet.Wallet;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnRoomEndedJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        Object roomIdObj = dataMap.get("roomId");
        Object userIdObj = dataMap.get("userId");
        Object startObj = dataMap.get("start");
        Object endObj = dataMap.get("end");
        try (Connection conn = MySQLConnector.getMySQLConnection()){
            int roomId = (int) roomIdObj;
            int userId = (int) userIdObj;
            long start = (long) startObj;
            long end = (long) endObj;
            String query = "SELECT id, stateCompleted FROM roomtimeinfo WHERE roomId=" + roomId + " LIMIT 1;";
            ResultSet l = conn.createStatement().executeQuery(query);
            if(l.next()) {
                int id = l.getInt("id");
                byte stateCompleted = l.getByte("stateCompleted");
                if(stateCompleted < 2) {
                    List<Integer> users = RoomUtils.getAllUsersBesidesAdmin(roomId);
                    query = "SELECT roomtimeinfo.transVal, roomtimeinfo.transType, rooms.maxMembers FROM roomtimeinfo" +
                            " INNER JOIN rooms ON roomtimeinfo.roomId=rooms.id WHERE roomtimeinfo.roomId=" + roomId +
                            " LIMIT 1;";
                    ResultSet tr = conn.createStatement().executeQuery(query);
                    if(tr.next() && users.size() != 0) {
                        TransactionTypes transactionType = TransactionTypes.valueOf(tr.getInt("transType"));
                        long diff = end-start;
                        long gcd = Utils.GCD(diff, 3600000L);
                        long toDiv = 3600000L / gcd;
                        diff = diff / gcd;
                        long payVal = 0;
                        int maxMembers = tr.getInt("maxMembers");
                        long fullTransVal = tr.getLong("transVal");
                        switch (transactionType){
                            case PER_HOUR_PAYMENT:
                                payVal = fullTransVal * diff / toDiv;
                                break;
                            case FULL_ROOM_PAYMENT:
                                payVal = fullTransVal / maxMembers;
                                break;
                            default:
                                break;
                        }
                        for(Integer usId : users){
                            Wallet.addTransactionItem(usId, transactionType, payVal, roomId, RoomUtils.getRoomName(roomId));
                        }
                    }
                    query = "UPDATE roomtimeinfo SET stateCompleted=2 WHERE id=" + id + ";";
                    conn.createStatement().executeUpdate(query);
                    JSONObject data = new JSONObject();
                    data.put("status", RoomState.EXECUTED);
                    EventManager.dispatchEvent(data, roomId, userId, EventTypes.ROOM_STATUS_CHANGE);
                }
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            Logger.getLogger("Orders").log(Level.WARNING, "Quartz couldn't access to MySQL");
        }
    }
}
