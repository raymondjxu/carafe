/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.classlib.java.nio;

import static org.junit.Assert.assertEquals;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class ByteBufferAbsoluteReadTest {
    @Test
    public void absoluteLongHeapReadPreservesPosition() {
        var buffer = ByteBuffer.allocate(24);
        for (var order : new ByteOrder[] { ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN }) {
            buffer.order(order).position(3);
            buffer.putLong(7, 0x0123456789ABCDEFL);
            assertEquals(0x0123456789ABCDEFL, buffer.getLong(7));
            assertEquals(3, buffer.position());
        }
    }

    @Test
    public void absoluteDoubleHeapReadPreservesPosition() {
        var buffer = ByteBuffer.allocate(24);
        for (var order : new ByteOrder[] { ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN }) {
            buffer.order(order).position(3);
            buffer.putDouble(7, 1.25);
            assertEquals(1.25, buffer.getDouble(7), 0);
            assertEquals(3, buffer.position());
        }
    }

    @Test
    public void absoluteLongDirectReadPreservesPosition() {
        var buffer = ByteBuffer.allocateDirect(24);
        for (var order : new ByteOrder[] { ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN }) {
            buffer.order(order).position(3);
            buffer.putLong(7, 0x0123456789ABCDEFL);
            assertEquals(0x0123456789ABCDEFL, buffer.getLong(7));
            assertEquals(3, buffer.position());
        }
    }

    @Test
    public void absoluteDoubleDirectReadPreservesPosition() {
        var buffer = ByteBuffer.allocateDirect(24);
        for (var order : new ByteOrder[] { ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN }) {
            buffer.order(order).position(3);
            buffer.putDouble(7, 1.25);
            assertEquals(1.25, buffer.getDouble(7), 0);
            assertEquals(3, buffer.position());
        }
    }
}
