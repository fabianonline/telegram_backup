/* Telegram_Backup
* Copyright (C) 2016 Fabian Schlenz, 2019 Bohdan Horbeshko
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
import java.util.LinkedList
import java.util.HashMap
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class CommandLineController {
	private val storage: ApiStorage
	var app: TelegramApp

	private fun getLine(): String {
		if (System.console() != null) {
			return System.console().readLine("> ")
		} else {
			print("> ")
			return Scanner(System.`in`).nextLine()
		}
	}

	private fun getPassword(): String {
		if (System.console() != null) {
			return String(System.console().readPassword("> "))
		} else {
			return getLine()
		}
	}

	init {
		logger.info("CommandLineController started. App version {}", Config.APP_APPVER)
		
		this.printHeader()
		if (CommandLineOptions.cmd_version) {
			System.exit(0)
		} else if (CommandLineOptions.cmd_help) {
			this.show_help()
			System.exit(0)
		} else if (CommandLineOptions.cmd_license) {
			CommandLineController.show_license()
			System.exit(0)
		}
		this.setupFileBase()
		if (CommandLineOptions.cmd_list_accounts) {
			this.list_accounts()
			System.exit(0)
		}
		logger.debug("Initializing TelegramApp")
		app = TelegramApp(Config.APP_ID, Config.APP_HASH, Config.APP_MODEL, Config.APP_SYSVER, Config.APP_APPVER, Config.APP_LANG)
		logger.trace("Checking accounts")
		val account = this.selectAccount()
		logger.debug("CommandLineOptions.cmd_login: {}", CommandLineOptions.cmd_login)
		logger.info("Initializing ApiStorage")
		storage = ApiStorage(account)
		logger.info("Initializing TelegramUpdateHandler")
		val handler = TelegramUpdateHandler()
		logger.info("Creating Client")
		val client = Kotlogram.getDefaultClient(app, storage, Kotlogram.PROD_DC4, handler)
		try {
			logger.info("Initializing UserManager")
			UserManager.init(client)
			val user = UserManager.getInstance()
			if (!CommandLineOptions.cmd_login && !user.loggedIn) {
				println("Your authorization data is invalid or missing. You will have to login with Telegram again.")
				CommandLineOptions.cmd_login = true
			}
			if (account != null && user.loggedIn) {
				if (account != "+" + user.user!!.getPhone()) {
					logger.error("Account: {}, user.user!!.getPhone(): +{}", account.anonymize(), user.user!!.getPhone().anonymize())
					throw RuntimeException("Account / User mismatch")
				}
			}
			logger.debug("CommandLineOptions.cmd_login: {}", CommandLineOptions.cmd_login)
			if (CommandLineOptions.cmd_login) {
				cmd_login(CommandLineOptions.val_account)
				System.exit(0)
			}
			// If we reach this point, we can assume that there is an account and a database can be loaded / created.
			Database.init(client)
			if (CommandLineOptions.cmd_stats) {
				cmd_stats()
				System.exit(0)
			}
			if (CommandLineOptions.val_test != null) {
				if (CommandLineOptions.val_test == 1) {
					TestFeatures.test1()
				} else if (CommandLineOptions.val_test == 2) {
					TestFeatures.test2()
				} else {
					System.out.println("Unknown test " + CommandLineOptions.val_test)
				}
				System.exit(1)
			}
			logger.debug("CommandLineOptions.val_export: {}", CommandLineOptions.val_export)
			if (CommandLineOptions.val_export != null) {
				if (CommandLineOptions.val_export!!.toLowerCase().equals("html")) {
					(HTMLExporter()).export()
					System.exit(0)
				} else {
					show_error("Unknown export format.")
				}
			}
			if (user.loggedIn) {
				System.out.println("You are logged in as ${user.userString.anonymize()}")
			} else {
				println("You are not logged in.")
				System.exit(1)
			}
			logger.info("Initializing Download Manager")
			val d = DownloadManager(client, CommandLineDownloadProgress())
			logger.debug("Calling DownloadManager.downloadMessages with limit {}", CommandLineOptions.val_limit_messages)
			d.downloadMessages(CommandLineOptions.val_limit_messages)
			logger.debug("CommandLineOptions.cmd_only_my_media: {}", CommandLineOptions.cmd_only_my_media)
			logger.debug("CommandLineOptions.cmd_no_media: {}", CommandLineOptions.cmd_no_media)
			logger.debug("CommandLineOptions.cmd_no_stickers: {}", CommandLineOptions.cmd_no_stickers)
			if (!CommandLineOptions.cmd_no_media) {
				logger.debug("Calling DownloadManager.downloadMedia")
				var filters = LinkedList<MediaFilter>()
				if (CommandLineOptions.cmd_only_my_media) {
					filters.add(MediaFilter.ONLY_MY)
				}
				if (CommandLineOptions.cmd_no_stickers) {
					filters.add(MediaFilter.NO_STICKERS)
				}
				d.downloadMedia(filters)
			} else {
				println("Skipping media download because --no-media is set.")
			}
		} catch (e: Throwable) {
			println("An error occured!")
			e.printStackTrace()
			logger.error("Exception caught!", e)
			// If we encountered an exception, we definitely don't want to start the daemon mode now.
			CommandLineOptions.cmd_daemon = false
		} finally {
			if (CommandLineOptions.cmd_daemon) {
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

	private fun setupFileBase() {
		logger.debug("Target dir at startup: {}", Config.FILE_BASE.anonymize())
		if (CommandLineOptions.val_target != null) {
			Config.FILE_BASE = CommandLineOptions.val_target!!
		}
		logger.debug("Target dir after options: {}", Config.FILE_BASE.anonymize())
		System.out.println("Base directory for files: " + Config.FILE_BASE.anonymize())
	}

	private fun selectAccount(): String? {
		var account = "none"
		val accounts = Utils.getAccounts()
		if (CommandLineOptions.cmd_login) {
			logger.debug("Login requested, doing nothing.")
			// do nothing
		} else if (CommandLineOptions.val_account != null) {
			logger.debug("Account requested: {}", CommandLineOptions.val_account!!.anonymize())
			logger.trace("Checking accounts for match.")
			var found = false
			for (acc in accounts) {
				logger.trace("Checking {}", acc.anonymize())
				if (acc == CommandLineOptions.val_account) {
					found = true
					logger.trace("Matches.")
					break
				}
			}
			if (!found) {
				show_error("Couldn't find account '" + CommandLineOptions.val_account!!.anonymize() + "'. Maybe you want to use '--login' first?")
			}
			account = CommandLineOptions.val_account!!
		} else if (accounts.size == 0) {
			println("No accounts found. Starting login process...")
			CommandLineOptions.cmd_login = true
			return null
		} else if (accounts.size == 1) {
			account = accounts.firstElement()
			System.out.println("Using only available account: " + account.anonymize())
		} else {
			show_error(("You didn't specify which account to use.\n" +
				"Use '--account <x>' to use account <x>.\n" +
				"Use '--list-accounts' to see all available accounts."))
			System.exit(1)
		}
		logger.debug("accounts.size: {}", accounts.size)
		logger.debug("account: {}", account.anonymize())
		return account
	}

	private fun cmd_stats() {
		println()
		println("Stats:")
		val format = "%40s: %d%n"
		System.out.format(format, "Number of accounts", Utils.getAccounts().size)
		System.out.format(format, "Number of messages", Database.getInstance().getMessageCount())
		System.out.format(format, "Number of chats", Database.getInstance().getChatCount())
		System.out.format(format, "Number of users", Database.getInstance().getUserCount())
		System.out.format(format, "Top message ID", Database.getInstance().getTopMessageID())
		println()
		println("Media Types:")
		for ((key, value) in Database.getInstance().getMessageMediaTypesWithCount()) {
			System.out.format(format, key, value)
		}
		println()
		println("Api layers of messages:")
		for ((key, value) in Database.getInstance().getMessageApiLayerWithCount()) {
			System.out.format(format, key, value)
		}
		println()
		println("Message source types:")
		for ((key, value) in Database.getInstance().getMessageSourceTypeWithCount()) {
			System.out.format(format, key, value)
		}
	}

	@Throws(RpcErrorException::class, IOException::class)
	private fun cmd_login(phoneToUse: String?) {
		val user = UserManager.getInstance()
		val phone: String
		if (phoneToUse == null) {
			println("Please enter your phone number in international format.")
			println("Example: +4917077651234")
			phone = getLine()
		} else {
			phone = phoneToUse
		}
		user.sendCodeToPhoneNumber(phone)
		println("Telegram sent you a code. Please enter it here.")
		val code = getLine()
		user.verifyCode(code)
		if (user.isPasswordNeeded) {
			println("We also need your account password. Please enter it now. It should not be printed, so it's okay if you see nothing while typing it.")
			val pw = getPassword()
			user.verifyPassword(pw)
		}
		storage.setPrefix("+" + user.user!!.getPhone())
		System.out.println("Everything seems fine. Please run this tool again with '--account +" + user.user!!.getPhone().anonymize() + " to use this account.")
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
		println(" --only-my-media       Download only media files sent by this account.")
		println(" --no-media            Do not download media files.")
		println(" --no-stickers         Do not download stickers.")
		println(" -t, --target <x>      Target directory for the files.")
		println(" -e, --export <format> Export the database. Valid formats are:")
		println("                html - Creates HTML files.")
		println(" --pagination <x>      Splits the HTML export into multiple HTML pages with <x> messages per page. Default is 5000.")
		println(" --no-pagination       Disables pagination.")
		println(" --license             Displays the license of this program.")
		println(" -d, --daemon          Keep running after the backup and automatically save new messages.")
		println(" --anonymize           (Try to) Remove all sensitive information from output. Useful for requesting support.")
		println(" --stats               Print some usage statistics.")
		println(" --with-channels       Backup channels as well.")
		println(" --with-supergroups    Backup supergroups as well.")
	}

	private fun list_accounts() {
		println("List of available accounts:")
		val accounts = Utils.getAccounts()
		if (accounts.size > 0) {
			for (str in accounts) {
				System.out.println(" " + str.anonymize())
			}
			println("Use '--account <x>' to use one of those accounts.")
		} else {
			println("NO ACCOUNTS FOUND")
			println("Use '--login' to login to a telegram account.")
		}
	}

	companion object {
		private val logger = LoggerFactory.getLogger(CommandLineController::class.java)

		public fun show_error(error: String) {
			logger.error(error)
			println("ERROR: " + error)
			System.exit(1)
		}

		fun show_license() {
			println("TODO: Print the GPL.")
		}
	}
}
