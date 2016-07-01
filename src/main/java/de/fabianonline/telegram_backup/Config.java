package de.fabianonline.telegram_backup;

import java.io.File;

class Config {
	public static final int APP_ID = 32860;
	public static final String APP_HASH = "16e4ff955cd0adfc058f95ca564f562d";
    public static final String APP_MODEL = "Desktop";
    public static final String APP_SYSVER = "1.0";
    public static final String APP_APPVER = "0.1";
    public static final String APP_LANG = "en";
    
    public static final String FILE_BASE = "data";
    public static final String FILE_NAME_AUTH_KEY = "auth.dat";
    public static final String FILE_NAME_DC = "dc.dat";
    public static final String FILE_NAME_DB = "database.sqlite";
    public static final String FILE_FILES_BASE = "files";
    public static final String FILE_STICKER_BASE = "stickers";
    
    public static final int FILE_DOWNLOAD_BLOCK_SIZE = 1024*1024;
    
    public static final int DELAY_AFTER_GET_FILE = 750;
}

