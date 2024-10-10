/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "faulthandler")
public final class FaulthandlerModuleBuiltins extends PythonBuiltins {

    private boolean enabled = true;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FaulthandlerModuleBuiltinsFactory.getFactories();
    }

    private static void dumpTraceback(Object callable, Object file) {
        if (!(callable instanceof PNone)) {
            Object f = file;
            if (file == PNone.NO_VALUE) {
                f = PNone.NONE;
            }
            try {
                CallNode.executeUncached(callable, PNone.NONE, PNone.NONE, f);
            } catch (RuntimeException e) {
                ExceptionUtils.printPythonLikeStackTrace(e);
            }
        }
    }

    @Builtin(name = "dump_traceback", minNumOfPositionalArgs = 0, parameterNames = {"file", "all_threads"})
    @ArgumentClinic(name = "file", defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "all_threads", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class DumpTracebackNode extends PythonClinicBuiltinNode {

        public static final TruffleString T_PRINT_STACK = tsLiteral("print_stack");

        @Specialization
        PNone doit(VirtualFrame frame, Object file, boolean allThreads,
                        @Cached("createFor(this)") IndirectCallData indirectCallData) {
            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = getContext();
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                // it's not important for this to be fast at all
                dump(language, context, file, allThreads);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static void dump(PythonLanguage language, PythonContext context, Object file, boolean allThreads) {
            Object printStackFunc;
            try {
                Object tracebackModule = AbstractImportNode.importModule(toTruffleStringUncached("traceback"));
                printStackFunc = PyObjectLookupAttr.executeUncached(tracebackModule, T_PRINT_STACK);
            } catch (PException e) {
                return;
            }

            if (allThreads) {
                if (PythonOptions.isPExceptionWithJavaStacktrace(language)) {
                    PrintWriter err = new PrintWriter(context.getStandardErr());
                    Thread[] ths = context.getThreads();
                    for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                        boolean found = false;
                        for (Thread pyTh : ths) {
                            if (pyTh == e.getKey()) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            continue;
                        }
                        err.println();
                        err.println(e.getKey());
                        for (StackTraceElement el : e.getValue()) {
                            err.println(el.toString());
                        }
                    }
                }

                context.getEnv().submitThreadLocal(null, new ThreadLocalAction(true, false) {
                    @Override
                    protected void perform(ThreadLocalAction.Access access) {
                        dumpTraceback(printStackFunc, file);
                    }
                });
            } else {
                if (PythonOptions.isPExceptionWithJavaStacktrace(language)) {
                    PrintWriter err = new PrintWriter(context.getStandardErr());
                    err.println();
                    err.println(Thread.currentThread());
                    for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                        err.println(el.toString());
                    }
                }
                dumpTraceback(printStackFunc, file);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FaulthandlerModuleBuiltinsClinicProviders.DumpTracebackNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "enable", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, parameterNames = {"$self", "file", "all_threads"})
    @GenerateNodeFactory
    abstract static class EnableNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PNone doit(PythonModule self, @SuppressWarnings("unused") Object file, @SuppressWarnings("unused") Object allThreads) {
            ((FaulthandlerModuleBuiltins) self.getBuiltins()).enabled = true;
            return PNone.NONE;
        }
    }

    @Builtin(name = "disable", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class DisableNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone doit(PythonModule self) {
            ((FaulthandlerModuleBuiltins) self.getBuiltins()).enabled = false;
            return PNone.NONE;
        }
    }

    @Builtin(name = "is_enabled", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class IsEnabledNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doit(PythonModule self) {
            return ((FaulthandlerModuleBuiltins) self.getBuiltins()).enabled;
        }
    }

    @Builtin(name = "cancel_dump_traceback_later")
    @GenerateNodeFactory
    abstract static class CancelDumpTracebackLaterNode extends PythonBuiltinNode {
        @Specialization
        PNone doit() {
            return PNone.NONE;
        }
    }
}
