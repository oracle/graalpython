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

import static com.oracle.graal.python.nodes.BuiltinNames.T__SIGNAL;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.graal.python.util.TimeUtils.SEC_TO_US;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@CoreFunctions(defineModule = "_signal", isEager = true)
public final class SignalModuleBuiltins extends PythonBuiltins {
    private static final int ITIMER_REAL = 0;
    private static final int ITIMER_VIRTUAL = 1;
    private static final int ITIMER_PROF = 2;

    public static final String J_DEFAULT_INT_HANDLER = "default_int_handler";
    public static final TruffleString T_DEFAULT_INT_HANDLER = tsLiteral(J_DEFAULT_INT_HANDLER);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SignalModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("SIG_DFL", Signals.SIG_DFL);
        addBuiltinConstant("SIG_IGN", Signals.SIG_IGN);
        addBuiltinConstant("ITIMER_REAL", ITIMER_REAL);
        addBuiltinConstant("ITIMER_VIRTUAL", ITIMER_VIRTUAL);
        addBuiltinConstant("ITIMER_PROF", ITIMER_PROF);
        addBuiltinConstant("NSIG", Signals.SIGMAX + 1);
    }

    /*
     * When using emulated posix mode (where we esentially emulate Linux), we rely on some signals
     * being present even if they don't exist on the underlying platform, i.e. SIGKILL doesn't exist
     * on Windows, but we still need to pass it to emulated kill.
     */
    private enum EmulatedSignal {
        TERM(15),
        KILL(9);

        public final TruffleString name;
        public final int number;

        EmulatedSignal(int number) {
            this.name = tsLiteral("SIG" + name());
            this.number = number;
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonModule signalModule = core.lookupBuiltinModule(T__SIGNAL);
        ModuleData moduleData = new ModuleData();
        signalModule.setModuleState(moduleData);

        for (int i = 0; i < Signals.PYTHON_SIGNAL_NAMES.length; i++) {
            TruffleString name = Signals.PYTHON_SIGNAL_NAMES[i];
            if (name != null) {
                moduleData.signals.put(Signals.SIGNAL_NAMES[i], i);
                signalModule.setAttribute(name, i);
            }
        }

        var context = core.getContext();
        if (PosixSupportLibrary.getUncached().getBackend(context.getPosixSupport()).equalsUncached(T_JAVA, TS_ENCODING)) {
            for (EmulatedSignal signal : EmulatedSignal.values()) {
                if (signalModule.getAttribute(signal.name) == PNone.NO_VALUE) {
                    moduleData.signals.put(signal.name(), signal.number);
                    signalModule.setAttribute(signal.name, signal.number);
                }
            }
        }

        context.registerAsyncAction(() -> {
            SignalTriggerAction poll = moduleData.signalQueue.poll();
            if (PythonOptions.AUTOMATIC_ASYNC_ACTIONS) {
                try {
                    while (poll == null) {
                        moduleData.signalSema.acquire();
                        poll = moduleData.signalQueue.poll();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return poll;
        });

        if (!context.getEnv().isPreInitialization() && context.getOption(PythonOptions.InstallSignalHandlers)) {
            Object defaultSigintHandler = signalModule.getAttribute(T_DEFAULT_INT_HANDLER);
            assert defaultSigintHandler != PNone.NO_VALUE;
            SignalNode.signal(null, new Signal("INT").getNumber(), defaultSigintHandler, moduleData);
        }
    }

    @TruffleBoundary
    public static int signalFromName(PythonContext context, String name) {
        PythonModule mod = context.lookupBuiltinModule(T__SIGNAL);
        return mod.getModuleState(ModuleData.class).signals.getOrDefault(name, -1);
    }

    public static void resetSignalHandlers(PythonModule mod) {
        ModuleData data = mod.getModuleState(ModuleData.class);
        if (data != null) {
            for (Map.Entry<Integer, SignalHandler> entry : data.defaultSignalHandlers.entrySet()) {
                try {
                    Signals.setSignalHandler(entry.getKey(), entry.getValue());
                } catch (IllegalArgumentException e) {
                    // Resetting the signal handlers to their original values is best-effort and
                    // may not work, so we ignore errors here.
                }
            }
            data.signalHandlers.clear();
            data.defaultSignalHandlers.clear();
        }
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

    @Builtin(name = "valid_signals")
    @GenerateNodeFactory
    abstract static class ValidSignalsNode extends PythonBuiltinNode {
        @Specialization
        static Object validSignals(
                        @Bind PythonLanguage language) {
            int signalCount = 0;
            for (int i = 0; i < Signals.SIGNAL_NAMES.length; i++) {
                if (Signals.SIGNAL_NAMES[i] != null) {
                    signalCount++;
                }
            }
            int[] validSignals = new int[signalCount];
            int j = 0;
            for (int i = 0; i < Signals.SIGNAL_NAMES.length; i++) {
                if (Signals.SIGNAL_NAMES[i] != null) {
                    validSignals[j++] = i;
                }
            }

            return PFactory.createTuple(language, validSignals);
        }
    }

    @Builtin(name = "alarm", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, declaresExplicitSelf = true, parameterNames = {"$mod", "seconds"})
    @ArgumentClinic(name = "seconds", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class AlarmNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        int alarm(PythonModule module, int seconds) {
            int remaining = 0;
            ModuleData data = module.getModuleState(ModuleData.class);
            Signals.Alarm currentAlarm = data.currentAlarm;
            if (currentAlarm != null) {
                if (currentAlarm.isRunning()) {
                    remaining = currentAlarm.getRemainingSeconds();
                    if (remaining < 0) {
                        remaining = 0;
                    }
                    currentAlarm.cancel();
                }
            }
            if (seconds > 0) {
                Signals.Alarm newAlarm = new Signals.Alarm(seconds);
                data.currentAlarm = newAlarm;
                newAlarm.start();
            }
            return remaining;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.AlarmNodeClinicProviderGen.INSTANCE;
        }
    }

    @TruffleBoundary
    private static Object handlerToPython(SignalHandler handler, int signum, ModuleData data) {
        if (!(handler instanceof Signals.PythonSignalHandler)) {
            // Save default JVM handlers to be restored later
            data.defaultSignalHandlers.put(signum, handler);
        }
        if (handler == sun.misc.SignalHandler.SIG_DFL) {
            return Signals.SIG_DFL;
        } else if (handler == sun.misc.SignalHandler.SIG_IGN) {
            return Signals.SIG_IGN;
        } else if (handler instanceof Signals.PythonSignalHandler) {
            return data.signalHandlers.getOrDefault(signum, PNone.NONE);
        } else {
            // Most likely JVM's handler, pretend it's the default
            return Signals.SIG_DFL;
        }
    }

    @Builtin(name = "getsignal", declaresExplicitSelf = true, minNumOfPositionalArgs = 2, parameterNames = {"$mod", "signalnum"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class GetSignalNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object getsignal(PythonModule mod, int signum) {
            ModuleData data = mod.getModuleState(ModuleData.class);
            return handlerToPython(Signals.getCurrentSignalHandler(signum), signum, data);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.GetSignalNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_DEFAULT_INT_HANDLER, minNumOfPositionalArgs = 0, takesVarArgs = true, takesVarKeywordArgs = false)
    @GenerateNodeFactory
    abstract static class DefaultIntHandlerNode extends PythonBuiltinNode {
        @Specialization
        static Object defaultIntHandler(@SuppressWarnings("unused") Object[] args,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.KeyboardInterrupt);
        }
    }

    @Builtin(name = "signal", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SignalNode extends PythonTernaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object signalHandler(PythonModule self, Object signal, Object handler,
                        @Bind Node inliningTarget) {
            ModuleData data = self.getModuleState(ModuleData.class);
            int signum = PyNumberAsSizeNode.executeExactUncached(signal);
            if (PyCallableCheckNode.executeUncached(handler)) {
                return signal(inliningTarget, signum, handler, data);
            } else {
                // Note: CPython checks if id is the same reference as SIG_IGN/SIG_DFL constants,
                // which
                // are instances of Handlers enum
                // The -1 fallback will be correctly reported as an error later on
                int id;
                try {
                    id = CastToJavaIntExactNode.executeUncached(handler);
                } catch (CannotCastException | PException e) {
                    id = -1;
                }
                return signal(inliningTarget, signum, id, data);
            }
        }

        @TruffleBoundary
        static Object signal(Node raisingNode, int signum, Object handler, ModuleData data) {
            SignalHandler oldHandler;
            SignalTriggerAction signalTrigger = new SignalTriggerAction(handler, signum);
            try {
                oldHandler = Signals.setSignalHandler(signum, () -> {
                    data.signalQueue.add(signalTrigger);
                    data.signalSema.release();
                });
            } catch (IllegalArgumentException e) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonErrorType.ValueError, e);
            }
            Object result = handlerToPython(oldHandler, signum, data);
            data.signalHandlers.put(signum, handler);
            return result;
        }

        @TruffleBoundary
        private static Object signal(Node raisingNode, int signum, int id, ModuleData data) {
            SignalHandler oldHandler;
            try {
                if (id == Signals.SIG_DFL && data.defaultSignalHandlers.containsKey(signum)) {
                    oldHandler = Signals.setSignalHandler(signum, data.defaultSignalHandlers.get(signum));
                } else {
                    oldHandler = Signals.setSignalHandler(signum, id);
                }
            } catch (IllegalArgumentException e) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonErrorType.TypeError, ErrorMessages.SIGNAL_MUST_BE_SIGIGN_SIGDFL_OR_CALLABLE_OBJ);
            }
            Object result = handlerToPython(oldHandler, signum, data);
            data.signalHandlers.remove(signum);
            return result;
        }
    }

    @Builtin(name = "set_wakeup_fd", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"fd", "warn_on_full_buffer"})
    @GenerateNodeFactory
    abstract static class SetWakeupFdNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static int doGeneric(Object fd, Object warnOnFullBuffer) {
            // TODO: implement
            return -1;
        }
    }

    @Builtin(name = "siginterrupt", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"signalnum", "flag"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "flag", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class SigInterruptNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone doInt(VirtualFrame frame, @SuppressWarnings("unused") int signalnum, boolean flag,
                        @Bind Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            if (flag) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.EINVAL);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.SigInterruptNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "raise_signal", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"signalnum"})
    @ArgumentClinic(name = "signalnum", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class RaiseSignalNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone doInt(int signum) {
            Signal.raise(new sun.misc.Signal(Signals.signalNumberToName(signum)));
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.RaiseSignalNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "setitimer", minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "which", "seconds", "interval"})
    @ArgumentClinic(name = "which", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetitimerNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.SetitimerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doIt(VirtualFrame frame, PythonModule self, int which, Object seconds, Object interval,
                        @Bind Node inliningTarget,
                        @Cached PyTimeFromObjectNode timeFromObjectNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Bind PythonLanguage language) {
            ModuleData moduleData = self.getModuleState(ModuleData.class);
            long usDelay = toMicroseconds(frame, inliningTarget, seconds, timeFromObjectNode);
            long usInterval = toMicroseconds(frame, inliningTarget, interval, timeFromObjectNode);
            if (which != ITIMER_REAL) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.EINVAL);
            }
            PTuple resultTuple = GetitimerNode.createResultTuple(language, moduleData);
            setitimer(moduleData, usDelay, usInterval);
            return resultTuple;
        }

        private static long toMicroseconds(VirtualFrame frame, Node inliningTarget, Object obj, PyTimeFromObjectNode timeFromObjectNode) {
            if (obj == PNone.NO_VALUE) {
                return 0;
            }
            return timeFromObjectNode.execute(frame, inliningTarget, obj, RoundType.CEILING, SEC_TO_US);
        }

        @TruffleBoundary
        private void setitimer(ModuleData moduleData, long usDelay, long usInterval) {
            if (moduleData.itimerFuture != null) {
                moduleData.itimerFuture.cancel(false);
                moduleData.itimerFuture = null;
                moduleData.itimerInterval = 0;
            }
            if (usDelay == 0) {
                return;
            }
            moduleData.itimerInterval = usInterval;
            Runnable r = () -> Signals.raiseSignal("ALRM");
            ScheduledExecutorService itimerService = getItimerService(moduleData);
            if (usInterval == 0) {
                moduleData.itimerFuture = itimerService.schedule(r, usDelay, TimeUnit.MICROSECONDS);
            } else {
                moduleData.itimerFuture = itimerService.scheduleAtFixedRate(r, usDelay, usInterval, TimeUnit.MICROSECONDS);
            }
        }

        @TruffleBoundary
        private ScheduledExecutorService getItimerService(ModuleData moduleData) {
            if (moduleData.itimerService == null) {
                ScheduledExecutorService itimerService = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread t = Executors.defaultThreadFactory().newThread(runnable);
                    t.setDaemon(true);
                    return t;
                });
                moduleData.itimerService = itimerService;
                getContext().registerAtexitHook(ctx -> itimerService.shutdown());
            }
            return moduleData.itimerService;
        }
    }

    @Builtin(name = "getitimer", minNumOfPositionalArgs = 1, declaresExplicitSelf = true, parameterNames = {"$self", "which"})
    @ArgumentClinic(name = "which", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class GetitimerNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SignalModuleBuiltinsClinicProviders.GetitimerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object doIt(VirtualFrame frame, PythonModule self, int which,
                        @Bind Node inliningTarget,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Bind PythonLanguage language) {
            ModuleData moduleData = self.getModuleState(ModuleData.class);
            if (which != ITIMER_REAL) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSError(frame, OSErrorEnum.EINVAL);
            }
            return createResultTuple(language, moduleData);
        }

        static PTuple createResultTuple(PythonLanguage language, ModuleData moduleData) {
            long oldInterval = moduleData.itimerInterval;
            long oldDelay = getOldDelay(moduleData);
            return PFactory.createTuple(language, new Object[]{oldDelay / (double) SEC_TO_US, oldInterval / (double) SEC_TO_US});
        }

        @TruffleBoundary
        static long getOldDelay(ModuleData moduleData) {
            if (moduleData.itimerFuture == null) {
                return 0;
            }
            long delay = moduleData.itimerFuture.getDelay(TimeUnit.MICROSECONDS);
            if (delay < 0) {
                return 0;
            }
            return delay;
        }
    }

    private static final class ModuleData {
        final Map<String, Integer> signals = new HashMap<>();
        final ConcurrentHashMap<Integer, Object> signalHandlers = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Integer, SignalHandler> defaultSignalHandlers = new ConcurrentHashMap<>();
        final ConcurrentLinkedDeque<SignalTriggerAction> signalQueue = new ConcurrentLinkedDeque<>();
        final Semaphore signalSema = new Semaphore(0);
        ScheduledExecutorService itimerService;
        ScheduledFuture<?> itimerFuture;
        long itimerInterval;
        Signals.Alarm currentAlarm;
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
    static final int SIGMAX = 64;
    static final String[] SIGNAL_NAMES = new String[SIGMAX + 1];
    static final TruffleString[] PYTHON_SIGNAL_NAMES = new TruffleString[SIGMAX + 1];

    static {
        for (String signal : new String[]{"HUP", "INT", "BREAK", "QUIT", "ILL", "TRAP", "IOT", "ABRT", "EMT", "FPE",
                        "KILL", "BUS", "SEGV", "SYS", "PIPE", "ALRM", "TERM", "USR1", "USR2", "CLD", "CHLD", "PWR",
                        "IO", "URG", "WINCH", "POLL", "STOP", "TSTP", "CONT", "TTIN", "TTOU", "VTALRM", "PROF",
                        "XCPU", "XFSZ", "RTMIN", "RTMAX", "INFO", "STKFLT"}) {
            try {
                int number = new sun.misc.Signal(signal).getNumber();
                if (number > SIGMAX) {
                    continue;
                }
                SIGNAL_NAMES[number] = signal;
                PYTHON_SIGNAL_NAMES[number] = tsLiteral("SIG" + signal);
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }
    }

    static final class Alarm {
        final int seconds;
        final long startMillis;
        private Thread thread;

        public Alarm(int seconds) {
            this.seconds = seconds;
            startMillis = System.currentTimeMillis();
        }

        public void start() {
            thread = new Thread(() -> {
                try {
                    Thread.sleep((long) seconds * 1000);
                    sun.misc.Signal.raise(new sun.misc.Signal("ALRM"));
                } catch (InterruptedException e) {
                    // Cancelled
                }
            });
            thread.start();
        }

        public boolean isRunning() {
            return thread.isAlive();
        }

        public void cancel() {
            thread.interrupt();
        }

        public int getRemainingSeconds() {
            return seconds - (int) ((System.currentTimeMillis() - startMillis) / 1000);
        }
    }

    @TruffleBoundary
    static void raiseSignal(String name) {
        sun.misc.Signal.raise(new sun.misc.Signal(name));
    }

    static class PythonSignalHandler implements sun.misc.SignalHandler {
        private final Runnable handler;

        public PythonSignalHandler(Runnable handler) {
            this.handler = handler;
        }

        @Override
        public void handle(sun.misc.Signal arg0) {
            handler.run();
        }
    }

    static String signalNumberToName(int signum) {
        return signum > SIGMAX ? "INVALID SIGNAL" : SIGNAL_NAMES[signum];
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, Runnable handler) throws IllegalArgumentException {
        return setSignalHandler(signum, new PythonSignalHandler(handler));
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, SignalHandler handler) throws IllegalArgumentException {
        return sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), handler);
    }

    @TruffleBoundary
    synchronized static SignalHandler setSignalHandler(int signum, int handler) throws IllegalArgumentException {
        sun.misc.SignalHandler h;
        if (handler == SIG_DFL) {
            h = sun.misc.SignalHandler.SIG_DFL;
        } else if (handler == SIG_IGN) {
            h = sun.misc.SignalHandler.SIG_IGN;
        } else {
            throw new IllegalArgumentException();
        }
        return sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), h);
    }

    @TruffleBoundary
    synchronized static SignalHandler getCurrentSignalHandler(int signum) {
        // To check what the current signal handler, we install default to get the current one
        // and immediately replace it again.
        sun.misc.SignalHandler oldH;
        try {
            oldH = sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), sun.misc.SignalHandler.SIG_DFL);
        } catch (IllegalArgumentException e) {
            return sun.misc.SignalHandler.SIG_DFL;
        }
        try {
            sun.misc.Signal.handle(new sun.misc.Signal(signalNumberToName(signum)), oldH);
        } catch (IllegalArgumentException e) {
            return sun.misc.SignalHandler.SIG_DFL;
        }
        return oldH;
    }
}
