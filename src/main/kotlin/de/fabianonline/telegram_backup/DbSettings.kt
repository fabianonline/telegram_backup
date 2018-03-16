package de.fabianonline.telegram_backup

class DbSettings(val database: Database) {
	private fun fetchValue(name: String): String? = database.fetchSetting(name)
	private fun saveValue(name: String, value: String?) = database.saveSetting(name, value)
	
	var pts: String?
		get() = fetchValue("pts")
		set(x: String?) = saveValue("pts", x)
}


