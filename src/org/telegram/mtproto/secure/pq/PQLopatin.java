package org.telegram.mtproto.secure.pq;

import java.util.Random;

/**
 * Created by ex3ndr on 12.02.14.
 */
public class PQLopatin implements PQImplementation {
    @Override
    public long findDivider(long src) {
        return findSmallMultiplierLopatin(src);
    }

    private long GCD(long a, long b) {
        while (a != 0 && b != 0) {
            while ((b & 1) == 0) {
                b >>= 1;
            }
            while ((a & 1) == 0) {
                a >>= 1;
            }
            if (a > b) {
                a -= b;
            } else {
                b -= a;
            }
        }
        return b == 0 ? a : b;
    }

    private long findSmallMultiplierLopatin(long what) {
        Random r = new Random();
        long g = 0;
        int it = 0;
        for (int i = 0; i < 3; i++) {
            int q = (r.nextInt(128) & 15) + 17;
            long x = r.nextInt(1000000000) + 1, y = x;
            int lim = 1 << (i + 18);
            for (int j = 1; j < lim; j++) {
                ++it;
                long a = x, b = x, c = q;
                while (b != 0) {
                    if ((b & 1) != 0) {
                        c += a;
                        if (c >= what) {
                            c -= what;
                        }
                    }
                    a += a;
                    if (a >= what) {
                        a -= what;
                    }
                    b >>= 1;
                }
                x = c;
                long z = x < y ? y - x : x - y;
                g = GCD(z, what);
                if (g != 1) {
                    break;
                }
                if ((j & (j - 1)) == 0) {
                    y = x;
                }
            }
            if (g > 1) {
                break;
            }
        }

        long p = what / g;
        return Math.min(p, g);
    }
}
