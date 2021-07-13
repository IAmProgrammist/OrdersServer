package pvapersonal.ru.eventsutils;

import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.models.Update;
import pvapersonal.ru.utils.RoomUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EventManager {
    private static final Object syncKey = new Object();
    private static Map<Integer, List<UpdateDispatcher>> listeners;
    private static Map<Integer, QueueUpdateDispatcher> queueListeners;
    private static final Map<Integer, Object> synchronizers = new ConcurrentHashMap<>();
    private static List<GeneralDispatcher> generalListeners;

    public static void init() {
        listeners = new ConcurrentHashMap<>();
        generalListeners = Collections.synchronizedList(new ArrayList<>());
        queueListeners = new ConcurrentHashMap<>();
    }

    public static void addListener(int room, UpdateDispatcher dispatcher) {
        synchronized (syncKey) {
            List<UpdateDispatcher> dispatchers = listeners.getOrDefault(room,
                    Collections.synchronizedList(new ArrayList<>()));
            dispatchers.add(dispatcher);
            listeners.put(room, dispatchers);
        }
    }

    public static void addListener(int userId, QueueUpdateDispatcher dispatcher) {
        synchronized (syncKey) {
            queueListeners.put(userId, dispatcher);
        }
    }

    public static void addListener(GeneralDispatcher dispatcher) {
        synchronized (syncKey) {
            generalListeners.add(dispatcher);
        }
    }

    public static void updateGeneralEvents(int userId) throws SQLException, ClassNotFoundException {
        Object synchronizeKey = synchronizers.computeIfAbsent(userId, k -> new Object());
        synchronized (synchronizeKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                String query = "SELECT eventcount FROM generalevents ORDER BY eventcount DESC LIMIT 1;";
                ResultSet set = conn.createStatement().executeQuery(query);
                if (set.next()) {
                    int eventcount = set.getInt("eventcount");
                    query = String.format("UPDATE `roomsusersgeneral` SET lastUpdateId=%d WHERE userId=%d;",
                            eventcount, userId);
                    conn.createStatement().executeUpdate(query);
                }
            }
        }
    }


    public static void updateEvents(int userId, int roomId) throws SQLException, ClassNotFoundException {
        Object synchronizeKey = synchronizers.computeIfAbsent(userId, k -> new Object());
        synchronized (synchronizeKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                String query = String.format("SELECT eventcount FROM events WHERE roomId=%d ORDER BY eventcount " +
                        "DESC LIMIT 1;", roomId);
                ResultSet set = conn.createStatement().executeQuery(query);
                if (set.next()) {
                    int eventcount = set.getInt("eventcount");
                    query = String.format("UPDATE `roomsusers` SET lastUpdateId=%d WHERE userId=%d AND roomId=%d;",
                            eventcount, userId, roomId);
                    conn.createStatement().executeUpdate(query);
                }
            }
        }
    }


    public static Updates findUpdates(int userId, boolean includeGeneral, List<Integer> observes) throws SQLException, ClassNotFoundException {
        Object synchronizeKey = synchronizers.computeIfAbsent(userId, k -> new Object());
        synchronized (synchronizeKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                Updates updates = new Updates();
                String query = String.format("SELECT roomId FROM roomsusers WHERE userId=%d;", userId);
                ResultSet roomIds = conn.createStatement().executeQuery(query);
                updates.setShouldStartAsync(true);
                JSONArray finalUpdates = new JSONArray();
                while (roomIds.next()) {
                    int roomId = roomIds.getInt("roomId");
                    updates.roomIDList.add(roomId);
                    query = String.format("SELECT roomsusers.lastUpdateId, events.eventcount FROM roomsusers LEFT JOIN" +
                            " events ON roomsusers.roomId=events.roomId WHERE roomsusers.roomId=%d AND " +
                            "roomsusers.userId=%d ORDER BY events.eventcount DESC LIMIT 1;", roomId, userId);
                    ResultSet set = conn.createStatement().executeQuery(query);
                    if (set.next()) {
                        long eventcount = set.getLong("eventcount");
                        long lastUpdateId = set.getLong("lastUpdateId");
                        if (lastUpdateId != eventcount) {
                            updates.setShouldStartAsync(false);
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
                updates.setFinalUpdates(finalUpdates);
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
                        maxUpdateQueue = Math.max(maxUpdateQueue, queueSet.getLong("eventcount"));
                        query = String.format("SELECT queueevents.*, rooms.name, users.surname, users.middlename, " +
                                        "users.name FROM queueevents LEFT JOIN rooms ON rooms.id=queueevents.roomId LEFT " +
                                        "JOIN users ON users.id=queueevents.target WHERE queueevents.id=%d LIMIT 1;",
                                queueSet.getInt("id"));
                        ResultSet newResultSet = conn.createStatement().executeQuery(query);
                        if (newResultSet.next()) {
                            updates.setShouldStartAsync(false);
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
                updates.setQueueUpdates(queueUpdates);
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
                        updates.setShouldStartAsync(false);
                        query = String.format("SELECT generalevents.*, rooms.name, users.surname, users.middlename, " +
                                "users.name FROM generalevents LEFT JOIN rooms ON rooms.id=generalevents.roomId LEFT " +
                                "JOIN users ON users.id=generalevents.initiator WHERE generalevents.id=%d;", generalId);
                        ResultSet sett = conn.createStatement().executeQuery(query);

                        while (sett.next()) {
                            Update update = new Update(sett);
                            if (observes.size() == 0 || observes.contains(update.roomId)) {
                                generalUpdates.put(update.toJSONString(userId));
                            }
                        }
                    }
                    updates.setGeneralUpdates(generalUpdates);
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
                }
                return updates;
            }
        }
    }


    public static class Updates {
        JSONArray finalUpdates = null;
        JSONArray queueUpdates = null;
        JSONArray generalUpdates = null;
        boolean shouldStartAsync = true;
        List<Integer> roomIDList = new ArrayList<>();

        public JSONArray getFinalUpdates() {
            return finalUpdates;
        }

        public void setFinalUpdates(JSONArray finalUpdates) {
            this.finalUpdates = finalUpdates;
        }

        public JSONArray getQueueUpdates() {
            return queueUpdates;
        }

        public void setQueueUpdates(JSONArray queueUpdates) {
            this.queueUpdates = queueUpdates;
        }

        public JSONArray getGeneralUpdates() {
            return generalUpdates;
        }

        public void setGeneralUpdates(JSONArray generalUpdates) {
            this.generalUpdates = generalUpdates;
        }

        public boolean isShouldStartAsync() {
            return shouldStartAsync;
        }

        public void setShouldStartAsync(boolean shouldStartAsync) {
            this.shouldStartAsync = shouldStartAsync;
        }

        public List<Integer> getRoomIDList() {
            return roomIDList;
        }

        public void setRoomIDList(List<Integer> roomIDList) {
            this.roomIDList = roomIDList;
        }

    }

    public static void dispatchEvent(JSONObject eventData, int roomId, int userId,
                                     EventTypes eventType) throws SQLException, ClassNotFoundException {
        dispatchEvent(eventData, roomId, userId, eventType, new EventDispatchedListener() {
            @Override
            public void dispatched(long eventcount) {
            }

            @Override
            public void error(Throwable err) {

            }
        });
    }

    public static void dispatchEvent(JSONObject eventData, int roomId, int userId,
                                     EventTypes eventType, EventDispatchedListener listener) throws SQLException, ClassNotFoundException {
        Thread t = new Thread(() -> {
            synchronized (syncKey) {
                try (Connection conn = MySQLConnector.getMySQLConnection()) {
                    String query;
                    ResultSet setEvent;
                    long eventCount = 0;
                    ResultSet sett;
                    ResultSet settt;
                    int eventId;
                    long eventDate = TimeManager.now();
                    Update update = null;
                    if (roomId > 0) {
                        query = String.format("SELECT eventcount FROM events WHERE roomId=%d ORDER BY eventcount" +
                                " DESC LIMIT 1;", roomId);
                        setEvent = conn.createStatement().executeQuery(query);
                        eventCount = 1;
                        if (setEvent.next()) {
                            eventCount = setEvent.getLong("eventcount") + 1;
                        }
                        query = String.format("INSERT INTO events (eventcount, eventName, eventData, " +
                                        "eventDate, roomId, initiator) VALUES (%d, '%s', '%s', %d, %d, %d)",
                                eventCount, eventType, eventData.toString(), eventDate, roomId, userId);
                        conn.createStatement().execute(query);
                        query = "SELECT LAST_INSERT_ID();";
                        settt = conn.createStatement().executeQuery(query);
                        settt.next();
                        eventId = settt.getInt(1);
                        query = String.format("SELECT events.*, rooms.name, users.surname, users.middlename, " +
                                "users.name FROM events LEFT JOIN rooms ON rooms.id=events.roomId LEFT " +
                                "JOIN users ON users.id=events.initiator WHERE events.id=%d;", eventId);
                        sett = conn.createStatement().executeQuery(query);
                        if (sett.next()) {
                            update = new Update(sett);
                        } else {
                            throw new SQLException();
                        }
                    }
                    if (eventType.isGeneral()) {
                        query = "SELECT eventcount FROM `generalevents` ORDER BY eventcount DESC LIMIT 1;";
                        setEvent = conn.createStatement().executeQuery(query);
                        eventCount = 1;
                        if (setEvent.next()) {
                            eventCount = setEvent.getLong("eventcount") + 1;
                        }
                        query = String.format("INSERT INTO `generalevents` (eventcount, eventName, eventData, " +
                                        "eventDate, roomId, initiator) VALUES (%d, '%s', '%s', %d, %d, %d)",
                                eventCount, eventType, eventData.toString(), eventDate, roomId, userId);
                        conn.createStatement().execute(query);
                        query = "SELECT LAST_INSERT_ID();";
                        settt = conn.createStatement().executeQuery(query);
                        settt.next();
                        eventId = settt.getInt(1);
                        query = String.format("SELECT `generalevents`.*, rooms.name, users.surname, users.middlename, " +
                                "users.name FROM `generalevents` LEFT JOIN rooms ON rooms.id=`generalevents`.roomId LEFT " +
                                "JOIN users ON users.id=`generalevents`.initiator WHERE `generalevents`.id=%d;", eventId);
                        sett = conn.createStatement().executeQuery(query);
                        Update generalUpdate;
                        if (sett.next()) {
                            generalUpdate = new Update(sett);
                        } else {
                            throw new SQLException();
                        }
                        for (GeneralDispatcher dispatcher : generalListeners) {
                            JSONObject evData = generalUpdate.additionalData;
                            if (evData.has("creatorId")) {
                                evData.put("isAdmin", dispatcher.initiator == evData.getInt("creatorId"));
                                evData.put("partitionType", RoomUtils.participiantType(roomId, dispatcher.initiator));
                            }
                            generalUpdate.additionalData = evData;
                            dispatcher.dispatchUpdate(generalUpdate);
                        }
                    }
                    List<UpdateDispatcher> listenerList = listeners.getOrDefault(roomId, new ArrayList<UpdateDispatcher>());
                    for (UpdateDispatcher a : listenerList) {
                        a.dispatchUpdate(update);
                    }
                    listeners.put(roomId, Collections.synchronizedList(new ArrayList<UpdateDispatcher>()));
                    listener.dispatched(eventCount);
                } catch (Exception err) {
                    listener.error(err);
                }
            }
        });
        t.start();
    }

    public static void removeDispatcher(UpdateDispatcher dispatcher) {
        synchronized (syncKey) {
            if (dispatcher.rId == -2) {
                queueListeners.remove(dispatcher.initiator);
            } else if (dispatcher.rId == -1) {
                generalListeners.remove(dispatcher);
            } else {
                List<UpdateDispatcher> dispatchers = listeners.getOrDefault(dispatcher.rId, Collections.synchronizedList(new ArrayList<>()));
                dispatchers.remove(dispatcher);
                listeners.put(dispatcher.rId, dispatchers);
            }
        }
    }

    public static void dispatchQueueEvent(int roomId, int userId) throws SQLException, ClassNotFoundException {
        synchronized (syncKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                QueueUpdateDispatcher dispatcher = queueListeners.getOrDefault(userId, null);
                String query = String.format("SELECT eventcount FROM `queueevents` WHERE roomId=%d AND target=%d ORDER BY eventcount" +
                        " DESC LIMIT 1;", roomId, userId);
                ResultSet setEvent = conn.createStatement().executeQuery(query);
                long eventCount = 1;
                if (setEvent.next()) {
                    eventCount = setEvent.getLong("eventcount") + 1;
                }
                long eventDate = TimeManager.now();
                query = String.format("INSERT INTO `queueevents` (eventcount, eventName, eventData, " +
                                "eventDate, roomId, target) VALUES (%d, '%s', '%s', %d, %d, %d)",
                        eventCount, EventTypes.QUEUE_MESSAGE_CREATE, new JSONObject(), eventDate, roomId, userId);
                conn.createStatement().execute(query);
                query = "SELECT LAST_INSERT_ID();";
                ResultSet settt = conn.createStatement().executeQuery(query);
                settt.next();
                //We need here only room name btw
                int eventId = settt.getInt(1);
                query = String.format("SELECT `queueevents`.*, rooms.name, users.surname, users.middlename, " +
                        "users.name FROM `queueevents` LEFT JOIN rooms ON rooms.id=`queueevents`.roomId LEFT " +
                        "JOIN users ON users.id=`queueevents`.target WHERE `queueevents`.id=%d;", eventId);
                ResultSet sett = conn.createStatement().executeQuery(query);
                Update update;
                if (sett.next()) {
                    update = new Update(sett);
                } else {
                    throw new SQLException();
                }
                if (dispatcher != null) {
                    dispatcher.dispatchUpdate(update);
                }
            }
        }
    }
}
