package pvapersonal.ru.eventsutils;

public abstract class QueueUpdateDispatcher extends UpdateDispatcher{
    public QueueUpdateDispatcher(int initiator) {
        super(-2, initiator);
    }
}
