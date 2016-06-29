package com.droidkit.actors.sample;

import com.droidkit.actors.*;
import com.droidkit.actors.dispatch.RunnableDispatcher;
import com.droidkit.actors.tasks.TaskActor;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by ex3ndr on 18.08.14.
 */
public class HttpDownloader extends TaskActor<byte[]> {

    private static final RunnableDispatcher dispatcher = new RunnableDispatcher(2);

    public static String path(String url) {
        return "/http_" + HashUtil.md5(url);
    }

    public static Props<HttpDownloader> prop(final String url) {
        return Props.create(HttpDownloader.class, new ActorCreator<HttpDownloader>() {
            @Override
            public HttpDownloader create() {
                return new HttpDownloader(url);
            }
        });
    }

    public static ActorSelection download(String url) {
        return new ActorSelection(prop(url), path(url));
    }

    private String url;
    private Runnable runnable;

    public HttpDownloader(final String url) {
        this.url = url;
        runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("HttpDownloader:startDownload:" + url);
                    URL urlSpec = new URL(url);
                    HttpURLConnection urlConnection = (HttpURLConnection) urlSpec.openConnection();
                    urlConnection.setConnectTimeout(15000);
                    urlConnection.setReadTimeout(15000);
                    InputStream in = urlConnection.getInputStream();
                    byte[] data = IOUtils.readAll(in);
                    complete(data);
                    Log.d("HttpDownloader:complete:" + url);
                } catch (IOException e) {
                    error(e);
                    Log.d("HttpDownloader:error:" + url);
                }
            }
        };
        setTimeOut(500);
    }

    @Override
    public void startTask() {
        Log.d("HttpDownloader:startTask:" + url);
        dispatcher.postAction(runnable);
    }

    @Override
    public void onTaskObsolete() {
        Log.d("HttpDownloader:onTaskObsolete:" + url);
    }
}
