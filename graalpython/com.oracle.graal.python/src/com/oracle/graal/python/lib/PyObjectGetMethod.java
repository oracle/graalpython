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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptors;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.ForeignMethod;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
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
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectGetMethod extends Node {

    public abstract Object execute(Frame frame, Object receiver, TruffleString name);

    protected static boolean isObjectGetAttribute(Object lazyClass) {
        Object getattributeSlot = null;
        Object getattrSlot = null;
        if (lazyClass instanceof PythonBuiltinClassType) {
            PythonBuiltinClassType type = (PythonBuiltinClassType) lazyClass;
            getattributeSlot = SpecialMethodSlot.GetAttribute.getValue(type);
            getattrSlot = SpecialMethodSlot.GetAttr.getValue(type);
        } else if (lazyClass instanceof PythonManagedClass) {
            PythonManagedClass type = (PythonManagedClass) lazyClass;
            getattributeSlot = SpecialMethodSlot.GetAttribute.getValue(type);
            getattrSlot = SpecialMethodSlot.GetAttr.getValue(type);
        }
        return getattributeSlot == BuiltinMethodDescriptors.OBJ_GET_ATTRIBUTE && getattrSlot == PNone.NO_VALUE;
    }

    @Specialization(guards = {"isObjectGetAttribute(lazyClass)" /* Implies not foreign */, "name == cachedName"}, limit = "1")
    static Object getFixedAttr(VirtualFrame frame, Object receiver, @SuppressWarnings("unused") TruffleString name,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getClassNode") @Cached InlinedGetClassNode getClass,
                    @Bind("getClass.execute(inliningTarget, receiver)") Object lazyClass,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Cached("create(name)") LookupAttributeInMRONode lookupNode,
                    @Shared("getDescrClass") @Cached InlinedGetClassNode getDescrClass,
                    @Shared("lookupGet") @Cached(parameters = "Get") LookupCallableSlotInMRONode lookupGet,
                    @Shared("lookupSet") @Cached(parameters = "Set") LookupCallableSlotInMRONode lookupSet,
                    @Shared("callGet") @Cached CallTernaryMethodNode callGet,
                    @Shared("readAttr") @Cached ReadAttributeFromObjectNode readAttr,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                    @Cached InlinedBranchProfile hasDescr,
                    @Cached InlinedBranchProfile returnDataDescr,
                    @Cached InlinedBranchProfile returnAttr,
                    @Cached InlinedBranchProfile returnUnboundMethod,
                    @Cached InlinedBranchProfile returnBoundDescr) {
        boolean methodFound = false;
        Object descr = lookupNode.execute(lazyClass);
        Object getMethod = PNone.NO_VALUE;
        if (descr != PNone.NO_VALUE) {
            hasDescr.enter(inliningTarget);
            if (MaybeBindDescriptorNode.isMethodDescriptor(descr)) {
                methodFound = true;
            } else {
                // lookupGet acts as branch profile for this branch
                Object descrType = getDescrClass.execute(inliningTarget, descr);
                getMethod = lookupGet.execute(descrType);
                if (getMethod != PNone.NO_VALUE && lookupSet.execute(descrType) != PNone.NO_VALUE) {
                    returnDataDescr.enter(inliningTarget);
                    return new BoundDescriptor(callGet.execute(frame, getMethod, descr, receiver, lazyClass));
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
        if (getMethod != PNone.NO_VALUE) {
            // callGet is used twice, and cannot act as the profile here
            returnBoundDescr.enter(inliningTarget);
            return new BoundDescriptor(callGet.execute(frame, getMethod, descr, receiver, lazyClass));
        }
        if (descr != PNone.NO_VALUE) {
            return new BoundDescriptor(descr);
        }
        throw raiseNode.raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, receiver, name);
    }

    // No explicit branch profiling when we're looking up multiple things
    @Specialization(guards = "isObjectGetAttribute(lazyClass)" /* Implies not foreign */, replaces = "getFixedAttr")
    static Object getDynamicAttr(Frame frame, Object receiver, TruffleString name,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getClassNode") @Cached InlinedGetClassNode getClass,
                    @Bind("getClass.execute(inliningTarget, receiver)") Object lazyClass,
                    @Cached LookupAttributeInMRONode.Dynamic lookupNode,
                    @Shared("getDescrClass") @Cached InlinedGetClassNode getDescrClass,
                    @Shared("lookupGet") @Cached(parameters = "Get") LookupCallableSlotInMRONode lookupGet,
                    @Shared("lookupSet") @Cached(parameters = "Set") LookupCallableSlotInMRONode lookupSet,
                    @Shared("callGet") @Cached CallTernaryMethodNode callGet,
                    @Shared("readAttr") @Cached ReadAttributeFromObjectNode readAttr,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
        boolean methodFound = false;
        Object descr = lookupNode.execute(lazyClass, name);
        Object getMethod = PNone.NO_VALUE;
        if (descr != PNone.NO_VALUE) {
            if (MaybeBindDescriptorNode.isMethodDescriptor(descr)) {
                methodFound = true;
            } else {
                Object descrType = getDescrClass.execute(inliningTarget, descr);
                getMethod = lookupGet.execute(descrType);
                if (getMethod != PNone.NO_VALUE && lookupSet.execute(descrType) != PNone.NO_VALUE) {
                    return new BoundDescriptor(callGet.execute(frame, getMethod, descr, receiver, lazyClass));
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
        if (getMethod != PNone.NO_VALUE) {
            return new BoundDescriptor(callGet.execute(frame, getMethod, descr, receiver, lazyClass));
        }
        if (descr != PNone.NO_VALUE) {
            return new BoundDescriptor(descr);
        }
        throw raiseNode.raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, receiver, name);
    }

    @Specialization(guards = "isForeignObjectNode.execute(receiver)", limit = "1")
    Object getForeignMethod(VirtualFrame frame, Object receiver, TruffleString name,
                    @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached TruffleString.ToJavaStringNode toJavaString,
                    @CachedLibrary("receiver") InteropLibrary lib,
                    @Shared @Cached PyObjectGetAttr getAttr) {
        String jName = toJavaString.execute(name);
        if (lib.isMemberInvocable(receiver, jName)) {
            return new BoundDescriptor(new ForeignMethod(receiver, jName));
        } else {
            return new BoundDescriptor(getAttr.execute(frame, receiver, name));
        }
    }

    @Fallback
    static Object getGenericAttr(Frame frame, Object receiver, TruffleString name,
                    @Shared @Cached PyObjectGetAttr getAttr) {
        return new BoundDescriptor(getAttr.execute(frame, receiver, name));
    }
}
