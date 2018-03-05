package de.fabianonline.telegram_backup

object IniSettings {
	val logger = LoggerFactory.getLogger(IniSettings::class)
	
	init {
		loadIni(UserManager.getInstance().fileBase + "config.ini")
	}
	
	fun loadIni(filename: String) {
	
	}
}
