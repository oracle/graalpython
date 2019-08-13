/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = "gc")
public final class GcModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return GcModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "collect", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GcCollectNode extends PythonBuiltinNode {
        @Specialization
        int collect(VirtualFrame frame) {
            doGc();
            // collect some weak references now
            getContext().triggerAsyncActions(frame, this);
            return 0;
        }

        @TruffleBoundary
        private static void doGc() {
            System.gc();
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
            return factory().createTuple(new Object[]{count, 0, 0});
        }
    }

    @Builtin(name = "is_tracked", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GcIsTrackedNode extends PythonBuiltinNode {
        @Specialization
        public boolean isTracked(@SuppressWarnings("unused") PythonNativeObject object) {
            return false;
        }

        @Specialization
        public boolean isTracked(@SuppressWarnings("unused") PythonNativeClass object) {
            // TODO: this is not correct
            return true;
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
        PList getReferents(@SuppressWarnings("unused") Object objects) {
            // TODO: this is just a dummy implementation; for native objects, this should actually
            // use 'tp_traverse'
            return factory().createList();
        }
    }
}
