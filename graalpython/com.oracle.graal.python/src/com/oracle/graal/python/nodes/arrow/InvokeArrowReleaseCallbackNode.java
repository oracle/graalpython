/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.arrow;

import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.arrow.ArrowUtil;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.api.SignatureLibrary;

@GenerateCached
@GenerateInline(inlineByDefault = true)
@GenerateUncached
public abstract class InvokeArrowReleaseCallbackNode extends PNodeWithContext {

    public abstract void execute(Node inliningTarget, long releaseCallback, long baseStructure);

    public final void executeCached(long releaseCallback, long baseStructure) {
        execute(this, releaseCallback, baseStructure);
    }

    @Specialization
    static void doIt(Node inliningTarget, long releaseCallback, long baseStructure,
                    @Bind("getContext(inliningTarget)") PythonContext ctx,
                    @Cached(value = "createReleaseCallbackSignature($node, ctx)", allowUncached = true) Object callbackSignature,
                    @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
        try {
            signatureLibrary.call(callbackSignature, new NativePointer(releaseCallback), baseStructure);
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere("Unable to call release callback. Error:", e);
        }
    }

    @NeverDefault
    static Object createReleaseCallbackSignature(Node location, PythonContext context) {
        return ArrowUtil.createNfiSignature(location, "(UINT64):VOID", context);
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    public abstract static class Lazy extends Node {
        public final InvokeArrowReleaseCallbackNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        abstract InvokeArrowReleaseCallbackNode execute(Node inliningTarget);

        @Specialization
        static InvokeArrowReleaseCallbackNode doIt(@Cached(inline = false) InvokeArrowReleaseCallbackNode node) {
            return node;
        }
    }
}
