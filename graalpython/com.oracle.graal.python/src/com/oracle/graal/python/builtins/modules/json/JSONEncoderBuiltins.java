/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.PGuards.isDouble;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.PGuards.isPFloat;
import static com.oracle.graal.python.nodes.PGuards.isPInt;
import static com.oracle.graal.python.nodes.PGuards.isString;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.truffle.api.CompilerDirectives.castExact;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONEncoderBuiltinsClinicProviders.MakeEncoderClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
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
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyListCheckExactNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
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
    private static final TruffleString T_BRACES = tsLiteral("{}");
    private static final TruffleString T_BRACKETS = tsLiteral("[]");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONEncoderBuiltinsFactory.getFactories();
    }

    @Builtin(name = "make_encoder", minNumOfPositionalArgs = 10, //
                    parameterNames = {"$cls", "markers", "default", "encoder", "indent", "key_separator", "item_separator", "sort_keys", "skipkeys", "allow_nan"}, //
                    constructsClass = PythonBuiltinClassType.JSONEncoder, //
                    doc = "JSON scanner object")
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
            return PFactory.createJSONEncoder(PythonLanguage.get(null), cls, TypeNodes.GetInstanceShape.executeUncached(cls),
                            markers, defaultFn, encoder, indent, keySeparator, itemSeparator, sortKeys, skipKeys, allowNan, fastEncode);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "obj", "_current_indent_level"})
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
        protected PTuple call(PJSONEncoder self, Object obj, @SuppressWarnings("unused") int indent,
                        @Bind PythonLanguage language) {
            return PFactory.createTuple(language, new Object[]{jsonEncode(self, obj)});
        }

        @TruffleBoundary
        private TruffleString jsonEncode(PJSONEncoder encoder, Object obj) {
            TruffleStringBuilderUTF32 builder = PythonUtils.createStringBuilder();
            appendListObj(encoder, builder, obj);
            return TruffleStringBuilder.ToStringNode.getUncached().execute(builder);
        }

        private void appendConst(TruffleStringBuilderUTF32 builder, Object obj) {
            if (obj == PNone.NONE) {
                builder.appendStringUncached(T_NULL);
            } else if (obj == Boolean.TRUE) {
                builder.appendStringUncached(T_TRUE);
            } else {
                assert obj == Boolean.FALSE;
                builder.appendStringUncached(T_FALSE);
            }
        }

        private void appendFloat(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, double obj) {
            if (!Double.isFinite(obj)) {
                if (!encoder.allowNan) {
                    throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.OUT_OF_RANGE_FLOAT_NOT_JSON_COMPLIANT);
                }
                if (obj > 0) {
                    builder.appendStringUncached(T_POSITIVE_INFINITY);
                } else if (obj < 0) {
                    builder.appendStringUncached(T_NEGATIVE_INFINITY);
                } else {
                    builder.appendStringUncached(T_NAN);
                }
            } else {
                builder.appendStringUncached(formatDouble(obj));
            }
        }

        private TruffleString formatDouble(double obj) {
            FloatFormatter f = new FloatFormatter(FloatBuiltins.StrNode.spec, this);
            f.setMinFracDigits(1);
            return FloatBuiltins.StrNode.doFormat(obj, f);
        }

        private void appendString(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, TruffleString obj) {
            switch (encoder.fastEncode) {
                case FastEncode:
                    JSONUtils.appendStringUncached(obj, builder, false);
                    break;
                case FastEncodeAscii:
                    JSONUtils.appendStringUncached(obj, builder, true);
                    break;
                case None:
                    Object result = CallUnaryMethodNode.getUncached().executeObject(encoder.encoder, obj);
                    if (!isString(result)) {
                        throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.ENCODER_MUST_RETURN_STR, result);
                    }
                    builder.appendStringUncached(CastToTruffleStringNode.executeUncached(result));
                    break;
                default:
                    assert false;
                    break;
            }
        }

        private static boolean isSimpleObj(Object obj) {
            return obj == PNone.NONE || obj == Boolean.TRUE || obj == Boolean.FALSE || isString(obj) || isInteger(obj) || isPInt(obj) || obj instanceof Float || isDouble(obj) || isPFloat(obj);
        }

        private boolean appendSimpleObj(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, Object obj) {
            if (obj == PNone.NONE || obj == Boolean.TRUE || obj == Boolean.FALSE) {
                appendConst(builder, obj);
            } else if (isJavaString(obj)) {
                appendString(encoder, builder, toTruffleStringUncached((String) obj));
            } else if (obj instanceof TruffleString) {
                appendString(encoder, builder, (TruffleString) obj);
            } else if (obj instanceof PString) {
                appendString(encoder, builder, StringNodes.StringMaterializeNode.executeUncached((PString) obj));
            } else if (obj instanceof Integer) {
                builder.appendIntNumberUncached((int) obj);
            } else if (obj instanceof Long) {
                builder.appendLongNumberUncached((long) obj);
            } else if (obj instanceof PInt) {
                builder.appendStringUncached(TruffleString.FromJavaStringNode.getUncached().execute(castExact(obj, PInt.class).toString(), TS_ENCODING));
            } else if (obj instanceof Float) {
                appendFloat(encoder, builder, (float) obj);
            } else if (obj instanceof Double) {
                appendFloat(encoder, builder, (double) obj);
            } else if (obj instanceof PFloat) {
                appendFloat(encoder, builder, ((PFloat) obj).asDouble());
            } else {
                return false;
            }
            return true;
        }

        private void appendListObj(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, Object obj) {
            if (appendSimpleObj(encoder, builder, obj)) {
                // done
            } else if (obj instanceof PList || obj instanceof PTuple) {
                appendList(encoder, builder, (PSequence) obj);
            } else if (obj instanceof PDict) {
                appendDict(encoder, builder, (PDict) obj);
            } else {
                startRecursion(encoder, obj);
                Object newObj = CallUnaryMethodNode.getUncached().executeObject(encoder.defaultFn, obj);
                appendListObj(encoder, builder, newObj);
                endRecursion(encoder, obj);
            }
        }

        private static void endRecursion(PJSONEncoder encoder, Object obj) {
            if (encoder.markers != PNone.NONE) {
                encoder.removeCircular(obj);
            }
        }

        private void startRecursion(PJSONEncoder encoder, Object obj) {
            if (encoder.markers != PNone.NONE) {
                if (!encoder.tryAddCircular(obj)) {
                    throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.CIRCULAR_REFERENCE_DETECTED);
                }
            }
        }

        private void appendDict(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, PDict dict) {
            HashingStorage storage = dict.getDictStorage();

            if (HashingStorageLen.executeUncached(storage) == 0) {
                builder.appendStringUncached(T_BRACES);
            } else {
                startRecursion(encoder, dict);
                builder.appendCodePointUncached('{');

                if (!encoder.sortKeys && PGuards.isBuiltinDict(dict)) {
                    HashingStorageIterator it = HashingStorageGetIterator.executeUncached(storage);
                    boolean first = true;
                    while (HashingStorageIteratorNext.executeUncached(storage, it)) {
                        Object key = HashingStorageIteratorKey.executeUncached(storage, it);
                        Object value = HashingStorageIteratorValue.executeUncached(storage, it);
                        first = appendDictEntry(encoder, builder, first, key, value);
                    }
                } else {
                    appendDictSlowPath(encoder, builder, dict);
                }

                builder.appendCodePointUncached('}');
                endRecursion(encoder, dict);
            }
        }

        private void appendDictSlowPath(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, com.oracle.graal.python.builtins.objects.dict.PDict dict) {
            PList items = ConstructListNode.getUncached().execute(null, callGetItems.executeObject(null, dict));
            if (encoder.sortKeys) {
                sortList.execute(null, items);
            }
            Object iter = callGetDictIter.executeCached(null, items);
            boolean first = true;
            while (true) {
                try {
                    Object item = PyIterNextNode.executeUncached(iter);
                    if (!(item instanceof PTuple itemTuple) || itemTuple.getSequenceStorage().length() != 2) {
                        throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.ITEMS_MUST_RETURN_2_TUPLES);
                    }
                    SequenceStorage sequenceStorage = itemTuple.getSequenceStorage();
                    Object key = SequenceStorageNodes.GetItemScalarNode.executeUncached(sequenceStorage, 0);
                    Object value = SequenceStorageNodes.GetItemScalarNode.executeUncached(sequenceStorage, 1);
                    first = appendDictEntry(encoder, builder, first, key, value);
                } catch (IteratorExhausted e) {
                    break;
                }
            }
        }

        private boolean appendDictEntry(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, boolean first, Object key, Object value) {
            if (!first) {
                builder.appendStringUncached(encoder.itemSeparator);
            }
            if (isString(key)) {
                appendSimpleObj(encoder, builder, key);
            } else {
                if (!isSimpleObj(key)) {
                    if (encoder.skipKeys) {
                        return true;
                    }
                    throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.KEYS_MUST_BE_STR_INT___NOT_P, key);
                }
                builder.appendCodePointUncached('"');
                appendSimpleObj(encoder, builder, key);
                builder.appendCodePointUncached('"');
            }
            builder.appendStringUncached(encoder.keySeparator);
            appendListObj(encoder, builder, value);
            return false;
        }

        private void appendList(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, PSequence list) {
            SequenceStorage storage = list.getSequenceStorage();

            if (storage.length() == 0) {
                builder.appendStringUncached(T_BRACKETS);
            } else {
                startRecursion(encoder, list);
                builder.appendCodePointUncached('[');

                if (PyTupleCheckExactNode.executeUncached(list) || PyListCheckExactNode.executeUncached(list)) {
                    for (int i = 0; i < storage.length(); i++) {
                        if (i > 0) {
                            builder.appendStringUncached(encoder.itemSeparator);
                        }
                        appendListObj(encoder, builder, SequenceStorageNodes.GetItemScalarNode.executeUncached(storage, i));
                    }
                } else {
                    appendListSlowPath(encoder, builder, list);
                }

                builder.appendCodePointUncached(']');
                endRecursion(encoder, list);
            }
        }

        private void appendListSlowPath(PJSONEncoder encoder, TruffleStringBuilderUTF32 builder, PSequence list) {
            Object iter = callGetListIter.executeCached(null, list);
            boolean first = true;
            while (true) {
                try {
                    Object item = PyIterNextNode.executeUncached(iter);
                    if (!first) {
                        builder.appendStringUncached(encoder.itemSeparator);
                    }
                    first = false;
                    appendListObj(encoder, builder, item);
                } catch (IteratorExhausted e) {
                    break;
                }
            }
        }
    }
}
