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
import de.fabianonline.telegram_backup.DownloadManager
import de.fabianonline.telegram_backup.Config

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.core.TLIntVector
import com.github.badoualy.telegram.tl.core.TLObject
import com.github.badoualy.telegram.tl.api.messages.TLAbsMessages
import com.github.badoualy.telegram.tl.api.messages.TLAbsDialogs
import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.api.upload.TLFile
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import com.github.badoualy.telegram.tl.api.request.TLRequestUploadGetFile

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayList
import java.util.LinkedList
import java.net.URL
import java.util.concurrent.TimeoutException

import org.apache.commons.io.FileUtils

import com.google.gson.*
import com.github.salomonbrys.kotson.*
import de.fabianonline.telegram_backup.*

class StickerFileManager(message: JsonObject, file_base: String) : DocumentFileManager(message, file_base) {

	override val isSticker = true

	val json = message["media"]["document"].obj
	val sticker = json["attributes"].array.first{it.obj.isA("documentAttributeSticker")}.obj
	override var isEmpty = sticker["stickerset"].obj.isA("inputStickerSetEmpty")

	private val filenameBase: String
		get() {
            val stickerSet = sticker["stickerset"].obj
			val set = stickerSet.get("shortName").nullString
                ?: stickerSet.get("id").nullString
                ?: stickerSet.get("_constructor").nullString
                ?: error("could not get a good name from: ${sticker["stickerset"]}")

			val hash = sticker["alt"].string.hashCode()
			return "${set}_${hash}"
		}

	override val targetFilename: String
		get() = filenameBase + "." + extension

	override val targetPath: String
		get() {
			val path = file_base + Config.FILE_FILES_BASE + File.separatorChar + Config.FILE_STICKER_BASE + File.separatorChar
			File(path).mkdirs()
			return path
		}

	override var extension = "webp"

	override val letter: String
		get() = "s"
	override val name: String
		get() = "sticker"
	override val description: String
		get() = "Sticker"

	companion object {
		private val logger = LoggerFactory.getLogger(StickerFileManager::class.java)
	}
}
