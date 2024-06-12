/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.advanced;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import com.sun.management.HotSpotDiagnosticMXBean;

public class LeakTest extends AbstractLanguageLauncher {
    public static void main(String[] args) {
        String languageId = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--lang")) {
                languageId = args[i + 1];
                break;
            }
        }
        new LeakTest(languageId).launch(args);
    }

    LeakTest(String languageId) {
        super();
        if (languageId == null) {
            printHelp(null);
            System.exit(255);
        }
        this.languageId = languageId;
    }

    // sharedEngine is an instance variable explicitly so we definitely keep the ASTs alive. This is
    // to ensure that we actually test that even when the engine is still alive, as the Context is
    // closed, no objects should still be reachable
    private Engine engine;

    private boolean sharedEngine = false;
    private boolean keepDump = false;
    private int repeatAndCheckSize = -1;
    private boolean nullStdout = false;
    private String languageId;
    private String code;
    private List<String> forbiddenClasses = new ArrayList<>();

    private static final int REPEAT_AND_CHECK_BASLINE_ITERATION = 32;

    private final class SystemExit extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SystemExit() {
            super(null, null);
        }

        @Override
        public synchronized Throwable getCause() {
            dumpAndAnalyze();
            System.exit(0);
            return null;
        }

        private void dumpAndAnalyze() {
            if (sharedEngine && engine == null) {
                throw new AssertionError("the engine is no longer alive!");
            }

            MBeanServer server = doFullGC();
            String threadDump = getThreadDump();
            Path dumpFile = dumpHeap(server, keepDump);
            boolean fail = checkForLeaks(dumpFile);
            if (fail) {
                System.err.print(threadDump);
                System.exit(255);
            } else {
                System.exit(0);
            }
        }

        private String getThreadDump() {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            ThreadInfo[] threads = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 100);
            final String line = "=====================================\n";
            StringBuilder sb = new StringBuilder(line);
            for (ThreadInfo thread : threads) {
                if (thread != null) {
                    sb.append("-------\n");
                    sb.append(thread.getThreadName()).append('\n');
                    sb.append("Thread state:").append(thread.getThreadState()).append('\n');
                    for (StackTraceElement element : thread.getStackTrace()) {
                        sb.append("    ").append(element).append('\n');
                    }
                }
            }
            return sb.append(line).toString();
        }

        private boolean checkForLeaks(Path dumpFile) {
            boolean fail = false;
            try {
                Heap heap = HeapFactory.createHeap(dumpFile.toFile());
                for (String fqn : forbiddenClasses) {
                    List<String> errors = new ArrayList<>();
                    JavaClass cls = heap.getJavaClassByName(fqn);
                    if (cls == null) {
                        System.err.println("No such class: " + fqn);
                        fail = true;
                        continue;
                    }
                    int cnt = getCntAndErrors(cls, errors);
                    for (Object subCls : cls.getSubClasses()) {
                        cnt += getCntAndErrors((JavaClass) subCls, errors);
                    }
                    if (cnt > 0) {
                        fail = true;
                        System.err.println("More instances of " + fqn + " than expected: " + cnt);
                        for (String e : errors) {
                            System.err.println(e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return fail;
        }

        private int getCntAndErrors(JavaClass cls, List<String> errors) {
            int cnt = cls.getInstancesCount();
            if (cnt > 0) {
                boolean realLeak = false;
                for (Object i : cls.getInstances()) {
                    Instance inst = (Instance) i;
                    if (inst.isGCRoot() || inst.getNearestGCRootPointer() != null) {
                        realLeak = true;
                        break;
                    }
                }
                if (!realLeak) {
                    return 0;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(cls.getName()).append(" ").append(cnt).append(" instance(s)");
                errors.add(sb.toString());
            }
            return cnt;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }
    }

    private static MBeanServer doFullGC() {
        // do this a few times to dump a small heap if we can
        MBeanServer server = null;
        for (int i = 0; i < 10; i++) {
            System.gc();
            Runtime.getRuntime().freeMemory();
            server = ManagementFactory.getPlatformMBeanServer();
            try {
                ObjectName objectName = new ObjectName("com.sun.management:type=DiagnosticCommand");
                server.invoke(objectName, "gcRun", new Object[]{null}, new String[]{String[].class.getName()});
            } catch (MalformedObjectNameException | InstanceNotFoundException | ReflectionException | MBeanException e) {
                throw new RuntimeException(e);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e1) {
                // do nothing
            }
        }
        return server;
    }

    private static Path dumpHeap(MBeanServer server, boolean keepDump) {
        Path dumpFile = null;
        try {
            Path p = Files.createTempDirectory("leakTest");
            if (!keepDump) {
                p.toFile().deleteOnExit();
            }
            dumpFile = p.resolve("heapdump.hprof");
            if (!keepDump) {
                dumpFile.toFile().deleteOnExit();
            } else {
                System.out.println("Dump file: " + dumpFile.toString());
            }
            HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(server,
                            "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
            mxBean.dumpHeap(dumpFile.toString(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dumpFile;
    }

    private static long getJavaHeapSize(boolean createHeapDump) {
        MBeanServer server = doFullGC();
        if (createHeapDump) {
            dumpHeap(server, true);
        }
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            if (arg.equals("--shared-engine")) {
                sharedEngine = true;
            } else if (arg.equals("--lang")) {
                // already parsed
                i++;
            } else if (arg.equals("--keep-dump")) {
                keepDump = true;
            } else if (arg.equals("--code")) {
                code = arguments.get(++i);
            } else if (arg.equals("--forbidden-class")) {
                forbiddenClasses.add(arguments.get(++i));
            } else if (arg.equals("--repeat-and-check-size")) {
                repeatAndCheckSize = Integer.parseInt(arguments.get(++i));
                if (repeatAndCheckSize <= REPEAT_AND_CHECK_BASLINE_ITERATION) {
                    System.err.printf("--repeat-and-check-size must be at least %d\n", REPEAT_AND_CHECK_BASLINE_ITERATION);
                    System.exit(1);
                }
            } else if (arg.equals("--null-stdout")) {
                nullStdout = true;
            } else {
                unrecognized.add(arg);
            }
        }
        unrecognized.add("--experimental-options");
        return unrecognized;
    }

    @Override
    protected void launch(Builder contextBuilder) {
        if (sharedEngine) {
            engine = Engine.newBuilder().build();
            contextBuilder.engine(engine);
        }
        contextBuilder.allowExperimentalOptions(true).allowAllAccess(true);
        if (nullStdout) {
            contextBuilder.out(OutputStream.nullOutputStream());
        }

        try (Context c = contextBuilder.build()) {
            try {
                c.eval(getLanguageId(), code);
            } catch (PolyglotException e) {
                handleException(e);
            }
        }

        if (repeatAndCheckSize > 0) {
            long initialSize = -1;
            for (int i = 0; i < repeatAndCheckSize; i++) {
                if (i == REPEAT_AND_CHECK_BASLINE_ITERATION) {
                    // Give the system some time to stabilize, fill caches, etc.
                    initialSize = getJavaHeapSize(keepDump);
                    System.out.printf("Baseline heap size: %,d\n", initialSize);
                }
                try (Context c = contextBuilder.build()) {
                    try {
                        c.eval(getLanguageId(), code);
                    } catch (PolyglotException e) {
                        handleException(e);
                    }
                }
            }
            // the check at the end will make a dump anyway, so no createHeapDump flag here
            long currentSize = getJavaHeapSize(false);
            System.out.printf("Heap size after all repetitions: %,d\n", currentSize);
            if (currentSize > initialSize * 1.1) {
                System.err.printf("Heap size grew too much after repeated context creations and invocations. From %,d bytes to %,d bytes.\n", initialSize, currentSize);
                System.exit(255);
            }
        }

        throw new SystemExit();
    }

    private void handleException(PolyglotException e) {
        if (e.isExit()) {
            if (e.getExitStatus() == 0) {
                throw new SystemExit();
            } else {
                exit(e.getExitStatus());
            }
        } else {
            e.printStackTrace();
            exit(255);
        }
    }

    @Override
    protected String getLanguageId() {
        return languageId;
    }

    @Override
    protected String[] getDefaultLanguages() {
        return new String[]{languageId};
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println("--lang ID --code CODE --forbidden-class FQN [--forbidden-class FQN]* [--shared-engine] [--keep-dump] [POLYGLOT-OPTIONS]");
    }
}
