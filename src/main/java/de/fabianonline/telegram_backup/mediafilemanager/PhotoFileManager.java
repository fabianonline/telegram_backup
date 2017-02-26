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
import de.fabianonline.telegram_backup.models.Message;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

public class PhotoFileManager extends AbstractMediaFileManager {
	private JsonObject photo;
	private JsonObject size = null;
	public PhotoFileManager(Message msg) {
		super(msg);
		photo = media.getAsJsonObject("photo");

		if (photo.getAsJsonPrimitive("_constructor").getAsString().startsWith("messageMediaPhoto#")) {
			int w = 0;
			int h = 0;
			for (JsonElement e : photo.getAsJsonArray("sizes")) {
				JsonObject s = e.getAsJsonObject();
				if (size==null || (s.getAsJsonPrimitive("w").getAsInt()>w && s.getAsJsonPrimitive("h").getAsInt()>h)) {
					size = s;
					w = s.getAsJsonPrimitive("w").getAsInt();
					h = s.getAsJsonPrimitive("h").getAsInt();
				}
			}
			if (size==null) throw new RuntimeException("Could not find a size for the photo.");
		} else {
			throw new RuntimeException("Unexpected photo type: " + photo.getAsJsonPrimitive("_constructor").getAsString());
		}
	}

	public int getSize() {
		if (size!=null) return size.getAsJsonPrimitive("size").getAsInt();
		return 0;
	}

	public String getExtension() { return "jpg"; }

	public void download(TelegramClient client) throws RpcErrorException, IOException, TimeoutException {
		if (isEmpty || size==null) return;
		JsonObject loc = size.getAsJsonObject("location");
		DownloadManager.downloadFile(client, getTargetPathAndFilename(), getSize(),
			loc.getAsJsonPrimitive("dcId").getAsInt(),
			loc.getAsJsonPrimitive("volumeId").getAsLong(),
			loc.getAsJsonPrimitive("localId").getAsInt(),
			loc.getAsJsonPrimitive("secret").getAsLong());
	}

	public String getLetter() { return "p"; }
	public String getName() { return "photo"; }
	public String getDescription() { return "Photo"; }
}
