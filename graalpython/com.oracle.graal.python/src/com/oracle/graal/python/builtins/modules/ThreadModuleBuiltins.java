/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.TIMEOUT_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXIT;
import static com.oracle.graal.python.nodes.BuiltinNames.J__THREAD;
import static com.oracle.graal.python.nodes.BuiltinNames.T_STDERR;
import static com.oracle.graal.python.nodes.BuiltinNames.T___EXCEPTHOOK__;
import static com.oracle.graal.python.nodes.BuiltinNames.T__THREAD;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.ref.WeakReference;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleThreadBuilder;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__THREAD)
public final class ThreadModuleBuiltins extends PythonBuiltins {

    public static final StructSequence.BuiltinTypeDescriptor EXCEPTHOOK_ARGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PExceptHookArgs,
                    4,
                    new String[]{
                                    "exc_type", "exc_value", "exc_traceback", "thread"},
                    new String[]{
                                    "Exception type", "Exception value", "Exception traceback",
                                    "Exception thread"});

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("error", core.lookupType(PythonBuiltinClassType.RuntimeError));
        addBuiltinConstant("TIMEOUT_MAX", TIMEOUT_MAX);
        StructSequence.initType(core, EXCEPTHOOK_ARGS_DESC);
        core.lookupBuiltinModule(T__THREAD).setModuleState(0);
        super.initialize(core);
    }

    @Builtin(name = "allocate_lock", maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AllocateLockNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        PLock construct(Object self, Object unused,
                        @Bind PythonLanguage language) {
            return PFactory.createLock(language);
        }
    }

    @Builtin(name = "get_ident", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetCurrentThreadIdNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static long getId() {
            return PThread.getThreadId(Thread.currentThread());
        }
    }

    @Builtin(name = "get_native_id", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetNativeIdNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static long getId() {
            return PThread.getThreadId(Thread.currentThread());
        }
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetThreadCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long getCount(PythonModule self) {
            return self.getModuleState(Integer.class);
        }
    }

    @Builtin(name = "stack_size", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetThreadStackSizeNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNoValue(stackSize)")
        long getStackSize(@SuppressWarnings("unused") PNone stackSize) {
            return getContext().getPythonThreadStackSize();
        }

        @Fallback
        static long getStackSize(VirtualFrame frame, Object stackSizeObj,
                        @Bind Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode raiseNode) {
            int stackSize = asSizeNode.executeExact(frame, inliningTarget, stackSizeObj);
            if (stackSize < 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.SIZE_MUST_BE_D_OR_S, 0, "a positive value");
            }
            return PythonContext.get(inliningTarget).getAndSetPythonsThreadStackSize(stackSize);
        }
    }

    @Builtin(name = "_excepthook", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetThreadExceptHookNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getExceptHook(@SuppressWarnings("unused") PythonModule self,
                        Object exceptHookArgs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectSetAttr setAttr,
                        @Cached PyObjectStrAsTruffleStringNode strNode) {

            Object argsType = GetClassNode.GetPythonObjectClassNode.executeUncached((PythonObject) exceptHookArgs);
            if (!TypeNodes.IsSameTypeNode.executeUncached(argsType, PythonBuiltinClassType.PExceptHookArgs)) {
                throw PRaiseNode.getUncached().raise(raiseNode, PythonBuiltinClassType.TypeError, ErrorMessages.ARG_TYPE_MUST_BE, "_thread.excepthook", "ExceptHookArgs");
            }
            SequenceStorage seq = ((PTuple) exceptHookArgs).getSequenceStorage();
            if (seq.length() != 4) {
                throw PRaiseNode.getUncached().raise(raiseNode, PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, 4, seq.length());
            }

            Object excType = SequenceStorageNodes.GetItemScalarNode.executeUncached(seq, 0);

            if (TypeNodes.IsSameTypeNode.executeUncached(excType, PythonBuiltinClassType.SystemExit)) {
                return PNone.NONE;
            }
            Object excValue = SequenceStorageNodes.GetItemScalarNode.executeUncached(seq, 1);
            Object excTraceback = SequenceStorageNodes.GetItemScalarNode.executeUncached(seq, 2);
            Object thread = SequenceStorageNodes.GetItemScalarNode.executeUncached(seq, 3);

            TruffleString name;

            Object nameAttr = lookupAttr.execute(null, inliningTarget, thread, tsLiteral("_name"));
            if (nameAttr != null && nameAttr != PNone.NONE && nameAttr != PNone.NO_VALUE) {
                name = strNode.execute(null, inliningTarget, nameAttr);
            } else {
                Object getIdentBuiltin = lookupAttr.execute(null, inliningTarget, thread, tsLiteral("get_ident"));
                Object ident = callNode.executeWithoutFrame(getIdentBuiltin);
                name = ident != null ? strNode.execute(null, inliningTarget, ident) : tsLiteral("<unknown>");
            }

            Object sysMod = getContext().getSysModule();
            Object stdErr = lookupAttr.execute(null, inliningTarget, sysMod, T_STDERR);

            boolean stdErrInvalid = stdErr == null || stdErr == PNone.NONE || stdErr == PNone.NO_VALUE;

            if (stdErrInvalid) {
                if (thread != null && thread != PNone.NONE && thread != PNone.NO_VALUE) {
                    stdErr = lookupAttr.execute(null, inliningTarget, thread, tsLiteral("_stderr"));
                }
                if (stdErr == null || stdErr == PNone.NONE || stdErr == PNone.NO_VALUE) {
                    return PNone.NONE;
                }
            }

            Object write = lookupAttr.execute(null, inliningTarget, stdErr, tsLiteral("write"));
            Object flush = lookupAttr.execute(null, inliningTarget, stdErr, tsLiteral("flush"));

            callNode.executeWithoutFrame(write, tsLiteral("Exception in thread "));
            callNode.executeWithoutFrame(write, name);
            callNode.executeWithoutFrame(write, tsLiteral(":\n"));
            callNode.executeWithoutFrame(flush);

            Object sysExcepthook = lookupAttr.execute(null, inliningTarget, sysMod, T___EXCEPTHOOK__);
            if (sysExcepthook != PNone.NO_VALUE && sysExcepthook != PNone.NONE) {
                if (!stdErrInvalid) {
                    callNode.executeWithoutFrame(sysExcepthook, excType, excValue, excTraceback);
                } else {
                    Object oldStdErr = lookupAttr.execute(null, inliningTarget, sysMod, T_STDERR);
                    try {
                        setAttr.execute(inliningTarget, sysMod, T_STDERR, stdErr);
                        callNode.executeWithoutFrame(sysExcepthook, excType, excValue, excTraceback);
                    } finally {
                        setAttr.execute(inliningTarget, sysMod, T_STDERR, oldStdErr == PNone.NO_VALUE ? PNone.NONE : oldStdErr);
                    }
                }
                callNode.executeWithoutFrame(flush);
            } else if (excValue instanceof PBaseException) {
                callNode.executeWithoutFrame(write, strNode.execute(null, inliningTarget, excValue));
                callNode.executeWithoutFrame(flush);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "start_new_thread", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @Builtin(name = "start_new", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class StartNewThreadNode extends PythonTernaryBuiltinNode {

        private static final TruffleString IN_THREAD_STARTED_BY = tsLiteral("in thread started by");

        @Specialization
        @SuppressWarnings("try")
        static long start(VirtualFrame frame, Object callable, Object args, Object kwargs,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached CallNode callNode,
                        @Cached ExecutePositionalStarargsNode getArgsNode,
                        @Cached ExpandKeywordStarargsNode getKwArgsNode) {
            TruffleLanguage.Env env = context.getEnv();
            PythonModule threadModule = context.lookupBuiltinModule(T__THREAD);

            // if args is an arbitrary iterable, converting it to an Object[] may run Python code
            Object[] arguments = getArgsNode.executeWith(frame, args);
            PKeyword[] keywords = getKwArgsNode.execute(frame, inliningTarget, kwargs);

            // TODO: python thread stack size != java thread stack size
            // ignore setting the stack size for the moment
            TruffleThreadBuilder threadBuilder = env.newTruffleThreadBuilder(() -> {
                try (GilNode.UncachedAcquire gil = GilNode.uncachedAcquire()) {
                    // the increment is protected by the gil
                    int curCount = threadModule.getModuleState(Integer.class);
                    threadModule.setModuleState(curCount + 1);
                    try {
                        // n.b.: It is important to pass 'null' frame here because each thread has
                        // its own stack and if we would pass the current frame, this would be
                        // connected as a caller which is incorrect. However, the thread-local
                        // 'topframeref' is initialized with EMPTY which will be picked up.
                        callNode.execute(null, callable, arguments, keywords);
                    } catch (PythonThreadKillException e) {
                        return;
                    } catch (PException e) {
                        if (!IsBuiltinObjectProfile.profileObjectUncached(e.getUnreifiedException(), PythonBuiltinClassType.SystemExit)) {
                            WriteUnraisableNode.getUncached().execute(e.getUnreifiedException(), IN_THREAD_STARTED_BY, callable);
                        }
                        // SystemExit is silently ignored (see _threadmodule.c: thread_run)
                    } finally {
                        curCount = threadModule.getModuleState(Integer.class);
                        threadModule.setModuleState(curCount - 1);
                    }
                }
            }).context(env.getContext()).threadGroup(context.getThreadGroup());

            Thread thread = threadBuilder.build();
            startThread(thread);
            return PThread.getThreadId(thread);
        }

        @TruffleBoundary
        private static void startThread(Thread thread) {
            thread.start();
        }
    }

    @Builtin(name = "_set_sentinel", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class SetSentinelNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object setSentinel() {
            PythonContext context = PythonContext.get(null);
            PLock sentinelLock = PFactory.createLock(context.getLanguage());
            context.setSentinelLockWeakref(new WeakReference<>(sentinelLock));
            return sentinelLock;
        }
    }

    @Builtin(name = "interrupt_main", parameterNames = {"signum"}, doc = "interrupt_main()\n" +
                    "\n" +
                    "Raise a KeyboardInterrupt in the main thread.\n" +
                    "A subthread can use this function to interrupt the main thread.")
    @ArgumentClinic(name = "signum", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "SIGINT")
    @GenerateNodeFactory
    abstract static class InterruptMainThreadNode extends PythonUnaryClinicBuiltinNode {
        static final int SIGINT = 2;

        @Specialization
        @SuppressWarnings("unused")
        Object getCount(@SuppressWarnings("unused") int signum) {
            // TODO: implement me
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ThreadModuleBuiltinsClinicProviders.InterruptMainThreadNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_EXIT)
    @Builtin(name = "exit_thread")
    @GenerateNodeFactory
    abstract static class ExitThreadNode extends PythonBuiltinNode {
        @Specialization
        static Object exit(
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseSystemExitStatic(inliningTarget, PNone.NONE);
        }
    }

    @Builtin(name = "daemon_threads_allowed", minNumOfPositionalArgs = 0, doc = "daemon_threads_allowed()\n" +
                    "\n" +
                    "Return True if daemon threads are allowed in the current interpreter,\n" +
                    "and False otherwise.\n")
    @GenerateNodeFactory
    public abstract static class DaemonThreadsAllowedNode extends PythonBuiltinNode {
        @Specialization
        public static boolean daemonAllowed() {
            return true;
        }
    }
}
