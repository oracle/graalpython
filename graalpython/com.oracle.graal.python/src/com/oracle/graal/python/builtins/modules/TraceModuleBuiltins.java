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
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
        @TruffleBoundary
        PNone doit(PythonModule mod, Object count, Object countfuncs, PSequence ignoremods, PSequence ignoredirs,
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
                String modStr;
                try {
                    modStr = castStr.execute(moduleName);
                } catch (CannotCastException e) {
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
                String dirStr;
                try {
                    dirStr = castStr.execute(dir);
                } catch (CannotCastException e) {
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

            tracker.start(new CoverageTracker.Config(filter.build(), lib.isTrue(count)));

            return PNone.NONE;
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class Stop extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone start(PythonModule mod,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object currentTracker = readNode.execute(mod, TRACKER);
            if (currentTracker instanceof CoverageTracker) {
                ((CoverageTracker) currentTracker).close();
                return PNone.NONE;
            } else {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.COVERAGE_TRACKER_NOT_RUNNING);
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
            boolean countFuncs = lib.isTrueWithState(readNode.execute(mod, TRACK_FUNCS), PArguments.getThreadState(frame));

            CoverageTracker tracker;
            if (currentTracker instanceof CoverageTracker) {
                tracker = (CoverageTracker) currentTracker;
            } else {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.COVERAGE_TRACKER_NOT_RUNNING);
            }
            SourceCoverage[] coverage = getCoverage(tracker);
            // callers -> not supported
            // calledfuncs -> {(filename, modulename, funcname) => 1}
            // counts -> {(filename, lineno) => count}
            PDict calledFuncs = factory().createDict();
            PDict counts = factory().createDict();
            for (SourceCoverage c : coverage) {
                String filename = getSourcePath(c);
                if (filename == null) {
                    continue;
                }

                String modName;
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(filename);
                String baseName = file.getName();
                if (baseName != null) {
                    modName = deriveModuleName(file, baseName);
                } else {
                    continue;
                }

                RootCoverage[] rootCoverage = getRootCoverage(c);
                for (RootCoverage r : rootCoverage) {
                    String name = getRootName(r);
                    if (name == null) {
                        continue;
                    }
                    if (countFuncs) {
                        PTuple tp = factory().createTuple(new Object[]{filename, modName, name});
                        setItemNode.execute(frame, calledFuncs, tp, 1);
                    }
                    SectionCoverage[] sectionCoverage = getSectionCoverage(r);
                    for (SectionCoverage s : sectionCoverage) {
                        if (hasLines(s)) {
                            int startLine = getStartLine(s);
                            long cnt = getCoverageCount(s);
                            if (cnt < 0) {
                                cnt = 1;
                            }
                            PTuple ctp = factory().createTuple(new Object[]{filename, startLine});
                            setItemNode.execute(frame, counts, ctp, cnt);
                        }
                    }
                }
            }
            return factory().createTuple(new Object[]{counts, calledFuncs});
        }

        @TruffleBoundary
        private static long getCoverageCount(SectionCoverage s) {
            return s.getCount();
        }

        @TruffleBoundary
        private static int getStartLine(SectionCoverage s) {
            return s.getSourceSection().getStartLine();
        }

        @TruffleBoundary
        private static boolean hasLines(SectionCoverage s) {
            return s.getSourceSection().hasLines();
        }

        @TruffleBoundary
        private static SectionCoverage[] getSectionCoverage(RootCoverage r) {
            return r.getSectionCoverage();
        }

        @TruffleBoundary
        private static String getRootName(RootCoverage r) {
            return r.getName();
        }

        @TruffleBoundary
        private static RootCoverage[] getRootCoverage(SourceCoverage c) {
            return c.getRoots();
        }

        @TruffleBoundary
        private static String deriveModuleName(TruffleFile file, String baseName) {
            if (baseName.endsWith("__init__.py")) {
                return file.getParent().getName();
            } else {
                return baseName.replaceFirst("\\.py$", "");
            }
        }

        @TruffleBoundary
        private static String getSourcePath(SourceCoverage c) {
            return c.getSource().getPath();
        }

        @TruffleBoundary
        private static SourceCoverage[] getCoverage(CoverageTracker tracker) {
            return tracker.getCoverage();
        }
    }
}
