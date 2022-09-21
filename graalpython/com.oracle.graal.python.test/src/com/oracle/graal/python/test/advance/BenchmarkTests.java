/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test.advance;

import static com.oracle.graal.python.test.PythonTests.assertBenchNoError;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class BenchmarkTests {
    private static final String BENCH_SUITE_MESO = "meso";

    @Test
    public void richards3() {
        Path script = Paths.get(BENCH_SUITE_MESO, "richards3.py");
        assertBenchNoError(script, new String[]{script.toString(), "20"});
    }

    @Test
    public void bm_ai() {
        Path script = Paths.get(BENCH_SUITE_MESO, "ai-nqueen.py");
        assertBenchNoError(script, new String[]{script.toString(), "5"});
    }

    @Test
    public void mandelbrot3_300() {
        Path script = Paths.get(BENCH_SUITE_MESO, "mandelbrot3.py");
        assertBenchNoError(script, new String[]{script.toString(), "200"});
    }

}
