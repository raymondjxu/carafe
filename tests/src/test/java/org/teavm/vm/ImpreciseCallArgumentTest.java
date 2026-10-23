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
import static org.junit.Assert.assertFalse;
import java.nio.file.InvalidPathException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTestRunner;

// TeaVM's InvalidPathException hierarchy makes the joined local wider than the Java parameter.
// The executed path holds NumberFormatException, so the inserted cast is valid at runtime.
@RunWith(TeaVMTestRunner.class)
@EachTestCompiledSeparately
public class ImpreciseCallArgumentTest {
    private static boolean flag;

    @Test
    public void staticCallArgument() {
        IllegalArgumentException e = flag ? new InvalidPathException("p", "r") : new NumberFormatException("n");
        assertFalse(isInvalidPath(e));
    }

    @Test
    public void virtualCallArgument() {
        IllegalArgumentException e = flag ? new InvalidPathException("p", "r") : new NumberFormatException("n");
        assertFalse(new Checker().isInvalidPath(e));
    }

    @Test
    public void suspendingCallArgument() {
        IllegalArgumentException e = flag ? new InvalidPathException("p", "r") : new NumberFormatException("n");
        assertEquals("x:false", "x:" + suspendingIsInvalidPath(e));
    }

    static boolean isInvalidPath(IllegalArgumentException e) {
        return e instanceof InvalidPathException;
    }

    static boolean suspendingIsInvalidPath(IllegalArgumentException e) {
        Thread.yield();
        return e instanceof InvalidPathException;
    }

    static class Checker {
        boolean isInvalidPath(IllegalArgumentException e) {
            return e instanceof InvalidPathException;
        }
    }
}
