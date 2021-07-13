package pvapersonal.ru.utils;

import jakarta.servlet.http.Part;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static final Long KEY_WORK_TIME = 2700000L;
    public static final Long TIME_ROOM_LIVES_AFTER = 7200000L;
    public static final Long USERS_LIFE_DAYS = 60L;
    public static final int NOTIFY_IN_MINS = 5;
    public static boolean CAN_RUN = true;

    public static String generateRandomString(int length){
        String resultString = "";
        for(int i = 0; i < length; i++) {
            double rand = Math.random();
            int minBound;
            int maxBound;
            if (rand <= 0.33f) {
                minBound = 48;
                maxBound = 57;
            } else if (rand <= 0.66f) {
                minBound = 65;
                maxBound = 90;
            } else {
                minBound = 97;
                maxBound = 122;
            }
            int randomLimitedInt = minBound + (int)
                    (Math.random() * (maxBound - minBound + 1));
            resultString += ((char)randomLimitedInt);
        }
        return resultString;
    }

    public static String getExtensionByStringHandling(File file) {
        return file.getName().split("\\.")[file.getName().split("\\.").length-1];
    }

    public static String getFileName(final Part part) {
        final String partHeader = part.getHeader("content-disposition");
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortMapByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public static long GCD(long a, long b) {
        if (b==0) return a;
        return GCD(b,a%b);
    }
}
