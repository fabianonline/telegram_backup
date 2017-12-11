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

package de.fabianonline.telegram_backup

import com.github.badoualy.telegram.tl.TLContext
import com.github.badoualy.telegram.tl.api.account.TLPassword
import com.github.badoualy.telegram.tl.core.TLMethod
import com.github.badoualy.telegram.tl.core.TLObject

import java.io.IOException
import java.io.InputStream

import com.github.badoualy.telegram.tl.StreamUtils.readTLObject

class TLRequestAccountGetPasswordWithCurrentSalt : TLMethod<TLPassword>() {
    private val _constructor = "account.getPassword#548a30f5"
    override fun getConstructorId(): Int = CONSTRUCTOR_ID

    @Throws(IOException::class)
    override fun deserializeResponse(stream: InputStream, context: TLContext): TLPassword {
        val response = (readTLObject(stream, context) ?: throw IOException("Unable to parse response")) as? TLPassword ?: throw IOException("Incorrect response type, expected getClass().getCanonicalName(), found response.getClass().getCanonicalName()")
        return response as TLPassword
    }

    override fun toString(): String {
        return _constructor
    }

    companion object {
        val CONSTRUCTOR_ID = 0x548a30f5
    }
}
