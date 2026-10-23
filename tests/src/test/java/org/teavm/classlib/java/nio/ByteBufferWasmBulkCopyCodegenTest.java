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

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.util.HashMap;
import org.junit.Test;

public class ByteBufferWasmBulkCopyCodegenTest {
    @Test
    public void heapToNativeCopyLoopsOverWideStores() throws IOException {
        assertCopyCalls("([BILorg/teavm/interop/Address;I)V", "putLong");
    }

    @Test
    public void nativeToHeapCopyLoopsOverWideLoads() throws IOException {
        assertCopyCalls("(Lorg/teavm/interop/Address;[BII)V", "getLong");
    }

    private void assertCopyCalls(String descriptor, String operation) throws IOException {
        // This is an instruction-selection contract, independent of noisy timing measurements:
        // bulk copying must include wide memory operations, which lower to Wasm i64 loads/stores.
        ClassModel cls;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "org/teavm/classlib/java/nio/TByteBufferWasmGC.class")) {
            cls = ClassFile.of().parse(input.readAllBytes());
        }
        boolean found = false;
        for (var method : cls.methods()) {
            if (!method.methodName().equalsString("copy") || !method.methodType().equalsString(descriptor)) {
                continue;
            }
            var elements = method.code().orElseThrow().elementList();
            var labels = new HashMap<Label, Integer>();
            for (var i = 0; i < elements.size(); ++i) {
                if (elements.get(i) instanceof LabelTarget target) {
                    labels.put(target.label(), i);
                }
            }
            for (var i = 0; i < elements.size(); ++i) {
                if (!(elements.get(i) instanceof InvokeInstruction call)
                        || !call.owner().asInternalName().equals("org/teavm/interop/Address")
                        || !call.name().equalsString(operation)) {
                    continue;
                }
                // A wide operation outside a loop would still copy the remaining bytes one at a time.
                for (var j = i + 1; j < elements.size(); ++j) {
                    if (elements.get(j) instanceof BranchInstruction branch
                            && labels.get(branch.target()) <= i) {
                        found = true;
                    }
                }
            }
        }
        assertTrue("Bulk copy must loop over Address." + operation + " for eight-byte chunks", found);
    }
}
