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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class ByteBufferBulkCopyTest {
    @Test
    public void bulkCopyFromHeapSliceToDirectUsesOffsetsOnce() {
        var bytes = new byte[31];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) (i * 17);
        }
        var source = ByteBuffer.wrap(bytes).position(3).slice();
        source.position(4).limit(23);
        var target = ByteBuffer.allocateDirect(40);
        target.position(5).put(source);
        var actual = new byte[19];
        target.get(5, actual);
        assertArrayEquals(java.util.Arrays.copyOfRange(bytes, 7, 26), actual);
        assertEquals(24, target.position());
        assertEquals(23, source.position());
    }

}
