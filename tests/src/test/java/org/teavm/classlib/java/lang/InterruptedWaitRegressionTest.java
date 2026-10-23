/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
public class InterruptedWaitRegressionTest {
    @Test
    public void interruptedWaitReacquiresMonitor() throws InterruptedException {
        var lock = new Object();
        var observed = new boolean[2];
        var failure = new Throwable[1];
        var waiter = new Thread(() -> {
            try {
                synchronized (lock) {
                    try {
                        lock.wait(5000);
                    } catch (InterruptedException e) {
                        observed[0] = true;
                    }
                    observed[1] = Thread.holdsLock(lock);
                }
            } catch (Throwable e) {
                failure[0] = e;
            }
        });
        waiter.start();
        Thread.sleep(50);
        waiter.interrupt();
        waiter.join(6000);
        assertTrue("Worker did not finish", !waiter.isAlive());
        assertTrue("Wait did not report interruption", observed[0]);
        assertTrue("Monitor not re-acquired after interrupted wait", observed[1]);
        assertTrue("Synchronized exit failed: " + failure[0], failure[0] == null);
    }

    @Test
    public void interruptedWaiterDoesNotSwallowNotify() throws InterruptedException {
        var lock = new Object();
        var interruptedWaiter = new Thread(() -> {
            try {
                synchronized (lock) {
                    lock.wait();
                }
            } catch (InterruptedException | IllegalMonitorStateException e) {
                // expected; IllegalMonitorStateException is the separate bug covered above
            }
        });
        interruptedWaiter.start();
        Thread.sleep(50);
        interruptedWaiter.interrupt();
        interruptedWaiter.join(3000);
        assertTrue("Interrupted worker did not finish", !interruptedWaiter.isAlive());

        var woken = new boolean[1];
        var waiter = new Thread(() -> {
            synchronized (lock) {
                try {
                    lock.wait(2000);
                } catch (InterruptedException e) {
                    // ignore
                }
                woken[0] = true;
            }
        });
        long start = System.currentTimeMillis();
        waiter.start();
        Thread.sleep(50);
        synchronized (lock) {
            lock.notify();
        }
        waiter.join(6000);
        assertTrue("Worker did not finish", !waiter.isAlive());
        long duration = System.currentTimeMillis() - start;
        assertTrue(woken[0]);
        assertTrue("notify() was lost, waiter only woke on timeout after " + duration + " ms", duration < 1000);
    }
}
