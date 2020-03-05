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

public class PyEuler31 extends BenchRunner {

    @Param({"100"}) public int arg1;

    private Value newpat;
    private Value EMPTY;
    private Value COINS;

    @Setup
    public void setup() {
        System.out.println("### setup ...");
        this.newpat = this.context.eval("python", //
                        "def newpat(t, end, p):" + //
                                        "  return t[:end] + (p,)\n" + //
                                        "newpat");
        this.COINS = this.context.eval("python", "[1, 2, 5, 10, 20, 50, 100, 200]");
        this.EMPTY = this.context.eval("python", "()");
    }

    @Benchmark
    public void euler31(Blackhole bh) {
        int result = gen(EMPTY, 0, arg1);
        bh.consume(result);
        System.out.println("total number of different ways: " + result);
    }

    private int gen(Value pattern, int coinnum, int num) {
        int count = 0;
        int coin = geti(COINS, coinnum);
        for (int p = 0; p < (num / coin + 1); p++) {
            Value newpattern = newpat(pattern, coinnum, p);
            int bal = balance(newpattern);
            if (bal > num) {
                return count;
            } else if (bal == num) {
                count++;
            } else if (coinnum < (COINS.getArraySize() - 1)) {
                count += gen(newpattern, coinnum + 1, num);
            }
        }
        return count;
    }

    private int balance(Value pattern) {
        long size = pattern.getArraySize();
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += geti(COINS, i) * geti(pattern, i);
        }
        return sum;
    }

    private Value newpat(Value t, int end, int p) {
        return this.newpat.execute(t, this.context.asValue(end), this.context.asValue(p));
    }
}