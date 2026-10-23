/*
 *  Copyright 2026 Raymond Xu.
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
package org.teavm.classlib.java.util.concurrent;

import static org.junit.Assert.assertEquals;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class ConcurrentMapComputeRegressionTest {
    // Issue: ConcurrentMap's default computeIfAbsent returns null when it inserts a new value.
    // TConcurrentMap.computeIfAbsent returns `putIfAbsent(key, newValue)`, which is the PREVIOUS value,
    // i.e. null for a fresh key. The JDK default returns newValue when putIfAbsent returns null. Only
    // TConcurrentHashMap overrides the method, so every other ConcurrentMap is affected, including
    // third-party ones such as guava's MapMaker maps (MapMakerInternalMap). In Owlcraft this made
    // Minecraft's ResourceKey intern map (new MapMaker().weakValues().makeMap()) return null from every
    // createRegistryKey() call. It was originally misattributed to weak references.
    @Test
    public void defaultComputeIfAbsentReturnsNewValue() {
        var map = new SimpleConcurrentMap<String, String>();
        assertEquals("v", map.computeIfAbsent("k", key -> "v"));
        assertEquals("v", map.get("k"));
        assertEquals("v", map.computeIfAbsent("k", key -> "other"));
    }

    // Deliberately relies on ConcurrentMap's default computeIfAbsent, like guava's MapMakerInternalMap.
    static class SimpleConcurrentMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
        private final Map<K, V> map = new HashMap<>();

        @Override
        public Set<Entry<K, V>> entrySet() {
            return map.entrySet();
        }

        @Override
        public V get(Object key) {
            return map.get(key);
        }

        @Override
        public V put(K key, V value) {
            return map.put(key, value);
        }

        @Override
        public V putIfAbsent(K key, V value) {
            var old = map.get(key);
            if (old == null) {
                map.put(key, value);
            }
            return old;
        }

        @Override
        public boolean remove(Object key, Object value) {
            if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
                map.remove(key);
                return true;
            }
            return false;
        }

        @Override
        public boolean replace(K key, V oldValue, V newValue) {
            if (map.containsKey(key) && Objects.equals(map.get(key), oldValue)) {
                map.put(key, newValue);
                return true;
            }
            return false;
        }

        @Override
        public V replace(K key, V value) {
            return map.containsKey(key) ? map.put(key, value) : null;
        }
    }
}
