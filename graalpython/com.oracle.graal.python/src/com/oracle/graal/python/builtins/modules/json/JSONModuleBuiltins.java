/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins.IntRef;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToJavaStringCheckedNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
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
                        @Bind Node inliningTarget,
                        @Cached CastToJavaStringCheckedNode castString,
                        @Bind PythonLanguage language) {
            IntRef nextIdx = new IntRef();
            TruffleString result = JSONScannerBuiltins.scanStringUnicode(castString.cast(inliningTarget, string, ErrorMessages.FIRST_ARG_MUST_BE_STRING_NOT_P, string), end, strict, nextIdx,
                            this);
            return PFactory.createTuple(language, new Object[]{result, nextIdx.value});
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
        static TruffleString call(TruffleString string,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int len = string.byteLength(TS_ENCODING);
                // 12.5% overallocated, TruffleStringBuilder.ToStringNode will copy anyway
                TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING, len + (len >> 3) + 2);
                JSONUtils.appendString(string, createCodePointIteratorNode.execute(string, TS_ENCODING), builder, false, nextNode, appendCodePointNode, appendStringNode, substringNode);
                return toStringNode.execute(builder);
            } catch (OutOfMemoryError | NegativeArraySizeException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.OverflowError, ErrorMessages.STR_TOO_LONG_TO_ESCAPE);
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
        static TruffleString call(TruffleString string,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int len = string.byteLength(TS_ENCODING);
                // 12.5% overallocated, TruffleStringBuilder.ToStringNode will copy anyway
                TruffleStringBuilder builder = TruffleStringBuilder.create(TS_ENCODING, len + (len >> 3) + 2);
                JSONUtils.appendString(string, createCodePointIteratorNode.execute(string, TS_ENCODING), builder, true,
                                nextNode, appendCodePointNode, appendStringNode, substringNode);
                return toStringNode.execute(builder);
            } catch (OutOfMemoryError | NegativeArraySizeException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.OverflowError, ErrorMessages.STR_TOO_LONG_TO_ESCAPE);
            }
        }
    }
}
