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
import de.fabianonline.telegram_backup.anonymize
import de.fabianonline.telegram_backup.toPrettyJson
import de.fabianonline.telegram_backup.CommandLineOptions

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
	val db = Database.getInstance()
	val user = UserManager.getInstance()

	@Throws(IOException::class)
	fun export() {
		try {
			val pagination = if (CommandLineOptions.cmd_no_pagination) -1 else CommandLineOptions.val_pagination
			
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

			println("Generating index.html...")
			val scope = HashMap<String, Any>()
			scope.put("user", user)
			scope.put("dialogs", dialogs)
			scope.put("chats", chats)

			// Collect stats data
			scope.put("count.chats", chats.size)
			scope.put("count.dialogs", dialogs.size)

			var count_messages_chats = 0
			var count_messages_dialogs = 0
			for (c in chats) count_messages_chats += c.count ?: 0
			for (d in dialogs) count_messages_dialogs += d.count ?: 0

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
			val page_mustache = mf.compile("templates/html/page.mustache")

			var i = 0
			println("Generating ${dialogs.size} dialog pages...")
			for (d in dialogs) {
				i++
				logger.trace("Dialog {}/{}: {}", i, dialogs.size, d.id.toString().anonymize())
				processChat(chat=d, pagination=pagination, index_mustache=mustache, base_dir=base, page_mustache=page_mustache);
				print(".")
				if (i % 100 == 0) {
					println(" - $i/${dialogs.size}")
				}
			}
			println()

			i = 0
			println("Generating ${chats.size} chat pages...")
			for (c in chats) {
				i++
				logger.trace("Chat {}/{}: {}", i, chats.size, c.id.toString().anonymize())
				processChat(chat=c, pagination=pagination, index_mustache=mustache, base_dir=base, page_mustache=page_mustache);
				print(".")
				if (i % 100 == 0) {
					println(" - $i/${chats.size}")
				}
			}
			println()

			println("Generating additional files...")
			// Copy CSS
			val cssFile = javaClass.getResource("/templates/html/style.css")
			val dest = File(base + "style.css")
			FileUtils.copyURLToFile(cssFile, dest)
			println("Finished.")
			println("Open the following link in your browser to view the export:")
			println("file://${base}index.html")
		} catch (e: IOException) {
			e.printStackTrace()
			logger.error("Caught an exception!", e)
			throw e
		}

	}
	
	private fun processChat(chat: Database.AbstractChat, pagination: Int, index_mustache: Mustache, base_dir: String, page_mustache: Mustache) {
		
		val scope = HashMap<String, Any>()
		
		val count = db.getMessageCountForExport(chat)
		
		val prefix = if (chat.type == "dialog") "user_" else "chat_"
		val id = if (chat is Database.Chat) chat.id else if (chat is Database.Dialog) chat.id else throw IllegalArgumentException("Unexpected unknown id")
		
		scope.put("user", user)
		scope.put(chat.type, chat)
		
		if (pagination>0 && count>pagination) { // pagination is enabled and we have more messages than allowed on one page
			scope.put("paginated", true)
			val pages_data = LinkedList<HashMap<String, String>>()
			
			var offset = 0
			var page = 1
			val pages: Int = count / pagination + 1
			val dir = "${base_dir}dialogs${File.separatorChar}"
			val filename_base = "${prefix}${id}_p"
			while (offset < count) {
				val page_scope = HashMap<String, Any>()
				val filename = "${filename_base}${page}.html"
				
				page_scope.put("page", page)
				page_scope.put("pages", pages)
				page_scope.put(chat.type, chat)
				
				if (page > 1) page_scope.put("previous_page", "${filename_base}${page-1}.html")
				if (page < pages) page_scope.put("next_page", "${filename_base}${page+1}.html")
				
				val messages = db.getMessagesForExport(chat, limit=pagination, offset=offset)
				page_scope.put("messages", messages)
				
				val w = getWriter(dir + filename)
				page_mustache.execute(w, page_scope)
				w.close()
				
				
				val data = HashMap<String, String>()
				data.put("page", ""+page)
				data.put("filename", "${prefix}${id}_p${page}.html")
				data.put("start_time", messages.getFirst().get("formatted_time") as String)
				data.put("start_date", messages.getFirst().get("formatted_date") as String)
				pages_data.add(data)
				page += 1
				offset += pagination
			}
			scope.put("pages_data", pages_data)
			scope.put("pages", pages)
		} else { // put all messages on one page
			scope.put("paginated", false)
			val messages = db.getMessagesForExport(chat)
			scope.put("messages", messages)
		}

		scope.putAll(db.getMessageAuthorsWithCount(chat))
		scope.put("heatmap_data", intArrayToString(db.getMessageTimesMatrix(chat)))
		scope.putAll(db.getMessageTypesWithCount(chat))
		scope.putAll(db.getMessageMediaTypesWithCount(chat))

		
		val w = getWriter(base_dir + "dialogs" + File.separatorChar + prefix + id + ".html")
		index_mustache.execute(w, scope)
		w.close()
	}

	@Throws(FileNotFoundException::class)
	private fun getWriter(filename: String): OutputStreamWriter {
		logger.trace("Creating writer for file {}", filename.anonymize())
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
