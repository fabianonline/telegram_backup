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

public class Log {
	static int level = 0;
	static final int factor = 2;

	public static void up() { level++; }
	public static void down() { level--; if (level<0) level=0; }

	public static void debug(String s, Object...  o) {
		if (!CommandLineOptions.cmd_debug) return;
		Object o2[] = new Object[o.length+1];
		System.arraycopy(o, 0, o2, 0, o.length);
		o2[o2.length-1]="";
		String format = "DEBUG:" + '%' + o2.length + "$" + (level*factor+1) + "s" + s + "\n";
		System.out.printf(format, o2);
	}
}
