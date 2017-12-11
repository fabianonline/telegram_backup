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

package de.fabianonline.telegram_backup.exporter

import de.fabianonline.telegram_backup.UserManager
import de.fabianonline.telegram_backup.Database
import de.fabianonline.telegram_backup.Utils

import java.io.File
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.io.FileWriter
import java.io.IOException
import java.io.FileNotFoundException
import java.net.URL
import org.apache.commons.io.FileUtils
import java.util.LinkedList
import java.util.HashMap

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HTMLExporter {

    @Throws(IOException::class)
    fun export() {
        try {
            val user = UserManager.getInstance()
            val db = Database.getInstance()

            // Create base dir
            logger.debug("Creating base dir")
            val base = user.fileBase + "files" + File.separatorChar
            File(base).mkdirs()
            File(base + "dialogs").mkdirs()

            logger.debug("Fetching dialogs")
            val dialogs = db.getListOfDialogsForExport()
            logger.trace("Got {} dialogs", dialogs.size)
            logger.debug("Fetching chats")
            val chats = db.getListOfChatsForExport()
            logger.trace("Got {} chats", chats.size)

            logger.debug("Generating index.html")
            val scope = HashMap<String, Any>()
            scope.put("user", user)
            scope.put("dialogs", dialogs)
            scope.put("chats", chats)

            // Collect stats data
            scope.put("count.chats", chats.size)
            scope.put("count.dialogs", dialogs.size)

            var count_messages_chats = 0
            var count_messages_dialogs = 0
            for (c in chats) count_messages_chats += c.count
            for (d in dialogs) count_messages_dialogs += d.count

            scope.put("count.messages", count_messages_chats + count_messages_dialogs)
            scope.put("count.messages.chats", count_messages_chats)
            scope.put("count.messages.dialogs", count_messages_dialogs)

            scope.put("count.messages.from_me", db.getMessagesFromUserCount())

            scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix()))

            scope.putAll(db.getMessageAuthorsWithCount())
            scope.putAll(db.getMessageTypesWithCount())
            scope.putAll(db.getMessageMediaTypesWithCount())

            val mf = DefaultMustacheFactory()
            var mustache = mf.compile("templates/html/index.mustache")
            var w = getWriter(base + "index.html")
            mustache.execute(w, scope)
            w.close()

            mustache = mf.compile("templates/html/chat.mustache")

            var i = 0
            logger.debug("Generating {} dialog pages", dialogs.size)
            for (d in dialogs) {
                i++
                logger.trace("Dialog {}/{}: {}", i, dialogs.size, Utils.anonymize("" + d.id))
                val messages = db.getMessagesForExport(d)
                scope.clear()
                scope.put("user", user)
                scope.put("dialog", d)
                scope.put("messages", messages)

                scope.putAll(db.getMessageAuthorsWithCount(d))
                scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix(d)))
                scope.putAll(db.getMessageTypesWithCount(d))
                scope.putAll(db.getMessageMediaTypesWithCount(d))

                w = getWriter(base + "dialogs" + File.separatorChar + "user_" + d.id + ".html")
                mustache.execute(w, scope)
                w.close()
            }

            i = 0
            logger.debug("Generating {} chat pages", chats.size)
            for (c in chats) {
                i++
                logger.trace("Chat {}/{}: {}", i, chats.size, Utils.anonymize("" + c.id))
                val messages = db.getMessagesForExport(c)
                scope.clear()
                scope.put("user", user)
                scope.put("chat", c)
                scope.put("messages", messages)

                scope.putAll(db.getMessageAuthorsWithCount(c))
                scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix(c)))
                scope.putAll(db.getMessageTypesWithCount(c))
                scope.putAll(db.getMessageMediaTypesWithCount(c))

                w = getWriter(base + "dialogs" + File.separatorChar + "chat_" + c.id + ".html")
                mustache.execute(w, scope)
                w.close()
            }

            logger.debug("Generating additional files")
            // Copy CSS
            val cssFile = javaClass.getResource("/templates/html/style.css")
            val dest = File(base + "style.css")
            FileUtils.copyURLToFile(cssFile, dest)
            logger.debug("Done exporting.")
        } catch (e: IOException) {
            e.printStackTrace()
            logger.error("Caught an exception!", e)
            throw e
        }

    }

    @Throws(FileNotFoundException::class)
    private fun getWriter(filename: String): OutputStreamWriter {
        logger.trace("Creating writer for file {}", Utils.anonymize(filename))
        return OutputStreamWriter(FileOutputStream(filename), Charset.forName("UTF-8").newEncoder())
    }

    private fun intArrayToString(data: Array<IntArray>): String {
        val sb = StringBuilder()
        sb.append("[")
        for (x in data.indices) {
            for (y in 0 until data[x].size) {
                if (x > 0 || y > 0) sb.append(",")
                sb.append("[" + x + "," + y + "," + data[x][y] + "]")
            }
        }
        sb.append("]")
        return sb.toString()
    }

    private fun mapToString(map: Map<String, Int>): String {
        val sb = StringBuilder("[")
        for ((key, value) in map) {
            sb.append("['$key', $value],")
        }
        sb.append("]")
        return sb.toString()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HTMLExporter::class.java)
    }
}
