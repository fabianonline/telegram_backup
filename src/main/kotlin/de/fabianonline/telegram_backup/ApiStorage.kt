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

import com.github.badoualy.telegram.api.TelegramApiStorage
import com.github.badoualy.telegram.mtproto.model.DataCenter
import com.github.badoualy.telegram.mtproto.auth.AuthKey
import com.github.badoualy.telegram.mtproto.model.MTSession

import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class ApiStorage(val base_dir: String) : TelegramApiStorage {
	var auth_key: AuthKey? = null
	var dc: DataCenter? = null
	val file_auth_key: File
	val file_dc: File
	val logger = LoggerFactory.getLogger(ApiStorage::class.java)
	                                   
	
	init {
		file_auth_key = File(base_dir + Config.FILE_NAME_AUTH_KEY)
		file_dc = File(base_dir + Config.FILE_NAME_DC)
	}

	override fun saveAuthKey(authKey: AuthKey) {
		FileUtils.writeByteArrayToFile(file_auth_key, authKey.key)
	}

	override fun loadAuthKey(): AuthKey? {
		try {
			return AuthKey(FileUtils.readFileToByteArray(file_auth_key))
		} catch (e: FileNotFoundException) {
			return null
		}
	}

	override fun saveDc(dataCenter: DataCenter) {
		FileUtils.write(file_dc, dataCenter.toString())
	}

	override fun loadDc(): DataCenter? {
		try {
			val infos = FileUtils.readFileToString(this.file_dc).split(":")
			return DataCenter(infos[0], Integer.parseInt(infos[1]))
		} catch (e: FileNotFoundException) {
			return null
		}
	}

	override fun deleteAuthKey() {
		try {
			FileUtils.forceDelete(file_auth_key)
		} catch (e: IOException) {
			logger.warn("Exception in deleteAuthKey(): {}", e)
		}
	}

	override fun deleteDc() {
		try {
			FileUtils.forceDelete(file_dc)
		} catch (e: IOException) {
			logger.warn("Exception in deleteDc(): {}", e)
		}
	}

	override fun saveSession(session: MTSession?) {}

	override fun loadSession(): MTSession? {
		return null
	}
}
