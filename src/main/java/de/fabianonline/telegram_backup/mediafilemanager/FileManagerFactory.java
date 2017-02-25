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
import de.fabianonline.telegram_backup.models.Message;

import com.google.gson.JsonObject;

import com.github.badoualy.telegram.api.TelegramClient;
import com.github.badoualy.telegram.tl.core.TLIntVector;
import com.github.badoualy.telegram.tl.core.TLObject;
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages;
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs;
import com.github.badoualy.telegram.tl.api.*;
import com.github.badoualy.telegram.tl.api.upload.TLFile;
import com.github.badoualy.telegram.tl.exception.RpcErrorException;
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

public class FileManagerFactory {
	public static AbstractMediaFileManager getFileManager(Message msg) {
		if (msg==null) return null;
		JsonObject media = msg.getMedia();
		if (media==null) return null;

		String media_constructor = media.getAsJsonPrimitive("_constructor").getAsString();

		if (media_constructor.startsWith("messageMediaPhoto#")) {
			return new PhotoFileManager(msg);
		} else if (media_constructor.startsWith("messageMediaDocument#")) {
			DocumentFileManager d = new DocumentFileManager(msg);
			if (d.isSticker()) {
				return new StickerFileManager(msg);
			}
			return d;
		} else if (media_constructor.startsWith("messageMediaGeo#")) {
			return new GeoFileManager(msg);
		} else if (media_constructor.startsWith("messageMediaEmpty#")) {
			return new UnsupportedFileManager(msg, "empty");
		} else if (media_constructor.startsWith("messageMediaUnsupported#")) {
			return new UnsupportedFileManager(msg, "unsupported");
		} else if (media_constructor.startsWith("messageMediaWebpage#")) {
			return new UnsupportedFileManager(msg, "webpage");
		} else if (media_constructor.startsWith("messageMediaContact#")) {
			return new UnsupportedFileManager(msg, "contact");
		} else if (media_constructor.startsWith("messageMediaVenue#")) {
			return new UnsupportedFileManager(msg, "venue");
		} else {
			AbstractMediaFileManager.throwUnexpectedObjectError(media);
		}
		return null;
	}
}
