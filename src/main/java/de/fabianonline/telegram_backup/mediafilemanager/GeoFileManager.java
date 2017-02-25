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

package de.fabianonline.telegram_backup.mediafilemanager;

import de.fabianonline.telegram_backup.UserManager;
import de.fabianonline.telegram_backup.Database;
import de.fabianonline.telegram_backup.StickerConverter;
import de.fabianonline.telegram_backup.DownloadProgressInterface;
import de.fabianonline.telegram_backup.DownloadManager;
import de.fabianonline.telegram_backup.Config;
import de.fabianonline.telegram_backup.models.Message;

import com.github.badoualy.telegram.api.TelegramClient;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

public class GeoFileManager extends AbstractMediaFileManager {
	protected double lat;
	protected double lon;

	public GeoFileManager(Message msg) {
		super(msg);
		JsonObject geo = media.getAsJsonObject("geo");
		if (geo.getAsJsonPrimitive("_constructor").getAsString().startsWith("geoPoint#")) {
			lat = geo.getAsJsonPrimitive("lat").getAsDouble();
			lon = geo.getAsJsonPrimitive("_long").getAsDouble();
		} else {
			isEmpty = true;
		}
	}

	public int getSize() {
		File f = new File(getTargetPathAndFilename());
		if (f.isFile()) return (int)f.length();

		// We don't know the size, so we just guess.
		return 100000;
	}

	public String getExtension() { return "png"; }

	public void download(TelegramClient c) throws IOException {
		String url = "https://maps.googleapis.com/maps/api/staticmap?" +
			"center=" + lat + "," + lon + "&" +
			"zoom=14&size=300x150&scale=2&format=png&" +
			"key=" + Config.SECRET_GMAPS;
		DownloadManager.downloadExternalFile(getTargetPathAndFilename(), url);
	}

	public String getLetter() { return "g"; }
	public String getName() { return "geo"; }
	public String getDescription() { return "Geolocation"; }
}
