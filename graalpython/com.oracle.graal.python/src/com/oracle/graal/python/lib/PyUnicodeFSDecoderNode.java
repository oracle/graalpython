/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

/**
 * Equivalent of CPython's {@code PyUnicode_FSDecoder}. Converts a string, bytes or path-like object
 * to a string path.
 */
@GenerateUncached
public abstract class PyUnicodeFSDecoderNode extends PNodeWithContext {
    public abstract String execute(Frame frame, Object object);

    @Specialization
    String doString(String object) {
        return checkString(object);
    }

    @Specialization
    String doPString(PString object,
                    @Cached CastToJavaStringNode cast) {
        return checkString(cast.execute(object));
    }

    @Specialization(limit = "1")
    String doBytes(PBytes object,
                    @CachedLibrary("object") PythonBufferAccessLibrary bufferLib) {
        return checkString(fromBuffer(object, bufferLib));
    }

    private static String fromBuffer(Object object, PythonBufferAccessLibrary bufferLib) {
        // TODO PyUnicode_DecodeFSDefault
        return PythonUtils.newString(bufferLib.getInternalOrCopiedByteArray(object), 0, bufferLib.getBufferLength(object));
    }

    @Fallback
    String doPathLike(VirtualFrame frame, Object object,
                    @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                    @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                    @Cached PyOSFSPathNode fspathNode,
                    @Cached PyUnicodeFSDecoderNode recursive) {
        Object path;
        if (bufferAcquireLib.hasBuffer(object)) {
            Object buffer = bufferAcquireLib.acquireReadonly(object);
            try {
                path = fromBuffer(buffer, bufferLib);
            } finally {
                bufferLib.release(buffer);
            }
        } else {
            // The node ensures that it is a string or bytes
            path = fspathNode.execute(frame, object);
        }
        assert path instanceof String || path instanceof PString || path instanceof PBytes;
        return recursive.execute(frame, path);
    }

    @TruffleBoundary
    private String checkString(String str) {
        if (str.indexOf(0) > 0) {
            throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
        }
        return str;
    }
}
