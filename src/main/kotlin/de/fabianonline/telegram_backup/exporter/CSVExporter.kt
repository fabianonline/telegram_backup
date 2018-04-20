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
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter 
import java.sql.Time
import java.text.SimpleDateFormat

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import de.fabianonline.telegram_backup.*
import com.github.badoualy.telegram.tl.api.*
import com.google.gson.*
import com.github.salomonbrys.kotson.*
       

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CSVExporter(val db: Database, val file_base: String, val settings: Settings) {
	val logger = LoggerFactory.getLogger(CSVExporter::class.java)
	val mustache = DefaultMustacheFactory().compile("templates/csv/messages.csv")
	val dialogs = db.getListOfDialogsForExport()
	val chats = db.getListOfChatsForExport()
	val datetime_format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
	val base = file_base + "files" + File.separatorChar
	
	fun export() {
		val today = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)
		val timezone = ZoneOffset.systemDefault()
		val days = if (settings.max_file_age==-1) 7 else settings.max_file_age

		// Create base dir
		logger.debug("Creating base dir")
		File(base).mkdirs()

		if (days > 0) {
			for (dayOffset in days downTo 1) {
				val day = today.minusDays(dayOffset.toLong())

				val start = day.toEpochSecond(timezone.rules.getOffset(day))
				val end = start + 24 * 60 * 60
				val filename = base + "messages.${day.format(DateTimeFormatter.ISO_LOCAL_DATE)}.csv"
				if (!File(file_base + filename).exists()) {
					logger.debug("Range: {} to {}", start, end)
					println("Processing messages for ${day}...")
					exportToFile(start, end, filename)
				}
			}
		} else {
			println("Processing all messages...")
			exportToFile(0, Long.MAX_VALUE, base + "messages.all.csv")
		}
	}

	fun exportToFile(start: Long, end: Long, filename: String) {
		val list = mutableListOf<Map<String, String?>>()
		db.getMessagesForCSVExport(start, end) {data: HashMap<String, Any> ->
			val scope = HashMap<String, String?>()
			val timestamp = data["time"] as Time
			scope.put("time", datetime_format.format(timestamp))
			scope.put("username", if (data["user_username"]!=null) data["user_username"] as String else null)
			if (data["source_type"]=="dialog") {
				scope.put("chat_name", "@" + (dialogs.firstOrNull{it.id==data["source_id"]}?.username ?: ""))
			} else {
				scope.put("chat_name", chats.firstOrNull{it.id==data["source_id"]}?.name)
			}
			scope.put("message", data["message"] as String)
			list.add(scope)
		}
		val writer = getWriter(filename)
		mustache.execute(writer, mapOf("messages" to list))
		writer.close()
	}
	
	private fun getWriter(filename: String): OutputStreamWriter {
		logger.trace("Creating writer for file {}", filename.anonymize())
		return OutputStreamWriter(FileOutputStream(filename), Charset.forName("UTF-8").newEncoder())
	}
}
