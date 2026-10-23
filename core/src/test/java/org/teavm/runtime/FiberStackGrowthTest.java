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
package org.teavm.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class FiberStackGrowthTest {
    @Test
    public void growingAllStacksPreservesTheirContents() throws ReflectiveOperationException {
        var constructor = Fiber.class.getDeclaredConstructor(Fiber.FiberRunner.class, boolean.class);
        constructor.setAccessible(true);
        var fiber = constructor.newInstance((Fiber.FiberRunner) () -> { }, true);
        for (int i = 0; i < 100; i++) {
            fiber.push(i);
            fiber.push((long) i);
            fiber.push((float) i);
            fiber.push((double) i);
            fiber.push(Integer.valueOf(i));
        }
        for (int i = 99; i >= 0; i--) {
            assertEquals(i, fiber.popInt());
            assertEquals(i, fiber.popLong());
            assertEquals(i, fiber.popFloat(), 0);
            assertEquals(i, fiber.popDouble(), 0);
            assertEquals(Integer.valueOf(i), fiber.popObject());
        }
    }

    @Test
    public void stackSavingCannotReachReflectiveArrayAllocation() throws Exception {
        // Arrays.copyOf can be coroutine-transformed via reflective array allocation. Calling it
        // while saving a coroutine recursively saves that same frame before it has been completed.
        var node = new ClassNode();
        try (var input = Fiber.class.getResourceAsStream("Fiber.class")) {
            new ClassReader(input).accept(node, 0);
        }
        var methods = new HashMap<String, MethodNode>();
        var pending = new ArrayDeque<String>();
        for (var method : node.methods) {
            methods.put(method.name + method.desc, method);
            if (method.name.equals("push")) {
                pending.add(method.name + method.desc);
            }
        }
        var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            var name = pending.remove();
            if (!visited.add(name)) {
                continue;
            }
            for (var instruction : methods.get(name).instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse("Fiber stack growth reaches potentially suspending " + call.owner + "." + call.name,
                            call.owner.equals("java/util/Arrays") || call.owner.equals("java/lang/reflect/Array"));
                    if (call.owner.equals(node.name)) {
                        pending.add(call.name + call.desc);
                    }
                }
            }
        }
    }
}
