/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Equivalent of CPython's {@code PyUnicode_FromEncodedObject}.
 */
@GenerateUncached
public abstract class PyUnicodeFromEncodedObject extends PNodeWithIndirectCall {
    public abstract Object execute(Frame frame, Object object, Object encoding, Object errors);

    @Specialization
    Object doBytes(VirtualFrame frame, PBytes object, Object encoding, Object errors,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached ConditionProfile emptyStringProfile,
                    @Cached PyUnicodeDecode decode) {
        // Decoding bytes objects is the most common case and should be fast
        if (emptyStringProfile.profile(lenNode.execute(object.getSequenceStorage()) == 0)) {
            return PythonUtils.EMPTY_STRING;
        }
        return decode.execute(frame, object, encoding, errors);
    }

    @Specialization
    @SuppressWarnings("unused")
    Object doString(VirtualFrame frame, String object, Object encoding, Object errors,
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
    Object doBuffer(VirtualFrame frame, Object object, Object encoding, Object errors,
                    @Cached ConditionProfile emptyStringProfile,
                    @Cached PyUnicodeDecode decode,
                    @CachedLibrary("object") PythonBufferAccessLibrary bufferLib) {
        try {
            int len = bufferLib.getBufferLength(object);
            if (emptyStringProfile.profile(len == 0)) {
                return PythonUtils.EMPTY_STRING;
            }
            final String unicode = PythonUtils.newString(bufferLib.getInternalOrCopiedByteArray(object), 0, len);
            return decode.execute(frame, unicode, encoding, errors);
        } finally {
            bufferLib.release(object, frame, this);
        }
    }

    public static PyUnicodeFromEncodedObject create() {
        return PyUnicodeFromEncodedObjectNodeGen.create(Truffle.getRuntime().createAssumption(), Truffle.getRuntime().createAssumption());
    }
}
