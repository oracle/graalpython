/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot.Flags.NO_BUILTIN_DESCRIPTORS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AEXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MISSING__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET_NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUBCLASSCHECK__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

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
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.strings.TruffleString;

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
    AEnter(T___AENTER__),
    AExit(T___AEXIT__),

    SetName(T___SET_NAME__, NO_BUILTIN_DESCRIPTORS),
    InstanceCheck(T___INSTANCECHECK__),
    Subclasscheck(T___SUBCLASSCHECK__),

    Exit(T___EXIT__),
    Enter(T___ENTER__),

    LengthHint(T___LENGTH_HINT__),
    // Note: __format__ does not seem to be actual slot in CPython, but it is looked up frequently
    Format(T___FORMAT__),
    Missing(T___MISSING__),

    Round(T___ROUND__),

    Reversed(T___REVERSED__),
    Bytes(T___BYTES__);

    static class Flags {
        static final boolean NO_BUILTIN_DESCRIPTORS = false;
    }

    public static final SpecialMethodSlot[] VALUES = values();

    private final TruffleString name;
    /**
     * Indicates if given slot may or must not store context independent (AST cacheable)
     * {@link BuiltinMethodDescriptor} objects.
     *
     * Values of some slots are always or mostly passed to call node variants that can handle
     * {@link BuiltinMethodDescriptor}.
     *
     * An alternative would be to update the whole calling machinery ({@code InvokeNode},
     * {@code GetSignature}, ...) to handle {@link BuiltinMethodDescriptor} and extend
     * {@link BuiltinMethodDescriptor} to contain all the information that is necessary for this
     * (GR-32148).
     */
    private final boolean allowsBuiltinDescriptors;

    SpecialMethodSlot(TruffleString name, boolean allowsBuiltinDescriptors) {
        this.name = name;
        this.allowsBuiltinDescriptors = allowsBuiltinDescriptors;
    }

    SpecialMethodSlot(TruffleString name) {
        this(name, true);
    }

    static {
        assert checkFind();
    }

    public TruffleString getName() {
        return name;
    }

    public Object getValue(PythonManagedClass klass) {
        assert klass.specialMethodSlots != null;
        return klass.specialMethodSlots[ordinal()];
    }

    @Idempotent
    public Object getValue(PythonBuiltinClassType klassType) {
        // should not be called during initialization
        return klassType.getSpecialMethodSlots()[ordinal()];
    }

    private void setValue(PythonManagedClass klass, Object value, PythonContext context) {
        // For builtin classes, we should see these updates only during initialization
        assert !context.isInitialized() || !(klass instanceof PythonBuiltinClass) ||
                        ((PythonBuiltinClass) klass).getType().getSpecialMethodSlots() == null : String.format("%s.%s = %s", klass, getName(), value);
        klass.specialMethodSlots[ordinal()] = asSlotValue(this, value, context.getLanguage());
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
                slot.setValue(klass, value, core.getContext());
            }
        }
    }

    private static final Object builtinSlotsInitializationLock = new Object();
    private static volatile boolean builtinSlotsInitialized;

    public static boolean areBuiltinSlotsInitialized() {
        return builtinSlotsInitialized;
    }

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
                Object value = slot.getValue(klass);
                if (value instanceof PBuiltinFunction && slot.allowsBuiltinDescriptors) {
                    BuiltinMethodDescriptor info = BuiltinMethodDescriptor.get((PBuiltinFunction) value);
                    if (info != null) {
                        typeSlots[slot.ordinal()] = info;
                    }
                } else if ((value instanceof BuiltinMethodDescriptor && slot.allowsBuiltinDescriptors) || PythonLanguage.canCache(value)) {
                    typeSlots[slot.ordinal()] = value;
                }
            }
            type.setSpecialMethodSlots(typeSlots);
        }
    }

    // --------------------------------------------------
    // Initialization and updates of the user classes:

    @TruffleBoundary
    public static void reinitializeSpecialMethodSlots(PythonManagedClass klass, PythonLanguage language) {
        reinitializeSpecialMethodSlots((Object) klass, language);
    }

    @TruffleBoundary
    public static void reinitializeSpecialMethodSlots(PythonNativeClass klass, PythonLanguage language) {
        reinitializeSpecialMethodSlots((Object) klass, language);
    }

    private static void reinitializeSpecialMethodSlots(Object klass, PythonLanguage language) {
        PythonAbstractClass[] subClasses;
        if (klass instanceof PythonManagedClass managedClass) {
            // specialMethodSlots can be null if the type is just being initialized, for example,
            // when the initialization calls the "mro" method, which may execute arbitrary code
            // including setting its __bases__ to something.
            // TODO: LookupAttributeInMRONode and other places rely on specialMethodSlots being
            // always initialized, can it happen that some code invoked during type initialization
            // is going to lookup something in that type's MRO?
            if (managedClass.specialMethodSlots != null) {
                managedClass.specialMethodSlots = null;
                initializeSpecialMethodSlots(managedClass, GetMroStorageNode.executeUncached(managedClass), language);
            }
            subClasses = GetSubclassesAsArrayNode.executeUncached(managedClass);
        } else if (klass instanceof PythonNativeClass clazz) {
            subClasses = GetSubclassesAsArrayNode.executeUncached(clazz);
        } else {
            throw new AssertionError(Objects.toString(klass));
        }
        for (PythonAbstractClass subClass : subClasses) {
            reinitializeSpecialMethodSlots(subClass, language);
        }
    }

    public static void initializeSpecialMethodSlots(PythonManagedClass klass, MroSequenceStorage mro, PythonLanguage language) {
        klass.specialMethodSlots = initializeSpecialMethodsSlots(klass, mro, language);
    }

    @TruffleBoundary
    private static Object[] initializeSpecialMethodsSlots(PythonManagedClass klass, MroSequenceStorage mro, PythonLanguage language) {
        // Note: the classes in MRO may not have their special slots initialized, which is
        // pathological case that can happen if MRO is fiddled with during MRO computation

        // Fast-path: If MRO(klass) == (A, B, C, ...) and A == klass and MRO(B) == (B, C, ...), then
        // we can just "extend" the slots of B with the new overrides in A. This fast-path seem to
        // handle large majority of the situations
        if (mro.length() >= 2 && klass.getBaseClasses().length <= 1) {
            PythonAbstractClass firstType = mro.getPythonClassItemNormalized(0);
            PythonAbstractClass secondType = mro.getPythonClassItemNormalized(1);
            if (firstType == klass && PythonManagedClass.isInstance(secondType)) {
                PythonManagedClass managedBase = PythonManagedClass.cast(secondType);
                if (managedBase.specialMethodSlots != null) {
                    if (isMroSubtype(mro, managedBase)) {
                        Object[] result = PythonUtils.arrayCopyOf(managedBase.specialMethodSlots, managedBase.specialMethodSlots.length);
                        setSlotsFromManaged(result, klass, language);
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
        PythonAbstractClass lastType = mro.getPythonClassItemNormalized(mro.length() - 1);
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
            PythonAbstractClass base = mro.getPythonClassItemNormalized(i);
            if (PythonManagedClass.isInstance(base)) {
                setSlotsFromManaged(slots, PythonManagedClass.cast(base), language);
            } else {
                setSlotsFromGeneric(slots, base, language);
            }
        }
        return slots;
    }

    private static boolean isMroSubtype(MroSequenceStorage superTypeMro, PythonManagedClass subType) {
        if (subType instanceof PythonBuiltinClass && ((PythonBuiltinClass) subType).getType() == PythonBuiltinClassType.PythonObject) {
            // object is subclass of everything
            return true;
        }
        MroSequenceStorage subTypeMro = GetMroStorageNode.executeUncached(subType);
        boolean isMroSubtype = subTypeMro.length() == superTypeMro.length() - 1;
        if (isMroSubtype) {
            for (int i = 0; i < subTypeMro.length(); i++) {
                if (superTypeMro.getPythonClassItemNormalized(i + 1) != subTypeMro.getPythonClassItemNormalized(i)) {
                    isMroSubtype = false;
                    break;
                }
            }
        }
        return isMroSubtype;
    }

    private static void setSlotsFromManaged(Object[] slots, PythonManagedClass source, PythonLanguage language) {
        PDict dict = GetDictIfExistsNode.getUncached().execute(source);
        if (dict == null) {
            for (SpecialMethodSlot slot : VALUES) {
                final Object value = ReadAttributeFromPythonObjectNode.executeUncached(source, slot.getName(), PNone.NO_VALUE);
                if (value != PNone.NO_VALUE) {
                    slots[slot.ordinal()] = asSlotValue(slot, value, language);
                }
            }
        } else {
            HashingStorage storage = dict.getDictStorage();
            for (SpecialMethodSlot slot : VALUES) {
                final Object value = HashingStorageGetItem.executeUncached(storage, slot.getName());
                if (value != null) {
                    slots[slot.ordinal()] = asSlotValue(slot, value, language);
                }
            }
        }
    }

    private static void setSlotsFromGeneric(Object[] slots, PythonAbstractClass base, PythonLanguage language) {
        ReadAttributeFromObjectNode readAttNode = ReadAttributeFromObjectNode.getUncachedForceType();
        for (SpecialMethodSlot slot : VALUES) {
            Object value = readAttNode.execute(base, slot.getName());
            if (value != PNone.NO_VALUE) {
                slots[slot.ordinal()] = asSlotValue(slot, value, language);
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
        fixupSpecialMethodInSubClasses(GetSubclassesAsArrayNode.executeUncached(klass), slot, newValue, PythonContext.get(null));
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

        Object oldValue = slot.getValue(klass);
        if (value == oldValue) {
            return;
        }

        Object newValue = value;
        if (value == PNone.NO_VALUE) {
            // We are removing the value: find the new value for the class that is being updated and
            // proceed with that
            newValue = LookupAttributeInMRONode.lookupSlowPath(klass, slot.getName());
        }

        PythonContext context = PythonContext.get(null);
        slot.setValue(klass, newValue, context);
        fixupSpecialMethodInSubClasses(GetSubclassesAsArrayNode.executeUncached(klass), slot, value, context);
    }

    // Note: originalValue == null means originalValue is not available
    private static void fixupSpecialMethodSlotInternal(PythonManagedClass klass, SpecialMethodSlot slot, Object newValue, PythonContext context) {
        Object currentOldValue = slot.getValue(klass);
        // Even if this slot was occupied by the same value as in the base, it does not mean that
        // the value was here because it was inherited from the base class where we now overridden
        // that slot. To stay on the safe side, we consult the MRO here.
        Object currentNewValue = LookupAttributeInMRONode.lookupSlowPath(klass, slot.getName());
        if (newValue != PNone.NO_VALUE) {
            // If the newly written value is not NO_VALUE, then should either override the slot with
            // the new value or leave it unchanged if it inherited the value from some other class
            assert currentNewValue != PNone.NO_VALUE;
            assert asSlotValue(slot, currentNewValue, context.getLanguage()) == currentOldValue || currentNewValue == newValue;
        }
        // Else if the newly written value was NO_VALUE, then we either remove the slot or we pull
        // its value from some other class in the MRO
        if (currentOldValue != currentNewValue) {
            // Something actually changed, fixup subclasses...
            slot.setValue(klass, currentNewValue, context);
            fixupSpecialMethodInSubClasses(GetSubclassesAsArrayNode.executeUncached(klass), slot, newValue, context);
        }
    }

    private static void fixupSpecialMethodSlot(Object klass, SpecialMethodSlot slot, Object newValue, PythonContext context) {
        if (klass instanceof PythonManagedClass clazz) {
            fixupSpecialMethodSlotInternal((PythonManagedClass) klass, slot, newValue, context);
        } else if (klass instanceof PythonNativeClass clazz) {
            fixupSpecialMethodInSubClasses(GetSubclassesAsArrayNode.executeUncached(clazz), slot, newValue, context);
        } else {
            throw new AssertionError(Objects.toString(klass));
        }
    }

    private static void fixupSpecialMethodInSubClasses(PythonAbstractClass[] subClasses, SpecialMethodSlot slot, Object newValue, PythonContext context) {
        for (PythonAbstractClass subClass : subClasses) {
            fixupSpecialMethodSlot(subClass, slot, newValue, context);
        }
    }

    private static Object asSlotValue(SpecialMethodSlot slot, Object value, PythonLanguage language) {
        if (value instanceof PBuiltinFunction && slot.allowsBuiltinDescriptors) {
            PBuiltinFunction builtinFun = (PBuiltinFunction) value;
            BuiltinMethodDescriptor info = BuiltinMethodDescriptor.get(builtinFun);
            if (info != null) {
                if (builtinFun.getDescriptor() == null) {
                    // Note: number of all builtins >> number of builtins used in slots, so it is
                    // better to do this lazily
                    language.registerBuiltinDescriptorCallTarget(info, builtinFun.getCallTarget());
                    builtinFun.setDescriptor(info);
                }
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
    public static boolean canBeSpecial(TruffleString name, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        int len = codePointLengthNode.execute(name, TS_ENCODING);
        return len > 5 && codePointAtIndexNode.execute(name, len - 2, TS_ENCODING) == '_' && codePointAtIndexNode.execute(name, len - 1, TS_ENCODING) == '_' &&
                        codePointAtIndexNode.execute(name, 1, TS_ENCODING) == '_' && codePointAtIndexNode.execute(name, 0, TS_ENCODING) == '_';
    }

    @TruffleBoundary
    public static SpecialMethodSlot findSpecialSlotUncached(TruffleString name) {
        return findSpecialSlot(name, TruffleString.CodePointLengthNode.getUncached(), TruffleString.CodePointAtIndexNode.getUncached(), TruffleString.EqualNode.getUncached());
    }

    public static SpecialMethodSlot findSpecialSlot(TruffleString name, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                    TruffleString.EqualNode eqNode) {
        if (!canBeSpecial(name, codePointLengthNode, codePointAtIndexNode)) {
            return null;
        }
        int x = codePointAtIndexNode.execute(name, 2, TS_ENCODING) * 26 + codePointAtIndexNode.execute(name, 3, TS_ENCODING);
        switch (x) {
            case 's' * 26 + 'e':    // se
                if (eqNode.execute(name, T___SET_NAME__, TS_ENCODING)) {
                    return SetName;
                }
                break;
            case 'i' * 26 + 'n':    // in
                if (eqNode.execute(name, T___INSTANCECHECK__, TS_ENCODING)) {
                    return InstanceCheck;
                }
                break;
            case 's' * 26 + 'u':    // su
                if (eqNode.execute(name, T___SUBCLASSCHECK__, TS_ENCODING)) {
                    return Subclasscheck;
                }
                break;
            case 'e' * 26 + 'x':    // ex
                if (eqNode.execute(name, T___EXIT__, TS_ENCODING)) {
                    return Exit;
                }
                break;
            case 'e' * 26 + 'n':    // en
                if (eqNode.execute(name, T___ENTER__, TS_ENCODING)) {
                    return Enter;
                }
                break;
            case 'l' * 26 + 'e':    // le
                if (eqNode.execute(name, T___LENGTH_HINT__, TS_ENCODING)) {
                    return LengthHint;
                }
                break;
            case 'r' * 26 + 'e':    // re
                if (eqNode.execute(name, T___REVERSED__, TS_ENCODING)) {
                    return Reversed;
                }
                break;
            case 'f' * 26 + 'o':    // fo
                if (eqNode.execute(name, T___FORMAT__, TS_ENCODING)) {
                    return Format;
                }
                break;
            case 'm' * 26 + 'i':    // mi
                if (eqNode.execute(name, T___MISSING__, TS_ENCODING)) {
                    return Missing;
                }
                break;
            case 'r' * 26 + 'o':    // ro
                if (eqNode.execute(name, T___ROUND__, TS_ENCODING)) {
                    return Round;
                }
                break;
            case 'b' * 26 + 'y':    // by
                if (eqNode.execute(name, T___BYTES__, TS_ENCODING)) {
                    return Bytes;
                }
                break;
            case 'a' * 26 + 'e': // ae
                if (eqNode.execute(name, T___AENTER__, TS_ENCODING)) {
                    return AEnter;
                }
                if (eqNode.execute(name, T___AEXIT__, TS_ENCODING)) {
                    return AExit;
                }
                break;
        }
        return null;
    }

    private static boolean checkFind() {
        for (SpecialMethodSlot slot : VALUES) {
            if (findSpecialSlotUncached(slot.getName()) != slot) {
                assert false : slot;
                return false;
            }
        }
        return findSpecialSlotUncached(tsLiteral("__bogus__")) == null;
    }

    // --------------------------------------------------
    // Methods for validation, some used in asserts, some unused but left here to aid local
    // debugging

    /**
     * Checks that there were no builtins' slots overridden.
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
                                    ((BuiltinMethodDescriptor) typeValue).isDescriptorOf((PBuiltinFunction) klassValue)) {
                        // BuiltinMethodDescriptor and matching PBuiltinFunction: OK
                        continue;
                    }
                    mismatches.add(type.getName().toJavaStringUncached() + "." + slot.getName().toJavaStringUncached());
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

    // Note: this only works in single context, single threaded case!
    // Uncomment the initialization of initializingTypes in the static block
    @SuppressWarnings("unused")
    public static boolean validateSlots(Object klassIn) {
        if (initializingTypes.contains(klassIn)) {
            return true;
        }
        ReadAttributeFromPythonObjectNode uncachedReadAttrNode = ReadAttributeFromPythonObjectNode.getUncached();
        final Python3Core core = PythonContext.get(uncachedReadAttrNode);
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
                    assert ((BuiltinMethodDescriptor) expected).isDescriptorOf((PBuiltinFunction) actual);
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
