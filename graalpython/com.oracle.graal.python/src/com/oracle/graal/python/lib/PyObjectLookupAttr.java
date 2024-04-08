/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.CallSlotGetAttrNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent to use for the various PyObject_LookupAttr* functions available in CPython. Note that
 * these functions clear the exception if it's an attribute error. This node does the same, only
 * raising non AttributeError exceptions.
 *
 * Similar to the CPython equivalent, this node returns {@code PNone.NO_VALUE} when the attribute
 * doesn't exist.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic({SpecialMethodSlot.class, SpecialMethodNames.class, PGuards.class})
public abstract class PyObjectLookupAttr extends Node {
    public static Object executeUncached(Object receiver, TruffleString name) {
        return PyObjectLookupAttrNodeGen.getUncached().execute(null, null, receiver, name);
    }

    public final Object executeCached(Frame frame, Object receiver, TruffleString name) {
        return execute(frame, this, receiver, name);
    }

    public abstract Object execute(Frame frame, Node inliningTarget, Object receiver, TruffleString name);

    protected static boolean hasNoGetAttr(Object lazyClass) {
        // only used in asserts
        return LookupAttributeInMRONode.Dynamic.getUncached().execute(lazyClass, T___GETATTR__) == PNone.NO_VALUE;
    }

    protected static boolean getAttributeIs(Node inliningTarget, GetCachedTpSlotsNode getSlotsNode, Object lazyClass, TpSlot slot) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, lazyClass);
        return slots.tp_getattro() == slot;
    }

    protected static boolean isObjectGetAttribute(Node inliningTarget, GetCachedTpSlotsNode getSlotsNode, Object lazyClass) {
        return getAttributeIs(inliningTarget, getSlotsNode, lazyClass, ObjectBuiltins.SLOTS.tp_getattro());
    }

    protected static boolean isModuleGetAttribute(Node inliningTarget, GetCachedTpSlotsNode getSlotsNode, Object lazyClass) {
        return getAttributeIs(inliningTarget, getSlotsNode, lazyClass, ModuleBuiltins.SLOTS.tp_getattro());
    }

    protected static boolean isTypeGetAttribute(Node inliningTarget, GetCachedTpSlotsNode getSlotsNode, Object lazyClass) {
        return getAttributeIs(inliningTarget, getSlotsNode, lazyClass, TypeBuiltins.SLOTS.tp_getattro());
    }

    protected static boolean isBuiltinTypeType(Object type) {
        return type == PythonBuiltinClassType.PythonClass;
    }

    protected static boolean isTypeSlot(TruffleString name, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        return SpecialMethodSlot.canBeSpecial(name, codePointLengthNode, codePointAtIndexNode) || //
                        TpSlots.canBeSpecialMethod(name, codePointLengthNode, codePointAtIndexNode) || //
                        name.equalsUncached(T_MRO, TS_ENCODING);
    }

    // simple version that needs no calls and only reads from the object directly
    @SuppressWarnings("unused")
    @Specialization(guards = {"isObjectGetAttribute(inliningTarget, getSlotsNode, type)", "name == cachedName", "isNoValue(descr)"}, limit = "3")
    static Object doBuiltinObject(VirtualFrame frame, Node inliningTarget, Object object, TruffleString name,
                    @Cached("name") TruffleString cachedName,
                    /* GR-44836 @Shared */ @Exclusive @Cached GetClassNode getClass,
                    @Exclusive @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Bind("getClass.execute(inliningTarget, object)") Object type,
                    @Cached("create(name)") LookupAttributeInMRONode lookupName,
                    @Bind("lookupName.execute(type)") Object descr,
                    @Shared @Cached(inline = false) ReadAttributeFromObjectNode readNode) {
        // It should not have __getattr__, because otherwise it would not have builtin
        // object#tp_getattro, but slot wrapper dispatching to __getattribute__ or __getattr__
        assert hasNoGetAttr(type);
        return readNode.execute(object, cachedName);
    }

    // simple version that needs no calls and only reads from the object directly. the only
    // difference for module.__getattribute__ over object.__getattribute__ is that it looks for a
    // module-level __getattr__ as well
    @SuppressWarnings("unused")
    @Specialization(guards = {"isModuleGetAttribute(inliningTarget, getSlotsNode, type)", "name == cachedName", "isNoValue(descr)"}, limit = "1")
    static Object doBuiltinModule(VirtualFrame frame, Node inliningTarget, Object object, TruffleString name,
                    @Cached("name") TruffleString cachedName,
                    /* GR-44836 @Shared */ @Exclusive @Cached GetClassNode getClass,
                    @Exclusive @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Bind("getClass.execute(inliningTarget, object)") Object type,
                    @Cached("create(name)") LookupAttributeInMRONode lookupName,
                    @Bind("lookupName.execute(type)") Object descr,
                    @Shared @Cached(inline = false) ReadAttributeFromObjectNode readNode,
                    @Exclusive @Cached(inline = false) ReadAttributeFromObjectNode readGetattr,
                    /* GR-44836 @Shared */ @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                    @Exclusive @Cached InlinedConditionProfile noValueFound,
                    @Cached(inline = false) CallNode callGetattr) {
        assert hasNoGetAttr(type);
        Object value = readNode.execute(object, cachedName);
        if (noValueFound.profile(inliningTarget, value == PNone.NO_VALUE)) {
            Object getAttr = readGetattr.execute(object, SpecialMethodNames.T___GETATTR__);
            if (getAttr != PNone.NO_VALUE) {
                // (tfel): I'm not profiling this, since modules with __getattr__ are kind of rare
                try {
                    return callGetattr.execute(frame, getAttr, name);
                } catch (PException e) {
                    e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, errorProfile);
                    return PNone.NO_VALUE;
                }
            } else {
                return PNone.NO_VALUE;
            }
        } else {
            return value;
        }
    }

    // If the class of an object is "type", the object must be a class and as "type" is the base
    // metaclass, which defines only certain type slots, it can not have inherited other
    // attributes via metaclass inheritance. For all non-type-slot attributes it therefore
    // suffices to only check for inheritance via super classes.
    @SuppressWarnings("unused")
    @Specialization(guards = {"isTypeGetAttribute(inliningTarget, getTypeSlotsNode, type)", "isBuiltinTypeType(type)", "!isTypeSlot(name, codePointLengthNode, codePointAtIndexNode)"}, limit = "1")
    static Object doBuiltinTypeType(VirtualFrame frame, Node inliningTarget, Object object, TruffleString name,
                    /* GR-44836 @Shared */ @Exclusive @Cached GetClassNode getClass,
                    @Exclusive @Cached GetCachedTpSlotsNode getTypeSlotsNode,
                    @SuppressWarnings("unused") @Exclusive @Cached GetObjectSlotsNode getSlotsNode,
                    @Bind("getClass.execute(inliningTarget, object)") Object type,
                    @Cached(inline = false) LookupAttributeInMRONode.Dynamic readNode,
                    @Exclusive @Cached InlinedConditionProfile valueFound,
                    @Exclusive @Cached InlinedConditionProfile noGetMethod,
                    @Exclusive @Cached CallSlotDescrGet callGetSlot,
                    /* GR-44836 @Shared */ @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        Object value = readNode.execute(object, name);
        if (valueFound.profile(inliningTarget, value != PNone.NO_VALUE)) {
            var valueSlots = getSlotsNode.execute(inliningTarget, value);
            var valueGet = valueSlots.tp_descr_get();
            if (noGetMethod.profile(inliningTarget, valueGet == null)) {
                return value;
            } else {
                try {
                    return callGetSlot.execute(frame, inliningTarget, valueGet, value, PNone.NO_VALUE, object);
                } catch (PException e) {
                    e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, errorProfile);
                    return PNone.NO_VALUE;
                }
            }
        }
        return PNone.NO_VALUE;
    }

    // simple version that only reads attributes from (super) class inheritance and the object
    // itself. the only difference for type.__getattribute__ over object.__getattribute__
    // is that it looks for a __get__ method on the value and invokes it if it is callable.
    @SuppressWarnings("unused")
    @Specialization(guards = {"isTypeGetAttribute(inliningTarget, getTypeSlotsNode, type)", "name == cachedName", "isNoValue(metaClassDescr)"}, replaces = "doBuiltinTypeType", limit = "1")
    static Object doBuiltinType(VirtualFrame frame, Node inliningTarget, Object object, TruffleString name,
                    @Cached("name") TruffleString cachedName,
                    @Exclusive @Cached GetCachedTpSlotsNode getTypeSlotsNode,
                    /* GR-44836 @Shared */ @Exclusive @Cached GetClassNode getClass,
                    @Exclusive @Cached GetObjectSlotsNode getSlotsNode,
                    @Bind("getClass.execute(inliningTarget, object)") Object type,
                    @Cached(value = "create(name)", inline = false) LookupAttributeInMRONode lookupInMetaclassHierachy,
                    @Bind("lookupInMetaclassHierachy.execute(type)") Object metaClassDescr,
                    @Cached(value = "create(name)", inline = false) LookupAttributeInMRONode readNode,
                    @Exclusive @Cached InlinedConditionProfile valueFound,
                    @Exclusive @Cached InlinedConditionProfile noGetMethod,
                    @Exclusive @Cached CallSlotDescrGet callGetSlot,
                    /* GR-44836 @Shared */ @Exclusive @Cached IsBuiltinObjectProfile errorProfile) {
        assert hasNoGetAttr(type);
        Object value = readNode.execute(object);
        if (valueFound.profile(inliningTarget, value != PNone.NO_VALUE)) {
            var valueSlots = getSlotsNode.execute(inliningTarget, value);
            var valueGet = valueSlots.tp_descr_get();
            if (noGetMethod.profile(inliningTarget, valueGet == null)) {
                return value;
            } else {
                try {
                    return callGetSlot.execute(frame, inliningTarget, valueGet, value, PNone.NO_VALUE, object);
                } catch (PException e) {
                    e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, errorProfile);
                    return PNone.NO_VALUE;
                }
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(replaces = {"doBuiltinObject", "doBuiltinModule", "doBuiltinType"})
    static Object getDynamicAttr(Frame frame, Node inliningTarget, Object receiver, TruffleString name,
                    /* GR-44836 @Shared */ @Exclusive @Cached GetClassNode getClass,
                    @Exclusive @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Exclusive @Cached CallSlotGetAttrNode callGetattribute,
                    /* GR-44836 @Shared */ @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                    @Shared @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                    @Shared @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        Object type = getClass.execute(inliningTarget, receiver);
        TpSlots slots = getSlotsNode.execute(inliningTarget, type);
        if (!codePointLengthNode.isAdoptable()) {
            // It pays to try this in the uncached case, avoiding a full call to tp_getattr(o)
            Object result = readAttributeQuickly(type, slots, receiver, name, codePointLengthNode, codePointAtIndexNode);
            if (result != null) {
                return result;
            }
            // Otherwise fallback to tp_getattr(o)
        }
        try {
            return callGetattribute.execute((VirtualFrame) frame, inliningTarget, slots, receiver, name);
        } catch (PException e) {
            e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, errorProfile);
        }
        return PNone.NO_VALUE;
    }

    @NeverDefault
    public static PyObjectLookupAttr create() {
        return PyObjectLookupAttrNodeGen.create();
    }

    public static PyObjectLookupAttr getUncached() {
        return PyObjectLookupAttrNodeGen.getUncached();
    }

    /**
     * We try to improve the performance of this in the interpreter and uncached case for a simple
     * class of cases. The reason is that in the uncached case, we would do a full call to the
     * __getattribute__ method and that raises an exception, which is expensive and may not be
     * needed. This actually always helps in interpreted mode even in the cached case, but we cannot
     * really use it then, because when we only use it in the interpreter, the compiled code would
     * skip this and immediately deopt, if the code after was never run and initialized. And anyway,
     * the hope is that in the cached case, we just stay in the above specializations
     * {@link #doBuiltinObject}, {@link #doBuiltinModule}, or {@link #doBuiltinType} and get the
     * fast path through them.
     *
     * This inlines parts of the logic of the {@code ObjectBuiltins.GetAttributeNode} and {@code
     * ModuleBuiltins.GetAttributeNode}. This method returns {@code PNone.NO_VALUE} when the
     * attribute is not found and the original would've raised an AttributeError. It returns {@code
     * null} when no shortcut was applicable. If {@code PNone.NO_VALUE} was returned, name is
     * guaranteed to be a {@code java.lang.TruffleString}.
     */
    static Object readAttributeQuickly(Object type, TpSlots slots, Object receiver, TruffleString stringName, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        if (slots.tp_getattro() == ObjectBuiltins.SLOTS.tp_getattro() && type instanceof PythonManagedClass) {
            PythonAbstractClass[] bases = ((PythonManagedClass) type).getBaseClasses();
            if (bases.length == 1) {
                PythonAbstractClass base = bases[0];
                if (base instanceof PythonBuiltinClass &&
                                ((PythonBuiltinClass) base).getType() == PythonBuiltinClassType.PythonObject) {
                    if (!(codePointAtIndexNode.execute(stringName, 0, TS_ENCODING) == '_' && codePointAtIndexNode.execute(stringName, 1, TS_ENCODING) == '_')) {
                        // not a special name, so this attribute cannot be inherited, and can
                        // only be on the type or the object. If it's on the type, return to
                        // the generic code.
                        ReadAttributeFromObjectNode readUncached = ReadAttributeFromObjectNode.getUncached();
                        Object descr = readUncached.execute(type, stringName);
                        if (descr == PNone.NO_VALUE) {
                            return readUncached.execute(receiver, stringName);
                        }
                    }
                }
            }
        } else if (slots.tp_getattro() == ModuleBuiltins.SLOTS.tp_getattro() && type == PythonBuiltinClassType.PythonModule) {
            // this is slightly simpler than the previous case, since we don't need to check
            // the type. There may be a module-level __getattr__, however. Since that would be
            // a call anyway, we return to the generic code in that case
            if (!SpecialMethodSlot.canBeSpecial(stringName, codePointLengthNode, codePointAtIndexNode)) {
                // not a special name, so this attribute cannot be on the module class
                ReadAttributeFromObjectNode readUncached = ReadAttributeFromObjectNode.getUncached();
                Object result = readUncached.execute(receiver, stringName);
                if (result != PNone.NO_VALUE) {
                    return result;
                }
            }
        }
        return null;
    }
}
