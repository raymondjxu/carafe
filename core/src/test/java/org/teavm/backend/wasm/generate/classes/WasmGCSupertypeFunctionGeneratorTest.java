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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmField;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmStructure;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.MutableClassHolderSource;
import org.teavm.model.classes.TagRegistry;

public class WasmGCSupertypeFunctionGeneratorTest {
    @Test
    public void actualInterfaceRangeGenerationBoundsHelperBodies() throws Exception {
        var source = new MutableClassHolderSource();
        var object = new ClassHolder("java.lang.Object");
        object.setParent(null);
        source.putClassHolder(object);
        var marker = new ClassHolder("Marker");
        marker.getModifiers().add(ElementModifier.INTERFACE);
        source.putClassHolder(marker);
        for (int i = 0; i < 600; i++) {
            var cls = new ClassHolder("Class" + i);
            cls.setParent("java.lang.Object");
            if (i % 2 == 0) {
                cls.getInterfaces().add("Marker");
            }
            source.putClassHolder(cls);
        }
        var hierarchy = new ClassHierarchy(source);
        var tags = new TagRegistry(source, hierarchy);
        assertEquals(300, tags.getRanges("Marker").size());
        var module = new WasmModule();
        var names = new WasmGCNameProvider();
        var types = new WasmFunctionTypes(module);
        var classGenerator = new WasmGCClassGenerator(module, source, source, hierarchy, null, types,
                tags, null, null, null, names, null, List.of());
        var metadata = classGenerator.reflectionTypes().classInfo();
        var structure = new WasmStructure("ClassInfo");
        structure.getFields().add(new WasmField(WasmType.INT32, "tag"));
        var field = metadata.getClass().getDeclaredField("structure");
        field.setAccessible(true);
        field.set(metadata, structure);
        var wrapper = new WasmFunction(types.of(WasmType.INT32, structure.getReference()));
        wrapper.setName("Marker@isSupertypes");
        module.functions.add(wrapper);
        var subtype = new WasmLocal(structure.getReference(), "subtype");
        wrapper.add(subtype);
        var generator = new WasmGCSupertypeFunctionGenerator(module, classGenerator, names, tags, types,
                new ArrayDeque<>());
        var generate = WasmGCSupertypeFunctionGenerator.class.getDeclaredMethod("generateIsClass",
                WasmLocal.class, String.class, WasmFunction.class);
        generate.setAccessible(true);
        generate.invoke(generator, subtype, "Marker", wrapper);
        assertEquals("300 disjoint implementor ranges must generate three bounded helpers", 4, module.functions.size());
        assertTrue(instructions(wrapper).size() < 30);
        assertTrue(module.functions.stream().allMatch(function -> instructions(function).size() < 1300));
    }

    @Test
    public void splitsRangesIntoBoundedOrderedHelpers() {
        var module = new WasmModule();
        var wrapperType = new WasmFunctionType("wrapper", WasmType.INT32, List.of(WasmType.INT32));
        var helperType = new WasmFunctionType("helper", WasmType.INT32, List.of(WasmType.INT32));
        var wrapper = new WasmFunction(wrapperType);
        wrapper.setName("example@isSupertypes");
        module.functions.add(wrapper);
        var tag = new WasmLocal(WasmType.INT32, "tag");
        wrapper.add(tag);

        var ranges = List.of(
                range(1, 3),
                range(5, 8),
                range(13, 21),
                range(34, 55),
                range(89, 144));

        appendChunkedRangeTests(
                module, wrapper, tag, helperType, ranges, 2);

        var helpers = module.functions.stream()
                .filter(function -> function.getName().startsWith("example@isSupertypes$range"))
                .toList();
        assertEquals(3, helpers.size());
        assertEquals(List.of(1, 8, 3, 1), constants(helpers.get(0)));
        assertEquals(List.of(13, 55, 21, 1), constants(helpers.get(1)));
        assertEquals(List.of(89, 144, 1), constants(helpers.get(2)));
        assertTrue(helpers.stream().allMatch(helper -> helper.getLocalVariables().size() == 1));

        var calls = instructions(wrapper).stream().filter(WasmCall.class::isInstance)
                .map(WasmCall.class::cast).toList();
        assertEquals(helpers, calls.stream().map(WasmCall::getFunction).toList());
        assertTrue(instructions(wrapper).get(instructions(wrapper).size() - 1) instanceof WasmInt32Constant);
        assertEquals(0, ((WasmInt32Constant) instructions(wrapper).get(instructions(wrapper).size() - 1)).getValue());
    }

    @Test
    public void rangePredicateUsesHalfOpenBounds() {
        var ranges = List.of(
                range(-2, 1),
                range(4, 7));

        assertTrue(matches(ranges, -2));
        assertTrue(matches(ranges, 0));
        assertTrue(matches(ranges, 4));
        assertTrue(matches(ranges, 6));
        assertEquals(false, matches(ranges, 1));
        assertEquals(false, matches(ranges, 7));
        assertEquals(false, matches(ranges, 3));
    }

    private static List<WasmInstruction> instructions(WasmFunction function) {
        var result = new ArrayList<WasmInstruction>();
        function.getBody().forEach(result::add);
        return result;
    }

    private static List<Integer> constants(WasmFunction function) {
        return instructions(function).stream()
                .filter(WasmInt32Constant.class::isInstance)
                .map(WasmInt32Constant.class::cast)
                .map(WasmInt32Constant::getValue)
                .toList();
    }
    private static Object range(int lower, int upper) {
        try {
            var type = Class.forName(WasmGCSupertypeFunctionGenerator.class.getName() + "$TagRange");
            var constructor = type.getDeclaredConstructor(int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(lower, upper);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Supertype range tests must support bounded helper functions", e);
        }
    }

    private static void appendChunkedRangeTests(WasmModule module, WasmFunction wrapper, WasmLocal tag,
            WasmFunctionType type, List<?> ranges, int chunkSize) {
        invoke("appendChunkedRangeTests", new Class<?>[] { WasmModule.class, WasmFunction.class,
                WasmLocal.class, WasmFunctionType.class, List.class, int.class },
                module, wrapper, tag, type, ranges, chunkSize);
    }

    private static boolean matches(List<?> ranges, int tag) {
        return (Boolean) invoke("matches", new Class<?>[] { List.class, int.class }, ranges, tag);
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            var method = WasmGCSupertypeFunctionGenerator.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Bounded supertype range generation failed", e);
        }
    }

}
