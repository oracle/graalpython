/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDERR;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "faulthandler")
public final class FaulthandlerModuleBuiltins extends PythonBuiltins {

    public static final TruffleString T_FAULTHANDLER = tsLiteral("faulthandler");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FaulthandlerModuleBuiltinsFactory.getFactories();
    }

    private static class ModuleState {
        boolean enabled;
        Thread dumpTracebackLaterThread;
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule module = core.lookupBuiltinModule(T_FAULTHANDLER);
        module.setModuleState(new ModuleState());
    }

    @TruffleBoundary
    private static void dumpTraceback(int fd) {
        ExceptionUtils.printPythonLikeStackTraceNoMessage(newRawFdPrintWriter(fd), new RuntimeException());
    }

    @Builtin(name = "dump_traceback", minNumOfPositionalArgs = 0, parameterNames = {"file", "all_threads"})
    @ArgumentClinic(name = "file", defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "all_threads", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class DumpTracebackNode extends PythonClinicBuiltinNode {

        @Specialization
        PNone doit(VirtualFrame frame, Object fileObj, boolean allThreads,
                        @Bind Node inliningTarget,
                        @Cached GetFilenoNode getFilenoNode,
                        @Cached("createFor(this)") IndirectCallData indirectCallData) {
            int fileno = getFilenoNode.execute(frame, inliningTarget, fileObj);
            PythonContext context = getContext();
            PythonLanguage language = context.getLanguage(this);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                // it's not important for this to be fast at all
                dump(language, context, fileno, fileObj, allThreads);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
            TruffleSafepoint.poll(this);
            return PNone.NONE;
        }

        @TruffleBoundary
        static void dump(PythonLanguage language, PythonContext context, int fd, Object fileObj, boolean allThreads) {
            PrintWriter err = newRawFdPrintWriter(fd);
            if (allThreads) {
                if (PythonOptions.isPExceptionWithJavaStacktrace(language)) {
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

                context.getEnv().submitThreadLocal(context.getThreads(), new ThreadLocalAction(true, false) {
                    @Override
                    protected void perform(ThreadLocalAction.Access access) {
                        dumpTraceback(fd);
                    }
                });
            } else {
                if (PythonOptions.isPExceptionWithJavaStacktrace(language)) {
                    err.println();
                    err.println(Thread.currentThread());
                    for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                        err.println(el);
                    }
                }
                dumpTraceback(fd);
            }
            // Keep the file object alive to make sure the fd doesn't get closed
            Reference.reachabilityFence(fileObj);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FaulthandlerModuleBuiltinsClinicProviders.DumpTracebackNodeClinicProviderGen.INSTANCE;
        }
    }

    @TruffleBoundary
    private static void cancelDumpTracebackLater(ModuleState moduleState, PythonContext context) {
        if (moduleState.dumpTracebackLaterThread != null && moduleState.dumpTracebackLaterThread.isAlive()) {
            context.getEnv().submitThreadLocal(new Thread[]{moduleState.dumpTracebackLaterThread}, new ThreadLocalAction(true, false) {
                @Override
                protected void perform(Access access) {
                    throw new ThreadDeath();
                }
            });
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetFilenoNode extends Node {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, Object file);

        @Specialization
        static int fileno(VirtualFrame frame, Node inliningTarget, Object file,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PRaiseNode raiseNode) {
            if (file instanceof PNone) {
                file = getAttr.execute(frame, inliningTarget, PythonContext.get(inliningTarget).getSysModule(), T_STDERR);
                if (file == PNone.NONE) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.SYS_STDERR_IS_NONE);
                }
            }
            if (longCheckNode.execute(inliningTarget, file)) {
                int fd = asIntNode.execute(frame, inliningTarget, file);
                if (fd < 0) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.FILE_IS_NOT_A_VALID_FILE_DESCRIPTOR);
                }
                return fd;
            } else {
                Object result = callMethod.execute(frame, inliningTarget, file, T_FILENO);
                int fd = -1;
                if (longCheckNode.execute(inliningTarget, result)) {
                    fd = asIntNode.execute(frame, inliningTarget, result);
                }
                if (fd < 0) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.FILE_FILENO_IS_NOT_A_VALID_FILE_DESCRIPTOR);
                }
                try {
                    callMethod.execute(frame, inliningTarget, file, T_FLUSH);
                } catch (AbstractTruffleException e) {
                    // Ignore flush errors
                }
                return fd;
            }
        }
    }

    @Builtin(name = "dump_traceback_later", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$mod", "timeout", "repeat", "file", "exit"})
    @ArgumentClinic(name = "repeat", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "false")
    @ArgumentClinic(name = "exit", conversion = ArgumentClinic.ClinicConversion.IntToBoolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class DumpTracebackLaterNode extends PythonClinicBuiltinNode {

        @Specialization
        static Object dumpLater(VirtualFrame frame, PythonModule module, Object timeoutObj, boolean repeat, Object file, boolean exit,
                        @Bind Node inliningTarget,
                        @Cached PyTimeFromObjectNode pyTimeFromObjectNode,
                        @Cached GetFilenoNode getFilenoNode) {
            long timeoutNs = pyTimeFromObjectNode.fromSeconds(frame, inliningTarget, timeoutObj, RoundType.TIMEOUT);
            int fd = getFilenoNode.execute(frame, inliningTarget, file);

            doDumpLater(inliningTarget, module, timeoutNs, repeat, fd, file, exit);
            return PNone.NONE;
        }

        @TruffleBoundary
        private static void doDumpLater(Node inliningTarget, PythonModule module, long timeoutNs, boolean repeat, int fd, Object fileObj, boolean exit) {
            PythonContext context = PythonContext.get(null);
            ModuleState moduleState = module.getModuleState(ModuleState.class);
            cancelDumpTracebackLater(moduleState, context);
            Thread thread = context.getEnv().newTruffleThreadBuilder(() -> {
                do {
                    sleepInterruptibly(inliningTarget, timeoutNs);
                    long timeoutS = timeoutNs / 1_000_000_000;
                    newRawFdPrintWriter(fd).printf("Timeout (%d:%02d:%02d)!%n", timeoutS / 3600, timeoutS / 60, timeoutS);
                    try {
                        DumpTracebackNode.dump(context.getLanguage(), context, fd, fileObj, true);
                        if (exit) {
                            // Sleep for a bit to give time for the safepoint-based
                            // printing to execute before we exit
                            sleepInterruptibly(inliningTarget, 100_000_000);
                        }
                    } finally {
                        if (exit) {
                            PosixModuleBuiltins.ExitNode.exit(1, inliningTarget);
                        }
                    }
                } while (repeat);
            }).build();
            moduleState.dumpTracebackLaterThread = thread;
            thread.start();
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FaulthandlerModuleBuiltinsClinicProviders.DumpTracebackLaterNodeClinicProviderGen.INSTANCE;
        }
    }

    private static class RawFdOutputStream extends OutputStream {
        private final int fd;

        private RawFdOutputStream(int fd) {
            this.fd = fd;
        }

        @Override
        public void write(byte[] bytes, int off, int len) {
            if (off != 0 || len != bytes.length) {
                bytes = Arrays.copyOfRange(bytes, off, off + len);
            }
            try {
                PosixSupportLibrary.getUncached().write(PythonContext.get(null).getPosixSupport(), fd, PosixSupportLibrary.Buffer.wrap(bytes));
            } catch (PosixSupportLibrary.PosixException e) {
                // Ignore
            }
        }

        @Override
        public void write(int b) {
            write(new byte[]{(byte) b}, 0, 0);
        }
    }

    private static PrintWriter newRawFdPrintWriter(int fd) {
        return new PrintWriter(new RawFdOutputStream(fd), true, StandardCharsets.US_ASCII);
    }

    private static void sleepInterruptibly(Node inliningTarget, long timeoutNs) {
        long deadlineNs = System.nanoTime() + timeoutNs;
        TruffleSafepoint.setBlockedThreadInterruptible(inliningTarget, (deadline) -> {
            long remainingNs = deadline - System.nanoTime();
            if (remainingNs > 0) {
                Thread.sleep(remainingNs / 1_000_000, (int) (remainingNs % 1_000_000));
            }
        }, deadlineNs);
    }

    @Builtin(name = "cancel_dump_traceback_later", declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CancelDumpTracebackLater extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone cancel(PythonModule module) {
            ModuleState moduleState = module.getModuleState(ModuleState.class);
            cancelDumpTracebackLater(moduleState, PythonContext.get(null));
            return PNone.NONE;
        }
    }

    @Builtin(name = "enable", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, parameterNames = {"$self", "file", "all_threads"})
    @GenerateNodeFactory
    abstract static class EnableNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PNone doit(PythonModule module, @SuppressWarnings("unused") Object file, @SuppressWarnings("unused") Object allThreads) {
            ModuleState moduleState = module.getModuleState(ModuleState.class);
            moduleState.enabled = true;
            return PNone.NONE;
        }
    }

    @Builtin(name = "disable", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class DisableNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone doit(PythonModule module) {
            ModuleState moduleState = module.getModuleState(ModuleState.class);
            moduleState.enabled = false;
            return PNone.NONE;
        }
    }

    @Builtin(name = "is_enabled", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class IsEnabledNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doit(PythonModule module) {
            ModuleState moduleState = module.getModuleState(ModuleState.class);
            return moduleState.enabled;
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
