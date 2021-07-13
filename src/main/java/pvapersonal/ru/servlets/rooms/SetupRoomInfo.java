package pvapersonal.ru.servlets.rooms;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.eventsutils.RoomState;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.quartzjobs.OnRoomDeletedJob;
import pvapersonal.ru.quartzjobs.OnRoomEndedJob;
import pvapersonal.ru.quartzjobs.OnRoomSearchParticipiants;
import pvapersonal.ru.quartzjobs.OnRoomStartJob;
import pvapersonal.ru.wallet.TransactionTypes;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/setuproom")
public class SetupRoomInfo extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        long now = TimeManager.now();
        String startSt = req.getParameter("start");
        String endSt = req.getParameter("end");
        String roomIdSt = req.getParameter("roomId");
        String key = req.getParameter("key");
        String comment = req.getParameter("comment");
        try {
            Long payVal = (long)(Double.parseDouble(req.getParameter("payVal")) * 100);
            TransactionTypes type = TransactionTypes.valueOf(Integer.parseInt(req.getParameter("payType")));
            if (type == TransactionTypes.ADMIN_PAYOUT) {
                resp.setStatus(422);
                response.put("msg", "Invalid value for 'payType'");
            } else {
                if (key == null || startSt == null || endSt == null || roomIdSt == null) {
                    resp.setStatus(422);
                    //Means app_error
                    response.put("code", 1);
                    response.put("msg", "Missing parameter 'key', 'roomId', 'start' or 'end'");

                } else {
                    try (Connection conn = MySQLConnector.getMySQLConnection()) {
                        Long start = Long.valueOf(startSt);
                        Long end = Long.valueOf(endSt);
                        int roomId = Integer.parseInt(roomIdSt);
                        if (start < now || end < now || end < start) {
                            resp.setStatus(422);
                            //Means that time is incorrect
                            response.put("code", 2);
                            response.put("msg", "Invalid values for 'start' or 'end'");
                        } else {
                            if (AccessKeyStore.isKeyValid(key)) {
                                AccessKeyStore.updateTimeForKey(key);
                                Integer userId = AccessKeyStore.getUserIdByKey(key);
                                if (AccessKeyStore.isAdmin(userId)) {
                                    String query = String.format("SELECT * FROM rooms WHERE id=%d AND admin=%d", roomId, userId);
                                    if (conn.createStatement().executeQuery(query).next()) {
                                        query = String.format("SELECT * FROM roomtimeinfo WHERE roomId=%d AND userId=%d;", roomId, userId);
                                        ResultSet pr = conn.createStatement().executeQuery(query);
                                        if (!pr.next()) {
                                            if(comment == null) {
                                                query = String.format("INSERT INTO roomtimeinfo (roomId, start, end, userId, transVal, transType, stateCompleted) VALUES (%d, " +
                                                        "%d, %d, %d, %d, %d, %d)", roomId, start, end, userId, payVal, type.paymentType, 0);
                                            }else{
                                                query = String.format("INSERT INTO roomtimeinfo (roomId, start, end, userId, transVal, transType, stateCompleted, comment) VALUES (%d, " +
                                                        "%d, %d, %d, %d, %d, %d, '%s')", roomId, start, end, userId, payVal, type.paymentType, 0, comment);
                                            }
                                            conn.createStatement().execute(query);
                                            JSONObject data = new JSONObject();
                                            data.put("status", RoomState.WAIT);
                                            data.put("start", start);
                                            data.put("transType", type.paymentType);
                                            data.put("transVal", payVal);
                                            if(comment!=null) {
                                                data.put("comment", comment);
                                            }
                                            data.put("end", end);
                                            EventManager.dispatchEvent(data, roomId, userId, EventTypes.ROOM_STATUS_CHANGE);
                                            JobDataMap map = new JobDataMap();
                                            map.put("roomId", roomId);
                                            map.put("userId", userId);
                                            map.put("start", start);
                                            map.put("end", end);
                                            JobDetail onStart = JobBuilder.newJob(OnRoomStartJob.class)
                                                    .withIdentity("startRoomJob", "roomId" + roomId)
                                                    .usingJobData(map)
                                                    .build();

                                            JobDetail onEnd = JobBuilder.newJob(OnRoomEndedJob.class)
                                                    .withIdentity("endRoomJob", "roomId" + roomId)
                                                    .usingJobData(map)
                                                    .build();
                                            JobDetail onDelete = JobBuilder.newJob(OnRoomDeletedJob.class)
                                                    .withIdentity("deleteRoomJob", "roomId" + roomId)
                                                    .usingJobData(map)
                                                    .build();
                                            TimeManager.RoomLifeRegistrator registrator = new TimeManager.RoomLifeRegistrator(userId,
                                                    roomId, start, end, onStart, onEnd, onDelete);
                                            registrator.register();
                                            JobDataMap mapQueue = new JobDataMap();
                                            mapQueue.put("start", start);
                                            mapQueue.put("roomId", roomId);
                                            JobDetail notifyRooms = JobBuilder.newJob(OnRoomSearchParticipiants.class)
                                                    .withIdentity("notifyRoom", "roomId" + roomId)
                                                    .usingJobData(mapQueue)
                                                    .build();
                                            TimeManager.RoomLifeNotifier notifier = new TimeManager.RoomLifeNotifier(roomId,
                                                    start, notifyRooms);
                                            notifier.register();
                                            response.put("msg", "Success!");
                                        } else {
                                            resp.setStatus(201);
                                            response.put("msg", "Room already set up");
                                        }
                                    } else {
                                        resp.setStatus(401);
                                        response.put("msg", "You are not an admin of room or room doesnt exists");
                                    }
                                } else {
                                    resp.setStatus(401);
                                    response.put("msg", "You are not an admin!");
                                }
                            } else {
                                resp.setStatus(403);
                                response.put("msg", "Access key expired");
                            }
                        }
                    } catch (SQLException | ClassNotFoundException | SchedulerException throwables) {
                        resp.setStatus(500);
                        response.put("msg", "Database is not available, try later");
                    }
                }
            }
        }catch (IllegalArgumentException | NullPointerException e){
            resp.setStatus(422);
            response.put("msg", "Invalid value for 'payType' or error while casting");
        }
        printWriter.println(response.toString());
        printWriter.close();
    }
}
