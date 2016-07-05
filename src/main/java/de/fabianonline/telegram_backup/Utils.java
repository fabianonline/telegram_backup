package de.fabianonline.telegram_backup;

import java.io.File;
import java.util.List;
import java.util.Vector;

public class Utils {
	static Vector<String> getAccounts() {
		Vector<String> accounts = new Vector<String>();
		File folder = new File(Config.FILE_BASE);
		File[] files = folder.listFiles();
		if (files != null) for (File f : files) {
			if (f.isDirectory() && f.getName().startsWith("+")) {
				accounts.add(f.getName());
			}
		}
		return accounts;
	}
}
