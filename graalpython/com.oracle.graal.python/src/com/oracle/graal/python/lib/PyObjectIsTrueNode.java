/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of CPython's {@code PyObject_IsTrue}. Converts object to a boolean value using its
 * {@code __bool__} special method. Falls back to comparing {@code __len__} result with 0. Defaults
 * to true if neither is defined.
 */
@GenerateUncached
public abstract class PyObjectIsTrueNode extends PNodeWithContext {
    public abstract boolean execute(Frame frame, Object object);

    @Specialization
    static boolean doBoolean(boolean object) {
        return object;
    }

    @Specialization
    static boolean doNone(@SuppressWarnings("unused") PNone object) {
        return false;
    }

    @Specialization
    static boolean doInt(int object) {
        return object != 0;
    }

    @Specialization
    static boolean doLong(long object) {
        return object != 0;
    }

    @Specialization
    static boolean doDouble(double object) {
        return object != 0.0;
    }

    @Specialization
    static boolean doString(String object) {
        return object.length() != 0;
    }

    @Specialization(guards = {"!isBoolean(object)", "!isPNone(object)", "!isInteger(object)", "!isDouble(object)"})
    static boolean doObject(VirtualFrame frame, Object object,
                    @Cached GetClassNode getClassNode,
                    @Cached LookupSpecialMethodNode.Dynamic lookupBool,
                    @Cached LookupSpecialMethodNode.Dynamic lookupLen,
                    @Cached CallUnaryMethodNode callBool,
                    @Cached CallUnaryMethodNode callLen,
                    @Cached PyNumberIndexNode indexNode,
                    @Cached CastToJavaIntLossyNode castLossy,
                    @Cached PyNumberAsSizeNode asSizeNode,
                    @Cached PRaiseNode raiseNode) {
        Object type = getClassNode.execute(object);
        Object boolDescr = lookupBool.execute(frame, type, __BOOL__, object, false);
        if (boolDescr != PNone.NO_VALUE) {
            Object result = callBool.executeObject(frame, boolDescr, object);
            if (result instanceof Boolean) {
                return (boolean) result;
            } else {
                throw raiseNode.raise(TypeError, ErrorMessages.BOOL_SHOULD_RETURN_BOOL, result);
            }
        }
        Object lenDescr = lookupLen.execute(frame, type, __LEN__, object, false);
        if (lenDescr != PNone.NO_VALUE) {
            Object result = indexNode.execute(frame, callLen.executeObject(frame, lenDescr, object));
            int len;
            try {
                len = asSizeNode.executeExact(frame, result);
            } catch (PException e) {
                len = castLossy.execute(result);
                if (len >= 0) {
                    throw e;
                }
            }
            if (len < 0) {
                throw raiseNode.raise(ValueError, ErrorMessages.LEN_SHOULD_RETURN_GT_ZERO);
            }
            return len != 0;
        }
        return true;
    }

    public static PyObjectIsTrueNode create() {
        return PyObjectIsTrueNodeGen.create();
    }

    public static PyObjectIsTrueNode getUncached() {
        return PyObjectIsTrueNodeGen.getUncached();
    }
}
