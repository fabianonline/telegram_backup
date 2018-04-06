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
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>. */
package de.fabianonline.telegram_backup

class CommandLineOptions(args: Array<String>) {
	val booleans = mutableListOf<String>()
	val values = mutableMapOf<String, MutableList<String>>()
	var last_key: String? = null
	
	init {
		for(arg in args) {
			if (arg.starts_with("--")) {
				if (last_key!=null) {
					booleans.add(last_key)
				}
				last_key = arg.substr(2)
			} else {
				if (last_key==null) throw RuntimeException("Unexpected parameter without switch: $arg")
				var list = values.get(last_key)
				if (list==null) {
					list = mutableListOf<String>()
					values.add(last_key, list)
				}
				list.add(arg)
				last_key = null
			}
		}
		if (last_key!=null) booleans.add(last_key)
	}
}
