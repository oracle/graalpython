/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.ErrorMessages.ENCODER_S_RETURNED_S_INSTEAD_OF_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ENCODER_RETURNED_P_INSTEAD_OF_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeWarning;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyUnicode_AsEncodedString}.
 */
@GenerateInline
@GenerateCached(false)
public abstract class PyUnicodeAsEncodedString extends PNodeWithContext {
    public static final String ENC_UTF_8 = "utf_8";
    public static final String ENC_UTF_16 = "utf_16";
    public static final String ENC_UTF_32 = "utf_32";
    public static final String ENC_UTF8 = "utf8";
    public static final String ENC_UTF16 = "utf16";
    public static final String ENC_UTF32 = "utf32";
    public static final String ENC_ASCII = "ascii";
    public static final String ENC_US_ASCII = "us_ascii";
    public static final String ENC_LATIN1 = "latin1";
    public static final String ENC_LATIN_1 = "latin_1";
    public static final String ENC_ISO_8859_1 = "iso_8859_1";
    public static final String ENC_ISO8859_1 = "iso8859_1";

    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object object, Object encoding, Object errors);

    public static boolean isCommon(TruffleString encoding, TruffleString.ToJavaStringNode toJavaStringNode) {
        // TODO GR-37601: review
        switch (toLowerCase(toJavaStringNode.execute(encoding))) {
            case ENC_ASCII:
            case ENC_ISO8859_1:
            case ENC_ISO_8859_1:
            case ENC_LATIN1:
            case ENC_LATIN_1:
            case ENC_US_ASCII:
            case ENC_UTF_8:
            case ENC_UTF_16:
            case ENC_UTF_32:
            case ENC_UTF8:
            case ENC_UTF16:
            case ENC_UTF32:
                return true;
            default:
                return false;
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static String toLowerCase(String s) {
        return s.toLowerCase();
    }

    @Specialization(guards = {"isString(unicode)", "isCommon(encoding, toJavaStringNode)"})
    static Object doCommon(VirtualFrame frame, Object unicode, TruffleString encoding, TruffleString errors,
                    @Shared @Cached(inline = false) CodecsModuleBuiltins.CodecsEncodeNode encodeNode,
                    @SuppressWarnings("unused") @Shared("ts2js") @Cached(inline = false) TruffleString.ToJavaStringNode toJavaStringNode) {
        return encodeNode.execute(frame, unicode, encoding, errors);
    }

    @Specialization(guards = {"isString(unicode)", "!isCommon(encoding, toJavaStringNode)"})
    static Object doRegistry(VirtualFrame frame, Node inliningTarget, Object unicode, TruffleString encoding, TruffleString errors,
                    @Exclusive @Cached(inline = false) CodecsModuleBuiltins.EncodeNode encodeNode,
                    @Cached InlinedConditionProfile isBytesProfile,
                    @Cached InlinedConditionProfile isByteArrayProfile,
                    @Cached SequenceStorageNodes.CopyNode copyNode,
                    @Cached(inline = false) WarningsModuleBuiltins.WarnNode warnNode,
                    @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                    @SuppressWarnings("unused") @Shared("ts2js") @Cached(inline = false) TruffleString.ToJavaStringNode toJavaStringNode) {
        final Object v = encodeNode.execute(frame, unicode, encoding, errors);
        // the normal path
        if (isBytesProfile.profile(inliningTarget, v instanceof PBytes)) {
            return v;
        }
        // If the codec returns a buffer, raise a warning and convert to bytes
        if (isByteArrayProfile.profile(inliningTarget, v instanceof PByteArray)) {
            warnNode.warnFormat(frame, RuntimeWarning, ENCODER_S_RETURNED_S_INSTEAD_OF_BYTES, encoding, "bytearray");
            return PythonContext.get(inliningTarget).factory().createBytes(copyNode.execute(inliningTarget, ((PByteArray) v).getSequenceStorage()));
        }

        throw raiseNode.get(inliningTarget).raise(TypeError, S_ENCODER_RETURNED_P_INSTEAD_OF_BYTES, encoding, v);
    }

    @Specialization(guards = {"isString(unicode)", "isNoValue(encoding)"})
    static Object doNoEncoding(VirtualFrame frame, Object unicode, @SuppressWarnings("unused") PNone encoding, Object errors,
                    @Shared @Cached(inline = false) CodecsModuleBuiltins.CodecsEncodeNode encodeNode) {
        return encodeNode.execute(frame, unicode, ENC_UTF8, errors);
    }

    @Specialization(guards = "!isString(unicode)")
    @SuppressWarnings({"unused", "truffle-static-method"})
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object unicode, Object encoding, Object errors,
                    @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
        throw raiseNode.get(inliningTarget).raiseBadInternalCall();
    }
}
