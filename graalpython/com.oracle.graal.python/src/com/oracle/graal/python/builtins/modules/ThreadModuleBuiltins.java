/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.lang.ref.WeakReference;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNode.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "_thread")
public class ThreadModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "__truffle_get_timeout_max__", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetTimeoutMaxConstNode extends PythonBuiltinNode {
        @Specialization
        double getId() {
            return TIMEOUT_MAX;
        }
    }

    @Builtin(name = "LockType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PLock)
    @GenerateNodeFactory
    abstract static class ConstructLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PLock construct(LazyPythonClass cls) {
            return factory().createLock(cls);
        }
    }

    @Builtin(name = "RLock", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PRLock)
    @GenerateNodeFactory
    abstract static class ConstructRLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PRLock construct(LazyPythonClass cls) {
            return factory().createRLock(cls);
        }
    }

    @Builtin(name = "get_ident", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetCurrentThreadIdNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        long getId() {
            return Thread.currentThread().getId();
        }
    }

    @Builtin(name = "_count", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetThreadCountNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        long getCount() {
            return getContext().getThreadGroup().activeCount();
        }
    }

    @Builtin(name = "stack_size", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetThreadStackSizeNode extends PythonUnaryBuiltinNode {
        private final ConditionProfile invalidSizeProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        long getStackSize(@SuppressWarnings("unused") PNone stackSize) {
            return getContext().getPythonThreadStackSize();
        }

        private long setAndGetStackSizeInternal(long stackSize) {
            if (invalidSizeProfile.profile(stackSize < 0)) {
                throw raise(ValueError, "size must be 0 or a positive value");
            }
            return getContext().getAndSetPythonsThreadStackSize(stackSize);
        }

        @Specialization
        long getStackSize(int stackSize) {
            return setAndGetStackSizeInternal(stackSize);
        }

        @Specialization
        long getStackSize(long stackSize) {
            return setAndGetStackSizeInternal(stackSize);
        }
    }

    @Builtin(name = "start_new_thread", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PThread)
    @GenerateNodeFactory
    abstract static class StartNewThreadNode extends PythonBuiltinNode {
        @Specialization
        long start(VirtualFrame frame, LazyPythonClass cls, Object callable, Object args, Object kwargs,
                        @Cached CallNode callNode,
                        @Cached ExecutePositionalStarargsNode getArgsNode,
                        @Cached ExpandKeywordStarargsNode getKwArgsNode) {
            PythonContext context = getContext();
            TruffleLanguage.Env env = context.getEnv();

            // TODO: python thread stack size != java thread stack size
            // ignore setting the stack size for the moment
            Thread thread = env.createThread(() -> {
                Object[] arguments = getArgsNode.executeWith(frame, args);
                PKeyword[] keywords = getKwArgsNode.executeWith(kwargs);

                // n.b.: It is important to pass 'null' frame here because each thread has it's own
                // stack and if we would pass the current frame, this would be connected as a caller
                // which is incorrect. However, the thread-local 'topframeref' is initialized with
                // EMPTY which will be picked up.
                callNode.execute(null, callable, arguments, keywords);
            }, env.getContext(), context.getThreadGroup());

            PThread pThread = factory().createPythonThread(cls, thread);
            pThread.start();
            return pThread.getId();
        }
    }

    @Builtin(name = "_set_sentinel", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class SetSentinelNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object setSentinel(
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            PLock sentinelLock = factory().createLock();
            context.setSentinelLockWeakref(new WeakReference<>(sentinelLock));
            return sentinelLock;
        }
    }
}
