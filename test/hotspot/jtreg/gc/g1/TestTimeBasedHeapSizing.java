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
 * @test TestTimeBasedHeapSizing
 * @bug 8357445
 * @summary Test time-based heap sizing functionality in G1
 * @requires vm.gc.G1
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management/sun.management
 * @run main/othervm -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+G1UseTimeBasedHeapSizing
 *     -Xms32m -Xmx128m -XX:G1HeapRegionSize=1M
 *     -XX:G1TimeBasedEvaluationIntervalMillis=5000
 *     -XX:G1UncommitDelayMillis=10000
 *     -XX:G1MinRegionsToUncommit=2
 *     -Xlog:gc+sizing=debug
 *     gc.g1.TestTimeBasedHeapSizing
 */

import java.util.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestTimeBasedHeapSizing {

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
        testBasicFunctionality();
    }

    static void testBasicFunctionality() throws Exception {
        String[] command = new String[TEST_VM_OPTS.split(" ").length + 1];
        System.arraycopy(TEST_VM_OPTS.split(" "), 0, command, 0, TEST_VM_OPTS.split(" ").length);
        command[command.length - 1] = "gc.g1.TestTimeBasedHeapSizing$BasicFunctionalityTest";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        
        output.shouldContain("Starting heap evaluation");
        output.shouldContain("Full region scan:");
        
        output.shouldHaveExitValue(0);
    }

    public static class BasicFunctionalityTest {
        private static final int MB = 1024 * 1024;
        private static ArrayList<byte[]> arrays = new ArrayList<>();
        
        public static void main(String[] args) throws Exception {
            System.out.println("BasicFunctionalityTest: Starting heap activity");
            
            // Create significant heap activity
            for (int cycle = 0; cycle < 3; cycle++) {
                System.out.println("Allocation cycle " + cycle);
                allocateMemory(25);  // 25MB per cycle
                Thread.sleep(200);   // Brief pause
                clearMemory();
                System.gc();
                Thread.sleep(200);
            }

            System.out.println("BasicFunctionalityTest: Starting idle period");
            
            // Sleep to allow time-based evaluation
            Thread.sleep(18000);  // 18 seconds
            
            System.out.println("BasicFunctionalityTest: Completed idle period");
            
            // Final cleanup
            clearMemory();
            Thread.sleep(500);
            
            System.out.println("BasicFunctionalityTest: Test completed");
            Runtime.getRuntime().halt(0);
        }
        
        static void allocateMemory(int mb) throws InterruptedException {
            for (int i = 0; i < mb; i++) {
                arrays.add(new byte[MB]);
                if (i % 4 == 0) Thread.sleep(10);
            }
        }
        
        static void clearMemory() {
            arrays.clear();
            System.gc();
        }
    }
}
