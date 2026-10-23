/*
 *  Copyright 2014 Alexey Andreev.
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class ArrayDequeIteratorRegressionTest {
    @Test
    public void iteratorRemoveNearTail() {
        var deque = new ArrayDeque<>(List.of(1, 2, 3, 4, 5));
        var seen = new java.util.ArrayList<Integer>();
        var it = deque.iterator();
        while (it.hasNext()) {
            var value = it.next();
            seen.add(value);
            if (value == 4) {
                it.remove();
            }
        }
        assertEquals(List.of(1, 2, 3, 4, 5), seen);
        assertArrayEquals(new Integer[] { 1, 2, 3, 5 }, deque.toArray(new Integer[0]));
    }
}
