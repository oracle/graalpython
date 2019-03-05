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

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_PTR_ARRAY_TYPE_ID;

import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
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

    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetTypeIDNode extends CExtBaseNode {

        public abstract Object execute(Object delegate);

        protected static Object callGetByteArrayTypeIDUncached() {
            return PCallCapiFunction.getUncached().execute(FUN_GET_BYTE_ARRAY_TYPE_ID, 0);
        }

        protected static Object callGetPtrArrayTypeIDUncached() {
            return PCallCapiFunction.getUncached().execute(FUN_GET_PTR_ARRAY_TYPE_ID, 0);
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "hasByteArrayContent(object)")
        Object doByteArray(@SuppressWarnings("unused") PSequence object,
                        @Exclusive @Cached("callGetByteArrayTypeIDUncached()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "hasByteArrayContent(object)", replaces = "doByteArray")
        Object doByteArrayMultiCtx(@SuppressWarnings("unused") Object object,
                        @Shared("callUnaryNode") @Cached PCallCapiFunction callUnaryNode) {
            return callUnaryNode.execute(FUN_GET_BYTE_ARRAY_TYPE_ID, 0);
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "!hasByteArrayContent(object)")
        Object doPtrArray(@SuppressWarnings("unused") Object object,
                        @Exclusive @Cached("callGetPtrArrayTypeIDUncached()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "!hasByteArrayContent(object)", replaces = "doPtrArray")
        Object doPtrArrayMultiCtx(@SuppressWarnings("unused") PSequence object,
                        @Shared("callUnaryNode") @Cached PCallCapiFunction callUnaryNode) {
            return callUnaryNode.execute(FUN_GET_PTR_ARRAY_TYPE_ID, 0);
        }

        protected static boolean hasByteArrayContent(Object object) {
            return object instanceof PBytes || object instanceof PByteArray;
        }

        public static GetTypeIDNode create() {
            return GetTypeIDNodeGen.create();
        }
    }

}
