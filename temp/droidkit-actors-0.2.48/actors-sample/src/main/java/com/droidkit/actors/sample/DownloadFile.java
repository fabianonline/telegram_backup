package com.droidkit.actors.sample;

import com.droidkit.actors.ReflectedActor;
import com.droidkit.actors.tasks.AskCallback;
import com.droidkit.actors.tasks.AskFuture;

/**
 * Created by ex3ndr on 18.08.14.
 */
public class DownloadFile extends ReflectedActor {

    public void onReceive(String[] url) {
        ask(HttpDownloader.download(url[0]));
        ask(HttpDownloader.download(url[1]));
        AskFuture future = ask(HttpDownloader.download(url[2]));

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        future.cancel();

//        combine("downloaded", byte[].class,
//                ask(HttpDownloader.download(url[0])),
//                ask(HttpDownloader.download(url[1])));
    }

    public void onDownloadedReceive(byte[][] data) {
        Log.d("DownloadFile:onDownloadedReceive:" + data);
        Log.d("downloaded " + data[0].length + " bytes and " + data[1].length + " bytes");
    }

    public void onDownloadedReceive(Throwable throwable) {
        Log.d("DownloadFile:onDownloadedReceiveError:" + throwable);
    }

    public void onReceive(String url) {
        Log.d("DownloadFile:onReceiveUrl:" + url);
        ask(HttpDownloader.download(url), new AskCallback() {
            @Override
            public void onResult(Object result) {
                self().send(result);
            }

            @Override
            public void onError(Throwable throwable) {

            }
        });
    }

    public void onReceive(byte[] data) {
        Log.d("DownloadFile:receiveData:" + data);
        Log.d("downloaded " + data.length + " bytes");
    }
}