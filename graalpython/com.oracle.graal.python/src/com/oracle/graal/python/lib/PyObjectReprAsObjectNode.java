/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyObject_Repr}. Converts the object to a string using its
 * {@code __repr__} special method. Falls back to default object {@code __repr__} implementation.
 * <p>
 * The output can be either a {@link TruffleString} or a {@link PString}.
 *
 * @see PyObjectReprAsTruffleStringNode
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectReprAsObjectNode extends PNodeWithContext {

    public static PyObjectReprAsObjectNode getUncached() {
        return PyObjectReprAsObjectNodeGen.getUncached();
    }

    public abstract Object execute(Frame frame, Object object);

    @Specialization
    static Object repr(VirtualFrame frame, Object obj,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Repr") LookupSpecialMethodSlotNode lookupRepr,
                    @Cached CallUnaryMethodNode callRepr,
                    @Cached ObjectNodes.DefaultObjectReprNode defaultRepr,
                    @Cached ConditionProfile isString,
                    @Cached ConditionProfile isPString,
                    @Cached PRaiseNode raiseNode) {
        Object type = getClassNode.execute(obj);
        Object reprMethod;
        try {
            reprMethod = lookupRepr.execute(frame, type, obj);
        } catch (PException e) {
            return defaultRepr.execute(frame, obj);
        }
        if (reprMethod != PNone.NO_VALUE) {
            Object result = callRepr.executeObject(frame, reprMethod, obj);
            result = assertNoJavaString(result);
            if (isString.profile(result instanceof TruffleString) || isPString.profile(result instanceof PString)) {
                return result;
            }
            if (result != PNone.NO_VALUE) {
                throw raiseNode.raise(TypeError, ErrorMessages.RETURNED_NON_STRING, T___REPR__, obj);
            }
        }
        return defaultRepr.execute(frame, obj);
    }

    public static PyObjectReprAsObjectNode create() {
        return PyObjectReprAsObjectNodeGen.create();
    }
}
