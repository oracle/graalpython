/* Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.object.IsBuiltinClassProfile.profileClassSlowPath;

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
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.JSONEncoder)
public class JSONEncoderBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONEncoderBuiltinsFactory.getFactories();
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "obj", "_current_indent_level"})
    @ArgumentClinic(name = "_current_indent_level", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CallEncoderNode extends PythonTernaryClinicBuiltinNode {

        @Child private CallUnaryMethodNode callEncode = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callDefaultFn = CallUnaryMethodNode.create();
        @Child private CastToJavaStringNode castEncodeResult = CastToJavaStringNode.create();
        @Child private LookupAndCallUnaryNode callGetItems = LookupAndCallUnaryNode.create(SpecialMethodNames.ITEMS);
        @Child private LookupAndCallUnaryNode callGetDictIter = LookupAndCallUnaryNode.create(SpecialMethodSlot.Iter);
        @Child private GetNextNode callDictNext = GetNextNode.create();
        @Child private IsBuiltinClassProfile stopDictIterationProfile = IsBuiltinClassProfile.create();
        @Child private HashingStorageLibrary dictLib = HashingStorageLibrary.getFactory().createDispatched(6);
        @Child private ListSortNode sortList = ListSortNode.create();
        @Child private LookupAndCallUnaryNode callGetListIter = LookupAndCallUnaryNode.create(SpecialMethodSlot.Iter);
        @Child private GetNextNode callListNext = GetNextNode.create();
        @Child private IsBuiltinClassProfile stopListIterationProfile = IsBuiltinClassProfile.create();
        @Child private GetClassNode getDictClass = GetClassNode.create();
        @Child private ConstructListNode constructList = ConstructListNode.create();

        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONEncoderBuiltinsClinicProviders.CallEncoderNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        protected PTuple call(PJSONEncoder self, Object obj, @SuppressWarnings("unused") int indent) {
            StringBuilder builder = new StringBuilder();
            appendListObj(self, builder, obj);
            return factory.createTuple(new Object[]{builder.toString()});
        }

        private static void appendConst(StringBuilder builder, Object obj) {
            if (obj == PNone.NONE) {
                builder.append("null");
            } else if (obj == Boolean.TRUE) {
                builder.append("true");
            } else {
                assert obj == Boolean.FALSE;
                builder.append("false");
            }
        }

        private void appendFloat(PJSONEncoder encoder, StringBuilder builder, double obj) {
            if (!Double.isFinite(obj)) {
                if (!encoder.allowNan) {
                    throw raise(ValueError, "Out of range float values are not JSON compliant");
                }
                if (obj > 0) {
                    builder.append("Infinity");
                } else if (obj < 0) {
                    builder.append("-Infinity");
                } else {
                    builder.append("NaN");
                }
            } else {
                FloatFormatter f = new FloatFormatter(PRaiseNode.getUncached(), FloatBuiltins.StrNode.spec);
                f.setMinFracDigits(1);
                builder.append(FloatBuiltins.StrNode.doFormat(obj, f));
            }
        }

        private void appendString(PJSONEncoder encoder, StringBuilder builder, String obj) {
            switch (encoder.fastEncode) {
                case FastEncode:
                    JSONModuleBuiltins.EncodeBaseString.appendString(obj, builder);
                    break;
                case FastEncodeAscii:
                    JSONModuleBuiltins.EncodeBaseStringAscii.appendString(obj, builder);
                    break;
                case None:
                    Object result = callEncode.executeObject(encoder.encoder, obj);
                    if (!PGuards.isString(result)) {
                        throw raise(TypeError, "encoder() must return a string, not %p", result);
                    }
                    builder.append(castEncodeResult.execute(result));
                    break;
                default:
                    assert false;
                    break;
            }
        }

        private boolean appendSimpleObj(PJSONEncoder encoder, StringBuilder builder, Object obj) {
            if (obj == PNone.NONE || obj == Boolean.TRUE || obj == Boolean.FALSE) {
                appendConst(builder, obj);
            } else if (obj instanceof String) {
                appendString(encoder, builder, (String) obj);
            } else if (obj instanceof PString) {
                appendString(encoder, builder, ((PString) obj).toString());
            } else if (obj instanceof Integer) {
                builder.append((int) obj);
            } else if (obj instanceof Long) {
                builder.append((long) obj);
            } else if (obj instanceof PInt) {
                builder.append(((PInt) obj).toString());
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

        private void appendListObj(PJSONEncoder encoder, StringBuilder builder, Object obj) {
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
                encoder.circular.remove(obj);
            }
        }

        private void startRecursion(PJSONEncoder encoder, Object obj) {
            if (encoder.markers != PNone.NONE) {
                if (encoder.circular.containsKey(obj)) {
                    throw raise(ValueError, "Circular reference detected");
                }
                encoder.circular.put(obj, null);
            }
        }

        private void appendDict(PJSONEncoder encoder, StringBuilder builder, PDict dict) {
            HashingStorage storage = dict.getDictStorage();

            if (dictLib.length(storage) == 0) {
                builder.append("{}");
            } else {
                startRecursion(encoder, dict);
                builder.append('{');

                if (!encoder.sortKeys && profileClassSlowPath(getDictClass.execute(dict), PDict)) {
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
                            throw raise(ValueError, "items must return 2-tuples");
                        }
                        SequenceStorage sequenceStorage = ((PTuple) item).getSequenceStorage();
                        Object key = sequenceStorage.getItemNormalized(0);
                        Object value = sequenceStorage.getItemNormalized(1);
                        first = appendDictEntry(encoder, builder, first, key, value);
                    }
                }

                builder.append('}');
                endRecursion(encoder, dict);
            }
        }

        private boolean appendDictEntry(PJSONEncoder encoder, StringBuilder builder, boolean first, Object key, Object value) {
            if (!first) {
                builder.append(encoder.itemSeparator);
            }
            boolean isString = key instanceof String || key instanceof PString;
            if (!isString) {
                builder.append('"');
            }
            if (!appendSimpleObj(encoder, builder, key)) {
                if (encoder.skipKeys) {
                    if (!isString) {
                        builder.setLength(builder.length() - 1);
                    }
                    return true;
                }
                throw raise(TypeError, "keys must be str, int, float, bool or None, not %p", key);
            }
            if (!isString) {
                builder.append('"');
            }
            builder.append(encoder.keySeparator);
            appendListObj(encoder, builder, value);
            return false;
        }

        private void appendList(PJSONEncoder encoder, StringBuilder builder, PSequence list) {
            SequenceStorage storage = list.getSequenceStorage();

            if (storage.length() == 0) {
                builder.append("[]");
            } else {
                startRecursion(encoder, list);
                builder.append('[');

                if (profileClassSlowPath(getDictClass.execute(list), PTuple) || profileClassSlowPath(getDictClass.execute(list), PList)) {
                    for (int i = 0; i < storage.length(); i++) {
                        if (i > 0) {
                            builder.append(encoder.itemSeparator);
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
                            builder.append(encoder.itemSeparator);
                        }
                        first = false;
                        appendListObj(encoder, builder, item);
                    }
                }

                builder.append(']');
                endRecursion(encoder, list);
            }
        }
    }
}
