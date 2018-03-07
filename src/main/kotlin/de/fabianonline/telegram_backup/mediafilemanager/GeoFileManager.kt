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
import de.fabianonline.telegram_backup.StickerConverter
import de.fabianonline.telegram_backup.DownloadProgressInterface
import de.fabianonline.telegram_backup.DownloadManager
import de.fabianonline.telegram_backup.IniSettings

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.core.TLIntVector
import com.github.badoualy.telegram.tl.core.TLObject
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.api.upload.TLFile
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.LinkedList
import java.net.URL
import java.util.concurrent.TimeoutException

import org.apache.commons.io.FileUtils

class GeoFileManager(msg: TLMessage, user: UserManager, client: TelegramClient) : AbstractMediaFileManager(msg, user, client) {
	protected lateinit var geo: TLGeoPoint

	// We don't know the size, so we just guess.
	override val size: Int
		get() {
			val f = File(targetPathAndFilename)
			return if (f.isFile()) f.length().toInt() else 100000
		}

	override val extension: String
		get() = "png"

	override val letter = "g"
	override val name = "geo"
	override val description = "Geolocation"

	init {
		val g = (msg.getMedia() as TLMessageMediaGeo).getGeo()
		if (g is TLGeoPoint) {
			this.geo = g
		} else if (g is TLGeoPointEmpty) {
			this.isEmpty = true
		} else {
			throwUnexpectedObjectError(g)
		}
	}

	@Throws(IOException::class)
	override fun download(): Boolean {
		val url = "https://maps.googleapis.com/maps/api/staticmap?" +
			"center=" + geo.getLat() + "," + geo.getLong() + "&" +
			"zoom=14&size=300x150&scale=2&format=png&" +
			"key=" + IniSettings.gmaps_key
		return DownloadManager.downloadExternalFile(targetPathAndFilename, url)
	}
}
