/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;

@CoreFunctions(defineModule = "faulthandler")
public class FaulthandlerModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey STACK_DUMP_REQUESTED = new HiddenKey("stackDumpRequested");
    private WeakHashMap<Thread, Object> dumpRequestedForThreads = new WeakHashMap<>();

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
                PythonObjectLibrary.getUncached().callObject(callable, null, PNone.NONE, PNone.NONE, f);
            } catch (RuntimeException e) {
                ExceptionUtils.printPythonLikeStackTrace(e);
            }
        }
    }

    private static final class StackDumpAction implements AsyncHandler.AsyncAction {
        @Override
        public void execute(PythonContext context) {
            CompilerDirectives.bailout("This should never be compiled");
            PythonModule mod = context.getCore().lookupBuiltinModule("faulthandler");
            Object dumpQueue = DynamicObjectLibrary.getUncached().getOrDefault(mod, STACK_DUMP_REQUESTED, null);
            if (dumpQueue instanceof WeakHashMap) {
                WeakHashMap<?, ?> weakDumpQueue = (WeakHashMap<?, ?>)dumpQueue;
                Object callableAndFile = weakDumpQueue.remove(Thread.currentThread());
                if (callableAndFile instanceof Object[]) {
                    Object callable = ((Object[]) callableAndFile)[0];
                    Object file = ((Object[]) callableAndFile)[1];
                    dumpTraceback(callable, file);
                }
            }
        }

        private static final StackDumpAction INSTANCE = new StackDumpAction();
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        final PythonContext ctx = core.getContext();
        PythonModule mod = core.lookupBuiltinModule("faulthandler");
        mod.setAttribute(STACK_DUMP_REQUESTED, dumpRequestedForThreads);
        ctx.registerAsyncAction(() -> {
            if (!dumpRequestedForThreads.isEmpty()) {
                return StackDumpAction.INSTANCE;
            } else {
                return null;
            }
        });
    }

    @Builtin(name = "dump_traceback", minNumOfPositionalArgs = 1, parameterNames = {"$mod", "file", "all_threads"}, declaresExplicitSelf = true)
    @ArgumentClinic(name = "file", defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "all_threads", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class DumpTracebackNode extends PythonClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        @SuppressWarnings("unchecked")
        public PNone doit(PythonModule mod, Object file, boolean allThreads) {
            Object printStackFunc;
            try {
                Object tracebackModule = AbstractImportNode.importModule("traceback");
                printStackFunc = PythonObjectLibrary.getUncached().lookupAttribute(tracebackModule, null, "print_stack");
            } catch (PException e) {
                return PNone.NONE;
            }

            if (allThreads) {
                if (PythonOptions.isWithJavaStacktrace(getContext().getLanguage())) {
                    Thread[] ths = getContext().getThreads();
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
                        System.err.println();
                        System.err.println(e.getKey());
                        for (StackTraceElement el : e.getValue()) {
                            System.err.println(el.toString());
                        }
                    }
                }

                Object dumpQueue = DynamicObjectLibrary.getUncached().getOrDefault(mod, STACK_DUMP_REQUESTED, null);
                if (dumpQueue instanceof WeakHashMap) {
                    WeakHashMap<Thread, Object[]> weakDumpQueue = (WeakHashMap<Thread, Object[]>)dumpQueue;
                    if (weakDumpQueue.isEmpty()) {
                        for (Thread th : getContext().getThreads()) {
                            weakDumpQueue.put(th, new Object[] { printStackFunc, file });
                        }
                    }
                }
            } else {
                if (PythonOptions.isWithJavaStacktrace(getContext().getLanguage())) {
                    System.err.println();
                    System.err.println(Thread.currentThread());
                    for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                        System.err.println(el.toString());
                    }
                }
                dumpTraceback(printStackFunc, file);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FaulthandlerModuleBuiltinsClinicProviders.DumpTracebackNodeClinicProviderGen.INSTANCE;
        }
    }
}
