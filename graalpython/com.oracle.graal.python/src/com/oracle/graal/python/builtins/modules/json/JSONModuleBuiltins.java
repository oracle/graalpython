/* Copyright (c) 2020, 2021, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins.IntRef;
import com.oracle.graal.python.builtins.modules.json.PJSONEncoder.FastEncode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToJavaStringCheckedNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = "_json")
public class JSONModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, "json speedups\n");
        builtinConstants.put("make_scanner", core.lookupType(PythonBuiltinClassType.JSONScanner));
        builtinConstants.put("make_encoder", core.lookupType(PythonBuiltinClassType.JSONEncoder));
        super.initialize(core);

    }

    static boolean isSimpleChar(char c) {
        return c >= ' ' && c <= '~' && c != '\\' && c != '"';
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
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            IntRef nextIdx = new IntRef();
            String result = JSONScannerBuiltins.scanStringUnicode(castString.execute(string, "first argument must be a string, not %p", new Object[]{string}), end, strict, nextIdx, raiseNode);
            return factory.createTuple(new Object[]{result.toString(), nextIdx.value});
        }
    }

    @Builtin(name = "encode_basestring", parameterNames = {"string"}, //
                    doc = "encode_basestring(string) -> string\n" +
                                    "\n" +
                                    "Return a JSON representation of a Python string")
    @GenerateNodeFactory
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.String)
    abstract static class EncodeBaseString extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONModuleBuiltinsClinicProviders.EncodeBaseStringClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        Object call(String string) {
            try {
                // 12.5% overallocated, StringBuilder.toString will always copy anyway
                StringBuilder builder = new StringBuilder(string.length() + (string.length() >> 3) + 2);
                appendString(string, builder);
                return builder.toString();
            } catch (OutOfMemoryError | NegativeArraySizeException e) {
                throw raise(PythonBuiltinClassType.OverflowError, "string is too long to escape");
            }
        }

        static void appendString(String string, StringBuilder builder) {
            builder.append('"');

            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                switch (c) {
                    case '\\':
                        builder.append('\\').append('\\');
                        break;
                    case '"':
                        builder.append('\\').append('"');
                        break;
                    case '\b':
                        builder.append('\\').append('b');
                        break;
                    case '\f':
                        builder.append('\\').append('f');
                        break;
                    case '\n':
                        builder.append('\\').append('n');
                        break;
                    case '\r':
                        builder.append('\\').append('r');
                        break;
                    case '\t':
                        builder.append('\\').append('t');
                        break;
                    default:
                        if (c <= 0x1f) {
                            builder.append("\\u00");
                            builder.append(Character.forDigit((c >> 4) & 0xf, 16));
                            builder.append(Character.forDigit(c & 0xf, 16));
                        } else {
                            builder.append(c);
                        }
                        break;
                }
            }
            builder.append('"');
        }
    }

    @Builtin(name = "encode_basestring_ascii", parameterNames = {"string"}, //
                    doc = "encode_basestring_ascii(string) -> string\n" +
                                    "\n" +
                                    "Return an ASCII-only JSON representation of a Python string")
    @GenerateNodeFactory
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.String)
    abstract static class EncodeBaseStringAscii extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONModuleBuiltinsClinicProviders.EncodeBaseStringAsciiClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        Object call(String string) {
            try {
                // 12.5% overallocated, StringBuilder.toString will always copy anyway
                StringBuilder builder = new StringBuilder(string.length() + (string.length() >> 3) + 2);
                appendString(string, builder);
                return builder.toString();
            } catch (OutOfMemoryError | NegativeArraySizeException e) {
                throw raise(PythonBuiltinClassType.OverflowError, "string is too long to escape");
            }
        }

        static void appendString(String string, StringBuilder builder) {
            builder.append('"');

            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                if (isSimpleChar(c)) {
                    builder.append(c);
                } else {
                    switch (c) {
                        case '\\':
                            builder.append('\\').append('\\');
                            break;
                        case '"':
                            builder.append('\\').append('"');
                            break;
                        case '\b':
                            builder.append('\\').append('b');
                            break;
                        case '\f':
                            builder.append('\\').append('f');
                            break;
                        case '\n':
                            builder.append('\\').append('n');
                            break;
                        case '\r':
                            builder.append('\\').append('r');
                            break;
                        case '\t':
                            builder.append('\\').append('t');
                            break;
                        default:
                            builder.append("\\u");
                            builder.append(Character.forDigit((c >> 12) & 0xf, 16));
                            builder.append(Character.forDigit((c >> 8) & 0xf, 16));
                            builder.append(Character.forDigit((c >> 4) & 0xf, 16));
                            builder.append(Character.forDigit(c & 0xf, 16));
                            break;
                    }
                }
            }
            builder.append('"');
        }
    }

    @Builtin(name = "make_scanner", parameterNames = {"$cls", "context"}, constructsClass = PythonBuiltinClassType.JSONScanner, //
                    doc = "_iterencode(obj, _current_indent_level) -> iterable")
    @GenerateNodeFactory
    public abstract static class MakeScanner extends PythonBinaryBuiltinNode {

        @Child private GetFixedAttributeNode getStrict = GetFixedAttributeNode.create("strict");
        @Child private GetFixedAttributeNode getObjectHook = GetFixedAttributeNode.create("object_hook");
        @Child private GetFixedAttributeNode getObjectPairsHook = GetFixedAttributeNode.create("object_pairs_hook");
        @Child private GetFixedAttributeNode getParseFloat = GetFixedAttributeNode.create("parse_float");
        @Child private GetFixedAttributeNode getParseInt = GetFixedAttributeNode.create("parse_int");
        @Child private GetFixedAttributeNode getParseConstant = GetFixedAttributeNode.create("parse_constant");

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
    @ArgumentClinic(name = "key_separator", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "item_separator", conversion = ArgumentClinic.ClinicConversion.String)
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
        protected PJSONEncoder doNew(Object cls, Object markers, Object defaultFn, Object encoder, Object indent, String keySeparator, String itemSeparator, boolean sortKeys, boolean skipKeys,
                        boolean allowNan,
                        @Cached PythonObjectFactory factory) {
            if (markers != PNone.NONE && !(markers instanceof PDict)) {
                throw raise(TypeError, "make_encoder() argument 1 must be dict or None, not %p", markers);
            }

            FastEncode fastEncode = FastEncode.None;
            if (encoder instanceof PBuiltinFunction) {
                PBuiltinFunction function = (PBuiltinFunction) encoder;
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
