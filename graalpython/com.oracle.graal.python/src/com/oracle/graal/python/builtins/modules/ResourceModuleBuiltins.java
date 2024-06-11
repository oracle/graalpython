/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RusageResult;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = "resource")
public final class ResourceModuleBuiltins extends PythonBuiltins {

    static int RLIMIT_CPU = 0;
    static int RLIMIT_FSIZE = 1;
    static int RLIMIT_DATA = 2;
    static int RLIMIT_STACK = 3;
    static int RLIMIT_CORE = 4;
    static int RLIMIT_AS = 5;
    static int RLIMIT_RSS = 5;
    static int RLIMIT_MEMLOCK = 6;
    static int RLIMIT_NPROC = 7;
    static int RLIMIT_NOFILE = 8;

    static long RLIM_INFINITY = Long.MAX_VALUE;

    static final StructSequence.BuiltinTypeDescriptor STRUCT_RUSAGE_DESC = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PStructRusage,
                    // @formatter:off The formatter joins these lines making it less readable
                    "struct_rusage: Result from getrusage.\n\n" +
                    "This object may be accessed either as a tuple of\n" +
                    "    (utime,stime,maxrss,ixrss,idrss,isrss,minflt,majflt,\n" +
                    "    nswap,inblock,oublock,msgsnd,msgrcv,nsignals,nvcsw,nivcsw)\n" +
                    "or via the attributes ru_utime, ru_stime, ru_maxrss, and so on.",
                    // @formatter:on
                    16,
                    new String[]{
                                    "ru_utime", "ru_stime", "ru_maxrss",
                                    "ru_ixrss", "ru_idrss", "ru_isrss",
                                    "ru_minflt", "ru_majflt",
                                    "ru_nswap", "ru_inblock", "ru_oublock",
                                    "ru_msgsnd", "ru_msgrcv", "ru_nsignals",
                                    "ru_nvcsw", "ru_nivcsw"
                    },
                    new String[]{
                                    "user time used", "system time used", "max. resident set size",
                                    "shared memory size", "unshared data size", "unshared stack size",
                                    "page faults not requiring I/O", "page faults requiring I/O",
                                    "number of swap outs", "block input operations", "block output operations",
                                    "IPC messages sent", "IPC messages received", "signals received",
                                    "voluntary context switches", "involuntary context switches"
                    });

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ResourceModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);

        addBuiltinConstant("error", PythonBuiltinClassType.OSError);

        if (PosixConstants.RUSAGE_CHILDREN.defined) {
            addBuiltinConstant("RUSAGE_CHILDREN", PosixConstants.RUSAGE_CHILDREN.getValueIfDefined());
        }
        addBuiltinConstant("RUSAGE_SELF", PosixConstants.RUSAGE_SELF.value);
        if (PosixConstants.RUSAGE_THREAD.defined) {
            addBuiltinConstant("RUSAGE_THREAD", PosixConstants.RUSAGE_THREAD.getValueIfDefined());
        }

        addBuiltinConstant("RLIMIT_CPU", RLIMIT_CPU);
        addBuiltinConstant("RLIMIT_FSIZE", RLIMIT_FSIZE);
        addBuiltinConstant("RLIMIT_DATA", RLIMIT_DATA);
        addBuiltinConstant("RLIMIT_STACK", RLIMIT_STACK);
        addBuiltinConstant("RLIMIT_CORE", RLIMIT_CORE);
        addBuiltinConstant("RLIMIT_AS", RLIMIT_AS);
        addBuiltinConstant("RLIMIT_RSS", RLIMIT_RSS);
        addBuiltinConstant("RLIMIT_MEMLOCK", RLIMIT_MEMLOCK);
        addBuiltinConstant("RLIMIT_NPROC", RLIMIT_NPROC);
        addBuiltinConstant("RLIMIT_NOFILE", RLIMIT_NOFILE);

        addBuiltinConstant("RLIM_INFINITY", RLIM_INFINITY);

        StructSequence.initType(core, STRUCT_RUSAGE_DESC);
    }

    @Builtin(name = "getrusage", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(ResourceModuleBuiltins.class)
    abstract static class GetRuUsageNode extends PythonBuiltinNode {

        @Specialization
        static PTuple getruusage(VirtualFrame frame, int who,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PosixSupport posixSupport = PosixSupport.get(inliningTarget);
            RusageResult rusage;
            try {
                rusage = posixLib.getrusage(posixSupport, who);
            } catch (PosixException e) {
                if (e.getErrorCode() == OSErrorEnum.EINVAL.getNumber()) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.RUSAGE_INVALID_WHO);
                } else {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            }

            return PythonObjectFactory.getUncached().createStructSeq(STRUCT_RUSAGE_DESC,
                            rusage.ru_utime(), rusage.ru_stime(),
                            rusage.ru_maxrss(), rusage.ru_ixrss(), rusage.ru_idrss(), rusage.ru_isrss(),
                            rusage.ru_minflt(), rusage.ru_majflt(), rusage.ru_nswap(), rusage.ru_inblock(), rusage.ru_oublock(),
                            rusage.ru_msgsnd(), rusage.ru_msgrcv(), rusage.ru_nsignals(), rusage.ru_nvcsw(), rusage.ru_nivcsw());
        }
    }

    @Builtin(name = "getpagesize", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class GetPageSizeNode extends PythonBuiltinNode {
        @Specialization
        static int getPageSize() {
            return 4096;
        }
    }

    @Builtin(name = "getrlimit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetRLimitNode extends PythonBuiltinNode {
        @Specialization
        static PTuple getPageSize(@SuppressWarnings("unused") int which,
                        @Cached PythonObjectFactory factory) {
            // dummy implementation - report "unrestricted" for everything
            return factory.createTuple(new Object[]{RLIM_INFINITY, RLIM_INFINITY});
        }
    }
}
