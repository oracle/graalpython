/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.attributes;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * Lookup a suitable slot function. When the slot is found to be inherited from a native
 * superclass, this returns the same function pointer, so behaves just like CPython would. For
 * inheriting managed slots we differ, however. CPython installs some slot functions in
 * PyHeapTypeObjects that do a lookup of the method in the type's dict and call it. We avoid this
 * and instead return a function pointer that directly calls the correct method (see {@link
 * #wrapManagedMethod}). However, there are two caveats:
 *
 *  1) In CPython, were a PyHeapTypeObject supertype to change a magic method in its dict after
 *  a subclass inherited from it, the slot lookup will (correctly) go to the new method. In our
 *  case it will not, since we have created a closure over the method at the time of
 *  inheritance. So far this has not been an issue, so we ignore it.
 *
 *  2) Some slots that map to the same Python method cannot be treated in this way. Consider what
 *  happens if we inherit from a managed type with a __len__ method and a native type with
 *  tp_as_mapping->mp_length. We would copy the mp_length slot from the native type, and create a
 *  closure to call the managed __len__ method for tp_as_sequence->sq_length. This is not what
 *  CPython does. In CPython, the sq_length slot would be set to a C function that does a dynamic
 *  lookup and call of __len__! So it depends on the MRO order if we end up in the managed __len__
 *  or in fact bounce back into native to invoke the mp_length function when calling
 *  tp_as_sequence->sq_length on the subtype! As for 1), we ignore the potential of later __dict__
 *  updates along the MRO chain and return the pointer to the native slot, if that should take
 *  precedence.
 */
public abstract class LookupNativeSlotNode extends PNodeWithContext {
    private LookupNativeSlotNode() {
    }

    @TruffleBoundary
    public static Object executeUncached(PythonManagedClass klass, SlotMethodDef slot) {
        var mro = GetMroStorageNode.getUncached().execute(null, klass);
        var readAttrNode = ReadAttributeFromObjectNode.getUncachedForceType();
        var readPointerNode = CStructAccess.ReadPointerNode.getUncached();
        var interopLibrary = InteropLibrary.getUncached();
        var overlappingSlot = slot.overlappingSlot;
        Object foundNativeSlotOverlap = null;
        for (int i = 0; i < mro.length(); i++) {
            PythonAbstractClass kls = mro.getItemNormalized(i);
            Object value = readSlot(slot, kls, readAttrNode, readPointerNode, interopLibrary);
            if (value != null) {
                if (foundNativeSlotOverlap != null && kls instanceof PythonManagedClass) {
                    // we found managed method with the same name as a previous slot. the slot
                    // should shadow the method
                    return foundNativeSlotOverlap;
                }
                return value;
            }
            if (overlappingSlot != null && foundNativeSlotOverlap == null && kls instanceof PythonAbstractNativeObject nativeObject) {
                foundNativeSlotOverlap = readSlot(overlappingSlot, kls, readAttrNode, readPointerNode, interopLibrary);
            }
        }
        if (foundNativeSlotOverlap != null) {
            return foundNativeSlotOverlap;
        }
        return getNULL();
    }

    private static Object getNULL() {
        return PythonContext.get(null).getNativeNull().getPtr();
    }

    private static Object readSlot(SlotMethodDef slot, PythonAbstractClass currentType, ReadAttributeFromObjectNode readNode, CStructAccess.ReadPointerNode readPointerNode,
                    InteropLibrary interopLibrary) {
        if (currentType instanceof PythonAbstractNativeObject nativeObject) {
            Object value = readPointerNode.readFromObj(nativeObject, slot.typeField);
            if (!PGuards.isNullOrZero(value, interopLibrary)) {
                if (slot.methodsField == null) {
                    return value;
                } else {
                    value = readPointerNode.read(value, slot.methodsField);
                    if (!PGuards.isNullOrZero(value, interopLibrary)) {
                        return value;
                    }
                }
            }
        } else {
            assert currentType instanceof PythonManagedClass;
            if (slot.methodFlag != 0 && currentType instanceof PythonBuiltinClass builtinClass) {
                if ((builtinClass.getType().getMethodsFlags() & slot.methodFlag) == 0) {
                    return null;
                }
            }
            Object value = readNode.execute(currentType, slot.methodName);
            if (value != PNone.NO_VALUE) {
                return wrapManagedMethod(slot, (PythonManagedClass) currentType, value);
            }
        }
        return null;
    }

    private static Object wrapManagedMethod(SlotMethodDef slot, PythonManagedClass owner, Object value) {
        if (value instanceof PNone) {
            return getNULL();
        }
        return PythonContext.get(null).getCApiContext().getOrCreateProcWrapper(owner, slot, () -> slot.wrapperFactory.apply(value));
    }

    @Override
    public boolean isAdoptable() {
        return false;
    }

    public static Object executeUncachedGetattroSlot(PythonManagedClass type) {
        var lookupGetattr = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.GetAttr);
        Object getattr = lookupGetattr.execute(type);
        if (getattr == PNone.NO_VALUE) {
            return LookupNativeSlotNode.executeUncached(type, SlotMethodDef.TP_GETATTRO);
        } else {
            var lookupGetattribute = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.GetAttribute);
            Object getattribute = lookupGetattribute.execute(type);
            return PythonContext.get(null).getCApiContext().getOrCreateProcWrapper(type, SlotMethodDef.TP_GETATTRO, () -> new PyProcsWrapper.GetAttrCombinedWrapper(getattribute, getattr));
        }
    }
}
