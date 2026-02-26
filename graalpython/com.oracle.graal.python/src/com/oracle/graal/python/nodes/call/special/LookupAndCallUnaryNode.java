/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PythonOptions.class)
public abstract class LookupAndCallUnaryNode extends UnaryOpNode {
    protected final TruffleString name;

    public abstract Object executeObject(VirtualFrame frame, Object receiver) throws SpecialMethodNotFound;

    @Override
    public Object execute(VirtualFrame frame, Object receiver) {
        return executeObject(frame, receiver);
    }

    @NeverDefault
    public static LookupAndCallUnaryNode create(TruffleString name) {
        return LookupAndCallUnaryNodeGen.create(name);
    }

    LookupAndCallUnaryNode(TruffleString name) {
        this.name = name;
    }

    public TruffleString getMethodName() {
        return name;
    }

    protected final PythonUnaryBuiltinNode getUnaryBuiltin(PythonBuiltinClassType clazz) {
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(clazz, name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonUnaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonUnaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
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

    // Object

    @Specialization(guards = {"clazz != null", "function != null", "isClazz(inliningTarget, clazz, receiver, getClassNode)"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObjectBuiltin(VirtualFrame frame, Object receiver,
                    @SuppressWarnings("unused") @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached("getBuiltinClass($node, receiver, getClassNode)") PythonBuiltinClassType clazz,
                    @Cached("getUnaryBuiltin(clazz)") PythonUnaryBuiltinNode function) {
        return function.execute(frame, receiver);
    }

    @Specialization(guards = "getObjectClass(receiver) == cachedClass", limit = "3")
    Object callObjectGeneric(VirtualFrame frame, Object receiver,
                    @Bind Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("receiver.getClass()") Class<?> cachedClass,
                    @Shared @Cached InlinedBranchProfile notFoundProfile,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached("create(name)") LookupSpecialMethodNode getattr,
                    @Shared @Cached CallUnaryMethodNode dispatchNode) {
        return doCallObject(frame, inliningTarget, notFoundProfile, receiver, getClassNode, getattr, dispatchNode);
    }

    @Specialization(replaces = "callObjectGeneric")
    @Megamorphic
    @InliningCutoff
    @SuppressWarnings("truffle-static-method")
    Object callObjectMegamorphic(VirtualFrame frame, Object receiver,
                    @Bind Node inliningTarget,
                    @Shared @Cached InlinedBranchProfile notFoundProfile,
                    @Shared @Cached GetClassNode getClassNode,
                    @Shared @Cached("create(name)") LookupSpecialMethodNode getattr,
                    @Shared @Cached CallUnaryMethodNode dispatchNode) {
        return doCallObject(frame, inliningTarget, notFoundProfile, receiver, getClassNode, getattr, dispatchNode);
    }

    protected Class<?> getObjectClass(Object object) {
        return object.getClass();
    }

    private Object doCallObject(VirtualFrame frame, Node inliningTarget, InlinedBranchProfile notFoundProfile,
                    Object receiver, GetClassNode getClassNode, LookupSpecialMethodNode getattr, CallUnaryMethodNode dispatchNode) {
        Object attr = getattr.execute(frame, getClassNode.execute(inliningTarget, receiver), receiver);
        if (attr == PNone.NO_VALUE) {
            notFoundProfile.enter(inliningTarget);
            throw SpecialMethodNotFound.INSTANCE;
        }
        return dispatchNode.executeObject(frame, attr, receiver);
    }

    @GenerateUncached
    @GenerateInline(false)       // footprint reduction 36 -> 20
    public abstract static class LookupAndCallUnaryDynamicNode extends PNodeWithContext {

        public abstract Object executeObject(Object receiver, TruffleString name);

        @Specialization
        static Object doObject(Object receiver, TruffleString name,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached LookupSpecialMethodNode.Dynamic getattr,
                        @Cached CallUnaryMethodNode dispatchNode,
                        @Cached InlinedConditionProfile profile) {
            Object attr = getattr.execute(null, inliningTarget, getClassNode.execute(inliningTarget, receiver), name, receiver);
            if (profile.profile(inliningTarget, attr != PNone.NO_VALUE)) {
                // NOTE: it's safe to pass a 'null' frame since this node can only be used via a
                // global state context manager
                return dispatchNode.executeObject(null, attr, receiver);
            }
            return PNone.NO_VALUE;
        }

        public static LookupAndCallUnaryDynamicNode getUncached() {
            return LookupAndCallUnaryNodeGen.LookupAndCallUnaryDynamicNodeGen.getUncached();
        }
    }
}
