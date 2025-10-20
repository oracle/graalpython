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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_FALSE;
import static com.oracle.graal.python.nodes.StringLiterals.T_TRUE;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyObject_Str}. Converts object to a string using its
 * {@code __str__} special method.
 * <p>
 * The output can be either a {@link String} or a {@link PString}.
 *
 * @see PyObjectReprAsTruffleStringNode
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class PyObjectStrAsObjectNode extends PNodeWithContext {
    public final Object executeCached(Frame frame, Object object) {
        return execute(frame, this, object);
    }

    public abstract Object execute(Frame frame, Node inlineTarget, Object object);

    public final Object execute(Node inliningTarget, Object object) {
        return execute(null, inliningTarget, object);
    }

    public static Object executeUncached(Object object) {
        return getUncached().execute(null, null, object);
    }

    @Specialization
    static Object str(TruffleString obj) {
        return obj;
    }

    @Specialization
    static TruffleString str(boolean object) {
        return object ? T_TRUE : T_FALSE;
    }

    @Specialization
    TruffleString str(long object,
                    @Cached TruffleString.FromLongNode fromLongNode) {
        return fromLongNode.execute(object, TS_ENCODING, false);
    }

    @Specialization(guards = "!isTruffleString(obj)")
    static Object str(VirtualFrame frame, Node inliningTarget, Object obj,
                    @Cached GetObjectSlotsNode getSlots,
                    @Cached CallSlotUnaryNode callSlot,
                    @Cached PyObjectReprAsObjectNode repr,
                    @Cached PyUnicodeCheckNode checkNode,
                    @Cached PRaiseNode raiseNode) {
        TpSlots slots = getSlots.execute(inliningTarget, obj);
        if (slots.tp_str() == null) {
            return repr.execute(frame, inliningTarget, obj);
        }
        Object result = callSlot.execute(frame, inliningTarget, slots.tp_str(), obj);
        assertNoJavaString(result);
        if (checkNode.execute(inliningTarget, result)) {
            return result;
        } else {
            throw raiseTypeError(inliningTarget, raiseNode, result);
        }
    }

    @InliningCutoff
    private static PException raiseTypeError(Node inliningTarget, PRaiseNode raiseNode, Object result) {
        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.RETURNED_NON_STRING, T___STR__, result);
    }

    @NeverDefault
    public static PyObjectStrAsObjectNode create() {
        return PyObjectStrAsObjectNodeGen.create();
    }

    public static PyObjectStrAsObjectNode getUncached() {
        return PyObjectStrAsObjectNodeGen.getUncached();
    }
}
