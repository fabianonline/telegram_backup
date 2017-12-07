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
import de.fabianonline.telegram_backup.Config
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

abstract class AbstractMediaFileManager(protected var message: TLMessage, protected var user: UserManager, protected var client: TelegramClient) {
    var isEmpty = false
        protected set
    abstract val size: Int
    abstract val extension: String
    val isDownloaded: Boolean
        get() = File(targetPathAndFilename).isFile()
    val isDownloading: Boolean
        get() = File(targetPathAndFilename + ".downloading").isFile()
    val targetPath: String
        get() {
            val path = user.getFileBase() + Config.FILE_FILES_BASE + File.separatorChar
            File(path).mkdirs()
            return path
        }
    val targetFilename: String
        get() = if (message.getToId() is TLPeerChannel) {
            "channel_" + (message.getToId() as TLPeerChannel).getChannelId() + "_" + message.getId() + "." + extension
        } else "" + message.getId() + "." + extension
    val targetPathAndFilename: String
        get() = targetPath + targetFilename

    abstract val letter: String
    abstract val name: String
    abstract val description: String
    @Throws(RpcErrorException::class, IOException::class, TimeoutException::class)
    abstract fun download()

    protected fun extensionFromMimetype(mime: String): String {
        when (mime) {
            "text/plain" -> return "txt"
        }

        val i = mime.lastIndexOf('/')
        val ext = mime.substring(i + 1).toLowerCase()

        return if (ext === "unknown") "dat" else ext

    }

    companion object {
        fun throwUnexpectedObjectError(o: Object) {
            throw RuntimeException("Unexpected " + o.getClass().getName())
        }
    }
}
