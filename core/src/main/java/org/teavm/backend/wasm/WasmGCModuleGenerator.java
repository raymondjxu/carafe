/*
 *  Copyright 2024 Alexey Andreev.
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

import java.util.ArrayList;
import org.teavm.backend.wasm.generate.WasmGCDeclarationsGenerator;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmGlobal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.instruction.WasmBranch;
import org.teavm.backend.wasm.model.instruction.WasmBreak;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmCastBranch;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmIntBinaryOperation;
import org.teavm.backend.wasm.model.instruction.WasmIntType;
import org.teavm.backend.wasm.model.instruction.WasmNullBranch;
import org.teavm.backend.wasm.model.instruction.WasmReturn;
import org.teavm.backend.wasm.model.instruction.WasmSwitch;
import org.teavm.backend.wasm.model.instruction.WasmTypeInference;
import org.teavm.backend.wasm.runtime.StringInternPool;
import org.teavm.interop.Address;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.runtime.heap.Heap;

public class WasmGCModuleGenerator {
    private static final int INITIALIZER_CHUNK_SIZE = 8192;
    private static final int MAX_CHUNK_OVERSHOOT_FACTOR = 4;

    private WasmGCDeclarationsGenerator declarationsGenerator;
    private WasmFunction initializer;

    public WasmGCModuleGenerator(WasmGCDeclarationsGenerator declarationsGenerator) {
        this.declarationsGenerator = declarationsGenerator;
    }

    public void generate() {
        declarationsGenerator.generate();
        splitNamedFunction(declarationsGenerator.module, "teavm@initReflection", INITIALIZER_CHUNK_SIZE);
        createInitializer();
    }

    public void initBuffersHeap(WasmGlobal offset, int minSize, WasmGlobal heapLimit) {
        var target = declarationsGenerator.functions().forStaticMethod(new MethodReference(Heap.class,
                "init", Address.class, int.class, int.class, void.class));
        var builder = new WasmInstructionList().builder();
        builder
                .getGlobal(offset)
                .i32Const(minSize)
                .getGlobal(heapLimit)
                .getGlobal(offset)
                .intBinary(WasmIntType.INT32, WasmIntBinaryOperation.SUB)
                .call(target);
        initializer.getBody().transferFrom(builder.list);
    }

    public WasmFunction generateReportGarbageCollectedStringFunction() {
        var entryType = ValueType.object(StringInternPool.class.getName() + "$Entry");
        return declarationsGenerator.functions().forStaticMethod(new MethodReference(
                StringInternPool.class.getName(),
                "remove",
                entryType,
                ValueType.VOID
        ));
    }

    private void createInitializer() {
        var functionType = declarationsGenerator.functionTypes.of(null);
        initializer = new WasmFunction(functionType);
        initializer.setName("teavm@initializer");
        declarationsGenerator.module.functions.add(initializer);
        declarationsGenerator.module.setStartFunction(initializer);
        declarationsGenerator.contributeToInitializer(initializer);

        // TeaVM emits all class metadata initialization into one multi-megabyte function. Chrome's
        // Liftoff compiler exhausts its Zone while compiling that single body, even when the module
        // as a whole fits in memory. Calls keep the start function small without changing ordering.
        splitInitializer(declarationsGenerator.module, initializer, functionType, INITIALIZER_CHUNK_SIZE);
    }

    static void splitInitializer(WasmModule module, WasmFunction initializer, WasmFunctionType functionType,
            int chunkSize) {
        splitFunction(module, initializer, functionType, "teavm@initializer", chunkSize);
    }

    static void splitNamedFunction(WasmModule module, String functionName, int chunkSize) {
        WasmFunction function = null;
        for (var candidate : module.functions) {
            if (functionName.equals(candidate.getName())) {
                if (function != null) {
                    throw new IllegalStateException("Cannot split duplicate function " + functionName);
                }
                function = candidate;
            }
        }
        // Reflection metadata is demand-driven, so small programs legitimately have no initializer.
        if (function != null) {
            splitFunction(module, function, function.getType(), functionName, chunkSize);
        }
    }

    private static void splitFunction(WasmModule module, WasmFunction function, WasmFunctionType functionType,
            String functionName, int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("Initializer chunk size must be positive");
        }
        if (!function.getLocalVariables().isEmpty()) {
            throw new IllegalStateException("Cannot split " + functionName + " with locals");
        }
        if (!functionType.getParameterTypes().isEmpty() || !functionType.getReturnTypes().isEmpty()) {
            throw new IllegalStateException("Cannot split non-void " + functionName);
        }

        var instructions = new ArrayList<WasmInstruction>();
        function.getBody().forEach(instructions::add);
        if (instructions.size() <= chunkSize) {
            return;
        }

        // A helper boundary may not carry values on the operand stack. TeaVM's model-level type
        // inference tracks those effects, avoiding unsafe cuts inside call argument sequences.
        var inference = new WasmTypeInference();
        var safeBoundaries = new boolean[instructions.size()];
        for (var i = 0; i < instructions.size(); i++) {
            var instruction = instructions.get(i);
            if (isTopLevelControlTransfer(instruction)) {
                throw new IllegalStateException("Cannot split " + functionName + " with a top-level control transfer");
            }
            instruction.acceptVisitor(inference);
            safeBoundaries[i] = inference.typeStack.isEmpty();
        }
        if (!inference.typeStack.isEmpty()) {
            throw new IllegalStateException(functionName + " leaves values on its operand stack");
        }

        var boundaries = new ArrayList<Integer>();
        for (int start = 0; start < instructions.size();) {
            var limit = Math.min(start + chunkSize, instructions.size());
            var boundary = limit - 1;
            while (boundary >= start && !safeBoundaries[boundary]) {
                boundary--;
            }
            if (boundary < start) {
                // Large string-pool literals are single valid expressions of about 10k instructions.
                // Cutting them at the target would corrupt the operand stack, so permit a controlled
                // overshoot while retaining a hard ceiling against another pathological giant body.
                var maximum = (int) Math.min(instructions.size(),
                        start + (long) chunkSize * MAX_CHUNK_OVERSHOOT_FACTOR);
                boundary = limit;
                while (boundary < maximum && !safeBoundaries[boundary]) {
                    boundary++;
                }
                if (boundary == maximum) {
                    throw new IllegalStateException(functionName + " has an expression larger than "
                            + ((long) chunkSize * MAX_CHUNK_OVERSHOOT_FACTOR)
                            + " instructions; no stack-empty split boundary");
                }
            }
            boundaries.add(boundary);
            start = boundary + 1;
        }

        var start = 0;
        for (var chunk = 0; chunk < boundaries.size(); chunk++) {
            var helper = new WasmFunction(functionType);
            helper.setName(functionName + "$" + chunk);
            module.functions.add(helper);
            var end = boundaries.get(chunk);
            for (var i = start; i <= end; i++) {
                var instruction = instructions.get(i);
                instruction.delete();
                helper.getBody().add(instruction);
            }
            function.getBody().add(new WasmCall(helper));
            start = end + 1;
        }
    }

    private static boolean isTopLevelControlTransfer(WasmInstruction instruction) {
        return instruction.isTerminating()
                || instruction instanceof WasmBranch
                || instruction instanceof WasmBreak
                || instruction instanceof WasmCastBranch
                || instruction instanceof WasmNullBranch
                || instruction instanceof WasmReturn
                || instruction instanceof WasmSwitch;
    }
}
