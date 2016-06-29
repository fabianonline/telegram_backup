package org.telegram.api.engine.file;

/**
 * Created by ex3ndr on 19.11.13.
 */
public interface UploadListener {
    public void onPartUploaded(int percent, int downloadedSize);
}
