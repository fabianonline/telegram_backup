package org.telegram.api.engine;

import org.telegram.api.TLAbsUpdates;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 11.11.13
 * Time: 7:42
 */
public interface ApiCallback {
    public void onAuthCancelled(TelegramApi api);

    public void onUpdatesInvalidated(TelegramApi api);

    public void onUpdate(TLAbsUpdates updates);
}
