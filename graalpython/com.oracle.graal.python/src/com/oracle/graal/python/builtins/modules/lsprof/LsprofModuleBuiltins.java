/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.lsprof;

import java.util.List;
import java.util.Map;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;

@CoreFunctions(defineModule = "_lsprof")
public final class LsprofModuleBuiltins extends PythonBuiltins {

    static final StructSequence.BuiltinTypeDescriptor PROFILER_ENTRY_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PProfilerEntry,
                    null,
                    6,
                    new String[]{
                                    "code", "callcount", "reccallcount", "totaltime", "inlinetime", "calls"
                    },
                    new String[]{
                                    "code object or built-in function name",
                                    "how many times this was called",
                                    "how many times called recursively",
                                    "total time in this entry",
                                    "inline time in this entry (not in subcalls)",
                                    "details of the calls"
                    });

    static final StructSequence.BuiltinTypeDescriptor PROFILER_SUBENTRY_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PProfilerSubentry,
                    null,
                    5,
                    new String[]{
                                    "code", "callcount", "reccallcount", "totaltime", "inlinetime"
                    },
                    new String[]{
                                    "called code object or built-in function name",
                                    "how many times this is called",
                                    "how many times this is called recursively",
                                    "total time spent in this call",
                                    "inline time (not in further subcalls)"
                    });

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LsprofModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        StructSequence.initType(core, PROFILER_ENTRY_DESC);
        StructSequence.initType(core, PROFILER_SUBENTRY_DESC);
    }

    @Builtin(name = "Profiler", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.LsprofProfiler)
    @GenerateNodeFactory
    abstract static class LsprofNew extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Profiler doit(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            PythonContext context = getContext();
            Env env = context.getEnv();
            Map<String, InstrumentInfo> instruments = env.getInstruments();
            InstrumentInfo instrumentInfo = instruments.get(CPUSamplerInstrument.ID);
            if (instrumentInfo != null) {
                CPUSampler sampler = env.lookup(instrumentInfo, CPUSampler.class);
                if (sampler != null) {
                    return PFactory.createProfiler(context.getLanguage(), cls, TypeNodes.GetInstanceShape.executeUncached(cls), sampler);
                }
            }
            throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.NotImplementedError, ErrorMessages.COVERAGE_TRACKER_NOT_AVAILABLE);
        }
    }
}
