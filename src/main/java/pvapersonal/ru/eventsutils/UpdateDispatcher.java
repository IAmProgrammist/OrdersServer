package pvapersonal.ru.eventsutils;

import pvapersonal.ru.other.TimeManager;
import pvapersonal.ru.models.Update;

public abstract class UpdateDispatcher {
    public int rId;
    public int initiator;
    public long timeMark;

    public UpdateDispatcher(int rId, int initiator) {
        this.rId = rId;
        this.initiator = initiator;
        timeMark = TimeManager.now();
    }

    public abstract void dispatchUpdate(Update update);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateDispatcher that = (UpdateDispatcher) o;
        return rId == that.rId && initiator == that.initiator && timeMark == that.timeMark;
    }

    @Override
    public int hashCode() {
        return 31*(rId + initiator + (int)timeMark);
    }
}
