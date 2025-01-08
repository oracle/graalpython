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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.Supplier;
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

@ImportStatic(PythonOptions.class)
abstract class LookupAndCallNonReversibleBinaryNode extends LookupAndCallBinaryNode {
    protected final SpecialMethodSlot slot;
    protected final TruffleString name;

    LookupAndCallNonReversibleBinaryNode(TruffleString name, Supplier<NotImplementedHandler> handlerFactory, boolean ignoreDescriptorException) {
        super(handlerFactory, ignoreDescriptorException);
        this.name = name;
        this.slot = null;
    }

    LookupAndCallNonReversibleBinaryNode(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handlerFactory, boolean ignoreDescriptorException) {
        super(handlerFactory, ignoreDescriptorException);
        // If the slot is reversible, use LookupAndCallReversibleBinaryNode via factory in
        // LookupAndCallBinaryNode
        assert slot.getReverse() == null;
        this.name = slot.getName();
        this.slot = slot;
    }

    protected final PythonBinaryBuiltinNode getBinaryBuiltin(PythonBuiltinClassType clazz) {
        if (slot != null) {
            Object attribute = slot.getValue(clazz);
            if (attribute instanceof BinaryBuiltinDescriptor) {
                return ((BinaryBuiltinDescriptor) attribute).createNode();
            }
            // If the slot does not contain builtin, full lookup wouldn't find a builtin either
            return null;
        }
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
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached("getBuiltinClass(this, left, getClassNode)") PythonBuiltinClassType clazz,
                    @Cached("getBinaryBuiltin(clazz)") PythonBinaryBuiltinNode function) {
        return function.execute(frame, left, right);
    }

    @Specialization(guards = {"left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
    @SuppressWarnings("truffle-static-method")
    Object callObjectGeneric(VirtualFrame frame, Object left, Object right,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                    @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Exclusive @Cached("createLookup()") LookupSpecialBaseNode getattr) {
        return doCallObject(frame, inliningTarget, left, right, getClassNode, getattr);
    }

    @Specialization(replaces = "callObjectGeneric")
    @Megamorphic
    @SuppressWarnings("truffle-static-method")
    Object callObjectMegamorphic(VirtualFrame frame, Object left, Object right,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached GetClassNode getClassNode,
                    @Exclusive @Cached("createLookup()") LookupSpecialBaseNode getattr) {
        return doCallObject(frame, inliningTarget, left, right, getClassNode, getattr);
    }

    private Object doCallObject(VirtualFrame frame, Node inliningTarget, Object left, Object right, GetClassNode getClassNode, LookupSpecialBaseNode getattr) {
        Object leftClass = getClassNode.execute(inliningTarget, left);
        Object leftCallable;
        try {
            leftCallable = getattr.execute(frame, leftClass, left);
        } catch (PException e) {
            if (ignoreDescriptorException) {
                leftCallable = PNone.NO_VALUE;
            } else {
                throw e;
            }
        }
        if (leftCallable == PNone.NO_VALUE) {
            if (handlerFactory != null) {
                return runErrorHandler(frame, left, right);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
        return ensureDispatch().executeObject(frame, leftCallable, left, right);
    }

    @NeverDefault
    protected final LookupSpecialBaseNode createLookup() {
        if (slot != null) {
            return LookupSpecialMethodSlotNode.create(slot);
        }
        return LookupSpecialMethodNode.create(name);
    }

    @Override
    public final TruffleString getName() {
        return name;
    }

}
