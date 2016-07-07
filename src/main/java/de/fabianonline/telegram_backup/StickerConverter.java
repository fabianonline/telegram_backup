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

package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.tl.api.*;
import java.lang.StringBuilder;
import java.io.File;

public class StickerConverter {
	public static String makeFilenameWithPath(TLDocumentAttributeSticker attr) {
		StringBuilder file = new StringBuilder();
		file.append(makePath());
		file.append(makeFilename(attr));
		return file.toString();
	}
	
	public static String makeFilename(TLDocumentAttributeSticker attr) {
		StringBuilder file = new StringBuilder();
		if (attr.getStickerset() instanceof TLInputStickerSetShortName) {
			file.append(((TLInputStickerSetShortName)attr.getStickerset()).getShortName());
		} else if (attr.getStickerset() instanceof TLInputStickerSetID) {
			file.append(((TLInputStickerSetID)attr.getStickerset()).getId());
		}
		file.append("_");
		file.append(attr.getAlt().hashCode());
		file.append(".webp");
		return file.toString();
	}
	
	public static String makePath() {
		String path = Config.FILE_BASE +
			File.separatorChar +
			Config.FILE_STICKER_BASE +
			File.separatorChar;
		new File(path).mkdirs();
		return path;
	}
}
