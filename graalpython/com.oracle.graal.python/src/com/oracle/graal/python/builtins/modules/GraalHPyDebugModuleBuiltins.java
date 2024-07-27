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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.StringLiterals.J_DEBUG;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.PythonUtils.PrototypeNodeFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = GraalHPyDebugModuleBuiltins.J_HPY_DEBUG)
@GenerateNodeFactory
public final class GraalHPyDebugModuleBuiltins extends PythonBuiltins {

    public static final String J_HPY_DEBUG = "_hpy_debug";

    static final String J_NOT_AVAILABLE = "_not_available";

    private static final TruffleString T_NEW_GENERATION = tsLiteral("new_generation");
    private static final TruffleString T_GET_OPEN_HANDLES = tsLiteral("get_open_handles");
    private static final TruffleString T_GET_CLOSED_HANDLES = tsLiteral("get_closed_handles");
    private static final TruffleString T_GET_QUEUE_MAX_SIZE = tsLiteral("get_closed_handles_queue_max_size");
    private static final TruffleString T_SET_QUEUE_MAX_SIZE = tsLiteral("set_closed_handles_queue_max_size");
    private static final TruffleString T_GET_DATA_MAX_SIZE = tsLiteral("get_protected_raw_data_max_size");
    private static final TruffleString T_SET_DATA_MAX_SIZE = tsLiteral("set_protected_raw_data_max_size");
    private static final TruffleString T_SET_ON_INVALID_HANDLE = tsLiteral("set_on_invalid_handle");
    private static final TruffleString T_STACK_TRACE_LIMIT = tsLiteral("set_handle_stack_trace_limit");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Collections.emptyList();
    }

    @Override
    public void postInitialize(Python3Core core) {
        PythonModule hpyDebugModule = core.lookupBuiltinModule(PythonUtils.tsLiteral(J_HPY_DEBUG));
        TruffleString[] keys = new TruffleString[]{T_NEW_GENERATION, T_GET_OPEN_HANDLES, T_GET_CLOSED_HANDLES, T_GET_QUEUE_MAX_SIZE, T_SET_QUEUE_MAX_SIZE, T_GET_DATA_MAX_SIZE, T_SET_DATA_MAX_SIZE,
                        T_SET_ON_INVALID_HANDLE, T_STACK_TRACE_LIMIT};
        try {
            GraalHPyContext hpyContext = GraalHPyContext.ensureHPyWasLoaded(null, core.getContext(), null, null);
            PythonModule nativeDebugModule = hpyContext.getHPyDebugModule();
            PDict nativeDebugDict = GetDictIfExistsNode.getUncached().execute(nativeDebugModule);
            for (TruffleString tkey : keys) {
                hpyDebugModule.setAttribute(tkey, nativeDebugDict.getItem(tkey));
            }
        } catch (IOException | ApiInitException | ImportException e) {
            /*
             * Error case: install "not_available" for everything. So, loading still works, but you
             * cannot use it.
             */
            PythonBuiltinObject notAvailableObj = createFunction(core, hpyDebugModule);
            for (TruffleString tkey : keys) {
                hpyDebugModule.setAttribute(tkey, notAvailableObj);
            }
        }
    }

    @Builtin(name = J_NOT_AVAILABLE, autoRegister = false, takesVarArgs = true, takesVarKeywordArgs = true)
    static final class NotAvailable extends PythonBuiltinNode {
        private static final NodeFactory<NotAvailable> NODE_FACTORY = new PrototypeNodeFactory<>(new NotAvailable());

        @Override
        public Object execute(VirtualFrame frame) {
            throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.RuntimeError, ErrorMessages.HPY_S_MODE_NOT_AVAILABLE, J_DEBUG);
        }
    }

    @TruffleBoundary
    static PBuiltinMethod createFunction(Python3Core core, PythonModule module) {
        Builtin builtin = NotAvailable.class.getAnnotation(Builtin.class);
        RootCallTarget callTarget = core.getLanguage().createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, NotAvailable.NODE_FACTORY, false), NotAvailable.class,
                        builtin.name());
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        TruffleString name = PythonUtils.toTruffleStringUncached(builtin.name());
        PBuiltinFunction fun = core.factory().createBuiltinFunction(name, null, 0, flags, callTarget);
        return core.factory().createBuiltinMethod(module, fun);
    }
}
