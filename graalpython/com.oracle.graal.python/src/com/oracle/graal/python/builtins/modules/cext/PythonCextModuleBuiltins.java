/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
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
import static com.oracle.graal.python.nodes.ErrorMessages.S_NEEDS_S_AS_FIRST_ARG;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi7BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltins.CFunctionNewExMethodNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureExecutableNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.PrefixSuffixNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextModuleBuiltins {

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyModule_SetDocString extends CApiBinaryBuiltinNode {
        @Specialization
        static int run(PythonModule module, Object doc,
                        @Cached ObjectBuiltins.SetattrNode setattrNode) {
            setattrNode.executeSetAttr(null, module, T___DOC__, doc);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObjectAsTruffleString}, call = Direct)
    @CApiBuiltin(name = "PyModule_New", ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyModule_NewObject extends CApiUnaryBuiltinNode {

        @Specialization
        static Object run(TruffleString name,
                        @Cached CallNode callNode) {
            return callNode.executeWithoutFrame(PythonBuiltinClassType.PythonModule, name);
        }
    }

    @CApiBuiltin(ret = PyModuleObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class _PyTruffleModule_CreateInitialized_PyModule_New extends CApiUnaryBuiltinNode {

        @Specialization
        Object run(TruffleString name,
                        @Cached CallNode callNode,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached PrefixSuffixNode prefixSuffixNode,
                        @Cached TruffleString.LastIndexOfCodePointNode lastIndexNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // see CPython's Objects/moduleobject.c - _PyModule_CreateInitialized for
            // comparison how they handle _Py_PackageContext
            TruffleString newModuleName = name;
            PythonContext ctx = getContext();
            TruffleString pyPackageContext = ctx.getPyPackageContext() == null ? null : ctx.getPyPackageContext();
            if (pyPackageContext != null && prefixSuffixNode.endsWith(pyPackageContext, newModuleName, 0, codePointLengthNode.execute(pyPackageContext, TS_ENCODING))) {
                newModuleName = pyPackageContext;
                ctx.setPyPackageContext(null);
            }
            Object newModule = callNode.executeWithoutFrame(PythonBuiltinClassType.PythonModule, new Object[]{newModuleName});
            // TODO: (tfel) I don't think this is the right place to set it, but somehow
            // at least in the import of sklearn.neighbors.dist_metrics through
            // sklearn.neighbors.ball_tree the __package__ attribute seems to be already
            // set in CPython. To not produce a warning, I'm setting it here, although I
            // could not find what CPython really does
            int nameLength = codePointLengthNode.execute(newModuleName, TS_ENCODING);
            int idx = lastIndexNode.execute(newModuleName, '.', nameLength, 0, TS_ENCODING);
            if (idx > -1) {
                setattrNode.executeSetAttr(null, newModule, T___PACKAGE__, substringNode.execute(newModuleName, 0, idx, TS_ENCODING, false));
            }
            return newModule;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyModule_GetNameObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object getName(PythonModule module,
                        @Bind Node inliningTarget,
                        @Cached PythonCextBuiltins.PromoteBorrowedValue promoteBorrowedValue,
                        @Cached PyUnicodeCheckNode pyUnicodeCheckNode,
                        // CPython reads from the module dict directly
                        @Cached ReadAttributeFromObjectNode read,
                        @Cached WriteAttributeToObjectNode write) {
            /*
             * Even thought the function returns a new reference, CPython assumes that the unicode
             * object returned from this function is still kept alive by the module's dict after a
             * decref, see PyModule_GetName. So we have to store the promoted string.
             */
            Object nameAttr = read.execute(module, T___NAME__);
            if (!pyUnicodeCheckNode.execute(inliningTarget, nameAttr)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.NAMELESS_MODULE);
            }
            Object promotedName = promoteBorrowedValue.execute(inliningTarget, nameAttr);
            if (promotedName == null) {
                return nameAttr;
            } else {
                write.execute(module, T___NAME__, promotedName);
                return promotedName;
            }
        }
    }

    static boolean isModuleSubtype(Node inliningTarget, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
        return isSubtypeNode.execute(getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PythonModule);
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString, PyObject}, call = Direct)
    @ImportStatic(PythonCextModuleBuiltins.class)
    abstract static class PyModule_AddObjectRef extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        static Object addObject(Object m, TruffleString k, Object o,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode) {
            writeAtrrNode.execute(m, k, o);
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        static Object pop(Object m, Object key, Object defaultValue,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, S_NEEDS_S_AS_FIRST_ARG, "PyModule_AddObjectRef", "module");
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, ConstCharPtrAsTruffleString, ArgDescriptor.Long}, call = Direct)
    @ImportStatic(PythonCextModuleBuiltins.class)
    abstract static class PyModule_AddIntConstant extends CApiTernaryBuiltinNode {
        @Specialization(guards = "isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        static Object addObject(Object m, TruffleString k, long o,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode) {
            writeAtrrNode.execute(m, k, o);
            return 0;
        }

        @Specialization(guards = "!isModuleSubtype(inliningTarget, m, getClassNode, isSubtypeNode)")
        static Object pop(@SuppressWarnings("unused") Object m, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtypeNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, S_NEEDS_S_AS_FIRST_ARG, "PyModule_AddIntConstant", "module");
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class PyTruffleModule_AddFunctionToModule extends CApi7BuiltinNode {

        @Specialization
        static Object moduleFunction(Object methodDefPtr, PythonModule mod, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Bind Node inliningTarget,
                        @Cached ObjectBuiltins.SetattrNode setattrNode,
                        @Cached ReadAttributeFromPythonObjectNode readAttrNode,
                        @Cached CFunctionNewExMethodNode cFunctionNewExMethodNode) {
            Object modName = readAttrNode.execute(mod, T___NAME__, null);
            assert modName != null : "module name is missing!";
            Object func = cFunctionNewExMethodNode.execute(inliningTarget, methodDefPtr, name, cfunc, flags, wrapper, mod, modName, doc);
            setattrNode.executeSetAttr(null, mod, name, func);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, Pointer, Pointer}, call = Ignored)
    abstract static class PyTruffleModule_Traverse extends CApiTernaryBuiltinNode {
        private static final String J__M_TRAVERSE = "m_traverse";
        private static final TruffleString T__M_TRAVERSE = tsLiteral(J__M_TRAVERSE);
        private static final CApiTiming TIMING = CApiTiming.create(true, J__M_TRAVERSE);

        @Specialization
        static int doGeneric(PythonModule self, Object visitFun, Object arg,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached CStructAccess.ReadI64Node readI64Node,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached EnsureExecutableNode ensureExecutableNode,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached ExternalFunctionInvokeNode externalFunctionInvokeNode,
                        @Cached CheckPrimitiveFunctionResultNode checkPrimitiveFunctionResultNode,
                        @Cached PythonToNativeNode toNativeNode) {

            /*
             * As in 'moduleobject.c: module_traverse': 'if (m->md_def && m->md_def->m_traverse &&
             * (m->md_def->m_size <= 0 || m->md_state != NULL))'
             */
            Object mdDef = self.getNativeModuleDef();
            if (mdDef != null) {
                Object mTraverse = readPointerNode.read(mdDef, CFields.PyModuleDef__m_traverse);
                if (!lib.isNull(mTraverse)) {
                    long mSize = readI64Node.read(mdDef, CFields.PyModuleDef__m_size);
                    Object mdState = self.getNativeModuleState();
                    if (mSize <= 0 || (mdState != null && !lib.isNull(mdState))) {
                        PythonThreadState threadState = getThreadStateNode.execute(inliningTarget);
                        Object traverseExecutable = ensureExecutableNode.execute(inliningTarget, mTraverse, PExternalFunctionWrapper.TRAVERSEPROC);
                        Object res = externalFunctionInvokeNode.call(null, inliningTarget, threadState, TIMING, T__M_TRAVERSE, traverseExecutable, toNativeNode.execute(self), visitFun, arg);
                        int ires = (int) checkPrimitiveFunctionResultNode.executeLong(threadState, StringLiterals.T_VISIT, res);
                        if (ires != 0) {
                            return ires;
                        }
                    }
                }
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyModule_GetFilenameObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object getFilename(PythonModule module,
                        @Bind Node inliningTarget,
                        @Cached ReadAttributeFromObjectNode read,
                        @Cached PyUnicodeCheckNode check,
                        @Cached PRaiseNode raiseNode) {
            Object file = read.execute(module, T___FILE__);
            if (file != PNone.NO_VALUE && check.execute(inliningTarget, file)) {
                return file;
            }
            throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.MODULE_FILENAME_MISSING);
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object module,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }
}
