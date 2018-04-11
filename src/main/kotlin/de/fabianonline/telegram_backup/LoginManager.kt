package de.fabianonline.telegram_backup

import com.github.badoualy.telegram.api.Kotlogram
import com.github.badoualy.telegram.api.TelegramApp
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.api.TLUser
import com.github.badoualy.telegram.tl.api.account.TLPassword
import com.github.badoualy.telegram.tl.api.auth.TLSentCode
import com.github.badoualy.telegram.tl.core.TLBytes
import com.github.badoualy.telegram.tl.exception.RpcErrorException
import java.security.MessageDigest
import java.util.*

class LoginManager(val app: TelegramApp, val target_dir: String, val phoneToUse: String?) {
	fun run() {
		var phone: String
		
		if (phoneToUse == null) {
			println("Please enter your phone number in international format.")
			println("Example: +4917077651234")
			phone = getLine()
		} else {
			phone = phoneToUse
		}
		
		val file_base = CommandLineController.build_file_base(target_dir, phone)
		
		// We now have an account, so we can create an ApiStorage and TelegramClient.
		val storage = ApiStorage(file_base)
		val client = Kotlogram.getDefaultClient(app, storage, Kotlogram.PROD_DC4, null)
		
		val sent_code = send_code_to_phone_number(client, phone)
		println("Telegram sent you a code. Please enter it here.")
		val code = getLine()
		
		try {
			verify_code(client, phone, sent_code, code)
		} catch(e: PasswordNeededException) {
			println("We also need your account password. Please enter it now. It should not be printed, so it's okay if you see nothing while typing it.")
			val pw = getPassword()
			verify_password(client, pw)
		}
		System.out.println("Everything seems fine. Please run this tool again with '--account ${phone} to use this account.")
	}
	
	private fun send_code_to_phone_number(client: TelegramClient, phone: String): TLSentCode {
		return client.authSendCode(false, phone, true)
	}
	
	private fun verify_code(client: TelegramClient, phone: String, sent_code: TLSentCode, code: String): TLUser {
		try {
			val auth = client.authSignIn(phone, sent_code.getPhoneCodeHash(), code)
			return auth.getUser().getAsUser()
		} catch (e: RpcErrorException) {
			if (e.getCode() == 401 && e.getTag()=="SESSION_PASSWORD_NEEDED") {
				throw PasswordNeededException()
			} else {
				throw e
			}
		}
	}
	
	private fun verify_password(client: TelegramClient, password: String): TLUser {
		val pw = password.toByteArray(charset = Charsets.UTF_8)
		val salt = (client.accountGetPassword() as TLPassword).getCurrentSalt().getData()
		val md = MessageDigest.getInstance("SHA-256")
		
		val salted = ByteArray(2 * salt.size + pw.size)
		System.arraycopy(salt, 0, salted, 0, salt.size)
		System.arraycopy(pw, 0, salted, salt.size, pw.size)
		System.arraycopy(salt, 0, salted, salt.size + pw.size, salt.size)
		val hash = md.digest(salted)
		val auth = client.authCheckPassword(TLBytes(hash))
		return auth.getUser().getAsUser()
	}
			
	
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
}

class PasswordNeededException: Exception("A password is needed to be able to login to this account.") {}
