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
package org.teavm.backend.wasm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.teavm.backend.wasm.generate.WasmGCDeclarationsGenerator;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmDrop;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmInt32Constant;
import org.teavm.backend.wasm.model.instruction.WasmIntBinary;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmReturn;
import org.teavm.dependency.DependencyInfo;
import org.teavm.model.MutableClassHolderSource;

public class WasmGCModuleGeneratorTest {
    @Test
    public void publicGenerationBoundsBothInitializerBodies() {
        var module = new WasmModule();
        var source = new MutableClassHolderSource();
        var dependency = (DependencyInfo) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { DependencyInfo.class },
                (proxy, method, args) -> null);
        var declarations = new WasmGCDeclarationsGenerator(
                module, source, source, null, getClass().getClassLoader(), null, dependency, null,
                List.of(), method -> true, false, "main", List.of(), null, null) {
            @Override
            public void generate() {
                var reflection = new WasmFunction(functionTypes.of(null));
                reflection.setName("teavm@initReflection");
                module.functions.add(reflection);
                fill(reflection);
            }

            @Override
            public void contributeToInitializer(WasmFunction function) {
                fill(function);
            }

            private void fill(WasmFunction function) {
                for (int i = 0; i < 5000; i++) {
                    function.getBody().builder().i32Const(i).drop();
                }
            }
        };
        new WasmGCModuleGenerator(declarations).generate();
        var reflection = module.functions.stream()
                .filter(function -> "teavm@initReflection".equals(function.getName())).findFirst().orElseThrow();
        assertTrue("Module start function must be split into bounded helper calls",
                instructions(module.getStartFunction()).size() <= 8192);
        assertTrue("Reflection metadata initializer must be split into bounded helper calls",
                instructions(reflection).size() <= 8192);
        assertEquals(List.of(0, 1), instructions(module.getStartFunction()).stream().map(WasmCall.class::cast)
                .map(call -> constants(call.getFunction()).get(0) / 4096).toList());
    }

    @Test
    public void splitsNamedReflectionInitializerIntoOrderedHelpers() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var reflectionInitializer = new WasmFunction(type);
        reflectionInitializer.setName("teavm@initReflection");
        module.functions.add(reflectionInitializer);
        addValueAndDrop(reflectionInitializer, 10);
        addValueAndDrop(reflectionInitializer, 20);
        addValueAndDrop(reflectionInitializer, 30);

        splitNamedFunction(module, "teavm@initReflection", 2);

        var calls = instructions(reflectionInitializer).stream().map(WasmCall.class::cast).toList();
        assertEquals(3, calls.size());
        assertEquals("teavm@initReflection$0", calls.get(0).getFunction().getName());
        assertEquals("teavm@initReflection$1", calls.get(1).getFunction().getName());
        assertEquals("teavm@initReflection$2", calls.get(2).getFunction().getName());
        assertEquals(List.of(10), constants(calls.get(0).getFunction()));
        assertEquals(List.of(20), constants(calls.get(1).getFunction()));
        assertEquals(List.of(30), constants(calls.get(2).getFunction()));
    }

    @Test
    public void rejectsDuplicateNamedFunctionsBeforeMovingInstructions() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var first = new WasmFunction(type);
        first.setName("teavm@initReflection");
        module.functions.add(first);
        addValueAndDrop(first, 1);
        var second = new WasmFunction(type);
        second.setName("teavm@initReflection");
        module.functions.add(second);
        addValueAndDrop(second, 2);

        var exception = assertThrows(IllegalStateException.class,
                () -> splitNamedFunction(module, "teavm@initReflection", 1));

        assertTrue(exception.getMessage().contains("duplicate"));
        assertEquals(List.of(1), constants(first));
        assertEquals(List.of(2), constants(second));
    }

    @Test
    public void splitsOnlyWhereOperandStackIsEmptyAndPreservesOrder() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var initializer = new WasmFunction(type);
        initializer.setName("teavm@initializer");
        module.functions.add(initializer);

        // The threshold falls in the middle of 1 + 2, so that expression must remain intact.
        addValueAndDrop(initializer, 0);
        var expression = List.<WasmInstruction>of(
                new WasmInt32Constant(1),
                new WasmInt32Constant(2),
                new WasmIntBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD),
                new WasmDrop());
        expression.forEach(initializer.getBody()::add);
        addValueAndDrop(initializer, 3);

        splitInitializer(module, initializer, type, 4);

        var calls = instructions(initializer).stream().map(WasmCall.class::cast).toList();
        assertEquals(3, calls.size());
        assertEquals(List.of(0), constants(calls.get(0).getFunction()));
        assertEquals(List.of(1, 2), constants(calls.get(1).getFunction()));
        assertEquals(List.of(3), constants(calls.get(2).getFunction()));
        assertTrue(instructions(calls.get(1).getFunction()).containsAll(expression));
    }

    @Test
    public void rejectsUnexpectedInitializerLocalsBeforeMovingInstructions() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var initializer = new WasmFunction(type);
        module.functions.add(initializer);
        initializer.add(new WasmLocal(WasmType.INT32));
        addValueAndDrop(initializer, 7);

        var exception = assertThrows(IllegalStateException.class,
                () -> splitInitializer(module, initializer, type, 1));

        assertTrue(exception.getMessage().contains("locals"));
        assertEquals(List.of(7), constants(initializer));
    }

    @Test
    public void extendsToNextSafeBoundaryWhenOneExpressionExceedsTargetSize() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var initializer = new WasmFunction(type);
        module.functions.add(initializer);
        initializer.getBody().add(new WasmInt32Constant(1));
        initializer.getBody().add(new WasmInt32Constant(2));
        initializer.getBody().add(new WasmIntBinary(WasmIntType.INT32, WasmIntBinaryOperation.ADD));
        initializer.getBody().add(new WasmDrop());
        addValueAndDrop(initializer, 3);

        splitInitializer(module, initializer, type, 3);

        var calls = instructions(initializer).stream().map(WasmCall.class::cast).toList();
        assertEquals(2, calls.size());
        assertEquals(List.of(1, 2), constants(calls.get(0).getFunction()));
        assertEquals(List.of(3), constants(calls.get(1).getFunction()));
    }

    @Test
    public void rejectsTopLevelControlTransfer() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var initializer = new WasmFunction(type);
        module.functions.add(initializer);
        initializer.getBody().add(new WasmReturn());
        addValueAndDrop(initializer, 1);

        var exception = assertThrows(IllegalStateException.class,
                () -> splitInitializer(module, initializer, type, 1));

        assertTrue(exception.getMessage().contains("control transfer"));
    }

    @Test
    public void instructionsAppendedLaterRemainAfterReferencedHelperCalls() {
        var module = new WasmModule();
        var type = new WasmFunctionType("void", (WasmType) null, List.of());
        var initializer = new WasmFunction(type);
        module.functions.add(initializer);
        addValueAndDrop(initializer, 1);
        addValueAndDrop(initializer, 2);

        splitInitializer(module, initializer, type, 2);
        addValueAndDrop(initializer, 99); // Mirrors initBuffersHeap, which runs after generate().

        var body = instructions(initializer);
        assertEquals(4, body.size());
        assertTrue(body.get(0) instanceof WasmCall);
        assertTrue(body.get(1) instanceof WasmCall);
        assertTrue(body.get(2) instanceof WasmInt32Constant);
        var moduleFunctions = module.functions.stream().toList();
        assertTrue(moduleFunctions.contains(((WasmCall) body.get(0)).getFunction()));
        assertTrue(moduleFunctions.contains(((WasmCall) body.get(1)).getFunction()));
    }

    private static void addValueAndDrop(WasmFunction function, int value) {
        function.getBody().add(new WasmInt32Constant(value));
        function.getBody().add(new WasmDrop());
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
    // Reflection keeps this regression executable against the unsplit upstream implementation.
    private static void splitInitializer(WasmModule module, WasmFunction function,
            WasmFunctionType type, int chunkSize) {
        invokeSplitter("splitInitializer", new Class<?>[] {
                WasmModule.class, WasmFunction.class, WasmFunctionType.class, int.class },
                module, function, type, chunkSize);
    }

    private static void splitNamedFunction(WasmModule module, String name, int chunkSize) {
        invokeSplitter("splitNamedFunction", new Class<?>[] { WasmModule.class, String.class, int.class },
                module, name, chunkSize);
    }

    private static void invokeSplitter(String name, Class<?>[] types, Object... args) {
        try {
            var method = WasmGCModuleGenerator.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Generated initializers must support bounded, stack-safe splitting", e);
        }
    }

}
