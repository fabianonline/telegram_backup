package org.telegram.mtproto.secure;

import java.security.SecureRandom;

import static org.telegram.mtproto.secure.CryptoUtils.xor;

/**
 * Created with IntelliJ IDEA.
 * User: ex3ndr
 * Date: 03.11.13
 * Time: 4:05
 */
public final class Entropy {
    private static SecureRandom random = new SecureRandom();

    public static byte[] generateSeed(int size) {
        synchronized (random) {
            return random.generateSeed(size);
        }
    }

    public static byte[] generateSeed(byte[] sourceSeed) {
        synchronized (random) {
            return xor(random.generateSeed(sourceSeed.length), sourceSeed);
        }
    }

    public static long generateRandomId() {
        synchronized (random) {
            return random.nextLong();
        }
    }

    public static int randomInt() {
        synchronized (random) {
            return random.nextInt();
        }
    }

    public static void feedEntropy(byte[] data) {
        synchronized (random) {
            random.setSeed(data);
        }
    }
}
