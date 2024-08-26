/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.nodes.statement.AbstractImportNode.importModule;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class PickleState {
    // copyreg.dispatch_table, {type_object: pickling_function}
    PDict dispatchTable;

    // For the extension opcodes EXT1, EXT2 and EXT4.
    // copyreg._extension_registry, {(module_name, function_name): code}
    PDict extensionRegistry;
    // copyreg._extension_cache, {code: object}
    PDict extensionCache;
    // copyreg._inverted_registry, {code: (module_name, function_name)}
    PDict invertedRegistry;

    // codecs.encode, used for saving bytes in older protocols
    Object codecsEncode;
    // builtins.getattr, used for saving nested names with protocol < 4
    Object getattr;

    // functools.partial, used for implementing __newobj_ex__ with protocols 2 and 3
    Object partial;

    // Import mappings for compatibility with Python 2.x
    // * _compat_pickle.NAME_MAPPING, {(oldmodule, oldname): (newmodule, newname)}
    PDict nameMapping2To3;
    // _compat_pickle.IMPORT_MAPPING, {oldmodule: newmodule}
    PDict importMapping2To3;
    // Same, but with REVERSE_NAME_MAPPING / REVERSE_IMPORT_MAPPING
    PDict nameMapping3To2;
    PDict importMapping3To2;

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 18
    public abstract static class PickleStateInitNode extends Node {

        public static final TruffleString T_GETATTR = tsLiteral("getattr");

        public abstract void execute(PickleState state);

        @Specialization
        void init(PickleState state,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyCallableCheckNode callableCheck) {
            PythonContext context = PythonContext.get(this);
            final PythonModule builtins = context.getBuiltins();
            state.getattr = getAttr.execute(null, inliningTarget, builtins, T_GETATTR);

            var copyreg = importModule(PickleUtils.T_MOD_COPYREG);
            var dispatchTable = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_DISPATCH_TABLE);
            if (dispatchTable instanceof PDict dispatchTableDict) {
                state.dispatchTable = dispatchTableDict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg.dispatch_table", "dict", dispatchTable);
            }

            var extensionRegistry = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_EXT_REGISTRY);
            if (extensionRegistry instanceof PDict extensionRegistryDict) {
                state.extensionRegistry = extensionRegistryDict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg._extension_registry", "dict", extensionRegistry);
            }

            var invertedRegistry = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_INV_REGISTRY);
            if (invertedRegistry instanceof PDict invertedRegistryDict) {
                state.invertedRegistry = invertedRegistryDict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg._inverted_registry", "dict", invertedRegistry);
            }

            var extensionCache = getAttr.execute(null, inliningTarget, copyreg, PickleUtils.T_ATTR_EXT_CACHE);
            if (extensionCache instanceof PDict extensionCacheDict) {
                state.extensionCache = extensionCacheDict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "copyreg._extension_cache", "dict", extensionCache);
            }

            final Object codecs = importModule(PickleUtils.T_MOD_CODECS);
            var codecsEncode = getAttr.execute(null, inliningTarget, codecs, PickleUtils.T_METHOD_ENCODE);
            if (callableCheck.execute(inliningTarget, codecsEncode)) {
                state.codecsEncode = codecsEncode;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "codecs.encode", "callable", codecsEncode);
            }

            final Object functools = importModule(PickleUtils.T_MOD_FUNCTOOLS);
            state.partial = getAttr.execute(null, inliningTarget, functools, PickleUtils.T_METHOD_PARTIAL);

            // Load the 2.x -> 3.x stdlib module mapping tables
            Object compatPickle = importModule(PickleUtils.T_MOD_COMPAT_PICKLE);
            var nameMapping2To3 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_NAME_MAPPING);
            if (nameMapping2To3 instanceof PDict nameMapping2To3Dict) {
                state.nameMapping2To3 = nameMapping2To3Dict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_NAME_MAPPING, "dict", nameMapping2To3);
            }

            var importMapping2To3 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_IMPORT_MAPPING);
            if (importMapping2To3 instanceof PDict importMapping2To3Dict) {
                state.importMapping2To3 = importMapping2To3Dict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_IMPORT_MAPPING, "dict", importMapping2To3);
            }

            // ... and the 3.x -> 2.x mapping tables
            var nameMapping3To2 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_REVERSE_NAME_MAPPING);
            if (nameMapping3To2 instanceof PDict nameMapping3To2Dict) {
                state.nameMapping3To2 = nameMapping3To2Dict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_REVERSE_NAME_MAPPING, "dict", nameMapping3To2);
            }
            var importMapping3To2 = getAttr.execute(null, inliningTarget, compatPickle, PickleUtils.T_ATTR_REVERSE_IMPORT_MAPPING);
            if (importMapping3To2 instanceof PDict importMapping3To2Dict) {
                state.importMapping3To2 = importMapping3To2Dict;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, PickleUtils.T_CP_REVERSE_IMPORT_MAPPING, "dict",
                                importMapping3To2);
            }
        }
    }
}
