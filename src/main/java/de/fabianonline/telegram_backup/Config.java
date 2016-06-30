package de.fabianonline.telegram_backup;

import java.io.File;

class Config {
	public static final int APP_ID = 32860;
	public static final String APP_HASH = "16e4ff955cd0adfc058f95ca564f562d";
    public static final String APP_MODEL = "Desktop";
    public static final String APP_SYSVER = "1.0";
    public static final String APP_APPVER = "0.1";
    public static final String APP_LANG = "en";

    public static final File FILE_AUTH_KEY = new File("auth.dat");
    public static final File FILE_DC = new File("dc.dat");
    public static final File FILE_SALT = new File("salt.dat");
}

