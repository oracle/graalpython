/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ANEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AWAIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RLSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;
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
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.BinaryOpSlotFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.BinarySlotFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.CallWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.DescrGetFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.DescrSetFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.GetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.HashfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InitWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.InquiryWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.IterNextWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.LenfuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.NbInPlacePowerWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.NbPowerWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.NewWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.ObjobjargWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.RichcmpFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SetattrWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SqContainsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SsizeargfuncSlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.SsizeobjargprocWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.TpSlotWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper.UnaryFuncWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureExecutableNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ReadPointerNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.WritePointerNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.TpSlotsFactory.GetObjectSlotsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TpSlotsFactory.GetTpSlotsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotManaged;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.TpSlotBinaryFuncBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.TpSlotSqConcat;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.TpSlotBinaryIOpBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.TpSlotBinaryOpBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.TpSlotReversiblePython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.TpSlotDescrGetBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.TpSlotDescrSetBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.TpSlotDescrSetPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.TpSlotGetAttrBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.TpSlotGetAttrPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.TpSlotHashBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.TpSlotInquiryBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpSlotIterNextBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.TpSlotLenBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.TpSlotMpAssSubscriptBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.TpSlotMpAssSubscriptPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotNbPower.TpSlotNbPowerBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.TpSlotRichCmpBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.TpSlotRichCmpPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.TpSlotSetAttrBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.TpSlotSetAttrPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.TpSlotSizeArgFunBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.TpSlotSqAssItemBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.TpSlotSqAssItemPython;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.TpSlotSqContainsBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.TpSlotUnaryFuncBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.TpSlotNewBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.TpSlotVarargsBuiltin;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
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
import com.oracle.truffle.api.object.DynamicObjectLibrary;
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
 *      - initialization of the wrappers: context initialization calls {@link #addOperatorsToBuiltin(Python3Core, PythonBuiltinClassType, PythonBuiltinClass)}
 *      - all the slots are static and shared per JVM, builtins do not allow to update attributes after ctx initialization
 *
 *     Native classes:
 *      - type_ready in typeobject.c calls these helpers in this order:
 *          - type_ready_graalpy_slot_conv: up-calls GraalPyPrivate_Type_AddSlot to create the Python wrappers for each slot
 *              - eventually will be replaced with one upcall to managed implementation of CPython's add_operators
 *                  that will use the {@link #SLOTDEFS} definitions and unsafe to read the slots
 *          - type_ready_inherit: does the slot inheritance in native
 *          - after type_ready_inherit: up-calls GraalPyPrivate_AddInheritedSlots to:
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
                TpSlot nb_index, //
                TpSlot nb_int, //
                TpSlot nb_float, //
                TpSlot nb_absolute, //
                TpSlot nb_positive, //
                TpSlot nb_negative, //
                TpSlot nb_invert, //
                TpSlot nb_add, //
                TpSlot nb_subtract, //
                TpSlot nb_multiply, //
                TpSlot nb_remainder, //
                TpSlot nb_lshift, //
                TpSlot nb_rshift, //
                TpSlot nb_and, //
                TpSlot nb_xor, //
                TpSlot nb_or, //
                TpSlot nb_floor_divide, //
                TpSlot nb_true_divide, //
                TpSlot nb_divmod, //
                TpSlot nb_matrix_multiply, //
                TpSlot nb_power, //
                TpSlot nb_inplace_add, //
                TpSlot nb_inplace_subtract, //
                TpSlot nb_inplace_multiply, //
                TpSlot nb_inplace_remainder, //
                TpSlot nb_inplace_lshift, //
                TpSlot nb_inplace_rshift, //
                TpSlot nb_inplace_and, //
                TpSlot nb_inplace_xor, //
                TpSlot nb_inplace_or, //
                TpSlot nb_inplace_floor_divide, //
                TpSlot nb_inplace_true_divide, //
                TpSlot nb_inplace_matrix_multiply, //
                TpSlot nb_inplace_power, //
                TpSlot sq_length, //
                TpSlot sq_item, //
                TpSlot sq_ass_item, //
                TpSlot sq_concat, //
                TpSlot sq_repeat, //
                TpSlot sq_inplace_concat, //
                TpSlot sq_inplace_repeat, //
                TpSlot sq_contains, //
                TpSlot mp_length, //
                TpSlot mp_subscript, //
                TpSlot mp_ass_subscript, //
                TpSlot combined_sq_mp_length, //
                TpSlot combined_mp_sq_length, //
                TpSlot tp_richcmp, //
                TpSlot tp_descr_get, //
                TpSlot tp_descr_set, //
                TpSlot tp_hash, //
                TpSlot tp_getattro, //
                TpSlot tp_getattr, //
                TpSlot combined_tp_getattro_getattr, //
                TpSlot tp_setattro, //
                TpSlot tp_setattr,
                TpSlot combined_tp_setattro_setattr,
                TpSlot tp_iter, //
                TpSlot tp_iternext, //
                TpSlot tp_repr, //
                TpSlot tp_str, //
                TpSlot tp_init, //
                TpSlot tp_new, //
                TpSlot tp_call, //
                TpSlot am_await, //
                TpSlot am_aiter, //
                TpSlot am_anext, //
                boolean has_as_number,
                boolean has_as_sequence,
                boolean has_as_mapping,
                boolean has_as_async) {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(TpSlot.class);

    // Force class initialization earlier
    private static final TpSlot NEXT_NOT_IMPLEMENTED = TpSlotIterNext.NEXT_NOT_IMPLEMENTED;

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
        AS_NUMBER(TpSlots::has_as_number, CFields.PyTypeObject__tp_as_number),
        AS_SEQUENCE(TpSlots::has_as_sequence, CFields.PyTypeObject__tp_as_sequence),
        AS_MAPPING(TpSlots::has_as_mapping, CFields.PyTypeObject__tp_as_mapping),
        AS_ASYNC(TpSlots::has_as_async, CFields.PyTypeObject__tp_as_async),
        NO_GROUP(null, null); // Must be last

        public static final TpSlotGroup[] VALID_VALUES = Arrays.copyOf(values(), values().length - 1);

        private final GroupGetter getter;
        private final CFields cField;

        TpSlotGroup(GroupGetter getter, CFields cField) {
            this.getter = getter;
            this.cField = cField;
        }

        public boolean getValue(TpSlots slots) {
            assert this != NO_GROUP;
            return getter.get(slots);
        }

        public boolean readFromNative(PythonAbstractNativeObject pythonClass) {
            Object ptr = ReadPointerNode.getUncached().readFromObj(pythonClass, cField);
            return !InteropLibrary.getUncached().isNull(ptr);
        }
    }

    @FunctionalInterface
    interface GroupGetter {
        boolean get(TpSlots slot);
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
        NB_INDEX(
                        TpSlots::nb_index,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_index,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_INT(
                        TpSlots::nb_int,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_int,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_FLOAT(
                        TpSlots::nb_float,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_float,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_ABSOLUTE(
                        TpSlots::nb_absolute,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_absolute,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_POSITIVE(
                        TpSlots::nb_positive,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_positive,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_NEGATIVE(
                        TpSlots::nb_negative,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_negative,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_INVERT(
                        TpSlots::nb_invert,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_invert,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        NB_ADD(
                        TpSlots::nb_add,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_add,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createAdd),
        NB_SUBTRACT(
                        TpSlots::nb_subtract,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_subtract,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createSubtract),
        NB_MULTIPLY(
                        TpSlots::nb_multiply,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_multiply,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createMultiply),
        NB_REMAINDER(
                        TpSlots::nb_remainder,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_remainder,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createRemainder),
        NB_LSHIFT(
                        TpSlots::nb_lshift,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_lshift,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createLShift),
        NB_RSHIFT(
                        TpSlots::nb_rshift,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_rshift,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createRShift),
        NB_AND(
                        TpSlots::nb_and,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_and,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createAnd),
        NB_XOR(
                        TpSlots::nb_xor,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_xor,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createXor),
        NB_OR(
                        TpSlots::nb_or,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_or,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createOr),
        NB_FLOOR_DIVIDE(
                        TpSlots::nb_floor_divide,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_floor_divide,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createFloorDivide),
        NB_TRUE_DIVIDE(
                        TpSlots::nb_true_divide,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_true_divide,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createTrueDivide),
        NB_DIVMOD(
                        TpSlots::nb_divmod,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_divmod,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createDivMod),
        NB_MATRIX_MULTIPLY(
                        TpSlots::nb_matrix_multiply,
                        TpSlotReversiblePython.class,
                        TpSlotBinaryOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_matrix_multiply,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinaryOpSlotFuncWrapper::createMatrixMultiply),
        NB_POWER(
                        TpSlots::nb_power,
                        TpSlotReversiblePython.class,
                        TpSlotNbPowerBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_power,
                        PExternalFunctionWrapper.TERNARYFUNC,
                        NbPowerWrapper::new),
        NB_INPLACE_ADD(
                        TpSlots::nb_inplace_add,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_add,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_SUBTRACT(
                        TpSlots::nb_inplace_subtract,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_subtract,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_MULTIPLY(
                        TpSlots::nb_inplace_multiply,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_multiply,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_REMAINDER(
                        TpSlots::nb_inplace_remainder,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_remainder,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_LSHIFT(
                        TpSlots::nb_inplace_lshift,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_lshift,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_RSHIFT(
                        TpSlots::nb_inplace_rshift,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_rshift,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_AND(
                        TpSlots::nb_inplace_and,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_and,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_XOR(
                        TpSlots::nb_inplace_xor,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_xor,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_OR(
                        TpSlots::nb_inplace_or,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_or,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_FLOOR_DIVIDE(
                        TpSlots::nb_inplace_floor_divide,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_floor_divide,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_TRUE_DIVIDE(
                        TpSlots::nb_inplace_true_divide,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_true_divide,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_MATRIX_MULTIPLY(
                        TpSlots::nb_inplace_matrix_multiply,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_matrix_multiply,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        NB_INPLACE_POWER(
                        TpSlots::nb_inplace_power,
                        TpSlotPythonSingle.class,
                        null, // No builtin implementations
                        TpSlotGroup.AS_NUMBER,
                        CFields.PyNumberMethods__nb_inplace_power,
                        PExternalFunctionWrapper.TERNARYFUNC,
                        NbInPlacePowerWrapper::new),
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
        SQ_ASS_ITEM(
                        TpSlots::sq_ass_item,
                        TpSlotSqAssItemPython.class,
                        TpSlotSqAssItemBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_ass_item,
                        PExternalFunctionWrapper.SETITEM,
                        SsizeobjargprocWrapper::new),
        SQ_REPEAT(
                        TpSlots::sq_repeat,
                        TpSlotPythonSingle.class,
                        TpSlotSizeArgFunBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_repeat,
                        PExternalFunctionWrapper.SSIZE_ARG,
                        SsizeargfuncSlotWrapper::new),
        SQ_INPLACE_CONCAT(
                        TpSlots::sq_inplace_concat,
                        TpSlotPythonSingle.class,
                        TpSlotBinaryIOpBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_inplace_concat,
                        PExternalFunctionWrapper.BINARYFUNC,
                        BinarySlotFuncWrapper::new),
        SQ_INPLACE_REPEAT(
                        TpSlots::sq_inplace_repeat,
                        TpSlotPythonSingle.class,
                        TpSlotSizeArgFunBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_inplace_repeat,
                        PExternalFunctionWrapper.SSIZE_ARG,
                        SsizeargfuncSlotWrapper::new),
        SQ_CONTAINS(
                        TpSlots::sq_contains,
                        TpSlotPythonSingle.class,
                        TpSlotSqContainsBuiltin.class,
                        TpSlotGroup.AS_SEQUENCE,
                        CFields.PySequenceMethods__sq_contains,
                        PExternalFunctionWrapper.OBJOBJPROC,
                        SqContainsWrapper::new),
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
        MP_ASS_SUBSCRIPT(
                        TpSlots::mp_ass_subscript,
                        TpSlotMpAssSubscriptPython.class,
                        TpSlotMpAssSubscriptBuiltin.class,
                        TpSlotGroup.AS_MAPPING,
                        CFields.PyMappingMethods__mp_ass_subscript,
                        PExternalFunctionWrapper.OBJOBJARGPROC,
                        ObjobjargWrapper::new),
        TP_RICHCOMPARE(
                        TpSlots::tp_richcmp,
                        TpSlotRichCmpPython.class,
                        TpSlotRichCmpBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_richcompare,
                        PExternalFunctionWrapper.RICHCMP,
                        RichcmpFunctionWrapper::new),
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
        TP_HASH(
                        TpSlots::tp_hash,
                        TpSlotPythonSingle.class,
                        TpSlotHashBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_hash,
                        PExternalFunctionWrapper.HASHFUNC,
                        HashfuncWrapper::new),
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
                        new NativeWrapperFactory.ShouldNotReach("tp_setattr")),
        TP_ITER(
                        TpSlots::tp_iter,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_iter,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        TP_ITERNEXT(
                        TpSlots::tp_iternext,
                        TpSlotPythonSingle.class,
                        TpSlotIterNextBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_iternext,
                        PExternalFunctionWrapper.ITERNEXT,
                        IterNextWrapper::new),
        TP_REPR(
                        TpSlots::tp_repr,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_repr,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        TP_STR(
                        TpSlots::tp_str,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_str,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        TP_INIT(
                        TpSlots::tp_init,
                        TpSlotPythonSingle.class,
                        TpSlotVarargsBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_init,
                        PExternalFunctionWrapper.INITPROC,
                        InitWrapper::new),
        TP_NEW(
                        TpSlots::tp_new,
                        TpSlotPythonSingle.class,
                        TpSlotNewBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_new,
                        PExternalFunctionWrapper.NEW,
                        NewWrapper::new),
        TP_CALL(
                        TpSlots::tp_call,
                        TpSlotPythonSingle.class,
                        TpSlotVarargsBuiltin.class,
                        TpSlotGroup.NO_GROUP,
                        CFields.PyTypeObject__tp_call,
                        PExternalFunctionWrapper.CALL,
                        CallWrapper::new),
        AM_AWAIT(
                        TpSlots::am_await,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_ASYNC,
                        CFields.PyAsyncMethods__am_await,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        AM_AITER(
                        TpSlots::am_aiter,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_ASYNC,
                        CFields.PyAsyncMethods__am_aiter,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new),
        AM_ANEXT(
                        TpSlots::am_anext,
                        TpSlotPythonSingle.class,
                        TpSlotUnaryFuncBuiltin.class,
                        TpSlotGroup.AS_ASYNC,
                        CFields.PyAsyncMethods__am_anext,
                        PExternalFunctionWrapper.UNARYFUNC,
                        UnaryFuncWrapper::new);

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
                    PExternalFunctionWrapper wrapper) {
        public static TpSlotDef create(TruffleString name, PythonFunctionFactory functionFactory, PExternalFunctionWrapper wrapper) {
            return new TpSlotDef(name, functionFactory, wrapper);
        }

        public static TpSlotDef withSimpleFunction(TruffleString name, PExternalFunctionWrapper wrapper) {
            return new TpSlotDef(name, SimplePythonWrapper.INSTANCE, wrapper);
        }

        public static TpSlotDef withNoFunctionNoWrapper(TruffleString name) {
            return new TpSlotDef(name, null, null);
        }

        public static TpSlotDef withNoFunction(TruffleString name, PExternalFunctionWrapper wrapper) {
            return new TpSlotDef(name, null, wrapper);
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
        addSlotDef(s, TpSlotMeta.TP_HASH, TpSlotDef.withSimpleFunction(T___HASH__, PExternalFunctionWrapper.HASHFUNC));
        addSlotDef(s, TpSlotMeta.TP_GETATTRO,
                        TpSlotDef.create(T___GETATTRIBUTE__, TpSlotGetAttrPython::create, PExternalFunctionWrapper.BINARYFUNC),
                        TpSlotDef.create(T___GETATTR__, TpSlotGetAttrPython::create, null));
        addSlotDef(s, TpSlotMeta.TP_SETATTRO,
                        TpSlotDef.create(T___SETATTR__, TpSlotSetAttrPython::create, PExternalFunctionWrapper.SETATTRO),
                        TpSlotDef.create(T___DELATTR__, TpSlotSetAttrPython::create, PExternalFunctionWrapper.DELATTRO));
        addSlotDef(s, TpSlotMeta.TP_RICHCOMPARE,
                        TpSlotDef.create(T___LT__, TpSlotRichCmpPython::create, PExternalFunctionWrapper.LT),
                        TpSlotDef.create(T___LE__, TpSlotRichCmpPython::create, PExternalFunctionWrapper.LE),
                        TpSlotDef.create(T___EQ__, TpSlotRichCmpPython::create, PExternalFunctionWrapper.EQ),
                        TpSlotDef.create(T___NE__, TpSlotRichCmpPython::create, PExternalFunctionWrapper.NE),
                        TpSlotDef.create(T___GT__, TpSlotRichCmpPython::create, PExternalFunctionWrapper.GT),
                        TpSlotDef.create(T___GE__, TpSlotRichCmpPython::create, PExternalFunctionWrapper.GE));
        addSlotDef(s, TpSlotMeta.TP_DESCR_GET, TpSlotDef.withSimpleFunction(T___GET__, PExternalFunctionWrapper.DESCR_GET));
        addSlotDef(s, TpSlotMeta.TP_DESCR_SET, //
                        TpSlotDef.create(T___SET__, TpSlotDescrSetPython::create, PExternalFunctionWrapper.DESCR_SET), //
                        TpSlotDef.create(T___DELETE__, TpSlotDescrSetPython::create, PExternalFunctionWrapper.DESCR_DELETE));
        addSlotDef(s, TpSlotMeta.TP_ITER, TpSlotDef.withSimpleFunction(T___ITER__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.TP_ITERNEXT, TpSlotDef.withSimpleFunction(T___NEXT__, PExternalFunctionWrapper.ITERNEXT));
        addSlotDef(s, TpSlotMeta.TP_STR, TpSlotDef.withSimpleFunction(T___STR__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.TP_REPR, TpSlotDef.withSimpleFunction(T___REPR__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.TP_INIT, TpSlotDef.withSimpleFunction(T___INIT__, PExternalFunctionWrapper.INITPROC));
        addSlotDef(s, TpSlotMeta.TP_NEW, TpSlotDef.withSimpleFunction(T___NEW__, PExternalFunctionWrapper.NEW));
        addSlotDef(s, TpSlotMeta.TP_CALL, TpSlotDef.withSimpleFunction(T___CALL__, PExternalFunctionWrapper.CALL));
        addSlotDef(s, TpSlotMeta.NB_ADD,
                        TpSlotDef.create(T___ADD__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RADD__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_SUBTRACT,
                        TpSlotDef.create(T___SUB__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RSUB__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_MULTIPLY,
                        TpSlotDef.create(T___MUL__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RMUL__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_REMAINDER,
                        TpSlotDef.create(T___MOD__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RMOD__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_LSHIFT,
                        TpSlotDef.create(T___LSHIFT__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RLSHIFT__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_RSHIFT,
                        TpSlotDef.create(T___RSHIFT__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RRSHIFT__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_AND,
                        TpSlotDef.create(T___AND__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RAND__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_XOR,
                        TpSlotDef.create(T___XOR__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RXOR__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_OR,
                        TpSlotDef.create(T___OR__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___ROR__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_FLOOR_DIVIDE,
                        TpSlotDef.create(T___FLOORDIV__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RFLOORDIV__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_TRUE_DIVIDE,
                        TpSlotDef.create(T___TRUEDIV__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RTRUEDIV__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_DIVMOD,
                        TpSlotDef.create(T___DIVMOD__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RDIVMOD__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_MATRIX_MULTIPLY,
                        TpSlotDef.create(T___MATMUL__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_L),
                        TpSlotDef.create(T___RMATMUL__, TpSlotReversiblePython::create, PExternalFunctionWrapper.BINARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_POWER,
                        TpSlotDef.create(T___POW__, TpSlotReversiblePython::create, PExternalFunctionWrapper.TERNARYFUNC),
                        TpSlotDef.create(T___RPOW__, TpSlotReversiblePython::create, PExternalFunctionWrapper.TERNARYFUNC_R));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_ADD, TpSlotDef.withSimpleFunction(T___IADD__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_SUBTRACT, TpSlotDef.withSimpleFunction(T___ISUB__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_MULTIPLY, TpSlotDef.withSimpleFunction(T___IMUL__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_REMAINDER, TpSlotDef.withSimpleFunction(T___IMOD__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_LSHIFT, TpSlotDef.withSimpleFunction(T___ILSHIFT__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_RSHIFT, TpSlotDef.withSimpleFunction(T___IRSHIFT__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_AND, TpSlotDef.withSimpleFunction(T___IAND__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_XOR, TpSlotDef.withSimpleFunction(T___IXOR__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_OR, TpSlotDef.withSimpleFunction(T___IOR__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_FLOOR_DIVIDE, TpSlotDef.withSimpleFunction(T___IFLOORDIV__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_TRUE_DIVIDE, TpSlotDef.withSimpleFunction(T___ITRUEDIV__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_MATRIX_MULTIPLY, TpSlotDef.withSimpleFunction(T___IMATMUL__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INPLACE_POWER, TpSlotDef.withSimpleFunction(T___IPOW__, PExternalFunctionWrapper.TERNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_BOOL, TpSlotDef.withSimpleFunction(T___BOOL__, PExternalFunctionWrapper.INQUIRY));
        addSlotDef(s, TpSlotMeta.NB_INDEX, TpSlotDef.withSimpleFunction(T___INDEX__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INT, TpSlotDef.withSimpleFunction(T___INT__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_FLOAT, TpSlotDef.withSimpleFunction(T___FLOAT__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_ABSOLUTE, TpSlotDef.withSimpleFunction(T___ABS__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_POSITIVE, TpSlotDef.withSimpleFunction(T___POS__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_NEGATIVE, TpSlotDef.withSimpleFunction(T___NEG__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.NB_INVERT, TpSlotDef.withSimpleFunction(T___INVERT__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.MP_LENGTH, TpSlotDef.withSimpleFunction(T___LEN__, PExternalFunctionWrapper.LENFUNC));
        addSlotDef(s, TpSlotMeta.MP_SUBSCRIPT, TpSlotDef.withSimpleFunction(T___GETITEM__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.MP_ASS_SUBSCRIPT,
                        TpSlotDef.create(T___SETITEM__, TpSlotMpAssSubscriptPython::create, PExternalFunctionWrapper.OBJOBJARGPROC),
                        TpSlotDef.create(T___DELITEM__, TpSlotMpAssSubscriptPython::create, PExternalFunctionWrapper.MP_DELITEM));
        addSlotDef(s, TpSlotMeta.SQ_LENGTH, TpSlotDef.withSimpleFunction(T___LEN__, PExternalFunctionWrapper.LENFUNC));
        // sq_concat does not have a slotdef for __radd__ unlike sq_repeat. This have consequences
        // w.r.t. inheritance from native classes, where sq_repeat is not overridden by __mul__.
        // Makes one wonder whether this CPython behavior is intended.
        // see test_sq_repeat_mul_without_rmul_inheritance
        addSlotDef(s, TpSlotMeta.SQ_CONCAT, TpSlotDef.withNoFunction(T___ADD__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.SQ_REPEAT,
                        TpSlotDef.withNoFunction(T___MUL__, PExternalFunctionWrapper.SSIZE_ARG),
                        TpSlotDef.withNoFunction(T___RMUL__, PExternalFunctionWrapper.SSIZE_ARG));
        addSlotDef(s, TpSlotMeta.SQ_ITEM, TpSlotDef.withSimpleFunction(T___GETITEM__, PExternalFunctionWrapper.GETITEM));
        addSlotDef(s, TpSlotMeta.SQ_ASS_ITEM,
                        TpSlotDef.create(T___SETITEM__, TpSlotSqAssItemPython::create, PExternalFunctionWrapper.SETITEM),
                        TpSlotDef.create(T___DELITEM__, TpSlotSqAssItemPython::create, PExternalFunctionWrapper.DELITEM));
        addSlotDef(s, TpSlotMeta.SQ_INPLACE_CONCAT, TpSlotDef.withNoFunction(T___IADD__, PExternalFunctionWrapper.BINARYFUNC));
        addSlotDef(s, TpSlotMeta.SQ_INPLACE_REPEAT, TpSlotDef.withNoFunction(T___IMUL__, PExternalFunctionWrapper.SSIZE_ARG));
        addSlotDef(s, TpSlotMeta.SQ_CONTAINS, TpSlotDef.withSimpleFunction(T___CONTAINS__, PExternalFunctionWrapper.OBJOBJPROC));
        addSlotDef(s, TpSlotMeta.AM_AWAIT, TpSlotDef.withSimpleFunction(T___AWAIT__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.AM_ANEXT, TpSlotDef.withSimpleFunction(T___ANEXT__, PExternalFunctionWrapper.UNARYFUNC));
        addSlotDef(s, TpSlotMeta.AM_AITER, TpSlotDef.withSimpleFunction(T___AITER__, PExternalFunctionWrapper.UNARYFUNC));

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
        for (TpSlotGroup group : TpSlotGroup.VALID_VALUES) {
            if (group.readFromNative(pythonClass)) {
                builder.setExplicitGroup(group);
            }
        }
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
                    long fieldPointer = interop.asPointer(field);
                    Object executable = ctx.getCApiContext().getClosureExecutable(fieldPointer);
                    if (executable instanceof TpSlotWrapper execWrapper) {
                        existingSlotWrapper = execWrapper;
                    } else if (executable != null) {
                        // This can happen for legacy slots where the delegate would be a PFunction
                        LOGGER.fine(() -> String.format("Unexpected executable for slot pointer: %s", executable));
                    } else if (def == TpSlotMeta.TP_HASH) {
                        // If the slot is not tp_iternext, but the value is
                        // PyObject_HashNotImplemented, we still assign it to the slot as wrapped
                        // native executable later on
                        if (CApiContext.isIdenticalToSymbol(fieldPointer, NativeCAPISymbol.FUN_PYOBJECT_HASH_NOT_IMPLEMENTED)) {
                            builder.set(def, TpSlotHashFun.HASH_NOT_IMPLEMENTED);
                            continue;
                        }
                    } else if (def == TpSlotMeta.TP_ITERNEXT) {
                        if (CApiContext.isIdenticalToSymbol(fieldPointer, NativeCAPISymbol.FUN_PY_OBJECT_NEXT_NOT_IMPLEMENTED)) {
                            builder.set(def, TpSlotIterNext.NEXT_NOT_IMPLEMENTED);
                            continue;
                        }
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
            Object executable = EnsureExecutableNode.executeUncached(field, def.nativeSignature);
            builder.set(def, TpSlotNative.createCExtSlot(executable));
        }
        return builder.build();
    }

    @TruffleBoundary
    public static void addOperatorsToNative(PythonAbstractNativeObject type) {
        PythonContext context = PythonContext.get(null);
        PythonLanguage language = context.getLanguage();
        TpSlots slots = GetTpSlotsNode.executeUncached(type);
        Object base = TypeNodes.GetBaseClassNode.executeUncached(type);
        TpSlots baseSlots = GetTpSlotsNode.executeUncached(base);
        PDict dict = GetDictIfExistsNode.getUncached().execute(type);
        assert dict != null;
        for (Entry<TpSlotMeta, TpSlotDef[]> slotDefEntry : SLOTDEFS.entrySet()) {
            TpSlotMeta tpSlotMeta = slotDefEntry.getKey();
            for (TpSlotDef tpSlotDef : slotDefEntry.getValue()) {
                if (tpSlotDef.wrapper == null) {
                    continue;
                }
                TpSlot value = tpSlotMeta.getValue(slots);
                if (value == null) {
                    continue;
                }
                if (tpSlotMeta.getValue(baseSlots) == value) {
                    // Ignore inherited slots
                    continue;
                }
                Object existingValue = PyDictGetItem.executeUncached(dict, tpSlotDef.name);
                if (existingValue != null) {
                    continue;
                }
                Object wrapperDescriptor;
                if (value == TpSlotHashFun.HASH_NOT_IMPLEMENTED) {
                    wrapperDescriptor = PNone.NONE;
                } else if (value instanceof TpSlotBuiltin<?> builtinSlot) {
                    wrapperDescriptor = builtinSlot.createBuiltin(context, type, tpSlotDef.name, tpSlotDef.wrapper);
                } else if (value instanceof TpSlotNative nativeSlot) {
                    wrapperDescriptor = PExternalFunctionWrapper.createWrapperFunction(tpSlotDef.name, nativeSlot.getCallable(), type, 0, tpSlotDef.wrapper, language);
                } else if (value instanceof TpSlotPython) {
                    // There should already be a python method somewhere in the MRO or this doesn't
                    // make sense
                    continue;
                } else {
                    throw new IllegalStateException("addOperators: unexpected object in slot " + value);
                }
                PyDictSetItem.executeUncached(dict, tpSlotDef.name, wrapperDescriptor);
            }
        }
    }

    public static void toNative(Object ptrToWrite, TpSlotMeta def, TpSlot value, Object nullValue) {
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
    public static void inherit(PythonClass klass, PDict namespace, MroSequenceStorage mro, boolean allocateAllGroups) {
        Builder klassSlots = buildInherited(klass, null, mro, allocateAllGroups);
        klass.setTpSlots(klassSlots.build());
    }

    @TruffleBoundary
    public static TpSlots.Builder buildInherited(PythonClass klass, PDict namespace, MroSequenceStorage mro, boolean allocateAllGroups) {
        // partially implements CPython:type_ready_inherit
        // slots of native classes are initialized in GraalPyPrivate_AddInheritedSlots, they are
        // just a mirror of the native slots initialized and inherited on the native side
        assert klass.getTpSlots() == null;
        Builder klassSlots = newBuilder();
        if (allocateAllGroups) {
            klassSlots.allocateAllGroups();
        }
        if (klass.getBase() != null) {
            // tp_new is first inherited from tp_base in type_ready_set_new
            klassSlots.set(TpSlotMeta.TP_NEW, GetTpSlotsNode.executeUncached(klass.getBase()).tp_new());
        }
        for (int i = 0; i < mro.length(); i++) {
            PythonAbstractClass type = mro.getPythonClassItemNormalized(i);
            TpSlots slots = GetTpSlotsNode.executeUncached(type);
            assert slots != null || type == klass;
            if (slots != null) {
                klassSlots.inherit(klass, namespace, slots);
            }
        }
        return klassSlots;
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
    public static void fixupSlotDispatchers(PythonClass klass, TpSlots.Builder slots) {
        updateSlots(klass, slots, SLOTDEFS.entrySet());
    }

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
                Object descr = lookup.execute(klass, name);
                genericCallables[i] = descr;
                genericCallablesNames[i] = name;
                if (descr == PNone.NO_VALUE) {
                    if (slot == TpSlotMeta.TP_ITERNEXT) {
                        specific = NEXT_NOT_IMPLEMENTED;
                    }
                    continue;
                }
                // Is the value a builtin function (in CPython PyWrapperDescr_Type) that wraps a
                // builtin or native slot?
                if (descr instanceof PBuiltinFunction builtin && builtin.getSlot() != null) {
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
                } else if (slot == TpSlotMeta.TP_NEW && descr instanceof PBuiltinMethod method && method.getBuiltinFunction().getSlot() != null &&
                                !(slots.get(TpSlotMeta.TP_NEW) instanceof TpSlotPython)) {
                    /*
                     * From CPython: The __new__ wrapper is not a wrapper descriptor, so must be
                     * special-cased differently. If we don't do this, creating an instance will
                     * always use slot_tp_new which will look up __new__ in the MRO which will call
                     * tp_new_wrapper which will look through the base classes looking for a static
                     * base and call its tp_new (usually PyType_GenericNew), after performing
                     * various sanity checks and constructing a new argument list. Cut all that
                     * nonsense short -- this speeds up instance creation tremendously.
                     */
                    /*
                     * msimacek note: This optimization is not implemented correctly in CPython -
                     * it's missing a check that the method is for the same type. This can manifest
                     * with multiple-inheritance when the __new__ method inherited from the MRO is
                     * different from the current value in tp_new, which was populated from
                     * tp_base->tp_new earlier. If the one from tp_base is a wrapper, it will pass
                     * this check and stay in the slot, diverging from the __new__ wrapper inherited
                     * in MRO. This behavior is already relied upon in the wild (pandas) so we don't
                     * check the type either.
                     *
                     * The last part of the condition is GraalPy-specific because we cache the
                     * Python method in the slot. So we make it go to the generic path to make sure
                     * it creates a new wrapper for the right method.
                     *
                     * See test_tp_new_bug_to_bug_compatibility in cpyext/test_object.py
                     */
                    specific = slots.get(TpSlotMeta.TP_NEW);
                } else if (descr == PNone.NONE && slot == TpSlotMeta.TP_HASH) {
                    specific = TpSlotHashFun.HASH_NOT_IMPLEMENTED;
                } else {
                    useGeneric = true;
                    generic = defs[i].functionFactory;
                }
            }

            TpSlot newValue = null;
            if (specific != null && !useGeneric) {
                newValue = specific;
            } else if (generic != null) {
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

    public static void addOperatorsToBuiltin(Python3Core core, PythonBuiltinClassType type, PythonBuiltinClass pythonBuiltinClass) {
        TpSlots slots = type.getDeclaredSlots();
        assert checkNoMagicOverrides(core, type);

        // Similar to CPython:add_operators
        for (var slotDefGroup : SLOTDEFS.entrySet()) {
            TpSlotMeta slotMeta = slotDefGroup.getKey();
            TpSlot slotValue = slotMeta.getter.get(slots);
            if (slotMeta == TpSlotMeta.TP_HASH && slotValue == TpSlotHashFun.HASH_NOT_IMPLEMENTED) {
                DynamicObjectLibrary.getUncached().put(pythonBuiltinClass, T___HASH__, PNone.NONE);
                continue;
            }
            if (!(slotValue instanceof TpSlotBuiltin<?> builtinSlot)) {
                continue;
            }
            for (TpSlotDef slotDef : slotDefGroup.getValue()) {
                if (slotDef.wrapper() != null && pythonBuiltinClass.getAttribute(slotDef.name()) == PNone.NO_VALUE) {
                    var value = builtinSlot.createBuiltin(core, type, slotDef.name(), slotDef.wrapper());
                    pythonBuiltinClass.setAttribute(slotDef.name(), value);
                }
            }
        }
    }

    public Builder copy() {
        var result = new Builder();
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            result.set(def, def.getValue(this));
        }
        for (TpSlotGroup group : TpSlotGroup.VALID_VALUES) {
            if (group.getValue(this)) {
                result.setExplicitGroup(group);
            }
        }
        return result;
    }

    public static Builder newBuilder() {
        return new Builder();
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

        private Builder inherit(PythonClass klass, PDict namespace, TpSlots base) {
            // similar to CPython:inherit_slots
            // indirect slots (from tp_as_number etc.) are not inherited if the group is not
            // allocated explicitly. Note: native heap types and managed types have always all
            // groups allocated.
            for (TpSlotMeta def : TpSlotMeta.VALUES) {
                if (def == TpSlotMeta.TP_RICHCOMPARE || def == TpSlotMeta.TP_HASH ||
                                def == TpSlotMeta.TP_GETATTRO || def == TpSlotMeta.TP_GETATTR ||
                                def == TpSlotMeta.TP_SETATTRO || def == TpSlotMeta.TP_SETATTR) {
                    // handled manually below the loop
                    continue;
                }
                if (def.group != TpSlotGroup.NO_GROUP && !explicitGroups[def.group.ordinal()]) {
                    continue;
                }
                TpSlot current = values[def.ordinal()];
                TpSlot otherValue = def.getter.get(base);
                TpSlot newValue = current != null ? current : otherValue;
                set(def, newValue);
            }
            if (get(TpSlotMeta.TP_GETATTR) == null && get(TpSlotMeta.TP_GETATTRO) == null) {
                set(TpSlotMeta.TP_GETATTR, base.tp_getattr());
                set(TpSlotMeta.TP_GETATTRO, base.tp_getattro());
            }
            if (get(TpSlotMeta.TP_SETATTR) == null && get(TpSlotMeta.TP_SETATTRO) == null) {
                set(TpSlotMeta.TP_SETATTR, base.tp_setattr());
                set(TpSlotMeta.TP_SETATTRO, base.tp_setattro());
            }
            if (get(TpSlotMeta.TP_RICHCOMPARE) == null && get(TpSlotMeta.TP_HASH) == null) {
                if (!overridesHash(namespace)) {
                    set(TpSlotMeta.TP_RICHCOMPARE, base.tp_richcmp());
                    set(TpSlotMeta.TP_HASH, base.tp_hash());
                }
            }
            return this;
        }

        private static boolean overridesHash(PDict namespace) {
            if (namespace == null) {
                return false;
            }
            Object eq = HashingStorageGetItem.executeUncached(namespace.getDictStorage(), T___EQ__);
            if (eq == null) {
                Object hash = HashingStorageGetItem.executeUncached(namespace.getDictStorage(), T___HASH__);
                return hash != null;
            }
            return true;
        }

        private TpSlot fistNonNull(TpSlotMeta a, TpSlotMeta b) {
            return values[a.ordinal()] != null ? values[a.ordinal()] : values[b.ordinal()];
        }

        TpSlot get(TpSlotMeta s) {
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
                            get(TpSlotMeta.NB_INDEX), //
                            get(TpSlotMeta.NB_INT), //
                            get(TpSlotMeta.NB_FLOAT), //
                            get(TpSlotMeta.NB_ABSOLUTE), //
                            get(TpSlotMeta.NB_POSITIVE), //
                            get(TpSlotMeta.NB_NEGATIVE), //
                            get(TpSlotMeta.NB_INVERT), //
                            get(TpSlotMeta.NB_ADD), //
                            get(TpSlotMeta.NB_SUBTRACT), //
                            get(TpSlotMeta.NB_MULTIPLY), //
                            get(TpSlotMeta.NB_REMAINDER), //
                            get(TpSlotMeta.NB_LSHIFT), //
                            get(TpSlotMeta.NB_RSHIFT), //
                            get(TpSlotMeta.NB_AND), //
                            get(TpSlotMeta.NB_XOR), //
                            get(TpSlotMeta.NB_OR), //
                            get(TpSlotMeta.NB_FLOOR_DIVIDE), //
                            get(TpSlotMeta.NB_TRUE_DIVIDE), //
                            get(TpSlotMeta.NB_DIVMOD), //
                            get(TpSlotMeta.NB_MATRIX_MULTIPLY), //
                            get(TpSlotMeta.NB_POWER), //
                            get(TpSlotMeta.NB_INPLACE_ADD), //
                            get(TpSlotMeta.NB_INPLACE_SUBTRACT), //
                            get(TpSlotMeta.NB_INPLACE_MULTIPLY), //
                            get(TpSlotMeta.NB_INPLACE_REMAINDER), //
                            get(TpSlotMeta.NB_INPLACE_LSHIFT), //
                            get(TpSlotMeta.NB_INPLACE_RSHIFT), //
                            get(TpSlotMeta.NB_INPLACE_AND), //
                            get(TpSlotMeta.NB_INPLACE_XOR), //
                            get(TpSlotMeta.NB_INPLACE_OR), //
                            get(TpSlotMeta.NB_INPLACE_FLOOR_DIVIDE), //
                            get(TpSlotMeta.NB_INPLACE_TRUE_DIVIDE), //
                            get(TpSlotMeta.NB_INPLACE_MATRIX_MULTIPLY), //
                            get(TpSlotMeta.NB_INPLACE_POWER), //
                            get(TpSlotMeta.SQ_LENGTH), //
                            get(TpSlotMeta.SQ_ITEM), //
                            get(TpSlotMeta.SQ_ASS_ITEM), //
                            get(TpSlotMeta.SQ_CONCAT), //
                            get(TpSlotMeta.SQ_REPEAT), //
                            get(TpSlotMeta.SQ_INPLACE_CONCAT), //
                            get(TpSlotMeta.SQ_INPLACE_REPEAT), //
                            get(TpSlotMeta.SQ_CONTAINS), //
                            get(TpSlotMeta.MP_LENGTH), //
                            get(TpSlotMeta.MP_SUBSCRIPT), //
                            get(TpSlotMeta.MP_ASS_SUBSCRIPT), //
                            sq_mp_length, //
                            mp_sq_length, //
                            get(TpSlotMeta.TP_RICHCOMPARE), //
                            get(TpSlotMeta.TP_DESCR_GET), //
                            get(TpSlotMeta.TP_DESCR_SET), //
                            get(TpSlotMeta.TP_HASH), //
                            get(TpSlotMeta.TP_GETATTRO), //
                            get(TpSlotMeta.TP_GETATTR), //
                            tp_get_attro_attr,
                            get(TpSlotMeta.TP_SETATTRO),
                            get(TpSlotMeta.TP_SETATTR),
                            tp_set_attro_attr,
                            get(TpSlotMeta.TP_ITER), //
                            get(TpSlotMeta.TP_ITERNEXT), //
                            get(TpSlotMeta.TP_REPR), //
                            get(TpSlotMeta.TP_STR), //
                            get(TpSlotMeta.TP_INIT), //
                            get(TpSlotMeta.TP_NEW), //
                            get(TpSlotMeta.TP_CALL), //
                            get(TpSlotMeta.AM_AWAIT), //
                            get(TpSlotMeta.AM_AITER), //
                            get(TpSlotMeta.AM_ANEXT), //
                            hasGroup(TpSlotGroup.AS_NUMBER),
                            hasGroup(TpSlotGroup.AS_SEQUENCE),
                            hasGroup(TpSlotGroup.AS_MAPPING),
                            hasGroup(TpSlotGroup.AS_ASYNC));
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

        public static TpSlots executeUncached(Object pythonObject) {
            return GetObjectSlotsNodeGen.getUncached().execute(null, pythonObject);
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
