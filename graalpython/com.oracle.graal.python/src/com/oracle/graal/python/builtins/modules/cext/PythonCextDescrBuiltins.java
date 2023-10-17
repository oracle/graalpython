/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.isClassOrStaticMethod;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi6BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi7BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltins.CreateGetSetNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltins.NewClassMethodNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextDescrBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyDictProxy_New extends CApiUnaryBuiltinNode {
        @Specialization
        static Object values(Object obj,
                        @Cached BuiltinConstructors.MappingproxyNode mappingNode) {
            return mappingNode.execute(null, PythonBuiltinClassType.PMappingproxy, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyTypeObject, Pointer, Pointer, ConstCharPtrAsTruffleString, Pointer}, call = Ignored)
    abstract static class PyTruffleDescr_NewGetSet extends CApi6BuiltinNode {

        @Specialization
        Object doNativeCallable(TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        @Cached CreateGetSetNode createGetSetNode) {
            return createGetSetNode.execute(name, cls, getter, setter, doc, closure);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int, Int, Pointer, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDescr_NewClassMethod extends CApi7BuiltinNode {

        @Specialization
        static Object doNativeCallable(Object methodDefPtr, TruffleString name, Object doc, int flags, Object wrapper, Object methObj, Object type,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached PythonObjectFactory factory) {
            Object func = newClassMethodNode.execute(methodDefPtr, name, methObj, flags, wrapper, type, doc);
            if (!isClassOrStaticMethod(flags)) {
                /*
                 * NewClassMethodNode only wraps method with METH_CLASS and METH_STATIC set but we
                 * need to do so here.
                 */
                func = factory.createClassmethodFromCallableObj(func);
            }
            return func;
        }
    }
}
