package com.oracle.graal.python.test.advanced;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;

import com.oracle.truffle.api.strings.TruffleString;

/**
 * Standalone tool that can be run via {@code mx python-nodes-footprint heap_dump1 heap_dump2}.
 *
 * It creates a mapping between root nodes instances in two heap dumps according to their source
 * sections. The tool provides comparison of retained sizes of root nodes with the same source
 * sections. Additionally, it also compares builtins root nodes in the same way.
 *
 * It is recommended to run GraalPy with the following options when taking the heap dumps.
 *
 * <pre>
 *     --engine.Splitting=false --engine.OSR=false --python.BuiltinsInliningMaxCallerSize=0 --python.ForceInitializeSourceSections=true
 * </pre>
 */
public class NodeFootprintAnalyzer {
    public record RootInfoID(String rootName, String sourceName, int line) {
        @Override
        public String toString() {
            return String.format("%s:%s:%d", rootName, sourceName, line);
        }
    }

    public abstract static class RootInfo implements Comparable<RootInfo> {
        // Placeholder for duplicate entries
        public static RootInfo DUPLICATE = new RootInfo(null, null) {
            @Override
            public long totalSize() {
                return 0;
            }

            @Override
            public long nodesSize() {
                return 0;
            }

            @Override
            public long bytecodesSize() {
                return 0;
            }

            @Override
            public long sourcesSize() {
                return 0;
            }

            @Override
            public boolean hasSourceMap() {
                return false;
            }
        };
        protected final Instance instance;
        private final RootInfoID id;

        public RootInfo(Instance instance, RootInfoID id) {
            this.instance = instance;
            this.id = id;
        }

        /**
         * Returns some representative approximation of total size. It doesn't always have to be
         * "retained size" of the root node, some object sub-graphs appear as being hold on to be
         * another GC root. The calculation is specific to the root node implementation.
         */
        public long totalCombinedSize() {
            return nodesSize() + bytecodesSize() + sourcesSize();
        }

        public abstract long totalSize();

        public abstract long nodesSize();

        public abstract long bytecodesSize();

        public abstract long sourcesSize();

        public abstract boolean hasSourceMap();

        @Override
        public int compareTo(RootInfo o) {
            return Long.compare(totalSize(), o.totalSize());
        }

        public Instance instance() {
            return instance;
        }

        public RootInfoID id() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (RootInfo) obj;
            return Objects.equals(this.instance, that.instance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instance);
        }

        protected static long getRetainedSizeChecked(Instance instance) {
            return instance == null ? 0 : instance.getRetainedSize();
        }
    }

    public static final class RootInfoFactory {
        static final Map<String, Function<Instance, RootInfo>> JAVA_CLASS_FNQ_TO_FACTORY = Map.of(
                "com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode", PBytecodeRootInfo::create,
                "com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen", PBytecodeDSLRootInfo::create);/*,
                "com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode", BuiltinFunctionRootInfo::create);,*/

        final HashMap<JavaClass, Function<Instance, RootInfo>> factories = new HashMap<>();

        public RootInfoFactory(Heap heap) {
            JAVA_CLASS_FNQ_TO_FACTORY.forEach((fnq, factory) -> {
                JavaClass klass = heap.getJavaClassByName(fnq);
                if (klass == null) {
                    System.err.println("WARNING: could not load Java class: " + fnq);
                } else {
                    factories.put(klass, factory);
                }
            });
        }

        public RootInfo create(Instance x) {
            for (Entry<JavaClass, Function<Instance, RootInfo>> entry : factories.entrySet()) {
                if (isSubclass(entry.getKey(), x)) {
                    return entry.getValue().apply(x);
                }
            }
            return null;
        }
    }

    public static final class PBytecodeRootInfo extends RootInfo {
        private final ObjectArrayInstance adoptedNodes;
        private final Instance bytecode;
        private final Instance srcOffsetTable;
        private final Instance sourceMap;

        public PBytecodeRootInfo(Instance instance, RootInfoID id, ObjectArrayInstance adoptedNodes, Instance bytecode, Instance srcOffsetTable, Instance sourceMap) {
            super(instance, id);
            this.adoptedNodes = adoptedNodes;
            this.bytecode = bytecode;
            this.srcOffsetTable = srcOffsetTable;
            this.sourceMap = sourceMap;
        }

        public static RootInfo create(Instance instance) {
            Object adoptedNodesField = instance.getValueOfField("adoptedNodes");
            ObjectArrayInstance adoptedNodes = null;
            if (adoptedNodesField instanceof ObjectArrayInstance adoptedNodesArr) {
                adoptedNodes = adoptedNodesArr;
            }
            RootInfoID rootId = getBytecodeRootId(instance);

            Object coField = instance.getValueOfField("co");
            assert coField instanceof Instance : "PBytecodeRootNode#co: unexpected type " + coField;
            Instance coInstance = (Instance) coField;

            Object srcOffsetTableField = coInstance.getValueOfField("srcOffsetTable");
            assert srcOffsetTableField instanceof Instance : "PBytecodeRootNode#co#srcOffsetTable: unexpected type " + srcOffsetTableField;
            Instance srcOffsetTableInstance = (Instance) srcOffsetTableField;

            Object bytecodeField = instance.getValueOfField("bytecode");
            assert bytecodeField instanceof Instance : "PBytecodeRootNode#bytecode: unexpected type " + bytecodeField;
            Instance bytecodeInstance = (Instance) bytecodeField;

            Object sourceMapField = coInstance.getValueOfField("sourceMap");
            Instance sourceMapInstance = null;
            if (sourceMapField instanceof Instance) {
                sourceMapInstance = (Instance) sourceMapField;
            }

            return new PBytecodeRootInfo(instance, rootId, adoptedNodes, bytecodeInstance, srcOffsetTableInstance, sourceMapInstance);
        }

        @Override
        public long totalSize() {
            return getRetainedSizeChecked(instance);
        }

        @Override
        public long nodesSize() {
            return getRetainedSizeChecked(adoptedNodes);
        }

        @Override
        public long bytecodesSize() {
            return getRetainedSizeChecked(bytecode);
        }

        @Override
        public long sourcesSize() {
            if (hasSourceMap()) {
                return getRetainedSizeChecked(sourceMap);
            } else {
                return getRetainedSizeChecked(srcOffsetTable);
            }
        }

        @Override
        public boolean hasSourceMap() {
            return sourceMap != null;
        }
    }

    public static final class PBytecodeDSLRootInfo extends RootInfo {
        private final Instance bytecode;
        private final Instance sourceInfo;
        private final Instance bytecodes;
        private final Instance cachedNodes;

        public PBytecodeDSLRootInfo(Instance instance, RootInfoID id, Instance bytecode, Instance bytecodes, Instance sourceInfo, Instance cachedNodes) {
            super(instance, id);
            this.bytecode = bytecode;
            this.bytecodes = bytecodes;
            this.sourceInfo = sourceInfo;
            this.cachedNodes = cachedNodes;
        }

        public static RootInfo create(Instance instance) {
            Object bytecodeField = instance.getValueOfField("bytecode");
            assert bytecodeField instanceof Instance : "PBytecodeDSLRootNodeGen#bytecode: unexpected type " + bytecodeField;
            Instance bytecodeInstance = (Instance) bytecodeField;

            Object bytecodesField = bytecodeInstance.getValueOfField("bytecodes");
            assert bytecodesField instanceof Instance : "PBytecodeDSLRootNodeGen#bytecode#bytecodes: unexpected type " + bytecodesField;

            Object sourceInfoField = bytecodeInstance.getValueOfField("sourceInfo");
            assert sourceInfoField instanceof Instance : "PBytecodeDSLRootNodeGen#bytecode#sourceInfo: unexpected type " + sourceInfoField;

            Object cachedNodesField = bytecodeInstance.getValueOfField("cachedNodes_");
            Instance cachedNodesInstance = null;
            if (cachedNodesField instanceof Instance) {
                cachedNodesInstance = (Instance) cachedNodesField;
            }

            RootInfoID rootInfoID = getBytecodeDSLRootId(instance);
            return new PBytecodeDSLRootInfo(instance, rootInfoID, (Instance) bytecodeField, (Instance) bytecodesField, (Instance) sourceInfoField, cachedNodesInstance);
        }

        @Override
        public long totalSize() {
            return getRetainedSizeChecked(instance);
        }

        @Override
        public long nodesSize() {
            return getRetainedSizeChecked(cachedNodes);
        }

        @Override
        public long bytecodesSize() {
            return getRetainedSizeChecked(bytecodes);
        }

        @Override
        public long sourcesSize() {
            return getRetainedSizeChecked(sourceInfo);
        }

        @Override
        public boolean hasSourceMap() {
            return false;
        }
    }

    public static final class BuiltinFunctionRootInfo extends RootInfo {
        private final Instance body;

        public BuiltinFunctionRootInfo(Instance instance, RootInfoID id, Instance body) {
            super(instance, id);
            this.body = body;
        }

        public static RootInfo create(Instance instance) {
            Object bodyField = instance.getValueOfField("body");
            Instance body = null;
            if (bodyField instanceof Instance bodyFieldInstance) {
                body = bodyFieldInstance;
            }
            return new BuiltinFunctionRootInfo(instance, getBuiltinRootId(instance), body);
        }

        @Override
        public long totalSize() {
            return body == null ? 0 : body.getRetainedSize();
        }

        @Override
        public long nodesSize() {
            return 0;
        }

        @Override
        public long bytecodesSize() {
            return 0;
        }

        @Override
        public long sourcesSize() {
            return 0;
        }

        @Override
        public boolean hasSourceMap() {
            return false;
        }
    }

    public static final class AdoptedNodesPair implements Comparable<AdoptedNodesPair> {
        private final int index;
        private final Instance i1;
        private final Instance i2;

        public AdoptedNodesPair(int index, Instance i1, Instance i2) {
            this.index = index;
            this.i1 = i1;
            this.i2 = i2;
        }

        long retainedDiff() {
            long i1Size = i1 != null ? i1.getRetainedSize() : 0;
            long i2Size = i2 != null ? i2.getRetainedSize() : 0;
            return i1Size - i2Size;
        }

        long getI1InstanceId() {
            return i1 == null ? 0 : i1.getInstanceId();
        }

        long getI2InstanceId() {
            return i2 == null ? 0 : i2.getInstanceId();
        }

        String getClassName() {
            if (i1 != null) {
                return i1.getJavaClass().getName();
            } else if (i2 != null) {
                return i2.getJavaClass().getName();
            } else {
                return "<null>";
            }
        }

        @Override
        public int compareTo(AdoptedNodesPair o) {
            return Long.compare(abs(retainedDiff()), abs(o.retainedDiff()));
        }
    }

    public static final class RootsPair implements Comparable<RootsPair> {
        private final RootInfo rootInfoDSL;
        private final RootInfo rootInfoManual;
        private ArrayList<AdoptedNodesPair> adoptedNodes;

        public RootsPair(RootInfo rootInfoDSL, RootInfo rootInfoManual) {
            this.rootInfoDSL = rootInfoDSL;
            this.rootInfoManual = rootInfoManual;
        }

        long sizeDiff() {
            return rootInfoDSL.totalSize() - rootInfoManual.totalSize();
        }

        long nodesSizeDiff() {
            return rootInfoDSL.nodesSize() - rootInfoManual.nodesSize();
        }

        long bytecodesSizeDiff() {
            return rootInfoDSL.bytecodesSize() - rootInfoManual.bytecodesSize();
        }

        long sourcesSizeDiff() {
            return rootInfoDSL.sourcesSize() - rootInfoManual.sourcesSize();
        }

        @Override
        public int compareTo(RootsPair o) {
            return Long.compare(abs(sizeDiff()), abs(o.sizeDiff()));
        }

        @SuppressWarnings("unchecked")
        public ArrayList<AdoptedNodesPair> getAdoptedNodes() {
            if (adoptedNodes == null) {
                // NOTE: implemented only for PBytecodeRootInfo for now. Can be generalized to any
                // root that exposes something like getAdoptedNodes()
                if (rootInfoDSL instanceof PBytecodeRootInfo bci1 && rootInfoManual instanceof PBytecodeRootInfo bci2) {
                    if (bci1.adoptedNodes.getLength() != bci2.adoptedNodes.getLength()) {
                        System.out.printf("WARNING: '%s': not the same adoptedNodes array size\n", rootInfoDSL.id);
                    }
                    int size = min(bci1.adoptedNodes.getLength(), bci2.adoptedNodes.getLength());
                    adoptedNodes = new ArrayList<>(size);
                    List<Instance> values1 = bci1.adoptedNodes.getValues();
                    List<Instance> values2 = bci2.adoptedNodes.getValues();
                    for (int i = 0; i < size; i++) {
                        adoptedNodes.add(new AdoptedNodesPair(i, values1.get(i), values2.get(i)));
                    }
                    adoptedNodes.sort(Comparator.reverseOrder());
                } else if (rootInfoDSL instanceof BuiltinFunctionRootInfo builtin1 && rootInfoManual instanceof BuiltinFunctionRootInfo builtin2) {
                    adoptedNodes = new ArrayList<>();
                    adoptedNodes.add(new AdoptedNodesPair(0, builtin1.body, builtin2.body));
                }
            }
            return adoptedNodes;
        }

        public String adoptedNodesDiffTable() {
            if (getAdoptedNodes() == null || getAdoptedNodes().size() <= 1) {
                return "";
            }
            return String.format("     %10s %10s %15s %15s %s\n", "diff", "index", "id1", "id2", "class") +
                    getAdoptedNodes().stream().//
                            filter(x -> x.retainedDiff() != 0).//
                            sorted(Comparator.reverseOrder()).//
                            map(x -> String.format("     %10d %10d %15d %15d %s",
                            x.retainedDiff(), //
                            x.index, //
                            x.getI1InstanceId(), //
                            x.getI2InstanceId(), //
                            x.getClassName())).collect(Collectors.joining("\n")) +
                    "\n";
        }
    }

    public static void printHelp(PrintStream printStream) {
        printStream.println("Arguments: [--csv] [--no-headers] [--output-dir <directory>] heap-dump-bytecode-dsl heap-dump-manual");
        printStream.println("    --csv           Output .csv instead of .txt");
        printStream.println("    --no-headers    Don't include header in .csv file (also works for .txt version)");
        printStream.println("    --output-dir    Where to output the non-shortened files.");
        printStream.println("                    Creates 'nfa-output' directory. Default: '/tmp'");
        printStream.println("Note: Files are stored in your selected output directory (of in '/tmp' if you didn't");
        printStream.println("      specify any), in the 'nfa-output' subdirectory. Each invocation of the tool creates");
        printStream.println("      another subdirectory in the 'nfa-output' direcotry with ISO date and time set as");
        printStream.println("      its name. If your platform supports it, a link called 'latest' is created that");
        printStream.println("      will always point to the directory containing the results of the latest run.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].contains("help") || args.length > 6) {
            printHelp(System.out);
            return;
        }

        boolean getCsv = true; // .txt version output seems broken
        boolean noHeaders = false;
        String outDirModifiable = "/tmp";
        for (int i = 0; i < args.length - 2; i++) {
            if (args[i].equals("--csv")) {
                getCsv = true;
            } else if (args[i].equals("--no-headers") || args[i].equals("--no-header")) {
                noHeaders = true;
            } else if (args[i].equals("--output-dir")) {
                outDirModifiable = args[i + 1];
                i++;
            } else if (args[i].equals("--help")) {
                printHelp(System.out);
                return;
            }else {
                System.err.println(String.format("Invalid option: \"%s\"", args[i]));
                printHelp(System.err);
                return;
            }
        }

        String fileDSL = args[args.length - 2];
        String fileManual = args[args.length - 1];

        if (!Files.exists(Path.of(fileDSL))) {
            System.err.println(String.format("File \"%s\" is an invalid file name or insufficient permissions were provided.", fileDSL));
            return;
        }
        if (!Files.exists(Path.of(fileManual))) {
            System.err.println(String.format("File \"%s\" is an invalid file name or insufficient permissions were provided.", fileManual));
            return;
        }

        String runId = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        Path baseDir = Paths.get(outDirModifiable, "nfa-output");
        Path runDir = baseDir.resolve(runId);
        Files.createDirectories(runDir);

        Path latestLink = baseDir.resolve("latest");
        try {
            Files.deleteIfExists(latestLink);
            Files.createSymbolicLink(latestLink, runDir);
        } catch (UnsupportedOperationException e) {
            System.err.println("WARNING: symlinks not supported on this platform: " + e.getMessage());
        }

        System.out.println();
        System.out.println("====================");
        System.out.printf("processing %s...\n", fileDSL);
        Map<RootInfoID, RootInfo> result0 = processDump(fileDSL);

        System.out.println("====================");
        System.out.printf("processing %s...\n", fileManual);
        Map<RootInfoID, RootInfo> result1 = processDump(fileManual);

        HashMap<RootInfoID, RootsPair> joined = new HashMap<>();
        ArrayList<RootInfoID> notFound = new ArrayList<>();
        for (Entry<RootInfoID, RootInfo> e : result0.entrySet()) {
            RootInfo r2 = result1.get(e.getKey());
            if (r2 == null) {
                notFound.add(e.getKey());
            } else {
                joined.put(e.getKey(), new RootsPair(e.getValue(), r2));
                result1.remove(e.getKey());
            }
        }
        notFound.addAll(result1.keySet());

        System.out.println("====================");
        System.out.println("Root nodes with the highest (approximate) total size difference: ");
        String header;
        if (getCsv) {
            header = "diff,bytecodes_size_dsl,bytecodes_size_manual,nodes_size_dsl,nodes_size_manual,sources_size_dsl,sources_size_manual,source_map_manual,total_size_dsl,total_size_manual,id_dsl,id_manual,name\n";
        } else {
            header = String.format("      %10s %15s %15s %12s %12s %15s %15s %11s %11s %12s %12s %15s %15s %10s\n", "diff", "bytecodes_size1", "bytecodes_size2", "nodes_size1", "nodes_size2", "sources_size1", "sources_size2", "source_map1", "source_map2", "total_size1", "total_size2", "id1", "id2", "name");
        }
        String body;
        if (getCsv) {
            body = "%d,%d,%d,%d,%d,%d,%d,%b,%d,%d,%d,%d,%s";
        } else {
            body = "root: %10d %15d %15d %12d %12d %15d %15d %11b %11b %12d %12d %15d %15d %s\n";
        }
        System.out.printf(header);
        List<String> diffs = joined.values().stream().//
                filter(x -> x.sizeDiff() != 0).//
                sorted(Comparator.reverseOrder()).//
                map(x -> String.format(body,
                x.sizeDiff(), //
                x.rootInfoDSL.bytecodesSize(), //
                x.rootInfoManual.bytecodesSize(), //
                x.rootInfoDSL.nodesSize(), //
                x.rootInfoManual.nodesSize(), //
                x.rootInfoDSL.sourcesSize(), //
                x.rootInfoManual.sourcesSize(), //
                x.rootInfoManual.hasSourceMap(), //
                x.rootInfoDSL.totalSize(), //
                x.rootInfoManual.totalSize(), //
                x.rootInfoDSL.instance.getInstanceId(), //
                x.rootInfoManual.instance.getInstanceId(), //
                x.rootInfoDSL.id)).toList();
        diffs.stream().limit(20).forEach(System.out::println);
        Path rootsDiffFilePath = runDir.resolve(String.format("roots-diff.%s", getCsv ? "csv" : "txt"));
        if (!getCsv) {
            Files.writeString(rootsDiffFilePath, String.format("Files: %s vs %s\n", Arrays.stream(fileDSL.split("/")).toList().getLast(), Arrays.stream(fileManual.split("/")).toList().getLast()), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(rootsDiffFilePath, "Root nodes with the highest (approximate) total size difference:\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        if (!noHeaders) {
            Files.writeString(rootsDiffFilePath, header, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.write(rootsDiffFilePath, diffs, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("Full list was written to " + rootsDiffFilePath);

        // BYTECODE sizes
        System.out.println("====================");
        System.out.println("Root nodes with the highest (approximate) bytecode size difference: ");
        header = String.format("      %10s %15s %15s %11s %15s %15s %10s\n", "diff", "size1", "size2", "ratio", "id1", "id2", "name");
        if (getCsv) {
            header = "diff,size_dsl,size_manual,ratio,id_dsl,id_maunal,name\n";
        }
        String bytecodesBody;
        if (getCsv) {
            bytecodesBody = "%d,%d,%d,%d.%02d,%d,%d,%s";
        } else {
            bytecodesBody = "root: %10d %15d %15d %7d.%02d %15d %15d %s\n";
        }
        System.out.printf(header);
        diffs = joined.values().stream().//
                filter(x -> x.bytecodesSizeDiff() != 0).//
                sorted(Comparator.comparingLong(RootsPair::bytecodesSizeDiff).reversed()).//
                map(x -> String.format(bytecodesBody,
                x.bytecodesSizeDiff(), //
                x.rootInfoDSL.bytecodesSize(), //
                x.rootInfoManual.bytecodesSize(), //
                x.rootInfoDSL.bytecodesSize() * 100 / x.rootInfoManual.bytecodesSize(), //
                (x.rootInfoDSL.bytecodesSize() * 10000 / x.rootInfoManual.bytecodesSize()) % 100, //
                x.rootInfoDSL.instance.getInstanceId(), //
                x.rootInfoManual.instance.getInstanceId(), //
                x.rootInfoDSL.id)).toList();
        diffs.stream().limit(20).forEach(System.out::println);
        rootsDiffFilePath = runDir.resolve(String.format("roots-bytecodes-diff.%s", getCsv ? "csv" : "txt"));
        if (!getCsv) {
            Files.writeString(rootsDiffFilePath, String.format("Files: %s vs %s\n", Arrays.stream(fileDSL.split("/")).toList().getLast(), Arrays.stream(fileManual.split("/")).toList().getLast()), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(rootsDiffFilePath, "Root nodes with the highest (approximate) bytecode size difference:\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        if (!noHeaders) {
            Files.writeString(rootsDiffFilePath, header, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.write(rootsDiffFilePath, diffs, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("Full list was written to " + rootsDiffFilePath);

        // NODE sizes
        System.out.println("====================");
        System.out.println("Root nodes with the highest (approximate) nodes size difference: ");
        String nodesBody;
        if (getCsv) {
            header = "diff,size_dsl,size_manual,ratio,id_dsl,id_manual,name\n";
        }
        else {
            header = String.format("      %10s %15s %15s %11s %15s %15s %10s\n", "diff", "size1", "size2", "ratio", "id1", "id2", "name");
        }
        if (getCsv) {
            nodesBody = "%d,%d,%d,%d.%02d,%d,%d,%s";
        } else {
            nodesBody = "root: %10d %15d %15d %7d.%02d %15d %15d %s\n";
        }
        System.out.printf(header);
        diffs = joined.values().stream().//
                filter(x -> x.nodesSizeDiff() != 0).//
                sorted(Comparator.comparingLong(RootsPair::nodesSizeDiff).reversed()).//
                map(x -> String.format(nodesBody,
                x.nodesSizeDiff(), //
                x.rootInfoDSL.nodesSize(), //
                x.rootInfoManual.nodesSize(), //
                x.rootInfoDSL.nodesSize() * 100 / x.rootInfoManual.nodesSize(), //
                (x.rootInfoDSL.nodesSize() * 10000 / x.rootInfoManual.nodesSize()) % 100, //
                x.rootInfoDSL.instance.getInstanceId(), //
                x.rootInfoManual.instance.getInstanceId(), //
                x.rootInfoDSL.id)).toList();
        diffs.stream().limit(20).forEach(System.out::println);
        rootsDiffFilePath = runDir.resolve(String.format("roots-nodes-diff.%s", getCsv ? "csv" : "txt"));
        if (!getCsv) {
            Files.writeString(rootsDiffFilePath, String.format("Files: %s vs %s\n", Arrays.stream(fileDSL.split("/")).toList().getLast(), Arrays.stream(fileManual.split("/")).toList().getLast()), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(rootsDiffFilePath, "Root nodes with the highest (approximate) nodes size difference:\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        if (!noHeaders) {
            Files.writeString(rootsDiffFilePath, header, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.write(rootsDiffFilePath, diffs, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("Full list was written to " + rootsDiffFilePath);

        // SOURCE size
        System.out.println("====================");
        System.out.println("Root nodes with the highest (approximate) sources size difference: ");
        header = String.format("      %10s %15s %15s %11s %11s %11s %15s %15s %10s\n", "diff", "size1", "size2", "ratio", "source_map1", "source_map2", "id1", "id2", "name");
        if (getCsv) {
            header = "diff,size_dsl,size_manual,ratio,source_map_manual,id_dsl,id_manual,name\n";
        }
        String sourcesBody;
        if (getCsv) {
            sourcesBody = "%d,%d,%d,%d.%02d,%b,%d,%d,%s";
        } else {
            sourcesBody = "root: %10d %15d %15d %7d.%02d %5b %5b %15d %15d %s\n";
        }
        System.out.printf(header);
        diffs = joined.values().stream().//
                filter(x -> x.sourcesSizeDiff() != 0).//
                sorted(Comparator.comparingLong(RootsPair::sourcesSizeDiff).reversed()).//
                map(x -> String.format(sourcesBody,
                x.sourcesSizeDiff(), //
                x.rootInfoDSL.sourcesSize(), //
                x.rootInfoManual.sourcesSize(), //
                x.rootInfoDSL.sourcesSize() * 100 / x.rootInfoManual.sourcesSize(), //
                (x.rootInfoDSL.sourcesSize() * 10000 / x.rootInfoManual.sourcesSize()) % 100, //
                x.rootInfoManual.hasSourceMap(), //
                x.rootInfoDSL.instance.getInstanceId(), //
                x.rootInfoManual.instance.getInstanceId(), //
                x.rootInfoDSL.id)).toList();
        diffs.stream().limit(20).forEach(System.out::println);
        rootsDiffFilePath = runDir.resolve(String.format("roots-sources-diff.%s", getCsv ? "csv" : "txt"));
        if (!getCsv) {
            Files.writeString(rootsDiffFilePath, String.format("Files: %s vs %s\n", Arrays.stream(fileDSL.split("/")).toList().getLast(), Arrays.stream(fileManual.split("/")).toList().getLast()), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(rootsDiffFilePath, "Root nodes with the highest (approximate) sources size difference:\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        if (!noHeaders) {
            Files.writeString(rootsDiffFilePath, header, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.write(rootsDiffFilePath, diffs, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("Full list was written to " + rootsDiffFilePath);



        if (!notFound.isEmpty()) {
            final String onlyOnceRootsFile = "/tmp/only-once-roots.txt";
            Files.write(Paths.get(onlyOnceRootsFile), notFound.stream().map(RootInfoID::toString).toList());
            System.out.println("WARNING: there were some roots nodes that were found in only one of the dumps.");
            System.out.println("List of the names of those roots was written to " + onlyOnceRootsFile);
        }

        System.out.println();
        System.out.printf("Total retained size: %15s %15s %15s\n", "size1", "size2", "diff");
        long totalDiff = joined.values().stream().mapToLong(RootsPair::sizeDiff).sum();
        long size1 = joined.values().stream().mapToLong(x -> x.rootInfoDSL.totalSize()).sum();
        long size2 = joined.values().stream().mapToLong(x -> x.rootInfoManual.totalSize()).sum();
        System.out.printf("                     %,15d %,15d %,15d (%d%%)\n", size1, size2, totalDiff, round(totalDiff / ((double) size2 / 100)));

        System.out.println();
        System.out.printf("Bytecodes retained size: %15s %15s %15s\n", "size1", "size2", "diff");
        totalDiff = joined.values().stream().mapToLong(RootsPair::bytecodesSizeDiff).sum();
        size1 = joined.values().stream().mapToLong(x -> x.rootInfoDSL.bytecodesSize()).sum();
        size2 = joined.values().stream().mapToLong(x -> x.rootInfoManual.bytecodesSize()).sum();
        System.out.printf("                     %,15d %,15d %,15d (%d%%)\n", size1, size2, totalDiff, round(totalDiff / ((double) size2 / 100)));

        System.out.println();
        System.out.printf("Nodes retained size: %15s %15s %15s\n", "size1", "size2", "diff");
        totalDiff = joined.values().stream().mapToLong(RootsPair::nodesSizeDiff).sum();
        size1 = joined.values().stream().mapToLong(x -> x.rootInfoDSL.nodesSize()).sum();
        size2 = joined.values().stream().mapToLong(x -> x.rootInfoManual.nodesSize()).sum();
        System.out.printf("                     %,15d %,15d %,15d (%d%%)\n", size1, size2, totalDiff, round(totalDiff / ((double) size2 / 100)));

        System.out.println();
        System.out.printf("Sources retained size: %15s %15s %15s\n", "size1", "size2", "diff");
        totalDiff = joined.values().stream().mapToLong(RootsPair::sourcesSizeDiff).sum();
        size1 = joined.values().stream().mapToLong(x -> x.rootInfoDSL.sourcesSize()).sum();
        size2 = joined.values().stream().mapToLong(x -> x.rootInfoManual.sourcesSize()).sum();
        System.out.printf("                     %,15d %,15d %,15d (%d%%)\n", size1, size2, totalDiff, round(totalDiff / ((double) size2 / 100)));

        System.out.println();
        System.out.println("To explore individual objects in VisualVM use OQL query: `[heap.findObject(ID)]`");
    }

    @SuppressWarnings("unchecked")
    private static Map<RootInfoID, RootInfo> processDump(String dumpFile) throws IOException {
        Heap heap = HeapFactory.createHeap(new File(dumpFile));
        Iterator<Instance> instancesIt = heap.getAllInstancesIterator();

        RootInfoFactory infoFactory = new RootInfoFactory(heap);
        Map<RootInfoID, RootInfo> roots = StreamSupport.stream(Spliterators.spliteratorUnknownSize(instancesIt, Spliterator.ORDERED), false).//
                map(infoFactory::create).//
                filter(Objects::nonNull).//
                collect(Collectors.toMap(RootInfo::id, x -> x, (a, b) -> RootInfo.DUPLICATE));
        List<RootInfoID> duplicates = roots.entrySet().stream().filter(x -> x.getValue() == RootInfo.DUPLICATE).map(Entry::getKey).toList();
        duplicates.forEach(roots::remove);
        if (!duplicates.isEmpty()) {
            final String duplicatedRootsFile = "/tmp/duplicated-roots.text";
            Files.write(Paths.get(duplicatedRootsFile), duplicates.stream().map(RootInfoID::toString).toList());
            System.out.println("WARNING: there were some roots nodes with duplicated names.");
            System.out.println("List of the names of those roots was written to " + duplicatedRootsFile);
        }

        Path dumpFileName = Paths.get(dumpFile).getFileName();

        generateOQL(roots.values().stream().map(RootInfo::instance), dumpFileName, "ast", "selects all AST objects");

        String rootsCsv = "id,class,name,nodes_rsize\n" + //
                roots.values().stream().sorted().//
                        map(NodeFootprintAnalyzer::asCSVLine).//
                        collect(Collectors.joining("\n")) +
                "\n";
        final String rootsFile = String.format("/tmp/%s-roots.csv", dumpFileName);
        Files.writeString(Paths.get(rootsFile), rootsCsv);
        System.out.println("Database with roots was written to " + rootsFile);

        return roots;
    }

    private static String asCSVLine(RootInfo info) {
        return String.format("%d,%s,%s,%d", info.instance.getInstanceId(), info.instance.getJavaClass().getName(), info.id(), info.totalSize());
    }

    private static void generateOQL(Stream<Instance> objects, Path dumpFileName, String id, String description) throws IOException {
        String oqlObjects = objects.map(x -> Long.toString(x.getInstanceId())).//
                map(x -> String.format("heap.findObject(%s)", x)).//
                collect(Collectors.joining(", \n"));
        String oql = String.format("""
                        [
                            %s
                        ];
                        """, oqlObjects);
        final String oqlFile = String.format("/tmp/%s-%s.oql", dumpFileName, id);
        Files.writeString(Paths.get(oqlFile), oql);
        System.out.printf("OQL script that %s was written to %s\n", description, oqlFile);
    }

    // Heap object helper methods:

    private static RootInfoID getBuiltinRootId(Instance root) {
        // name field (j.l.String)
        Object nameFieldValue = root.getValueOfField("name");
        if (!(nameFieldValue instanceof Instance fieldValueInstance)) {
            throw new IllegalStateException("Builtin does not have name field: " + root);
        }
        String name;
        if (fieldValueInstance.getJavaClass().getName().equals(String.class.getName())) {
            name = getJavaStringContents(fieldValueInstance);
        } else if (fieldValueInstance.getJavaClass().getName().equals(TruffleString.class.getName())) {
            name = getTruffleStringContents(fieldValueInstance);
        } else {
            throw new IllegalStateException("Builtin name field is not String or TruffleString: " + root);
        }
        // source will be the name of root concatenated with the builtin node factory class name
        // index will be -1
        String sourceInfo = root.getJavaClass().getName();
        if (root.getJavaClass().getName().equals("com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode")) {
            Object bodyFieldValue = root.getValueOfField("factory");
            if (bodyFieldValue instanceof Instance bodyFieldInstance) {
                sourceInfo += ":" + bodyFieldInstance.getJavaClass().getName();
            }
        }
        return new RootInfoID(name, sourceInfo, -1);
    }

    private static RootInfoID getBytecodeRootId(Instance root) {
        String name = getTruffleStringContents(getFieldInstance(root, "name"));
        return createRootInfoWithSourceSection(root, name);
    }

    private static RootInfoID createRootInfoWithSourceSection(Instance root, String name) {
        // Extract source object:
        String sourceName = getSourceName(root.getValueOfField("source"));
        Object sectionFieldValue = root.getValueOfField("sourceSection");
        if (sourceName == null && sectionFieldValue instanceof Instance sectionInstance) {
            Object sourceValue = sectionInstance.getValueOfField("source");
            sourceName = getSourceName(sourceValue);
        }
        if (sourceName == null) {
            System.out.println("WARNING: could not extract source name for " + root + " with name '" + name + "'");
        }

        Object co = root.getValueOfField("co");

        // Extract index in the source
        int index = -1;
        if (co instanceof Instance coInstance) {
            Object startLineValue = coInstance.getValueOfField("startLine");
            if (startLineValue instanceof Integer startLineAsInt && startLineAsInt != 0) {
                index = startLineAsInt;
            }
        }

        return new RootInfoID(name, sourceName, index);
    }

    private static RootInfoID createBytecodeDSLRootInfoWithSourceSection(Instance root, String name) {
        Object bytecode = getFieldInstance(root, "bytecode");
        String sourceName = null;
        if (bytecode instanceof Instance bytecodeInstance) {
            if (bytecodeInstance.getValueOfField("sources") instanceof Instance sources) {
                if (sources.getValueOfField("elementData") instanceof ObjectArrayInstance sourceElements) {
                    for (Object o : sourceElements.getValues()) {
                        if (o != null) {
                            sourceName = getSourceName(o);
                            break;
                        }
                    }
                }
            }
        }

        Object co = getFieldInstance(root, "co");

        int index = -1;
        if (co instanceof Instance coInstance) {
            Object startLineValue = coInstance.getValueOfField("startLine");
            if (startLineValue instanceof Integer startLineAsInt && startLineAsInt != 0) {
                index = startLineAsInt;
            }
        }

        if (sourceName == null) {
            System.out.println("WARNING: could not extract source name for " + root + " with name '" + name + "'");
        }

        return new RootInfoID(name, sourceName, index);
    }

    private static RootInfoID getBytecodeDSLRootId(Instance root) {
        // Name field (TruffleString)
        Instance coFieldValue = getFieldInstance(root, "co");
        String name = getTruffleStringContents(getFieldInstance(coFieldValue, "name"));

        // Extract source object:
        return createBytecodeDSLRootInfoWithSourceSection(root, name);
    }

    private static String getSourceName(Object sourceFieldValue) {
        if (sourceFieldValue instanceof Instance sourceInstance) {
            Object keyFieldValue = sourceInstance.getValueOfField("key");
            if (keyFieldValue instanceof Instance keyInstance) {
                Object pathFieldValue = keyInstance.getValueOfField("path");
                if (pathFieldValue instanceof Instance pathInstance) {
                    String path = getJavaStringContents(pathInstance);
                    if (path != null) {
                        return Paths.get(path).getFileName().toString();
                    }
                }

                Object nameFieldValue = keyInstance.getValueOfField("name");
                if (nameFieldValue instanceof Instance nameInstance) {
                    return getJavaStringContents(nameInstance);
                }
            }
        }
        return null;
    }

    private static String getTruffleStringContents(Instance tsInstance) {
        if (tsInstance == null) {
            return "null";
        }
        assert tsInstance.getJavaClass().getName().equals(TruffleString.class.getName());
        Object dataObj = tsInstance.getValueOfField("data");
        if (!(dataObj instanceof PrimitiveArrayInstance dataArr)) {
            throw new IllegalStateException("Cannot extract TruffleString contents");
        }
        return byteArrayToString(dataArr);
    }

    private static String getJavaStringContents(Instance stringInstance) {
        Object valueFieldValue = stringInstance.getValueOfField("value");
        if (valueFieldValue instanceof PrimitiveArrayInstance arr) {
            return byteArrayToString(arr);
        }
        System.out.println("WARNING: could not extract java.lang.String contents");
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String byteArrayToString(PrimitiveArrayInstance dataArr) {
        List<Object> values = dataArr.getValues();
        int[] dataInts = values.stream().mapToInt(x -> Byte.parseByte(x.toString())).toArray();
        byte[] dataBytes = new byte[dataInts.length];
        for (int i = 0; i < dataInts.length; i++) {
            dataBytes[i] = (byte) dataInts[i];
        }
        return new String(dataBytes, StandardCharsets.US_ASCII);
    }

    private static boolean isSubclass(JavaClass baseClass, Instance x) {
        if (baseClass == null) {
            return false;
        }
        JavaClass superClass = x.getJavaClass();
        while (superClass != null) {
            if (superClass.getJavaClassId() == baseClass.getJavaClassId()) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        return false;
    }

    private static Instance tryCo(Instance owner, String fieldName) {
        Object co = owner.getValueOfField("co");
        if (co instanceof Instance coInstance) {
            return getFieldInstance(coInstance, fieldName);
        } else {
            throw new IllegalStateException(String.format("Cannot extract field '%s' of object '%s'", fieldName, owner));
        }
    }

    private static Instance getFieldInstance(Instance owner, String fieldName) {
        Object value = owner.getValueOfField(fieldName);
        if (!(value instanceof Instance result)) {
            return tryCo(owner, fieldName);
        }
        return result;
    }
}