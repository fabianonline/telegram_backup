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
	val values = mutableMapOf<String, String>()
	var last_key: String? = null
	val substitutions = mapOf("-t" to "--target")
	
	init {
		val list = args.toMutableList()
		
		while (list.isNotEmpty()) {
			
			var current_arg = list.removeAt(0)
			if (!current_arg.startsWith("-")) throw RuntimeException("Unexpected unnamed parameter ${current_arg}")
			
			var next_arg: String? = null
			
			if (current_arg.contains("=")) {
				val parts = current_arg.split("=", limit=2)
				current_arg = parts[0]
				next_arg = parts[1]
			} else if (list.isNotEmpty() && !list[0].startsWith("--")) {
				next_arg = list.removeAt(0)
			}
			
			if (!current_arg.startsWith("--") && current_arg.startsWith("-")) {
				val replacement = substitutions.get(current_arg)
				if (replacement == null) throw RuntimeException("Unknown short parameter ${current_arg}")
				current_arg = replacement
			}
			
			current_arg = current_arg.substring(2)
				
			if (next_arg == null) {
				// current_arg seems to be a boolean value
				booleans.add(current_arg)
				values.put(current_arg, "true")
			} else {
				// current_arg has the value next_arg
				values.put(current_arg, next_arg)
			}
		}
	}
}
