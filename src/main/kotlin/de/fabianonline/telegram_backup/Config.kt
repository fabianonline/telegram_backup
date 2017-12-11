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

import java.io.File
import java.io.IOException
import java.io.FileInputStream
import java.util.Properties

object Config {
    val APP_ID = 32860
    val APP_HASH = "16e4ff955cd0adfc058f95ca564f562d"
    val APP_MODEL = "Desktop"
    val APP_SYSVER = "1.0"
    val APP_APPVER: String
    val APP_LANG = "en"

    var FILE_BASE = System.getProperty("user.home") + File.separatorChar + ".telegram_backup"
    val FILE_NAME_AUTH_KEY = "auth.dat"
    val FILE_NAME_DC = "dc.dat"
    val FILE_NAME_DB = "database.sqlite"
    val FILE_NAME_DB_BACKUP = "database.version_%d.backup.sqlite"
    val FILE_FILES_BASE = "files"
    val FILE_STICKER_BASE = "stickers"

    var DELAY_AFTER_GET_MESSAGES: Long = 400
    var DELAY_AFTER_GET_FILE: Long = 100
    var GET_MESSAGES_BATCH_SIZE = 200

    var RENAMING_MAX_TRIES = 5
    var RENAMING_DELAY: Long = 1000

    val SECRET_GMAPS = "AIzaSyBEtUDhCQKEH6i2Mn1GAiQ9M_tLN0vxHIs"

    init {
        val p = Properties()
        try {
            p.load(Config::class.java.getResourceAsStream("/build.properties"))
            APP_APPVER = p.getProperty("version")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }
}

