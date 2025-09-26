/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerAsserts;
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

    private static final TruffleString T_GETATTR = tsLiteral("getattr");

    public static void init(PickleState state, PythonContext context) {
        CompilerAsserts.neverPartOfCompilation();
        final PythonModule builtins = context.getBuiltins();
        state.getattr = PyObjectGetAttr.executeUncached(builtins, T_GETATTR);

        var copyreg = importModule(PickleUtils.T_MOD_COPYREG);
        state.dispatchTable = getDictAttr(copyreg, "copyreg.dispatch_table", PickleUtils.T_ATTR_DISPATCH_TABLE);
        state.extensionRegistry = getDictAttr(copyreg, "copyreg._extension_registry", PickleUtils.T_ATTR_EXT_REGISTRY);
        state.invertedRegistry = getDictAttr(copyreg, "copyreg._inverted_registry", PickleUtils.T_ATTR_INV_REGISTRY);
        state.extensionCache = getDictAttr(copyreg, "copyreg._extension_cache", PickleUtils.T_ATTR_EXT_CACHE);

        final Object codecs = importModule(PickleUtils.T_MOD_CODECS);
        var codecsEncode = PyObjectGetAttr.executeUncached(codecs, PickleUtils.T_METHOD_ENCODE);
        if (PyCallableCheckNode.executeUncached(codecsEncode)) {
            state.codecsEncode = codecsEncode;
        } else {
            throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, "codecs.encode", "callable", codecsEncode);
        }

        final Object functools = importModule(PickleUtils.T_MOD_FUNCTOOLS);
        state.partial = PyObjectGetAttr.executeUncached(functools, PickleUtils.T_METHOD_PARTIAL);

        // Load the 2.x -> 3.x stdlib module mapping tables
        var compatPickle = importModule(PickleUtils.T_MOD_COMPAT_PICKLE);
        state.nameMapping2To3 = getDictAttr(compatPickle, PickleUtils.T_CP_NAME_MAPPING, PickleUtils.T_ATTR_NAME_MAPPING);
        state.importMapping2To3 = getDictAttr(compatPickle, PickleUtils.T_CP_IMPORT_MAPPING, PickleUtils.T_ATTR_IMPORT_MAPPING);

        // ... and the 3.x -> 2.x mapping tables
        state.nameMapping3To2 = getDictAttr(compatPickle, PickleUtils.T_CP_REVERSE_NAME_MAPPING, PickleUtils.T_ATTR_REVERSE_NAME_MAPPING);
        state.importMapping3To2 = getDictAttr(compatPickle, PickleUtils.T_CP_REVERSE_IMPORT_MAPPING, PickleUtils.T_ATTR_REVERSE_IMPORT_MAPPING);
    }

    private static PDict getDictAttr(PythonModule mod, Object fullName, TruffleString name) {
        assert fullName instanceof String || fullName instanceof TruffleString;
        var value = PyObjectGetAttr.executeUncached(mod, name);
        if (value instanceof PDict dict) {
            return dict;
        } else {
            throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_A_S_NOT_A_P, fullName, "dict", value);
        }
    }
}
