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

class UserManager(val client: TelegramClient) {
	val tl_user: TLUser
	val logger = LoggerFactory.getLogger(UserManager::class.java)
	val phone: String
		get() = "+" + tl_user.getPhone()
	val id: Int
		get() = tl_user.getId()
	
	init {
		logger.debug("Calling getFullUser")
		val full_user = client.usersGetFullUser(TLInputUserSelf())
		tl_user = full_user.getUser().getAsUser()
	}

	override fun toString(): String {
		val sb = StringBuilder()
		sb.append(tl_user.getFirstName() ?: "")
		if (tl_user.getLastName() != null) {
			sb.append(" ")
			sb.append(tl_user.getLastName())
		}
		if (tl_user.getUsername() != null) {
			sb.append(" (@")
			sb.append(tl_user.getUsername())
			sb.append(")")
		}
		return sb.toString()
	}
	
	
}
