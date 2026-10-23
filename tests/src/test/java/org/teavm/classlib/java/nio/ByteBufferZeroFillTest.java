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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Address;
import org.teavm.junit.TeaVMTestRunner;
import org.teavm.runtime.heap.Heap;

@RunWith(TeaVMTestRunner.class)
public class ByteBufferZeroFillTest {
    @Test
    public void directAllocationIsZeroedAndDuplicatePreservesData() {
        if (PlatformDetector.isWebAssemblyGC()) {
            // Recycle dirty allocator storage so an initially zero Wasm memory cannot hide the bug.
            var dirty = Heap.alloc(65);
            Address.fill(dirty, (byte) 7, 65);
            Heap.release(dirty);
        }
        var buffer = ByteBuffer.allocateDirect(65);
        for (int i = 0; i < buffer.capacity(); ++i) {
            assertEquals(0, buffer.get(i));
            buffer.put(i, (byte) 7);
        }
        var duplicate = buffer.duplicate();
        for (int i = 0; i < buffer.capacity(); ++i) {
            assertEquals(7, duplicate.get(i));
        }
    }

}
