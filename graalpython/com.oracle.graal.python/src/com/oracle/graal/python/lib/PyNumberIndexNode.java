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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Equivalent of CPython's {@code PyNumber_Index}. Converts objects to Python integral types (can be
 * {@code int}, {@code long}, {@code boolean}, {@link PInt} or a native integer (
 * {@link PythonAbstractNativeObject}) using their {@code __index__} method. Raises
 * {@code TypeError} if they don't have any.
 */
@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class PyNumberIndexNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object object);

    public abstract int executeInt(Frame frame, Object object) throws UnexpectedResultException;

    public abstract long executeLong(Frame frame, Object object) throws UnexpectedResultException;

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
    int doCallIndexInt(VirtualFrame frame, PythonAbstractObject object,
                    @Shared("callIndex") @Cached CallIndexNode callIndex,
                    @Shared("lib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Shared("isSubtype") @Cached IsSubtypeNode isSubtype) throws UnexpectedResultException {
        if (isSubtype.execute(lib.getLazyPythonClass(object), PythonBuiltinClassType.PInt)) {
            throw new UnexpectedResultException(object);
        }
        try {
            return callIndex.executeInt(frame, object);
        } catch (UnexpectedResultException e) {
            // Implicit CompilerDirectives.transferToInterpreterAndInvalidate()
            EncapsulatingNodeReference nodeRef = EncapsulatingNodeReference.getCurrent();
            Node outerNode = nodeRef.set(this);
            try {
                checkResult(frame, object, e.getResult(), lib, isSubtype, IsBuiltinClassProfile.getUncached(), PRaiseNode.getUncached(), WarningsModuleBuiltins.WarnNode.getUncached());
                throw e;
            } finally {
                nodeRef.set(outerNode);
            }
        }
    }

    // TODO: Accept "Object" again once GR-30482 is fixed
    @Specialization(replaces = "doCallIndexInt")
    static Object doCallIndex(VirtualFrame frame, PythonAbstractObject object,
                    @Shared("callIndex") @Cached CallIndexNode callIndex,
                    @Shared("lib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Shared("isSubtype") @Cached IsSubtypeNode isSubtype,
                    @Shared("isInt") @Cached IsBuiltinClassProfile isInt,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                    @Shared("warnNode") @Cached WarningsModuleBuiltins.WarnNode warnNode) {
        if (isSubtype.execute(lib.getLazyPythonClass(object), PythonBuiltinClassType.PInt)) {
            return object;
        }
        Object result = callIndex.execute(frame, object);
        checkResult(frame, object, result, lib, isSubtype, isInt, raiseNode, warnNode);
        return result;
    }

    // TODO: Remove again once GR-30482 is fixed
    @Specialization(guards = "!isAnyPythonObject(object)")
    static Object doCallIndexForeign(VirtualFrame frame, Object object,
                    @Shared("callIndex") @Cached CallIndexNode callIndex,
                    @Shared("lib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Shared("isSubtype") @Cached IsSubtypeNode isSubtype,
                    @Shared("isInt") @Cached IsBuiltinClassProfile isInt,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                    @Shared("warnNode") @Cached WarningsModuleBuiltins.WarnNode warnNode) {
        Object result = callIndex.execute(frame, object);
        checkResult(frame, object, result, lib, isSubtype, isInt, raiseNode, warnNode);
        return result;
    }

    private static void checkResult(VirtualFrame frame, Object originalObject, Object result, PythonObjectLibrary lib, IsSubtypeNode isSubtype, IsBuiltinClassProfile isInt, PRaiseNode raiseNode,
                    WarningsModuleBuiltins.WarnNode warnNode) {
        if (!isInt.profileObject(result, PythonBuiltinClassType.PInt)) {
            if (!isSubtype.execute(lib.getLazyPythonClass(result), PythonBuiltinClassType.PInt)) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.INDEX_RETURNED_NON_INT, result);
            }
            warnNode.warnFormat(frame, null, DeprecationWarning, 1,
                            ErrorMessages.P_RETURNED_NON_P, originalObject, __INDEX__, "int", result, "int");
        }
    }

    @GenerateUncached
    protected abstract static class CallIndexNode extends PNodeWithContext {
        public abstract Object execute(Frame frame, Object object);

        public abstract int executeInt(Frame frame, Object object) throws UnexpectedResultException;

        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt(VirtualFrame frame, Object object,
                        @Shared("callIndex") @Cached("createIndexNode()") LookupAndCallUnaryNode callIndex) throws UnexpectedResultException {
            return callIndex.executeInt(frame, object);
        }

        @Specialization(replaces = "doInt")
        static Object doObject(VirtualFrame frame, Object object,
                        @Shared("callIndex") @Cached("createIndexNode()") LookupAndCallUnaryNode callIndex) {
            return callIndex.executeObject(frame, object);
        }

        @Specialization(replaces = {"doInt", "doObject"})
        static Object uncached(Object object,
                        @Cached LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode lookupAndCallUnaryDynamicNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = lookupAndCallUnaryDynamicNode.executeObject(object, __INDEX__);
            if (result == PNone.NO_VALUE) {
                throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, object);
            }
            return result;
        }

        protected static LookupAndCallUnaryNode createIndexNode() {
            return LookupAndCallUnaryNode.create(__INDEX__, () -> new LookupAndCallUnaryNode.NoAttributeHandler() {
                @Child PRaiseNode raiseNode = PRaiseNode.create();

                @Override
                public Object execute(Object receiver) {
                    throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, receiver);
                }
            });
        }
    }
}
