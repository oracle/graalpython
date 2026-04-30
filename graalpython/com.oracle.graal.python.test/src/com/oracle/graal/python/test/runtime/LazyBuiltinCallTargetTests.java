/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;

public class LazyBuiltinCallTargetTests {

    @Test
    public void builtinCallTargetsRemainLazyAfterStartup() {
        int totalBefore = PBuiltinFunction.getTrackedBuiltinFunctionCount();
        int callTargetsBefore = PBuiltinFunction.getTrackedBuiltinCallTargetCount();
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
            assertEquals(0, context.eval("python", "0").asInt());
        }
        int totalDelta = PBuiltinFunction.getTrackedBuiltinFunctionCount() - totalBefore;
        int callTargetDelta = PBuiltinFunction.getTrackedBuiltinCallTargetCount() - callTargetsBefore;
        assertTrue(String.format("expected tracked builtin functions to be registered during startup, totalDelta=%d", totalDelta), totalDelta > 0);
        assertTrue(String.format("expected fewer builtin call targets than builtin functions to be initialized during startup, totalDelta=%d callTargetDelta=%d", totalDelta, callTargetDelta),
                        callTargetDelta < totalDelta);
    }

    @Test
    public void builtinCallTargetsInitializeOnFirstUse() {
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
            assertEquals(0, context.eval("python", "0").asInt());
            int beforeCalls = PBuiltinFunction.getTrackedBuiltinCallTargetCount();
            assertEquals(0, context.eval("python", "abs(-42)\n[].append(1)\n0").asInt());
            int afterCalls = PBuiltinFunction.getTrackedBuiltinCallTargetCount();
            assertTrue(String.format("expected builtin calls to initialize at least one lazy call target, before=%d after=%d", beforeCalls, afterCalls), afterCalls > beforeCalls);
        }
    }

    @Test
    public void codeCallTargetsRemainLazyAfterFunctionDefinition() {
        int totalBefore = PCode.getTrackedCodeCount();
        int callTargetsBefore = PCode.getTrackedCodeCallTargetCount();
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
            assertEquals(0, context.eval("python", "def f():\n    return 1\n0").asInt());
        }
        int totalDelta = PCode.getTrackedCodeCount() - totalBefore;
        int callTargetDelta = PCode.getTrackedCodeCallTargetCount() - callTargetsBefore;
        assertTrue(String.format("expected tracked child code objects to be created, totalDelta=%d", totalDelta), totalDelta > 0);
        assertTrue(String.format("expected fewer code call targets than tracked code objects after definition, totalDelta=%d callTargetDelta=%d", totalDelta, callTargetDelta),
                        callTargetDelta < totalDelta);
    }

    @Test
    public void codeCallTargetsInitializeOnFirstInvocation() {
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
            assertEquals(0, context.eval("python", "def f():\n    return 1\n0").asInt());
            int beforeCalls = PCode.getTrackedCodeCallTargetCount();
            assertEquals(1, context.eval("python", "f()").asInt());
            int afterCalls = PCode.getTrackedCodeCallTargetCount();
            assertTrue(String.format("expected code invocation to initialize at least one lazy call target, before=%d after=%d", beforeCalls, afterCalls), afterCalls > beforeCalls);
        }
    }
}
