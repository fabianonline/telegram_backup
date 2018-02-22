package de.fabianonline.telegram_backup

class Settings() {

	val options = arrayOf(
		Option("gmaps_api_key", "abc", "This contains the api key bla foo bar.")
	)
	
	init {
		var all_settings = Database.getInstance().settingsGetSettings()
		for ((key, value) in all_settings) {
			val opt = options.find{it.key == key}
			if (opt==null) throw IllegalArgumentException("Setting with key ${key} is unknown.")
			if (value!=null) opt.value = value
		}
	}
	
	fun print() {
		val modifiedSettings = options.filter{!it.isDefaultValue()}
		val defaultSettings = options.filter{it.isDefaultValue()}
		
		if (modifiedSettings.count() > 0) {
			println("Modified settings:")
			modifiedSettings.forEach{it.print()}
			println("\n")
		}
		
		if (defaultSettings.count() > 0) {
			println("Settings with default value:")
			defaultSettings.forEach{it.print()}
			println("\n")
		}
	}
	
	inner open class HiddenOption(val key: String, val default: String) {
		var value: String? = null
		
		fun save() {
			if (isDefaultValue()) {
				Database.getInstance().settingsSetValue(key, null)
			} else {
				Database.getInstance().settingsSetValue(key, value)
			}
		}
		
		open fun print() {
			println("%-30s %-30s".format(key, value))
		}
		
		fun isDefaultValue(): Boolean = (value==null || value==default)
			
	}
	inner class Option(key: String, default: String, val descr: String): HiddenOption(key, default) {
		override fun print() {
			println("%-30s %-30s %s".format(key, value, descr))
		}
	}
}


