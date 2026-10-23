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
import static org.junit.Assert.assertThrows;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class ByteBufferAbsoluteBulkPutTest {
    @Test
    public void absoluteBulkPutCopiesConsumedSource() {
        var source = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4 });
        source.position(source.limit());
        for (var target : new ByteBuffer[] { ByteBuffer.allocate(12), ByteBuffer.allocateDirect(12) }) {
            target.position(2).limit(8);
            target.put(3, source, 1, 3);
            assertEquals(2, target.get(3));
            assertEquals(3, target.get(4));
            assertEquals(4, target.get(5));
            assertEquals(2, target.position());
            assertEquals(4, source.position());
        }
    }

    @Test
    public void absoluteBulkPutRespectsDestinationLimit() {
        var source = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4 });
        for (var target : new ByteBuffer[] { ByteBuffer.allocate(12), ByteBuffer.allocateDirect(12) }) {
            target.limit(8);
            assertThrows(IndexOutOfBoundsException.class, () -> target.put(7, source, 0, 2));
        }
    }

    @Test
    public void absoluteBulkPutRejectsOverflowedRange() {
        var source = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4 });
        for (var target : new ByteBuffer[] { ByteBuffer.allocate(12), ByteBuffer.allocateDirect(12) }) {
            assertThrows(IndexOutOfBoundsException.class,
                    () -> target.put(1, source, 1, Integer.MAX_VALUE));
        }
    }
}
