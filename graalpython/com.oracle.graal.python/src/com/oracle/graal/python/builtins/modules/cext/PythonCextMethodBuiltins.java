/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.METH_METHOD;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.MethodDescriptorWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.CharPtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextMethodBuiltins {

    /*
     * Native pointer to the PyMethodDef struct for functions created in C. We need to keep it
     * because the C program may expect to get its pointer back when accessing m_ml member of
     * methods.
     */
    public static final HiddenAttr METHOD_DEF_PTR = HiddenAttr.METHOD_DEF_PTR;

    static PythonBuiltinObject cFunctionNewExMethodNode(PythonLanguage language, long methodDefPtr, TruffleString name, long methPtr, int flags, Object self, Object moduleName, Object cls,
                    Object doc) {
        PBuiltinFunction func = MethodDescriptorWrapper.createWrapperFunction(language, name, methPtr, PNone.NO_VALUE, flags);
        HiddenAttr.WriteLongNode.executeUncached(func, METHOD_DEF_PTR, methodDefPtr);
        WriteAttributeToPythonObjectNode.executeUncached(func, T___NAME__, name);
        WriteAttributeToPythonObjectNode.executeUncached(func, T___DOC__, doc);
        PBuiltinMethod method;
        if (cls != PNone.NO_VALUE) {
            method = PFactory.createBuiltinMethod(language, self, func, cls);
        } else {
            method = PFactory.createBuiltinMethod(language, self, func);
        }
        assert moduleName == PNone.NO_VALUE || PyUnicodeCheckNode.executeUncached(moduleName);
        WriteAttributeToPythonObjectNode.executeUncached(method, T___MODULE__, moduleName);
        return method;
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyMethodDef, ConstCharPtrAsTruffleString, Pointer, Int, PyObject, PyObject, PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    public static long GraalPyPrivate_CMethod_NewEx(long methodDefPtr, long nameRaw, long methPtr, int flags, long selfRaw, long moduleRaw, long clsRaw, long docRaw) {
        // errors are expected to be thrown already in native code
        assert verifyFlags(flags, clsRaw);

        TruffleString name = (TruffleString) CharPtrToPythonNode.getUncached().execute(nameRaw);
        Object self = NativeToPythonInternalNode.executeUncached(selfRaw, false);
        Object module = NativeToPythonInternalNode.executeUncached(moduleRaw, false);
        Object cls = NativeToPythonInternalNode.executeUncached(clsRaw, false);
        Object doc = CharPtrToPythonNode.getUncached().execute(docRaw);
        assert doc == PNone.NO_VALUE || doc instanceof TruffleString;

        PythonBuiltinObject result = cFunctionNewExMethodNode(PythonLanguage.get(null), methodDefPtr, name, methPtr, flags, self, module, cls, doc);
        return PythonToNativeInternalNode.executeUncached(result, true);
    }

    private static boolean verifyFlags(int flags, long clsRaw) {
        boolean isMethod = (flags & METH_METHOD) != 0;
        return (!isMethod || clsRaw != NULLPTR) && (isMethod || clsRaw == NULLPTR);
    }
}
