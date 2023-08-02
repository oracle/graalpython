/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.llvm;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

import sun.misc.Unsafe;

abstract class GraalHPyLLVMNodes {

    private GraalHPyLLVMNodes() {
    }

    @GenerateUncached
    @GenerateInline(false)
    abstract static class HPyLLVMFromCharPointerNode extends HPyFromCharPointerNode {

        @Specialization
        @SuppressWarnings("unused")
        static TruffleString doCStringWrapper(GraalHPyContext hpyContext, CStringWrapper cStringWrapper, int n, Encoding encoding, boolean copy) {
            return cStringWrapper.getString();
        }

        @Specialization
        static TruffleString doCByteArrayWrapper(@SuppressWarnings("unused") GraalHPyContext hpyContext, CByteArrayWrapper cByteArrayWrapper, int n, Encoding encoding, boolean copy,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            CompilerAsserts.partialEvaluationConstant(encoding);
            CompilerAsserts.partialEvaluationConstant(copy);
            byte[] byteArray = cByteArrayWrapper.getByteArray();
            int length = n < 0 ? byteArray.length : n;
            return switchEncodingNode.execute(fromByteArrayNode.execute(byteArray, 0, length, encoding, copy), TS_ENCODING);
        }

        @Specialization(guards = {"!isCArrayWrapper(charPtr)", "isPointer(lib, charPtr)"})
        static TruffleString doPointer(GraalHPyContext hpyContext, Object charPtr, int n, Encoding encoding, boolean copy,
                        @Shared @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached TruffleString.FromNativePointerNode fromNative) {
            CompilerAsserts.partialEvaluationConstant(encoding);
            CompilerAsserts.partialEvaluationConstant(copy);
            long pointer;
            try {
                pointer = lib.asPointer(charPtr);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            int length;
            if (n < 0) {
                length = 0;
                /*
                 * We use 'PythonContext.getUnsafe()' here to ensure that native access is allowed
                 * because this specialization can be reached if 'charPtr' is not a CArrayWrapper
                 * but a pointer. An attacker could create a TruffleObject that answers
                 * 'isPointer()' with 'true' (but isn't really a native pointer).
                 */
                Unsafe unsafe = hpyContext.getContext().getUnsafe();
                while (unsafe.getByte(pointer + length) != 0) {
                    length++;
                }
            } else {
                length = n;
            }
            return fromNative.execute(charPtr, 0, length, encoding, copy);
        }

        @Specialization(guards = {"!isCArrayWrapper(charPtr)", "!isPointer(lib, charPtr)"})
        static TruffleString doForeignArray(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object charPtr, int n, Encoding encoding, @SuppressWarnings("unused") boolean copy,
                        @Shared @CachedLibrary(limit = "2") InteropLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary elementLib,
                        @Cached GraalHPyLLVMCallHelperFunctionNode callHelperFunctionNode,
                        @Cached GetByteArrayNode getByteArrayNode,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            CompilerAsserts.partialEvaluationConstant(encoding);
            CompilerAsserts.partialEvaluationConstant(copy);

            Object typedCharPtr;
            int length;
            try {
                if (!lib.hasArrayElements(charPtr)) {
                    /*
                     * If the foreign object does not have array elements, we assume it is an LLVM
                     * pointer where we can attach a type. We use size 'n' if available, otherwise
                     * we determine the size by looking for the zero-byte.
                     */
                    int size = n < 0 ? Integer.MAX_VALUE : n;
                    typedCharPtr = callHelperFunctionNode.call(hpyContext, GraalHPyNativeSymbol.GRAAL_HPY_FROM_I8_ARRAY, charPtr, size);
                    if (n < 0) {
                        length = 0;
                        while (elementLib.asByte(lib.readArrayElement(typedCharPtr, length)) != 0) {
                            length++;
                        }
                    } else {
                        length = n;
                    }
                } else {
                    /*
                     * Simple case: the foreign object has array elements, so just use the array
                     * size and read the elements.
                     */
                    typedCharPtr = charPtr;
                    length = n < 0 ? PInt.intValueExact(lib.getArraySize(charPtr)) : n;
                }
                assert lib.hasArrayElements(typedCharPtr);
                assert length >= 0;
                byte[] bytes = getByteArrayNode.execute(typedCharPtr, length);
                // since we created a fresh byte array, we don't need to copy it
                return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, 0, bytes.length, encoding, false), TS_ENCODING);
            } catch (InteropException | OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        static boolean isCArrayWrapper(Object object) {
            return object instanceof CArrayWrapper || object instanceof PySequenceArrayWrapper;
        }

        static boolean isPointer(InteropLibrary lib, Object object) {
            return lib.isPointer(object);
        }
    }
}
