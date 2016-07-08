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

import java.io.File;

public class Config {
	public static final int APP_ID = 32860;
	public static final String APP_HASH = "16e4ff955cd0adfc058f95ca564f562d";
    public static final String APP_MODEL = "Desktop";
    public static final String APP_SYSVER = "1.0";
    public static final String APP_APPVER = "1.0.3";
    public static final String APP_LANG = "en";
    
    public static String FILE_BASE = System.getProperty("user.home") + File.separatorChar + ".telegram_backup";
    public static final String FILE_NAME_AUTH_KEY = "auth.dat";
    public static final String FILE_NAME_DC = "dc.dat";
    public static final String FILE_NAME_DB = "database.sqlite";
    public static final String FILE_NAME_DB_BACKUP = "database.version_%d.backup.sqlite";
    public static final String FILE_FILES_BASE = "files";
    public static final String FILE_STICKER_BASE = "stickers";

    public static final int FILE_DOWNLOAD_BLOCK_SIZE = 10*1024*1024;
    
    public static int DELAY_AFTER_GET_MESSAGES = 200;
    public static int DELAY_AFTER_GET_FILE = 1000;
    
    public static final String SECRET_GMAPS = "AIzaSyBEtUDhCQKEH6i2Mn1GAiQ9M_tLN0vxHIs";
}

