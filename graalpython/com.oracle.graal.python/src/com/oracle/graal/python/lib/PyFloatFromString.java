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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.buffer.BufferAcquireGenerateUncachedNode;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyFloat_FromString}. Converts a string to a python float (Java
 * {@code double}). Raises {@code ValueError} when the conversion fails.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyFloatFromString extends PNodeWithContext {
    public abstract double execute(Frame frame, Node inliningTarget, Object obj);

    public abstract double execute(Frame frame, Node inliningTarget, TruffleString obj);

    @Specialization
    static double doString(VirtualFrame frame, Node inliningTarget, TruffleString object,
                    @Cached(inline = false) TruffleString.ToJavaStringNode toJavaStringNode,
                    @Shared @Cached PyObjectReprAsTruffleStringNode reprNode,
                    @Shared @Cached PRaiseNode.Lazy raiseNode) {
        return convertStringToDouble(frame, inliningTarget, toJavaStringNode.execute(object), object, reprNode, raiseNode);
    }

    @Specialization
    static double doGeneric(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached(parameters = "3", inline = false) BufferAcquireGenerateUncachedNode acquireNode,
                    @Cached(inline = false) CastToJavaStringNode cast,
                    @Shared @Cached PyObjectReprAsTruffleStringNode reprNode,
                    @Shared @Cached PRaiseNode.Lazy raiseNode) {
        String string = null;
        try {
            string = cast.execute(object);
        } catch (CannotCastException e) {
            Object buffer = null;
            try {
                buffer = acquireNode.acquireReadonly(frame, object);
            } catch (PException e1) {
                // fallthrough
            }
            if (buffer != null) {
                try {
                    PythonBufferAccessLibrary accessLib = acquireNode.getAccessLib();
                    byte[] bytes = accessLib.getInternalOrCopiedByteArray(buffer);
                    int len = accessLib.getBufferLength(buffer);
                    string = newString(bytes, 0, len);
                } finally {
                    acquireNode.release(frame, buffer);
                }
            }
        }
        if (string != null) {
            return convertStringToDouble(frame, inliningTarget, string, object, reprNode, raiseNode);
        }
        throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_NUMBER, "float()", object);
    }

    @TruffleBoundary(allowInlining = true)
    private static String newString(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length);
    }

    private static double convertStringToDouble(VirtualFrame frame, Node inliningTarget, String src, Object origObj, PyObjectReprAsTruffleStringNode reprNode, PRaiseNode.Lazy raiseNode) {
        String str = FloatUtils.removeUnicodeAndUnderscores(src);
        // Adapted from CPython's float_from_string_inner
        if (str != null) {
            int len = str.length();
            int offset = FloatUtils.skipAsciiWhitespace(str, 0, len);
            FloatUtils.StringToDoubleResult res = FloatUtils.stringToDouble(str, offset, len);
            if (res != null) {
                int end = FloatUtils.skipAsciiWhitespace(str, res.position, len);
                if (end == len) {
                    return res.value;
                }
            }
        }
        TruffleString repr;
        try {
            repr = reprNode.execute(frame, inliningTarget, origObj);
        } catch (PException e) {
            // Failed to format the message. Mirrors CPython behavior when the repr fails
            throw raiseNode.get(inliningTarget).raise(ValueError);
        }
        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_FLOAT, repr);
    }

}
