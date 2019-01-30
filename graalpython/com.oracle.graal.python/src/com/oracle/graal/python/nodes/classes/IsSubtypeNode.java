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

import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "cpython://Objects/abstract.c/recursive_issubclass")
@ImportStatic(PythonOptions.class)
public abstract class IsSubtypeNode extends PNodeWithContext {

    @Child private AbstractObjectGetBasesNode getBasesNode = AbstractObjectGetBasesNode.create();
    @Child private AbstractObjectIsSubclassNode abstractIsSubclassNode = AbstractObjectIsSubclassNode.create();
    @Child private GetMroNode getMroNode;
    @Child private IsSameTypeNode isSameTypeNode;

    private final ConditionProfile exceptionDerivedProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile exceptionClsProfile = ConditionProfile.createBinaryProfile();

    public static IsSubtypeNode create() {
        return IsSubtypeNodeGen.create();
    }

    public abstract boolean execute(Object derived, Object cls);

    @Specialization(guards = {"derived == cachedDerived", "cls == cachedCls"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    boolean isSubtypeOfConstantType(@SuppressWarnings("unused") PythonAbstractClass derived, @SuppressWarnings("unused") PythonAbstractClass cls,
                    @Cached("derived") PythonAbstractClass cachedDerived,
                    @Cached("cls") PythonAbstractClass cachedCls) {
        for (PythonAbstractClass n : getMro(cachedDerived)) {
            if (isSameType(n, cachedCls)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(guards = {"derived == cachedDerived"}, limit = "getVariableArgumentInlineCacheLimit()", replaces = "isSubtypeOfConstantType")
    @ExplodeLoop
    boolean isSubtypeOfVariableType(@SuppressWarnings("unused") PythonAbstractClass derived, PythonAbstractClass cls,
                    @Cached("derived") PythonAbstractClass cachedDerived) {
        for (PythonAbstractClass n : getMro(cachedDerived)) {
            if (isSameType(n, cls)) {
                return true;
            }
        }
        return false;
    }

    @Specialization(replaces = {"isSubtypeOfConstantType", "isSubtypeOfVariableType"})
    boolean issubTypeGeneric(PythonAbstractClass derived, PythonAbstractClass cls) {
        for (PythonAbstractClass n : getMro(derived)) {
            if (isSameType(n, cls)) {
                return true;
            }
        }
        return false;
    }

    @Fallback
    public boolean isSubclass(Object derived, Object cls) {
        if (exceptionDerivedProfile.profile(getBasesNode.execute(derived) == null)) {
            throw raise(PythonErrorType.TypeError, "issubclass() arg 1 must be a class");
        }

        if (exceptionClsProfile.profile(getBasesNode.execute(cls) == null)) {
            throw raise(PythonErrorType.TypeError, "issubclass() arg 2 must be a class or tuple of classes");
        }

        return abstractIsSubclassNode.execute(derived, cls);
    }

    private PythonAbstractClass[] getMro(PythonAbstractClass clazz) {
        if (getMroNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMroNode = insert(GetMroNode.create());
        }
        return getMroNode.execute(clazz);
    }

    private boolean isSameType(PythonAbstractClass left, PythonAbstractClass right) {
        if (isSameTypeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isSameTypeNode = insert(IsSameTypeNode.create());
        }
        return isSameTypeNode.execute(left, right);
    }
}
