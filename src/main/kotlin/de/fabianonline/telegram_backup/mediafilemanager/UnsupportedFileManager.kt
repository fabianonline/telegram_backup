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

package de.fabianonline.telegram_backup.mediafilemanager

import de.fabianonline.telegram_backup.UserManager

import com.github.badoualy.telegram.tl.api.*

class UnsupportedFileManager(msg: TLMessage, user: UserManager, type: String, file_base: String) : AbstractMediaFileManager(msg, user, file_base) {
	override var name = type
	override val targetFilename = ""
	override val targetPath = ""
	override val extension = ""
	override val size = 0
	override var isEmpty = false
	override val downloaded = false
	override val letter = " "
	override val description = "Unsupported / non-downloadable Media"

	override fun download(): Boolean = true
}
