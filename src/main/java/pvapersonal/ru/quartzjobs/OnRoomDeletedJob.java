package pvapersonal.ru.quartzjobs;

import org.json.JSONObject;
import org.quartz.*;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.queues.QueuesManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnRoomDeletedJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        Object roomIdObj = dataMap.get("roomId");
        Object userIdObj = dataMap.get("userId");
        try (Connection conn = MySQLConnector.getMySQLConnection()){
            int roomId = (int) roomIdObj;
            int userId = (int) userIdObj;
            QueuesManager.roomDeleted(roomId);
            EventManager.dispatchEvent(new JSONObject(), roomId, userId, EventTypes.ROOM_DELETED);
            String query = String.format("DELETE FROM rooms WHERE id=%d", roomId);
            conn.createStatement().executeUpdate(query);
            query = String.format("DELETE FROM roomtimeinfo WHERE roomId=%d AND userId=%d", roomId, userId);
            conn.createStatement().execute(query);
        } catch (SQLException | ClassNotFoundException throwables) {
            Logger.getLogger("Orders").log(Level.WARNING, "Quartz couldn't access to MySQL");
        }
    }
}
