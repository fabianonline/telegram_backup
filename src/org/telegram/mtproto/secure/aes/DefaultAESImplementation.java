package org.telegram.mtproto.secure.aes;

import org.telegram.mtproto.secure.KeyParameter;

import java.io.*;

import static org.telegram.mtproto.secure.CryptoUtils.substring;

/**
 * Created by ex3ndr on 12.02.14.
 */
public class DefaultAESImplementation implements AESImplementation {

    @Override
    public void AES256IGEDecrypt(byte[] src, byte[] dest, int len, byte[] iv, byte[] key) {
        AESFastEngine engine = new AESFastEngine();
        engine.init(false, new KeyParameter(key));

        int blocksCount = len / 16;

        byte[] curIvX = iv;
        byte[] curIvY = iv;
        int curIvXOffset = 16;
        int curIvYOffset = 0;

        for (int i = 0; i < blocksCount; i++) {
            int offset = i * 16;

            for (int j = 0; j < 16; j++) {
                dest[offset + j] = (byte) (src[offset + j] ^ curIvX[curIvXOffset + j]);
            }
            engine.processBlock(dest, offset, dest, offset);
            for (int j = 0; j < 16; j++) {
                dest[offset + j] = (byte) (dest[offset + j] ^ curIvY[curIvYOffset + j]);
            }

            curIvY = src;
            curIvYOffset = offset;
            curIvX = dest;
            curIvXOffset = offset;

            if (i % 31 == 32) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void AES256IGEEncrypt(byte[] src, byte[] dest, int len, byte[] iv, byte[] key) {
        AESFastEngine engine = new AESFastEngine();
        engine.init(true, new KeyParameter(key));

        int blocksCount = len / 16;

        byte[] curIvX = iv;
        byte[] curIvY = iv;
        int curIvXOffset = 16;
        int curIvYOffset = 0;

        for (int i = 0; i < blocksCount; i++) {

            int offset = i * 16;

            for (int j = 0; j < 16; j++) {
                dest[offset + j] = (byte) (src[offset + j] ^ curIvY[curIvYOffset + j]);
            }
            engine.processBlock(dest, offset, dest, offset);
            for (int j = 0; j < 16; j++) {
                dest[offset + j] = (byte) (dest[offset + j] ^ curIvX[curIvXOffset + j]);
            }

            curIvX = src;
            curIvXOffset = offset;
            curIvY = dest;
            curIvYOffset = offset;
        }
    }

    @Override
    public void AES256IGEEncrypt(String sourceFile, String destFile, byte[] iv, byte[] key) throws IOException {

        File src = new File(sourceFile);
        File dest = new File(destFile);

        AESFastEngine engine = new AESFastEngine();
        engine.init(true, new KeyParameter(key));

        byte[] curIvX = substring(iv, 16, 16);
        byte[] curIvY = substring(iv, 0, 16);

        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(src));
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(dest));
        byte[] buffer = new byte[16];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            byte[] outData = new byte[16];
            for (int j = 0; j < 16; j++) {
                outData[j] = (byte) (buffer[j] ^ curIvY[j]);
            }
            engine.processBlock(outData, 0, outData, 0);
            for (int j = 0; j < 16; j++) {
                outData[j] = (byte) (outData[j] ^ curIvX[j]);
            }

            curIvX = buffer;
            curIvY = outData;
            buffer = new byte[16];

            outputStream.write(outData);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    @Override
    public void AES256IGEDecrypt(String sourceFile, String destFile, byte[] iv, byte[] key) throws IOException {
        File src = new File(sourceFile);
        File dest = new File(destFile);

        AESFastEngine engine = new AESFastEngine();
        engine.init(false, new KeyParameter(key));

        byte[] curIvX = substring(iv, 16, 16);
        byte[] curIvY = substring(iv, 0, 16);

        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(src));
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(dest));
        byte[] buffer = new byte[16];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            byte[] outData = new byte[16];
            for (int j = 0; j < 16; j++) {
                outData[j] = (byte) (buffer[j] ^ curIvX[j]);
            }
            engine.processBlock(outData, 0, outData, 0);
            for (int j = 0; j < 16; j++) {
                outData[j] = (byte) (outData[j] ^ curIvY[j]);
            }

            curIvY = buffer;
            curIvX = outData;
            buffer = new byte[16];

            outputStream.write(outData);
        }
        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }
}
