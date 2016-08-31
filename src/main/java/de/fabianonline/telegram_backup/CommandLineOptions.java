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

package de.fabianonline.telegram_backup;

class CommandLineOptions {
	public static boolean cmd_console        = false;
	public static boolean cmd_help           = false;
	public static boolean cmd_login          = false;
	public static boolean cmd_debug          = false;
	public static boolean cmd_debug_telegram = false;
	public static boolean cmd_list_accounts  = false;
	public static boolean cmd_version        = false;
	public static boolean cmd_license        = false;
	public static boolean cmd_daemon         = false;
	public static boolean cmd_no_media       = false;
	
	public static String  val_account        = null;
	public static Integer val_limit_messages = null;
	public static String  val_target         = null;
	public static String  val_export         = null;

	public static void parseOptions(String[] args) {
		String last_cmd = null;

		for (String arg : args) {
			if (last_cmd != null) {
				switch (last_cmd) {
					case "--account":
						val_account = arg;                          break;
						
					case "--limit-messages":
						val_limit_messages = Integer.parseInt(arg); break;
						
					case "--target":
						val_target = arg;                           break;
						
					case "--export":
						val_export = arg;                           break;
				}
				last_cmd = null;
				continue;
			}

			switch (arg) {
				case "-a": case "--account":
					last_cmd = "--account";         continue;
					
				case "-h": case "--help":
					cmd_help = true;                break;
					
				case "-l": case "--login":
					cmd_login = true;               break;
					
				case "--debug":
					cmd_debug = true;               break;
				
				case "--debug-telegram":
					cmd_debug_telegram = true;      break;
					
				case "-A": case "--list-accounts":
					cmd_list_accounts = true;       break;
					
				case "--limit-messages":
					last_cmd = arg;                 continue;
					
				case "--console":
					cmd_console = true;             break;
					
				case "-t": case "--target":
					last_cmd = "--target";          continue;
					
				case "-V": case "--version":
					cmd_version = true;             break;
				
				case "-e": case "--export":
					last_cmd = "--export";          continue;
				
				case "--license":
					cmd_license = true;             break;
				
				case "-d": case "--daemon":
					cmd_daemon = true;              break;
				
				case "--no-media":
					cmd_no_media = true;            break;
					
				default:
					throw new RuntimeException("Unknown command " + arg);
			}
		}
		if (last_cmd != null) {
			CommandLineController.show_error("Command " + last_cmd + " had no parameter set.");
		}
	}
}
