/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PyUnicodeData;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PyUnicodeState;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PyUnicodeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PyUnicodeWrapperMRFactory.PyUnicodeToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.cext.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PyUnicodeWrapper.class)
public class PyUnicodeWrapperMR {

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private UnicodeAsWideCharNode asWideCharNode;
        @Child private CExtNodes.SizeofWCharNode sizeofWcharNode;

        public Object access(PyUnicodeData object, String key) {
            int elementSize;
            switch (key) {
                case NativeMemberNames.UNICODE_DATA_ANY:
                case NativeMemberNames.UNICODE_DATA_LATIN1:
                case NativeMemberNames.UNICODE_DATA_UCS2:
                case NativeMemberNames.UNICODE_DATA_UCS4:
                    elementSize = (int) getSizeofWcharNode().execute();
                    PString s = object.getDelegate();
                    return new PySequenceArrayWrapper(getAsWideCharNode().execute(s, elementSize, s.len()), elementSize);
            }
            throw UnknownIdentifierException.raise(key);
        }

        public Object access(PyUnicodeState object, String key) {
            // padding(24), ready(1), ascii(1), compact(1), kind(3), interned(2)
            int value = 0b000000000000000000000000_1_0_0_000_00;
            if (onlyAscii(object.getDelegate().getValue())) {
                value |= 0b1_0_000_00;
            }
            value |= ((int) getSizeofWcharNode().execute() << 2) & 0b11100;
            switch (key) {
                case NativeMemberNames.UNICODE_STATE_INTERNED:
                case NativeMemberNames.UNICODE_STATE_KIND:
                case NativeMemberNames.UNICODE_STATE_COMPACT:
                case NativeMemberNames.UNICODE_STATE_ASCII:
                case NativeMemberNames.UNICODE_STATE_READY:
                    // it's a bit field; so we need to return the whole 32-bit word
                    return value;
            }
            throw UnknownIdentifierException.raise(key);
        }

        private UnicodeAsWideCharNode getAsWideCharNode() {
            if (asWideCharNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asWideCharNode = insert(UnicodeAsWideCharNode.create(0));
            }
            return asWideCharNode;
        }

        private CExtNodes.SizeofWCharNode getSizeofWcharNode() {
            if (sizeofWcharNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sizeofWcharNode = insert(CExtNodes.SizeofWCharNode.create());
            }
            return sizeofWcharNode;
        }

        @CompilationFinal private CharsetEncoder asciiEncoder;

        public boolean isPureAscii(String v) {
            return asciiEncoder.canEncode(v);
        }

        private boolean onlyAscii(String value) {
            if (asciiEncoder == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asciiEncoder = Charset.forName("US-ASCII").newEncoder();
            }
            return doCheck(value, asciiEncoder);
        }

        @TruffleBoundary
        private static boolean doCheck(String value, CharsetEncoder asciiEncoder) {
            return asciiEncoder.canEncode(value);
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode = ToPyObjectNode.create();

        Object access(PyUnicodeWrapper obj) {
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj.getDelegate()));
            }
            return obj;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private Node isPointerNode;

        boolean access(PyUnicodeWrapper obj) {
            return obj.isNative() && (!(obj.getNativePointer() instanceof TruffleObject) || ForeignAccess.sendIsPointer(getIsPointerNode(), (TruffleObject) obj.getNativePointer()));
        }

        private Node getIsPointerNode() {
            if (isPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPointerNode = insert(Message.IS_POINTER.createNode());
            }
            return isPointerNode;
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private PAsPointerNode pAsPointerNode = PAsPointerNode.create();

        long access(PyUnicodeWrapper obj) {
            return pAsPointerNode.execute(obj);
        }
    }

    abstract static class PyUnicodeToNativeNode extends TransformToNativeNode {
        @CompilationFinal private TruffleObject derefHandleIntrinsic;
        @Child private PCallNativeNode callNativeUnary;
        @Child private PCallNativeNode callNativeBinary;
        @Child private CExtNodes.ToSulongNode toSulongNode;

        public abstract Object execute(Object value);

        @Specialization
        Object doUnicodeWrapper(PyUnicodeWrapper object) {
            return ensureIsPointer(callUnaryIntoCapi(getPyObjectHandle_ForJavaType(), object));
        }

        private TruffleObject getPyObjectHandle_ForJavaType() {
            if (derefHandleIntrinsic == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                derefHandleIntrinsic = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_DEREF_HANDLE);
            }
            return derefHandleIntrinsic;
        }

        private Object callUnaryIntoCapi(TruffleObject fun, Object arg) {
            if (callNativeUnary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeUnary = insert(PCallNativeNode.create(1));
            }
            return callNativeUnary.execute(fun, new Object[]{arg});
        }

        public static PyUnicodeToNativeNode create() {
            return PyUnicodeToNativeNodeGen.create();
        }
    }

}
