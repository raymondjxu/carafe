/*
 *  Copyright 2026 Raymond Xu.
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

import static org.junit.Assert.assertEquals;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class UUIDBitsRegressionTest {
    // Issue (missing API rather than a bug): TUUID stores a random hex string. It has no
    // UUID(long, long) constructor, no getMostSignificantBits()/getLeastSignificantBits(), and no
    // nameUUIDFromBytes() or compareTo(), so code that treats a UUID as two longs (serialization,
    // network codecs, name-based UUIDs) fails to compile with "Method java.util.UUID.<init>(JJ)V was
    // not found". A standard two-long implementation is needed.
    @Test
    public void randomVersionVariantAndComparison() {
        var random = UUID.randomUUID();
        assertEquals(4, random.version());
        assertEquals(2, random.variant());
        assertEquals(random, UUID.fromString(random.toString()));
        assertEquals(-1, new UUID(-1, 0).compareTo(new UUID(0, 0)));
        assertEquals(-1, new UUID(0, -1).compareTo(new UUID(0, 0)));
    }

    @Test
    public void timeBasedFields() {
        var uuid = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
        assertEquals(1, uuid.version());
        assertEquals(2, uuid.variant());
        assertEquals(0x1d07decf81d4faeL, uuid.timestamp());
        assertEquals(0x2765, uuid.clockSequence());
        assertEquals(0x00a0c91e6bf6L, uuid.node());
    }

    @Test
    public void bitsRoundTrip() {
        var uuid = new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L);
        assertEquals(0x0123456789ABCDEFL, uuid.getMostSignificantBits());
        assertEquals(0xFEDCBA9876543210L, uuid.getLeastSignificantBits());
        assertEquals("01234567-89ab-cdef-fedc-ba9876543210", uuid.toString());
        assertEquals(uuid, UUID.fromString(uuid.toString()));
    }
}
