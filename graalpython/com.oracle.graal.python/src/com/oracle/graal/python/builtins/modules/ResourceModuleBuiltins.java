/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "resource")
public class ResourceModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ResourceModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "getrusage", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetRuUsageNode extends PythonBuiltinNode {
        protected static int RUSAGE_CHILDREN = -1;
        protected static int RUSAGE_SELF = 0;
        protected static int RUSAGE_THREAD = 1;

        @Specialization(guards = {"who == RUSAGE_THREAD"})
        @TruffleBoundary
        PTuple getruusageThread(@SuppressWarnings("unused") int who) {
            long id = Thread.currentThread().getId();
            Runtime runtime = Runtime.getRuntime();

            double ru_utime = 0; // time in user mode (float)
            double ru_stime = 0; // time in system mode (float)
            long ru_maxrss; // maximum resident set size

            if (!TruffleOptions.AOT) {
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
                    ru_utime = threadMXBean.getThreadUserTime(id) / 1000000000.0;
                    ru_stime = (threadMXBean.getThreadCpuTime(id) - threadMXBean.getThreadUserTime(id)) / 1000000000.0;
                }

                if (threadMXBean instanceof com.sun.management.ThreadMXBean) {
                    com.sun.management.ThreadMXBean thMxBean = (com.sun.management.ThreadMXBean) threadMXBean;
                    ru_maxrss = thMxBean.getThreadAllocatedBytes(id);
                } else {
                    ru_maxrss = runtime.maxMemory();
                }
            } else {
                ru_maxrss = runtime.maxMemory();
            }

            String osName = System.getProperty("os.name");
            if (osName.contains("Linux")) {
                // peak memory usage (kilobytes on Linux
                ru_maxrss /= 1024;
            }

            long ru_ixrss = -1; // shared memory size
            long ru_idrss = -1; // unshared memory size
            long ru_isrss = -1; // unshared stack size
            long ru_minflt = -1; // page faults not requiring I/O
            long ru_majflt = -1;  // page faults requiring I/O
            long ru_nswap = -1;  // number of swap outs
            long ru_inblock = -1; // block input operations
            long ru_oublock = -1;  // block output operations
            long ru_msgsnd = -1; // messages sent
            long ru_msgrcv = -1; // messages received
            long ru_nsignals = -1; // signals received
            long ru_nvcsw = -1; // voluntary context switches
            long ru_nivcsw = -1; // nvoluntary context switches
            return factory().createTuple(new Object[]{ru_utime, ru_stime, ru_maxrss, ru_ixrss, ru_idrss, ru_isrss,
                            ru_minflt, ru_majflt, ru_nswap, ru_inblock, ru_oublock, ru_msgsnd, ru_msgrcv, ru_nsignals,
                            ru_nvcsw, ru_nivcsw});
        }

        @Specialization(guards = {"who == RUSAGE_SELF"})
        @TruffleBoundary
        PTuple getruusageSelf(@SuppressWarnings("unused") int who) {
            Runtime runtime = Runtime.getRuntime();

            double ru_utime = 0; // time in user mode (float)
            double ru_stime = 0; // time in system mode (float)
            long ru_maxrss;

            if (!TruffleOptions.AOT) {
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                if (threadMXBean.isThreadCpuTimeSupported()) {
                    for (long thId : threadMXBean.getAllThreadIds()) {
                        long tu = threadMXBean.getThreadUserTime(thId);
                        long tc = threadMXBean.getThreadCpuTime(thId);

                        if (tu != -1) {
                            ru_utime += tu / 1000000000.0;
                        }

                        if (tu != -1 && tc != -1) {
                            ru_stime += (tc - tu) / 1000000000.0;
                        }
                    }
                }

                MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
                MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
                MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
                ru_maxrss = heapMemoryUsage.getCommitted() + nonHeapMemoryUsage.getCommitted();
            } else {
                ru_maxrss = runtime.maxMemory();
            }

            String osName = System.getProperty("os.name");
            if (osName.contains("Linux")) {
                // peak memory usage (kilobytes on Linux
                ru_maxrss /= 1024;
            }

            long ru_ixrss = -1; // shared memory size
            long ru_idrss = -1; // unshared memory size
            long ru_isrss = -1; // unshared stack size
            long ru_minflt = -1; // page faults not requiring I/O
            long ru_majflt = -1;  // page faults requiring I/O
            long ru_nswap = -1;  // number of swap outs
            long ru_inblock = -1; // block input operations
            long ru_oublock = -1;  // block output operations
            long ru_msgsnd = -1; // messages sent
            long ru_msgrcv = -1; // messages received
            long ru_nsignals = -1; // signals received
            long ru_nvcsw = -1; // voluntary context switches
            long ru_nivcsw = -1; // nvoluntary context switches
            return factory().createTuple(new Object[]{ru_utime, ru_stime, ru_maxrss, ru_ixrss, ru_idrss, ru_isrss,
                            ru_minflt, ru_majflt, ru_nswap, ru_inblock, ru_oublock, ru_msgsnd, ru_msgrcv, ru_nsignals,
                            ru_nvcsw, ru_nivcsw});
        }

        @Fallback
        PTuple getruusage(@SuppressWarnings("unused") Object who) {
            throw raise(ValueError, "ruusage usage not yet implemented for specified arg.");
        }
    }
}
