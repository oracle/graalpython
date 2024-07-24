/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.lib.PyImportImport.T_FROMLIST;
import static com.oracle.graal.python.lib.PyImportImport.T_LEVEL;
import static com.oracle.graal.python.nodes.BuiltinNames.T_GLOBALS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LOCALS;
import static com.oracle.graal.python.nodes.BuiltinNames.T___IMPORT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___INITIALIZING__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___SPEC__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextImportBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    @CApiBuiltin(name = "PyImport_Import", ret = PyObjectTransfer, args = {PyObjectAsTruffleString}, call = Direct)
    @CApiBuiltin(name = "PyImport_ImportModuleNoBlock", ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyImport_ImportModule extends CApiUnaryBuiltinNode {
        @Specialization
        static Object imp(TruffleString name) {
            return AbstractImportNode.importModule(name);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {}, call = Direct)
    abstract static class PyImport_GetModuleDict extends CApiNullaryBuiltinNode {
        @Specialization
        Object getModuleDict() {
            return getContext().getSysModules();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObjectAsTruffleString, PyObject, PyObject, PyObject, Int}, call = Direct)
    abstract static class PyImport_ImportModuleLevelObject extends CApi5BuiltinNode {
        @Specialization
        Object importModuleLevelObject(TruffleString name, Object globals, Object locals, Object fromlist, int level,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode) {
            // Get the __import__ function from the builtins
            Object importFunc = getAttrNode.execute(null, inliningTarget, getContext().getBuiltins(), T___IMPORT__);
            // Call the __import__ function with the proper argument list
            return callNode.executeWithoutFrame(importFunc, new Object[]{name}, new PKeyword[]{
                            new PKeyword(T_GLOBALS, globals), new PKeyword(T_LOCALS, locals),
                            new PKeyword(T_FROMLIST, fromlist), new PKeyword(T_LEVEL, level)
            });
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyImport_GetModule extends CApiUnaryBuiltinNode {

        private static final TruffleString T__LOCK_UNLOCK_MODULE = tsLiteral("_lock_unlock_module");

        @Specialization
        Object getModule(Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PDict modules = context.getSysModules();
            Object m;
            try {
                m = getItem.execute(null, inliningTarget, modules, name);
            } catch (PException e) {
                return context.getNativeNull();
            }
            if (m != PNone.NONE) {
                boolean initializing = false;
                try {
                    Object spec = getAttr.execute(null, inliningTarget, m, T___SPEC__);
                    Object initializingObj = getAttr.execute(null, inliningTarget, spec, T___INITIALIZING__);
                    if (isTrueNode.execute(null, inliningTarget, initializingObj)) {
                        initializing = true;
                    }
                } catch (PException e) {
                    // ignore
                }
                if (initializing) {
                    waitForInitialization(name, context);
                }
            }
            return m;
        }

        @TruffleBoundary
        private static void waitForInitialization(Object name, PythonContext context) {
            PyObjectCallMethodObjArgs.executeUncached(context.getImportlib(), T__LOCK_UNLOCK_MODULE, name);
        }
    }
}
