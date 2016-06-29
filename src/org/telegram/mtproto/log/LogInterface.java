package org.telegram.mtproto.log;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 10.11.13
 * Time: 2:11
 */
public interface LogInterface {
    void w(String tag, String message);

    void d(String tag, String message);

    void e(String tag, Throwable t);
}
