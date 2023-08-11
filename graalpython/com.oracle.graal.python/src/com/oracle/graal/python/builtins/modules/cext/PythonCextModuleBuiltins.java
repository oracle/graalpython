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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.nodes.ErrorMessages.S_NEEDS_S_AS_FIRST_ARG;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi7BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltins.CFunctionNewExMethodNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EndsWithNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextModuleBuiltins {

    @CApiBuiltin(ret = Py_ssize_t, args = {}, call = Ignored)
    abstract static class _PyTruffleModule_GetAndIncMaxModuleNumber extends CApiNullaryBuiltinNode {

        @Specialization
        long doIt() {
            return getCApiContext().getAndIncMaxModuleNumber();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyModule_SetDocString extends CApiBinaryBuiltinNode {
        @Specialization
        static int run(PythonModule module, Object doc,
                        @Cached ObjectBuiltins.SetattrNode setattrNode) {
            setattrNode.execute(null, module, T___DOC__, doc);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObjectAsTruffleString}, call = Direct)
    @CApiBuiltin(name = "PyModule_New", ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyModule_NewObject extends CApiUnaryBuiltinNode {

        @Specialization
        static Object run(TruffleString name,
                        @Cached CallNode callNode) {
            return callNode.execute(PythonBuiltinClassType.PythonModule, new Object[]{name});
        }
    }

    @CApiBuiltin(ret = PyModuleObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class _PyTruffleModule_CreateInitialized_PyModule_New extends CApiUnaryBuiltinNode {

        @Specialization
        Object run(TruffleString name,
                        @Cached CallNode callNode,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached EndsWithNode endsWithNode,
                        @Cached TruffleString.LastIndexOfCodePointNode lastIndexNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // see CPython's Objects/moduleobject.c - _PyModule_CreateInitialized for
            // comparison how they handle _Py_PackageContext
            TruffleString newModuleName = name;
            PythonContext ctx = getContext();
            TruffleString pyPackageContext = ctx.getPyPackageContext() == null ? null : ctx.getPyPackageContext();
            if (pyPackageContext != null && endsWithNode.executeBoolean(null, pyPackageContext, newModuleName, 0, codePointLengthNode.execute(pyPackageContext, TS_ENCODING))) {
                newModuleName = pyPackageContext;
                ctx.setPyPackageContext(null);
            }
            Object newModule = callNode.execute(PythonBuiltinClassType.PythonModule, new Object[]{newModuleName});
            // TODO: (tfel) I don't think this is the right place to set it, but somehow
            // at least in the import of sklearn.neighbors.dist_metrics through
            // sklearn.neighbors.ball_tree the __package__ attribute seems to be already
            // set in CPython. To not produce a warning, I'm setting it here, although I
            // could not find what CPython really does
            int nameLength = codePointLengthNode.execute(newModuleName, TS_ENCODING);
            int idx = lastIndexNode.execute(newModuleName, '.', nameLength, 0, TS_ENCODING);
            if (idx > -1) {
                setattrNode.execute(null, newModule, T___PACKAGE__, substringNode.execute(newModuleName, 0, idx, TS_ENCODING, false));
            }
            return newModule;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyModule_GetNameObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object getName(Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttrNode) {
            return lookupAttrNode.execute(null, inliningTarget, o, T___NAME__);
        }
    }

    static boolean isModuleSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
        return isSubtypeNode.execute(null, getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PythonModule);
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString, PyObject}, call = Direct)
    @ImportStatic(PythonCextModuleBuiltins.class)
    abstract static class PyModule_AddObjectRef extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        static Object addObject(Object m, TruffleString k, Object o,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode) {
            writeAtrrNode.execute(m, k, o);
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        Object pop(Object m, Object key, Object defaultValue,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, S_NEEDS_S_AS_FIRST_ARG, "PyModule_AddObjectRef", "module");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString, ArgDescriptor.Long}, call = Direct)
    @ImportStatic(PythonCextModuleBuiltins.class)
    abstract static class PyModule_AddIntConstant extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        Object addObject(Object m, TruffleString k, long o,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode) {
            writeAtrrNode.execute(m, k, o);
            return 0;
        }

        @Specialization(guards = "!isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        Object pop(@SuppressWarnings("unused") Object m, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            throw raise(TypeError, S_NEEDS_S_AS_FIRST_ARG, "PyModule_AddIntConstant", "module");
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffleModule_AddFunctionToModule extends CApi7BuiltinNode {

        @Specialization
        static Object moduleFunction(Object methodDefPtr, PythonModule mod, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode) {
            Object modName = dylib.getOrDefault(mod.getStorage(), T___NAME__, null);
            assert modName != null : "module name is missing!";
            Object func = cFunctionNewExMethodNode.execute(methodDefPtr, name, cfunc, flags, wrapper, mod, modName, doc);
            setattrNode.execute(null, mod, name, func);
            return 0;
        }
    }
}
