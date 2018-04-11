package de.fabianonline.telegram_backup

import java.io.File
import java.util.LinkedList
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
		
	val sf = SettingsFactory(ini_settings, cli_settings)
	val gmaps_key = sf.getString("gmaps_key", default=Config.SECRET_GMAPS, secret=true)
	val pagination = sf.getBoolean("pagination", default=true)
	val pagination_size = sf.getInt("pagination_size", default=Config.DEFAULT_PAGINATION)
	val download_media = sf.getBoolean("download_media", default=true)
	val download_channels = sf.getBoolean("download_channels", default=false)
	val download_supergroups = sf.getBoolean("download_supergroups", default=false)
	val whitelist_channels = sf.getStringList("whitelist_channels", default=LinkedList<String>())
	val blacklist_channels = sf.getStringList("blacklist_channels", default=LinkedList<String>())
	val max_file_age = sf.getInt("max_file_age", default=-1)

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
	
	fun print() {
		println()
		Setting.all_settings.forEach { it.print() }
		println()
	}
}

class SettingsFactory(val ini: Map<String, List<String>>, val cli: CommandLineOptions) {
	fun getInt(name: String, default: Int, secret: Boolean = false) = getSetting(name, listOf(default.toString()), secret).get().toInt()
	fun getBoolean(name: String, default: Boolean, secret: Boolean = false) = getSetting(name, listOf(default.toString()), secret).get().toBoolean()
	fun getString(name: String, default: String, secret: Boolean = false) = getSetting(name, listOf(default), secret).get()
	fun getStringList(name: String, default: List<String>, secret: Boolean = false) = getSetting(name, default, secret).getList()
	
	fun getSetting(name: String, default: List<String>, secret: Boolean) = Setting(ini, cli, name, default, secret)
}

class Setting(val ini: Map<String, List<String>>, val cli: CommandLineOptions, val name: String, val default: List<String>, val secret: Boolean) {
	val values: List<String>
	val source: SettingSource
	val logger = LoggerFactory.getLogger(Setting::class.java)
	
	init {
		if (getCli(name) != null) {
			values = listOf(getCli(name)!!)
			source = SettingSource.CLI
		} else if (getIni(name) != null) {
			values = getIni(name)!!
			source = SettingSource.INI
		} else {
			values = default
			source = SettingSource.DEFAULT
		}
		
		logger.debug("Setting ${name} loaded. Source: ${source}. Value: ${values.toString().anonymize()}")
		
		all_settings.add(this)
	}
	fun get(): String = values.last()
	fun getList(): List<String> = values
	
	fun getIni(name: String): List<String>? {
		return ini[name]
	}
	
	fun getCli(name: String): String? {
		return cli.get(name)
	}
	
	fun print() {
		println("%-25s %-10s %s".format(name, source, (if (secret && source==SettingSource.DEFAULT) "[REDACTED]" else values)))
	}
	
	companion object {
		val all_settings = LinkedList<Setting>()
	}
}

enum class SettingSource {
	INI,
	CLI,
	DEFAULT
}
