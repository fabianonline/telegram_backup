package com.droidkit.actors.sample;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ex3ndr on 20.08.14.
 */
public class IOUtils {
    public static byte[] readAll(InputStream in) throws IOException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(in);
        ByteArrayOutputStream os = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4 * 1024];
        int len;
        int readed = 0;
        try {
            while ((len = bufferedInputStream.read(buffer)) >= 0) {
                Thread.yield();
                os.write(buffer, 0, len);
                readed += len;
            }
        } catch (java.io.IOException e) {

        }
        return os.toByteArray();
    }
}
