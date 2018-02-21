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

class StickerFileManager(msg: TLMessage, user: UserManager, client: TelegramClient) : DocumentFileManager(msg, user, client) {

	override val isSticker = true

	private val filenameBase: String
		get() {
			var sticker: TLDocumentAttributeSticker? = null
			for (attr in doc!!.getAttributes()) {
				if (attr is TLDocumentAttributeSticker) {
					sticker = attr
				}
			}

			val file = StringBuilder()
			val set = sticker!!.getStickerset()
			if (set is TLInputStickerSetShortName) {
				file.append(set.getShortName())
			} else if (set is TLInputStickerSetID) {
				file.append(set.getId())
			}
			file.append("_")
			file.append(sticker.getAlt().hashCode())
			return file.toString()
		}

	override val targetFilename: String
		get() = filenameBase + "." + extension

	override val targetPath: String
		get() {
			val path = user.fileBase + Config.FILE_FILES_BASE + File.separatorChar + Config.FILE_STICKER_BASE + File.separatorChar
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

	@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
	override fun download(): Boolean {
		val old_file = Config.FILE_BASE + File.separatorChar + Config.FILE_STICKER_BASE + File.separatorChar + targetFilename

		logger.trace("Old filename exists: {}", File(old_file).exists())

		if (File(old_file).exists()) {
			Files.copy(Paths.get(old_file), Paths.get(targetPathAndFilename), StandardCopyOption.REPLACE_EXISTING)
			return true
		}
		return super.download()
	}

	companion object {
		private val logger = LoggerFactory.getLogger(StickerFileManager::class.java)
	}
}
