/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Equivalent to use for the various PyObject_LookupAttr* functions available in CPython. Note that
 * these functions clear the exception if it's an attribute error. This node does the same, only
 * raising non AttributeError exceptions.
 *
 * Similar to the CPython equivalent, this node returns {@code PNone.NO_VALUE} when the attribute
 * doesn't exist.
 */
@GenerateUncached
@ImportStatic({SpecialMethodSlot.class, SpecialMethodNames.class, PGuards.class})

public abstract class PyObjectLookupAttr extends Node {
    private static final BuiltinMethodDescriptor OBJ_GET_ATTRIBUTE = BuiltinMethodDescriptor.get(ObjectBuiltinsFactory.GetAttributeNodeFactory.getInstance(), PythonBuiltinClassType.PythonObject);
    private static final BuiltinMethodDescriptor MODULE_GET_ATTRIBUTE = BuiltinMethodDescriptor.get(ModuleBuiltinsFactory.ModuleGetattritbuteNodeFactory.getInstance(),
                    PythonBuiltinClassType.PythonModule);
    private static final BuiltinMethodDescriptor TYPE_GET_ATTRIBUTE = BuiltinMethodDescriptor.get(TypeBuiltinsFactory.GetattributeNodeFactory.getInstance(), PythonBuiltinClassType.PythonClass);

    public abstract Object execute(Frame frame, Object receiver, Object name);

    protected static boolean hasNoGetattr(Object lazyClass) {
        Object slotValue = null;
        if (lazyClass instanceof PythonBuiltinClassType) {
            slotValue = SpecialMethodSlot.GetAttr.getValue((PythonBuiltinClassType) lazyClass);
        } else if (lazyClass instanceof PythonManagedClass) {
            slotValue = SpecialMethodSlot.GetAttr.getValue((PythonManagedClass) lazyClass);
        }
        return slotValue == PNone.NO_VALUE;
    }

    protected static boolean getAttributeIs(Object lazyClass, BuiltinMethodDescriptor expected) {
        Object slotValue = null;
        if (lazyClass instanceof PythonBuiltinClassType) {
            slotValue = SpecialMethodSlot.GetAttribute.getValue((PythonBuiltinClassType) lazyClass);
        } else if (lazyClass instanceof PythonManagedClass) {
            slotValue = SpecialMethodSlot.GetAttribute.getValue((PythonManagedClass) lazyClass);
        }
        return slotValue == expected;
    }

    protected static boolean isObjectGetAttribute(Object lazyClass) {
        return getAttributeIs(lazyClass, OBJ_GET_ATTRIBUTE);
    }

    protected static boolean isModuleGetAttribute(Object lazyClass) {
        return getAttributeIs(lazyClass, MODULE_GET_ATTRIBUTE);
    }

    protected static boolean isTypeGetAttribute(Object lazyClass) {
        return getAttributeIs(lazyClass, TYPE_GET_ATTRIBUTE);
    }

    // simple version that needs no calls and only reads from the object directly
    @SuppressWarnings("unused")
    @Specialization(guards = {"isObjectGetAttribute(type)", "hasNoGetattr(type)", "name == cachedName", "isNoValue(descr)"})
    static final Object doBuiltinObject(VirtualFrame frame, Object object, String name,
                    @Cached("name") String cachedName,
                    @Cached GetClassNode getClass,
                    @Bind("getClass.execute(object)") Object type,
                    @Cached("create(name)") LookupAttributeInMRONode lookupName,
                    @Bind("lookupName.execute(type)") Object descr,
                    @Cached ReadAttributeFromObjectNode readNode) {
        return readNode.execute(object, cachedName);
    }

    // simple version that needs no calls and only reads from the object directly. the only
    // difference for module.__getattribute__ over object.__getattribute__ is that it looks for a
    // module-level __getattr__ as well
    @SuppressWarnings("unused")
    @Specialization(guards = {"isModuleGetAttribute(type)", "hasNoGetattr(type)", "name == cachedName", "isNoValue(descr)"}, limit = "1")
    static final Object doBuiltinModule(VirtualFrame frame, Object object, String name,
                    @Cached("name") String cachedName,
                    @Cached GetClassNode getClass,
                    @Bind("getClass.execute(object)") Object type,
                    @Cached("create(name)") LookupAttributeInMRONode lookupName,
                    @Bind("lookupName.execute(type)") Object descr,
                    @Cached ReadAttributeFromObjectNode readNode,
                    @Cached ReadAttributeFromObjectNode readGetattr,
                    @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile,
                    @Cached ConditionProfile noValueFound,
                    @Cached CallNode callGetattr) {
        Object value = readNode.execute(object, cachedName);
        if (noValueFound.profile(value == PNone.NO_VALUE)) {
            Object getAttr = readGetattr.execute(object, SpecialMethodNames.__GETATTR__);
            if (getAttr != PNone.NO_VALUE) {
                // (tfel): I'm not profiling this, since modules with __getattr__ are kind of rare
                try {
                    return callGetattr.execute(frame, getAttr, name);
                } catch (PException e) {
                    e.expect(PythonBuiltinClassType.AttributeError, errorProfile);
                    return PNone.NO_VALUE;
                }
            } else {
                return PNone.NO_VALUE;
            }
        } else {
            return value;
        }
    }

    // simple version that needs no calls and only reads from the object directly. the only
    // difference for type.__getattribute__ over object.__getattribute__ is that it looks for a
    // __get__ method on the value and invokes it if it is callable.
    @SuppressWarnings("unused")
    @Specialization(guards = {"isTypeGetAttribute(type)", "hasNoGetattr(type)", "name == cachedName", "isNoValue(descr)"}, limit = "1")
    static final Object doBuiltinType(VirtualFrame frame, Object object, String name,
                    @Cached("name") String cachedName,
                    @Cached GetClassNode getClass,
                    @Bind("getClass.execute(object)") Object type,
                    @Cached("create(name)") LookupAttributeInMRONode lookupName,
                    @Bind("lookupName.execute(type)") Object descr,
                    @Cached ReadAttributeFromObjectNode readNode,
                    @Cached ConditionProfile valueFound,
                    @Cached("create(__GET__)") LookupInheritedAttributeNode lookupValueGet,
                    @Cached ConditionProfile noGetMethod,
                    @Cached CallTernaryMethodNode invokeValueGet,
                    @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile) {
        Object value = readNode.execute(object, cachedName);
        if (valueFound.profile(value != PNone.NO_VALUE)) {
            Object valueGet = lookupValueGet.execute(value);
            if (noGetMethod.profile(valueGet == PNone.NO_VALUE)) {
                return value;
            } else if (PGuards.isCallable(valueGet)) {
                try {
                    return invokeValueGet.execute(frame, valueGet, value, PNone.NONE, object);
                } catch (PException e) {
                    e.expect(PythonBuiltinClassType.AttributeError, errorProfile);
                    return PNone.NO_VALUE;
                }
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(replaces = {"doBuiltinObject", "doBuiltinModule", "doBuiltinType"})
    static Object getDynamicAttr(Frame frame, Object receiver, Object name,
                    @Cached GetClassNode getClass,
                    @Cached(parameters = "GetAttribute") LookupSpecialMethodSlotNode lookupGetattribute,
                    @Cached(parameters = "GetAttr") LookupSpecialMethodSlotNode lookupGetattr,
                    @Cached CallBinaryMethodNode callGetattribute,
                    @Cached CallBinaryMethodNode callGetattr,
                    @Shared("errorProfile") @Cached IsBuiltinClassProfile errorProfile) {
        Object type = getClass.execute(receiver);
        Object getattribute = lookupGetattribute.execute(frame, type, receiver);
        try {
            return callGetattribute.executeObject(frame, getattribute, receiver, name);
        } catch (PException e) {
            e.expect(PythonBuiltinClassType.AttributeError, errorProfile);
        }
        Object getattr = lookupGetattr.execute(frame, type, receiver);
        if (getattr != PNone.NO_VALUE) {
            try {
                return callGetattr.executeObject(frame, getattr, receiver, name);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.AttributeError, errorProfile);
            }
        }
        return PNone.NO_VALUE;
    }
}
