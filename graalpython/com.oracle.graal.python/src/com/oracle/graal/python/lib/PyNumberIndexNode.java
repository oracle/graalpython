/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyNumber_Index}. Converts objects to Python integral types (can be
 * {@code int}, {@code long}, {@code boolean}, {@link PInt} or a native integer (
 * {@link PythonAbstractNativeObject}) using their {@code __index__} method. Raises
 * {@code TypeError} if they don't have any.
 */
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PyNumberIndexNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Node inliningTarget, Object object);

    public static Object executeUncached(Object object) {
        return PyNumberIndexNodeGen.getUncached().execute(null, null, object);
    }

    @Specialization
    static int doInt(int object) {
        return object;
    }

    @Specialization
    static long doLong(long object) {
        return object;
    }

    @Specialization(guards = "isBuiltinPInt(object)")
    static PInt doPInt(PInt object) {
        return object;
    }

    @Fallback
    static Object doCallIndex(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached GetClassNode getClassNode,
                    @Cached TpSlots.GetCachedTpSlotsNode getSlots,
                    @Cached CallSlotUnaryNode callIndex,
                    @Cached(inline = false) IsSubtypeNode isSubtype,
                    @Cached PRaiseNode.Lazy raiseNode,
                    @Cached PyLongCheckExactNode checkNode,
                    @Cached CheckIndexResultNotInt checkResult,
                    @Cached PyLongCopy copy) {
        Object type = getClassNode.execute(inliningTarget, object);
        if (isSubtype.execute(type, PythonBuiltinClassType.PInt)) {
            return copy.execute(inliningTarget, object);
        }
        TpSlots slots = getSlots.execute(inliningTarget, type);
        if (slots.nb_index() == null) {
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, object);
        }
        Object result = callIndex.execute(frame, inliningTarget, slots.nb_index(), object);
        if (checkNode.execute(inliningTarget, result)) {
            return result;
        }
        return checkResult(frame, result, object, checkResult);
    }

    @InliningCutoff
    private static Object checkResult(VirtualFrame frame, Object result, Object object, CheckIndexResultNotInt checkResult) {
        return checkResult.execute(frame, object, result);
    }

    @GenerateInline(false) // Slow path
    @GenerateUncached
    abstract static class CheckIndexResultNotInt extends Node {
        abstract Object execute(VirtualFrame frame, Object original, Object result);

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object original, Object result,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PyLongCopy copy) {
            if (!isSubtype.execute(getClassNode.execute(inliningTarget, result), PythonBuiltinClassType.PInt)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
            }
            warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                            ErrorMessages.WARN_P_RETURNED_NON_P, original, T___INDEX__, "int", result, "int");
            return copy.execute(inliningTarget, result);
        }
    }
}
