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
import static com.oracle.graal.python.nodes.BuiltinNames.T_THREADING;
import static com.oracle.graal.python.nodes.BuiltinNames.T__THREAD;
import static com.oracle.graal.python.nodes.BuiltinNames.T___EXCEPTHOOK__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PThreadHandle;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyTupleCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
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

    private static final TruffleString T_GRAALPY_THREAD_EXIT = tsLiteral("_graalpy_thread_exit");

    public static final StructSequence.BuiltinTypeDescriptor EXCEPTHOOK_ARGS_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PExceptHookArgs,
                    4,
                    new String[]{
                                    "exc_type", "exc_value", "exc_traceback", "thread"},
                    new String[]{
                                    "Exception type", "Exception value", "Exception traceback",
                                    "Exception thread"});

    private static final class ModuleState {
        int count;
        final List<PThreadHandle> shutdownHandles = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("error", core.lookupType(PythonBuiltinClassType.RuntimeError));
        addBuiltinConstant("TIMEOUT_MAX", TIMEOUT_MAX);
        StructSequence.initType(core, EXCEPTHOOK_ARGS_DESC);
        core.lookupBuiltinModule(T__THREAD).setModuleState(new ModuleState());
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
            return Thread.currentThread().threadId();
        }
    }

    @Builtin(name = "get_native_id", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetNativeIdNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static long getId() {
            return Thread.currentThread().threadId();
        }
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetThreadCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long getCount(PythonModule self) {
            return self.getModuleState(ModuleState.class).count;
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

        @Specialization
        @SuppressWarnings("try")
        static long start(VirtualFrame frame, Object callable, Object args, Object kwargs,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached CallNode callNode,
                        @Cached ExecutePositionalStarargsNode getArgsNode,
                        @Cached ExpandKeywordStarargsNode getKwArgsNode,
                        @Cached PyCallableCheckNode callableCheck,
                        @Cached PyTupleCheckNode tupleCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!callableCheck.execute(inliningTarget, callable)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.FIRST_ARG_MUST_BE_CALLABLE);
            }
            if (!tupleCheck.execute(inliningTarget, args)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.SECOND_ARG_MUST_BE_TUPLE);
            }
            if (kwargs != PNone.NO_VALUE && !(kwargs instanceof PDict)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.OPTIONAL_THIRD_ARG_MUST_BE_DICT);
            }

            // if args is an arbitrary iterable, converting it to an Object[] may run Python code
            Object[] arguments = getArgsNode.executeWith(frame, args);
            PKeyword[] keywords = getKwArgsNode.execute(frame, inliningTarget, kwargs);

            PThreadHandle handle = PFactory.createThreadHandle(context.getLanguage(inliningTarget));
            startThread(context, handle, callable, arguments, keywords, true, callNode, raiseNode, inliningTarget);
            return handle.getIdent();
        }
    }

    @Builtin(name = "start_joinable_thread", minNumOfPositionalArgs = 1, parameterNames = {"function", "handle", "daemon"})
    @ArgumentClinic(name = "daemon", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class StartJoinableThreadNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        static PThreadHandle start(Object callable, Object handleObj, boolean daemon,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached CallNode callNode,
                        @Cached PyCallableCheckNode callableCheck,
                        @Cached PRaiseNode raiseNode) {
            if (!callableCheck.execute(inliningTarget, callable)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.THREAD_FUNCTION_MUST_BE_CALLABLE);
            }
            PThreadHandle handle;
            if (handleObj instanceof PNone) {
                handle = PFactory.createThreadHandle(context.getLanguage(inliningTarget));
            } else if (handleObj instanceof PThreadHandle) {
                handle = (PThreadHandle) handleObj;
            } else {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.HANDLE_MUST_BE_THREAD_HANDLE);
            }
            startThread(context, handle, callable, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, daemon, callNode, raiseNode, inliningTarget);
            return handle;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ThreadModuleBuiltinsClinicProviders.StartJoinableThreadNodeClinicProviderGen.INSTANCE;
        }
    }

    @SuppressWarnings("try")
    private static void startThread(PythonContext context, PThreadHandle handle, Object callable, Object[] arguments, PKeyword[] keywords, boolean daemon, CallNode callNode, PRaiseNode raiseNode,
                    Node inliningTarget) {
        if (context.isFinalizing()) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.PythonFinalizationError, ErrorMessages.CANT_CREATE_NEW_THREAD_AT_INTERPRETER_SHUTDOWN);
        }
        TruffleLanguage.Env env = context.getEnv();
        PythonModule threadModule = context.lookupBuiltinModule(T__THREAD);
        ModuleState state = threadModule.getModuleState(ModuleState.class);

        if (!handle.markStarting()) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.THREAD_ALREADY_STARTED);
        }
        if (!daemon) {
            addShutdownHandle(state, handle);
        }

        try {
            // TODO: python thread stack size != java thread stack size
            // ignore setting the stack size for the moment
            TruffleThreadBuilder threadBuilder = env.newTruffleThreadBuilder(() -> {
                try (GilNode.UncachedAcquire gil = GilNode.uncachedAcquire()) {
                    // the increment is protected by the gil
                    state.count++;
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
                            WriteUnraisableNode.getUncached().execute(e.getUnreifiedException(), ErrorMessages.IN_THREAD_STARTED_BY, callable);
                        }
                        // SystemExit is silently ignored (see _threadmodule.c: thread_run)
                    } finally {
                        state.count--;
                    }
                    // Do not call back into Python after PythonThreadKillException during shutdown.
                    removeDummyThread(context, callNode);
                } finally {
                    if (!daemon) {
                        removeShutdownHandle(state, handle);
                    }
                    handle.notifyThreadExiting();
                }
            }).context(env.getContext()).threadGroup(context.getThreadGroup());

            Thread thread = threadBuilder.build();
            handle.setRunning(thread);
            startThread(thread);
        } catch (Throwable t) {
            if (!daemon) {
                removeShutdownHandle(state, handle);
            }
            handle.notifyThreadExiting();
            throw t;
        }
    }

    private static void removeDummyThread(PythonContext context, CallNode callNode) {
        Object threadingModule = HashingStorageGetItem.executeUncached(context.getSysModules().getDictStorage(), T_THREADING);
        if (threadingModule != null) {
            Object callback = ReadAttributeFromObjectNode.getUncached().execute(threadingModule, T_GRAALPY_THREAD_EXIT);
            if (callback != PNone.NO_VALUE) {
                callNode.execute(null, callback);
            }
        }
    }

    @TruffleBoundary
    private static void startThread(Thread thread) {
        thread.start();
    }

    @TruffleBoundary
    private static void addShutdownHandle(ModuleState state, PThreadHandle handle) {
        state.shutdownHandles.add(handle);
    }

    @TruffleBoundary
    private static void removeShutdownHandle(ModuleState state, PThreadHandle handle) {
        state.shutdownHandles.remove(handle);
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

    @Builtin(name = "_is_main_interpreter", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class IsMainInterpreterNode extends PythonBuiltinNode {
        @Specialization
        static boolean isMainInterpreter() {
            return true;
        }
    }

    @Builtin(name = "_get_main_thread_ident", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetMainThreadIdentNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static long getMainThreadIdent(
                        @Bind PythonContext context) {
            Thread mainThread = context.getMainThread();
            return (mainThread != null ? mainThread : Thread.currentThread()).threadId();
        }
    }

    @Builtin(name = "_make_thread_handle", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class MakeThreadHandleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PThreadHandle makeThreadHandle(Object identObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached CastToJavaUnsignedLongNode castToJavaUnsignedLongNode,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheckNode.execute(inliningTarget, identObj)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.IDENT_MUST_BE_INTEGER);
            }
            PThreadHandle handle = PFactory.createThreadHandle(language);
            handle.setRunning(castToJavaUnsignedLongNode.execute(inliningTarget, identObj));
            return handle;
        }
    }

    @Builtin(name = "_shutdown", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ShutdownNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        @SuppressWarnings("try")
        static Object shutdown(PythonModule self,
                        @Bind Node inliningTarget) {
            ModuleState state = self.getModuleState(ModuleState.class);
            long currentIdent = Thread.currentThread().threadId();
            while (true) {
                PThreadHandle handle = nextShutdownHandle(state, currentIdent);
                if (handle == null) {
                    return PNone.NONE;
                }
                try (var gil = GilNode.UncachedRelease.uncachedRelease()) {
                    handle.join(inliningTarget, -1);
                }
            }
        }

        @TruffleBoundary
        private static PThreadHandle nextShutdownHandle(ModuleState state, long currentIdent) {
            synchronized (state.shutdownHandles) {
                for (PThreadHandle handle : state.shutdownHandles) {
                    if (handle.getIdent() != currentIdent) {
                        return handle;
                    }
                }
            }
            return null;
        }
    }
}
