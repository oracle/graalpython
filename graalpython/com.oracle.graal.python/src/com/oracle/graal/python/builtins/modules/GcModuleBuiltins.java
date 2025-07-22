/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GcModuleBuiltinsClinicProviders.GcCollectNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.GcModuleBuiltinsClinicProviders.SetDebugNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "gc")
public final class GcModuleBuiltins extends PythonBuiltins {

    private static final TruffleString CALLBACKS = PythonUtils.tsLiteral("callbacks");
    private static final TruffleString START = PythonUtils.tsLiteral("start");
    private static final TruffleString STOP = PythonUtils.tsLiteral("stop");
    private static final TruffleString GENERATION = PythonUtils.tsLiteral("generation");
    private static final TruffleString COLLECTED = PythonUtils.tsLiteral("collected");
    private static final TruffleString UNCOLLECTABLE = PythonUtils.tsLiteral("uncollectable");
    private static final int DEBUG_STATS = 1;
    private static final int DEBUG_COLLECTABLE = 1 << 1;
    private static final int DEBUG_UNCOLLECTABLE = 1 << 2;
    private static final int DEBUG_SAVEALL = 1 << 5;
    private static final int DEBUG_LEAK = DEBUG_COLLECTABLE | DEBUG_UNCOLLECTABLE | DEBUG_SAVEALL;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GcModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("DEBUG_STATS", DEBUG_STATS);
        addBuiltinConstant("DEBUG_COLLECTABLE", DEBUG_COLLECTABLE);
        addBuiltinConstant("DEBUG_UNCOLLECTABLE", DEBUG_UNCOLLECTABLE);
        addBuiltinConstant("DEBUG_SAVEALL", DEBUG_SAVEALL);
        addBuiltinConstant("DEBUG_LEAK", DEBUG_LEAK);
        addBuiltinConstant(CALLBACKS, PFactory.createList(core.getLanguage()));
        super.initialize(core);
    }

    @Builtin(name = "collect", parameterNames = {"$self", "generation"}, declaresExplicitSelf = true)
    @ArgumentClinic(name = "generation", conversion = ClinicConversion.Int, defaultValue = "2")
    @GenerateNodeFactory
    abstract static class GcCollectNode extends PythonBinaryClinicBuiltinNode {
        private static final NativeCAPISymbol SYMBOL = NativeCAPISymbol.FUN_GRAALPY_GC_COLLECT;
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, SYMBOL.getName());

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GcCollectNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static long collect(VirtualFrame frame, PythonModule self, @SuppressWarnings("unused") Object level,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectGetIter getIter,
                        @Cached(neverDefault = true) PyIterNextNode next,
                        @Bind PythonLanguage language,
                        @Cached CallBinaryMethodNode call,
                        @Cached GilNode gil,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached ExternalFunctionInvokeNode invokeNode,
                        @Cached CheckPrimitiveFunctionResultNode checkPrimitiveFunctionResultNode) {
            Object callbacks = getAttr.execute(frame, inliningTarget, self, CALLBACKS);
            Object iter = getIter.execute(frame, inliningTarget, callbacks);
            TruffleString phase = null;
            Object info;
            long res = 0;
            Object cb;
            try {
                cb = next.execute(frame, inliningTarget, iter);
                phase = START;
                info = PFactory.createDict(language, new PKeyword[]{
                                new PKeyword(GENERATION, 2),
                                new PKeyword(COLLECTED, 0),
                                new PKeyword(UNCOLLECTABLE, 0),
                });
                while (true) {
                    try {
                        call.executeObject(frame, cb, phase, info);
                        cb = next.execute(frame, inliningTarget, iter);
                    } catch (IteratorExhausted e) {
                        break;
                    }
                }
            } catch (IteratorExhausted e) {
                // fallthrough
            }
            long freedMemory = javaCollect(inliningTarget, gil);
            PythonContext pythonContext = PythonContext.get(inliningTarget);
            // call native 'gc_collect' if C API context is already available
            if (pythonContext.getCApiContext() != null && pythonContext.getLanguage(inliningTarget).getEngineOption(PythonOptions.PythonGC)) {
                Object executable = CApiContext.getNativeSymbol(inliningTarget, SYMBOL);
                PythonThreadState threadState = getThreadStateNode.execute(inliningTarget);
                Object result = invokeNode.call(frame, inliningTarget, threadState, C_API_TIMING, SYMBOL.getTsName(), executable, level);
                res = checkPrimitiveFunctionResultNode.executeLong(threadState, SYMBOL.getTsName(), result);
            }
            if (phase != null) {
                phase = STOP;
                info = PFactory.createDict(language, new PKeyword[]{
                                new PKeyword(GENERATION, 2),
                                new PKeyword(COLLECTED, freedMemory),
                                new PKeyword(UNCOLLECTABLE, 0),
                });
                iter = getIter.execute(frame, inliningTarget, callbacks);
                while (true) {
                    try {
                        cb = next.execute(frame, inliningTarget, iter);
                        call.executeObject(frame, cb, phase, info);
                    } catch (IteratorExhausted e) {
                        break;
                    }
                }
            }
            return res;
        }

        @TruffleBoundary
        static long javaCollect(Node inliningTarget, GilNode gil) {
            gil.release(true);
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory();
            try {
                PythonUtils.forceFullGC();
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    // doesn't matter, just trying to give the GC more time
                }
            } finally {
                gil.acquire();
            }
            // collect some weak references now
            PythonContext.triggerAsyncActions(inliningTarget);
            CApiTransitions.pollReferenceQueue();
            /*
             * CPython's GC returns the number of collected cycles. This is not something we can
             * determine, but to return some useful info to the Python program, we return the amount
             * of memory gained.
             */
            return Math.max(0, runtime.freeMemory() - freeMemory);
        }
    }

    @Builtin(name = "isenabled", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GcIsEnabledNode extends PythonBuiltinNode {
        @Specialization
        boolean doGeneric() {
            return getContext().getGcState().isEnabled();
        }
    }

    @Builtin(name = "disable", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class DisableNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone disable() {
            PythonContext context = PythonContext.get(null);
            context.getGcState().setEnabled(false);
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                CStructAccess.WriteIntNode.writeUncached(cApiContext.getGCState(), CFields.GCState__enabled, 0);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "enable", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class EnableNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone enable() {
            PythonContext context = PythonContext.get(null);
            context.getGcState().setEnabled(true);
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                CStructAccess.WriteIntNode.writeUncached(cApiContext.getGCState(), CFields.GCState__enabled, 1);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_debug", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetDebugNode extends PythonBuiltinNode {
        @Specialization
        int getDebug() {
            return getContext().getGcState().getDebug();
        }
    }

    @Builtin(name = "set_debug", minNumOfPositionalArgs = 1, parameterNames = {"flags"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int)
    abstract static class SetDebugNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SetDebugNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static PNone doGeneric(int flags) {
            PythonContext context = PythonContext.get(null);
            context.getGcState().setDebug(flags);
            CApiContext cApiContext = context.getCApiContext();
            if (cApiContext != null) {
                CStructAccess.WriteIntNode.writeUncached(cApiContext.getGCState(), CFields.GCState__debug, flags);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_count", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GcCountNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PTuple count() {
            List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long count = 0;
            for (GarbageCollectorMXBean gcbean : garbageCollectorMXBeans) {
                long cc = gcbean.getCollectionCount();
                if (cc > 0) {
                    count += cc;
                }
            }
            return PFactory.createTuple(PythonLanguage.get(null), new Object[]{count, 0, 0});
        }
    }

    @Builtin(name = "is_tracked", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GcIsTrackedNode extends PythonBuiltinNode {
        @Specialization
        static boolean doNative(@SuppressWarnings("unused") PythonNativeObject object) {
            return false;
        }

        @Fallback
        static boolean doManaged(@SuppressWarnings("unused") Object object) {
            return true;
        }
    }

    @Builtin(name = "get_referents", takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class GcGetReferentsNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PList getReferents(@SuppressWarnings("unused") Object objects) {
            // TODO: this is just a dummy implementation; for native objects, this should actually
            // use 'tp_traverse'
            return PFactory.createList(PythonLanguage.get(null));
        }
    }

    @Builtin(name = "get_referrers", takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class GcGetReferrersNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PList doGeneric(@SuppressWarnings("unused") Object objects) {
            // dummy implementation
            return PFactory.createList(PythonLanguage.get(null));
        }
    }
}
