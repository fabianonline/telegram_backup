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

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.api.auth.TLSentCode
import com.github.badoualy.telegram.tl.api.auth.TLAuthorization
import com.github.badoualy.telegram.tl.api.TLUser
import com.github.badoualy.telegram.tl.api.TLUserFull
import com.github.badoualy.telegram.tl.api.TLInputUserSelf
import com.github.badoualy.telegram.tl.api.account.TLPassword
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import com.github.badoualy.telegram.tl.core.TLBytes

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.io.IOException
import java.io.File

import org.slf4j.LoggerFactory
import org.slf4j.Logger

class UserManager @Throws(IOException::class)
private constructor(c: TelegramClient) {
	var user: TLUser? = null
	var phone: String? = null
	private var code: String? = null
	private val client: TelegramClient
	private var sent_code: TLSentCode? = null
	private var auth: TLAuthorization? = null
	var isPasswordNeeded = false
		private set

	val loggedIn: Boolean
		get() = user != null

	val userString: String
		get() {
			if (this.user == null) return "Not logged in"
			val sb = StringBuilder()
			if (this.user!!.getFirstName() != null) {
				sb.append(this.user!!.getFirstName())
			}
			if (this.user!!.getLastName() != null) {
				sb.append(" ")
				sb.append(this.user!!.getLastName())
			}
			if (this.user!!.getUsername() != null) {
				sb.append(" (@")
				sb.append(this.user!!.getUsername())
				sb.append(")")
			}
			return sb.toString()
		}

	val fileBase: String
		get() = Config.FILE_BASE + File.separatorChar + "+" + this.user!!.getPhone() + File.separatorChar

	init {
		this.client = c
		logger.debug("Calling getFullUser")
		try {
			val full_user = this.client.usersGetFullUser(TLInputUserSelf())
			this.user = full_user.getUser().getAsUser()
		} catch (e: RpcErrorException) {
			// This may happen. Ignoring it.
			logger.debug("Ignoring exception:", e)
		}

	}

	@Throws(RpcErrorException::class, IOException::class)
	fun sendCodeToPhoneNumber(number: String) {
		this.phone = number
		this.sent_code = this.client.authSendCode(false, number, true)
	}

	@Throws(RpcErrorException::class, IOException::class)
	fun verifyCode(code: String) {
		this.code = code
		try {
			this.auth = client.authSignIn(phone, this.sent_code!!.getPhoneCodeHash(), this.code)
			this.user = auth!!.getUser().getAsUser()
		} catch (e: RpcErrorException) {
			if (e.getCode() != 401 || !e.getTag().equals("SESSION_PASSWORD_NEEDED")) throw e
			this.isPasswordNeeded = true
		}

	}

	@Throws(RpcErrorException::class, IOException::class)
	fun verifyPassword(pw: String) {
		val password = pw.toByteArray(charset = Charsets.UTF_8)
		val salt = (client.accountGetPassword() as TLPassword).getCurrentSalt().getData()
		var md: MessageDigest
		try {
			md = MessageDigest.getInstance("SHA-256")
		} catch (e: NoSuchAlgorithmException) {
			e.printStackTrace()
			return
		}

		val salted = ByteArray(2 * salt.size + password.size)
		System.arraycopy(salt, 0, salted, 0, salt.size)
		System.arraycopy(password, 0, salted, salt.size, password.size)
		System.arraycopy(salt, 0, salted, salt.size + password.size, salt.size)
		val hash = md.digest(salted)
		auth = client.authCheckPassword(TLBytes(hash))
		this.user = auth!!.getUser().getAsUser()
	}

	companion object {
		private val logger = LoggerFactory.getLogger(UserManager::class.java)
		internal var instance: UserManager? = null

		@Throws(IOException::class)
		fun init(c: TelegramClient) {
			instance = UserManager(c)
		}

		fun getInstance(): UserManager {
			if (instance == null) throw RuntimeException("UserManager is not yet initialized.")
			return instance!!
		}
	}
}
