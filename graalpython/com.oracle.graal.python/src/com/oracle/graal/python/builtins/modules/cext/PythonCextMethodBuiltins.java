/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi9BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.CreateFunctionNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextMethodBuiltins {

    /*
     * Native pointer to the PyMethodDef struct for functions created in C. We need to keep it
     * because the C program may expect to get its pointer back when accessing m_ml member of
     * methods.
     */
    public static final HiddenKey METHOD_DEF_PTR = new HiddenKey("method_def_ptr");

    @GenerateInline
    @GenerateCached(false)
    abstract static class CFunctionNewExMethodNode extends Node {

        abstract Object execute(Node inliningTarget, Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object cls, Object doc);

        final Object execute(Node inliningTarget, Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object doc) {
            return execute(inliningTarget, methodDefPtr, name, methObj, flags, wrapper, self, module, PNone.NO_VALUE, doc);
        }

        @Specialization
        static Object doNativeCallable(Node inliningTarget, Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object self, Object module, Object cls, Object doc,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached CreateFunctionNode createFunctionNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib) {
            Object f = createFunctionNode.execute(inliningTarget, name, methObj, wrapper, PNone.NO_VALUE, flags);
            assert f instanceof PBuiltinFunction;
            PBuiltinFunction func = (PBuiltinFunction) f;
            dylib.put(func.getStorage(), T___NAME__, name);
            dylib.put(func.getStorage(), T___DOC__, doc);
            PBuiltinMethod method;
            if (cls != PNone.NO_VALUE) {
                method = factory.createBuiltinMethod(self, func, cls);
            } else {
                method = factory.createBuiltinMethod(self, func);
            }
            dylib.put(method.getStorage(), T___MODULE__, module);
            dylib.put(method.getStorage(), METHOD_DEF_PTR, methodDefPtr);
            return method;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyMethodDef, ConstCharPtrAsTruffleString, Pointer, Int, Int, PyObject, PyObject, PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffleCMethod_NewEx extends CApi9BuiltinNode {

        @Specialization
        static Object doNativeCallable(Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object self, Object module, Object cls, Object doc,
                        @Bind("this") Node inliningTarget,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode) {
            return cFunctionNewExMethodNode.execute(inliningTarget, methodDefPtr, name, methObj, flags, wrapper, self, module, cls, doc);
        }
    }
}
