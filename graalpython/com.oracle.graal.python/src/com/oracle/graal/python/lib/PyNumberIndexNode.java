/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Equivalent of CPython's {@code PyNumber_Index}. Converts objects to Python integral types (can be
 * {@code int}, {@code long}, {@code boolean}, {@link PInt} or a native integer (
 * {@link PythonAbstractNativeObject}) using their {@code __index__} method. Raises
 * {@code TypeError} if they don't have any.
 */
@ImportStatic(SpecialMethodSlot.class)
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PyNumberIndexNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Node inliningTarget, Object object);

    @Specialization
    static int doInt(int object) {
        return object;
    }

    @Specialization
    static long doLong(long object) {
        return object;
    }

    @Specialization
    static PInt doPInt(PInt object) {
        return object;
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    static int doCallIndexInt(VirtualFrame frame, Node inliningTarget, Object object,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Shared("lookupIndex") @Cached(parameters = "Index", inline = false) LookupSpecialMethodSlotNode lookupIndex,
                    @Shared("callIndex") @Cached(inline = false) CallUnaryMethodNode callIndex,
                    @Shared("isSubtype") @Cached(inline = false) IsSubtypeNode isSubtype,
                    @Exclusive @Cached PRaiseNode.Lazy raiseNode) throws UnexpectedResultException {
        Object type = getClassNode.execute(inliningTarget, object);
        if (isSubtype.execute(type, PythonBuiltinClassType.PInt)) {
            throw new UnexpectedResultException(object);
        }
        Object indexDescr = lookupIndex.execute(frame, type, object);
        if (indexDescr == PNone.NO_VALUE) {
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, object);
        }
        try {
            return PGuards.expectInteger(callIndex.executeObject(frame, indexDescr, object));
        } catch (UnexpectedResultException e) {
            // Implicit CompilerDirectives.transferToInterpreterAndInvalidate()
            EncapsulatingNodeReference nodeRef = EncapsulatingNodeReference.getCurrent();
            Node outerNode = nodeRef.set(inliningTarget);
            try {
                Object result = checkResult(frame, object, e.getResult(), null, GetClassNode.getUncached(),
                                IsSubtypeNode.getUncached(), PyLongCheckExactNode.getUncached(), PRaiseNode.Lazy.getUncached(),
                                WarningsModuleBuiltins.WarnNode.getUncached(), PythonObjectFactory.getUncached());
                throw new UnexpectedResultException(result);
            } finally {
                nodeRef.set(outerNode);
            }
        }
    }

    @Specialization(replaces = "doCallIndexInt")
    static Object doCallIndex(VirtualFrame frame, Node inliningTarget, Object object,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Shared("lookupIndex") @Cached(parameters = "Index", inline = false) LookupSpecialMethodSlotNode lookupIndex,
                    @Shared("callIndex") @Cached(inline = false) CallUnaryMethodNode callIndex,
                    @Shared("isSubtype") @Cached(inline = false) IsSubtypeNode isSubtype,
                    @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                    @Exclusive @Cached GetClassNode resultClassNode,
                    @Exclusive @Cached(inline = false) IsSubtypeNode resultSubtype,
                    @Cached PyLongCheckExactNode isInt,
                    @Cached(inline = false) WarningsModuleBuiltins.WarnNode warnNode,
                    @Cached(inline = false) PythonObjectFactory factory) {
        Object type = getClassNode.execute(inliningTarget, object);
        if (isSubtype.execute(type, PythonBuiltinClassType.PInt)) {
            return object;
        }
        Object indexDescr = lookupIndex.execute(frame, type, object);
        if (indexDescr == PNone.NO_VALUE) {
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, object);
        }
        Object result = callIndex.executeObject(frame, indexDescr, object);
        return checkResult(frame, object, result, inliningTarget, resultClassNode, resultSubtype, isInt, raiseNode, warnNode, factory);
    }

    private static Object checkResult(VirtualFrame frame, Object originalObject, Object result, Node inliningTarget, GetClassNode getClassNode, IsSubtypeNode isSubtype,
                    PyLongCheckExactNode isInt, PRaiseNode.Lazy raiseNode,
                    WarningsModuleBuiltins.WarnNode warnNode, PythonObjectFactory factory) {
        if (isInt.execute(inliningTarget, result)) {
            return result;
        }
        if (!isSubtype.execute(getClassNode.execute(inliningTarget, result), PythonBuiltinClassType.PInt)) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
        }
        warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                        ErrorMessages.WARN_P_RETURNED_NON_P, originalObject, T___INDEX__, "int", result, "int");
        if (result instanceof PInt) {
            return factory.createInt(((PInt) result).getValue());
        } else if (result instanceof Boolean) {
            return (boolean) result ? 1 : 0;
        } else if (result instanceof PythonAbstractNativeObject) {
            throw CompilerDirectives.shouldNotReachHere("Cannot convert native result from __index__");
        } else {
            throw CompilerDirectives.shouldNotReachHere("Unexpected type returned from __index__");
        }
    }
}
