package org.telegram.api.engine.file;

/**
 * Created by ex3ndr on 18.11.13.
 */
public interface DownloadListener {
    public void onPartDownloaded(int percent, int downloadedSize);

    public void onDownloaded();

    public void onFailed();
}
