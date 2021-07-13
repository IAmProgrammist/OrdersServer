package pvapersonal.ru.quartzjobs;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.queues.QueuesManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnRoomSearchParticipiants implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        Object roomIdObj = dataMap.get("roomId");
        Object startTime = dataMap.get("start");
        int roomId = (int) roomIdObj;
        long start = (long) startTime;
        try(Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = String.format("SELECT id FROM rooms WHERE id=%d;", roomId);
            if(conn.createStatement().executeQuery(query).next()) {
                if (TimeManager.now() < start) {
                    QueuesManager.dispatchRoomWaiting(roomId);
                }
            }
        } catch (SQLException | ClassNotFoundException throwables) {
            Logger.getLogger("Orders").log(Level.WARNING, "RoomSearchParticipiants не смог получить доступ к " +
                    "базам данных.");
        }
    }
}
