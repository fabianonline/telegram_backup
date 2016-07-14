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
	public static AbstractMediaFileManager getFileManager(TLMessage m, UserManager u, TelegramClient c) {
		if (m==null) return null;
		TLAbsMessageMedia media = m.getMedia();
		if (media==null) return null;
		
		if (media instanceof TLMessageMediaPhoto) {
			return new PhotoFileManager(m, u, c);
		} else if (media instanceof TLMessageMediaDocument) {
			DocumentFileManager d = new DocumentFileManager(m, u, c);
			if (d.isSticker()) {
				return new StickerFileManager(m, u, c);
			}
			return d;
		} else if (media instanceof TLMessageMediaGeo) {
			return new GeoFileManager(m, u, c);
		} else if (media instanceof TLMessageMediaEmpty) {
			return new UnsupportedFileManager(m, u, c, "empty");
		} else if (media instanceof TLMessageMediaUnsupported) {
			return new UnsupportedFileManager(m, u, c, "unsupported");
		} else if (media instanceof TLMessageMediaWebPage) {
			return new UnsupportedFileManager(m, u, c, "webpage");
		} else if (media instanceof TLMessageMediaContact) {
			return new UnsupportedFileManager(m, u, c, "contact");
		} else if (media instanceof TLMessageMediaVenue) {
			return new UnsupportedFileManager(m, u, c, "venue");
		} else {
			AbstractMediaFileManager.throwUnexpectedObjectError(media);
		}
		return null;
	}
}
