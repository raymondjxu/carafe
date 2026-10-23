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

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.io;

import static org.junit.Assert.assertEquals;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class BufferedInputStreamEofRegressionTest {
    @Test
    public void repeatedEofWithMarkDoesNotGrowBuffer() throws IOException {
        var stream = new InspectableBufferedInputStream(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        stream.mark(Integer.MAX_VALUE);
        for (int loop = 0; loop < 8; ++loop) {
            assertEquals(1, stream.read());
            assertEquals(2, stream.read());
            assertEquals(3, stream.read());
            assertEquals(-1, stream.read());
            assertEquals(8, stream.bufferSize());
            stream.reset();
        }
    }

    private static class InspectableBufferedInputStream extends BufferedInputStream {
        InspectableBufferedInputStream(InputStream source) {
            super(source, 8);
        }

        int bufferSize() {
            return buf.length;
        }
    }
}
