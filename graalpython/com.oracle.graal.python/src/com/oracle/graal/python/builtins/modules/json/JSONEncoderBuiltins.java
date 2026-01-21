/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins.RECURSION_LIMIT;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyFloatObject__ob_fval;
import static com.oracle.graal.python.nodes.PGuards.isDouble;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.PGuards.isPFloat;
import static com.oracle.graal.python.nodes.PGuards.isPInt;
import static com.oracle.graal.python.nodes.PGuards.isString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.truffle.api.CompilerDirectives.UNLIKELY_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.castExact;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONEncoderBuiltinsClinicProviders.MakeEncoderClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListSortNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyListCheckExactNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@CoreFunctions(extendClasses = PythonBuiltinClassType.JSONEncoder)
public final class JSONEncoderBuiltins extends PythonBuiltins {

    private static final TruffleString T_NULL = tsLiteral("null");
    private static final TruffleString T_TRUE = tsLiteral("true");
    private static final TruffleString T_FALSE = tsLiteral("false");
    private static final TruffleString T_POSITIVE_INFINITY = tsLiteral("Infinity");
    private static final TruffleString T_NEGATIVE_INFINITY = tsLiteral("-Infinity");
    private static final TruffleString T_NAN = tsLiteral("NaN");

    private static final byte STATE_INITIAL = 0;
    private static final byte STATE_BUILTIN_LIST = 1;
    private static final byte STATE_BUILTIN_DICT = 2;
    private static final byte STATE_GENERIC_LIST = 3;
    private static final byte STATE_GENERIC_DICT = 4;
    private static final byte STATE_DEFAULT_FN = 5;

    public static final TpSlots SLOTS = JSONEncoderBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONEncoderBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "make_encoder", minNumOfPositionalArgs = 10, //
                    parameterNames = {"$cls", "markers", "default", "encoder", "indent", "key_separator", "item_separator", "sort_keys", "skipkeys", "allow_nan"})
    @ArgumentClinic(name = "key_separator", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "item_separator", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "sort_keys", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @ArgumentClinic(name = "skipkeys", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @ArgumentClinic(name = "allow_nan", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    public abstract static class MakeEncoder extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MakeEncoderClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        PJSONEncoder doNew(Object cls, Object markers, Object defaultFn, Object encoder, Object indent, TruffleString keySeparator, TruffleString itemSeparator, boolean sortKeys,
                        boolean skipKeys, boolean allowNan) {
            if (markers != PNone.NONE && !(markers instanceof PDict)) {
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.MAKE_ENCODER_ARG_1_MUST_BE_DICT, markers);
            }

            PJSONEncoder.FastEncode fastEncode = PJSONEncoder.FastEncode.None;
            Object encoderAsFun = encoder;
            if (encoder instanceof PBuiltinMethod encoderMethod) {
                encoderAsFun = encoderMethod.getFunction();
            }
            if (encoderAsFun instanceof PBuiltinFunction function) {
                Class<? extends PythonBuiltinBaseNode> nodeClass = function.getNodeClass();
                if (nodeClass != null) {
                    if (JSONModuleBuiltins.EncodeBaseString.class.isAssignableFrom(nodeClass)) {
                        fastEncode = PJSONEncoder.FastEncode.FastEncode;
                    } else if (JSONModuleBuiltins.EncodeBaseStringAscii.class.isAssignableFrom(nodeClass)) {
                        fastEncode = PJSONEncoder.FastEncode.FastEncodeAscii;
                    }
                }
            }
            return PFactory.createJSONEncoder(cls, TypeNodes.GetInstanceShape.executeUncached(cls),
                            markers, defaultFn, encoder, indent, keySeparator, itemSeparator, sortKeys, skipKeys, allowNan, fastEncode);
        }
    }

    @Slot(value = SlotKind.tp_call, isComplex = true)
    @SlotSignature(name = "_iterencode", minNumOfPositionalArgs = 1, parameterNames = {"$self", "obj", "_current_indent_level"})
    @ArgumentClinic(name = "_current_indent_level", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CallEncoderNode extends PythonTernaryClinicBuiltinNode {
        @Child private LookupAndCallUnaryNode callGetItems = LookupAndCallUnaryNode.create(SpecialMethodNames.T_ITEMS);
        @Child private PyObjectGetIter callGetDictIter = PyObjectGetIter.create();
        @Child private PyObjectGetIter callGetListIter = PyObjectGetIter.create();
        @Child private ListSortNode sortList = ListSortNode.create();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONEncoderBuiltinsClinicProviders.CallEncoderNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple call(VirtualFrame frame, PJSONEncoder self, Object obj, @SuppressWarnings("unused") int indent,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedBranchProfile genericListProfile,
                        @Cached InlinedBranchProfile genericDictProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PyTupleCheckExactNode pyTupleCheckExactNode,
                        @Cached PyListCheckExactNode pyListCheckExactNode,
                        @Cached ConstructListNode constructListNode,
                        @Cached PyIterNextNode pyIterNextNode,
                        @Cached CallUnaryMethodNode callDefaultFn,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarCustomNode,
                        @Cached HashingStorageGetIterator hashingStorageGetIterator,
                        @Cached HashingStorageIteratorNext hashingStorageIteratorNext,
                        @Cached HashingStorageIteratorKey hashingStorageIteratorKey,
                        @Cached HashingStorageIteratorValue hashingStorageIteratorValue,
                        @Cached AppendSimpleObjectNode appendSimpleObjectNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilderUTF32 builder = PythonUtils.createStringBuilder();
            ArrayBuilder<StackEntry> stack = new ArrayBuilder<>(8);
            Object key = null;
            Object value = obj;
            byte state = STATE_INITIAL;
            boolean first = true;
            Object parent = null;
            SequenceStorage builtinListStorage = null;
            HashingStorage builtinDictStorage = null;
            HashingStorageIterator builtinDictIterator = null;
            Object genericIterator = null;
            boolean checkCircles = self.markers != PNone.NONE;
            PJSONEncoder.FastEncode fastEncode = self.fastEncode;
            outer: while (true) {
                boolean skip = false;
                if (state != STATE_INITIAL && state != STATE_DEFAULT_FN && !first) {
                    appendStringNode.execute(builder, self.itemSeparator);
                }
                if (state == STATE_BUILTIN_DICT || state == STATE_GENERIC_DICT) {
                    boolean isString = isString(key);
                    if (isString || isSimpleObj(key, inliningTarget, getClassNode, isSubtypeNode)) {
                        if (!isString) {
                            appendCodePointNode.execute(builder, '"');
                        }
                        appendSimpleObjectNode.execute(frame, self, fastEncode, builder, key);
                        if (!isString) {
                            appendCodePointNode.execute(builder, '"');
                        }
                    } else {
                        if (self.skipKeys) {
                            skip = true;
                        } else {
                            errorProfile.enter(inliningTarget);
                            throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.KEYS_MUST_BE_STR_INT___NOT_P, key);
                        }
                    }
                    if (!skip) {
                        appendStringNode.execute(builder, self.keySeparator);
                    }
                }
                if (!skip) {
                    if (appendSimpleObjectNode.execute(frame, self, fastEncode, builder, value)) {
                        first = false;
                        // done
                    } else {
                        // startRecursion(self, value);
                        if (checkCircles) {
                            for (int i = 0; i < stack.size(); i++) {
                                if (stack.get(i).obj == value) {
                                    errorProfile.enter(inliningTarget);
                                    throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.CIRCULAR_REFERENCE_DETECTED);
                                }
                            }
                        }
                        parent = value;
                        final Object stackIterator;
                        final Object stackStorage;
                        if (value instanceof PList || value instanceof PTuple) {
                            PSequence list = (PSequence) value;
                            appendCodePointNode.execute(builder, '[');
                            first = true;
                            if (pyTupleCheckExactNode.execute(inliningTarget, list) || pyListCheckExactNode.execute(inliningTarget, list)) {
                                state = STATE_BUILTIN_LIST;
                                builtinListStorage = list.getSequenceStorage();
                                stackStorage = builtinListStorage;
                                stackIterator = null;
                            } else {
                                genericListProfile.enter(inliningTarget);
                                state = STATE_GENERIC_LIST;
                                genericIterator = callGetListIter.executeCached(frame, list);
                                stackStorage = null;
                                stackIterator = genericIterator;
                            }
                        } else if (value instanceof PDict dict) {
                            appendCodePointNode.execute(builder, '{');
                            first = true;
                            if (!self.sortKeys && PGuards.isBuiltinDict(dict)) {
                                state = STATE_BUILTIN_DICT;
                                builtinDictStorage = dict.getDictStorage();
                                builtinDictIterator = hashingStorageGetIterator.execute(inliningTarget, builtinDictStorage);
                                stackStorage = builtinDictStorage;
                                stackIterator = builtinDictIterator;
                            } else {
                                genericDictProfile.enter(inliningTarget);
                                state = STATE_GENERIC_DICT;
                                PList items = constructListNode.execute(frame, callGetItems.executeObject(frame, dict));
                                if (self.sortKeys) {
                                    sortList.execute(frame, items);
                                }
                                genericIterator = callGetDictIter.executeCached(frame, items);
                                stackStorage = null;
                                stackIterator = genericIterator;
                            }
                        } else {
                            state = STATE_DEFAULT_FN;
                            stackStorage = null;
                            stackIterator = null;
                        }
                        if (stack.size() > RECURSION_LIMIT) {
                            errorProfile.enter(inliningTarget);
                            throw JSONScannerBuiltins.recursionError(this, language);
                        }
                        stack.add(new StackEntry(state, parent, stackStorage, stackIterator, 0));
                    }
                }
                while (true) {
                    switch (state) {
                        case STATE_INITIAL -> {
                            break outer;
                        }
                        case STATE_BUILTIN_LIST -> {
                            if (stack.peek().index < builtinListStorage.length()) {
                                value = getItemScalarNode.execute(inliningTarget, builtinListStorage, stack.peek().index++);
                                continue outer;
                            }
                            appendCodePointNode.execute(builder, ']');
                        }
                        case STATE_BUILTIN_DICT -> {
                            if (hashingStorageIteratorNext.execute(inliningTarget, builtinDictStorage, builtinDictIterator)) {
                                key = hashingStorageIteratorKey.execute(inliningTarget, builtinDictStorage, builtinDictIterator);
                                value = hashingStorageIteratorValue.execute(inliningTarget, builtinDictStorage, builtinDictIterator);
                                continue outer;
                            } else {
                                appendCodePointNode.execute(builder, '}');
                            }
                        }
                        case STATE_GENERIC_LIST, STATE_GENERIC_DICT -> {
                            try {
                                genericListProfile.enter(inliningTarget);
                                Object item = pyIterNextNode.execute(frame, inliningTarget, genericIterator);
                                if (state == STATE_GENERIC_DICT) {
                                    genericDictProfile.enter(inliningTarget);
                                    if (!(item instanceof PTuple itemTuple) || itemTuple.getSequenceStorage().length() != 2) {
                                        errorProfile.enter(inliningTarget);
                                        throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.ITEMS_MUST_RETURN_2_TUPLES);
                                    }
                                    SequenceStorage sequenceStorage = itemTuple.getSequenceStorage();
                                    key = getItemScalarCustomNode.execute(inliningTarget, sequenceStorage, 0);
                                    value = getItemScalarCustomNode.execute(inliningTarget, sequenceStorage, 1);
                                } else {
                                    value = item;
                                }
                            } catch (IteratorExhausted e) {
                                appendCodePointNode.execute(builder, state == STATE_GENERIC_LIST ? ']' : '}');
                                break;
                            }
                            continue outer;
                        }
                        case STATE_DEFAULT_FN -> {
                            if (stack.peek().index == 0) {
                                value = callDefaultFn.executeObject(self.defaultFn, parent);
                                stack.peek().index = 1;
                                continue outer;
                            }
                        }
                    }
                    first = false;
                    // current list or dict is exhausted, pop parent from stack
                    // endRecursion(self, parent);
                    stack.pop();
                    if (stack.isEmpty()) {
                        break outer;
                    }
                    StackEntry top = stack.peek();
                    state = top.state;
                    parent = top.obj;
                    switch (state) {
                        case STATE_BUILTIN_LIST -> {
                            builtinListStorage = (SequenceStorage) top.storage;
                        }
                        case STATE_BUILTIN_DICT -> {
                            builtinDictStorage = (HashingStorage) top.storage;
                            builtinDictIterator = (HashingStorageIterator) top.iterator;
                        }
                        case STATE_GENERIC_LIST, STATE_GENERIC_DICT -> {
                            genericIterator = top.iterator;
                        }
                        case STATE_DEFAULT_FN -> {
                        }
                    }
                }
            }
            return PFactory.createTuple(language, new Object[]{toStringNode.execute(builder)});
        }

        private static final class StackEntry {
            private final byte state;
            private final Object obj;
            private final Object storage;
            private final Object iterator;
            private int index;

            private StackEntry(byte state, Object obj, Object storage, Object iterator, int index) {
                this.state = state;
                this.obj = obj;
                this.storage = storage;
                this.iterator = iterator;
                this.index = index;
            }
        }

        private static boolean isSimpleObj(Object obj,
                        Node inliningTarget,
                        GetClassNode getClassNode,
                        IsSubtypeNode isSubtypeNode) {
            if (obj == PNone.NONE || obj == Boolean.TRUE || obj == Boolean.FALSE || isString(obj) || isInteger(obj) || isPInt(obj) || obj instanceof Float || isDouble(obj) || isPFloat(obj)) {
                return true;
            } else if (CompilerDirectives.injectBranchProbability(UNLIKELY_PROBABILITY, obj instanceof PythonAbstractNativeObject)) {
                return isNativeStringOrFloat(inliningTarget, getClassNode, isSubtypeNode, (PythonAbstractNativeObject) obj);
            } else {
                return false;
            }
        }

        @InliningCutoff
        private static boolean isNativeStringOrFloat(Node inliningTarget, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode, PythonAbstractNativeObject nativeObj) {
            Object pyClass = getClassNode.execute(inliningTarget, nativeObj);
            return isSubtypeNode.execute(pyClass, PythonBuiltinClassType.PString) || isSubtypeNode.execute(pyClass, PythonBuiltinClassType.PFloat);
        }

        @GenerateInline(false)
        abstract static class AppendSimpleObjectNode extends Node {

            abstract boolean execute(VirtualFrame frame, PJSONEncoder encoder, PJSONEncoder.FastEncode fastEncode, TruffleStringBuilderUTF32 builder, Object obj);

            @Specialization
            static boolean appendSimpleObj(VirtualFrame frame, PJSONEncoder encoder, PJSONEncoder.FastEncode fastEncode, TruffleStringBuilderUTF32 builder, Object obj,
                            @Bind Node inliningTarget,
                            @Cached InlinedConditionProfile intProfile,
                            @Cached InlinedConditionProfile longProfile,
                            @Cached InlinedConditionProfile doubleProfile,
                            @Cached InlinedConditionProfile bigIntProfile,
                            @Cached InlinedConditionProfile numberStringProfile,
                            @Cached InlinedConditionProfile fastEncodeProfile,
                            @Cached InlinedBranchProfile customStringEncoderProfile,
                            @Cached InlinedBranchProfile errorProfile,
                            @Cached CallUnaryMethodNode customToStringCall,
                            @Cached GetClassNode getClassNode,
                            @Cached IsSubtypeNode isSubtypeNode,
                            @Cached CastToTruffleStringNode.ReadNativeStringNode readNativeStringNode,
                            @Cached CStructAccess.ReadDoubleNode readNativeDoubleNode,
                            @Cached CastToTruffleStringNode castToTruffleStringNode,
                            @Cached StringNodes.StringMaterializeNode stringMaterializeNode,
                            @Cached TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode1,
                            @Cached TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode2,
                            @Cached TruffleString.CodePointAtIndexUTF32Node codePointAtNode,
                            @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                            @Cached TruffleStringBuilder.AppendIntNumberNode appendIntNumberNode,
                            @Cached TruffleStringBuilder.AppendLongNumberNode appendLongNumberNode,
                            @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                            @Cached TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                            @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                            @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
                final TruffleString constString = constToString(obj);
                if (constString != null) {
                    appendStringNode.execute(builder, constString);
                    return true;
                }
                final TruffleString string;
                if (obj instanceof TruffleString tString) {
                    string = tString;
                } else if (obj instanceof PString pString) {
                    string = stringMaterializeNode.execute(inliningTarget, pString);
                } else if (obj instanceof PythonAbstractNativeObject nativeObj && isSubtypeNode.execute(getClassNode.execute(inliningTarget, nativeObj), PythonBuiltinClassType.PString)) {
                    string = readNativeStringNode.execute(nativeObj.getPtr());
                } else {
                    string = null;
                }

                if (string != null) {
                    if (fastEncode == PJSONEncoder.FastEncode.None) {
                        customStringEncoderProfile.enter(inliningTarget);
                        Object result = customToStringCall.executeObject(frame, encoder.encoder, string);
                        if (!isString(result)) {
                            errorProfile.enter(inliningTarget);
                            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.ENCODER_MUST_RETURN_STR, result);
                        }
                        appendStringNode.execute(builder, castToTruffleStringNode.execute(inliningTarget, result));
                    } else {
                        assert fastEncode == PJSONEncoder.FastEncode.FastEncode || fastEncode == PJSONEncoder.FastEncode.FastEncodeAscii;
                        JSONUtils.appendString(string, builder, fastEncodeProfile.profile(inliningTarget, fastEncode == PJSONEncoder.FastEncode.FastEncodeAscii),
                                        byteIndexOfCodePointSetNode1,
                                        byteIndexOfCodePointSetNode2,
                                        codePointAtNode,
                                        appendCodePointNode,
                                        appendStringNode,
                                        appendSubstringNode,
                                        fromByteArrayNode);
                    }
                    return true;
                }
                if (intProfile.profile(inliningTarget, obj instanceof Integer)) {
                    appendIntNumberNode.execute(builder, (int) obj);
                    return true;
                }
                if (longProfile.profile(inliningTarget, obj instanceof Long)) {
                    appendLongNumberNode.execute(builder, (long) obj);
                    return true;
                }
                final double doubleValue;
                final boolean isDouble;
                if (obj instanceof Float) {
                    doubleValue = (float) obj;
                    isDouble = true;
                } else if (obj instanceof Double) {
                    doubleValue = (double) obj;
                    isDouble = true;
                } else if (obj instanceof PFloat) {
                    doubleValue = ((PFloat) obj).asDouble();
                    isDouble = true;
                } else if (obj instanceof PythonAbstractNativeObject nativeObj && isSubtypeNode.execute(getClassNode.execute(inliningTarget, nativeObj), PythonBuiltinClassType.PFloat)) {
                    doubleValue = readNativeDoubleNode.readFromObj(nativeObj, PyFloatObject__ob_fval);
                    isDouble = true;
                } else {
                    doubleValue = 0;
                    isDouble = false;
                }
                final TruffleString numberString;
                if (doubleProfile.profile(inliningTarget, isDouble)) {
                    numberString = floatToString(inliningTarget, encoder, doubleValue, errorProfile);
                } else if (bigIntProfile.profile(inliningTarget, obj instanceof PInt)) {
                    numberString = fromJavaStringNode.execute(castExact(obj, PInt.class).toString(), TS_ENCODING);
                } else {
                    numberString = null;
                }
                if (numberStringProfile.profile(inliningTarget, numberString != null)) {
                    appendStringNode.execute(builder, numberString);
                    return true;
                }
                return false;
            }

            private static TruffleString constToString(Object obj) {
                if (obj == PNone.NONE) {
                    return T_NULL;
                } else if (obj == Boolean.TRUE) {
                    return T_TRUE;
                } else if (obj == Boolean.FALSE) {
                    return T_FALSE;
                } else {
                    return null;
                }
            }

            private static TruffleString floatToString(Node inliningTarget, PJSONEncoder encoder, double obj, InlinedBranchProfile errorProfile) {
                if (!Double.isFinite(obj)) {
                    if (!encoder.allowNan) {
                        errorProfile.enter(inliningTarget);
                        throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.OUT_OF_RANGE_FLOAT_NOT_JSON_COMPLIANT, PyObjectReprAsTruffleStringNode.executeUncached(obj));
                    }
                    if (obj > 0) {
                        return T_POSITIVE_INFINITY;
                    } else if (obj < 0) {
                        return T_NEGATIVE_INFINITY;
                    } else {
                        return T_NAN;
                    }
                } else {
                    return formatDouble(inliningTarget, obj);
                }
            }

            @TruffleBoundary
            private static TruffleString formatDouble(Node inliningTarget, double obj) {
                FloatFormatter f = new FloatFormatter(FloatBuiltins.StrNode.spec, inliningTarget);
                f.setMinFracDigits(1);
                return FloatBuiltins.StrNode.doFormat(obj, f);
            }
        }
    }
}
