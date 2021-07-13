package pvapersonal.ru.servlets;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.*;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.models.Update;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {"/updates"}, asyncSupported = true)
public class UpdatesSearcher extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        resp.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        PrintWriter printWriter = resp.getWriter();
        boolean includeGeneral = Boolean.parseBoolean(req.getParameter("includeGeneral"));
        if (req.getParameter("key") == null) {
            resp.setStatus(422);
            response.put("msg", "Missing parameter 'key' or 'roomId'");
        } else {
            List<Integer> observes = new ArrayList<>();
            for (int i = 0; i < Integer.MAX_VALUE; i++) {
                if (req.getParameter("observe" + i) != null) {
                    observes.add(i, Integer.parseInt(req.getParameter("observe" + i)));
                } else {
                    break;
                }
            }
            String accessKey = req.getParameter("key");
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                if (AccessKeyStore.isKeyValid(accessKey)) {
                    AccessKeyStore.updateTimeForKey(accessKey);
                    Integer userId = AccessKeyStore.getUserIdByKey(accessKey);
                    EventManager.Updates updates = EventManager.findUpdates(userId, includeGeneral, observes);
                    if(updates.getFinalUpdates() != null){
                        response.put("updates", updates.getFinalUpdates());
                    }
                    if(updates.getQueueUpdates() != null){
                        response.put("queueUpdates", updates.getQueueUpdates());
                    }
                    if(updates.getGeneralUpdates() != null){
                        response.put("generalUpdates", updates.getGeneralUpdates());
                    }
                    /*String query = String.format("SELECT roomId FROM roomsusers WHERE userId=%d;", userId);
                    ResultSet roomIds = conn.createStatement().executeQuery(query);
                    Logger.getLogger("Orders").log(Level.INFO, "Started to look for updates");
                    boolean shouldStartAsync = true;
                    List<Integer> roomIDList = new ArrayList<>();
                    JSONArray finalUpdates = new JSONArray();
                    while (roomIds.next()) {
                        int roomId = roomIds.getInt("roomId");
                        roomIDList.add(roomId);
                        query = String.format("SELECT roomsusers.lastUpdateId, events.eventcount FROM roomsusers LEFT JOIN" +
                                " events ON roomsusers.roomId=events.roomId WHERE roomsusers.roomId=%d AND " +
                                "roomsusers.userId=%d ORDER BY events.eventcount DESC LIMIT 1;", roomId, userId);
                        ResultSet set = conn.createStatement().executeQuery(query);
                        if (set.next()) {
                            long eventcount = set.getLong("eventcount");
                            long lastUpdateId = set.getLong("lastUpdateId");
                            if (lastUpdateId != eventcount) {
                                shouldStartAsync = false;
                                query = String.format("SELECT events.*, rooms.name, users.surname, users.middlename, " +
                                        "users.name FROM events LEFT JOIN rooms ON rooms.id=events.roomId LEFT " +
                                        "JOIN users ON users.id=events.initiator WHERE events.eventcount>%d AND " +
                                        "events.roomId=%d;", lastUpdateId, roomId);
                                ResultSet sett = conn.createStatement().executeQuery(query);
                                JSONArray resultArray = new JSONArray();
                                while (sett.next()) {
                                    Update update = new Update(sett);
                                    resultArray.put(update.toJSONString(userId));
                                }
                                query = String.format("UPDATE roomsusers SET lastUpdateId=%d WHERE roomId=%d " +
                                        "AND userId=%d;", eventcount, roomId, userId);
                                conn.createStatement().executeUpdate(query);
                                finalUpdates.put(resultArray);
                            }
                        }
                    }
                    response.put("updates", finalUpdates);
                    Logger.getLogger("Orders").log(Level.INFO, "Found updates for " + finalUpdates.length() +
                            " rooms");
                    Map<Integer, Long> updateMap = new HashMap<>();
                    query = "SELECT * FROM queueevents WHERE target=" + userId;
                    JSONArray queueUpdates = new JSONArray();
                    ResultSet queueSet = conn.createStatement().executeQuery(query);
                    while (queueSet.next()) {
                        int roomId = queueSet.getInt("roomId");
                        long eventcount = Math.max(queueSet.getInt("eventcount"),
                                updateMap.getOrDefault(roomId, -1L));
                        updateMap.put(roomId, eventcount);
                    }
                    for (Map.Entry<Integer, Long> entry : updateMap.entrySet()) {
                        JSONArray roomUpdates = new JSONArray();
                        long lastUpdateIddd = 0;
                        query = String.format("SELECT * FROM `roomsusersqueue` WHERE roomId=%d AND userId=%d ORDER BY lastUpdateId LIMIT 1;", entry.getKey(), userId);
                        ResultSet sre = conn.createStatement().executeQuery(query);
                        if (sre.next()) {
                            lastUpdateIddd = sre.getLong("lastUpdateId");
                        }
                        query = String.format("SELECT * FROM queueevents WHERE eventcount>%d AND target=%d AND " +
                                "roomId=%d;", lastUpdateIddd, userId, entry.getKey());
                        queueSet = conn.createStatement().executeQuery(query);
                        long maxUpdateQueue = entry.getValue();
                        while (queueSet.next()) {
                            Logger.getLogger("Orders").log(Level.INFO, "Sync general queue update! Type: " + EventTypes.QUEUE_MESSAGE_CREATE);
                            maxUpdateQueue = Math.max(maxUpdateQueue, queueSet.getLong("eventcount"));
                            query = String.format("SELECT queueevents.*, rooms.name, users.surname, users.middlename, " +
                                            "users.name FROM queueevents LEFT JOIN rooms ON rooms.id=queueevents.roomId LEFT " +
                                            "JOIN users ON users.id=queueevents.target WHERE queueevents.id=%d LIMIT 1;",
                                    queueSet.getInt("id"));
                            ResultSet newResultSet = conn.createStatement().executeQuery(query);
                            if (newResultSet.next()) {
                                shouldStartAsync = false;
                                Update newUpdate = new Update(newResultSet);
                                roomUpdates.put(newUpdate.toJSONString(userId));
                            }
                        }
                        if (roomUpdates.length() != 0) {
                            queueUpdates.put(roomUpdates);
                            query = String.format("SELECT id FROM roomsusersqueue WHERE roomId=%d AND userId=%d;", entry.getKey(),
                                    userId);
                            if (conn.createStatement().executeQuery(query).next()) {
                                query = String.format("UPDATE `roomsusersqueue` SET lastUpdateId=%d WHERE roomId=%d AND " +
                                        "userId=%d;", maxUpdateQueue, entry.getKey(), userId);
                            } else {
                                query = String.format("INSERT INTO roomsusersqueue (lastUpdateId, roomId, userId) VALUES" +
                                        " (%d, %d, %d)", maxUpdateQueue, entry.getKey(), userId);
                            }
                            conn.createStatement().executeUpdate(query);
                        }
                    }
                    response.put("queueUpdates", queueUpdates);
                    Logger.getLogger("Orders").log(Level.INFO, "Found " + queueUpdates.length() + " rooms for queue updates");
                    if (includeGeneral) {
                        int lastUpdateIdd = 0;
                        query = "SELECT * FROM roomsusersgeneral WHERE userId=" + userId + ";";
                        ResultSet resultSet = conn.createStatement().executeQuery(query);
                        while (resultSet.next()) {
                            lastUpdateIdd = resultSet.getInt("lastUpdateId");
                        }
                        query = String.format("SELECT * FROM generalevents WHERE eventcount>%d;", lastUpdateIdd);
                        ResultSet set = conn.createStatement().executeQuery(query);
                        JSONArray generalUpdates = new JSONArray();
                        int maxUpdate = -1;
                        while (set.next()) {
                            int generalId = set.getInt("id");
                            int lastUpdateId = set.getInt("eventcount");
                            maxUpdate = Math.max(maxUpdate, lastUpdateId);
                            shouldStartAsync = false;
                            query = String.format("SELECT generalevents.*, rooms.name, users.surname, users.middlename, " +
                                    "users.name FROM generalevents LEFT JOIN rooms ON rooms.id=generalevents.roomId LEFT " +
                                    "JOIN users ON users.id=generalevents.initiator WHERE generalevents.id=%d;", generalId);
                            ResultSet sett = conn.createStatement().executeQuery(query);

                            while (sett.next()) {
                                Logger.getLogger("Orders").log(Level.INFO, "Sync update! Type: " + sett.getString("eventName"));
                                Update update = new Update(sett);
                                if (observes.size() == 0 || observes.contains(update.roomId)) {
                                    generalUpdates.put(update.toJSONString(userId));
                                }
                            }
                        }
                        response.put("generalUpdates", generalUpdates);
                        Logger.getLogger("Orders").log(Level.INFO, "Ayo some general updates here: " + generalUpdates.length());
                        if (maxUpdate != -1) {

                            query = "SELECT * FROM roomsusersgeneral WHERE userId=" + userId + ";";
                            ResultSet rs = conn.createStatement().executeQuery(query);
                            if (rs.next()) {
                                query = String.format("UPDATE roomsusersgeneral SET lastUpdateId=%d WHERE userId=%d;",
                                        maxUpdate, userId);
                            } else {
                                query = String.format("INSERT INTO roomsusersgeneral (userId, lastUpdateId) VALUES (%d, %d);",
                                        userId, maxUpdate);
                            }
                            conn.createStatement().executeUpdate(query);
                        }
                    }*/
                    if (updates.isShouldStartAsync()) {
                        List<Integer> roomIDList = updates.getRoomIDList();
                        response.put("isAsync", true);
                        final AsyncContext asyncContext = req.startAsync(req, resp);
                        List<UpdateDispatcher> dispatchers = new ArrayList<>();
                        final boolean[] asyncDone = {false};
                        GeneralDispatcher generalDispatcher = new GeneralDispatcher(userId) {
                            @Override
                            public void dispatchUpdate(Update update) {
                                try (Connection conn = MySQLConnector.getMySQLConnection()) {
                                    if (!asyncDone[0]) {
                                        JSONObject resultResponse = new JSONObject();
                                        PrintWriter out = asyncContext.getResponse().getWriter();
                                        JSONArray genUpdates = new JSONArray();
                                        genUpdates.put(update.toJSONString(userId));
                                        resultResponse.put("generalUpdates", genUpdates);
                                        resultResponse.put("isAsync", true);
                                        String query = String.format("UPDATE roomsusersgeneral " +
                                                "SET lastUpdateId=%d WHERE userId=%d", update.eventcount, userId);
                                        conn.createStatement().execute(query);
                                        if(observes.size() == 0 || observes.contains(update.roomId)) {
                                            out.write(resultResponse.toString());
                                            out.flush();
                                            out.close();
                                            asyncContext.complete();
                                        }
                                    }
                                } catch (IOException | SQLException | ClassNotFoundException e) {
                                    Logger.getLogger("Orders").log(Level.WARNING, "Не удалось отправить обновление!");
                                }

                            }
                        };
                        QueueUpdateDispatcher queueUpdateDispatcher = new QueueUpdateDispatcher(userId) {
                            //TODO: We will start it anyways
                            @Override
                            public void dispatchUpdate(Update update) {
                                try (Connection conn = MySQLConnector.getMySQLConnection()) {
                                    if (!asyncDone[0]) {
                                        JSONObject resultResponse = new JSONObject();
                                        PrintWriter out = asyncContext.getResponse().getWriter();
                                        JSONArray queueUpdate = new JSONArray();
                                        JSONArray roomUpdates = new JSONArray();
                                        roomUpdates.put(update.toJSONString(userId));
                                        queueUpdate.put(roomUpdates);
                                        resultResponse.put("queueUpdates", queueUpdate);
                                        resultResponse.put("isAsync", true);
                                        String query = String.format("SELECT * FROM orders.roomsusersqueue WHERE roomId=%d AND userId=%d;",
                                                update.roomId, update.initiator);
                                        ResultSet rs = conn.createStatement().executeQuery(query);
                                        if (rs.next()) {
                                            long evcount = rs.getLong("eventcount");
                                            if (evcount < update.eventcount) {
                                                query = String.format("UPDATE `roomsusersqueue` SET lastUpdateId=%d WHERE roomId=%d AND " +
                                                        "userId=%d;", update.eventcount, update.roomId, update.initiator);
                                            } else {
                                                query = null;
                                            }
                                        } else {
                                            query = String.format("INSERT INTO roomsusersqueue (lastUpdateId, roomId, userId) VALUES" +
                                                    " (%d, %d, %d)", update.eventcount, update.roomId, update.initiator);
                                        }
                                        if (query != null) {
                                            conn.createStatement().executeUpdate(query);
                                        }

                                        out.write(resultResponse.toString());
                                        out.flush();
                                        out.close();
                                        asyncContext.complete();
                                    }
                                } catch (IOException | SQLException | ClassNotFoundException e) {
                                    Logger.getLogger("Orders").log(Level.WARNING, "Не удалось отправить обновление!");
                                }
                            }
                        };
                        EventManager.addListener(userId, queueUpdateDispatcher);
                        if (includeGeneral) {
                            EventManager.addListener(generalDispatcher);
                        }
                        for (int roomId : roomIDList) {
                            UpdateDispatcher dispatcher = new UpdateDispatcher(roomId, userId) {
                                @Override
                                public void dispatchUpdate(Update update) {

                                    try (Connection conn = MySQLConnector.getMySQLConnection();
                                         PrintWriter out = asyncContext.getResponse().getWriter()) {
                                        if (!asyncDone[0]) {
                                            JSONObject resultResponse = new JSONObject();
                                            JSONArray updates = new JSONArray();
                                            JSONArray roomUpdate = new JSONArray();
                                            roomUpdate.put(update.toJSONString(userId));
                                            updates.put(roomUpdate);
                                            resultResponse.put("updates", updates);
                                            resultResponse.put("msg", "Success!");
                                            String query = String.format("UPDATE roomsusers " +
                                                            "SET lastUpdateId=%d WHERE roomId=%d AND userId=%d", update.eventcount,
                                                    roomId, userId);
                                            conn.createStatement().execute(query);
                                            out.write(resultResponse.toString());
                                            out.flush();
                                            out.close();
                                            asyncContext.complete();
                                        }
                                    } catch (IOException | SQLException | ClassNotFoundException e) {
                                        Logger.getLogger("Orders").log(Level.WARNING, "Не удалось отправить обновление!");
                                    }

                                }
                            };
                            dispatchers.add(dispatcher);
                            EventManager.addListener(roomId, dispatcher);
                        }
                        asyncContext.addListener(new AsyncListener() {
                            @Override
                            public void onComplete(AsyncEvent asyncEvent) throws IOException {
                                asyncDone[0] = true;
                                for (UpdateDispatcher a : dispatchers) {
                                    EventManager.removeDispatcher(a);
                                }
                                EventManager.removeDispatcher(generalDispatcher);
                                EventManager.removeDispatcher(queueUpdateDispatcher);
                            }

                            @Override
                            public void onTimeout(AsyncEvent asyncEvent) throws IOException {
                                // :/
                            }

                            @Override
                            public void onError(AsyncEvent asyncEvent) throws IOException {
                                // :/
                            }

                            @Override
                            public void onStartAsync(AsyncEvent asyncEvent) throws IOException {
                                // :/
                            }
                        });
                    }
                } else {
                    resp.setStatus(403);
                    response.put("msg", "Access key expired");
                }

            } catch (SQLException | ClassNotFoundException throwables) {
                resp.setStatus(502);
                response.put("msg", "Database is not available, try later");
            }
        }
        if (!req.isAsyncStarted()) {
            response.put("isAsync", false);
            printWriter.println(response.toString());
            printWriter.close();
        }
    }
}
