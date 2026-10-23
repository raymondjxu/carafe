/*
 *  Copyright 2018 Alexey Andreev.
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
package org.teavm.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FiberTest {
    private Fiber fiber;
    private Field current;
    private Field state;

    @Before
    public void setUp() throws ReflectiveOperationException {
        var constructor = Fiber.class.getDeclaredConstructor(Fiber.FiberRunner.class, boolean.class);
        constructor.setAccessible(true);
        fiber = constructor.newInstance((Fiber.FiberRunner) () -> { }, true);
        current = Fiber.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(null, fiber);
        state = Fiber.class.getDeclaredField("state");
        state.setAccessible(true);
    }

    @After
    public void tearDown() throws ReflectiveOperationException {
        current.set(null, null);
        var pending = Fiber.class.getDeclaredField("lastPendingCall");
        pending.setAccessible(true);
        pending.set(null, null);
    }

    @Test
    public void companionArmsCallbackBeforeSuspension() throws Throwable {
        var result = new Object();
        Fiber.suspend(callback -> {
            assertFalse(fiber.isSuspending());
            @SuppressWarnings("unchecked")
            var objectCallback = (org.teavm.interop.AsyncCallback<Object>) callback;
            objectCallback.complete(result);
        });
        assertTrue(fiber.isSuspending());
        state.setInt(fiber, Fiber.STATE_RESUMING);
        assertSame(result, Fiber.suspend(callback -> { }));
        assertFalse(fiber.isSuspending());
    }

    @Test
    public void companionFailureReachesResumingCaller() throws Throwable {
        var failure = new IllegalArgumentException("companion failed");
        try {
            Fiber.suspend(callback -> {
                throw failure;
            });
        } catch (Throwable e) {
            throw new AssertionError("A companion failure must reach the resumed caller", e);
        }
        assertTrue(fiber.isSuspending());
        state.setInt(fiber, Fiber.STATE_RESUMING);
        assertSame(failure, assertThrows(IllegalArgumentException.class, () -> Fiber.suspend(callback -> { })));
        assertFalse(fiber.isSuspending());
    }
}
