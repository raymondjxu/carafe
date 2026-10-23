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
package org.teavm.backend.wasm.vtable;

import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.MutableClassHolderSource;

public class WasmGCVirtualTableFoldingTest {
    @Test
    public void markerInterfaceHierarchyPreservesConcreteObjectTable() {
        var source = new MutableClassHolderSource();
        add(source, "java.lang.Object", null, false);
        // Unimplemented diamond branches still participate in vtable hierarchy construction.
        add(source, "MarkerA", "java.lang.Object", true);
        add(source, "MarkerB", "java.lang.Object", true);
        add(source, "Left", "java.lang.Object", true, "MarkerB");
        add(source, "Diamond", "java.lang.Object", true, "MarkerA", "Left");
        add(source, "Right", "java.lang.Object", true, "MarkerB");
        add(source, "First", "java.lang.Object", false, "Right");
        var tables = new WasmGCVirtualTableProvider(source, List.of(), method -> true);
        assertTrue("Object must keep a concrete table after marker interface merging",
                tables.lookup("java.lang.Object").isConcrete());
        assertTrue(tables.lookup("First").isConcrete());
    }

    private void add(MutableClassHolderSource source, String name, String parent, boolean itf, String... interfaces) {
        var cls = new ClassHolder(name);
        cls.setParent(parent);
        if (itf) {
            cls.getModifiers().add(ElementModifier.INTERFACE);
            cls.getModifiers().add(ElementModifier.ABSTRACT);
        }
        cls.getInterfaces().addAll(List.of(interfaces));
        source.putClassHolder(cls);
    }
}
