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
package org.teavm.classlib.java.util.stream;

import static org.junit.Assert.assertArrayEquals;
import java.util.Spliterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class SortedStreamSizeRegressionTest {
    @Test
    public void sortedStreamUsesSizeBeforeSourceIsDrained() {
        var source = new java.util.Spliterators.AbstractSpliterator<Integer>(3,
                Spliterator.SIZED | Spliterator.SUBSIZED) {
            int remaining = 3;

            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super Integer> action) {
                if (remaining == 0) {
                    return false;
                }
                action.accept(remaining--);
                return true;
            }

            @Override
            public long estimateSize() {
                return remaining;
            }
        };
        var result = java.util.stream.StreamSupport.stream(source, false).sorted().limit(2).toArray();
        assertArrayEquals(new Object[] { 1, 2 }, result);
    }
}
