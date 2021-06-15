/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MISSING__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET_NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

/**
 * Subset of special methods that is cached in {@link PythonManagedClass} and
 * {@link PythonBuiltinClassType}.
 *
 * For {@link PythonManagedClass}, we cache the result of special method lookup in a context
 * specific form: exactly the context specific object that regular MRO lookup would give. For
 * {@link PythonBuiltinClassType}, we cache only primitive and other context independent values and
 * additionally instances of {@link BuiltinMethodDescriptor}, which wrap context independent
 * information about the method that would be the result of the lookup. This information is further
 * split to language independent (per VM) part, which is the node factory, and per language part,
 * which is the call target. Call targets are cached in an array in the {@link PythonLanguage}
 * instance, and {@link BuiltinMethodDescriptor} holds only index into that array.
 *
 * The state of the special methods cache in {@link PythonManagedClass} should mostly reflect what
 * would be "cached" in the corresponding special slots in CPython. CPython updates the slots in
 * {@code type.__setattr__}, we do the same and additionally also in
 * {@link com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode}, which is used
 * directly from some places bypassing {@code type.__setattr__}.
 *
 * The cache in {@link PythonBuiltinClassType} may contain {@code null} entries, which indicate that
 * given slot cannot be cached in a context independent way. In such case, one needs to resolve to
 * {@link PythonBuiltinClass} and lookup the slot there.
 *
 * The cache makes an assumption that builtin types do not change after GraalPython is fully
 * initialized.
 */
public enum SpecialMethodSlot {
    GetAttribute(__GETATTRIBUTE__),
    GetAttr(__GETATTR__),
    SetAttr(__SETATTR__),
    DelAttr(__DELATTR__),

    Class(__CLASS__),
    Dict(__DICT__),

    Get(__GET__),
    Set(__SET__),
    Delete(__DELETE__),

    Iter(__ITER__),
    Next(__NEXT__),

    New(__NEW__),
    Init(__INIT__),
    Prepare(__PREPARE__),
    SetName(__SET_NAME__),
    InstanceCheck(__INSTANCECHECK__),
    Subclasscheck(__SUBCLASSCHECK__),
    Call(__CALL__),

    GetItem(__GETITEM__),
    SetItem(__SETITEM__),
    DelItem(__DELITEM__),

    Exit(__EXIT__),
    Enter(__ENTER__),

    Len(__LEN__),
    Contains(__CONTAINS__),
    Bool(__BOOL__),
    Hash(__HASH__),
    Index(__INDEX__),
    Float(__FLOAT__),
    Int(__INT__),
    Str(__STR__),
    Repr(__REPR__),
    // Note: __format__ does not seem to be actual slot in CPython, but it is looked up frequently
    Format(__FORMAT__),
    Missing(__MISSING__),

    Eq(__EQ__),
    Ne(__NE__),
    Lt(__LT__),
    Le(__LE__),
    Gt(__GT__),
    Ge(__GE__),

    And(__AND__),
    RAnd(__RAND__),
    Add(__ADD__),

    Reversed(__REVERSED__),
    Bytes(__BYTES__);

    public static final SpecialMethodSlot[] VALUES = values();
    private final String name;

    SpecialMethodSlot(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Object getValue(PythonManagedClass klass) {
        assert klass.specialMethodSlots != null;
        return klass.specialMethodSlots[ordinal()];
    }

    public Object getValue(PythonBuiltinClassType klassType) {
        // should not be called during initialization
        return klassType.getSpecialMethodSlots()[ordinal()];
    }

    private void setValue(PythonManagedClass klass, Object value) {
        // For builtin classes, we should see these updates only during initialization
        assert !PythonLanguage.getContext().isInitialized() || !(klass instanceof PythonBuiltinClass) ||
                        ((PythonBuiltinClass) klass).getType().getSpecialMethodSlots() == null : String.format("%s.%s = %s", klass, getName(), value);
        klass.specialMethodSlots[ordinal()] = asSlotValue(value);
        if (klass instanceof PythonClass) {
            ((PythonClass) klass).invalidateSlotsFinalAssumption();
        }
    }

    // --------------------------------------------------
    // Initialization of the builtin types/classes:

    /**
     * Initialized builtin classes and types right after they were initialized and populated with
     * builtin methods, i.e., before calling the Python initialization part.
     */
    public static void initializeBuiltinsSpecialMethodSlots(Python3Core core) {
        // Initialize the builtin classes (once per context)
        for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
            initializeBuiltinClassSpecialMethodSlots(core, core.lookupType(type));
        }
        // Initialize the builtin types (once per VM)
        initializeBuiltinTypeSlots(core);
    }

    private static void initializeBuiltinClassSpecialMethodSlots(Python3Core core, PythonBuiltinClass klass) {
        CompilerAsserts.neverPartOfCompilation();
        if (klass.specialMethodSlots != null) {
            // Already initialized
            return;
        }

        // First initialize the base class' slots
        PythonBuiltinClassType klassType = klass.getType();
        if (klassType.getBase() != null) {
            PythonBuiltinClass base = core.lookupType(klassType.getBase());
            initializeBuiltinClassSpecialMethodSlots(core, base);
            Object[] baseSlots = base.specialMethodSlots;
            klass.specialMethodSlots = Arrays.copyOf(baseSlots, baseSlots.length);
        } else {
            Object[] slots = new Object[VALUES.length];
            Arrays.fill(slots, PNone.NO_VALUE);
            klass.specialMethodSlots = slots;
        }

        ReadAttributeFromObjectNode readNode = ReadAttributeFromObjectNode.getUncachedForceType();
        for (SpecialMethodSlot slot : VALUES) {
            Object value = readNode.execute(klass, slot.getName());
            if (value != PNone.NO_VALUE) {
                slot.setValue(klass, value);
            }
        }
    }

    private static final Object builtinSlotsInitializationLock = new Object();
    private static volatile boolean builtinSlotsInitialized;

    /**
     * Initialized builtin type according to its respective builtin class. Only context independent
     * values are pushed from the class to the type, because types are shared across contexts. This
     * initialization should run only once per VM and this method takes care of that.
     */
    private static void initializeBuiltinTypeSlots(Python3Core core) {
        synchronized (builtinSlotsInitializationLock) {
            if (builtinSlotsInitialized) {
                return;
            }
            initializeBuiltinTypeSlotsImpl(core);
            builtinSlotsInitialized = true;
        }
    }

    private static void initializeBuiltinTypeSlotsImpl(Python3Core core) {
        for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
            Object[] typeSlots = new Object[VALUES.length];
            PythonBuiltinClass klass = core.lookupType(type);
            for (SpecialMethodSlot slot : VALUES) {
                if (type.redefinesSlot(slot)) {
                    continue;
                }
                Object value = slot.getValue(klass);
                if (value instanceof PBuiltinFunction) {
                    BuiltinMethodDescriptor info = BuiltinMethodDescriptor.get((PBuiltinFunction) value);
                    if (info != null) {
                        typeSlots[slot.ordinal()] = info;
                    }
                } else if (value instanceof BuiltinMethodDescriptor || PythonLanguage.canCache(value)) {
                    typeSlots[slot.ordinal()] = value;
                }
            }
            type.setSpecialMethodSlots(typeSlots);
        }
    }

    // --------------------------------------------------
    // Initialization and updates of the user classes:

    @TruffleBoundary
    public static void reinitializeSpecialMethodSlots(PythonManagedClass klass) {
        reinitializeSpecialMethodSlots((Object) klass);
    }

    @TruffleBoundary
    public static void reinitializeSpecialMethodSlots(PythonNativeClass klass) {
        reinitializeSpecialMethodSlots((Object) klass);
    }

    public static void reinitializeSpecialMethodSlots(Object klass) {
        java.util.Set<PythonAbstractClass> subClasses;
        if (klass instanceof PythonManagedClass) {
            PythonManagedClass managedClass = (PythonManagedClass) klass;
            // specialMethodSlots can be null if the type is just being initialized, for example,
            // when the initialization calls the "mro" method, which may execute arbitrary code
            // including setting its __bases__ to something.
            // TODO: LookupAttributeInMRONode and other places rely on specialMethodSlots being
            // always initialized, can it happen that some code invoked during type initialization
            // is going to lookup something in that type's MRO?
            if (managedClass.specialMethodSlots != null) {
                managedClass.specialMethodSlots = null;
                initializeSpecialMethodSlots(managedClass, GetMroStorageNode.getUncached());
            }
            subClasses = managedClass.getSubClasses();
        } else if (klass instanceof PythonNativeClass) {
            subClasses = GetSubclassesNode.getUncached().execute(klass);
        } else {
            throw new AssertionError(Objects.toString(klass));
        }
        for (PythonAbstractClass subClass : subClasses) {
            reinitializeSpecialMethodSlots(subClass);
        }
    }

    public static void initializeSpecialMethodSlots(PythonManagedClass klass, GetMroStorageNode getMroStorageNode) {
        MroSequenceStorage mro = getMroStorageNode.execute(klass);
        klass.specialMethodSlots = initializeSpecialMethodsSlots(klass, mro);
    }

    @TruffleBoundary
    private static Object[] initializeSpecialMethodsSlots(PythonManagedClass klass, MroSequenceStorage mro) {
        // Note: the classes in MRO may not have their special slots initialized, which is
        // pathological case that can happen if MRO is fiddled with during MRO computation

        // Fast-path: If MRO(klass) == (A, B, C, ...) and A == klass and MRO(B) == (B, C, ...), then
        // we can just "extend" the slots of B with the new overrides in A. This fast-path seem to
        // handle large majority of the situations
        if (mro.length() >= 2 && klass.getBaseClasses().length <= 1) {
            PythonAbstractClass firstType = mro.getItemNormalized(0);
            PythonAbstractClass secondType = mro.getItemNormalized(1);
            if (firstType == klass && PythonManagedClass.isInstance(secondType)) {
                PythonManagedClass managedBase = PythonManagedClass.cast(secondType);
                if (managedBase.specialMethodSlots != null) {
                    if (isMroSubtype(mro, managedBase)) {
                        Object[] result = PythonUtils.arrayCopyOf(managedBase.specialMethodSlots, managedBase.specialMethodSlots.length);
                        setSlotsFromManaged(result, klass);
                        return result;
                    }
                }
            }
        }

        // Deal with this pathological case
        if (mro.length() == 0) {
            Object[] slots = new Object[VALUES.length];
            Arrays.fill(slots, PNone.NO_VALUE);
            return slots;
        }

        // Check the last klass in MRO and use copy its slots for the beginning (if available)
        // In most cases this will be `object`, which contains most of the slots
        Object[] slots = null;
        PythonAbstractClass lastType = mro.getItemNormalized(mro.length() - 1);
        boolean slotsInitializedFromLast = false;
        if (PythonManagedClass.isInstance(lastType)) {
            PythonManagedClass lastClass = PythonManagedClass.cast(lastType);
            if (lastClass.specialMethodSlots != null) {
                slots = PythonUtils.arrayCopyOf(lastClass.specialMethodSlots, lastClass.specialMethodSlots.length);
                slotsInitializedFromLast = true;
            }
        }
        if (!slotsInitializedFromLast) {
            slots = new Object[VALUES.length];
            Arrays.fill(slots, PNone.NO_VALUE);
        }

        // Traverse MRO in reverse order overriding the initial slots values if we find new override
        int skip = slotsInitializedFromLast ? 1 : 0;
        for (int i = mro.length() - skip - 1; i >= 0; i--) {
            PythonAbstractClass base = mro.getItemNormalized(i);
            if (PythonManagedClass.isInstance(base)) {
                setSlotsFromManaged(slots, PythonManagedClass.cast(base));
            } else {
                setSlotsFromGeneric(slots, base);
            }
        }
        return slots;
    }

    private static boolean isMroSubtype(MroSequenceStorage superTypeMro, PythonManagedClass subType) {
        if (subType instanceof PythonBuiltinClass && ((PythonBuiltinClass) subType).getType() == PythonBuiltinClassType.PythonObject) {
            // object is subclass of everything
            return true;
        }
        MroSequenceStorage subTypeMro = GetMroStorageNode.getUncached().execute(subType);
        boolean isMroSubtype = subTypeMro.length() == superTypeMro.length() - 1;
        if (isMroSubtype) {
            for (int i = 0; i < subTypeMro.length(); i++) {
                if (superTypeMro.getItemNormalized(i + 1) != subTypeMro.getItemNormalized(i)) {
                    isMroSubtype = false;
                    break;
                }
            }
        }
        return isMroSubtype;
    }

    private static void setSlotsFromManaged(Object[] slots, PythonManagedClass source) {
        PDict dict = PythonObjectLibrary.getUncached().getDict(source);
        if (dict == null) {
            DynamicObject storage = source.getStorage();
            DynamicObjectLibrary domLib = DynamicObjectLibrary.getFactory().getUncached(storage);
            for (SpecialMethodSlot slot : VALUES) {
                final Object value = domLib.getOrDefault(source, slot.getName(), PNone.NO_VALUE);
                if (value != PNone.NO_VALUE) {
                    slots[slot.ordinal()] = asSlotValue(value);
                }
            }
        } else {
            HashingStorage storage = dict.getDictStorage();
            HashingStorageLibrary hlib = HashingStorageLibrary.getFactory().getUncached(storage);
            for (SpecialMethodSlot slot : VALUES) {
                final Object value = hlib.getItem(storage, slot.getName());
                if (value != null) {
                    slots[slot.ordinal()] = asSlotValue(value);
                }
            }
        }
    }

    private static void setSlotsFromGeneric(Object[] slots, PythonAbstractClass base) {
        ReadAttributeFromObjectNode readAttNode = ReadAttributeFromObjectNode.getUncachedForceType();
        for (SpecialMethodSlot slot : VALUES) {
            Object value = readAttNode.execute(base, slot.getName());
            if (value != PNone.NO_VALUE) {
                slots[slot.ordinal()] = asSlotValue(value);
            }
        }
    }

    @TruffleBoundary
    public static void fixupSpecialMethodSlot(PythonNativeClass klass, SpecialMethodSlot slot, Object value) {
        Object newValue = value;
        if (value == PNone.NO_VALUE) {
            // We are removing the value: find the new value for the class that is being updated and
            // proceed with that
            newValue = LookupAttributeInMRONode.lookupSlowPath(klass, slot.getName());
        }
        fixupSpecialMethodInSubClasses(GetSubclassesNode.getUncached().execute(klass), slot, null, newValue);
    }

    @TruffleBoundary
    public static void fixupSpecialMethodSlot(PythonManagedClass klass, SpecialMethodSlot slot, Object value) {
        if (klass.specialMethodSlots == null) {
            // This can happen during type initialization, we'll initialize the slots when the
            // whole initialization is done. We do the assert only if we maintain the stack of types
            // currently being initialized
            assert initializingTypes == null || initializingTypes.contains(klass);
            return;
        }

        Object original = slot.getValue(klass);
        if (value == original) {
            return;
        }

        Object newValue = value;
        if (value == PNone.NO_VALUE) {
            // We are removing the value: find the new value for the class that is being updated and
            // proceed with that
            newValue = LookupAttributeInMRONode.lookupSlowPath(klass, slot.getName());
        }

        slot.setValue(klass, newValue);
        fixupSpecialMethodInSubClasses(klass.getSubClasses(), slot, original, value);
    }

    // Note: originalValue == null means originalValue is not available
    private static void fixupSpecialMethodSlot(PythonManagedClass klass, SpecialMethodSlot slot, Object originalValue, Object newValue) {
        Object currentOldValue = slot.getValue(klass);
        if (originalValue != null && currentOldValue != originalValue && originalValue != PNone.NO_VALUE) {
            // If this slot is set to something that has been inherited from somewhere else, it will
            // not change. The only exception is if we introduced a new method entry into the MRO
            // (i.e., the original slot was empty).
            return;
        }
        // Even if this slot was occupied by the same value as in the base, it does not mean that
        // the value was here because it was inherited from the base class where we now overridden
        // that slot. To stay on the safe side, we consult the MRO here.
        MroSequenceStorage mro = GetMroStorageNode.getUncached().execute(klass);
        Object currentNewValue = PNone.NO_VALUE;
        for (int i = 0; i < mro.length(); i++) {
            Object kls = mro.getItemNormalized(i);
            Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(kls, slot.getName());
            if (value != PNone.NO_VALUE) {
                currentNewValue = value;
                break;
            }
        }
        // If we did not find it at all, it is OK as long as the new value is NO_VALUE
        assert newValue == PNone.NO_VALUE || currentNewValue != PNone.NO_VALUE;
        if (currentOldValue != currentNewValue) {
            // Something actually changed, fixup subclasses...
            slot.setValue(klass, currentNewValue);
            fixupSpecialMethodInSubClasses(klass.getSubClasses(), slot, originalValue, newValue);
        } else {
            // We assume no other changes in MRO, so we must have either overridden the slot with
            // the new value or left it untouched unless the new value is NO_VALUE, in which case we
            // may pull some other value from other part of the MRO. Additionally, nothing is
            // certain, if we didn't know the original value
            assert originalValue == null || newValue == PNone.NO_VALUE || currentOldValue == newValue;
        }
    }

    private static void fixupSpecialMethodSlot(Object klass, SpecialMethodSlot slot, Object originalValue, Object newValue) {
        if (klass instanceof PythonManagedClass) {
            fixupSpecialMethodSlot((PythonManagedClass) klass, slot, originalValue, newValue);
        } else if (klass instanceof PythonNativeClass) {
            fixupSpecialMethodInSubClasses(GetSubclassesNode.getUncached().execute(klass), slot, originalValue, newValue);
        } else {
            throw new AssertionError(Objects.toString(klass));
        }
    }

    private static void fixupSpecialMethodInSubClasses(java.util.Set<PythonAbstractClass> subClasses, SpecialMethodSlot slot, Object originalValue, Object newValue) {
        for (PythonAbstractClass subClass : subClasses) {
            fixupSpecialMethodSlot(subClass, slot, originalValue, newValue);
        }
    }

    private static Object asSlotValue(Object value) {
        if (value instanceof PBuiltinFunction) {
            BuiltinMethodDescriptor info = BuiltinMethodDescriptor.get((PBuiltinFunction) value);
            if (info != null) {
                return info;
            }
        }
        return value;
    }

    // --------------------------------------------------
    // Lookup of the slots:

    /**
     * Fast check that can rule out that given name is a special slot.
     */
    public static boolean canBeSpecial(String name) {
        int len = name.length();
        return len > 5 && name.charAt(0) == '_' && name.charAt(1) == '_' &&
                        name.charAt(len - 1) == '_' && name.charAt(len - 2) == '_';
    }

    public static SpecialMethodSlot findSpecialSlot(String name) {
        if (!canBeSpecial(name)) {
            return null;
        }
        switch (name) {
            case __GETATTRIBUTE__:
                return GetAttribute;
            case __GETATTR__:
                return GetAttr;
            case __SETATTR__:
                return SetAttr;
            case __STR__:
                return Str;
            case __FORMAT__:
                return Format;
            case __DELATTR__:
                return DelAttr;
            case __ITER__:
                return Iter;
            case __NEXT__:
                return Next;
            case __GET__:
                return Get;
            case __EQ__:
                return Eq;
            case __NEW__:
                return New;
            case __INIT__:
                return Init;
            case __CALL__:
                return Call;
            case __SET__:
                return Set;
            case __GETITEM__:
                return GetItem;
            case __SETITEM__:
                return SetItem;
            case __LEN__:
                return Len;
            case __EXIT__:
                return Exit;
            case __ENTER__:
                return Enter;
            case __CONTAINS__:
                return Contains;
            case __DELITEM__:
                return DelItem;
            case __CLASS__:
                return Class;
            case __INSTANCECHECK__:
                return InstanceCheck;
            case __NE__:
                return Ne;
            case __LT__:
                return Lt;
            case __LE__:
                return Le;
            case __MISSING__:
                return Missing;
            case __BOOL__:
                return Bool;
            case __HASH__:
                return Hash;
            case __DELETE__:
                return Delete;
            case __DICT__:
                return Dict;
            case __PREPARE__:
                return Prepare;
            case __GT__:
                return Gt;
            case __GE__:
                return Ge;
            case __AND__:
                return And;
            case __RAND__:
                return RAnd;
            case __SET_NAME__:
                return SetName;
            case __REPR__:
                return Repr;
            case __SUBCLASSCHECK__:
                return Subclasscheck;
            case __ADD__:
                return Add;
            case __INDEX__:
                return Index;
            case __INT__:
                return Int;
            case __FLOAT__:
                return Float;
            case __REVERSED__:
                return Reversed;
            case __BYTES__:
                return Bytes;
            default:
                return null;
        }
    }

    static {
        assert checkFind();
    }

    private static boolean checkFind() {
        for (SpecialMethodSlot slot : VALUES) {
            if (findSpecialSlot(slot.getName()) != slot) {
                return false;
            }
        }
        return findSpecialSlot("__bogus__") == null;
    }

    // --------------------------------------------------
    // Methods for validation, some used in asserts, some unused but left here to aid local
    // debugging

    /**
     * Checks that there were no builtins' slots overridden except those explicitly marked so by
     * {@link PythonBuiltinClassType#redefinesSlot}.
     */
    public static boolean checkSlotOverrides(Python3Core core) {
        assert builtinSlotsInitialized;
        HashSet<String> mismatches = new HashSet<>();
        for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
            PythonBuiltinClass klass = core.lookupType(type);
            for (SpecialMethodSlot slot : VALUES) {
                Object typeValue = slot.getValue(type);
                if (typeValue != null) {
                    Object klassValue = slot.getValue(klass);
                    if (klassValue.equals(typeValue)) {
                        // values are same: OK
                        continue;
                    }
                    if (typeValue instanceof BuiltinMethodDescriptor && klassValue instanceof PBuiltinFunction &&
                                    ((BuiltinMethodDescriptor) typeValue).getFactory() == ((PBuiltinFunction) klassValue).getBuiltinNodeFactory()) {
                        // BuiltinMethodDescriptor and matching PBuiltinFunction: OK
                        continue;
                    }
                    mismatches.add(type.getName() + "." + slot.getName());
                }
            }
        }
        // If this assertion fails, update the list of the redefinedSlots for the offending types
        // See the static block in PythonBuiltinClassType
        assert mismatches.size() == 0 : String.join(", ", mismatches);
        return true;
    }

    private static final ArrayDeque<Object> initializingTypes; // types that are being initialized

    static {
        // Uncomment to start using validateSlots
        // initializingTypes = new ArrayDeque<>();
        initializingTypes = null;
    }

    public static boolean pushInitializedTypePlaceholder() {
        if (initializingTypes != null) {
            initializingTypes.push(42);
        }
        return true;
    }

    public static boolean replaceInitializedTypeTop(Object type) {
        if (initializingTypes != null) {
            assert (Integer) initializingTypes.pop() == 42;
            initializingTypes.push(type);
        }
        return true;
    }

    public static boolean popInitializedType() {
        if (initializingTypes != null) {
            initializingTypes.pop();
        }
        return true;
    }

    public static boolean canRedefineSlot(PythonManagedClass klass) {
        return !(klass instanceof PythonBuiltinClass) || !PythonLanguage.getCore().isInitialized();
    }

    // Note: this only works in single context, single threaded case!
    // Uncomment the initialization of initializingTypes in the static block
    @SuppressWarnings("unused")
    public static boolean validateSlots(Object klassIn) {
        if (initializingTypes.contains(klassIn)) {
            return true;
        }
        final Python3Core core = PythonLanguage.getCore();
        PythonLanguage language = core.getLanguage();
        ReadAttributeFromDynamicObjectNode uncachedReadAttrNode = ReadAttributeFromDynamicObjectNode.getUncached();
        Object klass = klassIn;
        if (klass instanceof PythonBuiltinClassType) {
            PythonBuiltinClassType type = (PythonBuiltinClassType) klass;
            klass = core.lookupType(type);
            if (initializingTypes.contains(klass)) {
                return true;
            }
            if (type.getSpecialMethodSlots() == null) {
                return true;
            }
            for (SpecialMethodSlot slot : VALUES) {
                Object actual = LookupAttributeInMRONode.findAttr(core, type, slot.getName(), uncachedReadAttrNode);
                Object expected = slot.getValue(type);
                if (expected instanceof BuiltinMethodDescriptor) {
                    assert actual instanceof PBuiltinFunction;
                    assert ((PBuiltinFunction) actual).getBuiltinNodeFactory() == ((BuiltinMethodDescriptor) expected).getFactory();
                } else if (expected != null) {
                    assert PythonLanguage.canCache(expected);
                    assert actual == expected;
                }
            }
            klass = core.lookupType(type);
        }
        if (klass instanceof PythonManagedClass) {
            PythonManagedClass managed = (PythonManagedClass) klass;
            for (SpecialMethodSlot slot : VALUES) {
                Object actual = LookupAttributeInMRONode.lookupSlowPath(managed, slot.getName());
                Object expected = slot.getValue(managed);
                if (expected instanceof NodeFactory<?>) {
                    assert actual instanceof PBuiltinFunction;
                    assert ((PBuiltinFunction) actual).getBuiltinNodeFactory() == expected;
                } else {
                    assert actual == expected;
                }
            }
        }
        return true;
    }
}
