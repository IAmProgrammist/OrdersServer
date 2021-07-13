package pvapersonal.ru.eventsutils;

public enum EventTypes {
    ROOM_CREATED(true),
    USER_JOINED(true),
    USER_EXITED(true),
    ROOM_INFO_EDITED(true),
    ROOM_DELETED(true),
    ROOM_STATUS_CHANGE(true),
    QUEUE_MESSAGE_CREATE(true),
    QUEUE_TOGGLE(true),
    QUEUE_USER_ADDED(true),
    QUEUE_USER_REMOVED(true);
    private final boolean isGeneral;
    EventTypes(boolean isGeneral){
        this.isGeneral = isGeneral;
    }
    public boolean isGeneral(){
        return isGeneral;
    }
}
