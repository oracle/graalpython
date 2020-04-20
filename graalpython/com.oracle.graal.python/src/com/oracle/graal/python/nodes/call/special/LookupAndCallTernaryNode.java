/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.util.Supplier;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@ImportStatic({SpecialMethodNames.class})
public abstract class LookupAndCallTernaryNode extends Node {
    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(Object arg, Object arg2, Object arg3);
    }

    protected final String name;
    private final boolean isReversible;
    @Child private CallTernaryMethodNode dispatchNode = CallTernaryMethodNode.create();
    @Child private CallTernaryMethodNode reverseDispatchNode;
    @Child private CallTernaryMethodNode thirdDispatchNode;
    @Child private LookupInheritedAttributeNode getThirdAttrNode;
    @Child private NotImplementedHandler handler;
    protected final Supplier<NotImplementedHandler> handlerFactory;

    public abstract Object execute(VirtualFrame frame, Object arg1, Object arg2, Object arg3);

    public abstract Object execute(VirtualFrame frame, Object arg1, int arg2, Object arg3);

    public static LookupAndCallTernaryNode create(String name) {
        return LookupAndCallTernaryNodeGen.create(name, false, null);
    }

    public static LookupAndCallTernaryNode createReversible(
                    String name, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallTernaryNodeGen.create(name, true, handlerFactory);
    }

    LookupAndCallTernaryNode(
                    String name, boolean isReversible, Supplier<NotImplementedHandler> handlerFactory) {
        this.name = name;
        this.isReversible = isReversible;
        this.handlerFactory = handlerFactory;
    }

    protected boolean isReversible() {
        return isReversible;
    }

    @Specialization(guards = "!isReversible()")
    Object callObject(
                    VirtualFrame frame,
                    Object arg1,
                    int arg2,
                    Object arg3,
                    @Cached("create()") GetClassNode getclass,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getattr) {
        return dispatchNode.execute(frame, getattr.executeObject(frame, getclass.execute(arg1), name), arg1, arg2, arg3);
    }

    @Specialization(guards = "!isReversible()")
    Object callObject(
                    VirtualFrame frame,
                    Object arg1,
                    Object arg2,
                    Object arg3,
                    @Cached("create()") GetClassNode getclass,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getattr) {
        return dispatchNode.execute(frame, getattr.executeObject(frame, getclass.execute(arg1), name), arg1, arg2, arg3);
    }

    private CallTernaryMethodNode ensureReverseDispatch() {
        // this also serves as a branch profile
        if (reverseDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reverseDispatchNode = insert(CallTernaryMethodNode.create());
        }
        return reverseDispatchNode;
    }

    private LookupInheritedAttributeNode ensureGetAttrZ() {
        // this also serves as a branch profile
        if (getThirdAttrNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getThirdAttrNode = insert(LookupInheritedAttributeNode.create(name));
        }
        return getThirdAttrNode;
    }

    private CallTernaryMethodNode ensureThirdDispatch() {
        // this also serves as a branch profile
        if (thirdDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            thirdDispatchNode = insert(CallTernaryMethodNode.create());
        }
        return thirdDispatchNode;
    }

    @Specialization(guards = "isReversible()")
    Object callObject(
                    VirtualFrame frame,
                    Object v,
                    Object w,
                    Object z,
                    @Cached("create(name)") LookupAttributeInMRONode getattr,
                    @Cached("create(name)") LookupAttributeInMRONode getattrR,
                    @Cached("create()") GetClassNode getClass,
                    @Cached("create()") GetClassNode getClassR,
                    @Cached("create()") IsSubtypeNode isSubtype,
                    @Cached("create()") IsSameTypeNode isSameTypeNode,
                    @Cached("create()") BranchProfile notImplementedBranch) {
        PythonAbstractClass leftClass = getClass.execute(v);
        PythonAbstractClass rightClass = getClassR.execute(w);

        Object result = PNotImplemented.NOT_IMPLEMENTED;
        Object leftCallable = getattr.execute(leftClass);
        Object rightCallable = PNone.NO_VALUE;

        if (!isSameTypeNode.execute(leftClass, rightClass)) {
            rightCallable = getattrR.execute(rightClass);
            if (rightCallable == leftCallable) {
                rightCallable = PNone.NO_VALUE;
            }
        }
        if (leftCallable != PNone.NO_VALUE) {
            if (rightCallable != PNone.NO_VALUE && isSubtype.execute(frame, rightClass, leftClass)) {
                result = ensureReverseDispatch().execute(frame, rightCallable, v, w, z);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                rightCallable = PNone.NO_VALUE;
            }
            result = dispatchNode.execute(frame, leftCallable, v, w, z);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }
        if (rightCallable != PNone.NO_VALUE) {
            result = ensureReverseDispatch().execute(frame, rightCallable, v, w, z);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }

        Object zCallable = ensureGetAttrZ().execute(z);
        if (zCallable != PNone.NO_VALUE && zCallable != leftCallable && zCallable != rightCallable) {
            ensureThirdDispatch().execute(frame, zCallable, v, w, z);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }

        notImplementedBranch.enter();
        if (handlerFactory != null) {
            if (handler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handler = insert(handlerFactory.get());
            }
            return handler.execute(v, w, z);
        }
        return result;
    }
}
