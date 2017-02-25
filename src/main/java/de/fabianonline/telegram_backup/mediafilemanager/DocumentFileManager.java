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

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.net.URL;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

public class DocumentFileManager extends AbstractMediaFileManager {
	protected JsonObject doc;
	private String extension = null;

	public DocumentFileManager(Message msg) {
		super(msg);
		doc = media.getAsJsonObject("document");
		if ( ! doc.getAsJsonPrimitive("_constructor").getAsString().startsWith("document#")) {
			doc = null;
			isEmpty = true;
		}
	}

	public boolean isSticker() {
		if (this.isEmpty || doc==null) return false;
		for(JsonElement attr : doc.getAsJsonArray("attributes")) {
			if (attr.getAsJsonObject().getAsJsonPrimitive("_costructor").getAsString().startsWith("documentAttributeSticker#")) return true;
		}
		return false;
	}

	public int getSize() {
		if (doc != null) return doc.getAsJsonPrimitive("size").getAsInt();
		return 0;
	 }

	public String getExtension() {
		if (extension != null) return extension;
		if (doc == null) return "empty";
		String ext = null;
		String original_filename = null;
		for(JsonElement attr : doc.getAsJsonArray("attributes")) {
			if (attr.getAsJsonObject().getAsJsonPrimitive("_constructor").getAsString().startsWith("documentAttributeFilename#")) {
				original_filename = attr.getAsJsonObject().getAsJsonPrimitive("fileName").getAsString();
			}
		}

		if (original_filename != null) {
			int i = original_filename.lastIndexOf('.');
			if (i>0) ext = original_filename.substring(i+1);
		}

		if (ext==null) {
			ext = extensionFromMimetype(doc.getAsJsonPrimitive("mimeType").getAsString());
		}

		// Sometimes, extensions contain a trailing double quote. Remove this. Fixes #12.
		ext = ext.replace("\"", "");

		this.extension = ext;
		return ext;
	}

	public void download(TelegramClient c) throws RpcErrorException, IOException, TimeoutException {
		if (doc!=null) {
			DownloadManager.downloadFile(c, getTargetPathAndFilename(), getSize(),
				doc.getAsJsonPrimitive("dcId").getAsInt(),
				doc.getAsJsonPrimitive("id").getAsLong(),
				doc.getAsJsonPrimitive("accessHash").getAsLong());
		}
	}

	public String getLetter() { return "d"; }
	public String getName() { return "document"; }
	public String getDescription() { return "Document"; }
}
