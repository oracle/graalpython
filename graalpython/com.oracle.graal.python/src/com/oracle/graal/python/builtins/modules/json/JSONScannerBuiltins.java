/* Copyright (c) 2020, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.byteIndexToCodepointIndex;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.codepointIndexToByteIndex;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.HashMap;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyLongFromUnicodeObject;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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

@CoreFunctions(extendClasses = PythonBuiltinClassType.JSONScanner)
public final class JSONScannerBuiltins extends PythonBuiltins {

    public static final TruffleString T_JSON_DECODE_ERROR = tsLiteral("JSONDecodeError");
    static final int RECURSION_LIMIT = 50_000;

    static final class IntRef {
        int value;
    }

    public static final TpSlots SLOTS = JSONScannerBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONScannerBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "make_scanner", parameterNames = {"$cls", "context"})
    @GenerateNodeFactory
    public abstract static class MakeScanner extends PythonBinaryBuiltinNode {

        @Child private GetFixedAttributeNode getStrict = GetFixedAttributeNode.create(T_STRICT);
        @Child private GetFixedAttributeNode getObjectHook = GetFixedAttributeNode.create(tsLiteral("object_hook"));
        @Child private GetFixedAttributeNode getObjectPairsHook = GetFixedAttributeNode.create(tsLiteral("object_pairs_hook"));
        @Child private GetFixedAttributeNode getParseFloat = GetFixedAttributeNode.create(tsLiteral("parse_float"));
        @Child private GetFixedAttributeNode getParseInt = GetFixedAttributeNode.create(tsLiteral("parse_int"));
        @Child private GetFixedAttributeNode getParseConstant = GetFixedAttributeNode.create(tsLiteral("parse_constant"));

        @Specialization
        public PJSONScanner doNew(VirtualFrame frame, Object cls, Object context,
                        @Bind Node inliningTarget,
                        @Cached PyObjectIsTrueNode castStrict,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyFloatCheckExactNode pyFloatCheckExactNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode) {

            boolean strict = castStrict.execute(frame, getStrict.execute(frame, context));
            Object objectHook = getObjectHook.execute(frame, context);
            Object objectPairsHook = getObjectPairsHook.execute(frame, context);
            Object parseFloatProp = getParseFloat.execute(frame, context);
            Object parseIntProp = getParseInt.execute(frame, context);
            Object parseConstant = getParseConstant.execute(frame, context);
            Object parseFloat = pyFloatCheckExactNode.execute(inliningTarget, parseFloatProp) ? PNone.NONE : parseFloatProp;
            Object parseInt = pyLongCheckExactNode.execute(inliningTarget, parseIntProp) ? PNone.NONE : parseIntProp;
            return PFactory.createJSONScanner(cls, getInstanceShape.execute(cls), strict, objectHook, objectPairsHook, parseFloat, parseInt, parseConstant);
        }
    }

    @Slot(value = SlotKind.tp_call, isComplex = true)
    @SlotSignature(name = "scan_once", minNumOfPositionalArgs = 1, parameterNames = {"$self", "string", "idx"})
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "idx", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CallScannerNode extends PythonTernaryClinicBuiltinNode {

        // TODO: do we need these nodes, they are all used behind TruffleBoundary
        @Child private CallUnaryMethodNode callParseFloat = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callParseInt = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callParseConstant = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callObjectHook = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callObjectPairsHook = CallUnaryMethodNode.create();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONScannerBuiltinsClinicProviders.CallScannerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        protected PTuple call(VirtualFrame frame, PJSONScanner self, TruffleString string, int idx,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Cached InlinedConditionProfile defaultProfile,
                        @Cached InlinedBranchProfile errorProfile,
                        @Cached InlinedConditionProfile objectHookProfile,
                        @Cached InlinedConditionProfile objectPairsHookProfile,
                        @Cached InlinedConditionProfile parseFloatProfile,
                        @Cached InlinedConditionProfile parseIntProfile,
                        @Cached InlinedConditionProfile parseConstantProfile,
                        @Cached HashingStorageSetItem hashingStorageSetItem,
                        @Cached PyLongFromUnicodeObject pyLongFromUnicodeObject,
                        @Cached TruffleString.MaterializeNode materializeNode,
                        @Cached TruffleString.HashCodeNode hashCodeNode,
                        @Cached TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                        @Cached TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode,
                        @Cached TruffleString.IntIndexOfAnyIntUTF32Node indexOfAnyIntUTF32Node,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode,
                        @Cached TruffleStringBuilder.ToStringNode builderToStringNode) {
            IntRef nextIdx = new IntRef();
            boolean strict = self.strict;
            Object objectHook = self.objectHook;
            Object objectPairsHook = self.objectPairsHook;
            Object parseFloat = self.parseFloat;
            Object parseInt = self.parseInt;
            Object parseConstant = self.parseConstant;
            final Object result;
            materializeNode.execute(string, TS_ENCODING);
            if (defaultProfile.profile(inliningTarget, strict &&
                            objectHook == PNone.NONE &&
                            objectPairsHook == PNone.NONE &&
                            parseFloat == PNone.NONE &&
                            parseInt == PNone.NONE &&
                            parseConstant == PNone.NONE)) {
                result = scanOnceUnicode(frame, boundaryCallData, inliningTarget, string, idx, nextIdx,
                                self.memo,
                                true,
                                PNone.NONE,
                                PNone.NONE,
                                PNone.NONE,
                                PNone.NONE,
                                PNone.NONE,
                                errorProfile,
                                hashingStorageSetItem,
                                pyLongFromUnicodeObject,
                                hashCodeNode,
                                codePointAtIndexNode,
                                regionEqualByteIndexNode,
                                substringByteIndexNode,
                                byteIndexOfCodePointSetNode,
                                indexOfAnyIntUTF32Node,
                                toJavaStringNode,
                                appendCodePointNode,
                                appendSubstringByteIndexNode,
                                builderToStringNode);
            } else {
                result = scanOnceUnicodeCutoff(frame, boundaryCallData, inliningTarget, string, idx, nextIdx,
                                self.memo,
                                strict,
                                objectHookProfile.profile(inliningTarget, objectHook == PNone.NONE) ? PNone.NONE : objectHook,
                                objectPairsHookProfile.profile(inliningTarget, objectPairsHook == PNone.NONE) ? PNone.NONE : objectPairsHook,
                                parseFloatProfile.profile(inliningTarget, parseFloat == PNone.NONE) ? PNone.NONE : parseFloat,
                                parseIntProfile.profile(inliningTarget, parseInt == PNone.NONE) ? PNone.NONE : parseInt,
                                parseConstantProfile.profile(inliningTarget, parseConstant == PNone.NONE) ? PNone.NONE : parseConstant,
                                errorProfile,
                                hashingStorageSetItem,
                                pyLongFromUnicodeObject,
                                hashCodeNode,
                                codePointAtIndexNode,
                                regionEqualByteIndexNode,
                                substringByteIndexNode,
                                byteIndexOfCodePointSetNode,
                                indexOfAnyIntUTF32Node,
                                toJavaStringNode,
                                appendCodePointNode,
                                appendSubstringByteIndexNode,
                                builderToStringNode);
            }
            return PFactory.createTuple(PythonLanguage.get(this), new Object[]{result, nextIdx.value});
        }

        private static int skipWhitespace(TruffleString string, int start, int length, TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode) {
            int idx = start;
            while (idx < length && JSONModuleBuiltins.isWhitespace(codePointAtIndexNode.execute(string, idx))) {
                idx++;
            }
            return idx;
        }

        private Object matchNumberUnicode(Node inliningTarget, TruffleString string, int start, int length, IntRef nextIdx,
                        Object parseFloat,
                        Object parseInt,
                        InlinedBranchProfile errorProfile,
                        PyLongFromUnicodeObject pyLongFromUnicodeObject,
                        TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.ToJavaStringNode toJavaStringNode) {
            /*
             * Read a JSON number from PyUnicode pystr. idx is the index of the first character of
             * the number nextIdx is a return-by-reference index to the first character after the
             * number.
             *
             * Returns a new PyObject representation of that number: PyLong, or PyFloat. May return
             * other types if parse_int or parse_float are set
             */

            int idx = start;

            boolean negative = codePointAtIndexNode.execute(string, idx) == '-';
            /* read a sign if it's there, make sure it's not the end of the string */
            if (negative) {
                idx++;
                if (idx >= length) {
                    throw stopIteration(inliningTarget, errorProfile, this, start);
                }
            }

            /* read as many integer digits as we find as long as it doesn't start with 0 */
            int c = codePointAtIndexNode.execute(string, idx);
            long longValue = 0;
            if (isDecimalDigitWithoutZero(c)) {
                longValue = c - '0';
                idx++;
                while (idx < length && isDecimalDigit(c = codePointAtIndexNode.execute(string, idx))) {
                    longValue = longValue * 10 + (c - '0');
                    idx++;
                }
                /* if it starts with 0 we only expect one integer digit */
            } else if (c == '0') {
                idx++;
                /* no integer digits, error */
            } else {
                throw stopIteration(inliningTarget, errorProfile, this, start);
            }
            boolean isFloat = false;

            /* if the next char is '.' followed by a digit then read all float digits */
            if (idx < (length - 1) && codePointAtIndexNode.execute(string, idx) == '.' && isDecimalDigit(codePointAtIndexNode.execute(string, idx + 1))) {
                isFloat = true;
                idx += 2;
                while (idx < length && isDecimalDigit(codePointAtIndexNode.execute(string, idx))) {
                    idx++;
                }
            }

            /* if the next char is 'e' or 'E' then maybe read the exponent (or backtrack) */
            if (idx < (length - 1) && ((codePointAtIndexNode.execute(string, idx) | 0x20) == 'e')) {
                int e_start = idx;
                idx++;

                /* read an exponent sign if present */
                int plusMinus;
                if (idx < (length - 1) && ((plusMinus = codePointAtIndexNode.execute(string, idx)) == '-' || plusMinus == '+')) {
                    idx++;
                }

                /* read all digits */
                boolean gotDigits = false;
                while (idx < length && isDecimalDigit(codePointAtIndexNode.execute(string, idx))) {
                    idx++;
                    gotDigits = true;
                }

                /* if we got a digit, then parse as float. if not, backtrack */
                if (gotDigits) {
                    isFloat = true;
                } else {
                    idx = e_start;
                }
            }

            nextIdx.value = idx;
            TruffleString numStr = substringByteIndexNode.execute(string, codepointIndexToByteIndex(start), codepointIndexToByteIndex(idx - start), TS_ENCODING, true);
            if (isFloat) {
                if (parseFloat == PNone.NONE) {
                    return FloatUtils.parseValidString(toJavaStringNode.execute(numStr));
                } else {
                    /* copy the section we determined to be a number */
                    return callParseFloat.executeObject(parseFloat, numStr);
                }
            } else {
                if (parseInt == PNone.NONE) {
                    // long values with 18 digits or fewer cannot overflow.
                    if (idx - start <= 18) {
                        if (negative) {
                            longValue = -longValue;
                        }
                        return PInt.isIntRange(longValue) ? (int) longValue : longValue;
                    }
                    return parseLongGeneric(inliningTarget, numStr, pyLongFromUnicodeObject);
                } else {
                    /* copy the section we determined to be a number */
                    return callParseInt.executeObject(parseInt, numStr);
                }
            }
        }

        @InliningCutoff
        private static Object parseLongGeneric(Node inliningTarget, TruffleString numStr, PyLongFromUnicodeObject pyLongFromUnicodeObject) {
            return pyLongFromUnicodeObject.execute(inliningTarget, numStr, 10);
        }

        private enum ScannerState {
            initial,
            list,
            dict,
        }

        @InliningCutoff
        private Object scanOnceUnicodeCutoff(VirtualFrame frame, BoundaryCallData boundaryCallData, Node inliningTarget, TruffleString string, int idx, IntRef nextIdx,
                        HashMap<TruffleString, TruffleString> memo,
                        boolean strict,
                        Object objectHook,
                        Object objectPairsHook,
                        Object parseFloat,
                        Object parseInt,
                        Object parseConstant,
                        InlinedBranchProfile errorProfile,
                        HashingStorageSetItem hashingStorageSetItem,
                        PyLongFromUnicodeObject pyLongFromUnicodeObject,
                        TruffleString.HashCodeNode hashCodeNode,
                        TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                        TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode, TruffleString.IntIndexOfAnyIntUTF32Node indexOfAnyIntUTF32Node,
                        TruffleString.ToJavaStringNode toJavaStringNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode, TruffleStringBuilder.ToStringNode builderToStringNode) {
            return scanOnceUnicode(frame, boundaryCallData, inliningTarget, string, idx, nextIdx,
                            memo,
                            strict,
                            objectHook,
                            objectPairsHook,
                            parseFloat,
                            parseInt,
                            parseConstant,
                            errorProfile,
                            hashingStorageSetItem, pyLongFromUnicodeObject, hashCodeNode,
                            codePointAtIndexNode,
                            regionEqualByteIndexNode,
                            substringByteIndexNode,
                            byteIndexOfCodePointSetNode,
                            indexOfAnyIntUTF32Node,
                            toJavaStringNode,
                            appendCodePointNode,
                            appendSubstringByteIndexNode,
                            builderToStringNode);
        }

        private Object scanOnceUnicode(VirtualFrame frame, BoundaryCallData boundaryCallData, Node inliningTarget, TruffleString string, int idx, IntRef nextIdx,
                        HashMap<TruffleString, TruffleString> memo,
                        boolean strict,
                        Object objectHook,
                        Object objectPairsHook,
                        Object parseFloat,
                        Object parseInt,
                        Object parseConstant,
                        InlinedBranchProfile errorProfile,
                        HashingStorageSetItem hashingStorageSetItem,
                        PyLongFromUnicodeObject pyLongFromUnicodeObject,
                        TruffleString.HashCodeNode hashCodeNode,
                        TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                        TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode, TruffleString.IntIndexOfAnyIntUTF32Node indexOfAnyIntUTF32Node,
                        TruffleString.ToJavaStringNode toJavaStringNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode, TruffleStringBuilder.ToStringNode builderToStringNode) {
            ArrayBuilder<Object> stack = new ArrayBuilder<>(8);
            /*
             * Read one JSON term (of any kind) from PyUnicode pystr. idx is the index of the first
             * character of the term nextIdx is a return-by-reference index to the first character
             * after the number.
             *
             * Returns a new PyObject representation of the term.
             */
            int length = byteIndexToCodepointIndex(string.byteLength(TS_ENCODING));
            if (idx < 0) {
                throw raiseStatic(inliningTarget, errorProfile, this, PythonBuiltinClassType.ValueError, ErrorMessages.IDX_CANNOT_BE_NEG);
            }
            if (idx >= length) {
                throw stopIteration(inliningTarget, errorProfile, this, idx);
            }
            PythonLanguage language = PythonLanguage.get(null);
            boolean hasPairsHook = objectPairsHook != PNone.NONE;
            boolean hasObjectHook = objectHook != PNone.NONE;
            boolean hasParseConstantHook = parseConstant != PNone.NONE;
            ObjectSequenceStorage currentListStorage = null;
            ObjectSequenceStorage currentPairsStorage = null;
            EconomicMapStorage currentDictStorage = null;
            ScannerState state = ScannerState.initial;
            boolean commaSeen = false;
            while (true) {
                idx = skipWhitespace(string, idx, length, codePointAtIndexNode);
                final TruffleString propertyKey;
                final Object value;
                ScannerState nextState = null;
                if (state == ScannerState.dict) {
                    /* scanner is currently inside a dictionary */
                    int c;
                    if (idx >= length || (c = codePointAtIndexNode.execute(string, idx)) != '"' && (c != '}')) {
                        throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, idx, ErrorMessages.EXPECTING_PROP_NAME_ECLOSED_IN_DBL_QUOTES);
                    } else if (c == '}') {
                        if (commaSeen) {
                            throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, idx, ErrorMessages.EXPECTING_PROP_NAME_ECLOSED_IN_DBL_QUOTES);
                        }
                        nextIdx.value = ++idx;
                        // pop current dict
                        Object topOfStack = stack.pop();
                        TruffleString parentKey = null;
                        final Object dict;
                        /*
                         * If no hooks are present, the stack contains only the parent dicts or
                         * lists the current object is nested in. If a objectHook or objectPairsHook
                         * is present, the stack also stores the property keys of nested dicts. For
                         * example, when parsing the innermost dict of '{"a":{"b":{"c":"d"}}}', the
                         * stack will contain (from bottom to top) [<dictStorage>, <dictStorage>,
                         * "a", <dictStorage>, "b"]. This is necessary so after finishing parsing a
                         * dict, we can call the hook and replace it with the hook's return value in
                         * the parent dict, i.e. when in the previous example we finish parsing
                         * '{"c":"d"}', we effectively execute parent["b"] = objectHook({"c":"d"}).
                         */
                        if (hasPairsHook) {
                            if (topOfStack instanceof TruffleString) {
                                parentKey = (TruffleString) topOfStack;
                                topOfStack = stack.pop();
                            }
                            assert topOfStack == currentPairsStorage;
                            dict = callObjectPairsHook.executeObject(objectPairsHook, PFactory.createList(language, currentPairsStorage));
                        } else if (hasObjectHook) {
                            if (topOfStack instanceof TruffleString) {
                                parentKey = (TruffleString) topOfStack;
                                topOfStack = stack.pop();
                            }
                            assert topOfStack instanceof PDict;
                            assert ((PDict) topOfStack).getDictStorage() == currentDictStorage;
                            dict = callObjectHook.executeObject(objectHook, topOfStack);
                        } else {
                            assert topOfStack instanceof PDict;
                            assert ((PDict) topOfStack).getDictStorage() == currentDictStorage;
                            dict = topOfStack;
                        }
                        if (stack.isEmpty()) {
                            return dict;
                        } else {
                            if (hasPairsHook || hasObjectHook) {
                                nextState = parentKey == null ? ScannerState.list : ScannerState.dict;
                                if (nextState == ScannerState.dict) {
                                    Object parent = stack.peek();
                                    if (parent instanceof TruffleString) {
                                        parent = stack.get(stack.size() - 2);
                                    }
                                    if (hasPairsHook) {
                                        currentPairsStorage = (ObjectSequenceStorage) parent;
                                        currentPairsStorage.setObjectItemNormalized(currentPairsStorage.length() - 1, PFactory.createTuple(language, new Object[]{parentKey, dict}));
                                    } else {
                                        PDict parentDict = (PDict) parent;
                                        currentDictStorage = (EconomicMapStorage) parentDict.getDictStorage();
                                        HashingStorage setItemReturnVal = hashingStorageSetItem.execute(inliningTarget, currentDictStorage, parentKey, dict);
                                        assert currentDictStorage == setItemReturnVal;
                                    }
                                } else {
                                    currentListStorage = (ObjectSequenceStorage) ((PList) stack.peek()).getSequenceStorage();
                                    currentListStorage.setObjectItemNormalized(currentListStorage.length() - 1, dict);
                                }
                            } else {
                                Object parent = stack.peek();
                                if (parent instanceof PDict parentDict) {
                                    currentDictStorage = (EconomicMapStorage) parentDict.getDictStorage();
                                    nextState = ScannerState.dict;
                                } else {
                                    currentListStorage = (ObjectSequenceStorage) ((PList) parent).getSequenceStorage();
                                    nextState = ScannerState.list;
                                }
                            }
                        }
                        propertyKey = null;
                    } else {
                        /* read key */
                        TruffleString newKey = parseStringUnicode(frame, boundaryCallData, inliningTarget, string, idx + 1, length, strict, nextIdx, this, errorProfile,
                                        byteIndexOfCodePointSetNode,
                                        indexOfAnyIntUTF32Node,
                                        codePointAtIndexNode,
                                        substringByteIndexNode,
                                        appendCodePointNode,
                                        appendSubstringByteIndexNode, builderToStringNode);
                        /* force hash computation */
                        hashCodeNode.execute(newKey, TS_ENCODING);
                        TruffleString key = memoPutIfAbsent(memo, newKey);
                        if (key == null) {
                            key = newKey;
                        }
                        propertyKey = key;
                        idx = nextIdx.value;
                        /*
                         * skip whitespace between key and : delimiter, read :, skip whitespace
                         */
                        idx = skipWhitespace(string, idx, length, codePointAtIndexNode);
                        if (idx >= length || codePointAtIndexNode.execute(string, idx) != ':') {
                            throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, idx, ErrorMessages.EXPECTING_COLON_DELIMITER);
                        }
                        idx = skipWhitespace(string, idx + 1, length, codePointAtIndexNode);
                    }
                } else if (state == ScannerState.list) {
                    if (idx >= length) {
                        throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, length, ErrorMessages.EXPECTING_VALUE);
                    }
                    if (codePointAtIndexNode.execute(string, idx) == ']') {
                        if (commaSeen) {
                            throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, idx, ErrorMessages.EXPECTING_VALUE);
                        }
                        nextIdx.value = ++idx;
                        Object topOfStack = stack.pop();
                        assert topOfStack instanceof PList;
                        assert ((PList) topOfStack).getSequenceStorage() == currentListStorage;
                        if (stack.isEmpty()) {
                            return topOfStack;
                        } else {
                            Object parent = stack.peek();
                            if (parent instanceof PList parentList) {
                                currentListStorage = (ObjectSequenceStorage) parentList.getSequenceStorage();
                                nextState = ScannerState.list;
                            } else if (hasPairsHook) {
                                currentPairsStorage = (ObjectSequenceStorage) parent;
                                nextState = ScannerState.dict;
                            } else {
                                currentDictStorage = (EconomicMapStorage) ((PDict) parent).getDictStorage();
                                nextState = ScannerState.dict;
                            }
                        }
                    }
                    propertyKey = null;
                } else {
                    propertyKey = null;
                }
                if (nextState == null) {
                    if (idx >= length) {
                        throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, length, ErrorMessages.EXPECTING_VALUE);
                    }
                    int c = codePointAtIndexNode.execute(string, idx);
                    if (c == '{') {
                        idx++;
                        if (hasPairsHook) {
                            value = new ObjectSequenceStorage(4);
                        } else {
                            value = PFactory.createDict(language, EconomicMapStorage.create());
                        }
                        nextState = ScannerState.dict;
                    } else if (c == '[') {
                        idx++;
                        value = PFactory.createList(language, new ObjectSequenceStorage(4));
                        nextState = ScannerState.list;
                    } else {
                        value = parsePrimitiveUnicode(frame, boundaryCallData, inliningTarget, hasParseConstantHook, string, idx, length, nextIdx, c, strict, parseConstant, parseFloat, parseInt,
                                        errorProfile,
                                        pyLongFromUnicodeObject,
                                        codePointAtIndexNode,
                                        regionEqualByteIndexNode,
                                        substringByteIndexNode,
                                        byteIndexOfCodePointSetNode,
                                        indexOfAnyIntUTF32Node,
                                        appendCodePointNode,
                                        appendSubstringByteIndexNode,
                                        toJavaStringNode,
                                        builderToStringNode);
                        idx = nextIdx.value;
                    }
                    if (state == ScannerState.dict) {
                        assert propertyKey != null;
                        if (hasPairsHook) {
                            currentPairsStorage.appendItem(PFactory.createTuple(language, new Object[]{propertyKey, value}));
                        } else {
                            HashingStorage newStorage = hashingStorageSetItem.execute(inliningTarget, currentDictStorage, propertyKey, value);
                            assert newStorage == currentDictStorage;
                        }
                    } else if (state == ScannerState.list) {
                        assert propertyKey == null;
                        currentListStorage.appendItem(value);
                    } else if (nextState == null) {
                        assert stack.isEmpty();
                        return value;
                    }
                    if (nextState != null) {
                        stack.add(value);
                        if (stack.size() > RECURSION_LIMIT) {
                            throw recursionError(inliningTarget, errorProfile, this, language);
                        }
                        if (nextState == ScannerState.list) {
                            currentListStorage = (ObjectSequenceStorage) ((PList) value).getSequenceStorage();
                        } else if (hasPairsHook) {
                            assert nextState == ScannerState.dict;
                            currentPairsStorage = (ObjectSequenceStorage) value;
                            if (state == ScannerState.dict) {
                                /*
                                 * save the associated propertyKey so we can replace the current
                                 * value with the hook's result later
                                 */
                                stack.add(propertyKey);
                            }
                        } else {
                            assert nextState == ScannerState.dict;
                            currentDictStorage = (EconomicMapStorage) ((PDict) value).getDictStorage();
                            if (hasObjectHook && state == ScannerState.dict) {
                                /*
                                 * save the associated propertyKey so we can replace the current
                                 * value with the hook's result later
                                 */
                                stack.add(propertyKey);
                            }
                        }
                        state = nextState;
                        commaSeen = false;
                        continue;
                    }
                } else {
                    state = nextState;
                }
                idx = skipWhitespace(string, idx, length, codePointAtIndexNode);
                int c = idx < length ? codePointAtIndexNode.execute(string, idx) : 0;
                if (c == (state == ScannerState.dict ? '}' : ']')) {
                    commaSeen = false;
                    continue;
                }
                if (c != ',') {
                    throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, this, string, idx, ErrorMessages.EXPECTING_COMMA_DELIMITER);
                }
                commaSeen = true;
                idx++;
            }
        }

        @TruffleBoundary
        private static TruffleString memoPutIfAbsent(HashMap<TruffleString, TruffleString> memo, TruffleString newKey) {
            return memo.putIfAbsent(newKey, newKey);
        }

        private static final TruffleString[] DOUBLE_CONSTANTS = {
                        tsLiteral("NaN"),
                        tsLiteral("Infinity"),
                        tsLiteral("-Infinity"),
        };

        private static final TruffleString ULL = tsLiteral("ull");
        private static final TruffleString RUE = tsLiteral("rue");
        private static final TruffleString ALSE = tsLiteral("alse");
        private static final TruffleString AN = tsLiteral("aN");
        private static final TruffleString NFINITY = tsLiteral("nfinity");
        private static final TruffleString INFINITY = tsLiteral("Infinity");

        private Object parsePrimitiveUnicode(VirtualFrame frame, BoundaryCallData boundaryCallData, Node inliningTarget, boolean hasParseConstantHook, TruffleString string, int idx,
                        int length, IntRef nextIdx, int c,
                        boolean strict,
                        Object parseConstant,
                        Object parseFloat,
                        Object parseInt,
                        InlinedBranchProfile errorProfile,
                        PyLongFromUnicodeObject pyLongFromUnicodeObject,
                        TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                        TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode,
                        TruffleString.IntIndexOfAnyIntUTF32Node indexOfAnyIntUTF32Node,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode,
                        TruffleString.ToJavaStringNode toJavaStringNode,
                        TruffleStringBuilder.ToStringNode builderToStringNode) {
            int doubleConstant = -1;
            switch (c) {
                case '"':
                    /* string */
                    return parseStringUnicode(frame, boundaryCallData, inliningTarget, string, idx + 1, length, strict, nextIdx, this,
                                    errorProfile,
                                    byteIndexOfCodePointSetNode,
                                    indexOfAnyIntUTF32Node,
                                    codePointAtIndexNode,
                                    substringByteIndexNode,
                                    appendCodePointNode,
                                    appendSubstringByteIndexNode,
                                    builderToStringNode);
                case 'n':
                    /* null */
                    if (regionEquals(string, idx, ULL, regionEqualByteIndexNode)) {
                        nextIdx.value = idx + 4;
                        return PNone.NONE;
                    }
                    break;
                case 't':
                    /* true */
                    if (regionEquals(string, idx, RUE, regionEqualByteIndexNode)) {
                        nextIdx.value = idx + 4;
                        return true;
                    }
                    break;
                case 'f':
                    /* false */
                    if (regionEquals(string, idx, ALSE, regionEqualByteIndexNode)) {
                        nextIdx.value = idx + 5;
                        return false;
                    }
                    break;
                case 'N':
                    /* NaN */
                    if (regionEquals(string, idx, AN, regionEqualByteIndexNode)) {
                        nextIdx.value = idx + 3;
                        if (hasParseConstantHook) {
                            doubleConstant = 0;
                        } else {
                            return Double.NaN;
                        }
                    }
                    break;
                case 'I':
                    /* Infinity */
                    if (regionEquals(string, idx, NFINITY, regionEqualByteIndexNode)) {
                        nextIdx.value = idx + 8;
                        if (hasParseConstantHook) {
                            doubleConstant = 1;
                        } else {
                            return Double.POSITIVE_INFINITY;
                        }
                    }
                    break;
                case '-':
                    /* -Infinity */
                    if (regionEquals(string, idx, INFINITY, regionEqualByteIndexNode)) {
                        nextIdx.value = idx + 9;
                        if (hasParseConstantHook) {
                            doubleConstant = 2;
                        } else {
                            return Double.NEGATIVE_INFINITY;
                        }
                    }
                    break;
            }
            if (doubleConstant >= 0) {
                /*
                 * Read a JSON constant. constant is the constant string that was found ("NaN",
                 * "Infinity", "-Infinity").
                 *
                 * Returns the result of parse_constant
                 */
                return callParseConstant.executeObject(parseConstant, DOUBLE_CONSTANTS[doubleConstant]);
            }
            /* Didn't find a string, object, array, or named constant. Look for a number. */
            return matchNumberUnicode(inliningTarget, string, idx, length, nextIdx, parseFloat, parseInt, errorProfile,
                            pyLongFromUnicodeObject,
                            codePointAtIndexNode,
                            substringByteIndexNode,
                            toJavaStringNode);
        }

        private static boolean regionEquals(TruffleString a, int idx, TruffleString b, TruffleString.RegionEqualByteIndexNode regionEqualByteIndexNode) {
            int fromByteIndexB = codepointIndexToByteIndex(idx + 1);
            int lengthB = b.byteLength(TS_ENCODING);
            return fromByteIndexB + lengthB <= a.byteLength(TS_ENCODING) && regionEqualByteIndexNode.execute(a, fromByteIndexB, b, 0, lengthB, TS_ENCODING);
        }
    }

    private static final TruffleString.CodePointSet CODE_POINT_SET_STRICT = TruffleString.CodePointSet.fromRanges(new int[]{
                    0, 0x1f,
                    '"', '"',
                    '\\', '\\',
    }, TS_ENCODING);
    private static final int[] CODE_POINT_SET_NON_STRICT = new int[]{'"', '\\'};

    static TruffleString parseStringUnicode(VirtualFrame frame, BoundaryCallData boundaryCallData, Node inliningTarget, TruffleString string, int start, int length, boolean strict,
                    IntRef nextIdx, Node raisingNode,
                    InlinedBranchProfile errorProfile,
                    TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode,
                    TruffleString.IntIndexOfAnyIntUTF32Node indexOfAnyIntUTF32Node,
                    TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                    TruffleString.SubstringByteIndexNode substringByteIndexNode,
                    TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                    TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode,
                    TruffleStringBuilder.ToStringNode builderToStringNode) {
        if (start < 0 || start > length) {
            throw raiseStatic(inliningTarget, errorProfile, raisingNode, PythonBuiltinClassType.ValueError, ErrorMessages.END_IS_OUT_OF_BOUNDS);
        }
        int idx = start;
        if (start < length) {
            if (strict) {
                idx = byteIndexToCodepointIndex(byteIndexOfCodePointSetNode.execute(string, codepointIndexToByteIndex(start), codepointIndexToByteIndex(length), CODE_POINT_SET_STRICT));
            } else {
                idx = indexOfAnyIntUTF32Node.execute(string, start, length, CODE_POINT_SET_NON_STRICT);
            }
            if (idx < 0) {
                idx = length;
            } else if (codePointAtIndexNode.execute(string, idx) == '"') {
                nextIdx.value = idx + 1;
                return substringByteIndexNode.execute(string, codepointIndexToByteIndex(start), codepointIndexToByteIndex(idx - start), TS_ENCODING, false);
            }
        }
        return parseStringUnicodeSlowpath(frame, boundaryCallData, inliningTarget, string, start, length, strict, idx, nextIdx, raisingNode, errorProfile,
                        codePointAtIndexNode,
                        appendCodePointNode,
                        appendSubstringByteIndexNode,
                        builderToStringNode);
    }

    @InliningCutoff
    private static TruffleString parseStringUnicodeSlowpath(VirtualFrame frame, BoundaryCallData boundaryCallData, Node inliningTarget, TruffleString string, int start, int length,
                    boolean strict, int idx, IntRef nextIdx, Node raisingNode,
                    InlinedBranchProfile errorProfile,
                    TruffleString.CodePointAtIndexUTF32Node codePointAtIndexNode,
                    TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                    TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode,
                    TruffleStringBuilder.ToStringNode builderToStringNode) {
        TruffleStringBuilderUTF32 builder = TruffleStringBuilder.createUTF32();
        appendSubstringByteIndexNode.execute(builder, string, codepointIndexToByteIndex(start), codepointIndexToByteIndex(idx - start));
        char highSurrogate = 0;
        while (idx < length) {
            int c = codePointAtIndexNode.execute(string, idx++);
            if (c == '"') {
                // we reached the end of the string literal
                nextIdx.value = idx;
                return builderToStringNode.execute(builder);
            } else if (c == '\\') {
                // escape sequence, switch to StringBuilder
                if (idx >= length) {
                    throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, raisingNode, string, start - 1, ErrorMessages.UTERMINATED_STR_STARTING);
                }
                c = codePointAtIndexNode.execute(string, idx++);
                if (c == 'u') {
                    if (idx + 3 >= length) {
                        throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, raisingNode, string, idx - 1, ErrorMessages.INVALID_UXXXX_ESCAPE);
                    }
                    c = 0;
                    for (int i = 0; i < 4; i++) {
                        int d = codePointAtIndexNode.execute(string, idx++);
                        final int digit;
                        final int dLowerCase;
                        if (isDecimalDigit(d)) {
                            digit = d - '0';
                        } else if ('a' <= (dLowerCase = (d | 0x20)) && dLowerCase <= 'f') {
                            digit = dLowerCase - ('a' - 10);
                        } else {
                            throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, raisingNode, string, idx - 1, ErrorMessages.INVALID_UXXXX_ESCAPE);
                        }
                        c = (char) ((c << 4) + digit);
                    }
                } else {
                    switch (c) {
                        case '"':
                        case '\\':
                        case '/':
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = '\f';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        default:
                            throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, raisingNode, string, idx - 1, ErrorMessages.INVALID_ESCAPE);
                    }
                }
                if (isLowSurrogate(c) && highSurrogate != 0) {
                    c = Character.toCodePoint(highSurrogate, (char) c);
                }
                if (isHighSurrogate(c)) {
                    highSurrogate = (char) c;
                } else {
                    appendCodePointNode.execute(builder, c, 1, true);
                    highSurrogate = 0;
                }
            } else {
                // any other character: check if in strict mode
                if (strict && c < 0x20) {
                    throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, raisingNode, string, idx - 1, ErrorMessages.INVALID_CTRL_CHARACTER_AT);
                }
                appendCodePointNode.execute(builder, c, 1, true);
            }
        }
        throw decodeError(frame, boundaryCallData, inliningTarget, errorProfile, raisingNode, string, start - 1, ErrorMessages.UNTERMINATED_STR_STARTING_AT);
    }

    private static boolean isHighSurrogate(int ch) {
        return ch >= Character.MIN_HIGH_SURROGATE && ch < (Character.MAX_HIGH_SURROGATE + 1);
    }

    private static boolean isLowSurrogate(int ch) {
        return ch >= Character.MIN_LOW_SURROGATE && ch < (Character.MAX_LOW_SURROGATE + 1);
    }

    private static boolean isDecimalDigit(int c) {
        return '0' <= c && c <= '9';
    }

    private static boolean isDecimalDigitWithoutZero(int c) {
        return '1' <= c && c <= '9';
    }

    @InliningCutoff
    private static PException raiseStatic(Node inliningTarget, InlinedBranchProfile errorProfile, Node raisingNode, PythonBuiltinClassType type, TruffleString message) {
        errorProfile.enter(inliningTarget);
        throw PRaiseNode.raiseStatic(raisingNode, type, message);
    }

    @InliningCutoff
    static RuntimeException recursionError(Node inliningTarget, InlinedBranchProfile errorProfile, Node raisingNode, PythonLanguage language) {
        errorProfile.enter(inliningTarget);
        throw recursionError(raisingNode, language);
    }

    @TruffleBoundary
    static RuntimeException recursionError(Node raisingNode, PythonLanguage language) {
        CompilerAsserts.neverPartOfCompilation();
        throw PRaiseNode.raiseExceptionObjectStatic(raisingNode, PFactory.createBaseException(language, RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, EMPTY_OBJECT_ARRAY), false);
    }

    @InliningCutoff
    private static RuntimeException decodeError(VirtualFrame frame, BoundaryCallData boundaryCallData, Node inliningTarget, InlinedBranchProfile errorProfile, Node raisingNode,
                    TruffleString jsonString, int pos, TruffleString format) {
        errorProfile.enter(inliningTarget);
        Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
        try {
            throw decodeError(raisingNode, jsonString, pos, format);
        } finally {
            ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
        }
    }

    @TruffleBoundary
    private static RuntimeException decodeError(Node raisingNode, TruffleString jsonString, int pos, TruffleString format) {
        CompilerAsserts.neverPartOfCompilation();
        Object module = AbstractImportNode.importModule(toTruffleStringUncached("json.decoder"));
        Object errorClass = PyObjectLookupAttr.executeUncached(module, T_JSON_DECODE_ERROR);
        Object exception = CallNode.executeUncached(errorClass, format, jsonString, pos);
        throw PRaiseNode.raiseExceptionObjectStatic(raisingNode, exception, false);
    }

    @InliningCutoff
    private static RuntimeException stopIteration(Node inliningTarget, InlinedBranchProfile errorProfile, Node raisingNode, Object value) {
        errorProfile.enter(inliningTarget);
        throw stopIteration(raisingNode, value);
    }

    @TruffleBoundary
    private static RuntimeException stopIteration(Node raisingNode, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        Object exception = CallNode.executeUncached(PythonContext.get(raisingNode).lookupType(PythonBuiltinClassType.StopIteration), value);
        throw PRaiseNode.raiseExceptionObjectStatic(raisingNode, exception, false);
    }
}
