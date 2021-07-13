package pvapersonal.ru.other;

import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import pvapersonal.ru.quartzjobs.OnRoomDeletedJob;
import pvapersonal.ru.quartzjobs.OnRoomEndedJob;
import pvapersonal.ru.quartzjobs.OnRoomStartJob;
import pvapersonal.ru.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TimeManager {

    private static Long delta;
    private static Scheduler scheduler;

    public static void init() throws IOException, SchedulerException, SQLException, ClassNotFoundException {
        Logger.getLogger("Orders").log(Level.INFO, "Не изменяйте время на локальной машине! Это может привести к " +
                "сбоям. Перезагрузите сервер, если время нужно изменить.");
        URL url = new URL("https://yandex.com/time/sync.json");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.connect();
        Long now = System.currentTimeMillis();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        con.disconnect();
        JSONObject response = new JSONObject(content.toString());
        delta = response.getLong("time") - now;

        //Initiating Quartz
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        scheduler = schedulerFactory.getScheduler();
        scheduler.start();
        Logger.getLogger("Orders").log(Level.INFO, "Создаём заново Jobs");
        try (Connection conn = MySQLConnector.getMySQLConnection()) {
            String query = "SELECT * FROM roomtimeinfo";
            ResultSet set = conn.createStatement().executeQuery(query);
            while (set.next()){
                long start = set.getLong("start");
                long end = set.getLong("end");
                int roomId = set.getInt("roomId");
                int userId = set.getInt("userId");
                JobDataMap map = new JobDataMap();
                map.put("roomId", roomId);
                map.put("userId", userId);
                map.put("start", start);
                map.put("end", end);
                JobDetail onStart = JobBuilder.newJob(OnRoomStartJob.class)
                        .withIdentity("startRoomJob", "roomId"+roomId)
                        .usingJobData(map)
                        .build();
                JobDetail onEnd = JobBuilder.newJob(OnRoomEndedJob.class)
                        .withIdentity("endRoomJob", "roomId"+roomId)
                        .usingJobData(map)
                        .build();
                JobDetail onDelete = JobBuilder.newJob(OnRoomDeletedJob.class)
                        .withIdentity("deleteRoomJob", "roomId"+roomId)
                        .usingJobData(map)
                        .build();
                TimeManager.RoomLifeRegistrator registrator = new TimeManager.RoomLifeRegistrator(userId,
                        roomId, start, end, onStart, onEnd, onDelete);
                registrator.register();
            }
        }
    }

    public static Long now(){
        return System.currentTimeMillis() + delta;
    }

    public static class RoomLifeRegistrator{
        public Long startDate;
        public Long endDate;
        public Long deleteDate;
        public JobDetail onStart;
        public JobDetail onEnd;
        public JobDetail onDelete;
        public Integer roomId;
        public Integer userId;

        public RoomLifeRegistrator(int userId, int roomId, Long startDate, Long endDate, JobDetail onStart, JobDetail onEnd, JobDetail onDelete) throws SchedulerException {
            this.startDate = startDate;
            this.endDate = endDate;
            deleteDate = endDate + Utils.TIME_ROOM_LIVES_AFTER;
            this.onStart = onStart;
            this.onEnd = onEnd;
            this.onDelete = onDelete;
            this.roomId = roomId;
            this.userId = userId;
        }

        public void register() throws SchedulerException {
            Long now = now();
            TriggerBuilder<Trigger> startTriggerBuilder = TriggerBuilder
                    .newTrigger()
                    .withIdentity("startRoomTrigger", "roomId"+roomId)
                    .usingJobData("roomId", roomId)
                    .usingJobData("userId", userId)
                    .usingJobData("start", startDate)
                    .usingJobData("end", endDate)
                    .forJob(onStart);
            Trigger startTrigger;
            if(now > startDate){
                startTrigger = startTriggerBuilder
                        .startNow()
                        .build();
            }else{
                startTrigger = startTriggerBuilder
                        .startAt(new Date(startDate))
                        .build();
            }
            scheduler.scheduleJob(onStart, startTrigger);
            Trigger endTrigger;
            TriggerBuilder<Trigger> endTriggerBuilder = TriggerBuilder
                    .newTrigger()
                    .withIdentity("endRoomTrigger", "roomId"+roomId)
                    .startAt(new Date(endDate))
                    .usingJobData("roomId", roomId)
                    .usingJobData("userId", userId)
                    .usingJobData("start", startDate)
                    .usingJobData("end", endDate)
                    .forJob(onEnd);
            if(now > endDate){
                endTrigger = endTriggerBuilder
                        .startNow()
                        .build();
            } else{
                endTrigger = endTriggerBuilder
                        .startAt(new Date(endDate))
                        .build();
            }
            scheduler.scheduleJob(onEnd, endTrigger);
            TriggerBuilder<Trigger> deleteTriggerBuilder = TriggerBuilder
                    .newTrigger()
                    .withIdentity("deleteRoomTrigger", "roomId"+roomId)
                    .usingJobData("roomId", roomId)
                    .usingJobData("userId", userId)
                    .usingJobData("start", startDate)
                    .usingJobData("end", endDate)
                    .forJob(onDelete);
            Trigger deleteTrigger;
            if(now + Utils.TIME_ROOM_LIVES_AFTER > deleteDate){
                deleteTrigger = deleteTriggerBuilder
                        .startNow()
                        .build();
            }else{
                deleteTrigger = deleteTriggerBuilder
                        .startAt(new Date(deleteDate))
                        .build();
            }
            scheduler.scheduleJob(onDelete, deleteTrigger);
        }
    }

    public static class RoomLifeNotifier{
        public Long start;
        public JobDetail job;
        public int roomId;

        public RoomLifeNotifier(int roomId, Long start, JobDetail notifyRooms) {
            this.start = start;
            this.roomId = roomId;
            this.job = notifyRooms;
        }

        public void register() throws SchedulerException {
            SimpleTrigger notifyTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("notifier", "roomId" + roomId)
                    .forJob(job)
                    .withSchedule(
                            SimpleScheduleBuilder
                            .simpleSchedule()
                            .withIntervalInMinutes(Utils.NOTIFY_IN_MINS)
                            .repeatForever()
                    )
                    .startNow()
                    .endAt(new Date(start))
                    .build();
            scheduler.scheduleJob(job, notifyTrigger);
        }
    }
}
