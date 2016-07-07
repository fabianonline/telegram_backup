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

public class GeoFileManager extends AbstractMediaFileManager {
	protected TLGeoPoint geo;
	
	public GeoFileManager(TLMessage msg, UserManager user, TelegramClient client) {
		super(msg, user, client);
		TLAbsGeoPoint g = ((TLMessageMediaGeo)msg.getMedia()).getGeo();
		if (g instanceof TLGeoPoint) {
			this.geo = (TLGeoPoint) g;
		} else if (g instanceof TLGeoPointEmpty) {
			this.isEmpty = true;
		} else {
			throwUnexpectedObjectError(g);
		}
	}
	
	public int getSize() {
		File f = new File(getTargetPathAndFilename());
		if (f.isFile()) return (int)f.length();
		
		// We don't know the size, so we just guess.
		return 100000;
	}
	
	public String getExtension() { return "png"; }
	
	public void download() throws IOException {
		String url = "https://maps.googleapis.com/maps/api/staticmap?" +
			"center=" + geo.getLat() + "," + geo.getLong() + "&" +
			"zoom=14&size=300x150&scale=2&format=png&" +
			"key=" + Config.SECRET_GMAPS;
		DownloadManager.downloadExternalFile(getTargetPathAndFilename(), url);
	}

	public String getLetter() { return "g"; }
	public String getName() { return "geo"; }	
	public String getDescription() { return "Geolocation"; }
}
