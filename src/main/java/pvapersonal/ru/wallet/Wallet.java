package pvapersonal.ru.wallet;

import pvapersonal.ru.other.MySQLConnector;
import pvapersonal.ru.other.TimeManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Wallet {

    private static final Object syncKey = new Object();

    public static List<TransactionItem> getPayHistory(int userId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = String.format("SELECT * FROM `wallet` WHERE userId=%d ORDER BY transDate;", userId);
            ResultSet set = conn.createStatement().executeQuery(query);
            List<TransactionItem> transactions = new ArrayList<>();
            while (set.next()){
                TransactionItem item = new TransactionItem(set);
                transactions.add(item);
            }
            Collections.sort(transactions, new Comparator<TransactionItem>() {
                @Override
                public int compare(TransactionItem o1, TransactionItem o2) {
                    return o2.transDate.compareTo(o1.transDate);
                }
            });
            return transactions;
        }
    }

    public static void addTransactionItem(int userId, TransactionTypes type, long transactionVal, int roomId, String initiatorName) throws SQLException, ClassNotFoundException {
        synchronized (syncKey){
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                String query = String.format("INSERT INTO wallet (userId, transType, transVal, roomId, initiatorName," +
                        " transDate) VALUES (%d, %d, %d, %d, '%s', %d)", userId, type.paymentType, transactionVal, roomId,
                        initiatorName, TimeManager.now());
                conn.createStatement().execute(query);
            }
        }
    }

    public static long getWalletCount(int userId) throws SQLException, ClassNotFoundException {
        try(Connection conn = MySQLConnector.getMySQLConnection()){
            String query = String.format("SELECT * FROM `wallet` WHERE userId=%d ORDER BY transDate;", userId);
            ResultSet set = conn.createStatement().executeQuery(query);
            long count = 0;
            while (set.next()){
                TransactionItem item = new TransactionItem(set);
                if(item.transType != TransactionTypes.ADMIN_PAYOUT){
                    count += item.transVal;
                }else{
                    count -= item.transVal;
                }
            }
            return count;
        }
    }

    public static void payout(int userId, long payVal) throws SQLException, ClassNotFoundException {
        synchronized (syncKey) {
            try (Connection conn = MySQLConnector.getMySQLConnection()) {
                long wallCount = getWalletCount(userId);
                payVal = Math.min(payVal, wallCount);
                String query = String.format("INSERT INTO wallet (userId, transType, transVal, roomId, initiatorName, transDate) VALUES" +
                        " (%d, %d, %d, %d, %s, %d)", userId, 2, payVal, 0, null, TimeManager.now());
                conn.createStatement().execute(query);
            }
        }
    }
}
