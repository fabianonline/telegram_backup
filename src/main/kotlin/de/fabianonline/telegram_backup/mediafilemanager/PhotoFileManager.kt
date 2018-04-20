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
import de.fabianonline.telegram_backup.DownloadManager
import de.fabianonline.telegram_backup.DownloadProgressInterface
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.exception.RpcErrorException

import java.io.IOException
import java.util.concurrent.TimeoutException

import com.google.gson.*
import com.github.salomonbrys.kotson.*
import de.fabianonline.telegram_backup.*

class PhotoFileManager(message: JsonObject, file_base: String) : AbstractMediaFileManager(message, file_base) {
	//private lateinit var photo: TLPhoto
	override var size = 0
	//private lateinit var photo_size: TLPhotoSize

	override val extension = "jpg"
	override val letter = "p"
	override val name = "photo"
	override val description = "Photo"
	
	val biggestSize: JsonObject
	var biggestSizeW = 0
	var biggestSizeH = 0
	
	val json = message["media"]["photo"].obj
	override var isEmpty = json.isA("photoEmpty")

	init {
		/*val p = (msg.getMedia() as TLMessageMediaPhoto).getPhoto()*/
		if (!isEmpty) {
			var bsTemp: JsonObject? = null
		
			for (elm in json["sizes"].array) {
				val s = elm.obj
				if (!s.isA("photoSize")) continue
				if (bsTemp == null || (s["w"].int > biggestSizeW && s["h"].int > biggestSizeH)) {
					bsTemp = s
					biggestSizeW = s["w"].int
					biggestSizeH = s["h"].int
					size = s["size"].int // file size
				}
			}

			if (bsTemp == null) throw RuntimeException("Could not find a size for a photo.")
			biggestSize = bsTemp
		} else {
			biggestSize = JsonObject()
		}
			
		/*} else if (p is TLPhotoEmpty) {
			this.isEmpty = true
		} else {
			throwUnexpectedObjectError(p)
		}*/
	}

	@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
	override fun download(prog: DownloadProgressInterface?): Boolean {
		/*if (isEmpty) return true*/
		//val loc = photo_size.getLocation() as TLFileLocation
		
		val loc = biggestSize["location"].obj
		DownloadManager.downloadFile(targetPathAndFilename, size, loc["dcId"].int, loc["volumeId"].long, loc["localId"].int, loc["secret"].long, prog)
		return true
	}
}
