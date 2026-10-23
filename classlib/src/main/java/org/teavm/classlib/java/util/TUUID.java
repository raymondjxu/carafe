/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import org.teavm.classlib.PlatformDetector;
import org.teavm.jso.crypto.Crypto;

public class TUUID implements Comparable<TUUID> {

    private static final Random RANDOM = new Random();
    private final long mostSigBits;
    private final long leastSigBits;

    public TUUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    public static TUUID randomUUID() {
        if (PlatformDetector.isJavaScript() && Crypto.isSupported()) {
            return fromString(Crypto.current().randomUUID());
        }
        Random r = RANDOM;
        long msb = r.nextLong();
        long lsb = r.nextLong();
        msb &= 0xffffffffffff0fffL;
        msb |= 0x0000000000004000L;
        lsb &= 0x3fffffffffffffffL;
        lsb |= 0x8000000000000000L;
        return new TUUID(msb, lsb);
    }

    public static TUUID nameUUIDFromBytes(byte[] name) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("MD5 not supported", e);
        }
        byte[] d = md.digest(name);
        d[6] &= 0x0f;
        d[6] |= 0x30;
        d[8] &= 0x3f;
        d[8] |= 0x80;
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (d[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (d[i] & 0xff);
        }
        return new TUUID(msb, lsb);
    }

    public static TUUID fromString(String name) {
        int len = name.length();
        if (len > 36) {
            throw new IllegalArgumentException("UUID string too large");
        }
        int dash1 = name.indexOf('-');
        int dash2 = name.indexOf('-', dash1 + 1);
        int dash3 = name.indexOf('-', dash2 + 1);
        int dash4 = name.indexOf('-', dash3 + 1);
        int dash5 = name.indexOf('-', dash4 + 1);
        if (dash4 < 0 || dash5 >= 0) {
            throw new IllegalArgumentException("Invalid UUID string: " + name);
        }
        long msb = hex(name, 0, dash1) & 0xffffffffL;
        msb <<= 16;
        msb |= hex(name, dash1 + 1, dash2) & 0xffffL;
        msb <<= 16;
        msb |= hex(name, dash2 + 1, dash3) & 0xffffL;
        long lsb = hex(name, dash3 + 1, dash4) & 0xffffL;
        lsb <<= 48;
        lsb |= hex(name, dash4 + 1, len) & 0xffffffffffffL;
        return new TUUID(msb, lsb);
    }

    private static long hex(String s, int from, int to) {
        if (from >= to || to - from > 16) {
            throw new IllegalArgumentException("Invalid UUID string: " + s);
        }
        long result = 0;
        for (int i = from; i < to; i++) {
            int d = Character.digit(s.charAt(i), 16);
            if (d < 0) {
                throw new IllegalArgumentException("Invalid UUID string: " + s);
            }
            result = (result << 4) | d;
        }
        return result;
    }

    public long getMostSignificantBits() {
        return mostSigBits;
    }

    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    public int version() {
        return (int) ((mostSigBits >> 12) & 0x0f);
    }

    public int variant() {
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62))) & (leastSigBits >> 63));
    }

    public long timestamp() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return (mostSigBits & 0x0fffL) << 48
                | ((mostSigBits >> 16) & 0x0ffffL) << 32
                | (mostSigBits >>> 32);
    }

    public int clockSequence() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return (int) ((leastSigBits & 0x3fff000000000000L) >>> 48);
    }

    public long node() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return leastSigBits & 0x0000ffffffffffffL;
    }

    @Override
    public String toString() {
        return digits(mostSigBits >> 32, 8) + "-"
                + digits(mostSigBits >> 16, 4) + "-"
                + digits(mostSigBits, 4) + "-"
                + digits(leastSigBits >> 48, 4) + "-"
                + digits(leastSigBits, 12);
    }

    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        String s = Long.toHexString(hi | (val & (hi - 1)));
        return s.substring(1);
    }

    @Override
    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return ((int) (hilo >> 32)) ^ (int) hilo;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TUUID)) {
            return false;
        }
        TUUID other = (TUUID) obj;
        return mostSigBits == other.mostSigBits && leastSigBits == other.leastSigBits;
    }

    @Override
    public int compareTo(TUUID val) {
        int c = Long.compare(mostSigBits, val.mostSigBits);
        return c != 0 ? c : Long.compare(leastSigBits, val.leastSigBits);
    }
}
