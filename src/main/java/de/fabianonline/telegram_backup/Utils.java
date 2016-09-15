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
import com.google.gson.*;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import de.fabianonline.telegram_backup.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	public static final int VERSIONS_EQUAL = 0;
	public static final int VERSION_1_NEWER = 1;
	public static final int VERSION_2_NEWER = 2;
	
	private static final Logger logger = (Logger)LoggerFactory.getLogger(Utils.class);
	
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
		if (e==null || e.getCode()!=420) return;
		
		int delay = e.getTagInteger();
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
	
	static Version getNewestVersion() {
		try {
			String data_url = "https://api.github.com/repos/fabianonline/telegram_backup/releases";
			logger.debug("Requesting current release info from {}", data_url);
			String json = IOUtils.toString(new URL(data_url));
			JsonParser parser = new JsonParser();
			JsonElement root_elm = parser.parse(json);
			if (root_elm.isJsonArray()) {
				JsonArray root = root_elm.getAsJsonArray();
				JsonObject newest_version = null;
				for (JsonElement e : root) if (e.isJsonObject()) {
					JsonObject version = e.getAsJsonObject();
					if (version.getAsJsonPrimitive("prerelease").getAsBoolean() == false) {
						newest_version = version;
						break;
					}
				}
				if (newest_version == null) return null;
				String new_v = newest_version.getAsJsonPrimitive("tag_name").getAsString();
				logger.debug("Found current release version {}", new_v);
				String cur_v = Config.APP_APPVER;
				
				int result = compareVersions(cur_v, new_v);
				
				return new Version(new_v, newest_version.getAsJsonPrimitive("html_url").getAsString(), newest_version.getAsJsonPrimitive("body").getAsString(), result == VERSION_2_NEWER);
			}
			return null;
		} catch(Exception e) {
			return null;
		}
	}
	
	public static int compareVersions(String v1, String v2) {
		logger.debug("Comparing versions {} and {}.", v1, v2);
		if (v1.equals(v2)) return VERSIONS_EQUAL;
				
		String[] v1_p = v1.split("-", 2);
		String[] v2_p = v2.split("-", 2);
		
		logger.trace("Parts to compare without suffixes: {} and {}.", v1_p[0], v2_p[0]);
		
		String[] v1_p2 = v1_p[0].split("\\.");
		String[] v2_p2 = v2_p[0].split("\\.");
		
		logger.trace("Length of the parts without suffixes: {} and {}.", v1_p2.length, v2_p2.length);
		
		int i;
		for (i=0; i<v1_p2.length && i<v2_p2.length; i++) {
			int i_1 = Integer.parseInt(v1_p2[i]);
			int i_2 = Integer.parseInt(v2_p2[i]);
			logger.trace("Comparing parts: {} and {}.", i_1, i_2);
			if (i_1 > i_2) {
				logger.debug("v1 is newer");
				return VERSION_1_NEWER;
			} else if (i_2 > i_1) {
				logger.debug("v2 is newer");
				return VERSION_2_NEWER;
			}
		}
		logger.trace("At least one of the versions has run out of parts.");
		if (v1_p2.length > v2_p2.length) {
			logger.debug("v1 is longer, so it is newer");
			return VERSION_1_NEWER;
		} else if (v2_p2.length > v1_p2.length) {
			logger.debug("v2 is longer, so it is newer");
			return VERSION_2_NEWER;
		}
		
		// startsWith
		if (v1_p.length>1 && v2_p.length==1) {
			logger.debug("v1 has a suffix, v2 not.");
			if (v1_p[1].startsWith("pre")) {
				logger.debug("v1 is a pre version, so v1 is newer");
				return VERSION_2_NEWER;
			} else {
				return VERSION_1_NEWER;
			}
		} else if (v1_p.length==1 && v2_p.length>1) {
			logger.debug("v1 has no suffix, but v2 has");
			if (v2_p[1].startsWith("pre")) {
				logger.debug("v2 is a pre version, so v1 is better");
				return VERSION_1_NEWER;
			} else {
				return VERSION_2_NEWER;
			}
		} else if (v1_p.length>1 && v2_p.length>1) {
			logger.debug("Both have a suffix");
			if (v1_p[1].startsWith("pre") && !v2_p[1].startsWith("pre")) {
				logger.debug("v1 is a 'pre' version, v2 not.");
				return VERSION_2_NEWER;
			} else if (!v1_p[1].startsWith("pre") && v2_p[1].startsWith("pre")) {
				logger.debug("v2 is a 'pre' version, v2 not.");
				return VERSION_1_NEWER;
			}
			return VERSIONS_EQUAL;
		}
		logger.debug("We couldn't find a real difference, so we're assuming the versions are equal-ish.");
		return VERSIONS_EQUAL;
	}
}
