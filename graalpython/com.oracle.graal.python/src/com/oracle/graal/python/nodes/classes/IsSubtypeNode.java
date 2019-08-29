/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "cpython://Objects/abstract.c/recursive_issubclass")
@ImportStatic(PythonOptions.class)
public abstract class IsSubtypeNode extends PNodeWithContext {

    @Child private AbstractObjectGetBasesNode getBasesNode = AbstractObjectGetBasesNode.create();
    @Child private AbstractObjectIsSubclassNode abstractIsSubclassNode = AbstractObjectIsSubclassNode.create();
    @Child private GetMroStorageNode getMroNode;
    @Child private IsSameTypeNode isSameTypeNode;
    @Child private PRaiseNode raise;

    private final ConditionProfile exceptionDerivedProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile exceptionClsProfile = ConditionProfile.createBinaryProfile();

    public abstract boolean execute(VirtualFrame frame, Object derived, Object cls);

    protected static boolean isSameType(IsSameTypeNode isSameTypeNode, LazyPythonClass cls, LazyPythonClass cachedCls) {
        return isSameTypeNode.execute(cls, cachedCls);
    }

    @Specialization(guards = { //
                    "isSameType(isSameDerivedNode, derived, cachedDerived)", //
                    "isSameType(isSameClsNode, cls, cachedCls)", //
                    "mro.getInternalClassArray().length < 32" //
    }, //
                    limit = "getVariableArgumentInlineCacheLimit()", //
                    assumptions = { //
                                    "mro.getLookupStableAssumption()", //
                                    "singleContextAssumption()"
                    })
    @ExplodeLoop
    boolean isSubtypeOfCached(@SuppressWarnings("unused") LazyPythonClass derived, @SuppressWarnings("unused") PythonAbstractClass cls,
                    @Cached("derived") @SuppressWarnings("unused") LazyPythonClass cachedDerived,
                    @Cached("cls") PythonAbstractClass cachedCls,
                    @Cached("getMro(cachedDerived)") MroSequenceStorage mro,
                    @Cached("createFast()") @SuppressWarnings("unused") IsSameTypeNode isSameDerivedNode,
                    @Cached("createFast()") @SuppressWarnings("unused") IsSameTypeNode isSameClsNode) {
        for (PythonAbstractClass n : mro.getInternalClassArray()) {
            if (isSameType(n, cachedCls)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(guards = { //
                    "isSameType(isSameDerivedNode, derived, cachedDerived)", //
                    "mro.getInternalClassArray().length < 32" //
    }, //
                    limit = "getVariableArgumentInlineCacheLimit()", //
                    replaces = "isSubtypeOfCached", //
                    assumptions = { //
                                    "mro.getLookupStableAssumption()", //
                                    "singleContextAssumption()"
                    })
    @ExplodeLoop
    boolean isSubtypeOfVariableTypeCached(@SuppressWarnings("unused") LazyPythonClass derived, LazyPythonClass cls,
                    @Cached("derived") @SuppressWarnings("unused") LazyPythonClass cachedDerived,
                    @Cached("getMro(cachedDerived)") MroSequenceStorage mro,
                    @Cached("createFast()") @SuppressWarnings("unused") IsSameTypeNode isSameDerivedNode) {
        for (PythonAbstractClass n : mro.getInternalClassArray()) {
            if (isSameType(n, cls)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(guards = { //
                    "derived == cachedDerived", //
                    "cls == cachedCls", //
                    "mro.getInternalClassArray().length < 32" //
    }, //
                    limit = "getVariableArgumentInlineCacheLimit()", //
                    assumptions = "mro.getLookupStableAssumption()")
    @ExplodeLoop
    boolean isSubtypeOfConstantType(@SuppressWarnings("unused") LazyPythonClass derived, @SuppressWarnings("unused") PythonAbstractClass cls,
                    @Cached("derived") @SuppressWarnings("unused") LazyPythonClass cachedDerived,
                    @Cached("cls") PythonAbstractClass cachedCls,
                    @Cached("getMro(cachedDerived)") MroSequenceStorage mro) {
        for (PythonAbstractClass n : mro.getInternalClassArray()) {
            if (isSameType(n, cachedCls)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(guards = { //
                    "derived == cachedDerived", //
                    "mro.getInternalClassArray().length < 32" //
    }, //
                    limit = "getVariableArgumentInlineCacheLimit()", //
                    replaces = "isSubtypeOfConstantType", //
                    assumptions = "mro.getLookupStableAssumption()" //
    )
    @ExplodeLoop
    boolean isSubtypeOfVariableType(@SuppressWarnings("unused") LazyPythonClass derived, LazyPythonClass cls,
                    @Cached("derived") @SuppressWarnings("unused") LazyPythonClass cachedDerived,
                    @Cached("getMro(cachedDerived)") MroSequenceStorage mro) {
        for (PythonAbstractClass n : mro.getInternalClassArray()) {
            if (isSameType(n, cls)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(replaces = {"isSubtypeOfCached", "isSubtypeOfVariableTypeCached", "isSubtypeOfConstantType", "isSubtypeOfVariableType"})
    boolean issubTypeGeneric(LazyPythonClass derived, LazyPythonClass cls) {
        for (PythonAbstractClass n : getMro(derived).getInternalClassArray()) {
            if (isSameType(n, cls)) {
                return true;
            }
        }
        return false;
    }

    @Fallback
    public boolean isSubclass(VirtualFrame frame, Object derived, Object cls) {
        if (raise == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raise = insert(PRaiseNode.create());
        }

        if (exceptionDerivedProfile.profile(getBasesNode.execute(frame, derived) == null)) {
            throw raise.raise(PythonErrorType.TypeError, "issubclass() arg 1 must be a class");
        }

        if (exceptionClsProfile.profile(getBasesNode.execute(frame, cls) == null)) {
            throw raise.raise(PythonErrorType.TypeError, "issubclass() arg 2 must be a class or tuple of classes");
        }

        return abstractIsSubclassNode.execute(frame, derived, cls);
    }

    protected MroSequenceStorage getMro(LazyPythonClass clazz) {
        if (getMroNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMroNode = insert(GetMroStorageNode.create());
        }
        return getMroNode.execute(clazz);
    }

    private boolean isSameType(LazyPythonClass left, LazyPythonClass right) {
        if (isSameTypeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isSameTypeNode = insert(IsSameTypeNode.createFast());
        }
        return isSameTypeNode.execute(left, right);
    }

    public static IsSubtypeNode create() {
        return IsSubtypeNodeGen.create();
    }

    @GenerateUncached
    public abstract static class IsSubtypeWithoutFrameNode extends PNodeWithContext {

        public abstract boolean executeWithGlobalState(Object derived, Object cls);

        @Specialization
        public boolean execute(Object derived, Object cls,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached IsSameTypeNode isSameTypeNode) {
            for (PythonAbstractClass n : getMroStorageNode.execute(derived).getInternalClassArray()) {
                if (isSameTypeNode.execute(n, cls)) {
                    return true;
                }
            }
            return false;
        }
    }
}
