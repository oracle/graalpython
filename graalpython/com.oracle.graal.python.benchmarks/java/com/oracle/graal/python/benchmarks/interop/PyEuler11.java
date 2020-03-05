/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.benchmarks.interop;

import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

public class PyEuler11 extends BenchRunner {

    @Param({"1000"}) public int arg1;

    private Value sub_list1;
    private Value sub_list2;
    private Value sub_list3;
    private Value sub_list4;
    private Value NUMS;

    @Setup
    public void setup() {
        System.out.println("### setup ...");
        this.sub_list1 = this.context.eval("python", "def sub_list1(nums, row, col): return list(nums[i][col] for i in range(row, row+4))\nsub_list1");
        this.sub_list2 = this.context.eval("python", "def sub_list2(nums, row, col): return list(nums[row][i] for i in range(col, col+4))\nsub_list2");
        this.sub_list3 = this.context.eval("python", "def sub_list3(nums, row, col): return list(nums[row+i][col+i] for i in range(0, 4))\nsub_list3");
        this.sub_list4 = this.context.eval("python", "def sub_list4(nums, row, col): return list(nums[row+i][col-i] for i in range(0, 4))\nsub_list4");
        this.NUMS = this.context.eval("python", "[\n" +
                        "[ 8, 2,22,97,38,15, 0,40, 0,75, 4, 5, 7,78,52,12,50,77,91, 8,],\n" +
                        "[49,49,99,40,17,81,18,57,60,87,17,40,98,43,69,48, 4,56,62, 0,],\n" +
                        "[81,49,31,73,55,79,14,29,93,71,40,67,53,88,30, 3,49,13,36,65,],\n" +
                        "[52,70,95,23, 4,60,11,42,69,24,68,56, 1,32,56,71,37, 2,36,91,],\n" +
                        "[22,31,16,71,51,67,63,89,41,92,36,54,22,40,40,28,66,33,13,80,],\n" +
                        "[24,47,32,60,99, 3,45, 2,44,75,33,53,78,36,84,20,35,17,12,50,],\n" +
                        "[32,98,81,28,64,23,67,10,26,38,40,67,59,54,70,66,18,38,64,70,],\n" +
                        "[67,26,20,68, 2,62,12,20,95,63,94,39,63, 8,40,91,66,49,94,21,],\n" +
                        "[24,55,58, 5,66,73,99,26,97,17,78,78,96,83,14,88,34,89,63,72,],\n" +
                        "[21,36,23, 9,75, 0,76,44,20,45,35,14, 0,61,33,97,34,31,33,95,],\n" +
                        "[78,17,53,28,22,75,31,67,15,94, 3,80, 4,62,16,14, 9,53,56,92,],\n" +
                        "[16,39, 5,42,96,35,31,47,55,58,88,24, 0,17,54,24,36,29,85,57,],\n" +
                        "[86,56, 0,48,35,71,89, 7, 5,44,44,37,44,60,21,58,51,54,17,58,],\n" +
                        "[19,80,81,68, 5,94,47,69,28,73,92,13,86,52,17,77, 4,89,55,40,],\n" +
                        "[ 4,52, 8,83,97,35,99,16, 7,97,57,32,16,26,26,79,33,27,98,66,],\n" +
                        "[88,36,68,87,57,62,20,72, 3,46,33,67,46,55,12,32,63,93,53,69,],\n" +
                        "[ 4,42,16,73,38,25,39,11,24,94,72,18, 8,46,29,32,40,62,76,36,],\n" +
                        "[20,69,36,41,72,30,23,88,34,62,99,69,82,67,59,85,74, 4,36,16,],\n" +
                        "[20,73,35,29,78,31,90, 1,74,31,49,71,48,86,81,16,23,57, 5,54,],\n" +
                        "[ 1,70,54,71,83,51,54,69,16,92,33,48,61,43,52, 1,89,19,67,48,],\n" +
                        "]");
    }

    @Benchmark
    public int euler11(Blackhole bh) {
        for (int i = 0; i < arg1; i++) {
            bh.consume(solve());
        }

        int max = solve();
        System.out.println("max: " + max);
        return max;
    }

    private int solve() {
        int max = Integer.MIN_VALUE;
        long size1 = NUMS.getArraySize();
        long size2 = NUMS.getArrayElement(0).getArraySize();
        for (int row = 0; row < size1; row++) {
            for (int col = 0; col < size2; col++) {
                if (row + 4 <= size1) {
                    max = Math.max(max, product(call(sub_list1, row, col)));
                }
                if (col + 4 <= size2) {
                    max = Math.max(max, product(call(sub_list2, row, col)));
                }
                if (row + 4 <= size1 && col + 4 <= size2) {
                    max = Math.max(max, product(call(sub_list3, row, col)));
                }
                if (row + 4 <= size1 && col >= 3) {
                    max = Math.max(max, product(call(sub_list4, row, col)));
                }
            }
        }
        return max;
    }

    private static int product(Value seq) {
        int n = 1;
        for (int i = 0; i < seq.getArraySize(); i++) {
            n *= geti(seq, i);
        }
        return n;
    }

    private Value call(Value func, int row, int col) {
        return func.execute(NUMS, context.asValue(row), context.asValue(col));
    }
}