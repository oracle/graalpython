/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = GraalHPyTraceModuleBuiltins.J_HPY_TRACE)
@GenerateNodeFactory
public final class GraalHPyTraceModuleBuiltins extends PythonBuiltins {

    public static final String J_HPY_TRACE = "_hpy_trace";

    private static final TruffleString T_GET_DURATIONS = tsLiteral("get_durations");
    private static final TruffleString T_GET_CALL_COUNTS = tsLiteral("get_call_counts");
    private static final TruffleString T_SET_TRACE_FUNCTIONS = tsLiteral("set_trace_functions");
    private static final TruffleString T_GET_FREQUENCY = tsLiteral("get_frequency");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Collections.emptyList();
    }

    @Override
    public void postInitialize(Python3Core core) {
        PythonModule hpyTraceModule = core.lookupBuiltinModule(PythonUtils.tsLiteral(J_HPY_TRACE));
        TruffleString[] keys = new TruffleString[]{T_GET_DURATIONS, T_GET_CALL_COUNTS, T_SET_TRACE_FUNCTIONS, T_GET_FREQUENCY};
        try {
            GraalHPyContext hpyContext = GraalHPyContext.ensureHPyWasLoaded(null, core.getContext(), null, null);
            PythonModule nativeTraceModule = hpyContext.getHPyTraceModule();
            PDict nativeTraceDict = GetDictIfExistsNode.getUncached().execute(nativeTraceModule);
            for (TruffleString tkey : keys) {
                hpyTraceModule.setAttribute(tkey, nativeTraceDict.getItem(tkey));
            }
        } catch (IOException | ApiInitException | ImportException e) {
            /*
             * Error case: install "not_available" for everything. So, loading still works, but you
             * cannot use it.
             */
            PythonBuiltinObject notAvailableObj = GraalHPyDebugModuleBuiltins.createFunction(core, hpyTraceModule);
            for (TruffleString tkey : keys) {
                hpyTraceModule.setAttribute(tkey, notAvailableObj);
            }
        }
    }
}
