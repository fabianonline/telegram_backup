package de.fabianonline.telegram_backup

class DbSettings() {
	private fun fetchValue(name: String): String? = Database.getInstance().fetchSetting(name)
	private fun saveValue(name: String, value: String?) = Database.getInstance().saveSetting(name, value)
	
	var pts: String?
		get() = fetchValue("pts")
		set(x: String?) = saveValue("pts", x)
}


