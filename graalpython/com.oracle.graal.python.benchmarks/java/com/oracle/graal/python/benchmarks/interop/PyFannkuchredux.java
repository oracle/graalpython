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

public class PyFannkuchredux extends BenchRunner {

    @Param({"11"}) public int arg1;

    private Value createArray;

    @Setup
    public void setup() {
        System.out.println("### setup ...");
        this.createArray = this.context.eval("python", //
                        "from array import array\n" + //
                                        "def create_array(n):" + //
                                        "  return array('i', range(n))\n" + //
                                        "create_array");
    }

    @Benchmark
    public void fannkuchredux3(Blackhole bh) {
        int[] result = fannkuch(arg1);
        bh.consume(result);
        System.out.println("" + result[0]);
        System.out.println(String.format("Pfannkuchen(%d) = %d", arg1, result[1]));
    }

    public int[] fannkuch(int n) {
        int[] result = new int[]{0, 0};
        int sign, maxflips, summ, flips, m, q0, qq, i, t, j, sx, tmp;
        Value p = array(n);
        Value q = array(n);
        Value s = array(n);
        sign = 1;
        maxflips = 0;
        summ = 0;
        m = n - 1;
        while (true) {
            q0 = geti(p, 0);
            if (q0 != 0) {
                i = 1;
                while (i < n) {
                    seti(q, i, geti(p, i));
                    i += 1;
                }
                flips = 1;
                while (true) {
                    qq = geti(q, q0);
                    if (qq == 0) {
                        summ += sign * flips;
                        if (flips > maxflips) {
                            maxflips = flips;
                        }
                        break;
                    }
                    seti(q, q0, q0);
                    if (q0 >= 3) {
                        i = 1;
                        j = q0 - 1;
                        while (true) {
                            tmp = geti(q, i);
                            seti(q, i, geti(q, j));
                            seti(q, j, tmp);
                            i += 1;
                            j -= 1;
                            if (i >= j) {
                                break;
                            }
                        }
                    }
                    q0 = qq;
                    flips += 1;
                }
            }

            if (sign == 1) {
                tmp = geti(p, 1);
                seti(p, 1, geti(p, 0));
                seti(p, 0, tmp);

                sign = -1;
            } else {
                tmp = geti(p, 1);
                seti(p, 1, geti(p, 2));
                seti(p, 2, tmp);

                sign = 1;
                i = 2;
                while (i < n) {
                    sx = geti(s, i);
                    if (sx != 0) {
                        seti(s, i, sx - 1);
                        break;
                    }
                    if (i == m) {
                        result[0] = summ;
                        result[1] = maxflips;
                        return result;
                    }
                    seti(s, i, i);
                    t = geti(p, 0);
                    j = 0;
                    while (j <= i) {
                        seti(p, j, geti(p, j + 1));
                        j += 1;
                    }
                    seti(p, i + 1, t);
                    i += 1;
                }
            }
        }
    }

    public Value array(int n) {
        return this.createArray.execute(context.asValue(n));
    }
}