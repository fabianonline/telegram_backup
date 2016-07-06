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
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
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
	
	static void obeyFloodWaitException(RpcErrorException e) throws RpcErrorException {
		if (e==null || ! e.getTag().startsWith("420: FLOOD_WAIT_")) return;
		
		int delay = Integer.parseInt(e.getTag().substring(16));
		int minutes = (delay/60)+1;
		int wait = (minutes / 5) * 5 + 5;
		System.out.println("");
		System.out.println(
			"Telegram complained about us (okay, me) making too many requests in too short time by\n" +
			"sending us \"" + e.getTag() + "\" as an error. So we now have to wait a bit. Telegram\n" +
			"asked us to wait for " + delay + " seconds, which is about " + minutes + " minutes.\n" +
			"I'm adding a few minutes to let the API recover, so we are going to wait for " + wait + " mins.\n" +
			"\n" +
			"So I'm going to do just that for now. If you don't want to wait, you can quit by pressing\n" +
			"Ctrl+C. You can restart me at any time and I will just continue to download your\n" +
			"messages and media. But be advised that just restarting me is not going to change\n" +
			"the fact that Telegram won't talk to me until then.");
		try { Thread.sleep(wait * 60 * 1000); } catch(InterruptedException e2) {}
		System.out.println("");
	}
}
