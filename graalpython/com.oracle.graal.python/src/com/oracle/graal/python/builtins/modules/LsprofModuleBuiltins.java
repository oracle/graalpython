/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSampler.Payload;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;

@CoreFunctions(defineModule = "_lsprof")
public class LsprofModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return LsprofModuleBuiltinsFactory.getFactories();
    }

    public static PythonBuiltins newProfilerBuiltins() {
        return new ProfilerBuiltins();
    }

    @Builtin(name = "Profiler", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.LsprofProfiler)
    @GenerateNodeFactory
    abstract static class LsprofNew extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Profiler doit(LazyPythonClass cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            Env env = getContext().getEnv();
            Map<String, InstrumentInfo> instruments = env.getInstruments();
            InstrumentInfo instrumentInfo = instruments.get(CPUSamplerInstrument.ID);
            if (instrumentInfo != null) {
                CPUSampler sampler = env.lookup(instrumentInfo, CPUSampler.class);
                if (sampler != null) {
                    return factory().trace(new Profiler(cls, factory().makeStorage(cls), sampler));
                }
            }
            throw raise(PythonBuiltinClassType.NotImplementedError, "coverage tracker not available");
        }
    }
}

class Profiler extends PythonBuiltinObject {
    boolean subcalls;
    boolean builtins;
    double timeunit;
    Object externalTimer;
    double time;
    final CPUSampler sampler;

    public Profiler(LazyPythonClass cls, DynamicObject storage, CPUSampler sampler) {
        super(cls, storage);
        this.sampler = sampler;
        this.sampler.setFilter(SourceSectionFilter.newBuilder().includeInternal(true).build());
        this.sampler.setMode(CPUSampler.Mode.ROOTS);
        this.sampler.setPeriod(1);
    }
}

@CoreFunctions(extendClasses = PythonBuiltinClassType.LsprofProfiler)
class ProfilerBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ProfilerBuiltinsFactory.getFactories();
    }

    @Builtin(name = SpecialMethodNames.__INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "timer", "timeunit", "subcalls", "builtins"})
    @GenerateNodeFactory
    abstract static class Init extends PythonBuiltinNode {
        @Specialization
        PNone doit(Profiler self, Object timer, double timeunit, long subcalls, long builtins) {
            self.subcalls = subcalls > 0;
            self.builtins = builtins > 0;
            self.timeunit = timeunit;
            self.externalTimer = timer;
            return PNone.NONE;
        }

        @Specialization
        @SuppressWarnings("unused")
        PNone doit(Profiler self, Object timer, PNone timeunit, PNone subcalls, PNone builtins) {
            self.subcalls = true;
            self.builtins = true;
            self.timeunit = -1;
            self.externalTimer = timer;
            return PNone.NONE;
        }
    }

    @Builtin(name = "enable", minNumOfPositionalArgs = 1, parameterNames = {"$self", "subcalls", "builtins"})
    @GenerateNodeFactory
    abstract static class Enable extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone doit(Profiler self, long subcalls, long builtins) {
            self.subcalls = subcalls > 0;
            self.builtins = builtins > 0;
            // TODO: deal with any arguments
            self.time = System.currentTimeMillis();
            self.sampler.setCollecting(true);
            return PNone.NONE;
        }

        @Specialization
        PNone doit(Profiler self, long subcalls, @SuppressWarnings("unused") PNone builtins) {
            return doit(self, subcalls, self.builtins ? 1 : 0);
        }

        @Specialization
        PNone doit(Profiler self, @SuppressWarnings("unused") PNone subcalls, long builtins) {
            return doit(self, self.subcalls ? 1 : 0, builtins);
        }

        @Specialization
        PNone doit(Profiler self, @SuppressWarnings("unused") PNone subcalls, @SuppressWarnings("unused") PNone builtins) {
            return doit(self, self.subcalls ? 1 : 0, self.builtins ? 1 : 0);
        }
    }

    @Builtin(name = "disable", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class Disable extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone doit(Profiler self) {
            self.sampler.setCollecting(false);
            self.time = (System.currentTimeMillis() - self.time) / 1000D;
            return PNone.NONE;
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class Clear extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone doit(Profiler self) {
            self.sampler.clearData();
            return PNone.NONE;
        }
    }

    @Builtin(name = "getstats", minNumOfPositionalArgs = 1, doc = "" +
                    "getstats() -> list of profiler_entry objects\n" +
                    "\n" +
                    "Return all information collected by the profiler.\n" +
                    "Each profiler_entry is a tuple-like object with the\n" +
                    "following attributes:\n" +
                    "\n" +
                    "    code          code object or functionname\n" +
                    "    callcount     how many times this was called\n" +
                    "    reccallcount  how many times called recursively\n" +
                    "    totaltime     total time in this entry\n" +
                    "    inlinetime    inline time in this entry (not in subcalls)\n" +
                    "    calls         details of the calls\n" +
                    "\n" +
                    "The calls attribute is either None or a list of\n" +
                    "profiler_subentry objects:\n" +
                    "\n" +
                    "    code          called code object\n" +
                    "    callcount     how many times this is called\n" +
                    "    reccallcount  how many times this is called recursively\n" +
                    "    totaltime     total time spent in this call\n" +
                    "    inlinetime    inline time (not in further subcalls)\n")
    @GenerateNodeFactory
    abstract static class GetStats extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PList doit(Profiler self) {
            double avgSampleSeconds = self.sampler.getPeriod() / 1000D;
            List<PList> entries = new ArrayList<>();
            for (ProfilerNode<Payload> node : self.sampler.getRootNodes()) {
                countNode(entries, node, avgSampleSeconds);
            }
            self.sampler.close();
            return factory().createList(entries.toArray());
        }

        private void countNode(List<PList> entries, ProfilerNode<Payload> node, double avgSampleTime) {
            Collection<ProfilerNode<Payload>> children = node.getChildren();
            Object[] profilerEntry = getProfilerEntry(node, avgSampleTime);
            Object[] calls = new Object[children.size()];
            int callIdx = 0;
            for (ProfilerNode<Payload> childNode : children) {
                countNode(entries, childNode, avgSampleTime);
                calls[callIdx++] = factory().createList(getProfilerEntry(childNode, avgSampleTime));
            }
            assert callIdx == calls.length;
            profilerEntry[profilerEntry.length - 1] = factory().createList(calls);
            entries.add(factory().createList(profilerEntry));
        }

        private static Object[] getProfilerEntry(ProfilerNode<Payload> node, double avgSampleTime) {
            SourceSection sec = node.getSourceSection();
            String rootName;
            if (sec == null) {
                rootName = node.getRootName();
            } else {
                rootName = sec.getSource().getName() + ":" + sec.getStartLine() + "(" + node.getRootName() + ")";
            }
            if (rootName == null) {
                rootName = "<unknown root>";
            }
            int otherHitCount = node.getPayload().getHitCount();
            int selfHitCount = node.getPayload().getSelfHitCount();
            long hitCount = (long) otherHitCount + selfHitCount;
            Object[] profilerEntry = new Object[]{
                            rootName,
                            hitCount,
                            0,
                            otherHitCount * avgSampleTime,
                            selfHitCount * avgSampleTime,
                            PNone.NONE
            };
            return profilerEntry;
        }
    }
}
