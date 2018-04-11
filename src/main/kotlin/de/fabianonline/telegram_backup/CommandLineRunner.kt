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

package de.fabianonline.telegram_backup

import de.fabianonline.telegram_backup.CommandLineController
import de.fabianonline.telegram_backup.Utils
import de.fabianonline.telegram_backup.Version
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.classic.Level

fun main(args: Array<String>) {
	val clr = CommandLineRunner(args)
	
	clr.setupLogging()
	clr.checkVersion()
	clr.run()
}

class CommandLineRunner(args: Array<String>) {
	val logger = LoggerFactory.getLogger(CommandLineRunner::class.java) as Logger
	val options = CommandLineOptions(args)
	
	fun run() {
		// Always use the console for now.
		try {
			CommandLineController(options)
		} catch (e: Throwable) {
			println("An error occured!")
			e.printStackTrace()
			logger.error("Exception caught!", e)
			System.exit(1)
		}
	}
	
	fun setupLogging() {
		if (options.isSet("anonymize")) {
			Utils.anonymize = true
		}
	
	
		logger.trace("Setting up Loggers...")
		val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
		val rootContext = rootLogger.getLoggerContext()
		rootContext.reset()

		val encoder = PatternLayoutEncoder()
		encoder.setContext(rootContext)
		encoder.setPattern("%d{HH:mm:ss.SSS} %-5level %-35.-35(%logger{0}.%method): %message%n")
		encoder.start()

		val appender = ConsoleAppender<ILoggingEvent>()
		appender.setContext(rootContext)
		appender.setEncoder(encoder)
		appender.start()

		rootLogger.addAppender(appender)
		rootLogger.setLevel(Level.OFF)

		if (options.isSet("trace")) {
			(LoggerFactory.getLogger("de.fabianonline.telegram_backup") as Logger).setLevel(Level.TRACE)
		} else if (options.isSet("debug")) {
			(LoggerFactory.getLogger("de.fabianonline.telegram_backup") as Logger).setLevel(Level.DEBUG)
		}

		if (options.isSet("trace_telegram")) {
			(LoggerFactory.getLogger("com.github.badoualy") as Logger).setLevel(Level.TRACE)
		}
	}

	fun checkVersion(): Boolean {
		val v = Utils.getNewestVersion()
		if (v != null && v.isNewer) {
			println()
			println()
			println()
			println("A newer version is vailable!")
			println("You are using: " + Config.APP_APPVER)
			println("Available:     " + v.version)
			println("Get it here:   " + v.url)
			println()
			println()
			println("Changes in this version:")
			println(v.body)
			println()
			println()
			println()
			TimeUnit.SECONDS.sleep(5)
			return false
		}
		return true
	}
}
