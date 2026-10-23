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
package org.teavm.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.util.AbstractQueue;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTestRunner;

// TeaVM joins these queue implementations above AbstractQueue; the actual receiver remains valid.
@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
public class ImpreciseReceiverTypeTest {
    private static boolean flag;

    @Test
    public void virtualCallReceiverFromLocal() {
        AbstractQueue<String> queue = flag ? new LinkedBlockingDeque<>() : new PriorityQueue<>();
        assertNotNull(queue);
        queue.add("a");
        assertEquals("a", queue.element());
    }

    @Test
    public void virtualCallReceiverFromAssignment() {
        AbstractQueue<String> queue = flag ? new LinkedBlockingDeque<>() : new PriorityQueue<>();
        queue.add("a");
        assertEquals("a", queue.element());
    }
}
