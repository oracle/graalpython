/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.getUncachedInterop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.BinaryOpSlotFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.BinarySlotFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.DescrGetFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.DescrSetFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.GetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InquiryWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.LenfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SetattrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SsizeargfuncSlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.TpSlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPySlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ReadPointerNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.WritePointerNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlotsFactory.GetObjectSlotsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TpSlotsFactory.GetTpSlotsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotHPyNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.TpSlotBinaryFuncBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.TpSlotSqConcat;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.TpSlotBinaryOpBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.TpSlotBinaryOpPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.TpSlotDescrGetBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.TpSlotDescrSetBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.TpSlotDescrSetPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.TpSlotGetAttrBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.TpSlotGetAttrPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.TpSlotInquiryBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.TpSlotLenBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.TpSlotSetAttrBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.TpSlotSetAttrPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.TpSlotSizeArgFunBuiltin;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.InlineWeakValueProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Wraps fields that hold slot values, instances of {@link TpSlot}, such as {@link #tp_getattr()}.
 * This is GraalPy equivalent of the same fields in CPython's {@code PyTypeObject}.
 * <p>
 * Summary of the interactions:
 *
 * <pre>
 *     Builtins:
 *      - initialization of the slots: static ctor of {@link PythonBuiltinClassType}
 *      - initialization of the wrappers: context initialization calls {@link TpSlots#addOperatorsToBuiltin(Map, Python3Core, PythonBuiltinClassType)}
 *      - all the slots are static and shared per JVM, builtins do not allow to update attributes after ctx initialization
 *
 *     Native classes:
 *      - type_ready in typeobject.c calls these helpers in this order:
 *          - type_ready_graalpy_slot_conv: up-calls PyTruffleType_AddSlot to create the Python wrappers for each slot
 *              - eventually will be replaced with one upcall to managed implementation of CPython's add_operators
 *                  that will use the {@link #SLOTDEFS} definitions and unsafe to read the slots
 *          - type_ready_inherit: does the slot inheritance in native
 *          - type_ready_graalpy_process_inherited_slots: up-calls PyTruffle_AddInheritedSlots to:
 *              - transfer the native slots to their managed mirror {@link TpSlots}
 *              - re-validate cached lookups in TpSlotPython, see comment in {@link #fromNative(PythonAbstractNativeObject, PythonContext)}
 *              - fixup getsets and members
 *
 *     Python classes:
 *      - BuiltinConstructors#Type calls helper CreateTypeNode to create the new class object, which should
 *       mirror this logic of type_new_impl in CPython:
 *          - call type_ready
 *              - call add_operators (in type_ready_fill_dict) to create the Python wrappers
 *                  - only for the slots defined by the type itself, so we don't need that for managed classes
 *              - call type_ready_inherit to inherit native slots
 *          - call fixup_slot_dispatchers
 *              - creates Python wrappers for inherited slots using slotdefs
 *              - it has some quirks, so we follow the same algorithm using the the slotdefs ({@link #SLOTDEFS}).
 *              - This is not called for native types: nor static, neither heap types
 *      - When Python class goes to native (in ToNativeTypeNode) we convert the slots to native in
 *          {@link TpSlot#toNative(TpSlotMeta, TpSlot, Object)}
 *          - TpSlotNative slots are unwrapped
 *          - For managed slots we create corresponding {@link PyProcsWrapper}
 *              - when {@link PyProcsWrapper} goes to native, it registers itself in a map in context, so
 *              that when it comes back from native in {@link #fromNative(PythonAbstractNativeObject, PythonContext)}
 *              we can recognize it and use the managed TpSlot object.
 *
 *     For both Python and native classes:
 *      - upon attribute change (in WriteAttributeToObjectNode) we update the slots by calling fixup_slot_dispatchers
 *      - CPython does that in the type's tp_setattr(o) slot, se we are inconsistent here, which can be fixed later
 * </pre>
 * <p>
 * Note: fields with "combined" prefix, such as {@link #combined_sq_mp_length()}, are optimization:
 * the value is {@link #sq_length()} if non-null otherwise {@link #mp_length()}.
 *
 * @param tp_getattr Note: in CPython, this slot is considered deprecated and the preferred slot
 *            should be {@link #tp_getattro()}. We assume that no builtins define this slot.
 */
public record TpSlots(TpSlot nb_bool, //
                TpSlot nb_add,
                TpSlot nb_multiply, //
                TpSlot sq_length, //
                TpSlot sq_item, //
                TpSlot sq_concat, //
                TpSlot sq_repeat, //
                TpSlot mp_length, //
                TpSlot mp_subscript, //
                TpSlot combined_sq_mp_length, //
                TpSlot combined_mp_sq_length, //
                TpSlot tp_descr_get, //
                TpSlot tp_descr_set, //
                TpSlot tp_getattro, //
                TpSlot tp_getattr, //
                TpSlot combined_tp_getattro_getattr, //
                TpSlot tp_setattro, //
                TpSlot tp_setattr,
                TpSlot combined_tp_setattro_setattr,
                boolean has_as_number,
                boolean has_as_sequence,
                boolean has_as_mapping) {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(TpSlot.class);

    @FunctionalInterface
    private interface TpSlotGetter {
        TpSlot get(TpSlots slots);
    }

    /**
     * Creates a native function (closure) that wraps a managed slot. It has signature of given
     * native slot and calls into the appropriate "slot call node" (e.g.,
     * {@code CallSlotNbBoolNode}) to execute the managed slot (builtin or Python function wrapper).
     * This is GraalPy specific concept.
     */
    @FunctionalInterface
    private interface NativeWrapperFactory {
        TpSlotWrapper create(TpSlotManaged slot);

        final class Unimplemented implements NativeWrapperFactory {
            @Override
            public TpSlotWrapper create(TpSlotManaged slot) {
                throw new IllegalStateException("TODO: " + slot);
            }
        }

        record ShouldNotReach(String slotName) implements NativeWrapperFactory {
            @Override
            public TpSlotWrapper create(TpSlotManaged slot) {
                throw new IllegalStateException(String.format("Slot %s should never be assigned a managed slot value.", slotName));
            }
        }
    }

    /**
     * Creates a slot that wraps Python magic methods. Similar to CPython
     * {@code wrapperbase.function}.
     */
    @FunctionalInterface
    public interface PythonFunctionFactory {
        /**
         * For slots that map to multiple magic methods, this factory receives all the magic methods
         * ({@link PNone#NO_VALUE} for those missing). When called, at least one magic method must
         * be present.
         */
        TpSlot create(Object[] callables, TruffleString[] callableNames, Object klass);
    }

    public static final class SimplePythonWrapper implements PythonFunctionFactory {
        private static final SimplePythonWrapper INSTANCE = new SimplePythonWrapper();

        @Override
        public TpSlot create(Object[] callables, TruffleString[] names, Object klass) {
            assert callables.length == 1;
            assert callables[0] != PNone.NO_VALUE;
            return new TpSlotPythonSingle(callables[0], klass, names[0]);
        }
    }

    public enum TpSlotGroup {
        AS_NUMBER(CFields.PyTypeObject__tp_as_number),
        AS_SEQUENCE(CFields.PyTypeObject__tp_as_sequence),
        AS_MAPPING(CFields.PyTypeObject__tp_as_mapping),
        NO_GROUP(null); // Must be last

        public static final TpSlotGroup[] VALID_VALUES = Arrays.copyOf(values(), values().length - 1);

        private final CFields cField;

        TpSlotGroup(CFields cField) {
            this.cField = cField;
        }
    }

    /**
     * Metadata for each slot field.
     */
    public enum TpSlotMeta {
        NB_BOOL(
                        TpSlots::nb_bool,
                        TpSlotPythonSingle.class,
                        TpSlotInquiryBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_bool,
                        PExternalFunctionWrapper.INQUIRY,
                        InquiryWrapper::new),
        NB_ADD(
                        TpSlots::nb_add,
                        TpSlotBinaryOpPython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_add,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createAdd),
        NB_MULTIPLY(
                        TpSlots::nb_multiply,
                        TpSlotBinaryOpPython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_multiply,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createMultiply),
        SQ_LENGTH(
                        TpSlots::sq_length,
                        TpSlotPythonSingle.class,
                        TpSlotLenBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_length,
                        PExternalFunctionWrapper.LENFUNC,
                        LenfuncWrapper::new),
        SQ_CONCAT(
                        TpSlots::sq_concat,
                        TpSlotPythonSingle.class,
                        TpSlotSqConcat.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_concat,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        SQ_ITEM(
                        TpSlots::sq_item,
                        TpSlotPythonSingle.class,
                        TpSlotSizeArgFunBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_item,
                        PExternalFunctionWrapper.GETITEM,
                        SsizeargfuncSlotWrapper::new),
        SQ_REPEAT(
                        TpSlots::sq_repeat,
                        TpSlotPythonSingle.class,
                        TpSlotSizeArgFunBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_repeat,
                        PExternalFunctionWrapper.SSIZE_ARG,
                        SsizeargfuncSlotWrapper::new),
        MP_LENGTH(
                        TpSlots::mp_length,
                        TpSlotPythonSingle.class,
                        TpSlotLenBuiltin.class,
                        TpSlotGroup.AS_MAPPING,
                        CFields.PyMappingMethods__mp_length,
                        PExternalFunctionWrapper.LENFUNC,
                        LenfuncWrapper::new),
        MP_SUBSCRIPT(
                        TpSlots::mp_subscript,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryFuncBuiltin.class,
                        TpSlotGroup.AS_MAPPING,
                        CFields.PyMappingMethods__mp_subscript,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        TP_DESCR_GET(
                        TpSlots::tp_descr_get,
                        TpSlotPythonSingle.class,
                        TpSlotDescrGetBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_descr_get,
                        PExternalFunctionWrapper.DESCR_GET,
                        DescrGetFunctionWrapper::new),
        TP_DESCR_SET(
                        TpSlots::tp_descr_set,
                        TpSlotDescrSetPython.class,
                        TpSlotDescrSetBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_descr_set,
                        PExternalFunctionWrapper.DESCR_SET,
                        DescrSetFunctionWrapper::new),
        TP_GETATTRO(
                        TpSlots::tp_getattro,
                        TpSlotGetAttrPython.class,
                        TpSlotGetAttrBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_getattro,
                        PExternalFunctionWrapper.BINARYFUNC,
                        GetAttrWrapper::new),
        TP_GETATTR(
                        TpSlots::tp_getattr,
                        null,
                        null,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_getattr,
                        PExternalFunctionWrapper.GETATTR,
                        new NativeWrapperFactory.ShouldNotReach("tp_getattr")),
        TP_SETATTRO(
                        TpSlots::tp_setattro,
                        TpSlotSetAttrPython.class,
                        TpSlotSetAttrBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_setattro,
                        PExternalFunctionWrapper.SETATTRO,
                        SetattrWrapper::new),
        TP_SETATTR(
                        TpSlots::tp_setattr,
                        null,
                        null,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_setattr,
                        PExternalFunctionWrapper.SETATTR,
                        new NativeWrapperFactory.ShouldNotReach("tp_setattr"));

        public static final TpSlotMeta[] VALUES = values();

        private final TpSlotGetter getter;
        private final Class<? extends TpSlotPython> permittedPythonSlotClass;
        @SuppressWarnings("rawtypes") private final Class<? extends TpSlotBuiltin> permittedBuiltinSlotClass;
        private final TpSlotGroup group;
        private final CFields nativeGroupOrField;
        private final CFields nativeField;
        private final PExternalFunctionWrapper nativeSignature;
        private final NativeWrapperFactory nativeWrapperFactory;

        TpSlotMeta(TpSlotGetter getter, Class<? extends TpSlotPython> permittedPythonSlotClass, @SuppressWarnings("rawtypes") Class<? extends TpSlotBuiltin> permittedBuiltinSlotClass,
                        TpSlotGroup group, CFields nativeField, PExternalFunctionWrapper nativeSignature, NativeWrapperFactory nativeWrapperFactory) {
            this.permittedPythonSlotClass = permittedPythonSlotClass;
            this.permittedBuiltinSlotClass = permittedBuiltinSlotClass;
            this.nativeWrapperFactory = nativeWrapperFactory;
            this.getter = getter;
            assert group != null;
            this.group = group;
            if (group == TpSlotGroup.NO_GROUP) {
                this.nativeGroupOrField = nativeField;
                this.nativeField = null;
            } else {
                this.nativeGroupOrField = group.cField;
                this.nativeField = nativeField;
            }
            this.nativeSignature = nativeSignature;
        }

        public boolean isValidSlotValue(Object value) {
            return value == null || value instanceof TpSlotNative ||
                            (permittedBuiltinSlotClass != null && permittedBuiltinSlotClass.isAssignableFrom(value.getClass())) ||
                            (permittedPythonSlotClass != null && permittedPythonSlotClass.isAssignableFrom(value.getClass()));
        }

        public boolean supportsManagedSlotValues() {
            return permittedBuiltinSlotClass != null || permittedPythonSlotClass != null;
        }

        /**
         * First offset that needs to be read to access the slot value. It is either the slot itself
         * or the group, e.g., {@code tp_as_number}. Never null.
         */
        public CFields getNativeGroupOrField() {
            return nativeGroupOrField;
        }

        public boolean hasGroup() {
            return nativeField != null;
        }

        /**
         * Second offset that needs to be read to access the slot value or {@code null}, i.e.,
         * offset in the group, or {@code null} if the slot is not in group.
         */
        public CFields getNativeField() {
            return nativeField;
        }

        public TpSlot getValue(TpSlots slots) {
            return getter.get(slots);
        }

        public Object getNativeValue(TpSlots slots, Object defaultValue) {
            return TpSlot.toNative(this, getter.get(slots), defaultValue);
        }

        private Object getNativeValue(TpSlot slot, Object defaultValue) {
            return TpSlot.toNative(this, slot, defaultValue);
        }

        /**
         * Returns Java {@code null} if the native value is NULL, otherwise interop object
         * representing the native value.
         */
        public Object readFromNative(PythonAbstractNativeObject pythonClass) {
            Object field = ReadPointerNode.getUncached().readFromObj(pythonClass, nativeGroupOrField);
            InteropLibrary ptrInterop = null;
            if (nativeField != null) {
                ptrInterop = InteropLibrary.getUncached(field);
                if (!ptrInterop.isNull(field)) {
                    field = ReadPointerNode.getUncached().read(field, nativeField);
                } else {
                    return null;
                }
            }
            if (getUncachedInterop(ptrInterop, field).isNull(field)) {
                return null;
            }
            return field;
        }

        public TpSlotWrapper createNativeWrapper(TpSlotManaged slot) {
            return nativeWrapperFactory.create(slot);
        }

        // Temporary, used only for migration
        public boolean hasNativeWrapperFactory() {
            return !(nativeWrapperFactory instanceof NativeWrapperFactory.Unimplemented);
        }

        public PExternalFunctionWrapper getNativeSignature() {
            return nativeSignature;
        }

        public TpSlotGroup getGroup() {
            return group;
        }
    }

    /**
     * Mirrors CPython's {@code wrapperbase} aka {@code slotdef}. CPython's {@code slotdefs} is a
     * flattened view on our map with all {@link TpSlotDef} objects grouped by their native slot
     * field.
     *
     * @param name
     * @param functionFactory {@code descrobject.h:wrapperbase#function}
     * @param wrapper {@code descrobject.h:wrapperbase#wrapper}
     */
    public record TpSlotDef(TruffleString name, PythonFunctionFactory functionFactory,
                    PExternalFunctionWrapper wrapper, HPySlotWrapper hpyWrapper) {
        public static TpSlotDef withoutHPy(TruffleString name, PythonFunctionFactory functionFactory, PExternalFunctionWrapper wrapper) {
            return new TpSlotDef(name, functionFactory, wrapper, null);
        }

        public static TpSlotDef withSimpleFunction(TruffleString name, PExternalFunctionWrapper wrapper) {
            return new TpSlotDef(name, SimplePythonWrapper.INSTANCE, wrapper, null);
        }

        public static TpSlotDef withSimpleFunction(TruffleString name, PExternalFunctionWrapper wrapper, HPySlotWrapper hpyWrapper) {
            return new TpSlotDef(name, SimplePythonWrapper.INSTANCE, wrapper, hpyWrapper);
        }

        public static TpSlotDef withNoFunctionNoWrapper(TruffleString name) {
            return new TpSlotDef(name, null, null, null);
        }

        public static TpSlotDef withNoFunction(TruffleString name, PExternalFunctionWrapper wrapper) {
            return new TpSlotDef(name, null, wrapper, null);
        }

        public static TpSlotDef withNoFunction(TruffleString name, PExternalFunctionWrapper wrapper, HPySlotWrapper hpyWrapper) {
            return new TpSlotDef(name, null, wrapper, hpyWrapper);
        }
    }

    /**
     * This should mirror the {@code slotdefs} in CPython <b>including the order</b>. Unlike CPython
     * we group the definitions to simplify the iteration over definitions for the same tp slot.
     */
    private static final LinkedHashMap<TpSlotMeta, TpSlotDef[]> SLOTDEFS;
    private static final Map<TruffleString, List<TpSlotMeta>> SPECIAL2SLOT;

    private static final Map<TruffleString, Set<Entry<TpSlotMeta, TpSlotDef[]>>> SPECIAL2SLOTDEF;

    private static void addSlotDef(LinkedHashMap<TpSlotMeta, TpSlotDef[]> defs, TpSlotMeta slot, TpSlotDef... slotDefs) {
        defs.put(slot, slotDefs);
    }

    static {
        LinkedHashMap<TpSlotMeta, TpSlotDef[]> s = new LinkedHashMap<>(30);

        addSlotDef(s, TpSlotMeta.TP_GETATTR,
                        TpSlotDef.withNoFunctionNoWrapper(T___GETATTRIBUTE__),
                        TpSlotDef.withNoFunctionNoWrapper(T___GETATTR__));
        addSlotDef(s, TpSlotMeta.TP_SETATTR,
                        TpSlotDef.withNoFunctionNoWrapper(T___SETATTR__),
                        TpSlotDef.withNoFunctionNoWrapper(T___DELATTR__));
        addSlotDef(s, TpSlotMeta.TP_GETATTRO,
                        TpSlotDef.withoutHPy(T___GETATTRIBUTE__, TpSlotGetAttrPython::create, PExternalFunctionWrapper.BINARYFUNC),
                        TpSlotDef.withoutHPy(T___GETATTR__, TpSlotGetAttrPython::create, null));
        addSlotDef(s, TpSlotMeta.TP_SETATTRO,
                        TpSlotDef.withoutHPy(T___SETATTR__, TpSlotSetAttrPython::create, PExternalFunctionWrapper.SETATTRO),
                        TpSlotDef.withoutHPy(T___DELATTR__, TpSlotSetAttrPython::create, PExternalFunctionWrapper.DELATTRO));
        addSlotDef(s, TpSlotMeta.TP_DESCR_GET, TpSlotDef.withSimpleFunction(T___GET__, PExternalFunctionWrapper.DESCR_GET));
        addSlotDef(s, TpSlotMeta.TP_DESCR_SET, //
                        TpSlotDef.withoutHPy(T___SET__, TpSlotDescrSetPython::create, PExternalFunctionWrapper.DESCR_SET), //
                        TpSlotDef.withoutHPy(T___DELETE__, TpSlotDescrSetPython::create, PExternalFunctionWrapper.DESCR_DELETE));
        addSlotDef(s, TpSlotMeta.NB_ADD,
                        TpSlotDef.withoutHPy(T___ADD__, TpSlotBinaryOpPython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.withoutHPy(T___RADD__, TpSlotBinaryOpPython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_MULTIPLY,
                        TpSlotDef.withoutHPy(T___MUL__, TpSlotBinaryOpPython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.withoutHPy(T___RMUL__, TpSlotBinaryOpPython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_BOOL, TpSlotDef.withSimpleFunction(T___BOOL__, PExternalFunctionWrapper.INQUIRY));
        addSlotDef(s, TpSlotMeta.MP_LENGTH, TpSlotDef.withSimpleFunction(T___LEN__, PExternalFunctionWrapper.LENFUNC, HPySlotWrapper.LENFUNC));
        addSlotDef(s, TpSlotMeta.MP_SUBSCRIPT, TpSlotDef.withSimpleFunction(T___GETITEM__, PExternalFunctionWrapper.BINARYFUNC, HPySlotWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.SQ_LENGTH, TpSlotDef.withSimpleFunction(T___LEN__, PExternalFunctionWrapper.LENFUNC, HPySlotWrapper.LENFUNC));
        // sq_concat does not have a slotdef for __radd__ unlike sq_repeat. This have consequences
        // w.r.t. inheritance from native classes, where sq_repeat is not overridden by __mul__.
        // Makes one wonder whether this CPython behavior is intended.
        // see test_sq_repeat_mul_without_rmul_inheritance
        addSlotDef(s, TpSlotMeta.SQ_CONCAT, TpSlotDef.withNoFunction(T___ADD__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.SQ_REPEAT,
                        TpSlotDef.withNoFunction(T___MUL__, PExternalFunctionWrapper.SSIZE_ARG, HPySlotWrapper.INDEXARGFUNC),
                        TpSlotDef.withNoFunction(T___RMUL__, PExternalFunctionWrapper.SSIZE_ARG, HPySlotWrapper.INDEXARGFUNC));
        addSlotDef(s, TpSlotMeta.SQ_ITEM, TpSlotDef.withSimpleFunction(T___GETITEM__, PExternalFunctionWrapper.GETITEM, HPySlotWrapper.SQ_ITEM));

        SLOTDEFS = s;
        SPECIAL2SLOT = new HashMap<>(SLOTDEFS.size() * 2);
        SPECIAL2SLOTDEF = new HashMap<>(SLOTDEFS.size() * 2);
        for (var e : SLOTDEFS.entrySet()) {
            for (TpSlotDef slotDef : e.getValue()) {
                SPECIAL2SLOT.computeIfAbsent(slotDef.name(), k -> new ArrayList<>()).add(e.getKey());
                SPECIAL2SLOTDEF.computeIfAbsent(slotDef.name(), k -> new HashSet<>()).add(e);
            }
        }
    }

    public static TpSlots createEmpty() {
        return newBuilder().build();
    }

    /**
     * Creates {@link TpSlots} from native slots of a native class. This should be called as (or
     * close to the) final step of native class initialization.
     */
    public static TpSlots fromNative(PythonAbstractNativeObject pythonClass, PythonContext ctx) {
        var builder = TpSlots.newBuilder();
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            if (!def.hasNativeWrapperFactory()) {
                continue;
            }
            Object field = def.readFromNative(pythonClass);
            if (field == null) {
                continue;
            }

            // Is this pointer representing some TpSlot that we transferred to native?
            InteropLibrary interop = InteropLibrary.getUncached(field);
            TpSlotWrapper existingSlotWrapper = null;
            if (interop.isPointer(field)) {
                try {
                    Object executable = ctx.getCApiContext().getClosureExecutable(interop.asPointer(field));
                    if (executable instanceof TpSlotWrapper execWrapper) {
                        existingSlotWrapper = execWrapper;
                    } else if (executable != null) {
                        // This can happen for legacy slots where the delegate would be a PFunction
                        LOGGER.warning(() -> String.format("Unexpected executable for slot pointer: %s", executable));
                    }
                } catch (UnsupportedMessageException e) {
                    throw new IllegalStateException(e);
                }
            } else if (field instanceof TpSlotWrapper execWrapper) {
                existingSlotWrapper = execWrapper;
            }

            if (existingSlotWrapper != null) {
                TpSlot existingSlot = existingSlotWrapper.getSlot();
                TpSlot newSlot = existingSlot;
                // Corner case that can happen with native type inheriting managed type:
                // 1) the type can inherit some TpSlotPython, which have cached lookups
                // 2) however, in typeobject.c:add_operators another slot that shares the same magic
                // method adds its magic method wrapper overriding the magic method that the slot
                // from 1) cached. We must therefore re-validate all the cached lookups here.
                if (existingSlot instanceof TpSlotPython pythonSlot) {
                    TpSlotPython newPythonSlot = pythonSlot.forNewType(pythonClass);
                    newSlot = newPythonSlot;
                    if (newSlot != existingSlot) {
                        // If the slot changed we make wrapper with the same signature as the
                        // original slot, which does not have to be the signature of the currently
                        // processed slot field, because user could have assigned some incompatible
                        // existing slot value into the slots field we're reading here
                        TpSlotWrapper newWrapper = existingSlotWrapper.cloneWith(newPythonSlot);
                        toNative(pythonClass.getPtr(), def, newWrapper, ctx.getNativeNull());
                        // we need to continue with the new closure pointer
                        field = def.readFromNative(pythonClass);
                    }
                }
                if (def.isValidSlotValue(newSlot)) {
                    builder.set(def, newSlot);
                    continue;
                }
                // If the slot value is not valid for given slot, we fallthrough and wrap the slot
                // pointer we got from native into TpSlotNative, so we will preserve the identity
                // of builtin slots pointers (for Python slots we are doomed, because we cache the
                // lookups in them, so we must have different identity), and calls are channeled
                // through native trampoline, so whatever native calling convention hack the user
                // intends to abuse should in theory work in the same way as in CPython.
            }
            // There is no mapping from this pointer to existing TpSlot, we create a new
            // TpSlotNative wrapping the executable
            Object executable = CExtContext.ensureExecutable(field, def.nativeSignature);
            builder.set(def, TpSlotNative.createCExtSlot(executable));
        }
        return builder.build();
    }

    private static void toNative(Object ptrToWrite, TpSlotMeta def, TpSlot value, Object nullValue) {
        assert !(ptrToWrite instanceof PythonAbstractNativeObject); // this should be the pointer
        Object slotNativeValue = def.getNativeValue(value, nullValue);
        toNative(ptrToWrite, def, slotNativeValue, nullValue);
    }

    /**
     * Writes back given managed slot to the native klass slots. This should be called any time we
     * update the slots on the managed side to reflect that change in native.
     */
    private static void toNative(Object prtToWrite, TpSlotMeta def, Object slotNativeValue, Object nullValue) {
        assert !(slotNativeValue instanceof TpSlot); // this should be the native representation
        assert !(prtToWrite instanceof PythonAbstractNativeObject); // this should be the pointer
        CompilerAsserts.neverPartOfCompilation();
        CFields fieldToWrite = def.nativeGroupOrField;
        if (def.nativeField != null) {
            prtToWrite = ReadPointerNode.getUncached().read(prtToWrite, def.nativeGroupOrField);
            if (InteropLibrary.getUncached().isNull(prtToWrite)) {
                if (slotNativeValue == nullValue) {
                    return;
                } else {
                    throw new IllegalStateException("Trying to write a native slot whose group is not allocated. " +
                                    "Do we need to update 'updateSlots' to ignore non-allocated groups like CPython?");
                }
            }
            fieldToWrite = def.nativeField;
        }
        WritePointerNode.getUncached().write(prtToWrite, fieldToWrite, slotNativeValue);
    }

    @TruffleBoundary
    public static void inherit(PythonClass klass, MroSequenceStorage mro, boolean allocateAllGroups) {
        // partially implements CPython:type_ready_inherit
        // slots of native classes are initialized in PyTruffle_AddInheritedSlots, they are just a
        // mirror of the native slots initialized and inherited on the native side
        assert klass.getTpSlots() == null;
        Builder klassSlots = newBuilder();
        if (allocateAllGroups) {
            klassSlots.allocateAllGroups();
        }
        for (int i = 0; i < mro.length(); i++) {
            PythonAbstractClass type = mro.getPythonClassItemNormalized(i);
            TpSlots slots = GetTpSlotsNode.executeUncached(type);
            assert slots != null || type == klass;
            if (slots != null) {
                klassSlots.inherit(slots);
            }
        }
        klass.setTpSlots(klassSlots.build());
    }

    public static boolean canBeSpecialMethod(TruffleString name, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        int len = codePointLengthNode.execute(name, TS_ENCODING);
        return len > 5 && codePointAtIndexNode.execute(name, len - 2, TS_ENCODING) == '_' && codePointAtIndexNode.execute(name, len - 1, TS_ENCODING) == '_' &&
                        codePointAtIndexNode.execute(name, 1, TS_ENCODING) == '_' && codePointAtIndexNode.execute(name, 0, TS_ENCODING) == '_';
    }

    public static boolean isSpecialMethod(TruffleString name) {
        CompilerAsserts.neverPartOfCompilation();
        return SPECIAL2SLOT.containsKey(name);
    }

    /*
     * Return a slot metadata for a given name, but ONLY if the attribute has exactly one slot
     * function. Mirrors CPython's {@code resolve_slotdups}.
     */
    private static TpSlotMeta resolveSlotdups(Builder slots, TruffleString name) {
        TpSlotMeta found = null;
        for (TpSlotMeta s : SPECIAL2SLOT.get(name)) {
            TpSlot value = slots.get(s);
            if (value != null) {
                if (found != null) {
                    return null;
                }
                found = s;
            }
        }
        return found;
    }

    /**
     * Mirrors CPython's {@code typeobject.c:fixup_slot_dispatchers}.
     */
    @TruffleBoundary
    public static void fixupSlotDispatchers(PythonClass klass) {
        updateSlots(klass, klass.getTpSlots(), SLOTDEFS.entrySet());
    }

    /**
     * Mirrors CPython's {@code typeobject.c:update_all_slots}.
     */
    @TruffleBoundary
    public static void updateAllSlots(PythonAbstractClass klass) {
        updateSlot(klass, SLOTDEFS.entrySet());
    }

    /**
     * Mirrors CPython's {@code typeobject.c:update_slot}.
     */
    @TruffleBoundary
    public static void updateSlot(PythonAbstractClass klass, TruffleString specialMethodName) {
        // We find all tp slots that have a slotdef that has name equal to the name that was changed
        Set<Entry<TpSlotMeta, TpSlotDef[]>> slotdefGroups = SPECIAL2SLOTDEF.get(specialMethodName);
        if (slotdefGroups == null) {
            return;
        }
        updateSlot(klass, slotdefGroups);
    }

    private static void updateSlot(PythonAbstractClass klass, Set<Entry<TpSlotMeta, TpSlotDef[]>> slotdefGroups) {
        // slots can be null if the type is just being initialized, for example,
        // when the initialization calls the "mro" method, which may execute arbitrary code
        // including setting its __bases__ to something.
        TpSlots slots = GetTpSlotsNode.executeUncached(klass);
        if (slots == null) {
            return;
        }
        updateSlots(klass, slots, slotdefGroups);
        for (PythonAbstractClass subClass : GetSubclassesAsArrayNode.executeUncached(klass)) {
            updateSlot(subClass, slotdefGroups);
        }
    }

    private static void updateSlots(PythonAbstractClass klass, TpSlots slots, Set<Entry<TpSlotMeta, TpSlotDef[]>> slotdefGroups) {
        setSlots(klass, updateSlots(klass, slots.copy(), slotdefGroups).build());
    }

    /**
     * The loop body mirrors CPython's {@code typeobject.c:update_one_slot}, but we take a set of
     * "slot groups" to update whereas CPython usually does the loop over such groups in the caller.
     * <p>
     * Sets slots to dispatchers that call the corresponding Python magic methods, such as
     * {@code __bool__}. In CPython this is done in {@code type_new}, which is {@code tp_new} for
     * types, which means that this is done only for classes created from Python, native classes
     * inherit the native slots directly in {@code type_ready}.
     * <p>
     * Note that the inheritance logic is slightly different than what would MRO attributes
     * inheritance do. We mirror CPython's logic as closely as possible.
     */
    private static Builder updateSlots(PythonAbstractClass klass, Builder slots, Set<Entry<TpSlotMeta, TpSlotDef[]>> slotdefGroups) {
        LookupAttributeInMRONode.Dynamic lookup = LookupAttributeInMRONode.Dynamic.getUncached();
        IsSubtypeNode isSubType = IsSubtypeNode.getUncached();
        Object nativeNull = PythonContext.get(null).getNativeNull();
        for (var slotdefGroup : slotdefGroups) {
            TpSlotMeta slot = slotdefGroup.getKey(); // ~ "ptr" in CPython algorithm
            if (slot.hasGroup() && !slots.hasGroup(slot.getGroup())) {
                // CPython skips "indirect" slots, for which the type does not have the group
                // allocated. Note however that native heap types and managed types have always all
                // the groups allocated.
                continue;
            }

            boolean useGeneric = false;
            // 'generic' will be either slot.pythonWrapperFactory or null, we could have used a
            // boolean flag, but to stay closer to the original...
            PythonFunctionFactory generic = null;
            TpSlot specific = null;
            TpSlotDef[] defs = slotdefGroup.getValue();
            Object[] genericCallables = new Object[defs.length];
            TruffleString[] genericCallablesNames = new TruffleString[defs.length];

            // There may be multiple magic methods for one tp slot. In this loop we iterate over all
            // the special method names that can fill the slot, and we figure out how to set it.
            //
            // If all the special method agree on one builtin or native method to call, we set
            // the slot to that bypassing the Python level call. If they disagree or if one of them
            // is generic dispatcher to a Python magic method, we also set the slot to a dispatcher
            // to Python magic method, which we, unlike CPython, cache in the slot - this may cause
            // a difference in behavior - if that turns out to be a problem, we can use MRO stable
            // assumption to still be able to cache the value, but detect MRO change and fallback to
            // dynamic lookup.
            for (int i = 0; i < defs.length; i++) {
                TruffleString name = defs[i].name();
                Object decr = lookup.execute(klass, name);
                genericCallables[i] = decr;
                genericCallablesNames[i] = name;
                if (decr == PNone.NO_VALUE) {
                    /*- TODO:
                    if (ptr == (void**)&type->tp_iternext) {
                        specific = (void *)_PyObject_NextNotImplemented;
                    }*/
                    continue;
                }
                // Is the value a builtin function (in CPython PyWrapperDescr_Type) that wraps a
                // builtin or native slot?
                if (decr instanceof PBuiltinFunction builtin && builtin.getSlot() != null) {
                    /*
                     * CPython source comment: if the special method is a wrapper_descriptor with
                     * the correct name but the type has precisely one slot set for that name and
                     * that slot is not the one that we are updating, then NULL is put in the slot
                     * (this exception is the only place in update_one_slot() where the *existing*
                     * slots matter).
                     *
                     * This translates to not setting variable 'generic' and leaving it null if
                     * resolve_slotdups returned something that is not our slot.
                     *
                     * For example: add_operators called earlier than this creates "__add__" that
                     * wraps sq_concat and there is no nb_add slot => here we find out that
                     * "__add__" is a wrapper of a different slot (fallback to useGeneric = true)
                     * and we also find out that there is exactly one other slot covering "__add__",
                     * which is sq_concat and that's different to our current slot, so we are not
                     * going to set the variable 'generic' and leave it null. All in all, nb_add is
                     * set to null.
                     */
                    TpSlotMeta tptr = resolveSlotdups(slots, name);
                    if (tptr == null || tptr == slot) {
                        generic = defs[i].functionFactory;
                    }

                    TpSlot wrappedSlot = builtin.getSlot();
                    boolean canSetSpecific = specific == null || specific == wrappedSlot || areSameNativeCallables(wrappedSlot, specific);
                    if (canSetSpecific && //
                                    builtin.getSlotWrapper() == defs[i].wrapper() && //
                                    isSubType.execute(klass, builtin.getEnclosingType())) {
                        specific = wrappedSlot;
                    } else {
                        /*-
                         * We cannot use the specific slot function because either
                         * - it is not unique: there are multiple methods for this slot, and they conflict (canSetSpecific)
                         * - the signature is wrong (as checked by the ->wrapper comparison above)
                         * - it's wrapping the wrong class
                         */
                        useGeneric = true;
                    }

                    // TODO: special cases:
                    // PyCFunction_Type && tp_new (looks like just optimization)
                    // descr == Py_None && ptr == (void**)&type->tp_hash ->
                    // PyObject_HashNotImplemented
                } else {
                    useGeneric = true;
                    generic = defs[i].functionFactory;
                }
            }

            if (specific != null && !useGeneric) {
                slots.set(slot, specific);
            } else {
                TpSlot newValue = null;
                if (generic != null) {
                    newValue = generic.create(genericCallables, genericCallablesNames, klass);
                }
                slots.set(slot, newValue);
                if (klass instanceof PythonAbstractNativeObject nativeClass) {
                    // Update the slots on the native side if this is a native class
                    toNative(nativeClass.getPtr(), slot, newValue, nativeNull);
                }
                if (klass instanceof PythonManagedClass managedClass) {
                    // Update the slots on the native side if this is a managed class that has a
                    // native mirror allocated already
                    PythonClassNativeWrapper classNativeWrapper = managedClass.getClassNativeWrapper();
                    if (classNativeWrapper != null) {
                        Object replacement = classNativeWrapper.getReplacementIfInitialized();
                        if (replacement != null) {
                            toNative(replacement, slot, newValue, nativeNull);
                        }
                    }
                }
            }
        }
        return slots;
    }

    private static boolean areSameNativeCallables(TpSlot a, TpSlot b) {
        return a instanceof TpSlotNative na && b instanceof TpSlotNative nb && na.isSameCallable(nb, InteropLibrary.getUncached());
    }

    public static void setSlots(PythonAbstractClass klass, TpSlots slots) {
        if (klass instanceof PythonClass pythonClass) {
            pythonClass.setTpSlots(slots);
        } else if (klass instanceof PythonAbstractNativeObject nativeClass) {
            nativeClass.setTpSlots(slots);
        } else {
            String name = klass == null ? "null" : klass.getClass().getName();
            throw new AssertionError("Unexpected type :" + name);
        }
    }

    public static void initializeBuiltinSlots(PythonLanguage language) {
        for (PythonBuiltinClassType klass : PythonBuiltinClassType.VALUES) {
            for (TpSlotMeta slotMeta : TpSlotMeta.VALUES) {
                TpSlot slotValue = slotMeta.getValue(klass.getDeclaredSlots());
                if (slotValue instanceof TpSlotBuiltin<?> builtinSlot) {
                    builtinSlot.initialize(language);
                } else {
                    // No other than builtin slots are allowed in builtins
                    assert slotValue == null;
                }
            }
        }
    }

    private static boolean checkNoMagicOverrides(Python3Core core, PythonBuiltinClassType type) {
        // Check that no one is trying to define magic methods directly
        // If the assertion fires: you should define @Slot instead of @Builtin
        // We do not look in MRO, we may have already called addOperatorsToBuiltin on super
        var readAttr = ReadAttributeFromObjectNode.getUncachedForceType();
        PythonBuiltinClass typeObj = core.lookupType(type);
        for (TruffleString name : SPECIAL2SLOT.keySet()) {
            assert readAttr.execute(typeObj, name) == PNone.NO_VALUE : type.name() + ":" + name;
        }
        return true;
    }

    public static void addOperatorsToBuiltin(Map<TruffleString, BoundBuiltinCallable<?>> builtins, Python3Core core, PythonBuiltinClassType type) {
        TpSlots slots = type.getDeclaredSlots();
        assert checkNoMagicOverrides(core, type);

        // Similar to CPython:add_operators
        for (var slotDefGroup : SLOTDEFS.entrySet()) {
            TpSlotMeta slotMeta = slotDefGroup.getKey();
            TpSlot slotValue = slotMeta.getter.get(slots);
            if (!(slotValue instanceof TpSlotBuiltin<?> builtinSlot)) {
                continue;
            }
            for (TpSlotDef slotDef : slotDefGroup.getValue()) {
                if (slotDef.wrapper() != null && !builtins.containsKey(slotDef.name())) {
                    var value = builtinSlot.createBuiltin(core, type, slotDef.name(), slotDef.wrapper());
                    builtins.put(slotDef.name(), value);
                }
            }
        }
    }

    /**
     * Version of CPython's {@code add_operators} that assumes only native slots. This is useful for
     * the HPy case where users cannot "steal" other type's slots or builtin slots. TODO: (GR-53923)
     * extend this to support python/builtin slots, use it instead of PyTruffleType_AddSlot upcalls.
     */
    @TruffleBoundary
    public void addOperators(PythonClass type) {
        // Current version assumes no dict and writes to the object directly
        assert GetDictIfExistsNode.getUncached().execute(type) == null;

        PythonContext context = PythonContext.get(null);
        var factory = context.factory();
        PythonLanguage language = context.getLanguage();
        for (Entry<TpSlotMeta, TpSlotDef[]> slotDefEntry : SLOTDEFS.entrySet()) {
            TpSlotMeta tpSlotMeta = slotDefEntry.getKey();
            for (TpSlotDef tpSlotDef : slotDefEntry.getValue()) {
                if (tpSlotDef.wrapper == null) {
                    continue;
                }
                TpSlot value = tpSlotMeta.getValue(this);
                if (value == null) {
                    continue;
                }
                Object existingValue = ReadAttributeFromObjectNode.getUncachedForceType().execute(type, tpSlotDef.name);
                if (!PGuards.isNoValue(existingValue)) {
                    continue;
                }
                // TODO: special case for PyObject_HashNotImplemented once we have tp_hash
                Object wrapperDescriptor = null;
                if (value instanceof TpSlotBuiltin<?> builtinSlot) {
                    wrapperDescriptor = builtinSlot.createBuiltin(context, type, tpSlotDef.name, tpSlotDef.wrapper);
                } else if (value instanceof TpSlotPython) {
                    // TODO: see CExtNodes$CreateFunctionNode
                    throw new IllegalStateException("addOperators: TpSlotPython not implemented yet");
                } else if (value instanceof TpSlotNative nativeSlot) {
                    if (nativeSlot instanceof TpSlotHPyNative hpySlot) {
                        wrapperDescriptor = HPyExternalFunctionNodes.createWrapperFunction(language, context.getHPyContext(), tpSlotDef.hpyWrapper, hpySlot, tpSlotDef.wrapper, tpSlotDef.name,
                                        nativeSlot.getCallable(), type,
                                        factory);
                    } else {
                        wrapperDescriptor = PExternalFunctionWrapper.createWrapperFunction(tpSlotDef.name, (TpSlotCExtNative) nativeSlot, type, tpSlotDef.wrapper, language, factory);
                    }
                }
                assert wrapperDescriptor != null;
                // TODO: optionally take TpDict and write into it if provided
                WriteAttributeToPythonObjectNode.executeUncached(type, tpSlotDef.name, wrapperDescriptor);
            }
        }
    }

    public Builder copy() {
        var result = new Builder();
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            result.set(def, def.getValue(this));
        }
        for (TpSlotGroup group : TpSlotGroup.VALID_VALUES) {
            result.setExplicitGroup(group);
        }
        return result;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static TpSlots merge(TpSlots a, TpSlots b) {
        return a.copy().merge(b).build();
    }

    public static final class Builder {
        private final TpSlot[] values = new TpSlot[TpSlotMeta.VALUES.length];
        private final boolean[] explicitGroups = new boolean[TpSlotGroup.VALID_VALUES.length];

        public void allocateAllGroups() {
            Arrays.fill(explicitGroups, true);
        }

        public void setExplicitGroup(TpSlotGroup group) {
            assert group != TpSlotGroup.NO_GROUP;
            explicitGroups[group.ordinal()] = true;
        }

        public Builder set(TpSlotMeta slotMeta, TpSlot value) {
            assert slotMeta.isValidSlotValue(value) : String.format("Slot %s is being assigned to type incompatible slot value %s.", slotMeta.name(), value);
            values[slotMeta.ordinal()] = value;
            return this;
        }

        public Builder overrideIgnoreGroups(TpSlots other) {
            for (TpSlotMeta def : TpSlotMeta.VALUES) {
                TpSlot current = values[def.ordinal()];
                TpSlot otherValue = def.getter.get(other);
                TpSlot newValue = otherValue != null ? otherValue : current;
                set(def, newValue);
            }
            return this;
        }

        private Builder inherit(TpSlots other) {
            // similar to CPython:inherit_slots
            // indirect slots (from tp_as_number etc.) are not inherited if the group is not
            // allocated explicitly. Note: native heap types and managed types have always all
            // groups allocated.
            for (TpSlotMeta def : TpSlotMeta.VALUES) {
                if (def.group != TpSlotGroup.NO_GROUP && !explicitGroups[def.group.ordinal()]) {
                    continue;
                }
                TpSlot current = values[def.ordinal()];
                TpSlot otherValue = def.getter.get(other);
                TpSlot newValue = current != null ? current : otherValue;
                set(def, newValue);
            }
            return this;
        }

        /**
         * Should be used when merging together generated slots from two or more
         * {@link com.oracle.graal.python.builtins.PythonBuiltins}. Checks that slots are not
         * overriding each other.
         */
        public Builder merge(TpSlots other) {
            for (TpSlotMeta def : TpSlotMeta.VALUES) {
                TpSlot current = values[def.ordinal()];
                TpSlot otherValue = def.getter.get(other);
                if (otherValue != null) {
                    assert current == null : def.name();
                    set(def, otherValue);
                }
            }
            return this;
        }

        private TpSlot fistNonNull(TpSlotMeta a, TpSlotMeta b) {
            return values[a.ordinal()] != null ? values[a.ordinal()] : values[b.ordinal()];
        }

        private TpSlot get(TpSlotMeta s) {
            return values[s.ordinal()];
        }

        private boolean hasGroup(TpSlotGroup group) {
            assert group != TpSlotGroup.NO_GROUP;
            if (explicitGroups[group.ordinal()]) {
                return true;
            }
            for (TpSlotMeta def : TpSlotMeta.VALUES) {
                if (def.group == group && values[def.ordinal()] != null) {
                    return true;
                }
            }
            return false;
        }

        public TpSlots build() {
            TpSlot sq_mp_length = fistNonNull(TpSlotMeta.SQ_LENGTH, TpSlotMeta.MP_LENGTH);
            TpSlot mp_sq_length = fistNonNull(TpSlotMeta.MP_LENGTH, TpSlotMeta.SQ_LENGTH);
            TpSlot tp_get_attro_attr = fistNonNull(TpSlotMeta.TP_GETATTRO, TpSlotMeta.TP_GETATTR);
            TpSlot tp_set_attro_attr = fistNonNull(TpSlotMeta.TP_SETATTRO, TpSlotMeta.TP_SETATTR);
            return new TpSlots(
                            get(TpSlotMeta.NB_BOOL), //
                            get(TpSlotMeta.NB_ADD), //
                            get(TpSlotMeta.NB_MULTIPLY), //
                            get(TpSlotMeta.SQ_LENGTH), //
                            get(TpSlotMeta.SQ_ITEM), //
                            get(TpSlotMeta.SQ_CONCAT), //
                            get(TpSlotMeta.SQ_REPEAT), //
                            get(TpSlotMeta.MP_LENGTH), //
                            get(TpSlotMeta.MP_SUBSCRIPT), //
                            sq_mp_length, //
                            mp_sq_length, //
                            get(TpSlotMeta.TP_DESCR_GET), //
                            get(TpSlotMeta.TP_DESCR_SET), //
                            get(TpSlotMeta.TP_GETATTRO), //
                            get(TpSlotMeta.TP_GETATTR), //
                            tp_get_attro_attr,
                            get(TpSlotMeta.TP_SETATTRO),
                            get(TpSlotMeta.TP_SETATTR),
                            tp_set_attro_attr,
                            hasGroup(TpSlotGroup.AS_NUMBER),
                            hasGroup(TpSlotGroup.AS_SEQUENCE),
                            hasGroup(TpSlotGroup.AS_MAPPING));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetTpSlotsNode extends Node {
        public abstract TpSlots execute(Node inliningTarget, Object pythonClass);

        public static TpSlots executeUncached(Object pythonClass) {
            return GetTpSlotsNodeGen.getUncached().execute(null, pythonClass);
        }

        @Specialization
        static TpSlots doBuiltinType(PythonBuiltinClassType type) {
            return type.getSlots();
        }

        @Specialization
        static TpSlots doManaged(PythonManagedClass klass) {
            return klass.getTpSlots();
        }

        @Specialization
        static TpSlots doNative(Node inliningTarget, PythonAbstractNativeObject nativeKlass,
                        @Cached InlinedBranchProfile slotsNotInitializedProfile) {
            TpSlots tpSlots = nativeKlass.getTpSlots();
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, tpSlots == null)) {
                /*
                 * This happens when we try to get slots of a type that didn't go through
                 * PyType_Ready yet. Specifically, numpy has a "fortran" type (defined in
                 * `fortranobject.c`) that they never ready and just expect it to work because it's
                 * simple. So just do the minimum to make the slots available.
                 */
                slotsNotInitializedProfile.enter(inliningTarget);
                tpSlots = initializeNativeSlots(nativeKlass);
            }
            return tpSlots;
        }

        @TruffleBoundary
        private static TpSlots initializeNativeSlots(PythonAbstractNativeObject nativeKlass) {
            TpSlots tpSlots = TpSlots.fromNative(nativeKlass, PythonContext.get(null));
            nativeKlass.setTpSlots(tpSlots);
            return tpSlots;
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @ImportStatic(PGuards.class)
    public abstract static class GetCachedTpSlotsNode extends Node {
        public abstract TpSlots execute(Node inliningTarget, Object pythonClass);

        @Specialization
        static TpSlots doBuiltin(PythonBuiltinClassType klass) {
            return klass.getSlots();
        }

        @Specialization(replaces = "doBuiltin")
        static TpSlots doOtherCached(Node inliningTarget, Object klass,
                        @Cached InlineWeakValueProfile weakValueProfile,
                        @Cached GetTpSlotsNode getSlots) {
            return weakValueProfile.execute(inliningTarget, getSlots.execute(inliningTarget, klass));
        }

        @GenerateCached(false)
        private static final class Uncached extends GetCachedTpSlotsNode {
            private static final Uncached INSTANCE = new Uncached();

            @Override
            public TpSlots execute(@SuppressWarnings("unused") Node inliningTarget, Object pythonClass) {
                return GetTpSlotsNode.executeUncached(pythonClass);
            }
        }

        public static GetCachedTpSlotsNode getUncached() {
            return Uncached.INSTANCE;
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    public abstract static class GetObjectSlotsNode extends Node {
        public abstract TpSlots execute(Node inliningTarget, Object pythonObject);

        public final TpSlots executeCached(Object pythonObject) {
            return execute(this, pythonObject);
        }

        @NeverDefault
        public static GetObjectSlotsNode create() {
            return GetObjectSlotsNodeGen.create();
        }

        // Note: it seems that switching the GetClassNode with an adhoc GetClassNode variant that
        // does not have any inline caches does not change peak at least for micro:if-polymorph
        // TODO: verify this on all benchmarks and get rid of the IC if possible
        @Specialization
        static TpSlots doIt(Node inliningTarget, Object pythonObject,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCachedTpSlotsNode getSlotsNode) {
            return getSlotsNode.execute(inliningTarget, getClassNode.execute(inliningTarget, pythonObject));
        }
    }

    @SuppressWarnings("all")
    private static boolean areAssertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    // Used for sanity checks in assertions
    public boolean areEqualTo(TpSlots otherSlots) {
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            TpSlot thisValue = def.getter.get(this);
            TpSlot otherValue = def.getter.get(otherSlots);
            if (thisValue != otherValue) {
                return false;
            }
        }
        return true;
    }
}
