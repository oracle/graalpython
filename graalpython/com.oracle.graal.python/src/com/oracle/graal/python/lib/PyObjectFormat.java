/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FORMAT__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyObject_Format}. The {@code formatSpec} argument must be a Python
 * string, otherwise this raises a SystemError. Unlike CPython's {@code PyObject_Format}, this does
 * not accept native {@code NULL} as {@code formatSpec}. Convert it to an empty string to get
 * equivalent behavior.
 */
@GenerateInline
@GenerateUncached
public abstract class PyObjectFormat extends PNodeWithContext {
    public final Object executeNonInlined(VirtualFrame frame, Object obj, Object formatSpec) {
        return execute(frame, null, obj, formatSpec);
    }

    public abstract Object execute(VirtualFrame frame, Node node, Object obj, Object formatSpec);

    @Specialization
    static Object doNone(VirtualFrame frame, Node inliningTarget, Object obj, PNone formatSpec,
                    @Shared("impl") @Cached PyObjectFormatStr formatStr) {
        return formatStr.execute(frame, inliningTarget, obj, T_EMPTY_STRING);
    }

    @Fallback
    static Object doOthers(VirtualFrame frame, Node inliningTarget, Object obj, Object formatSpec,
                    @Shared("impl") @Cached PyObjectFormatStr formatStr) {
        return formatStr.execute(frame, inliningTarget, obj, formatSpec);
    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class PyObjectFormatStr extends PNodeWithContext {
        public abstract Object execute(Frame frame, Node inliningTarget, Object obj, Object formatSpec);

        static boolean isEmptyString(Object formatSpec) {
            // to keep the fast-path optimization guard simple, we ignore empty PStrings
            return (formatSpec instanceof TruffleString && ((TruffleString) formatSpec).isEmpty());
        }

        @Specialization(guards = {"isString(obj)", "isEmptyString(formatSpec)"})
        static Object doString(Object obj, Object formatSpec) {
            return obj;
        }

        @Specialization(guards = {"isEmptyString(formatSpec)"})
        static Object doLong(long obj, Object formatSpec) {
            return obj;
        }

        // Note: PRaiseNode is @Exclusive to workaround a bug in DSL
        @Specialization(guards = "isString(formatSpec)")
        static Object doGeneric(VirtualFrame frame, Object obj, Object formatSpec,
                        @Cached(parameters = "Format", inline = false) LookupAndCallBinaryNode callFormat,
                        @Exclusive @Cached(inline = false) PRaiseNode raiseNode) {
            Object res = callFormat.executeObject(frame, obj, formatSpec);
            if (res == NO_VALUE) {
                throw raiseNode.raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_FORMAT, obj);
            }
            if (!PGuards.isString(res)) {
                throw raiseNode.raise(TypeError, ErrorMessages.S_MUST_RETURN_S_NOT_P, T___FORMAT__, "str", res);
            }
            return res;
        }

        @Specialization(guards = "isString(formatSpec)", replaces = "doGeneric")
        static Object doGenericUncached(VirtualFrame frame, Object obj, Object formatSpec) {
            PythonUtils.assertUncached();
            Object klass = GetClassNode.executeUncached(obj);
            Object slot = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Format).execute(klass);
            return CallBinaryMethodNode.getUncached().executeObject(frame, slot, obj, formatSpec);
        }

        // Note: PRaiseNode is @Exclusive to workaround a bug in DSL
        @Fallback
        static Object doNonStringFormat(Object obj, Object formatSpec,
                        @Exclusive @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.S_MUST_BE_S_NOT_P, "Format specifier", "a string", formatSpec);
        }
    }
}
