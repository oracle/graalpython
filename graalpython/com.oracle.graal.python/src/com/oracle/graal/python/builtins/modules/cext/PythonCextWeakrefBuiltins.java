/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PYWEAKREFERENCE_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.nodes.BuiltinNames.T_PROXY_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins.ReferenceTypeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public final class PythonCextWeakrefBuiltins {

    @CApiBuiltin(ret = Void, args = {PyObject}, call = Direct)
    abstract static class PyObject_ClearWeakRefs extends CApiUnaryBuiltinNode {
        @Specialization
        static Object warn(@SuppressWarnings("unused") Object ref) {
            // TODO: implement
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyWeakref_NewRef extends CApiBinaryBuiltinNode {
        @Specialization
        static Object refType(Object object, Object callback,
                        @Cached ReferenceTypeNode referenceType) {
            return referenceType.execute(PythonBuiltinClassType.PReferenceType, object, callback);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyWeakref_NewProxy extends CApiBinaryBuiltinNode {
        @Specialization
        static Object refType(Object object, Object callback,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs call) {
            PythonModule weakrefModule = PythonContext.get(inliningTarget).lookupBuiltinModule(T__WEAKREF);
            return call.execute(null, inliningTarget, weakrefModule, T_PROXY_TYPE, object, callback == PNone.NO_VALUE ? PNone.NONE : callback);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    abstract static class PyWeakref_GetObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object call(Object reference,
                        @Bind Node inliningTarget,
                        @Cached ReadAttributeFromPythonObjectNode read) {
            if (reference instanceof PReferenceType ref) {
                return ref.getPyObject();
            }
            if (reference instanceof PythonObject obj) {
                // maybe a _weakref.py proxytype
                Object weakref = read.execute(obj, T__WEAKREF);
                if (weakref instanceof PReferenceType ref) {
                    return ref.getPyObject();
                }
            }
            /*
             * This weak reference has died in the managed side due to its referent being collected.
             */
            return PNone.NONE;
        }
    }

    @CApiBuiltin(name = "_PyWeakref_ClearRef", ret = Void, args = {PYWEAKREFERENCE_PTR}, call = Direct)
    abstract static class PyWeakref_ClearRef extends CApiUnaryBuiltinNode {
        @Specialization
        static Object call(Object reference) {
            if (reference instanceof PReferenceType ref) {
                ref.clearRef();
            }
            return PNone.NONE;
        }
    }
}
