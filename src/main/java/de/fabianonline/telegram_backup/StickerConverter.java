package de.fabianonline.telegram_backup;

import com.github.badoualy.telegram.tl.api.*;
import java.lang.StringBuilder;
import java.io.File;

class StickerConverter {
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
