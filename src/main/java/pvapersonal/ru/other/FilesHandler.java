package pvapersonal.ru.other;

import pvapersonal.ru.utils.Utils;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FilesHandler {

    private static File ROOT;

    public static void init() {
        ROOT = new File(System.getProperty("user.dir").toString());
        ROOT = new File(ROOT, "/home/orders-images");
        ROOT.mkdirs();
        Logger.getLogger("Orders").log(Level.INFO, "You're looking for " + ROOT.getAbsolutePath());
    }

    public static AbstractMap.SimpleEntry<Integer, String> loadFile(InputStream loadFile, String ext) throws IOException, SQLException, ClassNotFoundException {
        String randString = Utils.generateRandomString(8);
        File imageFile = new File(ROOT, randString + "." + ext);
        while(imageFile.exists()){
            randString = Utils.generateRandomString(8);
            imageFile = new File(ROOT,  randString + "." + ext);
        }
        if(!imageFile.createNewFile()){
            throw new IOException("Couldn't create file, check rights");
        }
        byte[] buffer = new byte[loadFile.available()];
        loadFile.read(buffer);
        OutputStream out = new FileOutputStream(imageFile);
        Logger.getLogger("Orders").log(Level.INFO, "Just wrote file into " + imageFile.getAbsolutePath());
        out.write(buffer);
        out.flush();
        out.close();
        Connection conn = MySQLConnector.getMySQLConnection();
        String query = "INSERT INTO files (fileName) VALUES ('" + randString + "." + ext + "');";
        conn.createStatement().execute(query);
        ResultSet set = conn.createStatement().executeQuery("SELECT LAST_INSERT_ID();");
        set.next();
        Integer createdInt = set.getInt(1);
        conn.close();
        AbstractMap.SimpleEntry<Integer, String> an = new AbstractMap.SimpleEntry<Integer, String>(createdInt, (randString + "." + ext));
        return an;
    }

    //Returns file if exists and null if not
    public static File getFile(String fileName) {
        File checkFile = new File(ROOT, fileName);
        Logger.getLogger("Orders").log(Level.INFO, "I'm looking for " + checkFile.getAbsolutePath());
        if(checkFile.exists()){
            return checkFile;
        }else{
            return null;
        }
    }


}
