package pvapersonal.ru.queues;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class QueueItem implements Comparable<QueueItem>{
    public int userId;
    public String accessKey;
    public List<Integer> notifiedRoomList;
    public Map<Integer, Boolean> keyboard;
    public boolean enabled;
    public Long registartionDate;

    public QueueItem(int userId, String accessKey, List<Integer> notifiedRoomList, boolean enabled, Long registartionDate) {
        this.accessKey = accessKey;
        this.userId = userId;
        this.notifiedRoomList = notifiedRoomList;
        this.enabled = enabled;
        this.registartionDate = registartionDate;
        this.keyboard = new ConcurrentHashMap<>();
    }

    public void setEnabled(boolean enabled){
        this.enabled = enabled;
    }

    public boolean isNotified(int roomId){
        for(int a : notifiedRoomList){
            if(roomId == a){
                return true;
            }
        }
        return false;
    }

    public void addRoomNotify(Integer notifyRoomId){
        if(!notifiedRoomList.contains(notifyRoomId)){
            notifiedRoomList.add(notifyRoomId);
        }
    }

    public boolean checkKeyboard(int roomId){
        if(keyboard.containsKey(roomId)){
            return keyboard.get(roomId);
        }
        return true;
    }

    public void checkKeyboard(int roomId, boolean isCheck){
        keyboard.put(roomId, isCheck);
    }

    @Override
    public int compareTo(QueueItem o) {
        if(enabled && !o.enabled){
            return -1;
        }
        if(!enabled && o.enabled){
            return 1;
        }
        return registartionDate.compareTo(o.registartionDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueItem queueItem = (QueueItem) o;
        return userId == queueItem.userId;
    }
}
