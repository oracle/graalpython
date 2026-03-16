/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.HiddenAttr.METHOD_DEF_PTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi6BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.MethodDescriptorWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.CharPtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonClassInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextDescrBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyDictProxy_New extends CApiUnaryBuiltinNode {
        @Specialization
        static Object values(Object obj,
                        @Cached MappingproxyBuiltins.MappingproxyNode mappingNode) {
            return mappingNode.execute(null, PythonBuiltinClassType.PMappingproxy, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyTypeObject, Pointer, Pointer, ConstCharPtrAsTruffleString, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Descr_NewGetSet extends CApi6BuiltinNode {

        @Specialization
        static Object doNativeCallable(TruffleString name, Object cls, long getter, long setter, Object doc, long closure) {
            return PythonCextTypeBuiltins.createGetSet(name, cls, getter, setter, doc, closure);
        }
    }

    /** Implementation of {@code PyDescr_NewClassMethod}. */
    @CApiBuiltin(ret = PyObjectTransfer, args = {Pointer, ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int, Pointer, PyTypeObject}, call = Ignored)
    public static long GraalPyPrivate_Descr_NewClassMethod(long methodDefPtr, long nameRaw, long docRaw, int flags, long methPtr, long typeRaw) {
        CompilerAsserts.neverPartOfCompilation();
        PythonLanguage language = PythonLanguage.get(null);
        TruffleString name = (TruffleString) CharPtrToPythonNode.getUncached().execute(nameRaw);
        Object doc = CharPtrToPythonNode.getUncached().execute(docRaw);
        assert doc == PNone.NO_VALUE || doc instanceof TruffleString;
        Object type = NativeToPythonClassInternalNode.executeUncached(typeRaw);
        PBuiltinFunction func = MethodDescriptorWrapper.createWrapperFunction(language, name, methPtr, type, flags);
        assert func != null;
        PDecoratedMethod classMethod = PFactory.createClassmethodFromCallableObj(language, func);
        WriteAttributeToPythonObjectNode.executeUncached(classMethod, T___NAME__, name);
        WriteAttributeToPythonObjectNode.executeUncached(classMethod, T___DOC__, doc);
        HiddenAttr.WriteLongNode.executeUncached(classMethod, METHOD_DEF_PTR, methodDefPtr);
        return PythonToNativeInternalNode.executeUncached(classMethod, true);
    }
}
