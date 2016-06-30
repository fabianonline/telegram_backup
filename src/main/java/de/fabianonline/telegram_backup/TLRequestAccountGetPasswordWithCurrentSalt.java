package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.tl.TLContext;
import com.github.badoualy.telegram.tl.api.account.TLPassword;
import com.github.badoualy.telegram.tl.core.TLMethod;
import com.github.badoualy.telegram.tl.core.TLObject;

import java.io.IOException;
import java.io.InputStream;

import static com.github.badoualy.telegram.tl.StreamUtils.readTLObject;

public class TLRequestAccountGetPasswordWithCurrentSalt extends TLMethod<TLPassword> {
    public static final int CONSTRUCTOR_ID = 0x548a30f5;
    private final String _constructor = "account.getPassword#548a30f5";
    public TLRequestAccountGetPasswordWithCurrentSalt() {}
    public TLPassword deserializeResponse(InputStream stream, TLContext context) throws IOException {
        final TLObject response = readTLObject(stream, context);
        if (response == null) {
            throw new IOException("Unable to parse response");
        }
        if (!(response instanceof TLPassword)) {
            throw new IOException("Incorrect response type, expected getClass().getCanonicalName(), found response.getClass().getCanonicalName()");
        }
        return (TLPassword) response;
    }
    public String toString() { return _constructor; }
    public int getConstructorId() { return CONSTRUCTOR_ID; }
}
