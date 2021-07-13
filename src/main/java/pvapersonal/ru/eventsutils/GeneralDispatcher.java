package pvapersonal.ru.eventsutils;

public abstract class GeneralDispatcher extends UpdateDispatcher {
    public GeneralDispatcher(int initiator) {
        super(-1, initiator);
    }
}
