package pvapersonal.ru.queues;

import org.json.JSONArray;
import org.json.JSONObject;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.eventsutils.EventTypes;
import pvapersonal.ru.other.AccessKeyStore;
import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.utils.RoomUtils;
import pvapersonal.ru.utils.UsersUtils;
import pvapersonal.ru.utils.Utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class QueuesManager {
    private static Map<Integer, QueueItem> queue = new HashMap<>();
    private static final Object syncKey = new Object();

    public static void dispatchRoomWaiting(int roomId) {
        synchronized (syncKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                Map<Integer, QueueItem> toNotify = queue.entrySet().stream().filter(a -> {
                    QueueItem queueItem = a.getValue();
                    try {
                        return AccessKeyStore.isKeyValid(queueItem.accessKey);
                    } catch (Exception throwables) {
                        return false;
                    }
                }).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                toNotify = Utils.sortMapByValue(toNotify);
                String query = "SELECT name, maxMembers FROM rooms WHERE id=" + roomId + " LIMIT 1;";
                ResultSet set = conn.createStatement().executeQuery(query);
                if (set.next()) {
                    int maxMembers = set.getInt("maxMembers");
                    int count = RoomUtils.getAllUsersBesidesAdmin(roomId).size();
                    int membersToJoin = maxMembers - count;
                    int notified = 0;
                    for (Map.Entry<Integer, QueueItem> item : toNotify.entrySet()) {
                        QueueItem queueItem = item.getValue();
                        UsersUtils usersUtils = new UsersUtils(item.getKey());
                        if (queueItem.enabled && !queueItem.isNotified(roomId) && !usersUtils.isMember(roomId)
                        && !usersUtils.isAdmin(roomId)) {
                            EventManager.dispatchQueueEvent(roomId, queueItem.userId);
                            queueItem.addRoomNotify(roomId);
                            queueItem.checkKeyboard(roomId, true);
                            notified++;
                        }
                        if (notified >= membersToJoin) {
                            break;
                        }
                    }
                } else {
                    throw new SQLException();
                }
            } catch (SQLException | ClassNotFoundException throwables) {
                Logger.getLogger("Orders").log(Level.WARNING, "QueueManager не смогу получить доступ к базам данных");
            }
        }
    }

    public static void queueAdded(String key) {
        synchronized (syncKey) {
            try {
                int userId = AccessKeyStore.getUserIdByKey(key);
                QueueItem queueItem = queue.getOrDefault(userId, null);
                if (queueItem == null) {
                    long createDate = TimeManager.now();
                    queueItem = new QueueItem(userId, key, new ArrayList<>(),
                            true, createDate);
                    JSONObject data = new JSONObject();
                    data.put("toggle", true);
                    data.put("exactDate", createDate);
                    EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_USER_ADDED);
                }else{
                    JSONObject data = new JSONObject();
                    queueItem.registartionDate = TimeManager.now();
                    queueItem.accessKey = key;
                    queueItem.setEnabled(true);
                    data.put("toggle", true);
                    data.put("exactDate", queueItem.registartionDate);
                    queueItem.accessKey = key;
                    EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_TOGGLE);
                }
                queue.put(userId, queueItem);
            } catch (SQLException | ClassNotFoundException throwables) {
                Logger.getLogger("Orders").log(Level.WARNING, "QueueManager не смогу получить доступ к базам данных");
            }
        }
    }

    public static void queueRemoved(String key) {
        synchronized (syncKey) {
            try {
                int userId = AccessKeyStore.getUserIdByKey(key);
                QueueItem queueItem = queue.getOrDefault(userId, null);
                if (queueItem == null) {
                	long createDate = TimeManager.now();
                    queueItem = new QueueItem(userId, key, new ArrayList<>(),
                            false, createDate);
                    JSONObject data = new JSONObject();
                    data.put("toggle", false);
                    data.put("exactDate", createDate);
                    EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_USER_ADDED);
                }else{
                	JSONObject data = new JSONObject();
                	data.put("toggle", false);
                	EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_TOGGLE);
                    queueItem.setEnabled(false);
                    queueItem.accessKey = key;
				}
                queue.put(userId, queueItem);
            } catch (SQLException | ClassNotFoundException throwables) {
                Logger.getLogger("Orders").log(Level.WARNING, "QueueManager не смогу получить доступ к базам данных");
            }
        }
    }

    public static boolean toggleQueue(String key) throws SQLException, ClassNotFoundException {
        synchronized (syncKey) {
            int userId = AccessKeyStore.getUserIdByKey(key);
            QueueItem queueItem = queue.getOrDefault(userId, null);
            if (queueItem == null) {
                long exactTime = TimeManager.now();
                queueItem = new QueueItem(userId, key, new ArrayList<>(),
                        false, exactTime);
                JSONObject data = new JSONObject();
                data.put("exactDate", exactTime);
				data.put("toggle", false);
                EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_USER_ADDED);
                queue.put(userId, queueItem);
                return false;
            }else {
                if (queueItem.enabled) {
                    queueRemoved(key);
                    return false;
                } else {
                    queueAdded(key);
                    return true;
                }
            }
        }
    }

    public static JSONArray getQueue(int userId) throws SQLException, ClassNotFoundException {
        JSONArray result = new JSONArray();
        synchronized (syncKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                for (Map.Entry<Integer, QueueItem> entry : queue.entrySet()) {
                    JSONObject queueItem = new JSONObject();
                    String query = "SELECT name, surname, middlename FROM users WHERE id=" + entry.getKey() + " LIMIT 1;";
                    ResultSet set = conn.createStatement().executeQuery(query);
                    String userName = "Неизвестно";
                    if (set.next()) {
                        userName = set.getString("middlename") == null ?
                                String.format("%s %s", set.getString("surname"), set.getString("name")) :
                                String.format("%s %s %s", set.getString("surname"), set.getString("name"),
                                        set.getString("middlename"));
                    }
                    queueItem.put("userName", userName);
                    queueItem.put("waiting", entry.getValue().enabled);
                    queueItem.put("userId", entry.getKey());
                    queueItem.put("self", entry.getKey() == userId);
                    queueItem.put("date", entry.getValue().registartionDate);
                    result.put(queueItem);
                }
            }
        }
        return result;
    }

    public static void roomVisited(Integer userId, Integer roomId) {
        synchronized (syncKey){
            if(queue.containsKey(userId)){
                QueueItem item = queue.get(userId);
                item.addRoomNotify(roomId);
                queue.put(userId, item);
            }
        }
    }

    public static void roomDeleted(Integer roomId){
        synchronized (syncKey){
            for(Map.Entry<Integer, QueueItem> itemEntry : queue.entrySet()){
                QueueItem it = itemEntry.getValue();
                it.notifiedRoomList.removeAll(Collections.singletonList(roomId));
                queue.put(itemEntry.getKey(), it);
            }
        }
    }

    public static boolean shouldShowExpandedKeyboard(int userId, int roomId){
        synchronized (syncKey){
            if(queue.containsKey(userId)){
                return queue.get(userId).checkKeyboard(roomId);
            }else{
                return true;
            }
        }
    }

    public static void keyboardAffected(String key, int roomId) throws SQLException, ClassNotFoundException {
        synchronized (syncKey){
            int userId = AccessKeyStore.getUserIdByKey(key);
            QueueItem queueItem = queue.getOrDefault(userId, null);
            if(queueItem == null) {
                long exactTime = TimeManager.now();
                queueItem = new QueueItem(userId, key, new ArrayList<>(),
                        false, exactTime);
                JSONObject data = new JSONObject();
                data.put("exactDate", exactTime);
                data.put("toggle", false);
                EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_USER_ADDED);
            }
            queueItem.checkKeyboard(roomId, false);
            queue.put(userId, queueItem);
        }
    }

    public static void cancelled(String key, int roomId) throws SQLException, ClassNotFoundException {
        synchronized (syncKey){
            int userId = AccessKeyStore.getUserIdByKey(key);
            QueueItem queueItem = queue.getOrDefault(userId, null);
            if(queueItem != null){
                if(queueItem.keyboard.getOrDefault(roomId, false)){
                    queueItem.checkKeyboard(roomId, false);
                    singleRoomNotify(roomId);
                }
            }else{
                long exactTime = TimeManager.now();
                queueItem = new QueueItem(userId, key, new ArrayList<>(),
                        false, exactTime);
                JSONObject data = new JSONObject();
                data.put("exactDate", exactTime);
                data.put("toggle", false);
                queueItem.checkKeyboard(roomId, false);
                EventManager.dispatchEvent(data, 0, userId, EventTypes.QUEUE_USER_ADDED);
            }
            queue.put(userId, queueItem);
        }
    }

    private static void singleRoomNotify(int roomId) {
        synchronized (syncKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                Map<Integer, QueueItem> toNotify = queue.entrySet().stream().filter(a -> {
                    QueueItem queueItem = a.getValue();
                    try {
                        return AccessKeyStore.isKeyValid(queueItem.accessKey);
                    } catch (Exception throwables) {
                        return false;
                    }
                }).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                toNotify = Utils.sortMapByValue(toNotify);
                String query = "SELECT name, maxMembers FROM rooms WHERE id=" + roomId + " LIMIT 1;";
                ResultSet set = conn.createStatement().executeQuery(query);
                if (set.next()) {
                    int maxMembers = set.getInt("maxMembers");
                    int count = RoomUtils.getAllUsersBesidesAdmin(roomId).size();
                    int membersToJoin = Math.min(maxMembers - count, 1);
                    int notified = 0;
                    for (Map.Entry<Integer, QueueItem> item : toNotify.entrySet()) {
                        QueueItem queueItem = item.getValue();
                        UsersUtils usersUtils = new UsersUtils(item.getKey());
                        if (queueItem.enabled && !queueItem.isNotified(roomId) && !usersUtils.isMember(roomId)
                                && !usersUtils.isAdmin(roomId)) {
                            EventManager.dispatchQueueEvent(roomId, queueItem.userId);
                            queueItem.addRoomNotify(roomId);
                            queueItem.checkKeyboard(roomId, true);
                            notified++;
                        }
                        if (notified >= membersToJoin) {
                            break;
                        }
                    }
                } else {
                    throw new SQLException();
                }
            } catch (SQLException | ClassNotFoundException throwables) {
                Logger.getLogger("Orders").log(Level.WARNING, "QueueManager не смогу получить доступ к базам данных");
            }
        }
    }

    public static void cleanUser(int adminId) {
        synchronized (syncKey){
            queue.remove(adminId);
        }
    }
}
