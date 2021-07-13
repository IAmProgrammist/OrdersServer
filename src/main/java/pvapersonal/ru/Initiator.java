package pvapersonal.ru;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.quartz.SchedulerException;
import pvapersonal.ru.eventsutils.EventManager;
import pvapersonal.ru.other.*;
import pvapersonal.ru.utils.Utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class Initiator implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContextListener.super.contextInitialized(sce);
        try {
            EventManager.init();
            Logger.getLogger("Orders").log(Level.INFO, "Encoding is " + Charset.defaultCharset().toString());
            FilesHandler.init();
            MySQLConnector.init("localhost", "/*Your database name here*/",
                    "/*Your username here*/", "/*Your password here*/");
            TimeManager.init();
            AccessKeyStore.init();
        } catch (ClassNotFoundException e) {
            Logger.getLogger("Orders").log(Level.WARNING, "Не найден MySQL Connector. Работа сервера" +
                    "невозможна.");
            Utils.CAN_RUN = false;
        } catch (SQLException throwables) {
            Logger.getLogger("Orders").log(Level.WARNING, "Не удалось подключить базу данных. Работа сервера" +
                    "невозможна.");
            Utils.CAN_RUN = false;
        } catch (IOException e) {
            Logger.getLogger("Orders").log(Level.WARNING, "Не удалось загрузить время. Работа сервера" +
                    "невозможна.");
            Utils.CAN_RUN = false;
        } catch (SchedulerException e) {
            Logger.getLogger("Orders").log(Level.WARNING, "Не удалось загрузить Quartz. Работа сервера" +
                    "невозможна.");
            Utils.CAN_RUN = false;
        }
    }
}

