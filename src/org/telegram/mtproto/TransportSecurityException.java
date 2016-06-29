package org.telegram.mtproto;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 6:44
 */
public class TransportSecurityException extends IOException {
    public TransportSecurityException() {
    }

    public TransportSecurityException(String s) {
        super(s);
    }

    public TransportSecurityException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public TransportSecurityException(Throwable throwable) {
        super(throwable);
    }
}
