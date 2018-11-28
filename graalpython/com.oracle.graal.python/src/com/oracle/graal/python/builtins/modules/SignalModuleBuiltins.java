/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Hashtable;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@CoreFunctions(defineModule = "_signal")
public class SignalModuleBuiltins extends PythonBuiltins {
    private static Hashtable<Integer, Object> signalHandlers = new Hashtable<>();

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

    @Builtin(name = "alarm", fixedNumOfPositionalArgs = 1)
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

    @Builtin(name = "getsignal", fixedNumOfPositionalArgs = 1)
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

    @Builtin(name = "signal", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SignalNode extends PythonBinaryBuiltinNode {
        @Child CreateArgumentsNode createArgs = CreateArgumentsNode.create();

        @Specialization
        @TruffleBoundary
        Object signal(int signum, int id) {
            Object retval;
            try {
                retval = Signals.setSignalHandler(signum, id);
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
            signalHandlers.put(signum, id);
            return retval;
        }

        private Object installSignalHandler(int signum, PythonCallable handler, RootCallTarget callTarget, Object[] arguments) {
            Object retval;
            try {
                retval = Signals.setSignalHandler(signum, () -> {
                    callTarget.call(arguments);
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
        // TODO: This needs to be fixed, any object with a "__call__" should work

        // TODO: the second argument should be the interrupted, currently executing frame
        // we'll get that when we switch to executing these handlers (just like finalizers)
        // on the main thread
        @Specialization
        @TruffleBoundary
        Object signal(int signum, PBuiltinMethod handler) {
            return installSignalHandler(signum, handler, handler.getCallTarget(), createArgs.executeWithSelf(handler.getSelf(), new Object[]{signum, PNone.NONE}));
        }

        @Specialization
        @TruffleBoundary
        Object signal(int signum, PBuiltinFunction handler) {
            return installSignalHandler(signum, handler, handler.getCallTarget(), createArgs.execute(new Object[]{signum, PNone.NONE}));
        }

        @Specialization
        @TruffleBoundary
        Object signal(int signum, PMethod handler) {
            return installSignalHandler(signum, handler, handler.getCallTarget(), createArgs.executeWithSelf(handler.getSelf(), new Object[]{signum, PNone.NONE}));
        }

        @Specialization
        @TruffleBoundary
        Object signal(int signum, PFunction handler) {
            return installSignalHandler(signum, handler, handler.getCallTarget(), createArgs.execute(new Object[]{signum, PNone.NONE}));
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

    @TruffleBoundary
    synchronized static void scheduleAlarm(long seconds) {
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            while ((System.currentTimeMillis() - t0) < seconds * 1000) {
                try {
                    Thread.sleep(seconds * 1000);
                } catch (InterruptedException e) {
                }
            }
            sun.misc.Signal.raise(new sun.misc.Signal("ALRM"));
        }).start();
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
