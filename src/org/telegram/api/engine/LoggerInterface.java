package org.telegram.api.engine;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 11.11.13
 * Time: 4:48
 */
public interface LoggerInterface {
    void w(String tag, String message);

    void d(String tag, String message);

    void e(String tag, Throwable t);
}
