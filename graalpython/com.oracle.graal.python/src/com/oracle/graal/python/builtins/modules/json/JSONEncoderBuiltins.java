/* Copyright (c) 2020, 2022, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PDict;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PList;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PTuple;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.PGuards.isDouble;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.PGuards.isPFloat;
import static com.oracle.graal.python.nodes.PGuards.isPInt;
import static com.oracle.graal.python.nodes.PGuards.isString;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOUBLE_QUOTE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_BRACES;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_BRACKETS;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACKET;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.truffle.api.CompilerDirectives.castExact;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListSortNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.JSONEncoder)
public class JSONEncoderBuiltins extends PythonBuiltins {

    private static final TruffleString T_NULL = tsLiteral("null");
    private static final TruffleString T_JSON_TRUE = tsLiteral("true");
    private static final TruffleString T_JSON_FALSE = tsLiteral("false");
    private static final TruffleString T_POSITIVE_INFINITY = tsLiteral("Infinity");
    private static final TruffleString T_NEGATIVE_INFINITY = tsLiteral("-Infinity");
    private static final TruffleString T_NAN = tsLiteral("NaN");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONEncoderBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "obj", "_current_indent_level"})
    @ArgumentClinic(name = "_current_indent_level", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CallEncoderNode extends PythonTernaryClinicBuiltinNode {

        @Child private CallUnaryMethodNode callEncode = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callDefaultFn = CallUnaryMethodNode.create();
        @Child private CastToTruffleStringNode castEncodeResult = CastToTruffleStringNode.create();
        @Child private LookupAndCallUnaryNode callGetItems = LookupAndCallUnaryNode.create(SpecialMethodNames.T_ITEMS);
        @Child private LookupAndCallUnaryNode callGetDictIter = LookupAndCallUnaryNode.create(SpecialMethodSlot.Iter);
        @Child private GetNextNode callDictNext = GetNextNode.create();
        @Child private IsBuiltinClassProfile stopDictIterationProfile = IsBuiltinClassProfile.create();
        @Child private HashingStorageLibrary dictLib = HashingStorageLibrary.getFactory().createDispatched(6);
        @Child private ListSortNode sortList = ListSortNode.create();
        @Child private LookupAndCallUnaryNode callGetListIter = LookupAndCallUnaryNode.create(SpecialMethodSlot.Iter);
        @Child private GetNextNode callListNext = GetNextNode.create();
        @Child private IsBuiltinClassProfile stopListIterationProfile = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile isClassProfile = IsBuiltinClassProfile.create();
        @Child private ConstructListNode constructList = ConstructListNode.create();
        @Child private StringNodes.StringMaterializeNode stringMaterializeNode = StringNodes.StringMaterializeNode.create();
        @Child private TruffleString.FromJavaStringNode fromJavaStringNode = TruffleString.FromJavaStringNode.create();
        @Child private TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode = TruffleString.CreateCodePointIteratorNode.create();
        @Child private TruffleStringIterator.NextNode nextNode = TruffleStringIterator.NextNode.create();
        @Child private TruffleStringBuilder.AppendCodePointNode appendCodePointNode = TruffleStringBuilder.AppendCodePointNode.create();
        @Child private TruffleStringBuilder.AppendStringNode appendStringNode = TruffleStringBuilder.AppendStringNode.create();
        @Child private TruffleStringBuilder.AppendLongNumberNode appendLongNumberNode = TruffleStringBuilder.AppendLongNumberNode.create();

        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONEncoderBuiltinsClinicProviders.CallEncoderNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        protected PTuple call(PJSONEncoder self, Object obj, @SuppressWarnings("unused") int indent,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING);
            appendListObj(self, builder, obj);
            return factory.createTuple(new Object[]{toStringNode.execute(builder)});
        }

        private void appendConst(TruffleStringBuilder builder, Object obj) {
            if (obj == PNone.NONE) {
                appendStringNode.execute(builder, T_NULL);
            } else if (obj == Boolean.TRUE) {
                appendStringNode.execute(builder, T_JSON_TRUE);
            } else {
                assert obj == Boolean.FALSE;
                appendStringNode.execute(builder, T_JSON_FALSE);
            }
        }

        private void appendFloat(PJSONEncoder encoder, TruffleStringBuilder builder, double obj) {
            if (!Double.isFinite(obj)) {
                if (!encoder.allowNan) {
                    throw raise(ValueError, ErrorMessages.OUT_OF_RANGE_FLOAT_NOT_JSON_COMPLIANT);
                }
                if (obj > 0) {
                    appendStringNode.execute(builder, T_POSITIVE_INFINITY);
                } else if (obj < 0) {
                    appendStringNode.execute(builder, T_NEGATIVE_INFINITY);
                } else {
                    appendStringNode.execute(builder, T_NAN);
                }
            } else {
                appendStringNode.execute(builder, formatDouble(obj));
            }
        }

        private static TruffleString formatDouble(double obj) {
            FloatFormatter f = new FloatFormatter(PRaiseNode.getUncached(), FloatBuiltins.StrNode.spec);
            f.setMinFracDigits(1);
            return FloatBuiltins.StrNode.doFormat(obj, f);
        }

        private void appendString(PJSONEncoder encoder, TruffleStringBuilder builder, TruffleString obj) {
            switch (encoder.fastEncode) {
                case FastEncode:
                    JSONModuleBuiltins.appendString(createCodePointIteratorNode.execute(obj, TS_ENCODING), builder, false, nextNode, appendCodePointNode);
                    break;
                case FastEncodeAscii:
                    JSONModuleBuiltins.appendString(createCodePointIteratorNode.execute(obj, TS_ENCODING), builder, true, nextNode, appendCodePointNode);
                    break;
                case None:
                    Object result = callEncode.executeObject(encoder.encoder, obj);
                    if (!isString(result)) {
                        throw raise(TypeError, ErrorMessages.ENCODER_MUST_RETURN_STR, result);
                    }
                    appendStringNode.execute(builder, castEncodeResult.execute(result));
                    break;
                default:
                    assert false;
                    break;
            }
        }

        private boolean isSimpleObj(Object obj) {
            return obj == PNone.NONE || obj == Boolean.TRUE || obj == Boolean.FALSE || isString(obj) || isInteger(obj) || isPInt(obj) || obj instanceof Float || isDouble(obj) || isPFloat(obj);
        }

        private boolean appendSimpleObj(PJSONEncoder encoder, TruffleStringBuilder builder, Object obj) {
            if (obj == PNone.NONE || obj == Boolean.TRUE || obj == Boolean.FALSE) {
                appendConst(builder, obj);
            } else if (isJavaString(obj)) {
                appendString(encoder, builder, toTruffleStringUncached((String) obj));
            } else if (obj instanceof TruffleString) {
                appendString(encoder, builder, (TruffleString) obj);
            } else if (obj instanceof PString) {
                appendString(encoder, builder, stringMaterializeNode.execute((PString) obj));
            } else if (obj instanceof Integer) {
                appendLongNumberNode.execute(builder, (int) obj);
            } else if (obj instanceof Long) {
                appendLongNumberNode.execute(builder, (long) obj);
            } else if (obj instanceof PInt) {
                appendStringNode.execute(builder, fromJavaStringNode.execute(castExact(obj, PInt.class).toString(), TS_ENCODING));
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

        private void appendListObj(PJSONEncoder encoder, TruffleStringBuilder builder, Object obj) {
            if (appendSimpleObj(encoder, builder, obj)) {
                // done
            } else if (obj instanceof PList || obj instanceof PTuple) {
                appendList(encoder, builder, (PSequence) obj);
            } else if (obj instanceof PDict) {
                appendDict(encoder, builder, (PDict) obj);
            } else {
                startRecursion(encoder, obj);
                Object newObj = callDefaultFn.executeObject(encoder.defaultFn, obj);
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
                    throw raise(ValueError, ErrorMessages.CIRCULAR_REFERENCE_DETECTED);
                }
            }
        }

        private void appendDict(PJSONEncoder encoder, TruffleStringBuilder builder, PDict dict) {
            HashingStorage storage = dict.getDictStorage();

            if (dictLib.length(storage) == 0) {
                appendStringNode.execute(builder, T_EMPTY_BRACES);
            } else {
                startRecursion(encoder, dict);
                appendStringNode.execute(builder, T_LBRACE);

                if (!encoder.sortKeys && isClassProfile.profileObject(dict, PDict)) {
                    HashingStorageIterable<DictEntry> entries = dictLib.entries(storage);
                    boolean first = true;
                    for (DictEntry entry : entries) {
                        first = appendDictEntry(encoder, builder, first, entry.key, entry.value);
                    }
                } else {
                    PList items = constructList.execute(null, callGetItems.executeObject(null, dict));
                    if (encoder.sortKeys) {
                        sortList.execute(null, items);
                    }
                    Object iter = callGetDictIter.executeObject(null, items);
                    boolean first = true;
                    while (true) {
                        Object item;
                        try {
                            item = callDictNext.execute(null, iter);
                        } catch (PException e) {
                            e.expectStopIteration(stopDictIterationProfile);
                            break;
                        }
                        if (!(item instanceof PTuple) || ((PTuple) item).getSequenceStorage().length() != 2) {
                            throw raise(ValueError, ErrorMessages.ITEMS_MUST_RETURN_2_TUPLES);
                        }
                        SequenceStorage sequenceStorage = ((PTuple) item).getSequenceStorage();
                        Object key = sequenceStorage.getItemNormalized(0);
                        Object value = sequenceStorage.getItemNormalized(1);
                        first = appendDictEntry(encoder, builder, first, key, value);
                    }
                }

                appendStringNode.execute(builder, T_RBRACE);
                endRecursion(encoder, dict);
            }
        }

        private boolean appendDictEntry(PJSONEncoder encoder, TruffleStringBuilder builder, boolean first, Object key, Object value) {
            if (!first) {
                appendStringNode.execute(builder, encoder.itemSeparator);
            }
            if (isString(key)) {
                appendSimpleObj(encoder, builder, key);
            } else {
                if (!isSimpleObj(key)) {
                    if (encoder.skipKeys) {
                        return true;
                    }
                    throw raise(TypeError, ErrorMessages.KEYS_MUST_BE_STR_INT___NOT_P, key);
                }
                appendStringNode.execute(builder, T_DOUBLE_QUOTE);
                appendSimpleObj(encoder, builder, key);
                appendStringNode.execute(builder, T_DOUBLE_QUOTE);
            }
            appendStringNode.execute(builder, encoder.keySeparator);
            appendListObj(encoder, builder, value);
            return false;
        }

        private void appendList(PJSONEncoder encoder, TruffleStringBuilder builder, PSequence list) {
            SequenceStorage storage = list.getSequenceStorage();

            if (storage.length() == 0) {
                appendStringNode.execute(builder, T_EMPTY_BRACKETS);
            } else {
                startRecursion(encoder, list);
                appendStringNode.execute(builder, T_LBRACKET);

                if (isClassProfile.profileObject(list, PTuple) || isClassProfile.profileObject(list, PList)) {
                    for (int i = 0; i < storage.length(); i++) {
                        if (i > 0) {
                            appendStringNode.execute(builder, encoder.itemSeparator);
                        }
                        appendListObj(encoder, builder, storage.getItemNormalized(i));
                    }
                } else {
                    Object iter = callGetListIter.executeObject(null, list);
                    boolean first = true;
                    while (true) {
                        Object item;
                        try {
                            item = callListNext.execute(null, iter);
                        } catch (PException e) {
                            e.expectStopIteration(stopListIterationProfile);
                            break;
                        }
                        if (!first) {
                            appendStringNode.execute(builder, encoder.itemSeparator);
                        }
                        first = false;
                        appendListObj(encoder, builder, item);
                    }
                }

                appendStringNode.execute(builder, T_RBRACKET);
                endRecursion(encoder, list);
            }
        }
    }
}
