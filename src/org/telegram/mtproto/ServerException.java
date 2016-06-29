package org.telegram.mtproto;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 6:47
 */
public class ServerException extends IOException {
    public ServerException() {
    }

    public ServerException(String s) {
        super(s);
    }

    public ServerException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ServerException(Throwable throwable) {
        super(throwable);
    }
}
