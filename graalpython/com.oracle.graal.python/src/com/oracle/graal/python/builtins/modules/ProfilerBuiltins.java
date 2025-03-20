package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;

@CoreFunctions(extendClasses = PythonBuiltinClassType.LsprofProfiler)
public class ProfilerBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ProfilerBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ProfilerBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, parameterNames = {"$self", "timer", "timeunit", "subcalls", "builtins"})
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
        static PList doit(Profiler self) {
            double avgSampleSeconds = self.sampler.getPeriod() / 1000D;
            List<PTuple> entries = new ArrayList<>();
            for (CPUSamplerData data : self.sampler.getDataList()) {
                Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threads = data.getThreadData();
                for (Thread thread : threads.keySet()) {
                    for (ProfilerNode<CPUSampler.Payload> node : threads.get(thread)) {
                        countNode(entries, node, avgSampleSeconds);
                    }
                }
            }

            self.sampler.close();
            return PFactory.createList(PythonLanguage.get(null), entries.toArray());
        }

        private static void countNode(List<PTuple> entries, ProfilerNode<CPUSampler.Payload> node, double avgSampleTime) {
            PythonLanguage language = PythonLanguage.get(null);
            Collection<ProfilerNode<CPUSampler.Payload>> children = node.getChildren();
            Object[] profilerEntry = getProfilerEntry(node, avgSampleTime);
            Object[] calls = new Object[children.size()];
            int callIdx = 0;
            for (ProfilerNode<CPUSampler.Payload> childNode : children) {
                countNode(entries, childNode, avgSampleTime);
                calls[callIdx++] = PFactory.createStructSeq(language, LsprofModuleBuiltins.PROFILER_SUBENTRY_DESC, getProfilerEntry(childNode, avgSampleTime));
            }
            assert callIdx == calls.length;
            profilerEntry = Arrays.copyOf(profilerEntry, 6);
            profilerEntry[profilerEntry.length - 1] = PFactory.createList(language, calls);
            entries.add(PFactory.createStructSeq(language, LsprofModuleBuiltins.PROFILER_ENTRY_DESC, profilerEntry));
        }

        private static Object[] getProfilerEntry(ProfilerNode<CPUSampler.Payload> node, double avgSampleTime) {
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
                            toTruffleStringUncached(rootName),
                            hitCount,
                            0,
                            otherHitCount * avgSampleTime,
                            selfHitCount * avgSampleTime
            };
            return profilerEntry;
        }
    }
}
