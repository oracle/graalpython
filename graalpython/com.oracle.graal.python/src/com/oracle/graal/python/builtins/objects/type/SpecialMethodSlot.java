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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MISSING__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
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
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.BinaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.TernaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.UnaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;

/**
 * Subset of special methods that is cached in {@link PythonManagedClass} and
 * {@link PythonBuiltinClassType}.
 * 
 * For {@link PythonManagedClass}, we cache the result of special method lookup in a context
 * specific form: exactly the context specific object that regular MRO lookup would give. For
 * {@link PythonBuiltinClassType}, we cache only primitive and other context independent values and
 * additionally instances of {@link BuiltinMethodInfo}, which wrap context independent information
 * about the method that would be the result of the lookup. This information is further split to
 * language independent (per VM) part, which is the node factory, and per language part, which is
 * the call target. Call targets are cached in an array in the {@link PythonLanguage} instance, and
 * {@link BuiltinMethodInfo} holds only index into that array.
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
    Str(__STR__),
    // Note: __format__ does not seem to be actual slot in CPython, but it is looked up frequently
    Format(__FORMAT__),
    DelAttr(__DELATTR__),
    Iter(__ITER__),
    Next(__NEXT__),
    Get(__GET__),
    Eq(__EQ__),
    New(__NEW__),
    Init(__INIT__),
    Call(__CALL__),
    Set(__SET__),
    GetItem(__GETITEM__),
    SetItem(__SETITEM__),
    Len(__LEN__),
    Exit(__EXIT__),
    Enter(__ENTER__),
    Contains(__CONTAINS__),
    DelItem(__DELITEM__),
    Class(__CLASS__),
    InstanceCheck(__INSTANCECHECK__),
    Ne(__NE__),
    Lt(__LT__),
    Missing(__MISSING__),
    Bool(__BOOL__),
    Hash(__HASH__),
    Dict(__DICT__),
    Delete(__DELETE__),
    Prepare(__PREPARE__),
    Gt(__GT__),
    And(__AND__),
    RAnd(__RAND__),
    SetName(__SET_NAME__),
    Repr(__REPR__),
    Subclasscheck(__SUBCLASSCHECK__),
    Add(__ADD__);

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
                        ((PythonBuiltinClass) klass).getType().getSpecialMethodSlots() == null;
        klass.specialMethodSlots[ordinal()] = value;
        if (klass instanceof PythonClass) {
            ((PythonClass) klass).invalidateSlotsFinalAssumption();
        }
    }

    private void setInitialValue(PythonManagedClass klass, Object value) {
        // For builtin classes, we should see these updates only during initialization
        assert !PythonLanguage.getContext().isInitialized() || !(klass instanceof PythonBuiltinClass) ||
                        ((PythonBuiltinClass) klass).getType().getSpecialMethodSlots() == null;
        klass.specialMethodSlots[ordinal()] = value;
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
    private static void initializeBuiltinTypeSlots(PythonCore core) {
        synchronized (builtinSlotsInitializationLock) {
            if (builtinSlotsInitialized) {
                return;
            }
            initializeBuiltinTypeSlotsImpl(core);
            builtinSlotsInitialized = true;
        }
    }

    private static void initializeBuiltinTypeSlotsImpl(PythonCore core) {
        for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
            Object[] typeSlots = new Object[VALUES.length];
            PythonBuiltinClass klass = core.lookupType(type);
            for (SpecialMethodSlot slot : VALUES) {
                if (type.redefinesSlot(slot)) {
                    continue;
                }
                Object value = slot.getValue(klass);
                if (value instanceof PBuiltinFunction) {
                    NodeFactory<? extends PythonBuiltinBaseNode> factory = ((PBuiltinFunction) value).getBuiltinNodeFactory();
                    assert factory != null && !needsFrame(factory);
                    Class<? extends PythonBuiltinBaseNode> nodeClass = factory.getNodeClass();
                    if (PythonUnaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
                        typeSlots[slot.ordinal()] = new UnaryBuiltinInfo(factory, type, slot);
                    } else if (PythonBinaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
                        typeSlots[slot.ordinal()] = new BinaryBuiltinInfo(factory, type, slot);
                    } else if (PythonTernaryBuiltinNode.class.isAssignableFrom(nodeClass)) {
                        typeSlots[slot.ordinal()] = new TernaryBuiltinInfo(factory, type, slot);
                    }
                } else if (PythonLanguage.canCache(value)) {
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
                Arrays.fill(managedClass.specialMethodSlots, PNone.NO_VALUE);
                setSpecialMethodSlots(managedClass, GetMroStorageNode.getUncached());
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
        Object[] slots = new Object[VALUES.length];
        Arrays.fill(slots, PNone.NO_VALUE);
        klass.specialMethodSlots = slots;
        setSpecialMethodSlots(klass, getMroStorageNode);
    }

    private static void setSpecialMethodSlots(PythonManagedClass klass, GetMroStorageNode getMroStorageNode) {
        MroSequenceStorage mro = getMroStorageNode.execute(klass);
        setSpecialMethodSlots(klass, mro);
    }

    @TruffleBoundary
    private static void setSpecialMethodSlots(PythonManagedClass klass, MroSequenceStorage mro) {
        ReadAttributeFromObjectNode readNode = ReadAttributeFromObjectNode.getUncachedForceType();
        for (int i = 0; i < mro.length(); i++) {
            Object kls = mro.getItemNormalized(i);
            for (SpecialMethodSlot slot : VALUES) {
                if (slot.getValue(klass) == PNone.NO_VALUE) {
                    Object value = readNode.execute(kls, slot.getName());
                    if (value != PNone.NO_VALUE) {
                        slot.setInitialValue(klass, value);
                    }
                }
            }
        }
    }

    @TruffleBoundary
    public static void fixupSpecialMethodSlot(PythonNativeClass klass, SpecialMethodSlot slot, Object value) {
        Object newValue = value;
        if (value == PNone.NO_VALUE) {
            // We are removing the value: find the new value for the class that is being updated and
            // proceed with that
            newValue = LookupAttributeInMRONode.lookupSlow(klass, slot.getName());
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
            newValue = LookupAttributeInMRONode.lookupSlow(klass, slot.getName());
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
                slot.setValue(klass, value);
                break;
            }
        }
        assert newValue == null || newValue == PNone.NO_VALUE || slot.getValue(klass) != PNone.NO_VALUE;
        if (currentOldValue != currentNewValue) {
            // Something actually changed, fixup subclasses...
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

    private static boolean needsFrame(NodeFactory<? extends PythonBuiltinBaseNode> factory) {
        for (Builtin builtin : factory.getNodeClass().getAnnotationsByType(Builtin.class)) {
            if (builtin.needsFrame()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks that there were no builtins' slots overridden except those explicitly marked so by
     * {@link PythonBuiltinClassType#redefinesSlot}.
     */
    public static boolean checkSlotOverrides(PythonCore core) {
        assert builtinSlotsInitialized;
        HashSet<String> mismatches = new HashSet<>();
        for (PythonBuiltinClassType type : PythonBuiltinClassType.VALUES) {
            PythonBuiltinClass klass = core.lookupType(type);
            for (SpecialMethodSlot slot : VALUES) {
                Object typeValue = slot.getValue(type);
                if (typeValue != null) {
                    Object klassValue = slot.getValue(klass);
                    if (typeValue instanceof BuiltinMethodInfo) {
                        if (!(klassValue instanceof PBuiltinFunction) ||
                                        ((BuiltinMethodInfo) typeValue).getFactory() != ((PBuiltinFunction) klassValue).getBuiltinNodeFactory()) {
                            mismatches.add(type.getName() + "." + slot.getName());
                        }
                    } else if (!typeValue.equals(klassValue)) {
                        mismatches.add(type.getName() + "." + slot.getName());
                    }
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
        final PythonCore core = PythonLanguage.getCore();
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
                if (expected instanceof BuiltinMethodInfo) {
                    assert actual instanceof PBuiltinFunction;
                    assert ((PBuiltinFunction) actual).getBuiltinNodeFactory() == ((BuiltinMethodInfo) expected).getFactory();
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
                Object actual = LookupAttributeInMRONode.lookupSlow(managed, slot.getName());
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
