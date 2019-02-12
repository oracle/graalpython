/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PySequenceArrayWrapper.class)
public class PySequenceArrayWrapperMR {

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child GetTypeIDNode getTypeIDNode = GetTypeIDNode.create();

        public Object access(PySequenceArrayWrapper object) {
            return getTypeIDNode.execute(object.getDelegate());
        }

    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private CExtNodes.IsPointerNode pIsPointerNode = CExtNodes.IsPointerNode.create();

        boolean access(PySequenceArrayWrapper obj) {
            return pIsPointerNode.execute(obj);
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private Node asPointerNode;

        long access(PySequenceArrayWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (nativePointer instanceof TruffleObject) {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
            return (long) nativePointer;
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetTypeIDNode extends CExtBaseNode {

        @Child private PCallNativeNode callUnaryNode = PCallNativeNode.create();

        @CompilationFinal private TruffleObject funGetByteArrayTypeID;
        @CompilationFinal private TruffleObject funGetPtrArrayTypeID;

        public abstract Object execute(Object delegate);

        protected Object callGetByteArrayTypeID() {
            return callGetArrayTypeID(importCAPISymbol(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID));
        }

        protected Object callGetPtrArrayTypeID() {
            return callGetArrayTypeID(importCAPISymbol(NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID));
        }

        private Object callGetByteArrayTypeIDCached() {
            if (funGetByteArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetByteArrayTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID);
            }
            return callGetArrayTypeID(funGetByteArrayTypeID);
        }

        private Object callGetPtrArrayTypeIDCached() {
            if (funGetPtrArrayTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetPtrArrayTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID);
            }
            return callGetArrayTypeID(funGetPtrArrayTypeID);
        }

        private Object callGetArrayTypeID(TruffleObject fun) {
            // We use length=0 indicating an unknown length. This allows us to reuse the type but
            // Sulong will report the wrong length via interop for a pointer to this object.
            return callUnaryNode.execute(fun, new Object[]{0});
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "hasByteArrayContent(object)")
        Object doByteArray(@SuppressWarnings("unused") PSequence object,
                        @Cached("callGetByteArrayTypeID()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "hasByteArrayContent(object)", replaces = "doByteArray")
        Object doByteArrayMultiCtx(@SuppressWarnings("unused") Object object) {
            return callGetByteArrayTypeIDCached();
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "!hasByteArrayContent(object)")
        Object doPtrArray(@SuppressWarnings("unused") Object object,
                        @Cached("callGetPtrArrayTypeID()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "!hasByteArrayContent(object)", replaces = "doPtrArray")
        Object doPtrArrayMultiCtx(@SuppressWarnings("unused") PSequence object) {
            return callGetPtrArrayTypeIDCached();
        }

        protected static boolean hasByteArrayContent(Object object) {
            return object instanceof PBytes || object instanceof PByteArray;
        }

        public static GetTypeIDNode create() {
            return GetTypeIDNodeGen.create();
        }
    }

}
