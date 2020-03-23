package com.oracle.graal.python.builtins.modules;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.tools.coverage.CoverageTracker;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;

@CoreFunctions(defineModule = "_trace")
public class TraceModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TraceModuleBuiltinsFactory.getFactories();
    }

    private static final HiddenKey TRACKER = new HiddenKey("coverageTracker");
    private static final HiddenKey TRACK_FUNCS = new HiddenKey("trackCalledFuncs");

    @Builtin(name = "start", parameterNames = {"mod", "count", "countfuncs", "ignoremods", "ignoredirs"}, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class TraceNew extends PythonBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        PNone doit(VirtualFrame frame, PythonModule mod, Object count, Object countfuncs, PSequence ignoremods, PSequence ignoredirs,
                        @Cached SequenceNodes.GetSequenceStorageNode getStore,
                        @Cached SequenceStorageNodes.ToArrayNode toArray,
                        @Cached CastToJavaStringNode castStr,
                        @CachedLibrary("count") PythonObjectLibrary lib,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached WriteAttributeToObjectNode writeNode) {
            Object currentTracker = readNode.execute(mod, TRACKER);
            CoverageTracker tracker = null;

            SourceSectionFilter.Builder filter = SourceSectionFilter.newBuilder();
            filter.includeInternal(false).mimeTypeIs(PythonLanguage.MIME_TYPE);
            PythonContext context = getContext();
            String stdLibHome = context.getStdlibHome();
            filter.sourceIs((src) -> {
                String path = src.getPath();
                return path != null && !path.contains(stdLibHome);
            });

            Object[] ignoreMods = toArray.execute(getStore.execute(ignoremods));
            Env env = context.getEnv();
            for (Object moduleName : ignoreMods) {
                String modStr = castStr.execute(moduleName);
                if (modStr == null) {
                    continue;
                }
                String fileNameSeparator = env.getFileNameSeparator();
                modStr = modStr.replace(".", fileNameSeparator);
                String modFile = modStr + ".py";
                String modInit = modStr + fileNameSeparator + "__init__.py";
                filter.sourceIs((src) -> {
                    String path = src.getPath();
                    return path != null && !(path.endsWith(modFile) || path.endsWith(modInit));
                });
            }

            Object[] ignoreDirs = toArray.execute(getStore.execute(ignoredirs));
            for (Object dir : ignoreDirs) {
                String dirStr = castStr.execute(dir);
                if (dirStr == null) {
                    continue;
                }
                String absDir;
                try {
                    absDir = env.getPublicTruffleFile(dirStr).getCanonicalFile().getPath();
                } catch (SecurityException | IOException e) {
                    continue;
                }
                filter.sourceIs((src) -> {
                    String path = src.getPath();
                    return path != null && !path.equals(absDir);
                });
            }

            if (currentTracker instanceof CoverageTracker) {
                tracker = (CoverageTracker) currentTracker;
            } else {
                Map<String, InstrumentInfo> instruments = env.getInstruments();
                InstrumentInfo instrumentInfo = instruments.get(CoverageInstrument.ID);
                if (instrumentInfo != null) {
                    tracker = env.lookup(instrumentInfo, CoverageTracker.class);
                    if (tracker != null) {
                        writeNode.execute(mod, TRACKER, tracker);
                    }
                }
            }
            if (tracker == null) {
                throw raise(PythonBuiltinClassType.NotImplementedError, "coverage tracker not available");
            }
            writeNode.execute(mod, TRACK_FUNCS, countfuncs);

            tracker.start(new CoverageTracker.Config(filter.build(), lib.isTrueWithState(count, PArguments.getThreadState(frame))));

            return PNone.NONE;
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class Stop extends PythonUnaryBuiltinNode {
        @Specialization
        PNone start(PythonModule mod,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object currentTracker = readNode.execute(mod, TRACKER);
            if (currentTracker instanceof CoverageTracker) {
                ((CoverageTracker) currentTracker).close();
                return PNone.NONE;
            } else {
                throw raise(PythonBuiltinClassType.TypeError, "coverage tracker not running");
            }
        }
    }

    @Builtin(name = "results", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class Results extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple start(VirtualFrame frame, PythonModule mod,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            Object currentTracker = readNode.execute(mod, TRACKER);
            boolean countFuncs = lib.isTrue(readNode.execute(mod, TRACK_FUNCS));

            CoverageTracker tracker;
            if (currentTracker instanceof CoverageTracker) {
                tracker = (CoverageTracker) currentTracker;
            } else {
                throw raise(PythonBuiltinClassType.TypeError, "coverage tracker not running");
            }
            SourceCoverage[] coverage = tracker.getCoverage();
            // callers -> not supported
            // calledfuncs -> {(filename, modulename, funcname) => 1}
            // counts -> {(filename, lineno) => count}
            PDict calledFuncs = factory().createDict();
            PDict counts = factory().createDict();
            for (SourceCoverage c : coverage) {
                String filename = c.getSource().getPath();
                if (filename == null) {
                    continue;
                }

                String modName;
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(filename);
                String baseName = file.getName();
                if (baseName != null) {
                    if (baseName.endsWith("__init__.py")) {
                        modName = file.getParent().getName();
                    } else {
                        modName = baseName.replaceFirst("\\.py$", "");
                    }
                } else {
                    continue;
                }

                for (RootCoverage r : c.getRoots()) {
                    String name = r.getName();
                    if (name == null) {
                        continue;
                    }
                    if (countFuncs) {
                        PTuple tp = factory().createTuple(new Object[] {filename, modName, name});
                        setItemNode.execute(frame, calledFuncs, tp, 1);
                    }
                    for (SectionCoverage s : r.getSectionCoverage()) {
                        if (s.getSourceSection().hasLines()) {
                            int startLine = s.getSourceSection().getStartLine();
                            int endLine = startLine; // s.getSourceSection().getEndLine();
                            long cnt = s.getCount();
                            if (cnt < 0) {
                                cnt = 1;
                            }
                            for (int i = startLine; i <= endLine; i++) {
                                PTuple ctp = factory().createTuple(new Object[] {filename, i});
                                setItemNode.execute(frame, counts, ctp, cnt);
                            }
                        }
                    }
                }
            }
            return factory().createTuple(new Object[] { counts, calledFuncs });
        }
    }
}
