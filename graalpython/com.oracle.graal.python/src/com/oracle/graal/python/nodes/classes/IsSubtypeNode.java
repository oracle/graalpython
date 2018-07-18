/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "cpython://Objects/abstract.c/recursive_issubclass")
@NodeChildren({@NodeChild(value = "derived", type = PNode.class), @NodeChild(value = "cls", type = PNode.class)})
public abstract class IsSubtypeNode extends PNode {
    @Child private AbstractObjectGetBasesNode getBasesNode = AbstractObjectGetBasesNode.create();
    @Child private AbstractObjectIsSubclassNode abstractIsSubclassNode = AbstractObjectIsSubclassNode.create();

    private ConditionProfile exceptionDerivedProfile = ConditionProfile.createBinaryProfile();
    private ConditionProfile exceptionClsProfile = ConditionProfile.createBinaryProfile();

    public static IsSubtypeNode create() {
        return IsSubtypeNodeGen.create(null, null);
    }

    public static IsSubtypeNode create(PNode derived, PNode cls) {
        return IsSubtypeNodeGen.create(derived, cls);
    }

    public abstract boolean execute(Object derived, Object cls);

    @Specialization(guards = {"derived == cachedDerived", "cls == cachedCls"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    boolean isSubtypeOfConstantType(
                    @SuppressWarnings("unused") PythonClass derived,
                    @SuppressWarnings("unused") PythonClass cls,
                    @Cached("derived") PythonClass cachedDerived,
                    @Cached("cls") PythonClass cachedCls) {
        for (PythonClass n : cachedDerived.getMethodResolutionOrder()) {
            if (n == cachedCls) {
                return true;
            }
        }
        return false;
    }

    @Specialization(guards = {"derived == cachedDerived"}, limit = "getVariableArgumentInlineCacheLimit()", replaces = "isSubtypeOfConstantType")
    @ExplodeLoop
    boolean isSubtypeOfVariableType(
                    @SuppressWarnings("unused") PythonClass derived,
                    PythonClass cls,
                    @Cached("derived") PythonClass cachedDerived) {
        boolean isSubtype = false;
        for (PythonClass n : cachedDerived.getMethodResolutionOrder()) {
            if (n == cls) {
                isSubtype = true;
            }
        }
        return isSubtype;
    }

    @Specialization(replaces = {"isSubtypeOfConstantType", "isSubtypeOfVariableType"})
    boolean issubTypeGeneric(PythonClass derived, PythonClass cls) {
        for (PythonClass n : derived.getMethodResolutionOrder()) {
            if (n == cls) {
                return true;
            }
        }
        return false;
    }

    @Specialization
    public boolean isSubclass(Object derived, Object cls) {
        if (exceptionDerivedProfile.profile(getBasesNode.execute(derived) == null)) {
            throw raise(PythonErrorType.TypeError, "issubclass() arg 1 must be a class");
        }

        if (exceptionClsProfile.profile(getBasesNode.execute(cls) == null)) {
            throw raise(PythonErrorType.TypeError, "issubclass() arg 2 must be a class or tuple of classes");
        }

        return abstractIsSubclassNode.execute(derived, cls);
    }
}
