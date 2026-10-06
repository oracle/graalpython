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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public final class DecimalNodes {
    private DecimalNodes() {
    }

    /** Resolve an optional context argument, matching CPython's CONTEXT_CHECK_VA. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetContextNode extends Node {
        public abstract PDecimalContext execute(Node inliningTarget, Object context);

        @Specialization
        static PDecimalContext doContext(@SuppressWarnings("unused") Node inliningTarget, PDecimalContext context) {
            return context;
        }

        @Specialization
        static PDecimalContext doDefault(Node inliningTarget, @SuppressWarnings("unused") PNone context) {
            return DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
        }

        @Fallback
        static PDecimalContext doInvalid(Node inliningTarget, @SuppressWarnings("unused") Object context,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OPTIONAL_DECIMAL_CONTEXT);
        }
    }

    @GenerateInline(false) // Error path
    @GenerateUncached
    public abstract static class RaiseSignalNode extends Node {
        public abstract void execute(VirtualFrame frame, int trapped);

        @Specialization
        static void raise(VirtualFrame frame, int trapped,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            DecimalModuleState state = DecimalContextUtils.getState(inliningTarget);
            Object[] conditions = new Object[Integer.bitCount(trapped)];
            int index = 0;
            for (DecimalExceptionTypes condition : DecimalContextUtils.INVALID_CONDITIONS) {
                if ((trapped & DecimalContextUtils.conditionMask(condition)) != 0) {
                    conditions[index++] = state.getExceptionType(condition);
                }
            }
            for (int i = 1; i < DecimalContextUtils.SIGNALS.length; i++) {
                if ((trapped & DecimalContextUtils.mask(DecimalContextUtils.SIGNALS[i])) != 0) {
                    conditions[index++] = state.getExceptionType(DecimalContextUtils.SIGNALS[i]);
                }
            }
            assert index == conditions.length;
            for (DecimalExceptionTypes signal : DecimalContextUtils.SIGNALS) {
                if ((trapped & DecimalContextUtils.mask(signal)) != 0) {
                    PList args = PFactory.createList(language, conditions);
                    Object exception = callNode.execute(frame, state.getExceptionType(signal), args);
                    throw raiseNode.raiseExceptionObject(inliningTarget, exception);
                }
            }
        }
    }
}
