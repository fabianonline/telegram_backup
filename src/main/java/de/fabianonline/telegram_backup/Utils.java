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
import java.util.concurrent.TimeUnit;
import com.google.gson.*;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import de.fabianonline.telegram_backup.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.badoualy.telegram.tl.core.TLBytes;
import java.lang.reflect.Type;

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
	
	public static String anonymize(String str) {
		if (!CommandLineOptions.cmd_anonymize) return str;
		return str.replaceAll("[0-9]", "1").replaceAll("[A-Z]", "A").replaceAll("[a-z]", "a") + " (ANONYMIZED)";
	}
	
	public static Gson getGson() {
		return new GsonBuilder()
			.registerTypeAdapter(TLBytes.class, new TLBytesSerializer())
			.create();
	}
}
	
class TLBytesSerializer implements JsonSerializer<TLBytes> {
	public JsonElement serialize(TLBytes bytes, Type typeOfSrc, JsonSerializationContext ctx) {
		return new JsonNull();
	}
}
