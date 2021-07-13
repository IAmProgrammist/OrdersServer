package pvapersonal.ru.quartzjobs;

import org.json.JSONObject;
import org.quartz.*;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.MySQLConnector;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnRoomStartJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        Object roomIdObj = dataMap.get("roomId");
        Object userIdObj = dataMap.get("userId");
        try (Connection conn = MySQLConnector.getMySQLConnection()){
            int roomId = (int) roomIdObj;
            int userId = (int) userIdObj;
            String query = "SELECT id, stateCompleted FROM roomtimeinfo WHERE roomId=" + roomId + " LIMIT 1;";
            ResultSet l = conn.createStatement().executeQuery(query);
            if(l.next()) {
                int id = l.getInt("id");
                byte stateCompleted = l.getByte("stateCompleted");
                if(stateCompleted < 1) {
                    query = "UPDATE roomtimeinfo SET stateCompleted=1 WHERE id=" + id + ";";
                    conn.createStatement().executeUpdate(query);
                    JSONObject data = new JSONObject();
                    data.put("status", RoomState.EXECUTING);
                    EventManager.dispatchEvent(data, roomId, userId, EventTypes.ROOM_STATUS_CHANGE);
                }
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            Logger.getLogger("Orders").log(Level.WARNING, "Quartz couldn't access to MySQL");
        }
    }
}
