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
package com.oracle.graal.python.benchmarks.parser;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.benchmarks.interop.BenchOutputFormat;
import com.oracle.graal.python.benchmarks.interop.BenchRunner;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.parser.PythonSSTNodeFactory;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = BenchRunner.WARMUP_ITERATIONS)
@Measurement(iterations = BenchRunner.MEASUREMENT_ITERATIONS, time = 1)
public class ParserBenchRunner {

    public static final int MEASUREMENT_ITERATIONS = 5;
    public static final int WARMUP_ITERATIONS = 0;
    public static final int NUMBER_OF_CYCLES = 1;

    // parameters of benchmark
    @Param({"false"}) public boolean recursion;
    @Param({""}) public String files;
    @Param({""}) public String excludedFiles;
    @Param({"" + NUMBER_OF_CYCLES}) public int parsingCycles;

    protected final PythonContext pyContext;
    protected final PythonParserImpl parser;
    protected final PythonCore core;
    private List<Source> sources;

    public ParserBenchRunner() {
        Context context = Context.newBuilder().engine(Engine.newBuilder().build()).allowAllAccess(true).build();
        context.initialize("python");
        context.enter();

        this.pyContext = PythonLanguage.getContext();
        this.parser = (PythonParserImpl) pyContext.getCore().getParser();
        this.core = pyContext.getCore();
    }

    public static void main(String[] args) throws RunnerException {
        assert args.length > 1;
        final HashMap<String, String> rest = new HashMap<>();
        String benchName = args[0];
        String benchClass = args[1];
        int iter = MEASUREMENT_ITERATIONS;
        int warmup = WARMUP_ITERATIONS;
        int numberOfCycles = NUMBER_OF_CYCLES;
        boolean folderRecursion = false;
        String files = "";
        String excludedFiles = "";
        boolean excluded = false;
        for (int i = 2; i < args.length; i++) {
            final String arg = args[i];
            if (arg.contentEquals("-i")) {
                i++;
                iter = Integer.valueOf(args[i]);
            } else if (arg.contentEquals("-w")) {
                i++;
                warmup = Integer.valueOf(args[i]);
            } else if (arg.contentEquals("-n")) {
                i++;
                numberOfCycles = Integer.valueOf(args[i]);
            } else if (arg.contentEquals("-r")) {
                folderRecursion = true;
            } else if (arg.contentEquals("-e")) {
                excluded = true;
            } else {
                if (excluded) {
                    excludedFiles = excludedFiles + arg + ",";
                } else {
                    files = files + arg + ",";
                }
            }
        }
        if (!files.isEmpty()) {
            files = files.substring(0, files.length() - 1);
        }
        if (!excludedFiles.isEmpty()) {
            excludedFiles = excludedFiles.substring(0, excludedFiles.length() - 1);
        }
        ChainedOptionsBuilder options = new OptionsBuilder();
        options = options.include(benchClass);
        options = options.warmupIterations(warmup).measurementIterations(iter);
        options = options.param("parsingCycles", "" + numberOfCycles);
        options = options.param("recursion", folderRecursion ? "true" : "false");
        options = options.param("files", files);
        options = options.param("excludedFiles", excludedFiles);
        Options opt = options.forks(1).build();
        new Runner(opt, new BenchOutputFormat(System.out, VerboseMode.SILENT, benchName)).run();
    }

    public List<Source> getSources() {
        if (sources == null) {
            sources = createSources();
        }
        return sources;
    }

    private List<Source> createSources() {
        String[] paths = files.split(",");
        List<Source> result = new ArrayList<>();
        Set<String> excludedPaths = new HashSet<>();
        if (!excludedFiles.isEmpty()) {
            excludedPaths.addAll(Arrays.asList(excludedFiles.split(",")));
        }
        File file;
        PythonFileFilter filter = new PythonFileFilter(recursion, excludedPaths);
        PythonContext pyContext = PythonLanguage.getContext();
        for (String path : paths) {
            file = new File(path);
            if (file.isDirectory() || filter.accept(file)) {
                processFile(result, file, filter, pyContext);
            }
        }
        return result;
    }

    public List<PythonParserImpl.CacheItem> getAntlrResults(List<Source> sources) {
        List<PythonParserImpl.CacheItem> result = new ArrayList<>(sources.size());

        for (Source source : sources) {
            try {
                PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(core, source, parser);
                PythonParserImpl.CacheItem cachedItem = parser.parseWithANTLR(PythonParser.ParserMode.File, core, sstFactory, source, null, null);
                result.add(cachedItem.copy());
            } catch (RuntimeException e) {
                // do nothing
            }
        }
        return result;
    }

    private static void processFile(List<Source> target, File file, PythonFileFilter fileFilter, PythonContext context) {
        if (file.isDirectory()) {
            for (File f : file.listFiles(fileFilter)) {
                processFile(target, f, fileFilter, context);
            }
        } else {
            try {
                TruffleFile tfile = context.getEnv().getInternalTruffleFile(file.getAbsolutePath());
                target.add(PythonLanguage.newSource(context, tfile, file.getName()));
            } catch (IOException ex) {
                Logger.getLogger(ParserBenchRunner.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }

    private static class PythonFileFilter implements FileFilter {
        private final boolean includeDirectory;
        private final Set<String> excludedPaths;

        public PythonFileFilter(boolean includeDirectory, Set<String> excludedPaths) {
            this.includeDirectory = includeDirectory;
            this.excludedPaths = excludedPaths;
        }

        @Override
        public boolean accept(File file) {
            if (file.isDirectory()) {
                return includeDirectory && !excludedPaths.contains(file.getPath());
            }
            return file.getName().endsWith(".py") && !excludedPaths.contains(file.getPath());
        }
    }
}
