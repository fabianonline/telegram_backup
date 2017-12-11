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

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class ApiStorage(prefix: String?) : TelegramApiStorage {
    private var prefix: String? = null
    private var do_save = false
    private var auth_key: AuthKey? = null
    private var dc: DataCenter? = null
    private var file_auth_key: File? = null
    private var file_dc: File? = null

    init {
        this.setPrefix(prefix)
    }

    fun setPrefix(prefix: String?) {
        this.prefix = prefix
        this.do_save = this.prefix != null
        if (this.do_save) {
            val base = Config.FILE_BASE +
                    File.separatorChar +
                    this.prefix +
                    File.separatorChar
            this.file_auth_key = File(base + Config.FILE_NAME_AUTH_KEY)
            this.file_dc = File(base + Config.FILE_NAME_DC)
            this._saveAuthKey()
            this._saveDc()
        } else {
            this.file_auth_key = null
            this.file_dc = null
        }
    }

    override fun saveAuthKey(authKey: AuthKey) {
        this.auth_key = authKey
        this._saveAuthKey()
    }

    private fun _saveAuthKey() {
        if (this.do_save && this.auth_key != null) {
            try {
                FileUtils.writeByteArrayToFile(this.file_auth_key, this.auth_key!!.key)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    override fun loadAuthKey(): AuthKey? {
        if (this.auth_key != null) return this.auth_key
        if (this.file_auth_key != null) {
            try {
                return AuthKey(FileUtils.readFileToByteArray(this.file_auth_key))
            } catch (e: IOException) {
                if (e !is FileNotFoundException) e.printStackTrace()
            }

        }

        return null
    }

    override fun saveDc(dataCenter: DataCenter) {
        this.dc = dataCenter
        this._saveDc()
    }

    private fun _saveDc() {
        if (this.do_save && this.dc != null) {
            try {
                FileUtils.write(this.file_dc, this.dc!!.toString())
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    override fun loadDc(): DataCenter? {
        if (this.dc != null) return this.dc
        if (this.file_dc != null) {
            try {
                val infos = FileUtils.readFileToString(this.file_dc).split(":")
                return DataCenter(infos[0], Integer.parseInt(infos[1]))
            } catch (e: IOException) {
                if (e !is FileNotFoundException) e.printStackTrace()
            }

        }
        return null
    }

    override fun deleteAuthKey() {
        if (this.do_save) {
            try {
                FileUtils.forceDelete(this.file_auth_key)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    override fun deleteDc() {
        if (this.do_save) {
            try {
                FileUtils.forceDelete(this.file_dc)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    override fun saveSession(session: MTSession?) {}

    override fun loadSession(): MTSession? {
        return null
    }
}
