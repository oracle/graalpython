/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.HiddenKey;

@CoreFunctions(defineModule = "_signal")
public class SignalModuleBuiltins extends PythonBuiltins {
    private static ConcurrentHashMap<Integer, Object> signalHandlers = new ConcurrentHashMap<>();

    private static final HiddenKey signalQueueKey = new HiddenKey("signalQueue");
    private final ConcurrentLinkedDeque<SignalTriggerAction> signalQueue = new ConcurrentLinkedDeque<>();
    private static final HiddenKey signalSemaKey = new HiddenKey("signalQueue");
    private final Semaphore signalSema = new Semaphore(0);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SignalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("SIG_DFL", Signals.SIG_DFL);
        builtinConstants.put("SIG_IGN", Signals.SIG_IGN);
        for (int i = 0; i < Signals.signalNames.length; i++) {
            String name = Signals.signalNames[i];
            if (name != null) {
                builtinConstants.put("SIG" + name, i);
            }
        }
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);

        PythonModule signalModule = core.lookupBuiltinModule("_signal");
        signalModule.setAttribute(signalQueueKey, signalQueue);
        signalModule.setAttribute(signalSemaKey, signalSema);

        core.getContext().registerAsyncAction(() -> {
            SignalTriggerAction poll = signalQueue.poll();
            try {
                while (poll == null) {
                    signalSema.acquire();
                    poll = signalQueue.poll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return poll;
        });
    }

    private static class SignalTriggerAction extends AsyncHandler.AsyncPythonAction {
        private final Object callableObject;
        private final int signum;

        SignalTriggerAction(Object callable, int signum) {
            this.callableObject = callable;
            this.signum = signum;
        }

        @Override
        public Object callable() {
            return callableObject;
        }

        @Override
        public Object[] arguments() {
            return new Object[]{signum, null};
        }

        @Override
        public int frameIndex() {
            return 1;
        }
    }

    @Builtin(name = "alarm", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AlarmNode extends PythonUnaryBuiltinNode {
        @Specialization
        int alarm(long seconds) {
            Signals.scheduleAlarm(seconds);
            return 0;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int alarm(PInt seconds) {
            Signals.scheduleAlarm(seconds.longValueExact());
            return 0;
        }

        @Specialization
        int alarmOvf(PInt seconds) {
            try {
                Signals.scheduleAlarm(seconds.longValueExact());
                return 0;
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError, "Python int too large to convert to C long");
            }
        }
    }

    @Builtin(name = "getsignal", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSignalNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getsignal(int signum) {
            int currentSignalHandler = Signals.getCurrentSignalHandler(signum);
            if (currentSignalHandler == Signals.SIG_UNKNOWN) {
                if (signalHandlers.containsKey(signum)) {
                    return signalHandlers.get(signum);
                } else {
                    return PNone.NONE;
                }
            } else {
                return currentSignalHandler;
            }
        }
    }

    @Builtin(name = "default_int_handler", minNumOfPositionalArgs = 0, takesVarArgs = true, takesVarKeywordArgs = false)
    @GenerateNodeFactory
    abstract static class DefaultIntHandlerNode extends PythonBuiltinNode {
        @Specialization
        Object defaultIntHandler(@SuppressWarnings("unused") Object[] args) {
            // TODO should be implemented properly.
            throw raise(PythonErrorType.KeyboardInterrupt);
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @Builtin(name = "signal", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SignalNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!idNumLib.isCallable(idNum)", limit = "1")
        Object signalId(@SuppressWarnings("unused") PythonModule self, Object signal, Object idNum,
                        @SuppressWarnings("unused") @CachedLibrary("idNum") PythonObjectLibrary idNumLib,
                        @CachedLibrary("signal") PythonObjectLibrary signalLib) {
            // Note: CPython checks if id is the same reference as SIG_IGN/SIG_DFL constants, which
            // are instances of Handlers enum
            // The -1 fallback will be correctly reported as an error later on
            int id = idNum instanceof Integer ? (int) idNum : -1;
            return signal(signalLib.asSize(signal), id);
        }

        @TruffleBoundary
        private Object signal(int signum, int id) {
            Object retval;
            try {
                retval = Signals.setSignalHandler(signum, id);
            } catch (IllegalArgumentException e) {
                throw raise(PythonErrorType.TypeError, "TypeError: signal handler must be signal.SIG_IGN, signal.SIG_DFL, or a callable object");
            }
            if ((int) retval == Signals.SIG_UNKNOWN) {
                if (signalHandlers.containsKey(signum)) {
                    retval = signalHandlers.get(signum);
                } else {
                    retval = PNone.NONE;
                }
            }
            signalHandlers.put(signum, id);
            return retval;
        }

        @Specialization(guards = "handlerLib.isCallable(handler)", limit = "1")
        Object signalHandler(PythonModule self, Object signal, Object handler,
                        @SuppressWarnings("unused") @CachedLibrary("handler") PythonObjectLibrary handlerLib,
                        @CachedLibrary("signal") PythonObjectLibrary signalLib,
                        @Cached("create()") ReadAttributeFromObjectNode readQueueNode,
                        @Cached("create()") ReadAttributeFromObjectNode readSemaNode) {
            return signal(self, signalLib.asSize(signal), handler, readQueueNode, readSemaNode);
        }

        @TruffleBoundary
        private Object signal(PythonModule self, int signum, Object handler, ReadAttributeFromObjectNode readQueueNode, ReadAttributeFromObjectNode readSemaNode) {
            ConcurrentLinkedDeque<SignalTriggerAction> queue = getQueue(self, readQueueNode);
            Semaphore semaphore = getSemaphore(self, readSemaNode);
            Object retval;
            SignalTriggerAction signalTrigger = new SignalTriggerAction(handler, signum);
            try {
                retval = Signals.setSignalHandler(signum, () -> {
                    queue.add(signalTrigger);
                    semaphore.release();
                });
            } catch (IllegalArgumentException e) {
                throw raise(PythonErrorType.ValueError, e);
            }
            if ((int) retval == Signals.SIG_UNKNOWN) {
                if (signalHandlers.containsKey(signum)) {
                    retval = signalHandlers.get(signum);
                } else {
                    retval = PNone.NONE;
                }
            }
            signalHandlers.put(signum, handler);
            return retval;
        }

        @SuppressWarnings("unchecked")
        private static ConcurrentLinkedDeque<SignalTriggerAction> getQueue(PythonModule self, ReadAttributeFromObjectNode readNode) {
            Object queueObject = readNode.execute(self, signalQueueKey);
            if (queueObject instanceof ConcurrentLinkedDeque) {
                ConcurrentLinkedDeque<SignalTriggerAction> queue = (ConcurrentLinkedDeque<SignalTriggerAction>) queueObject;
                return queue;
            } else {
                throw new IllegalStateException("the signal trigger queue was modified!");
            }
        }

        private static Semaphore getSemaphore(PythonModule self, ReadAttributeFromObjectNode readNode) {
            Object semaphore = readNode.execute(self, signalSemaKey);
            if (semaphore instanceof Semaphore) {
                return (Semaphore) semaphore;
            } else {
                throw new IllegalStateException("the signal trigger semaphore was modified!");
            }
        }
    }
}

// Checkstyle: stop
/*
 * (tfel): This class is supposed to go away to be replaced with a yet to be designed Truffle API
 * for signals
 */
final class Signals {
    static final int SIG_DFL = 0;
    static final int SIG_IGN = 1;
    static final int SIG_UNKNOWN = -1;
    private static final int SIGMAX = 31;
    static final String[] signalNames = new String[SIGMAX + 1];

    static {
        for (String signal : new String[]{"HUP", "INT", "QUIT", "TRAP", "ABRT", "KILL", "ALRM", "TERM", "USR1", "USR2"}) {
            try {
                int number = new sun.misc.Signal(signal).getNumber();
                if (number > SIGMAX) {
                    continue;
                }
                signalNames[number] = signal;
            } catch (IllegalArgumentException e) {
            }
        }
    }

    private static class Alarm implements Runnable {
        private final long seconds;

        Alarm(long seconds) {
            this.seconds = seconds;
        }

        public void run() {
            long t0 = System.currentTimeMillis();
            while ((System.currentTimeMillis() - t0) < seconds * 1000) {
                try {
                    Thread.sleep(seconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            sun.misc.Signal.raise(new sun.misc.Signal("ALRM"));
        }
    }

    @TruffleBoundary
    synchronized static void scheduleAlarm(long seconds) {
        new Thread(new Alarm(seconds)).start();
    }

    private static class PythonSignalHandler implements sun.misc.SignalHandler {
        private final Runnable handler;

        public PythonSignalHandler(Runnable handler) {
            this.handler = handler;
        }

        public void handle(sun.misc.Signal arg0) {
            handler.run();
        }
    }

    static String signalNumberToName(int signum) {
        return signum > SIGMAX ? "INVALID SIGNAL" : signalNames[signum];
    }

    @TruffleBoundary
    synchronized static int setSignalHandler(int signum, Runnable handler) throws IllegalArgumentException {
        sun.misc.SignalHandler oldH = sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), new PythonSignalHandler(handler));
        return handlerToInt(oldH);
    }

    @TruffleBoundary
    synchronized static int setSignalHandler(int signum, int handler) throws IllegalArgumentException {
        sun.misc.SignalHandler h;
        if (handler == SIG_DFL) {
            h = sun.misc.SignalHandler.SIG_DFL;
        } else if (handler == SIG_IGN) {
            h = sun.misc.SignalHandler.SIG_IGN;
        } else {
            throw new IllegalArgumentException();
        }
        return handlerToInt(sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), h));
    }

    @TruffleBoundary
    synchronized static int getCurrentSignalHandler(int signum) {
        // To check what the current signal handler, we install default to get the current one
        // and immediately replace it again.
        sun.misc.SignalHandler oldH;
        try {
            oldH = sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), sun.misc.SignalHandler.SIG_DFL);
        } catch (IllegalArgumentException e) {
            return SIG_DFL;
        }
        try {
            sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), oldH);
        } catch (IllegalArgumentException e) {
            return SIG_DFL;
        }
        return handlerToInt(oldH);
    }

    private static int handlerToInt(sun.misc.SignalHandler oldH) {
        if (oldH == sun.misc.SignalHandler.SIG_DFL) {
            return SIG_DFL;
        } else if (oldH == sun.misc.SignalHandler.SIG_IGN) {
            return SIG_IGN;
        } else {
            return SIG_UNKNOWN;
        }
    }
}
