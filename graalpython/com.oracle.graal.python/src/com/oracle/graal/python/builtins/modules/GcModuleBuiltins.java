/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
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

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GcModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("DEBUG_LEAK", 0);
        addBuiltinConstant("DEBUG_UNCOLLECTABLE", 0);
        addBuiltinConstant("DEBUG_UNCOLLECTABLE", 0);
        addBuiltinConstant(CALLBACKS, PythonObjectFactory.getUncached().createList());
        super.initialize(core);
    }

    @Builtin(name = "collect", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GcCollectNode extends PythonBuiltinNode {
        @Specialization
        static PNone collect(VirtualFrame frame, PythonModule self, @SuppressWarnings("unused") Object level,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectGetIter getIter,
                        @Cached(neverDefault = true) PyIterNextNode next,
                        @Cached PythonObjectFactory factory,
                        @Cached CallBinaryMethodNode call,
                        @Cached GilNode gil) {
            Object callbacks = getAttr.execute(frame, inliningTarget, self, CALLBACKS);
            Object iter = getIter.execute(frame, inliningTarget, callbacks);
            Object cb = next.execute(frame, iter);
            TruffleString phase = null;
            Object info = null;
            if (cb != null) {
                phase = START;
                info = factory.createDict(new PKeyword[]{
                                new PKeyword(GENERATION, 2),
                                new PKeyword(COLLECTED, 0),
                                new PKeyword(UNCOLLECTABLE, 0),
                });
                do {
                    call.executeObject(frame, cb, phase, info);
                } while ((cb = next.execute(frame, iter)) != null);
            }
            long freedMemory = javaCollect(inliningTarget, gil);
            if (phase != null) {
                phase = STOP;
                info = factory.createDict(new PKeyword[]{
                                new PKeyword(GENERATION, 2),
                                new PKeyword(COLLECTED, freedMemory),
                                new PKeyword(UNCOLLECTABLE, 0),
                });
                iter = getIter.execute(frame, inliningTarget, callbacks);
                while ((cb = next.execute(frame, iter)) != null) {
                    call.executeObject(frame, cb, phase, info);
                }
            }
            return PNone.NONE;
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
        boolean isenabled() {
            return getContext().isGcEnabled();
        }
    }

    @Builtin(name = "disable", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class DisableNode extends PythonBuiltinNode {
        @Specialization
        PNone disable() {
            getContext().setGcEnabled(false);
            return PNone.NONE;
        }
    }

    @Builtin(name = "enable", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class EnableNode extends PythonBuiltinNode {
        @Specialization
        PNone enable() {
            getContext().setGcEnabled(true);
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_debug", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetDebugNode extends PythonBuiltinNode {
        @Specialization
        int getDebug() {
            return 0;
        }
    }

    @Builtin(name = "set_debug", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetDebugNode extends PythonBuiltinNode {
        @Specialization
        PNone setDebug(@SuppressWarnings("unused") Object ignored) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_count", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GcCountNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public PTuple count() {
            List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long count = 0;
            for (GarbageCollectorMXBean gcbean : garbageCollectorMXBeans) {
                long cc = gcbean.getCollectionCount();
                if (cc > 0) {
                    count += cc;
                }
            }
            return PythonObjectFactory.getUncached().createTuple(new Object[]{count, 0, 0});
        }
    }

    @Builtin(name = "is_tracked", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GcIsTrackedNode extends PythonBuiltinNode {
        @Specialization
        public boolean isTracked(@SuppressWarnings("unused") PythonNativeObject object) {
            return false;
        }

        @Fallback
        public boolean isTracked(@SuppressWarnings("unused") Object object) {
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
            return PythonContext.get(null).factory().createList();
        }
    }

    @Builtin(name = "get_referrers", takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class GcGetReferrersNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PList doGeneric(@SuppressWarnings("unused") Object objects) {
            // dummy implementation
            return PythonContext.get(null).factory().createList();
        }
    }
}
