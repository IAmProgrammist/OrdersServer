package pvapersonal.ru.eventsutils;

public abstract class EventDispatchedListener {
    public abstract void dispatched(long eventcount);
    public abstract void error(Throwable err);
}
