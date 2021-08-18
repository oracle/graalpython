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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.CArgObject;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArray;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArrayType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCData;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtr;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtrType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointer;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointerType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCSimpleType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCStructType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SimpleCData;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnionType;

import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public class CtypesNodes {

    @GenerateUncached
    protected abstract static class PyTypeCheck extends Node {

        protected abstract boolean execute(Object receiver, Object type);

        // corresponds to UnionTypeObject_Check
        protected boolean isUnionTypeObject(Object obj) {
            return execute(obj, UnionType);
        }

        // corresponds to CDataObject_Check
        protected boolean isCDataObject(Object obj) {
            return execute(obj, PyCData);
        }

        // corresponds to PyCArrayTypeObject_Check
        protected boolean isPyCArrayTypeObject(Object obj) {
            return execute(obj, PyCArrayType);
        }

        // corresponds to ArrayObject_Check
        protected boolean isArrayObject(Object obj) {
            return execute(obj, PyCArray);
        }

        // corresponds to PyCFuncPtrObject_Check
        protected boolean isPyCFuncPtrObject(Object obj) {
            return execute(obj, PyCFuncPtr);
        }

        // corresponds to PyCFuncPtrTypeObject_Check
        protected boolean isPyCFuncPtrTypeObject(Object obj) {
            return execute(obj, PyCFuncPtrType);
        }

        // corresponds to PyCPointerTypeObject_Check
        protected boolean isPyCPointerTypeObject(Object obj) {
            return execute(obj, PyCPointerType);
        }

        // corresponds to PointerObject_Check
        protected boolean isPointerObject(Object obj) {
            return execute(obj, PyCPointer);
        }

        // corresponds to PyCSimpleTypeObject_Check
        protected boolean isPyCSimpleTypeObject(Object obj) {
            return execute(obj, PyCSimpleType);
        }

        /*
         * This function returns TRUE for c_int, c_void_p, and these kind of classes. FALSE
         * otherwise FALSE also for subclasses of c_int and such.
         */
        // corresponds to _ctypes_simple_instance
        boolean ctypesSimpleInstance(Object type, PythonContext context, GetBaseClassNode getBaseClassNode) {
            if (isPyCSimpleTypeObject(type)) {
                return getBaseClassNode.execute(type) != context.getCore().lookupType(SimpleCData);
            }
            return false;
        }

        // corresponds to PyCStructTypeObject_Check
        protected boolean isPyCStructTypeObject(Object obj) {
            return execute(obj, PyCStructType);
        }

        @Specialization
        static boolean checkType(Object receiver, Object type,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object clazz = getClassNode.execute(receiver);
            return isSameTypeNode.execute(clazz, type) || isSubtypeNode.execute(clazz, type);
        }
    }

    @GenerateUncached
    protected abstract static class PyTypeCheckExact extends Node {

        protected abstract boolean execute(Object receiver, Object type);

        protected boolean isPyCSimpleTypeObject(Object obj) {
            return execute(obj, PyCSimpleType);
        }

        protected boolean isPyCArg(Object obj) {
            return execute(obj, CArgObject);
        }

        @Specialization
        static boolean checkExact(Object receiver, Object type,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(getClassNode.execute(receiver), type);
        }

    }
}
