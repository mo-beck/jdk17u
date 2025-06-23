/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package gc.g1;

/**
 * @test TestTimeBasedRegionTracking
 * @bug 8357445
 * @summary Test region activity tracking and state transitions for time-based heap sizing
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management/sun.management
 * @run main/othervm -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+G1UseTimeBasedHeapSizing 
 *      -Xms32m -Xmx128m -XX:G1HeapRegionSize=1M
 *      -XX:G1TimeBasedEvaluationIntervalMillis=5000
 *      -XX:G1UncommitDelayMillis=10000 
 *      -XX:G1MinRegionsToUncommit=2 
 *      -Xlog:gc+sizing=debug gc.g1.TestTimeBasedRegionTracking 
 */

import java.util.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestTimeBasedRegionTracking {

    private static final String TEST_VM_OPTS = "-XX:+UseG1GC " +
        "-XX:+UnlockExperimentalVMOptions " +
        "-XX:+G1UseTimeBasedHeapSizing " +
        "-XX:G1TimeBasedEvaluationIntervalMillis=5000 " +
        "-XX:G1UncommitDelayMillis=10000 " +
        "-XX:G1MinRegionsToUncommit=2 " +
        "-XX:G1HeapRegionSize=1M " +
        "-Xmx128m -Xms32m " +
        "-Xlog:gc+sizing=debug";

    public static void main(String[] args) throws Exception {
        testRegionStateTransitions();
    }

    static void testRegionStateTransitions() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedRegionTracking$RegionTransitionTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        
        // Verify region state changes
        output.shouldContain("Region state transition:");
        output.shouldContain("Uncommit candidates found:");
        
        output.shouldHaveExitValue(0);
    }

    public static class RegionTransitionTest {
        private static final int MB = 1024 * 1024;
        private static ArrayList<byte[]> arrays = new ArrayList<>();

        public static void main(String[] args) throws Exception {
            // Phase 1: Active allocation 
            allocateMemory(32); // 32MB
            System.gc();
            
            // Phase 2: Idle period
            arrays.clear();
            System.gc();
            Thread.sleep(15000); // Wait for uncommit
            
            // Phase 3: Reallocation
            allocateMemory(16); // 16MB
            System.gc();

            // Clean up and wait for final uncommit evaluation
            arrays = null;
            System.gc();
            Thread.sleep(2000);
            Runtime.getRuntime().halt(0);
        }
        
        static void allocateMemory(int mb) throws InterruptedException {
            for (int i = 0; i < mb; i++) {
                arrays.add(new byte[MB]);
                if (i % 4 == 0) Thread.sleep(10);
            }
        }
    }
}
