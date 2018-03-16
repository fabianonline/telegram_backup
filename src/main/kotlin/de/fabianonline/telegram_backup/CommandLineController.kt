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

import de.fabianonline.telegram_backup.TelegramUpdateHandler
import de.fabianonline.telegram_backup.exporter.HTMLExporter
import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.api.TelegramApp
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import java.io.File
import java.io.IOException
import java.util.Scanner
import java.util.Vector
import java.util.HashMap
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class CommandLineController(val options: CommandLineOptions) {
	init {
		val storage: ApiStorage
		val app: TelegramApp
		val target_dir: String
		val file_base: String
		val phone_number: String
		val handler: TelegramUpdateHandler
		val client: TelegramClient
		val user_manager: UserManager
		val inisettings: IniSettings
		val database: Database
		logger.info("CommandLineController started. App version {}", Config.APP_APPVER)
		
		printHeader()
		if (options.cmd_version) {
			System.exit(0)
		} else if (options.cmd_help) {
			show_help()
			System.exit(0)
		} else if (options.cmd_license) {
			show_license()
			System.exit(0)
		}
		
		// Setup TelegramApp
		logger.debug("Initializing TelegramApp")
		app = TelegramApp(Config.APP_ID, Config.APP_HASH, Config.APP_MODEL, Config.APP_SYSVER, Config.APP_APPVER, Config.APP_LANG)

		// Setup file_base
		logger.debug("Target dir from Config: {}", Config.TARGET_DIR.anonymize())
		target_dir = options.val_target ?: Config.TARGET_DIR
		logger.debug("Target dir after options: {}", target_dir)
		println("Base directory for files: ${target_dir.anonymize()}")

		if (options.cmd_list_accounts) {
			Utils.print_accounts(target_dir)
			System.exit(0)
		}
		
		if (options.cmd_login) {
			cmd_login(app, target_dir, options.val_account)
		}

		logger.trace("Checking accounts")
		phone_number = try { selectAccount(target_dir, options.val_account)
		} catch(e: AccountNotFoundException) {
			show_error("The specified account could not be found.")
		} catch(e: NoAccountsException) {
			println("No accounts found. Starting login process...")
			cmd_login(app, target_dir, options.val_account)
		}
		
		// TODO: Create a new TelegramApp if the user set his/her own TelegramApp credentials

		// At this point we can assume that the selected user account ("phone_number") exists.
		// So we can create some objects:
		file_base = build_file_base(target_dir, phone_number)

		logger.info("Initializing ApiStorage")
		storage = ApiStorage(file_base)
		
		logger.info("Initializing TelegramUpdateHandler")
		handler = TelegramUpdateHandler()
		
		logger.info("Creating Client")
		client = Kotlogram.getDefaultClient(app, storage, Kotlogram.PROD_DC4, handler)
		
		// From now on we have a new catch-all-block that will terminate it's TelegramClient when an exception happens.
		try {
			logger.info("Initializing UserManager")
			user_manager = UserManager(client)
		
			// TODO
			/*if (!options.cmd_login && !user.loggedIn) {
				println("Your authorization data is invalid or missing. You will have to login with Telegram again.")
				options.cmd_login = true
			}*/
			
			if (phone_number != user_manager.phone) {
				logger.error("phone_number: {}, user_manager.phone: {}", phone_number.anonymize(), user_manager.phone.anonymize())
				show_error("Account / User mismatch")
			}
			
			// Load the ini file.
			inisettings = IniSettings(file_base)
			
			// If we reach this point, we can assume that there is an account and a database can be loaded / created.
			database = Database(file_base, user_manager)
			
			if (options.cmd_stats) {
				cmd_stats(file_base, database)
				System.exit(0)
			}
			
			if (options.val_test != null) {
				if (options.val_test == 1) {
					TestFeatures.test1()
				} else if (options.val_test == 2) {
					TestFeatures.test2()
				} else {
					System.out.println("Unknown test " + options.val_test)
				}
				System.exit(1)
			}
			
			val export = options.val_export
			logger.debug("options.val_export: {}", export)
			if (export != null) {
				if (export.toLowerCase().equals("html")) {
					HTMLExporter().export()
					System.exit(0)
				} else {
					show_error("Unknown export format '${export}'.")
				}
			}
			
			println("You are logged in as ${user_manager.toString().anonymize()}")

			logger.info("Initializing Download Manager")
			val d = DownloadManager(client, CommandLineDownloadProgress(), database, user_manager, inisettings)
			
			if (options.cmd_list_channels) {
				val chats = d.getChats()
				val print_header = {download: Boolean -> println("%-15s %-40s %s".format("ID", "Title", if (download) "Download" else "")); println("-".repeat(65)) }
				val format = {c: DownloadManager.Channel, download: Boolean -> "%-15s %-40s %s".format(c.id.toString().anonymize(), c.title.anonymize(), if (download) (if(c.download) "YES" else "no") else "")}
				var download: Boolean

				println("Channels:")
				download = inisettings.download_channels
				if (!download) println("Download of channels is disabled - see download_channels in config.ini")
				print_header(download)
				for (c in chats.channels) {
					println(format(c, download))
				}
				println()
				println("Supergroups:")
				download = inisettings.download_supergroups
				if (!download) println("Download of supergroups is disabled - see download_supergroups in config.ini")
				print_header(download)
				for (c in chats.supergroups) {
					println(format(c, download))
				}
				System.exit(0)
			}
			
			logger.debug("Calling DownloadManager.downloadMessages with limit {}", options.val_limit_messages)
			d.downloadMessages(options.val_limit_messages)
			logger.debug("IniSettings#download_media: {}", inisettings.download_media)
			if (inisettings.download_media) {
				logger.debug("Calling DownloadManager.downloadMedia")
				d.downloadMedia()
			} else {
				println("Skipping media download because download_media is set to false.")
			}
		} catch (e: Throwable) {
			println("An error occured!")
			e.printStackTrace()
			logger.error("Exception caught!", e)
			// If we encountered an exception, we definitely don't want to start the daemon mode now.
			options.cmd_daemon = false
		} finally {
			if (options.cmd_daemon) {
				handler.activate()
				println("DAEMON mode requested - keeping running.")
			} else {
				client.close()
				println()
				println("----- EXIT -----")
				System.exit(0)
			}
		}
	}

	private fun printHeader() {
		System.out.println("Telegram_Backup version " + Config.APP_APPVER + ", Copyright (C) 2016, 2017 Fabian Schlenz")
		println()
		println("Telegram_Backup comes with ABSOLUTELY NO WARRANTY. This is free software, and you are")
		println("welcome to redistribute it under certain conditions; run it with '--license' for details.")
		println()
	}

	private fun selectAccount(file_base: String, requested_account: String?): String {
		var found_account: String? = null
		val accounts = Utils.getAccounts(file_base)
		if (requested_account != null) {
			logger.debug("Account requested: {}", requested_account.anonymize())
			logger.trace("Checking accounts for match.")
			found_account = accounts.find{it == requested_account}
		} else if (accounts.size == 0) {
			throw NoAccountsException()
		} else if (accounts.size == 1) {
			found_account = accounts.firstElement()
			println("Using only available account: " + found_account.anonymize())
		} else {
			show_error(("You have more than one account but didn't specify which one to use.\n" +
				"Use '--account <x>' to use account <x>.\n" +
				"Use '--list-accounts' to see all available accounts."))
			System.exit(1)
		}
		
		if (found_account == null) {
			throw AccountNotFoundException()
		}
		
		logger.debug("accounts.size: {}", accounts.size)
		logger.debug("account: {}", found_account.anonymize())
		return found_account
	}

	private fun cmd_stats(file_base: String, db: Database) {
		println()
		println("Stats:")
		val format = "%40s: %d%n"
		System.out.format(format, "Number of accounts", Utils.getAccounts(file_base).size)
		System.out.format(format, "Number of messages", db.getMessageCount())
		System.out.format(format, "Number of chats", db.getChatCount())
		System.out.format(format, "Number of users", db.getUserCount())
		System.out.format(format, "Top message ID", db.getTopMessageID())
		println()
		println("Media Types:")
		for ((key, value) in db.getMessageMediaTypesWithCount()) {
			System.out.format(format, key, value)
		}
		println()
		println("Api layers of messages:")
		for ((key, value) in db.getMessageApiLayerWithCount()) {
			System.out.format(format, key, value)
		}
		println()
		println("Message source types:")
		for ((key, value) in db.getMessageSourceTypeWithCount()) {
			System.out.format(format, key, value)
		}
	}

	private fun cmd_login(app: TelegramApp, target_dir: String, phoneToUse: String?): Nothing {
		LoginManager(app, target_dir, phoneToUse).run()
		System.exit(0)
		throw RuntimeException("Code never reaches this. This exists just to keep the Kotlin compiler happy.")
	}

	private fun show_help() {
		println("Valid options are:")
		println(" -h, --help            Shows this help.")
		println(" -a, --account <x>     Use account <x>.")
		println(" -l, --login           Login to an existing telegram account.")
		println(" --debug               Shows some debug information.")
		println(" --trace               Shows lots of debug information. Overrides --debug.")
		println(" --trace-telegram      Shows lots of debug messages from the library used to access Telegram.")
		println(" -A, --list-accounts   List all existing accounts ")
		println(" --limit-messages <x>  Downloads at most the most recent <x> messages.")
		println(" -t, --target <x>      Target directory for the files.")
		println(" -e, --export <format> Export the database. Valid formats are:")
		println("                html - Creates HTML files.")
		println(" --license             Displays the license of this program.")
		println(" -d, --daemon          Keep running after the backup and automatically save new messages.")
		println(" --anonymize           (Try to) Remove all sensitive information from output. Useful for requesting support.")
		println(" --stats               Print some usage statistics.")
		println(" --list-channels       Lists all channels together with their ID")
	}

	companion object {
		private val logger = LoggerFactory.getLogger(CommandLineController::class.java)

		public fun show_error(error: String): Nothing {
			logger.error(error)
			println("ERROR: " + error)
			System.exit(1)
			throw RuntimeException("Code never reaches this. This exists just to keep the Kotlin compiler happy.")
		}

		fun show_license() {
			println("TODO: Print the GPL.")
		}
		
		fun build_file_base(target_dir: String, account_to_use: String) = target_dir + File.separatorChar + account_to_use + File.separatorChar
	}

	class AccountNotFoundException() : Exception("Account not found") {}
	class NoAccountsException() : Exception("No accounts found") {}
}
