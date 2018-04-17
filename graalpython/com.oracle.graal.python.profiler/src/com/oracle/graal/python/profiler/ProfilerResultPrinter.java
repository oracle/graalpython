/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.profiler;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

import com.oracle.graal.python.nodes.*;
import com.oracle.graal.python.nodes.argument.*;
import com.oracle.graal.python.nodes.call.*;
import com.oracle.graal.python.nodes.call.CallDispatchBoxedNode.LinkedDispatchBoxedNode;
import com.oracle.graal.python.nodes.call.CallDispatchUnboxedNode.LinkedDispatchUnboxedNode;
import com.oracle.graal.python.nodes.call.PythonCallNode.BoxedCallNode;
import com.oracle.graal.python.nodes.call.PythonCallNode.CallConstructorNode;
import com.oracle.graal.python.nodes.call.PythonCallNode.UnboxedCallNode;
import com.oracle.graal.python.nodes.control.*;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.function.*;
import com.oracle.graal.python.runtime.*;


public class ProfilerResultPrinter {

    private PrintStream out = System.out;
    private PythonProfilerNodeProber profilerProber;
    private List<PNode> nodesEmptySourceSections = new ArrayList<>();
    private List<PNode> nodesUsingExistingProbes = new ArrayList<>();
    private final PythonParseResult parseResult;

    public ProfilerResultPrinter(PythonProfilerNodeProber profilerProber, PythonParseResult parseResult) {
        this.profilerProber = profilerProber;
        this.parseResult = parseResult;
    }

    private static long excludedTime = 0;
    private static long totalCounter = 0;
    private static long cumulativeTime = 0;

    public void printCallProfilerResults() {
        List<MethodBodyInstrument> methodBodyInstruments = profilerProber.getMethodBodyInstruments();
        Map<MethodBodyInstrument, List<Long>> timeMap = new HashMap<>();

        if (methodBodyInstruments.size() > 0) {
            printBanner("Call Time Profiling Results", 116);
            /**
             * 50 is the length of the text by default padding left padding is added, so space is
             * added to the beginning of the string, minus sign adds padding to the right
             */

            out.format("%-40s", "Function Name");
            out.format("%-20s", "Counter");
            out.format("%-20s", "Excluded Time");
            out.format("%-20s", "Avg Excluded");
            out.format("%-20s", "Cumulative Time");
            out.format("%-20s", "Avg Cumulative");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.println();
            out.println("===============                         ===============     ===============     ===============     ===============     ===============     ====     ======");

            excludedTime = 0;

            for (MethodBodyInstrument methodBodyInstrument : methodBodyInstruments) {
                Node methodBody = methodBodyInstrument.getNode();

                totalCounter = 0;
                cumulativeTime = 0;
                getCumulativeCounterTime(methodBodyInstrument);

                if (totalCounter > 0) {
                    if (methodBody instanceof ReturnTargetNode) {
                        getExcludedTime(methodBody, methodBodyInstrument);
                    } else {
                        excludedTime = cumulativeTime;
                    }

                    List<Long> times = new ArrayList<>();
                    times.add(totalCounter);
                    times.add(excludedTime);
                    times.add(cumulativeTime);
                    timeMap.put(methodBodyInstrument, times);
                }

            }

            printTime(timeMap);
        }
    }

    private void getCumulativeCounterTime(MethodBodyInstrument methodBodyInstrument) {
        ModuleNode moduleNode = (ModuleNode) parseResult.getModuleRoot();
        Node moduleBody = moduleNode.getBody();
        traverseBody(moduleBody, methodBodyInstrument);

        for (RootNode functionRoot : parseResult.getFunctionRoots()) {
            if (functionRoot instanceof FunctionRootNode) {
                Node methodBody = ((FunctionRootNode) functionRoot).getBody();
                traverseBody(methodBody, methodBodyInstrument);
            }
        }
    }

    private static void traverseBody(Node methodBody, MethodBodyInstrument methodBodyInstrument) {
        methodBody.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof BoxedCallNode || node instanceof CallConstructorNode || node instanceof UnboxedCallNode) {
                    CallDispatchNode callDispatchNode = null;
                    if (node instanceof BoxedCallNode) {
                        callDispatchNode = ((BoxedCallNode) node).getDispatchNode();
                    } else if (node instanceof CallConstructorNode) {
                        callDispatchNode = ((CallConstructorNode) node).getDispatchNode();
                    } else if (node instanceof UnboxedCallNode) {
                        callDispatchNode = ((UnboxedCallNode) node).getDispatchNode();
                    }

                    if (node.getParent() instanceof PythonWrapperNode) {
                        PythonWrapperNode callWrapper = (PythonWrapperNode) node.getParent();
                        Node callProbe = (Node) callWrapper.getProbe();
                        TimeProfilerInstrument subCallInstrument = (TimeProfilerInstrument) callProbe.getChildren().iterator().next();

                        DirectCallNode callNode = null;
                        if (callDispatchNode instanceof LinkedDispatchBoxedNode) {
                            LinkedDispatchBoxedNode linkDispatchNode = (LinkedDispatchBoxedNode) callDispatchNode;
                            callNode = linkDispatchNode.getInvokeNode().getDirectCallNode();
                        } else if (callDispatchNode instanceof LinkedDispatchUnboxedNode) {
                            LinkedDispatchUnboxedNode linkUnboxedDispatchNode = (LinkedDispatchUnboxedNode) callDispatchNode;
                            callNode = linkUnboxedDispatchNode.getInvokeNode().getDirectCallNode();
                        }

                        if (callNode != null) {
                            RootCallTarget callTarget = (RootCallTarget) callNode.getCallTarget();

                            PythonWrapperNode wrapper = null;
                            if (callTarget.getRootNode() instanceof FunctionRootNode) {
                                FunctionRootNode childRootNode = (FunctionRootNode) callTarget.getRootNode();
                                wrapper = (PythonWrapperNode) childRootNode.getBody();
                            } else if (callTarget.getRootNode() instanceof BuiltinFunctionRootNode) {
                                BuiltinFunctionRootNode childRootNode = (BuiltinFunctionRootNode) callTarget.getRootNode();
                                if (childRootNode.getBody() instanceof PythonWrapperNode) {
                                    wrapper = (PythonWrapperNode) childRootNode.getBody();
                                }
                            }

                            if (wrapper != null) {
                                Node probe = (Node) wrapper.getProbe();
                                MethodBodyInstrument currentMethodBodyInstrument = (MethodBodyInstrument) probe.getChildren().iterator().next();

                                if (currentMethodBodyInstrument.equals(methodBodyInstrument)) {
                                    totalCounter = totalCounter + subCallInstrument.getCounter();
                                    cumulativeTime = cumulativeTime + subCallInstrument.getTime();
                                }
                            }
                        }
                    }
                }
                return true;
            }
        });
    }

    public void getExcludedTime(Node methodBody, MethodBodyInstrument methodBodyInstrument) {
        excludedTime = cumulativeTime;

        methodBody.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof BoxedCallNode) {
                    PythonWrapperNode callWrapper = (PythonWrapperNode) node.getParent();
                    if (!(callWrapper.getParent() instanceof ArgumentsNode)) {
                        Node callProbe = (Node) callWrapper.getProbe();
                        TimeProfilerInstrument subCallInstrument = (TimeProfilerInstrument) callProbe.getChildren().iterator().next();
                        BoxedCallNode boxedCallNode = (BoxedCallNode) node;
                        CallDispatchNode callDispatchNode = boxedCallNode.getDispatchNode();
                        if (callDispatchNode instanceof LinkedDispatchBoxedNode) {
                            LinkedDispatchBoxedNode linkDispatchNode = (LinkedDispatchBoxedNode) callDispatchNode;
                            DirectCallNode callNode = linkDispatchNode.getInvokeNode().getDirectCallNode();
                            RootCallTarget callTarget = (RootCallTarget) callNode.getCallTarget();

                            PythonWrapperNode wrapper = null;
                            if (callTarget.getRootNode() instanceof FunctionRootNode) {
                                FunctionRootNode childRootNode = (FunctionRootNode) callTarget.getRootNode();
                                wrapper = (PythonWrapperNode) childRootNode.getBody();
                            } else if (callTarget.getRootNode() instanceof BuiltinFunctionRootNode) {
                                BuiltinFunctionRootNode childRootNode = (BuiltinFunctionRootNode) callTarget.getRootNode();
                                if (childRootNode.getBody() instanceof PythonWrapperNode) {
                                    wrapper = (PythonWrapperNode) childRootNode.getBody();
                                }
                            }

                            if (wrapper != null) {
                                Node probe = (Node) wrapper.getProbe();
                                MethodBodyInstrument currentMethodBodyInstrument = (MethodBodyInstrument) probe.getChildren().iterator().next();
                                /**
                                 * Do not exclude recursive calls
                                 */
                                if (!methodBodyInstrument.equals(currentMethodBodyInstrument)) {
                                    excludedTime = excludedTime - subCallInstrument.getTime();
                                }
                            }
                        }
                    }
                }
                return true;
            }
        });
    }

    private void printTime(Map<MethodBodyInstrument, List<Long>> timesMap) {
        Map<MethodBodyInstrument, List<Long>> sortedTimesMap;
        if (PythonOptions.SortProfilerResults) {
            sortedTimesMap = sortTimeProfilerResults(timesMap);
        } else {
            sortedTimesMap = timesMap;
        }
        long totalCalls = 0;

        for (Map.Entry<MethodBodyInstrument, List<Long>> entry : sortedTimesMap.entrySet()) {
            MethodBodyInstrument methodBodyInstrument = entry.getKey();
            Node methodBody = methodBodyInstrument.getNode();
            String methodName = null;

            if (methodBody instanceof ReturnTargetNode) {
                methodName = ((FunctionRootNode) methodBody.getRootNode()).getFunctionName();
            } else if (methodBody instanceof PythonBuiltinNode) {
                methodName = ((BuiltinFunctionRootNode) methodBody.getRootNode()).getFunctionName();
            }

            List<Long> times = entry.getValue();
            long counter = times.get(0);
            long excluded = times.get(1);
            long cumulative = times.get(2);

            out.format("%-40s", methodName);
            out.format("%15s", counter);
            totalCalls = totalCalls + counter;
            out.format("%20s", (excluded / 1000000000));
            out.format("%20s", ((excluded / counter) / 1000000000));
            out.format("%20s", (cumulative / 1000000000));
            out.format("%20s", ((cumulative / counter) / 1000000000));
            if (methodBody instanceof ReturnTargetNode) {
                out.format("%9s", methodBody.getSourceSection().getStartLine());
                out.format("%11s", methodBody.getSourceSection().getStartColumn());
            } else {
                out.format("%9s", "-");
                out.format("%11s", "-");
            }
            out.println();
        }

        out.println("Total number of executed calls: " + totalCalls);
    }

    public void printControlFlowProfilerResults() {
        long totalCount = 0;
        totalCount += printLoopProfilerResults();
        totalCount += printIfProfilerResults();
        totalCount += printBreakContinueProfilerResults();
        out.println("Total number of executed control flow instruments: " + totalCount);
    }

    private long printLoopProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> loopInstruments = getInstruments(profilerProber.getLoopInstruments());

        if (loopInstruments.size() > 0) {
            printCaption("Loop Profiling Results");

            for (ProfilerInstrument instrument : loopInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    Node loopNode = node.getParent().getParent();

                    if (loopNode instanceof LoopNode) {
                        /**
                         * During generator optimizations for node is replaced with
                         * PeeledGeneratorLoopNode. Since the for loop is replaced, it's better not
                         * to print the result of this specific for profiling.
                         *
                         */
                        printProfilerResult(loopNode, instrument.getCounter());
                        totalCount = totalCount + instrument.getCounter();
                    }
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }

        return totalCount;
    }

    private long printIfProfilerResults() {
        long totalCount = 0;
        Map<ProfilerInstrument, List<ProfilerInstrument>> ifInstruments;
        if (PythonOptions.SortProfilerResults) {
            ifInstruments = sortIfProfilerResults(profilerProber.getIfInstruments());
        } else {
            ifInstruments = profilerProber.getIfInstruments();
        }

        if (ifInstruments.size() > 0) {
            printBanner("If Node Profiling Results", 116);
            out.format("%-20s", "If Counter");
            out.format("%-18s", "Then Counter");
            out.format("%-18s", "Else Counter");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-70s", "In Method");
            out.println();
            out.println("===========         ============      =============     ====     ======     ========================================");

            Iterator<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>> it = ifInstruments.entrySet().iterator();
            while (it.hasNext()) {
                Entry<ProfilerInstrument, List<ProfilerInstrument>> entry = it.next();
                ProfilerInstrument ifInstrument = entry.getKey();
                if (ifInstrument.getCounter() > 0) {
                    List<ProfilerInstrument> instruments = entry.getValue();
                    ProfilerInstrument thenInstrument = instruments.get(0);
                    out.format("%11s", ifInstrument.getCounter());
                    out.format("%21s", thenInstrument.getCounter());

                    totalCount = totalCount + ifInstrument.getCounter();
                    totalCount = totalCount + thenInstrument.getCounter();

                    if (instruments.size() == 1) {
                        out.format("%19s", "-");
                    } else if (instruments.size() == 2) {
                        ProfilerInstrument elseInstrument = instruments.get(1);
                        out.format("%19s", elseInstrument.getCounter());
                        totalCount = totalCount + elseInstrument.getCounter();
                    }

                    Node ifNode = ifInstrument.getNode();
                    out.format("%9s", ifNode.getSourceSection().getStartLine());
                    out.format("%11s", ifNode.getSourceSection().getStartColumn());
                    out.format("%5s", "");
                    out.format("%-70s", ifNode.getRootNode());
                    out.println();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }

        return totalCount;
    }

    private long printBreakContinueProfilerResults() {
        return printProfilerResults("Break Continue Profiling Results", getInstruments(profilerProber.getBreakContinueInstruments()));
    }

    public void printVariableAccessProfilerResults() {
        if (PythonOptions.ProfileTypeDistribution) {
            printProfilerTypeDistributionResults("Variable Access Profiling Results", profilerProber.getVariableAccessTypeDistributionInstruments());
        } else {
            printProfilerResults("Variable Access Profiling Results", getInstruments(profilerProber.getVariableAccessInstruments()));
        }
    }

    public void printOperationProfilerResults() {
        if (PythonOptions.ProfileTypeDistribution) {
            printProfilerTypeDistributionResults("Operation Profiling Results", profilerProber.getOperationTypeDistributionInstruments());
        } else {
            printProfilerResults("Operation Profiling Results", getInstruments(profilerProber.getOperationInstruments()));
        }
    }

    public void printCollectionOperationsProfilerResults() {
        printProfilerResults("Collection Operations Profiling Results", getInstruments(profilerProber.getCollectionOperationsInstruments()));
    }

    private static List<ProfilerInstrument> getInstruments(List<ProfilerInstrument> instruments) {
        if (PythonOptions.SortProfilerResults) {
            List<ProfilerInstrument> sortedInstruments = sortProfilerResult(instruments);
            return sortedInstruments;
        }

        return instruments;
    }

    private long printProfilerResults(String caption, List<ProfilerInstrument> instruments) {
        long totalCount = 0;

        if (instruments.size() > 0) {
            printCaption(caption);

            for (ProfilerInstrument instrument : instruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    printProfilerResult(node, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }
            out.println("Total number of executed instruments: " + totalCount);
        }

        return totalCount;
    }

    private void printProfilerTypeDistributionResults(String caption, List<TypeDistributionProfilerInstrument> instruments) {
        long totalCount = 0;

        if (instruments.size() > 0) {
            printBanner(caption, 140);
            out.format("%-50s", "Node");
            out.format("%-20s", "Counter");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-70s", "In Method");
            out.println();
            out.println("=============                                     ===============     ====     ======     ==================================================");

            for (TypeDistributionProfilerInstrument profilerInstrument : instruments) {
                Map<Class<? extends Node>, Counter> types = profilerInstrument.getTypes();

                if (types.isEmpty()) {
                    Node initialNode = profilerInstrument.getInitialNode();
                    Node onlyNode = profilerInstrument.getOnlyNode();
                    long counter = profilerInstrument.getOnlyCounter();
                    Class<? extends Node> nodeClass = onlyNode.getClass();
                    totalCount = totalCount + counter;
                    out.format("%-50s", nodeClass.getSimpleName());
                    out.format("%15s", counter);
                    out.format("%9s", initialNode.getSourceSection().getStartLine());
                    out.format("%11s", initialNode.getSourceSection().getStartColumn());
                    out.format("%5s", "");
                    out.format("%-70s", initialNode.getRootNode());
                    out.println();
                } else {
                    Iterator<Map.Entry<Class<? extends Node>, Counter>> it = types.entrySet().iterator();
                    out.println();

                    while (it.hasNext()) {
                        Entry<Class<? extends Node>, Counter> entry = it.next();
                        Node initialNode = profilerInstrument.getInitialNode();
                        Class<? extends Node> nodeClass = entry.getKey();
                        long counter = entry.getValue().getCounter();
                        totalCount = totalCount + counter;
                        out.format("%-50s", nodeClass.getSimpleName());
                        out.format("%15s", counter);
                        out.format("%9s", initialNode.getSourceSection().getStartLine());
                        out.format("%11s", initialNode.getSourceSection().getStartColumn());
                        out.format("%5s", "");
                        out.format("%-70s", initialNode.getRootNode());
                        out.println();
                    }

                    out.println();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }
    }

    private void printCaption(String caption) {
        printBanner(caption, 116);
        out.format("%-25s", "Node");
        out.format("%-20s", "Counter");
        out.format("%-9s", "Line");
        out.format("%-11s", "Column");
        out.format("%-70s", "In Method");
        out.println();
        out.println("=============            ===============     ====     ======     ===================================================");
    }

    private void printProfilerResult(Node node, long counter) {
        String nodeName = getShortName(node);
        out.format("%-25s", nodeName);
        out.format("%15s", counter);
        out.format("%9s", node.getSourceSection().getStartLine());
        out.format("%11s", node.getSourceSection().getStartColumn());
        out.format("%11s", node.getSourceSection().getCharLength());
        out.format("%5s", "");
        out.format("%-70s", node.getRootNode());
        out.println();
    }

    private static String getShortName(Node node) {
        NodeInfo nodeInfo = node.getClass().getAnnotation(NodeInfo.class);

        if (nodeInfo == null) {
            nodeInfo = node.getClass().getSuperclass().getAnnotation(NodeInfo.class);
        } else if (nodeInfo.shortName().equals("")) {
            nodeInfo = node.getClass().getSuperclass().getAnnotation(NodeInfo.class);
        }

        if (nodeInfo != null) {
            return nodeInfo.shortName();
        } else {
            throw new RuntimeException("Short name is missing in " + node);
        }
    }

    private static List<ProfilerInstrument> sortProfilerResult(List<ProfilerInstrument> list) {
        Collections.sort(list, new Comparator<ProfilerInstrument>() {
            @Override
            public int compare(final ProfilerInstrument profiler1, final ProfilerInstrument profiler2) {
                return Long.compare(profiler2.getCounter(), profiler1.getCounter());
            }
        });

        return list;
    }

    private static Map<ProfilerInstrument, List<ProfilerInstrument>> sortIfProfilerResults(Map<ProfilerInstrument, List<ProfilerInstrument>> map) {
        List<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>>() {

            public int compare(Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> if1, Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> if2) {
                return Long.compare(if2.getKey().getCounter(), if1.getKey().getCounter());
            }
        });

        Map<ProfilerInstrument, List<ProfilerInstrument>> result = new LinkedHashMap<>();
        for (Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    private static Map<MethodBodyInstrument, List<Long>> sortTimeProfilerResults(Map<MethodBodyInstrument, List<Long>> map) {
        List<Map.Entry<MethodBodyInstrument, List<Long>>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<MethodBodyInstrument, List<Long>>>() {

            public int compare(Map.Entry<MethodBodyInstrument, List<Long>> if1, Map.Entry<MethodBodyInstrument, List<Long>> if2) {
                return Long.compare(if2.getValue().get(0).longValue(), if1.getValue().get(0).longValue());
            }
        });

        Map<MethodBodyInstrument, List<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<MethodBodyInstrument, List<Long>> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public void addNodeEmptySourceSection(PNode node) {
        nodesEmptySourceSections.add(node);
    }

    public void addNodeUsingExistingProbe(PNode node) {
        nodesUsingExistingProbes.add(node);
    }

    public void printNodesEmptySourceSections() {
        if (nodesEmptySourceSections.size() > 0) {
            printBanner("Nodes That Have Empty Source Sections", 10);
            for (PNode node : nodesEmptySourceSections) {
                out.println(node.getClass().getSimpleName() + " in " + node.getRootNode());
            }
        }
    }

    public void printNodesUsingExistingProbes() {
        if (nodesUsingExistingProbes.size() > 0) {
            printBanner("Nodes That Reuses an Existing Probe", 10);
            for (PNode node : nodesUsingExistingProbes) {
                out.println(node.getClass().getSimpleName() + " in " + node.getRootNode());
            }
        }
    }

    private void printBanner(String caption, int size) {
        // CheckStyle: stop system..print check
        int bannerSize = size - caption.length() - 2;
        for (int i = 0; i < bannerSize / 2; i++) {
            out.print("=");
        }

        out.print(" " + caption + " ");

        for (int i = 0; i < (bannerSize - (bannerSize / 2)); i++) {
            out.print("=");
        }

        out.println();
        // CheckStyle: resume system..print check
    }
}
