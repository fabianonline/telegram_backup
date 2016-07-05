/* Telegram_Backup
 * Copyright (C) 2016 Fabian Schlenz
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. */

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
