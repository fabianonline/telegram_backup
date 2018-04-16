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

package de.fabianonline.telegram_backup.mediafilemanager

import de.fabianonline.telegram_backup.UserManager
import de.fabianonline.telegram_backup.Database
import de.fabianonline.telegram_backup.DownloadProgressInterface

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.core.TLIntVector
import com.github.badoualy.telegram.tl.core.TLObject
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.api.upload.TLFile
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile
import de.fabianonline.telegram_backup.Settings

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.LinkedList
import java.net.URL
import java.util.concurrent.TimeoutException
import com.google.gson.*
import com.github.salomonbrys.kotson.*

import org.apache.commons.io.FileUtils

object FileManagerFactory {
	fun getFileManager(m: TLMessage?, u: UserManager, file_base: String, settings: Settings?): AbstractMediaFileManager? {
		if (m == null) return null
		val json = Gson().toJsonTree(m).obj
		return getFileManager(json, u, file_base, settings)
	}
	            
	fun getFileManager(m: JsonObject?, u: UserManager, file_base: String, settings: Settings?): AbstractMediaFileManager? {
		if (m == null) return null
		val media = m.get("media")?.obj ?: return null
		
		if (media.contains("photo")) {
			return PhotoFileManager(media["photo"].obj, u, file_base)
		}

		/*if (media is TLMessageMediaPhoto) {
			return PhotoFileManager(m, u, file_base)
		} else if (media is TLMessageMediaDocument) {
			val d = DocumentFileManager(m, u, file_base)
			return if (d.isSticker) {
				StickerFileManager(m, u, file_base)
			} else d
		} else if (media is TLMessageMediaGeo) {
			return GeoFileManager(m, u, file_base, settings)
		} else if (media is TLMessageMediaEmpty) {
			return UnsupportedFileManager(m, u, file_base, "empty")
		} else if (media is TLMessageMediaUnsupported) {
			return UnsupportedFileManager(m, u, file_base, "unsupported")
		} else if (media is TLMessageMediaWebPage) {
			return UnsupportedFileManager(m, u, file_base, "webpage")
		} else if (media is TLMessageMediaContact) {
			return UnsupportedFileManager(m, u, file_base, "contact")
		} else if (media is TLMessageMediaVenue) {
			return UnsupportedFileManager(m, u, file_base, "venue")
		} else {
			AbstractMediaFileManager.throwUnexpectedObjectError(media)
		}*/
		return null
	}
}
