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

    override fun onMessageDownloadStart(count: Int, source: String?) {
        i = 0
        if (source == null) {
            System.out.println("Downloading $count messages.")
        } else {
            System.out.println("Downloading " + count + " messages from " + Utils.anonymize(source))
        }
    }

    override fun onMessageDownloaded(number: Int) {
        i += number
        print("..." + i)
    }

    override fun onMessageDownloadFinished() {
        println(" done.")
    }

    override fun onMediaDownloadStart(count: Int) {
        i = 0
        mediaCount = count
        println("Checking and downloading media.")
        println("Legend:")
        println("'V' - Video         'P' - Photo         'D' - Document")
        println("'S' - Sticker       'A' - Audio         'G' - Geolocation")
        println("'.' - Previously downloaded file        'e' - Empty file")
        println("' ' - Ignored media type (weblinks or contacts, for example)")
        println("'x' - File skipped because of timeout errors")
        println("" + count + " Files to check / download")
    }

    override fun onMediaDownloaded(fm: AbstractMediaFileManager) {
        show(fm.letter.toUpperCase())
    }

    override fun onMediaDownloadedEmpty() {
        show("e")
    }

    override fun onMediaAlreadyPresent(fm: AbstractMediaFileManager) {
        show(".")
    }

    override fun onMediaSkipped() {
        show("x")
    }

    override fun onMediaDownloadFinished() {
        showNewLine()
        println("Done.")
    }

    private fun show(letter: String) {
        print(letter)
        i++
        if (i % 100 == 0) showNewLine()
    }

    private fun showNewLine() {
        println(" - $i/$mediaCount")
    }
}
