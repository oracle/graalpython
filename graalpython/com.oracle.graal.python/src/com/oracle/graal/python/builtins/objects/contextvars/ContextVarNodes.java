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
package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public final class ContextVarNodes {
    private ContextVarNodes() {
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetValueNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, PContextVar var, PythonThreadState threadState);

        public static GetValueNode getUncached() {
            return ContextVarNodesFactory.GetValueNodeGen.getUncached();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSingleContext(inliningTarget)", "values == cachedValues", "var == cachedVar"}, limit = "3", excludeForUncached = true)
        static Object doCached(Node inliningTarget, PContextVar var, PythonThreadState threadState,
                        @Shared("initializeContext") @Cached InlinedBranchProfile initializeContextProfile,
                        @Bind("threadState.getContextVarsContext(inliningTarget, initializeContextProfile).contextVarValues") Hamt values,
                        @Cached(value = "values", weak = true) Hamt cachedValues,
                        @Cached(value = "var", weak = true) PContextVar cachedVar,
                        @Cached(value = "lookupValue(cachedValues, cachedVar)", weak = true) Object cachedValue) {
            return cachedValue == PNone.NO_VALUE ? null : cachedValue;
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(Node inliningTarget, PContextVar var, PythonThreadState threadState,
                        @Shared("initializeContext") @Cached InlinedBranchProfile initializeContextProfile) {
            return var.getValue(inliningTarget, threadState, initializeContextProfile);
        }

        @TruffleBoundary
        static Object lookupValue(Hamt values, PContextVar var) {
            Object value = values.lookup(var, var.getHash());
            // Weak caches cannot hold null, so use the sentinel for an unset variable.
            return value == null ? PNone.NO_VALUE : value;
        }
    }
}
