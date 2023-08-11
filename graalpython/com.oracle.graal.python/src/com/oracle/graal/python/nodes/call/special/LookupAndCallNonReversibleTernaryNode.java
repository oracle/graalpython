/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.TernaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

// cpython://Objects/abstract.c#ternary_op
// Order operations are tried until either a valid result or error: v.op(v,w,z), w.op(v,w,z), z.op(v,w,z)
@ImportStatic({SpecialMethodNames.class, PythonOptions.class})
public abstract class LookupAndCallNonReversibleTernaryNode extends LookupAndCallTernaryNode {

    LookupAndCallNonReversibleTernaryNode(TruffleString name) {
        super(name);
    }

    LookupAndCallNonReversibleTernaryNode(SpecialMethodSlot slot) {
        super(slot);
    }

    protected static PythonBuiltinClassType getBuiltinClass(Node inliningTarget, Object receiver, GetClassNode getClassNode) {
        Object clazz = getClassNode.execute(inliningTarget, receiver);
        return clazz instanceof PythonBuiltinClassType ? (PythonBuiltinClassType) clazz : null;
    }

    protected static boolean isClazz(Node inliningTarget, PythonBuiltinClassType clazz, Object receiver, GetClassNode getClassNode) {
        return getClassNode.execute(inliningTarget, receiver) == clazz;
    }

    protected final PythonTernaryBuiltinNode getTernaryBuiltin(PythonBuiltinClassType clazz) {
        if (slot != null) {
            Object attribute = slot.getValue(clazz);
            if (attribute instanceof TernaryBuiltinDescriptor) {
                return ((TernaryBuiltinDescriptor) attribute).createNode();
            }
            // If the slot does not contain builtin, full lookup wouldn't find a builtin either
            return null;
        }
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(clazz, name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonTernaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonTernaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
            }
        }
        return null;
    }

    @Specialization(guards = {"clazz != null", "function != null", "isClazz(inliningTarget, clazz, v, getClassNode)"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObjectBuiltin(VirtualFrame frame, Object v, Object w, Object z,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached("getBuiltinClass(this, v, getClassNode)") PythonBuiltinClassType clazz,
                    @Cached("getTernaryBuiltin(clazz)") PythonTernaryBuiltinNode function) {
        return function.execute(frame, v, w, z);
    }

    @Specialization(guards = "arg1.getClass() == cachedArg1Class", limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObject(VirtualFrame frame, Object arg1, Object arg2, Object arg3,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("arg1.getClass()") Class<?> cachedArg1Class,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Exclusive @Cached("createLookup()") LookupSpecialBaseNode getattr,
                    @Exclusive @Cached CallTernaryMethodNode dispatchNode) {
        Object klass = getClassNode.execute(inliningTarget, arg1);
        return dispatchNode.execute(frame, getattr.execute(frame, klass, arg1), arg1, arg2, arg3);
    }

    @Specialization(replaces = "callObject")
    @Megamorphic
    static Object callObjectMegamorphic(VirtualFrame frame, Object arg1, Object arg2, Object arg3,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Exclusive @Cached("createLookup()") LookupSpecialBaseNode getattr,
                    @Exclusive @Cached CallTernaryMethodNode dispatchNode) {
        Object klass = getClassNode.execute(inliningTarget, arg1);
        return dispatchNode.execute(frame, getattr.execute(frame, klass, arg1), arg1, arg2, arg3);
    }
}
