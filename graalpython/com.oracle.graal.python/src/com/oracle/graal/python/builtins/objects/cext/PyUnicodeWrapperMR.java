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

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PyUnicodeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PyUnicodeWrapperMRFactory.PyUnicodeToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.ToPyObjectNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PyUnicodeWrapper.class)
public class PyUnicodeWrapperMR {

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode = ToPyObjectNode.create();
        @Child private InvalidateNativeObjectsAllManagedNode invalidateNode = InvalidateNativeObjectsAllManagedNode.create();

        Object access(PyUnicodeWrapper obj) {
            invalidateNode.execute();
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
            return obj;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private CExtNodes.IsPointerNode pIsPointerNode = CExtNodes.IsPointerNode.create();

        boolean access(PyUnicodeWrapper obj) {
            return pIsPointerNode.execute(obj);
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private PAsPointerNode pAsPointerNode = PAsPointerNode.create();

        long access(PyUnicodeWrapper obj) {
            return pAsPointerNode.execute(obj);
        }
    }

    abstract static class PyUnicodeToNativeNode extends CExtBaseNode {
        @CompilationFinal private TruffleObject derefHandleIntrinsic;
        @Child private PCallNativeNode callNativeUnary;
        @Child private PCallNativeNode callNativeBinary;
        @Child private CExtNodes.ToSulongNode toSulongNode;

        public abstract Object execute(Object value);

        @Specialization
        Object doUnicodeWrapper(PyUnicodeWrapper object) {
            return callUnaryIntoCapi(getPyObjectHandle_ForJavaType(), object);
        }

        private TruffleObject getPyObjectHandle_ForJavaType() {
            if (derefHandleIntrinsic == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                derefHandleIntrinsic = importCAPISymbol(NativeCAPISymbols.FUN_DEREF_HANDLE);
            }
            return derefHandleIntrinsic;
        }

        private Object callUnaryIntoCapi(TruffleObject fun, Object arg) {
            if (callNativeUnary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeUnary = insert(PCallNativeNode.create());
            }
            return callNativeUnary.execute(fun, new Object[]{arg});
        }

        public static PyUnicodeToNativeNode create() {
            return PyUnicodeToNativeNodeGen.create();
        }
    }

}
