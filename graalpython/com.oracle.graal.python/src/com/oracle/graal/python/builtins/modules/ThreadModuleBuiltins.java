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

import static com.oracle.graal.python.builtins.objects.thread.AbstractPythonLock.TIMEOUT_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXIT;
import static com.oracle.graal.python.nodes.BuiltinNames.J__THREAD;
import static com.oracle.graal.python.nodes.BuiltinNames.T__THREAD;
import static com.oracle.graal.python.nodes.HiddenAttr.THREAD_COUNT;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.lang.ref.WeakReference;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.thread.PThreadLocal;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleThreadBuilder;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__THREAD)
public final class ThreadModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("error", core.lookupType(PythonBuiltinClassType.RuntimeError));
        addBuiltinConstant("TIMEOUT_MAX", TIMEOUT_MAX);
        addBuiltinConstant(THREAD_COUNT, 0);
        super.initialize(core);
    }

    @Builtin(name = "_local", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PThreadLocal)
    @GenerateNodeFactory
    abstract static class ThreadLocalNode extends PythonBuiltinNode {
        @Specialization
        PThreadLocal construct(Object cls, Object[] args, PKeyword[] keywordArgs,
                        @Cached PythonObjectFactory factory) {
            return factory.createThreadLocal(cls, args, keywordArgs);
        }
    }

    @Builtin(name = "allocate_lock", maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AllocateLockNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        PLock construct(Object self, Object unused,
                        @Cached PythonObjectFactory factory) {
            return factory.createLock(PythonBuiltinClassType.PLock);
        }
    }

    @Builtin(name = "LockType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PLock)
    @GenerateNodeFactory
    abstract static class ConstructLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PLock construct(Object cls,
                        @Cached PythonObjectFactory factory) {
            return factory.createLock(cls);
        }
    }

    @Builtin(name = "RLock", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PRLock)
    @GenerateNodeFactory
    abstract static class ConstructRLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PRLock construct(Object cls,
                        @Cached PythonObjectFactory factory) {
            return factory.createRLock(cls);
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
            return (int) HiddenAttr.ReadNode.executeUncached(self, THREAD_COUNT, 0);
        }
    }

    @Builtin(name = "stack_size", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GetThreadStackSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        long getStackSize(@SuppressWarnings("unused") PNone stackSize) {
            return getContext().getPythonThreadStackSize();
        }

        @Specialization
        static long getStackSize(long stackSize,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (stackSize < 0) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.SIZE_MUST_BE_D_OR_S, 0, "a positive value");
            }
            return PythonContext.get(inliningTarget).getAndSetPythonsThreadStackSize(stackSize);
        }
    }

    @Builtin(name = "start_new_thread", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PThread)
    @Builtin(name = "start_new", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class StartNewThreadNode extends PythonBuiltinNode {

        private static final TruffleString IN_THREAD_STARTED_BY = tsLiteral("in thread started by");

        @Specialization
        @SuppressWarnings("try")
        long start(VirtualFrame frame, Object cls, Object callable, Object args, Object kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached ExecutePositionalStarargsNode getArgsNode,
                        @Cached ExpandKeywordStarargsNode getKwArgsNode,
                        @Cached PythonObjectFactory factory) {
            PythonContext context = getContext();
            TruffleLanguage.Env env = context.getEnv();
            PythonModule threadModule = context.lookupBuiltinModule(T__THREAD);

            // if args is an arbitrary iterable, converting it to an Object[] may run Python code
            Object[] arguments = getArgsNode.executeWith(frame, args);
            PKeyword[] keywords = getKwArgsNode.execute(frame, inliningTarget, kwargs);

            // TODO: python thread stack size != java thread stack size
            // ignore setting the stack size for the moment
            TruffleThreadBuilder threadBuilder = env.newTruffleThreadBuilder(() -> {
                GilNode.UncachedAcquire gil = GilNode.uncachedAcquire();
                try {
                    // the increment is protected by the gil
                    int curCount = (int) HiddenAttr.ReadNode.executeUncached(threadModule, THREAD_COUNT, 0);
                    HiddenAttr.WriteNode.executeUncached(threadModule, THREAD_COUNT, curCount + 1);
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
                    } finally {
                        curCount = (int) HiddenAttr.ReadNode.executeUncached(threadModule, THREAD_COUNT, 1);
                        HiddenAttr.WriteNode.executeUncached(threadModule, THREAD_COUNT, curCount - 1);
                    }
                } finally {
                    gil.close();
                }
            }).context(env.getContext()).threadGroup(context.getThreadGroup());

            PThread pThread = factory.createPythonThread(cls, threadBuilder.build());
            pThread.start();
            return pThread.getId();
        }
    }

    @Builtin(name = "_set_sentinel", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class SetSentinelNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object setSentinel() {
            PLock sentinelLock = PythonObjectFactory.getUncached().createLock();
            PythonContext.get(this).setSentinelLockWeakref(new WeakReference<>(sentinelLock));
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
                @Cached PRaiseNode raiseNode) {
            throw raiseNode.raiseSystemExit(PNone.NONE);
        }
    }
}
