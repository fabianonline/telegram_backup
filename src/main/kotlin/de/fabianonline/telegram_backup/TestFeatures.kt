package de.fabianonline.telegram_backup

import com.github.badoualy.telegram.tl.api.*
import com.github.badoualy.telegram.api.TelegramClient
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.SQLException
import java.sql.ResultSet
import java.io.IOException
import java.nio.charset.Charset

internal class TestFeatures(val db: Database) {
	fun test1() {
		// Tests entries in a cache4.db in the current working directory for compatibility
		try {
			Class.forName("org.sqlite.JDBC")
		} catch (e: ClassNotFoundException) {
			CommandLineController.show_error("Could not load jdbc-sqlite class.")
		}

		val path = "jdbc:sqlite:cache4.db"

		var conn: Connection
		var stmt: Statement? = null

		conn = DriverManager.getConnection(path)
		stmt = conn.createStatement()

		var unsupported_constructor = 0
		var success = 0

		val rs = stmt.executeQuery("SELECT data FROM messages")
		while (rs.next()) {
			try {
				TLApiContext.getInstance().deserializeMessage(rs.getBytes(1))
			} catch (e: com.github.badoualy.telegram.tl.exception.UnsupportedConstructorException) {
				unsupported_constructor++
			} catch (e: IOException) {
				System.out.println("IOException: " + e)
			}
			success++
		}

		System.out.println("Success:                 " + success)
		System.out.println("Unsupported constructor: " + unsupported_constructor)
	}

	fun test2() {
		// Prints system.encoding and default charset
		System.out.println("Default Charset:   " + Charset.defaultCharset())
		System.out.println("file.encoding:     " + System.getProperty("file.encoding"))
		System.out.println("Database encoding: " + db.getEncoding())
	}
}
