/* Copyright (c) 2020, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins.IntRef;
import com.oracle.graal.python.builtins.modules.json.PJSONEncoder.FastEncode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToJavaStringCheckedNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(defineModule = "_json")
public final class JSONModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(SpecialAttributeNames.T___DOC__, "json speedups\n");
        addBuiltinConstant("make_scanner", core.lookupType(PythonBuiltinClassType.JSONScanner));
        addBuiltinConstant("make_encoder", core.lookupType(PythonBuiltinClassType.JSONEncoder));
        super.initialize(core);

    }

    static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    @Builtin(name = "scanstring", minNumOfPositionalArgs = 2, parameterNames = {"string", "end", "strict"}, //
                    doc = "scanstring(string, end, strict=True) -> (string, end)\n" +
                                    "\n" +
                                    "Scan the string s for a JSON string. End is the index of the\n" +
                                    "character in s after the quote that started the JSON string.\n" +
                                    "Unescapes all valid JSON string escape sequences and raises ValueError\n" +
                                    "on attempt to decode an invalid string. If strict is False then literal\n" +
                                    "control characters are allowed in the string.\n" +
                                    "\n" +
                                    "Returns a tuple of the decoded string and the index of the character in s\n" +
                                    "after the end quote.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "strict", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "true", useDefaultForNone = true)
    abstract static class ScanString extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONModuleBuiltinsClinicProviders.ScanStringClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object call(Object string, int end, boolean strict,
                        @Cached CastToJavaStringCheckedNode castString,
                        @Cached PythonObjectFactory factory) {
            IntRef nextIdx = new IntRef();
            TruffleString result = JSONScannerBuiltins.scanStringUnicode(castString.cast(string, ErrorMessages.FIRST_ARG_MUST_BE_STRING_NOT_P, string), end, strict, nextIdx,
                            this);
            return factory.createTuple(new Object[]{result, nextIdx.value});
        }
    }

    @Builtin(name = "encode_basestring", parameterNames = {"string"}, //
                    doc = "encode_basestring(string) -> string\n" +
                                    "\n" +
                                    "Return a JSON representation of a Python string")
    @GenerateNodeFactory
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class EncodeBaseString extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONModuleBuiltinsClinicProviders.EncodeBaseStringClinicProviderGen.INSTANCE;
        }

        @Specialization
        TruffleString call(TruffleString string,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            try {
                int len = string.byteLength(TS_ENCODING);
                // 12.5% overallocated, TruffleStringBuilder.ToStringNode will copy anyway
                TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING, len + (len >> 3) + 2);
                JSONUtils.appendString(string, createCodePointIteratorNode.execute(string, TS_ENCODING), builder, false, nextNode, appendCodePointNode, appendStringNode, substringNode);
                return toStringNode.execute(builder);
            } catch (OutOfMemoryError | NegativeArraySizeException e) {
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.STR_TOO_LONG_TO_ESCAPE);
            }
        }

    }

    @Builtin(name = "encode_basestring_ascii", parameterNames = {"string"}, //
                    doc = "encode_basestring_ascii(string) -> string\n" +
                                    "\n" +
                                    "Return an ASCII-only JSON representation of a Python string")
    @GenerateNodeFactory
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class EncodeBaseStringAscii extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONModuleBuiltinsClinicProviders.EncodeBaseStringAsciiClinicProviderGen.INSTANCE;
        }

        @Specialization
        TruffleString call(TruffleString string,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            try {
                int len = string.byteLength(TS_ENCODING);
                // 12.5% overallocated, TruffleStringBuilder.ToStringNode will copy anyway
                TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING, len + (len >> 3) + 2);
                JSONUtils.appendString(string, createCodePointIteratorNode.execute(string, TS_ENCODING), builder, true,
                                nextNode, appendCodePointNode, appendStringNode, substringNode);
                return toStringNode.execute(builder);
            } catch (OutOfMemoryError | NegativeArraySizeException e) {
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.STR_TOO_LONG_TO_ESCAPE);
            }
        }
    }

    @Builtin(name = "make_scanner", parameterNames = {"$cls", "context"}, constructsClass = PythonBuiltinClassType.JSONScanner, //
                    doc = "_iterencode(obj, _current_indent_level) -> iterable")
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
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castStrict,
                        @Cached PythonObjectFactory factory) {

            boolean strict = castStrict.executeBoolean(frame, getStrict.execute(frame, context));
            Object objectHook = getObjectHook.execute(frame, context);
            Object objectPairsHook = getObjectPairsHook.execute(frame, context);
            Object parseFloat = getParseFloat.execute(frame, context);
            Object parseInt = getParseInt.execute(frame, context);
            Object parseConstant = getParseConstant.execute(frame, context);
            return factory.createJSONScanner(cls, strict, objectHook, objectPairsHook, parseFloat, parseInt, parseConstant);
        }
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
            return JSONModuleBuiltinsClinicProviders.MakeEncoderClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        protected PJSONEncoder doNew(Object cls, Object markers, Object defaultFn, Object encoder, Object indent, TruffleString keySeparator, TruffleString itemSeparator, boolean sortKeys,
                        boolean skipKeys, boolean allowNan,
                        @Cached PythonObjectFactory factory) {
            if (markers != PNone.NONE && !(markers instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.MAKE_ENCODER_ARG_1_MUST_BE_DICT, markers);
            }

            FastEncode fastEncode = FastEncode.None;
            Object encoderAsFun = encoder;
            if (encoder instanceof PBuiltinMethod encoderMethod) {
                encoderAsFun = encoderMethod.getFunction();
            }
            if (encoderAsFun instanceof PBuiltinFunction function) {
                Class<? extends PythonBuiltinBaseNode> nodeClass = function.getNodeClass();
                if (nodeClass != null) {
                    if (JSONModuleBuiltins.EncodeBaseString.class.isAssignableFrom(nodeClass)) {
                        fastEncode = FastEncode.FastEncode;
                    } else if (JSONModuleBuiltins.EncodeBaseStringAscii.class.isAssignableFrom(nodeClass)) {
                        fastEncode = FastEncode.FastEncodeAscii;
                    }
                }
            }
            return factory.createJSONEncoder(cls, markers, defaultFn, encoder, indent, keySeparator, itemSeparator, sortKeys, skipKeys, allowNan, fastEncode);
        }
    }
}
