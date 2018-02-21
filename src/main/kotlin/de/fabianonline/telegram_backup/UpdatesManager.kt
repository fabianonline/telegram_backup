package de.fabianonline.telegram_backup

import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.tl.api.updates.TLState
import com.github.badoualy.telegram.tl.api.updates.TLAbsDifference

object UpdatesManager {
	fun update(client: TelegramClient) {
		val state: TLState = client.updatesGetState()
		
		println("pts: ${state.pts}")
		println("qts: ${state.qts}")
		println("date: ${state.date}")
		println("seq: ${state.seq}")
		println("unreadCount: ${state.unreadCount}")
		
		val pts = 1 //211282
		val date = 1515060280
		val qts = -1 // We don't support encryption. Setting qts to -1 signals this to telegram, so we won't get updates for encrypted messages.
		val diff = client.updatesGetDifference(pts, date, qts)
		
		println("diff type: ${diff.javaClass}")
		println(diff.toPrettyJson())
		
		System.exit(1)
	}
}
