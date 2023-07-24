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

import static com.oracle.graal.python.nodes.ErrorMessages.DECODING_STR_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Equivalent of CPython's {@code PyUnicode_FromEncodedObject}.
 */
@GenerateUncached
public abstract class PyUnicodeFromEncodedObject extends PNodeWithIndirectCall {
    public abstract Object execute(Frame frame, Object object, Object encoding, Object errors);

    @Specialization
    Object doBytes(VirtualFrame frame, PBytes object, Object encoding, Object errors,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached InlinedConditionProfile emptyStringProfile,
                    @Shared @Cached PyUnicodeDecode decode) {
        // Decoding bytes objects is the most common case and should be fast
        if (emptyStringProfile.profile(inliningTarget, object.getSequenceStorage().length() == 0)) {
            return T_EMPTY_STRING;
        }
        return decode.execute(frame, object, encoding, errors);
    }

    @Specialization
    @SuppressWarnings("unused")
    Object doString(VirtualFrame frame, TruffleString object, Object encoding, Object errors,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonBuiltinClassType.TypeError, DECODING_STR_NOT_SUPPORTED);
    }

    @Specialization
    @SuppressWarnings("unused")
    Object doPString(VirtualFrame frame, PString object, Object encoding, Object errors,
                    @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonBuiltinClassType.TypeError, DECODING_STR_NOT_SUPPORTED);
    }

    @Specialization(guards = {"!isPBytes(object)", "!isString(object)"}, limit = "3")
    @SuppressWarnings("truffle-static-method")
    Object doBuffer(VirtualFrame frame, Object object, Object encoding, Object errors,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached InlinedConditionProfile emptyStringProfile,
                    @Shared @Cached PyUnicodeDecode decode,
                    @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                    @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                    @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {

        Object buffer = bufferAcquireLib.acquireReadonly(object, frame, getContext(), getLanguage(), this);
        try {
            int len = bufferLib.getBufferLength(buffer);
            if (emptyStringProfile.profile(inliningTarget, len == 0)) {
                return T_EMPTY_STRING;
            }
            // TODO GR-37601: this is probably never executed
            TruffleString utf8 = fromByteArrayNode.execute(bufferLib.getInternalOrCopiedByteArray(object), 0, len, Encoding.UTF_8, true);
            final TruffleString unicode = switchEncodingNode.execute(utf8, TS_ENCODING);
            return decode.execute(frame, unicode, encoding, errors);
        } finally {
            bufferLib.release(object, frame, this);
        }
    }

    @NeverDefault
    public static PyUnicodeFromEncodedObject create() {
        return PyUnicodeFromEncodedObjectNodeGen.create(Truffle.getRuntime().createAssumption(), Truffle.getRuntime().createAssumption());
    }
}
