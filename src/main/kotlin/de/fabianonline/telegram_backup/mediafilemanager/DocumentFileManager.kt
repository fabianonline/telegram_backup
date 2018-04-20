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

import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.tl.exception.RpcErrorException

import java.io.IOException
import java.util.concurrent.TimeoutException
import com.google.gson.*
import com.github.salomonbrys.kotson.*
import de.fabianonline.telegram_backup.*

open class DocumentFileManager(message: JsonObject, file_base: String) : AbstractMediaFileManager(message, file_base) {
	//protected var doc: TLDocument? = null
	override lateinit var extension: String

	open val isSticker: Boolean
		get() = json.get("attributes")?.array?.any{it.obj.isA("documentAttributeSticker")} ?: false

	override val size: Int
		get() = json["size"].int

	open override val letter: String = "d"
	open override val name: String = "document"
	open override val description: String = "Document"
	
	private val json = message["media"]["document"].obj

	init {
		extension = processExtension()
	}

	private fun processExtension(): String {
		//if (doc == null) return "empty"
		var ext: String? = null
		var original_filename: String? = null
		if (json.contains("attributes"))
			for (attr in json["attributes"].array) {
				if (attr.obj["_constructor"].string.startsWith("documentAttributeFilename")) {
					original_filename = attr.obj["fileName"].string
				}
			}
		if (original_filename != null) {
			val i = original_filename.lastIndexOf('.')
			if (i > 0) ext = original_filename.substring(i + 1)

		}
		if (ext == null) {
			ext = extensionFromMimetype(json["mimeType"].string)
		}

		// Sometimes, extensions contain a trailing double quote. Remove this. Fixes #12.
		ext = ext.replace("\"", "")

		return ext
	}

	@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
	override fun download(): Boolean {
		DownloadManager.downloadFile(targetPathAndFilename, size, json["dcId"].int, json["id"].long, json["accessHash"].long)
		return true
	}
}
