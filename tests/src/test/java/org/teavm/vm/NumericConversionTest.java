/*
 *  Copyright 2024 Alexey Andreev.
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
package org.teavm.vm;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.SkipPlatform;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.junit.TestPlatform;

@RunWith(TeaVMTestRunner.class)
public class NumericConversionTest {
    @Test
    @SkipPlatform({TestPlatform.JAVASCRIPT, TestPlatform.C})
    public void floatOverflow() {
        assertEquals(2147483647, (int) (floatOne() * (1 << 30) * (1 << 3)));
        assertEquals(2147483647, (int) (floatOne() * Float.POSITIVE_INFINITY));
        assertEquals(-2147483648, (int) (-floatOne() * (1 << 30) * (1 << 3)));
        assertEquals(-2147483648, (int) (-floatOne() * Float.POSITIVE_INFINITY));
        assertEquals(0, (int) (floatOne() * Float.NaN));

        assertEquals((1L << 63) - 1, (long) (floatOne() * (1L << 60) * (1 << 5)));
        assertEquals((1L << 63) - 1, (long) (floatOne() * Float.POSITIVE_INFINITY));
        assertEquals(1L << 63, (long) (-floatOne() * (1L << 60) * (1 << 5)));
        assertEquals(1L << 63, (long) (-floatOne() * Float.POSITIVE_INFINITY));
        assertEquals(0, (long) (floatOne() * Float.NaN));
    }

    @Test
    @SkipPlatform({TestPlatform.JAVASCRIPT, TestPlatform.C})
    public void doubleOverflow() {
        assertEquals(2147483647, (int) (doubleOne() * (1 << 30) * (1 << 3)));
        assertEquals(2147483647, (int) (doubleOne() * Float.POSITIVE_INFINITY));
        assertEquals(-2147483648, (int) (-doubleOne() * (1 << 30) * (1 << 3)));
        assertEquals(-2147483648, (int) (-doubleOne() * Float.POSITIVE_INFINITY));
        assertEquals(0, (int) (doubleOne() * Double.NaN));

        assertEquals((1L << 63) - 1, (long) (doubleOne() * (1L << 60) * (1 << 5)));
        assertEquals((1L << 63) - 1, (long) (doubleOne() * Double.POSITIVE_INFINITY));
        assertEquals(1L << 63, (long) (-doubleOne() * (1L << 60) * (1 << 5)));
        assertEquals(1L << 63, (long) (-doubleOne() * Double.POSITIVE_INFINITY));
        assertEquals(0, (long) (doubleOne() * Double.NaN));
    }

    // Issue: on the JS backend, (long) of NaN or +/-Infinity crashes instead of following JLS 5.1.3.
    // long.js Long_fromNumber passes the double straight into BigInt(), which throws
    // "RangeError: The number NaN cannot be converted to a BigInt" for NaN and infinities.
    // Java defines (long) NaN == 0 and clamps out-of-range values (including infinities) to
    // Long.MIN_VALUE / Long.MAX_VALUE. doubleOverflow() above covers this but skips JavaScript, which
    // hides the crash. Found in Owlcraft (Minecraft client): a NaN produced during a render frame
    // crashed the client where the JVM would have yielded 0.
    @Test
    public void doubleToLongSpecialValues() {
        assertEquals(0L, (long) (doubleOne() * Double.NaN));
        assertEquals(Long.MAX_VALUE, (long) (doubleOne() * Double.POSITIVE_INFINITY));
        assertEquals(Long.MIN_VALUE, (long) (doubleOne() * Double.NEGATIVE_INFINITY));
        assertEquals(Long.MAX_VALUE, (long) (doubleOne() * 1e30));
        assertEquals(Long.MIN_VALUE, (long) (doubleOne() * -1e30));
    }

    private float floatOne() {
        return 1;
    }

    private double doubleOne() {
        return 1;
    }
}
