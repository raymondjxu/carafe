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
package org.teavm.backend.wasm.generate.classes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.Test;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.generate.reflection.ClassInfoStruct;
import org.teavm.backend.wasm.generate.reflection.ReflectionTypes;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.model.ValueType;

public class WasmGCNewArrayFunctionGeneratorTest {
    @Test
    public void constructorsWithoutReflectiveAllocationHaveNoNullSupertype() throws Exception {
        check(null);
    }

    @Test
    public void constructorsRetainReachableReflectiveAllocationSupertype() throws Exception {
        check(new WasmFunctionType("arrayFactory", (WasmType) null, List.of()));
    }

    private void check(WasmFunctionType supertype) throws Exception {
        var module = new WasmModule();
        var names = new WasmGCNameProvider();
        var types = new WasmFunctionTypes(module);
        var reflection = new ReflectionTypes(names, module, null, null, types, null, null, null);
        var classInfo = reflection.classInfo();
        // Supply already-laid-out metadata: these tests exercise constructor signatures, not the
        // dependency-driven metadata field layout, which is absent in this minimal compilation.
        var structureField = ClassInfoStruct.class.getDeclaredField("structure");
        structureField.setAccessible(true);
        structureField.set(classInfo, new WasmStructure("ClassInfo"));
        var factoryField = ClassInfoStruct.class.getDeclaredField("newArrayFunctionType");
        factoryField.setAccessible(true);
        factoryField.set(classInfo, supertype);
        var array = new WasmGCClassInfo(ValueType.arrayOf(ValueType.INTEGER));
        array.structure = new WasmStructure("Array");
        var provider = (WasmGCClassInfoProvider) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { WasmGCClassInfoProvider.class }, (proxy, method, arguments) -> {
                    if (method.getName().equals("reflectionTypes")) {
                        return reflection;
                    }
                    if (method.getName().equals("getClassInfo")) {
                        return array;
                    }
                    throw new AssertionError("Unexpected metadata request: " + method);
                });
        var generator = new WasmGCNewArrayFunctionGenerator(module, types, provider, names, new ArrayDeque<>());
        for (var item : new ValueType[] { ValueType.INTEGER, ValueType.object("java.lang.Object") }) {
            var function = generator.generateNewArrayFunction(item);
            assertFalse("An unreachable reflective factory must not produce a null Wasm supertype",
                    function.getType().getSupertypes().contains(null));
            if (supertype != null) {
                assertSame(supertype, function.getType().getSupertypes().iterator().next());
            }
        }
    }
}
