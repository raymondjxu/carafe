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

import static org.junit.Assert.assertThrows;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class ByteBufferNumericBoundsTest {
    @Test
    public void longWritesUseFullWidth() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putLong(5, 1));
        }
    }

    @Test
    public void floatReadsRejectNegativeIndex() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getFloat(-1));
        }
    }

    @Test
    public void floatWritesRejectNegativeIndex() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putFloat(-1, 1));
        }
    }

    @Test
    public void doubleReadsRejectNegativeIndex() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getDouble(-1));
        }
    }

    @Test
    public void doubleWritesRejectNegativeIndex() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putDouble(-1, 1));
        }
    }

    @Test
    public void floatReadsRespectLimit() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getFloat(9));
        }
    }

    @Test
    public void floatWritesRespectLimit() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putFloat(9, 1));
        }
    }

    @Test
    public void doubleReadsRespectLimit() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getDouble(5));
        }
    }

    @Test
    public void doubleWritesRespectLimit() {
        for (var buffer : new ByteBuffer[] { ByteBuffer.allocate(24), ByteBuffer.allocateDirect(24) }) {
            buffer.limit(12);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putDouble(5, 1));
        }
    }
}
