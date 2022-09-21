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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyLong_AsLongAndOverflow}. Converts an object into a Java long
 * using it's {@code __index__} or (deprecated) {@code __int__} method. In CPython the overflow is
 * communicated through an output variable, we communicate it using a checked exception
 * {@link com.oracle.graal.python.util.OverflowException}.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyLongAsLongAndOverflowNode extends PNodeWithContext {
    public abstract long execute(Frame frame, Object object) throws OverflowException;

    @Specialization
    static long doInt(int object) {
        return object;
    }

    @Specialization
    static long doLong(long object) {
        return object;
    }

    @Specialization
    static long doPInt(PInt object) throws OverflowException {
        return object.longValueExact();
    }

    @Specialization
    static long doBoolean(boolean x) {
        return x ? 1 : 0;
    }

    // TODO When we implement casting native longs, this should cast them instead of calling their
    // __index__
    @Specialization(guards = "!canBeInteger(object)")
    long doObject(VirtualFrame frame, Object object,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Index") LookupSpecialMethodSlotNode lookupIndex,
                    @Cached(parameters = "Int") LookupSpecialMethodSlotNode lookupInt,
                    @Cached CallUnaryMethodNode call,
                    @Cached PyLongCheckNode resultSubtype,
                    @Cached PyLongCheckExactNode resultIsInt,
                    @Cached WarningsModuleBuiltins.WarnNode warnNode,
                    @Cached PRaiseNode raiseNode,
                    @Cached PyLongAsLongAndOverflowNode recursive) throws OverflowException {
        Object type = getClassNode.execute(object);
        Object indexDescr = lookupIndex.execute(frame, type, object);
        Object result = null;
        if (indexDescr != PNone.NO_VALUE) {
            result = call.executeObject(frame, indexDescr, object);
            checkResult(frame, object, result, resultSubtype, resultIsInt, raiseNode, warnNode, T___INDEX__);
        }
        Object intDescr = lookupInt.execute(frame, type, object);
        if (intDescr != PNone.NO_VALUE) {
            result = call.executeObject(frame, intDescr, object);
            checkResult(frame, object, result, resultSubtype, resultIsInt, raiseNode, warnNode, T___INT__);
            warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                            ErrorMessages.WARN_INT_CONVERSION_DEPRECATED, object);
        }
        if (result == null) {
            throw raiseNode.raise(TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, object);
        }
        return recursive.execute(frame, result);
    }

    private static void checkResult(VirtualFrame frame, Object originalObject, Object result, PyLongCheckNode isSubtype, PyLongCheckExactNode isInt, PRaiseNode raiseNode,
                    WarningsModuleBuiltins.WarnNode warnNode, TruffleString methodName) {
        if (!isInt.execute(result)) {
            if (!isSubtype.execute(result)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.RETURNED_NON_INT, methodName, result);
            }
            warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                            ErrorMessages.WARN_P_RETURNED_NON_P, originalObject, methodName, "int", result, "int");
        }
    }
}
