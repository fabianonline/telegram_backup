package org.telegram.mtproto.secure.aes;

import java.io.IOException;

/**
 * Created by ex3ndr on 12.02.14.
 */
public abstract interface AESImplementation {
    public void AES256IGEDecrypt(byte[] src, byte[] dest, int len, byte[] iv, byte[] key);

    public void AES256IGEEncrypt(byte[] src, byte[] dest, int len, byte[] iv, byte[] key);

    public void AES256IGEEncrypt(String sourceFile, String destFile, byte[] iv, byte[] key) throws IOException;

    public void AES256IGEDecrypt(String sourceFile, String destFile, byte[] iv, byte[] key) throws IOException;
}
