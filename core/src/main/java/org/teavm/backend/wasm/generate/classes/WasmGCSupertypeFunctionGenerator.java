/*
 *  Copyright 2026 Alexey Andreev.
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.generate.WasmGCNameProvider;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.model.ValueType;
import org.teavm.model.classes.TagRegistry;

public class WasmGCSupertypeFunctionGenerator implements WasmGCSupertypeFunctionProvider {
    private static final int RANGES_PER_HELPER = 128;

    private Map<ValueType, WasmFunction> functions = new HashMap<>();
    private WasmModule module;
    private WasmGCClassGenerator classGenerator;
    private WasmGCNameProvider nameProvider;
    private TagRegistry tagRegistry;
    private WasmFunctionTypes functionTypes;
    private WasmFunctionType functionType;
    private Queue<Runnable> queue;
    private WasmFunction isArrayFunction;

    WasmGCSupertypeFunctionGenerator(
            WasmModule module,
            WasmGCClassGenerator classGenerator,
            WasmGCNameProvider nameProvider,
            TagRegistry tagRegistry,
            WasmFunctionTypes functionTypes,
            Queue<Runnable> queue
    ) {
        this.module = module;
        this.classGenerator = classGenerator;
        this.nameProvider = nameProvider;
        this.tagRegistry = tagRegistry;
        this.functionTypes = functionTypes;
        this.queue = queue;
    }

    @Override
    public WasmFunction getIsSupertypeFunction(ValueType type) {
        if (type instanceof ValueType.Array) {
            return getIsArraySupertypeFunction();
        }
        var result = functions.get(type);
        if (result == null) {
            result = generateIsSupertypeFunction(type);
            functions.put(type, result);
        }
        return result;
    }

    private WasmFunction generateIsSupertypeFunction(ValueType type) {
        var function = new WasmFunction(getFunctionType());
        var classClass = classGenerator.standardClasses.classClass();
        function.setName(nameProvider.topLevel(nameProvider.suggestForType(type) + "@isSupertypes"));
        var subtypeVar = new WasmLocal(classClass.getType(), "subtype");
        var supertypeVar = new WasmLocal(classClass.getType(), "supertype");
        function.add(subtypeVar);
        function.add(supertypeVar);
        module.functions.add(function);

        queue.add(() -> {
            if (type instanceof ValueType.Object) {
                var className = ((ValueType.Object) type).getClassName();
                generateIsClass(subtypeVar, className, function);
            } else {
                assert !(type instanceof ValueType.Array);
                var expected = classGenerator.getClassInfo(type).pointer;
                function.getBody().builder()
                        .getLocal(subtypeVar)
                        .getGlobal(expected)
                        .refEqual();
            }
        });

        return function;
    }

    private void generateIsClass(WasmLocal subtypeVar, String className, WasmFunction function) {
        var ranges = tagRegistry.getRanges(className);
        if (ranges.isEmpty()) {
            function.getBody().add(new WasmInt32Constant(0));
            return;
        }

        var classInfoStruct = classGenerator.reflectionTypes().classInfo().structure();
        int tagOffset = classGenerator.reflectionTypes().classInfo().tagIndex();
        var tagVar = new WasmLocal(WasmType.INT32, "tag");
        function.add(tagVar);

        ranges.sort(Comparator.comparingInt(range -> range.lower));
        var tagRanges = ranges.stream().map(range -> new TagRange(range.lower, range.upper)).toList();

        function.getBody().builder()
                .getLocal(subtypeVar)
                .structGet(classInfoStruct, tagOffset)
                .setLocal(tagVar);
        var helperType = functionTypes.of(WasmType.INT32, WasmType.INT32);
        appendChunkedRangeTests(module, function, tagVar, helperType, tagRanges, RANGES_PER_HELPER);
    }

    static void appendChunkedRangeTests(WasmModule module, WasmFunction wrapper, WasmLocal tagVar,
            WasmFunctionType helperType, List<TagRange> ranges, int rangesPerHelper) {
        if (rangesPerHelper < 1) {
            throw new IllegalArgumentException("Ranges per supertype helper must be positive");
        }

        var wrapperBody = wrapper.getBody().builder();
        for (int start = 0, helperIndex = 0; start < ranges.size(); start += rangesPerHelper, helperIndex++) {
            int end = Math.min(start + rangesPerHelper, ranges.size());
            var helper = new WasmFunction(helperType);
            helper.setName(wrapper.getName() + "$range" + helperIndex);
            var helperTag = new WasmLocal(WasmType.INT32, "tag");
            helper.add(helperTag);
            module.functions.add(helper);
            appendRangeTest(helper, helperTag, ranges.subList(start, end));

            wrapperBody.getLocal(tagVar).call(helper);
            wrapperBody.conditional().getThenBlock().builder().i32Const(1).return_();
        }
        wrapperBody.i32Const(0);
    }

    private static void appendRangeTest(WasmFunction helper, WasmLocal tagVar, List<TagRange> ranges) {
        var body = helper.getBody().builder();
        var first = ranges.get(0);
        var last = ranges.get(ranges.size() - 1);

        body.getLocal(tagVar).i32Const(first.lower())
                .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED);
        body.conditional().getThenBlock().builder().i32Const(0).return_();

        body.getLocal(tagVar).i32Const(last.upper())
                .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GE_SIGNED);
        body.conditional().getThenBlock().builder().i32Const(0).return_();

        for (int i = 1; i < ranges.size(); i++) {
            int lowerHole = ranges.get(i - 1).upper();
            int upperHole = ranges.get(i).lower();
            body.getLocal(tagVar).i32Const(lowerHole)
                    .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.GE_SIGNED);
            var hole = body.conditional().getThenBlock().builder();
            hole.getLocal(tagVar).i32Const(upperHole)
                    .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.LT_SIGNED);
            hole.conditional().getThenBlock().builder().i32Const(0).return_();
        }
        body.i32Const(1);
    }

    static boolean matches(List<TagRange> ranges, int tag) {
        return ranges.stream().anyMatch(range -> tag >= range.lower() && tag < range.upper());
    }

    record TagRange(int lower, int upper) {
    }

    WasmFunction getIsArraySupertypeFunction() {
        if (isArrayFunction != null) {
            return isArrayFunction;
        }

        var function = new WasmFunction(getFunctionType());
        isArrayFunction = function;
        module.functions.add(function);

        var classInfoType = classGenerator.reflectionTypes().classInfo();
        function.setName(nameProvider.topLevel("teavm@isArrayType"));
        var subtypeVar = new WasmLocal(classInfoType.structure().getReference(), "subtype");
        var supertypeVar = new WasmLocal(classInfoType.structure().getReference(), "supertype");
        function.add(subtypeVar);
        function.add(supertypeVar);

        int itemOffset = classInfoType.itemTypeIndex();
        var body = function.getBody().builder();

        body.getLocal(supertypeVar).structGet(classInfoType.structure(), itemOffset).setLocal(supertypeVar);
        body.getLocal(subtypeVar).structGet(classInfoType.structure(), itemOffset).setLocal(subtypeVar);

        body.getLocal(subtypeVar).isNull();
        var itemTest = body.conditional(WasmType.INT32);
        itemTest.getThenBlock().builder().i32Const(0);

        var funcType = functionTypes.of(WasmType.INT32, classInfoType.structure().getReference(),
                classInfoType.structure().getReference());
        var elseBody = itemTest.getElseBlock().builder();
        elseBody.getLocal(subtypeVar).getLocal(supertypeVar)
                .getLocal(supertypeVar).structGet(classInfoType.structure(), classInfoType.supertypeFunctionIndex())
                .callReference(funcType);

        return function;
    }

    @Override
    public WasmFunctionType getFunctionType() {
        if (functionType == null) {
            var classInfoStruct = classGenerator.reflectionTypes().classInfo().structure().getReference();
            functionType = functionTypes.of(WasmType.INT32, classInfoStruct, classInfoStruct);
        }
        return functionType;
    }
}
