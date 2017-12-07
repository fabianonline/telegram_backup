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

import de.fabianonline.telegram_backup.DownloadProgressInterface
import de.fabianonline.telegram_backup.mediafilemanager.AbstractMediaFileManager

internal class CommandLineDownloadProgress : DownloadProgressInterface {
    private var mediaCount = 0
    private var i = 0

    fun onMessageDownloadStart(count: Int, source: String?) {
        i = 0
        if (source == null) {
            System.out.println("Downloading $count messages.")
        } else {
            System.out.println("Downloading " + count + " messages from " + Utils.anonymize(source))
        }
    }

    fun onMessageDownloaded(number: Int) {
        i += number
        System.out.print("..." + i)
    }

    fun onMessageDownloadFinished() {
        System.out.println(" done.")
    }

    fun onMediaDownloadStart(count: Int) {
        i = 0
        mediaCount = count
        System.out.println("Checking and downloading media.")
        System.out.println("Legend:")
        System.out.println("'V' - Video         'P' - Photo         'D' - Document")
        System.out.println("'S' - Sticker       'A' - Audio         'G' - Geolocation")
        System.out.println("'.' - Previously downloaded file        'e' - Empty file")
        System.out.println("' ' - Ignored media type (weblinks or contacts, for example)")
        System.out.println("'x' - File skipped because of timeout errors")
        System.out.println("" + count + " Files to check / download")
    }

    fun onMediaDownloaded(fm: AbstractMediaFileManager) {
        show(fm.getLetter().toUpperCase())
    }

    fun onMediaDownloadedEmpty() {
        show("e")
    }

    fun onMediaAlreadyPresent(fm: AbstractMediaFileManager) {
        show(".")
    }

    fun onMediaSkipped() {
        show("x")
    }

    fun onMediaDownloadFinished() {
        showNewLine()
        System.out.println("Done.")
    }

    private fun show(letter: String) {
        System.out.print(letter)
        i++
        if (i % 100 == 0) showNewLine()
    }

    private fun showNewLine() {
        System.out.println(" - $i/$mediaCount")
    }
}
