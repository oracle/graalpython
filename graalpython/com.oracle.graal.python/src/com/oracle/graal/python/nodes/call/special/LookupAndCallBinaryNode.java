/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Helper node for what would be a sequence of {@code _PyObject_LookupSpecial} and
 * {@code PyObject_CallXXXA}. Callers should handle exception {@link SpecialMethodNotFound}, which
 * indicates that the method was not found.
 */
@ImportStatic(PythonOptions.class)
public abstract class LookupAndCallBinaryNode extends Node {
    @Child private CallBinaryMethodNode dispatchNode;
    protected final TruffleString name;

    LookupAndCallBinaryNode(TruffleString name) {
        this.name = name;
    }

    public abstract Object executeObject(VirtualFrame frame, Object arg, Object arg2) throws SpecialMethodNotFound;

    @NeverDefault
    public static LookupAndCallBinaryNode create(TruffleString name) {
        return LookupAndCallBinaryNodeGen.create(name);
    }

    protected final CallBinaryMethodNode ensureDispatch() {
        // this also serves as a branch profile
        if (dispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchNode = insert(CallBinaryMethodNode.create());
        }
        return dispatchNode;
    }

    protected final PythonBinaryBuiltinNode getBinaryBuiltin(PythonBuiltinClassType clazz) {
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(clazz, name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonBinaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonBinaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
            }
        }
        return null;
    }

    protected static PythonBuiltinClassType getBuiltinClass(Node inliningTarget, Object receiver, GetClassNode getClassNode) {
        Object clazz = getClassNode.execute(inliningTarget, receiver);
        return clazz instanceof PythonBuiltinClassType ? (PythonBuiltinClassType) clazz : null;
    }

    protected static boolean isClazz(Node inliningTarget, PythonBuiltinClassType clazz, Object receiver, GetClassNode getClassNode) {
        return getClassNode.execute(inliningTarget, receiver) == clazz;
    }

    // Object, Object

    @Specialization(guards = {"clazz != null", "function != null", "isClazz(inliningTarget, clazz, left, getClassNode)"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObjectBuiltin(VirtualFrame frame, Object left, Object right,
                    @SuppressWarnings("unused") @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached("getBuiltinClass(this, left, getClassNode)") PythonBuiltinClassType clazz,
                    @Cached("getBinaryBuiltin(clazz)") PythonBinaryBuiltinNode function) {
        return function.execute(frame, left, right);
    }

    @Specialization(guards = {"left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
    @SuppressWarnings("truffle-static-method")
    Object callObjectGeneric(VirtualFrame frame, Object left, Object right,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                    @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Exclusive @Cached("create(name)") LookupSpecialMethodNode getattr) {
        return doCallObject(frame, inliningTarget, left, right, getClassNode, getattr);
    }

    @Specialization(replaces = "callObjectGeneric")
    @Megamorphic
    @SuppressWarnings("truffle-static-method")
    Object callObjectMegamorphic(VirtualFrame frame, Object left, Object right,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Exclusive @Cached("create(name)") LookupSpecialMethodNode getattr) {
        return doCallObject(frame, inliningTarget, left, right, getClassNode, getattr);
    }

    private Object doCallObject(VirtualFrame frame, Node inliningTarget, Object left, Object right, GetClassNode getClassNode, LookupSpecialMethodNode getattr) {
        Object leftClass = getClassNode.execute(inliningTarget, left);
        Object leftCallable = getattr.execute(frame, leftClass, left);
        if (PGuards.isNoValue(leftCallable)) {
            throw SpecialMethodNotFound.INSTANCE;
        }
        return ensureDispatch().executeObject(frame, leftCallable, left, right);
    }
}
