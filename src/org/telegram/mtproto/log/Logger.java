package org.telegram.mtproto.log;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 3:54
 */
public class Logger {

    public static final boolean LOG_THREADS = true;
    public static final boolean LOG_IGNORED = true;
    public static final boolean LOG_PING = true;

    private static LogInterface logInterface;

    public static void registerInterface(LogInterface logInterface) {
        Logger.logInterface = logInterface;
    }

    public static void w(String tag, String message) {
        if (logInterface != null) {
            logInterface.w(tag, message);
        } else {
            System.out.println(tag + ":" + message);
        }
    }

    public static void d(String tag, String message) {
        if (logInterface != null) {
            logInterface.d(tag, message);
        } else {
            System.out.println(tag + ":" + message);
        }
    }

    public static void e(String tag, Throwable t) {
        if (logInterface != null) {
            logInterface.e(tag, t);
        } else {
            t.printStackTrace();
        }
    }
}
