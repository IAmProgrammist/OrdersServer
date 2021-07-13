package pvapersonal.ru.wallet;

public enum TransactionTypes {
    ADMIN_PAYOUT(2),
    FULL_ROOM_PAYMENT(1),
    PER_HOUR_PAYMENT(0);
    public final int paymentType;
    TransactionTypes(int paymentType){
        this.paymentType = paymentType;
    }

    public static TransactionTypes valueOf(int type){
        for(TransactionTypes a : TransactionTypes.values()){
            if(a.paymentType == type){
                return a;
            }
        }
        throw new IllegalArgumentException();
    }
}
