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

open class DocumentFileManager(msg: TLMessage, user: UserManager, client: TelegramClient) : AbstractMediaFileManager(msg, user, client) {
	protected var doc: TLDocument? = null
	override lateinit var extension: String

	open val isSticker: Boolean
		get() {
			if (this.isEmpty || doc == null) return false
			return doc!!.getAttributes()?.filter { it is TLDocumentAttributeSticker }?.isNotEmpty() ?: false
		}

	override val size: Int
		get() = if (doc != null) doc!!.getSize() else 0

	open override val letter: String = "d"
	open override val name: String = "document"
	open override val description: String = "Document"

	init {
		val d = (msg.getMedia() as TLMessageMediaDocument).getDocument()
		if (d is TLDocument) {
			this.doc = d
		} else if (d is TLDocumentEmpty) {
			this.isEmpty = true
		} else {
			throwUnexpectedObjectError(d)
		}
		extension = processExtension()
	}

	private fun processExtension(): String {
		if (doc == null) return "empty"
		var ext: String? = null
		var original_filename: String? = null
		if (doc!!.getAttributes() != null)
			for (attr in doc!!.getAttributes()) {
				if (attr is TLDocumentAttributeFilename) {
					original_filename = attr.getFileName()
				}
			}
		if (original_filename != null) {
			val i = original_filename.lastIndexOf('.')
			if (i > 0) ext = original_filename.substring(i + 1)

		}
		if (ext == null) {
			ext = extensionFromMimetype(doc!!.getMimeType())
		}

		// Sometimes, extensions contain a trailing double quote. Remove this. Fixes #12.
		ext = ext.replace("\"", "")

		return ext
	}

	@Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
	override fun download(): Boolean {
		if (doc != null) {
			DownloadManager.downloadFile(targetPathAndFilename, size, doc!!.getDcId(), doc!!.getId(), doc!!.getAccessHash(), doc!!.getVersion())
		}
		return true
	}
}
