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
package org.teavm.classlib.java.util;

import static org.junit.Assert.assertEquals;
import java.util.Formatter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class FormatterLiteralRegressionTest {
    @Test
    public void lineSeparatorDoesNotConsumeArgument() {
        assertEquals("1" + System.lineSeparator() + "2", String.format("%d%n%d", 1, 2));
    }

    @Test
    public void percentLiteralDoesNotConsumeArgument() {
        assertEquals("1% 2", new Formatter().format("%d%% %d", 1, 2).toString());
    }
}
