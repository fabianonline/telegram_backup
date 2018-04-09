package de.fabianonline.telegram_backup

import java.io.File
import org.slf4j.LoggerFactory

class Settings(val file_base: String, val database: Database, val cli_settings: CommandLineOptions) {
	val logger = LoggerFactory.getLogger(Settings::class.java)

	private val db_settings: Map<String, String>

	val ini_settings: Map<String, List<String>>

	init {
		db_settings = database.fetchSettings()
		ini_settings = load_ini("config.ini")
		copy_sample_ini("config.sample.ini")
	}
		// Merging CLI and INI settings
	val gmaps_key = get_setting_string("gmaps_key", default=Config.SECRET_GMAPS)
	val pagination = get_setting_boolean("pagination", default=true)
	val pagination_size = get_setting_int("pagination_size", default=Config.DEFAULT_PAGINATION)
	val download_media = get_setting_boolean("download_media", default=true)
	val download_channels = get_setting_boolean("download_channels", default=false)
	val download_supergroups = get_setting_boolean("download_supergroups", default=false)
	val whitelist_channels = get_setting_list("whitelist_channels")
	val blacklist_channels = get_setting_list("blacklist_channels")


	private fun get_setting_string(name: String, default: String): String {
		return ini_settings[name]?.last() ?: cli_settings.values[name] ?: default
	}

	private fun get_setting_int(name: String, default: Int): Int {
		return ini_settings[name]?.last()?.toInt() ?: cli_settings.values[name]?.toInt() ?: default
	}

	private fun get_setting_boolean(name: String, default: Boolean): Boolean {
		return ini_settings[name]?.last()?.toBoolean() ?: cli_settings.values[name]?.toBoolean() ?: default
	}

	private fun get_setting_list(name: String): List<String>? {
		return ini_settings[name]
	}

	private fun load_ini(filename: String): Map<String, List<String>> {
		val map = mutableMapOf<String, MutableList<String>>()
		val file = File(file_base + filename)
		logger.trace("Checking ini file {}", filename.anonymize())
		if (!file.exists()) return map
		logger.debug("Loading ini file {}", filename.anonymize())
		file.forEachLine { parseLine(it, map) }
		return map
	}

	private fun parseLine(original_line: String, map: MutableMap<String, MutableList<String>>) {
		logger.trace("Parsing line: {}", original_line)
		var line = original_line.trim().replaceAfter("#", "").removeSuffix("#")
		logger.trace("After cleaning: {}", line)
		if (line == "") return
		val parts: List<String> = line.split("=", limit=2).map{it.trim()}

		if (parts.size < 2) throw RuntimeException("Invalid config setting: $line")

		val (key, value) = parts
		if (value=="") {
			map.remove(key)
		} else {
			var list = map.get(key)
			if (list == null) {
				list = mutableListOf<String>()
				map.put(key, list)
			}
			list.add(value)
		}
	}

	private fun copy_sample_ini(filename: String) {
		val stream = Config::class.java.getResourceAsStream("/config.sample.ini")
		File(filename).outputStream().use { stream.copyTo(it) }
		stream.close()
	}

}
/*
class DbSettings(val database: Database) {
	private fun fetchValue(name: String): String? = database.fetchSetting(name)
	private fun saveValue(name: String, value: String?) = database.saveSetting(name, value)
	
	var pts: String?
		get() = fetchValue("pts")
		set(x: String?) = saveValue("pts", x)
}


package de.fabianonline.telegram_backup



class Settings(val file_base: String) {
	val logger = LoggerFactory.getLogger(Settings::class.java)
	var settings = mutableMapOf<String, MutableList<String>>()
	
	init {
		loadIni(file_base + "config.ini")
		copySampleIni(file_base + "config.sample.ini")
	}
	

	

	

	
	fun println() = println(settings)
	
	fun getString(key: String, default: String? = null): String? = settings.get(key)?.last() ?: default
	fun getStringList(key: String): List<String>? = settings.get(key)
	fun getInt(key: String, default: Int? = null): Int? = try { settings.get(key)?.last()?.toInt() } catch (e: NumberFormatException) { null } ?: default
	fun getBoolean(key: String, default: Boolean = false): Boolean {
		val value = settings.get(key)?.last()
		if (value==null) return default
		return value=="true"
	}
	fun getArray(key: String): List<String> = settings.get(key) ?: listOf<String>()
	
	val gmaps_key = getString("gmaps_key", default=Config.SECRET_GMAPS)!!
	val pagination = getBoolean("pagination", default=true)
	val pagination_size = getInt("pagination_size", default=Config.DEFAULT_PAGINATION)!!
	val download_media = getBoolean("download_media", default=true)
	val download_channels = getBoolean("download_channels", default=false)
	val download_supergroups = getBoolean("download_supergroups", default=false)
	val whitelist_channels = getStringList("whitelist_channels")
	val blacklist_channels = getStringList("blacklist_channels")
}
*/
