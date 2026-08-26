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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CONST_PY_SLOT_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_ABI_INFO_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_SSIZE_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VOID_PTR_LIST;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.bindFunctionPointer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.nodes.ErrorMessages.NAMELESS_MODULE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_NEEDS_S_AS_FIRST_ARG;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.ABI;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.PRaiseNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionInvoker;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.CheckPrimitiveFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionSignature;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.CharPtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.PrefixSuffixNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromModuleNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.nativeaccess.NativeFunctionPointer;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
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
    abstract static class GraalPyPrivate_Module_CreateInitialized_PyModule_New extends CApiUnaryBuiltinNode {

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
                        @Bind PythonContext context,
                        @Cached EnsurePythonObjectNode ensureNode,
                        @Cached PyUnicodeCheckNode pyUnicodeCheckNode,
                        // CPython reads from the module dict directly
                        @Cached ReadAttributeFromModuleNode read,
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
            Object promotedName = ensureNode.execute(context, nameAttr, false);
            if (promotedName == nameAttr) {
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

    @CApiBuiltin(ret = Int, args = {PyObject, PyMethodDef}, call = Ignored)
    public static int GraalPyPrivate_Module_AddFunctions(long moduleRaw, long functions) {
        CompilerAsserts.neverPartOfCompilation();
        Object module = NativeToPythonInternalNode.executeUncached(moduleRaw, false);

        // the necessary type check is done in the C function
        assert module instanceof PythonModule;
        Object modName = ReadAttributeFromPythonObjectNode.executeUncached((PythonModule) module, T___NAME__, PNone.NO_VALUE);
        if (!PyUnicodeCheckNode.executeUncached(modName)) {
            return PRaiseNativeNodeGen.getUncached().raiseIntWithoutFrame(-1, SystemError, NAMELESS_MODULE, EMPTY_OBJECT_ARRAY);
        }

        addMethodsToObject(functions, module, modName);
        return 0;
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, PyMethodDef}, call = Ignored)
    public static int GraalPyPrivate_AddMethodsToObject(long moduleRaw, long nameRaw, long functions) {
        CompilerAsserts.neverPartOfCompilation();
        Object module = NativeToPythonInternalNode.executeUncached(moduleRaw, false);
        Object name = NativeToPythonInternalNode.executeUncached(nameRaw, false);
        addMethodsToObject(functions, module, name);
        return 0;
    }

    /**
     * Implementation of {@code moduleobject.c: _add_methods_to_object}.
     *
     * TODO(fa): overlaps with
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes#createLegacyMethod}
     */
    private static void addMethodsToObject(long functions, Object module, Object modName) {
        PythonLanguage language = PythonLanguage.get(null);
        long nameRaw;

        // iterate over a native array of PyModuleDef elements
        for (long def = functions; (nameRaw = readPtrField(def, CFields.PyMethodDef__ml_name)) != NULLPTR; def += CStructs.PyMethodDef.size()) {
            long cfunc = readPtrField(def, CFields.PyMethodDef__ml_meth);
            int flags = readIntField(def, CFields.PyMethodDef__ml_flags);
            long docRaw = readPtrField(def, CFields.PyMethodDef__ml_doc);

            TruffleString name = (TruffleString) CharPtrToPythonNode.executeUncached(nameRaw);
            Object doc = CharPtrToPythonNode.executeUncached(docRaw);
            assert doc == PNone.NO_VALUE || doc instanceof TruffleString;

            PythonBuiltinObject func = PythonCextMethodBuiltins.cFunctionNewExMethodNode(language, def, name, cfunc, flags, module, modName, PNone.NO_VALUE, doc);
            WriteAttributeToObjectNode.getUncached().execute(module, name, func);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {CONST_PY_SLOT_PTR, PyObject}, call = Direct, abi = ABI.ABI3T)
    public static long PyModule_FromSlotsAndSpec(long slots, long specPtr) {
        if (slots == NULLPTR) {
            throw PRaiseNode.raiseStatic(null, SystemError, ErrorMessages.PYMODULE_FROMSLOTS_NULL_SLOTS);
        }
        Object spec = NativeToPythonInternalNode.executeUncached(specPtr, false);
        TruffleString name = CastToTruffleStringNode.executeUncached(PyObjectGetAttr.executeUncached(spec, T_NAME));
        ModuleSpec moduleSpec = new ModuleSpec(name, T_EMPTY_STRING, spec);
        Object module = CExtNodes.createModuleFromSlotsAndSpec(null, PythonContext.get(null).getCApiContext(), slots, moduleSpec);
        return PythonToNativeInternalNode.executeNewRefUncached(module);
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct, abi = ABI.ABI3T)
    public static int PyModule_Exec(long modulePtr) {
        Object object = NativeToPythonInternalNode.executeUncached(modulePtr, false);
        if (object instanceof PythonModule module) {
            return CExtNodes.execModule(null, PythonContext.get(null).getCApiContext(), module);
        }
        throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.EXPECTED_MODULE_GOT_T, object);
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PY_SSIZE_T_PTR}, call = Direct, abi = ABI.ABI3T)
    public static int PyModule_GetStateSize(long modulePtr, long result) {
        Object object = NativeToPythonInternalNode.executeUncached(modulePtr, false);
        if (object instanceof PythonModule module) {
            NativeMemory.writeLong(result, module.getNativeModuleStateSize());
            return 0;
        }
        NativeMemory.writeLong(result, -1);
        throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.EXPECTED_MODULE_GOT_T, object);
    }

    @CApiBuiltin(ret = Int, args = {PyObject, VOID_PTR_LIST}, call = Direct, abi = ABI.ABI3T)
    public static int PyModule_GetToken(long modulePtr, long result) {
        Object object = NativeToPythonInternalNode.executeUncached(modulePtr, false);
        if (object instanceof PythonModule module) {
            NativeMemory.writeLong(result, module.getNativeModuleToken());
            return 0;
        }
        NativeMemory.writeLong(result, NULLPTR);
        throw PRaiseNode.raiseStatic(null, TypeError, ErrorMessages.EXPECTED_MODULE_GOT_T, object);
    }

    @CApiBuiltin(ret = Int, args = {PyObject, VOID_PTR_LIST}, call = Direct, abi = ABI.ABI3T)
    public static int PyModule_GetToken_DuringGC(long modulePtr, long result) {
        Object object = NativeToPythonInternalNode.executeUncached(modulePtr, false);
        if (object instanceof PythonModule module) {
            NativeMemory.writeLong(result, module.getNativeModuleToken());
            return 0;
        }
        NativeMemory.writeLong(result, NULLPTR);
        return -1;
    }

    @CApiBuiltin(ret = Pointer, args = {PyObject}, call = Direct, abi = ABI.ABI3T)
    public static long PyModule_GetState_DuringGC(long modulePtr) {
        Object object = NativeToPythonInternalNode.executeUncached(modulePtr, false);
        if (object instanceof PythonModule module) {
            return module.getNativeModuleState();
        }
        return NULLPTR;
    }

    @CApiBuiltin(ret = Int, args = {PY_ABI_INFO_PTR, ConstCharPtr}, call = Direct, abi = ABI.ABI3T)
    public static int PyABIInfo_Check(long info, long moduleName) {
        TruffleString name = moduleName == NULLPTR ? T_EMPTY_STRING : (TruffleString) CharPtrToPythonNode.executeUncached(moduleName);
        return CExtNodes.checkAbiInfo(null, info, name);
    }

    private static final CApiTiming TIMING_INVOKE_TRAVERSE_PROC = CApiTiming.create(true, "invokeTraverseProc");

    @CApiBuiltin(ret = Int, args = {PyObject, Pointer, Pointer}, call = Ignored)
    public static int GraalPyPrivate_Module_Traverse(long selfPtr, long visitFun, long arg) {

        PythonModule self = (PythonModule) NativeToPythonInternalNode.executeUncached(selfPtr, false);
        long traverse = self.getNativeModuleTraverse();
        long stateSize = self.getNativeModuleStateSize();
        long state = self.getNativeModuleState();
        if (traverse != NULLPTR && (stateSize <= 0 || state != NULLPTR)) {
            PythonContext ctx = PythonContext.get(null);
            PythonThreadState threadState = ctx.getThreadState(ctx.getLanguage());
            NativeFunctionPointer traverseExecutable = bindFunctionPointer(traverse, ExternalFunctionSignature.TRAVERSEPROC);
            int ires = ExternalFunctionInvoker.invokeTRAVERSEPROC(null, TIMING_INVOKE_TRAVERSE_PROC, ctx.ensureNativeContext(), BoundaryCallData.getUncached(),
                            threadState, traverseExecutable, PythonToNativeInternalNode.executeUncached(self, false), visitFun, arg);
            CheckPrimitiveFunctionResultNodeGen.getUncached().executeLong(null, threadState, StringLiterals.T_VISIT, ires);
            return ires;
        }
        return 0;
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyModule_GetFilenameObject extends CApiUnaryBuiltinNode {
        @Specialization
        static Object getFilename(PythonModule module,
                        @Bind Node inliningTarget,
                        @Cached ReadAttributeFromModuleNode read,
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

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyModuleObject, PyModuleDef}, call = Ignored)
    public static void GraalPyPrivate_Module_SetDef(long object, long value) {
        PythonModule module = (PythonModule) NativeToPythonInternalNode.executeUncached(object, false);
        module.setNativeModuleDef(value);
    }

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyModuleObject, Pointer}, call = Ignored)
    public static void GraalPyPrivate_Module_SetState(long object, long value) {
        PythonModule module = (PythonModule) NativeToPythonInternalNode.executeUncached(object, false);
        module.setNativeModuleState(value);
    }
}
