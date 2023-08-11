/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.advance;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;

import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Standalone tool that can be run via {@code mx python-nodes-footprint heap_dump1 heap_dump2}.
 * 
 * It creates a mapping between root nodes instances in two heap dumps according to their source
 * sections. The tool provides comparison of retained sizes of root nodes with the same source
 * sections.
 *
 * It is recommended to run GraalPy with the following options when taking the heap dumps.
 * 
 * <pre>
 *     --engine.Splitting=false --engine.OSR=false --python.BuiltinsInliningMaxCallerSize=0 --python.ForceInitializeSourceSections=true
 * </pre>
 */
public class NodeFootprintAnalyzer {
    public abstract static class RootInfo implements Comparable<RootInfo> {
        // Placeholder for duplicate entries
        public static RootInfo DUPLICATE = new RootInfo(null, null) {
            @Override
            public long adoptedNodesRetrainedSize() {
                return 0;
            }
        };
        private final Instance instance;
        private final String name;

        public RootInfo(Instance instance, String name) {
            this.instance = instance;
            this.name = name;
        }

        public abstract long adoptedNodesRetrainedSize();

        @Override
        public int compareTo(RootInfo o) {
            return Long.compare(adoptedNodesRetrainedSize(), o.adoptedNodesRetrainedSize());
        }

        public Instance instance() {
            return instance;
        }

        public String name() {
            return name;
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
    }

    public static final class PBytecodeRootInfo extends RootInfo {
        private final ObjectArrayInstance adoptedNodes;

        public PBytecodeRootInfo(Instance instance, String name, ObjectArrayInstance adoptedNodes) {
            super(instance, name);
            this.adoptedNodes = adoptedNodes;
        }

        public static RootInfo create(Instance instance) {
            Object adoptedNodesField = instance.getValueOfField("adoptedNodes");
            ObjectArrayInstance adoptedNodes = null;
            if (adoptedNodesField instanceof ObjectArrayInstance adoptedNodesArr) {
                adoptedNodes = adoptedNodesArr;
            }
            return new PBytecodeRootInfo(instance, getRootName(instance), adoptedNodes);
        }

        @Override
        public long adoptedNodesRetrainedSize() {
            return adoptedNodes == null ? 0 : adoptedNodes.getRetainedSize();
        }
    }

    public static final class BuiltinFunctionRootInfo extends RootInfo {
        private final Instance body;

        public BuiltinFunctionRootInfo(Instance instance, String name, Instance body) {
            super(instance, name);
            this.body = body;
        }

        public static RootInfo create(Instance instance) {
            Object bodyField = instance.getValueOfField("body");
            Instance body = null;
            if (bodyField instanceof Instance bodyFieldInstance) {
                body = bodyFieldInstance;
            }
            return new BuiltinFunctionRootInfo(instance, getRootName(instance), body);
        }

        @Override
        public long adoptedNodesRetrainedSize() {
            return body == null ? 0 : body.getRetainedSize();
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
        private final RootInfo r1;
        private final RootInfo r2;
        private ArrayList<AdoptedNodesPair> adoptedNodes;

        public RootsPair(RootInfo r1, RootInfo r2) {
            this.r1 = r1;
            this.r2 = r2;
        }

        long adoptedNodesRetainedSizeDiff() {
            return r1.adoptedNodesRetrainedSize() - r2.adoptedNodesRetrainedSize();
        }

        @Override
        public int compareTo(RootsPair o) {
            return Long.compare(abs(adoptedNodesRetainedSizeDiff()), abs(o.adoptedNodesRetainedSizeDiff()));
        }

        @SuppressWarnings("unchecked")
        public ArrayList<AdoptedNodesPair> getAdoptedNodes() {
            if (adoptedNodes == null) {
                if (r1 instanceof PBytecodeRootInfo bci1 && r2 instanceof PBytecodeRootInfo bci2) {
                    if (bci1.adoptedNodes.getLength() != bci2.adoptedNodes.getLength()) {
                        System.out.printf("WARNING: '%s': not the same adoptedNodes array size\n", r1.name);
                    }
                    int size = min(bci1.adoptedNodes.getLength(), bci2.adoptedNodes.getLength());
                    adoptedNodes = new ArrayList<>(size);
                    List<Instance> values1 = bci1.adoptedNodes.getValues();
                    List<Instance> values2 = bci2.adoptedNodes.getValues();
                    for (int i = 0; i < size; i++) {
                        adoptedNodes.add(new AdoptedNodesPair(i, values1.get(i), values2.get(i)));
                    }
                    adoptedNodes.sort(Comparator.reverseOrder());
                } else if (r1 instanceof BuiltinFunctionRootInfo builtin1 && r2 instanceof BuiltinFunctionRootInfo builtin2) {
                    adoptedNodes = new ArrayList<>();
                    adoptedNodes.add(new AdoptedNodesPair(0, builtin1.body, builtin2.body));
                }
            }
            return adoptedNodes;
        }

        public String adoptedNodesDiffTable() {
            if (getAdoptedNodes().size() <= 1) {
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

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].contains("help") || args.length > 2) {
            System.out.println("Arguments: heap-dump-file1 heap-dump-file2");
            return;
        }

        System.out.println();
        System.out.println("====================");
        System.out.printf("processing %s...\n", args[0]);
        Map<String, RootInfo> result0 = processDump(args[0]);

        System.out.println("====================");
        System.out.printf("processing %s...\n", args[1]);
        Map<String, RootInfo> result1 = processDump(args[1]);

        HashMap<String, RootsPair> joined = new HashMap<>();
        ArrayList<String> notFound = new ArrayList<>();
        for (Entry<String, RootInfo> e : result0.entrySet()) {
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
        System.out.println("Root nodes with the highest difference: ");
        System.out.printf("      %10s %10s %10s %15s %15s %10s\n", "diff", "ast_size1", "ast_size2", "id1", "id2", "name");
        List<String> diffs = joined.values().stream().//
                        filter(x -> x.adoptedNodesRetainedSizeDiff() != 0).//
                        sorted(Comparator.reverseOrder()).//
                        map(x -> String.format("root: %10d %10d %10d %15d %15d %s\n%s",
                                        x.adoptedNodesRetainedSizeDiff(), //
                                        x.r1.adoptedNodesRetrainedSize(), //
                                        x.r2.adoptedNodesRetrainedSize(), //
                                        x.r1.instance.getInstanceId(), //
                                        x.r2.instance.getInstanceId(), //
                                        x.r1.name, //
                                        x.adoptedNodesDiffTable())).toList();
        diffs.stream().limit(20).forEach(System.out::println);
        final String rootsDiffFile = "/tmp/roots-diff.txt";
        Files.write(Paths.get(rootsDiffFile), diffs);
        System.out.println("Full list was written to " + rootsDiffFile);

        if (notFound.size() > 0) {
            final String onlyOnceRootsFile = "/tmp/only-once-roots.txt";
            Files.write(Paths.get(onlyOnceRootsFile), notFound);
            System.out.println("WARNING: there were some roots nodes that were found in only one of the dumps.");
            System.out.println("List of the names of those roots was written to " + onlyOnceRootsFile);
        }

        System.out.println();
        System.out.printf("Total retained size: %15s %15s %15s\n", "size1", "size2", "diff");
        long totalDiff = joined.values().stream().mapToLong(RootsPair::adoptedNodesRetainedSizeDiff).sum();
        long size1 = joined.values().stream().mapToLong(x -> x.r1.adoptedNodesRetrainedSize()).sum();
        long size2 = joined.values().stream().mapToLong(x -> x.r2.adoptedNodesRetrainedSize()).sum();
        System.out.printf("                     %15d %15d %15d (%d%%)\n", size1, size2, totalDiff, round(totalDiff / ((double) size2 / 100)));

        System.out.println();
        System.out.println("To explore individual objects in VisualVM use OQL query: `[heap.findObjet(ID)]`");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, RootInfo> processDump(String dumpFile) throws IOException {
        Heap heap = HeapFactory.createHeap(new File(dumpFile));
        Iterator<Instance> instancesIt = heap.getAllInstancesIterator();

        JavaClass bytecodeRootNodeClass = heap.getJavaClassByName(PBytecodeRootNode.class.getName());
        JavaClass builtinRootNodeClass = heap.getJavaClassByName(BuiltinFunctionRootNode.class.getName());
        Map<String, RootInfo> roots = Stream.concat(
                        StreamSupport.stream(Spliterators.spliteratorUnknownSize(instancesIt, Spliterator.ORDERED),
                                        false).//
                                        filter(x -> isSubclass(bytecodeRootNodeClass, x)).//
                                        map(PBytecodeRootInfo::create),
                        StreamSupport.stream(Spliterators.spliteratorUnknownSize(instancesIt, Spliterator.ORDERED),
                                        false).//
                                        filter(x -> isSubclass(builtinRootNodeClass, x)).//
                                        map(BuiltinFunctionRootInfo::create)).//
                        collect(Collectors.toMap(RootInfo::name, x -> x, (a, b) -> RootInfo.DUPLICATE));
        List<String> duplicates = roots.entrySet().stream().filter(x -> x.getValue() == RootInfo.DUPLICATE).map(Entry::getKey).toList();
        duplicates.forEach(roots::remove);
        if (duplicates.size() > 0) {
            final String duplicatedRootsFile = "/tmp/duplicated-roots.text";
            Files.write(Paths.get(duplicatedRootsFile), duplicates);
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
        return String.format("%d,%s,%s,%d", info.instance.getInstanceId(), info.instance.getJavaClass().getName(), info.name(), info.adoptedNodesRetrainedSize());
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

    private static String getRootName(Instance root) {
        Object nameFieldValue = root.getValueOfField("name");
        if (nameFieldValue instanceof Instance fieldValueInstance) {
            if (fieldValueInstance.getJavaClass().getName().equals(TruffleString.class.getName())) {
                Object dataObj = fieldValueInstance.getValueOfField("data");
                if (dataObj instanceof PrimitiveArrayInstance dataArr) {
                    return getRootSourceInfo(root) + ":" + byteArrayToString(dataArr);
                }
            } else if (fieldValueInstance.getJavaClass().getName().equals(String.class.getName())) {
                String sourceInfo = root.getJavaClass().getName();
                if (root.getJavaClass().getName().equals("com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode")) {
                    Object bodyFieldValue = root.getValueOfField("factory");
                    if (bodyFieldValue instanceof Instance bodyFieldInstance) {
                        sourceInfo = bodyFieldInstance.getJavaClass().getName();
                    }
                }
                return sourceInfo + ":" + getJavaStringContents(fieldValueInstance);
            }
        }
        return "<unknown>";
    }

    private static String getRootSourceInfo(Instance root) {
        String sourceName = getSourceName(root.getValueOfField("source"));
        Object sectionFieldValue = root.getValueOfField("sourceSection");
        if (sectionFieldValue instanceof Instance sectionInstance &&
                        sectionInstance.getJavaClass().getName().equals("com.oracle.truffle.api.source.SourceSectionLoaded")) {
            Object indexValue = sectionInstance.getValueOfField("charIndex");
            if (indexValue instanceof Integer indexAsInt && indexAsInt != 0) {
                return sourceName + ":" + indexAsInt;
            }
        }
        return sourceName;
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
                    String name = getJavaStringContents(nameInstance);
                    if (name != null) {
                        return name;
                    }
                }
            }
        }
        System.out.println("WARNING: could not extract source name");
        return "<unknown>";
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
        JavaClass superClass = x.getJavaClass();
        while (superClass != null) {
            if (superClass.getJavaClassId() == baseClass.getJavaClassId()) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        return false;
    }
}
