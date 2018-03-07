package de.fabianonline.telegram_backup

import java.io.File
import org.slf4j.LoggerFactory
import org.slf4j.Logger

object IniSettings {
	val logger = LoggerFactory.getLogger(IniSettings::class.java)
	var settings = mutableMapOf<String, MutableList<String>>()
	
	init {
		loadIni(UserManager.getInstance().fileBase + "config.ini")
		copySampleIni(UserManager.getInstance().fileBase + "config.sample.ini")
	}
	
	fun loadIni(filename: String) {
		val file = File(filename)
		logger.trace("Checking ini file {}", filename.anonymize())
		if (!file.exists()) return
		logger.debug("Loading ini file {}", filename.anonymize())
		file.forEachLine { parseLine(it) }
	}
	
	fun parseLine(original_line: String) {
		logger.trace("Parsing line: {}", original_line)
		var line = original_line.trim().replaceAfter("#", "").removeSuffix("#")
		logger.trace("After cleaning: {}", line)
		if (line == "") return
		val parts: List<String> = line.split("=", limit=2).map{it.trim()}

		if (parts.size < 2) throw RuntimeException("Invalid config setting: $line")
		
		val (key, value) = parts
		if (value=="") {
			settings.remove(key)
		} else {
			var map = settings.get(key)
			if (map == null) {
				map = mutableListOf<String>()
				settings.put(key, map)
			}
			map.add(value)
		}
	}
	
	fun copySampleIni(filename: String) {
		val stream = Config::class.java.getResourceAsStream("/config.sample.ini")
		File(filename).outputStream().use { stream.copyTo(it) }
		stream.close()
	}
	
	fun println() = println(settings)
	
	fun get(key: String, default: String? = null): String? = settings.get(key)?.last() ?: default
	fun getInt(key: String, default: Int? = null): Int? = try { settings.get(key)?.last()?.toInt() } catch (e: NumberFormatException) { null } ?: default
	fun getArray(key: String): List<String> = settings.get(key) ?: listOf<String>()
	
	val gmaps_key: String
		get() = get("gmaps_key", default=Config.SECRET_GMAPS)!!
}
