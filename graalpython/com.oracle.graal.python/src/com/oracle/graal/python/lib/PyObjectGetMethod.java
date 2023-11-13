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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.ForeignMethod;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of _PyObject_GetMethod. Like CPython, the node uses {@link PyObjectGetAttr} for any
 * object that does not have the generic {@code object.__getattribute__}. For the generic {@code
 * object.__getattribute__} the node inlines the default logic but without binding methods, and
 * falls back to looking into the object dict. Returns something that can be handled by
 * {@link CallNode} or one of the {@code CallNAryMethodNode} nodes, like
 * {@link CallUnaryMethodNode}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectGetMethod extends Node {
    public final Object executeCached(Frame frame, Object receiver, TruffleString name) {
        return execute(frame, this, receiver, name);
    }

    public abstract Object execute(Frame frame, Node inliningTarget, Object receiver, TruffleString name);

    protected static boolean isObjectGetAttribute(Node inliningTarget, GetCachedTpSlotsNode getSlotsNode, Object lazyClass) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, lazyClass);
        return slots.tp_get_attro() == ObjectBuiltins.SLOTS.tp_get_attro();
    }

    // isObjectGetAttribute implies not foreign
    @Specialization(guards = {"isObjectGetAttribute(inliningTarget, getTypeSlotsNode, lazyClass)", "name == cachedName"}, limit = "1")
    static Object getFixedAttr(VirtualFrame frame, Node inliningTarget, Object receiver, @SuppressWarnings("unused") TruffleString name,
                    @SuppressWarnings("unused") /* Truffle bug: @Shared("getClassNode") */ @Exclusive @Cached GetClassNode getClass,
                    @SuppressWarnings("unused") @Exclusive @Cached GetCachedTpSlotsNode getTypeSlotsNode,
                    @Bind("getClass.execute(inliningTarget, receiver)") Object lazyClass,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Cached("create(name)") LookupAttributeInMRONode lookupNode,
                    @Exclusive @Cached GetObjectSlotsNode getSlotsNode,
                    @Exclusive @Cached CallSlotDescrGet callGetNode,
                    @Shared("readAttr") @Cached(inline = false) ReadAttributeFromObjectNode readAttr,
                    @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                    @Cached InlinedBranchProfile hasDescr,
                    @Cached InlinedBranchProfile returnDataDescr,
                    @Cached InlinedBranchProfile returnAttr,
                    @Cached InlinedBranchProfile returnUnboundMethod,
                    @Cached InlinedBranchProfile returnBoundDescr) {
        boolean methodFound = false;
        Object descr = lookupNode.execute(lazyClass);
        TpSlot getMethod = null;
        if (descr != PNone.NO_VALUE) {
            hasDescr.enter(inliningTarget);
            if (MaybeBindDescriptorNode.isMethodDescriptor(descr)) {
                methodFound = true;
            } else {
                // lookupGet acts as branch profile for this branch
                var descrSlots = getSlotsNode.execute(inliningTarget, descr);
                getMethod = descrSlots.tp_descr_get();
                if (getMethod != null && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    returnDataDescr.enter(inliningTarget);
                    return new BoundDescriptor(callGetNode.execute(frame, inliningTarget, getMethod, descr, receiver, lazyClass));
                }
            }
        }
        if (receiver instanceof PythonAbstractObject) {
            // readAttr acts as branch profile here
            Object attr = readAttr.execute(receiver, name);
            if (attr != PNone.NO_VALUE) {
                returnAttr.enter(inliningTarget);
                return new BoundDescriptor(attr);
            }
        }
        if (methodFound) {
            returnUnboundMethod.enter(inliningTarget);
            return descr;
        }
        if (getMethod != null) {
            // callGet is used twice, and cannot act as the profile here
            returnBoundDescr.enter(inliningTarget);
            return new BoundDescriptor(callGetNode.execute(frame, inliningTarget, getMethod, descr, receiver, lazyClass));
        }
        if (descr != PNone.NO_VALUE) {
            return new BoundDescriptor(descr);
        }
        throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, receiver, name);
    }

    // No explicit branch profiling when we're looking up multiple things
    // isObjectGetAttribute implies not foreign
    @Specialization(guards = "isObjectGetAttribute(inliningTarget, getTypeSlotsNode, lazyClass)", replaces = "getFixedAttr", limit = "1")
    @InliningCutoff
    static Object getDynamicAttr(Frame frame, Node inliningTarget, Object receiver, TruffleString name,
                    @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClass,
                    @SuppressWarnings("unused") @Exclusive @Cached GetCachedTpSlotsNode getTypeSlotsNode,
                    @Bind("getClass.execute(inliningTarget, receiver)") Object lazyClass,
                    @Cached(inline = false) LookupAttributeInMRONode.Dynamic lookupNode,
                    @Exclusive @Cached GetObjectSlotsNode getSlotsNode,
                    @Exclusive @Cached CallSlotDescrGet callGetNode,
                    @Shared("readAttr") @Cached(inline = false) ReadAttributeFromObjectNode readAttr,
                    /* Truffle bug: @Shared("raiseNode") */ @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
        boolean methodFound = false;
        Object descr = lookupNode.execute(lazyClass, name);
        TpSlot getMethod = null;
        if (descr != PNone.NO_VALUE) {
            if (MaybeBindDescriptorNode.isMethodDescriptor(descr)) {
                methodFound = true;
            } else {
                var descrSlots = getSlotsNode.execute(inliningTarget, descr);
                getMethod = descrSlots.tp_descr_get();
                if (getMethod != null && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    return new BoundDescriptor(callGetNode.execute((VirtualFrame) frame, inliningTarget, getMethod, descr, receiver, lazyClass));
                }
            }
        }
        if (receiver instanceof PythonAbstractObject) {
            Object attr = readAttr.execute(receiver, name);
            if (attr != PNone.NO_VALUE) {
                return new BoundDescriptor(attr);
            }
        }
        if (methodFound) {
            return descr;
        }
        if (getMethod != null) {
            return new BoundDescriptor(callGetNode.execute((VirtualFrame) frame, inliningTarget, getMethod, descr, receiver, lazyClass));
        }
        if (descr != PNone.NO_VALUE) {
            return new BoundDescriptor(descr);
        }
        throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, receiver, name);
    }

    @Specialization(guards = "isForeignObject(inliningTarget, isForeignObjectNode, receiver)", limit = "1")
    @InliningCutoff
    static Object getForeignMethod(VirtualFrame frame, Node inliningTarget, Object receiver, TruffleString name,
                    @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached(inline = false) TruffleString.ToJavaStringNode toJavaString,
                    @CachedLibrary("receiver") InteropLibrary lib,
                    @Exclusive @Cached PyObjectGetAttr getAttr) {
        String jName = toJavaString.execute(name);
        if (lib.isMemberInvocable(receiver, jName)) {
            return new BoundDescriptor(new ForeignMethod(receiver, jName));
        } else {
            return new BoundDescriptor(getAttr.execute(frame, inliningTarget, receiver, name));
        }
    }

    @InliningCutoff
    static boolean isForeignObject(Node inliningTarget, IsForeignObjectNode isForeignObjectNode, Object receiver) {
        return isForeignObjectNode.execute(inliningTarget, receiver);
    }

    @Fallback
    @InliningCutoff
    static Object getGenericAttr(Frame frame, Node inliningTarget, Object receiver, TruffleString name,
                    @Exclusive @Cached PyObjectGetAttr getAttr) {
        return new BoundDescriptor(getAttr.execute(frame, inliningTarget, receiver, name));
    }
}
