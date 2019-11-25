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
package com.oracle.graal.python.builtins.objects.memoryview;

import com.oracle.graal.python.builtins.objects.bytes.PythonBufferLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonBufferLibrary.class)
public class PMemoryView extends PythonBuiltinObject {

    static final String C_MEMORYVIEW = "__c_memoryview";

    public PMemoryView(LazyPythonClass cls, @SuppressWarnings("unused") Object obj) {
        super(cls);
    }

    @ExportMessage
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength(
                    @Shared("readNativeMemoryViewNode") @Cached ReadAttributeFromDynamicObjectNode readNativeMemoryViewNode,
                    @Shared("lenNode") @Cached LookupAndCallUnaryDynamicNode lenNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntNode castToIntNode) {
        Object nativeMemoryViewObject = readNativeMemoryViewNode.execute(this, C_MEMORYVIEW);
        return castToIntNode.execute(lenNode.executeObject(nativeMemoryViewObject, SpecialMethodNames.__LEN__));
    }

    @ExportMessage
    byte[] getBufferBytes(
                    @Shared("readNativeMemoryViewNode") @Cached ReadAttributeFromDynamicObjectNode readNativeMemoryViewNode,
                    @Shared("lenNode") @Cached LookupAndCallUnaryDynamicNode lenNode,
                    @Shared("castToIntNode") @Cached CastToJavaIntNode castToIntNode,
                    @Cached PInteropSubscriptNode subscriptNode,
                    @Cached CastToByteNode castToByteNode) {
        Object nativeMemoryViewObject = readNativeMemoryViewNode.execute(this, C_MEMORYVIEW);
        int len = castToIntNode.execute(lenNode.executeObject(nativeMemoryViewObject, SpecialMethodNames.__LEN__));
        byte[] data = new byte[len];
        for (int i = 0; i < data.length; i++) {
            data[i] = castToByteNode.execute(null, subscriptNode.execute(nativeMemoryViewObject, i));
        }
        return data;
    }
}
