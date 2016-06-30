package de.fabianonline.telegram_backup;

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;

class DownloadManager {
	UserManager user;
	Database db;
	
	public DownloadManager(UserManager u) {
		this.user = u;
		this.db = new Database(u);
	}
	
	public void downloadMessages() {
		System.out.println("downloading messages... not.");
	}
}
