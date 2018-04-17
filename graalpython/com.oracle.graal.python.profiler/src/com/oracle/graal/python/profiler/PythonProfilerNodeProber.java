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

import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;

import com.oracle.graal.python.nodes.*;
import com.oracle.graal.python.runtime.*;


public final class PythonProfilerNodeProber implements ASTNodeProber {

    private static final PythonProfilerNodeProber INSTANCE = new PythonProfilerNodeProber();

    private List<MethodBodyInstrument> methodBodyInstruments;
    private List<PNode> builtinBodies;
    private List<TimeProfilerInstrument> callInstruments;
    private List<ProfilerInstrument> loopInstruments;
    private List<ProfilerInstrument> breakContinueInstruments;
    private List<ProfilerInstrument> variableAccessInstruments;
    private List<ProfilerInstrument> operationInstruments;
    private List<ProfilerInstrument> collectionOperationsInstruments;

    private List<TypeDistributionProfilerInstrument> variableAccessTypeDistributionInstruments;
    private List<TypeDistributionProfilerInstrument> operationTypeDistributionInstruments;

    private Map<ProfilerInstrument, List<ProfilerInstrument>> ifInstruments;

    private PythonProfilerNodeProber() {
        methodBodyInstruments = new ArrayList<>();
        callInstruments = new ArrayList<>();
        loopInstruments = new ArrayList<>();
        breakContinueInstruments = new ArrayList<>();
        variableAccessInstruments = new ArrayList<>();
        operationInstruments = new ArrayList<>();
        collectionOperationsInstruments = new ArrayList<>();
        variableAccessTypeDistributionInstruments = new ArrayList<>();
        operationTypeDistributionInstruments = new ArrayList<>();
        ifInstruments = new LinkedHashMap<>();
    }

    public static PythonProfilerNodeProber getInstance() {
        return INSTANCE;
    }

    public Node probeAs(Node astNode, SyntaxTag tag, Object... args) {
        return astNode;
    }

    public PythonWrapperNode probeAsMethodBody(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);
        MethodBodyInstrument profilerInstrument = createAttachMethodBodyInstrument(wrapper);
        methodBodyInstruments.add(profilerInstrument);
        return wrapper;
    }

    public void addBuiltinMethodBody(PNode node) {
        builtinBodies.add(node);
    }

    public PythonWrapperNode probeAsCall(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);
        wrapper.getProbe().tagAs(StandardSyntaxTag.CALL);
        TimeProfilerInstrument profilerInstrument = createAttachTimeProfilerInstrument(wrapper);
        callInstruments.add(profilerInstrument);
        return wrapper;
    }

    public PythonWrapperNode probeAsLoop(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        loopInstruments.add(profilerInstrument);
        return wrapper;
    }

    public List<PythonWrapperNode> probeAsIf(PNode ifNode, PNode thenNode, PNode elseNode, PythonContext context) {
        List<PythonWrapperNode> wrappers = new ArrayList<>();
        PythonWrapperNode ifWrapper = createWrapper(ifNode, context);
        PythonWrapperNode thenWrapper = createWrapper(thenNode, context);
        PythonWrapperNode elseWrapper = createWrapper(elseNode, context);
        wrappers.add(ifWrapper);
        wrappers.add(thenWrapper);
        wrappers.add(elseWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(thenWrapper);
        ProfilerInstrument elseInstrument = createAttachProfilerInstrument(elseWrapper);
        instruments.add(thenInstrument);
        instruments.add(elseInstrument);
        ifInstruments.put(ifInstrument, instruments);

        return wrappers;
    }

    public List<PythonWrapperNode> probeAsIfWithoutElse(PNode ifNode, PNode thenNode, PythonContext context) {
        List<PythonWrapperNode> wrappers = new ArrayList<>();
        PythonWrapperNode ifWrapper = createWrapper(ifNode, context);
        PythonWrapperNode thenWrapper = createWrapper(thenNode, context);
        wrappers.add(ifWrapper);
        wrappers.add(thenWrapper);

        List<ProfilerInstrument> instruments = new ArrayList<>();
        ProfilerInstrument ifInstrument = createAttachProfilerInstrument(ifWrapper);
        ProfilerInstrument thenInstrument = createAttachProfilerInstrument(thenWrapper);
        instruments.add(thenInstrument);
        ifInstruments.put(ifInstrument, instruments);
        return wrappers;
    }

    public PythonWrapperNode probeAsBreakContinue(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        breakContinueInstruments.add(profilerInstrument);
        return wrapper;
    }

    public PythonWrapperNode probeAsVariableAccess(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);

        if (PythonOptions.ProfileTypeDistribution) {
            TypeDistributionProfilerInstrument profilerInstrument = createAttachTypeDistributionProfilerInstrument(wrapper);
            variableAccessTypeDistributionInstruments.add(profilerInstrument);
        } else {
            ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
            variableAccessInstruments.add(profilerInstrument);
        }

        return wrapper;
    }

    public PythonWrapperNode probeAsOperation(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);

        if (PythonOptions.ProfileTypeDistribution) {
            TypeDistributionProfilerInstrument profilerInstrument = createAttachTypeDistributionProfilerInstrument(wrapper);
            operationTypeDistributionInstruments.add(profilerInstrument);
        } else {
            ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
            operationInstruments.add(profilerInstrument);
        }

        return wrapper;
    }

    public PythonWrapperNode probeAsCollectionOperation(PNode node, PythonContext context) {
        PythonWrapperNode wrapper = createWrapper(node, context);
        ProfilerInstrument profilerInstrument = createAttachProfilerInstrument(wrapper);
        collectionOperationsInstruments.add(profilerInstrument);
        return wrapper;
    }

    private static PythonWrapperNode createWrapper(PNode node, PythonContext context) {
        PythonWrapperNode wrapper;
        if (node instanceof PythonWrapperNode) {
            wrapper = (PythonWrapperNode) node;
        } else if (node.getParent() != null && node.getParent() instanceof PythonWrapperNode) {
            wrapper = (PythonWrapperNode) node.getParent();
        } else {
            wrapper = new PythonWrapperNode(context, node);
            wrapper.assignSourceSection(node.getSourceSection());
        }

        return wrapper;
    }

    private static ProfilerInstrument createAttachProfilerInstrument(PythonWrapperNode wrapper) {
        ProfilerInstrument profilerInstrument = new ProfilerInstrument(wrapper.getChild());
        profilerInstrument.assignSourceSection(wrapper.getChild().getSourceSection());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    private static TypeDistributionProfilerInstrument createAttachTypeDistributionProfilerInstrument(PythonWrapperNode wrapper) {
        TypeDistributionProfilerInstrument profilerInstrument = new TypeDistributionProfilerInstrument(wrapper.getChild());
        profilerInstrument.assignSourceSection(wrapper.getChild().getSourceSection());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    private static TimeProfilerInstrument createAttachTimeProfilerInstrument(PythonWrapperNode wrapper) {
        TimeProfilerInstrument profilerInstrument = new TimeProfilerInstrument(wrapper.getChild());
        profilerInstrument.assignSourceSection(wrapper.getChild().getSourceSection());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    private static MethodBodyInstrument createAttachMethodBodyInstrument(PythonWrapperNode wrapper) {
        MethodBodyInstrument profilerInstrument = new MethodBodyInstrument(wrapper.getChild());
        profilerInstrument.assignSourceSection(wrapper.getChild().getSourceSection());
        wrapper.getProbe().addInstrument(profilerInstrument);
        return profilerInstrument;
    }

    public List<MethodBodyInstrument> getMethodBodyInstruments() {
        return methodBodyInstruments;
    }

    public List<TimeProfilerInstrument> getCallInstruments() {
        return callInstruments;
    }

    public List<ProfilerInstrument> getLoopInstruments() {
        return loopInstruments;
    }

    public Map<ProfilerInstrument, List<ProfilerInstrument>> getIfInstruments() {
        return ifInstruments;
    }

    public List<ProfilerInstrument> getBreakContinueInstruments() {
        return breakContinueInstruments;
    }

    public List<ProfilerInstrument> getVariableAccessInstruments() {
        return variableAccessInstruments;
    }

    public List<ProfilerInstrument> getOperationInstruments() {
        return operationInstruments;
    }

    public List<ProfilerInstrument> getCollectionOperationsInstruments() {
        return collectionOperationsInstruments;
    }

    public List<TypeDistributionProfilerInstrument> getVariableAccessTypeDistributionInstruments() {
        return variableAccessTypeDistributionInstruments;
    }

    public List<TypeDistributionProfilerInstrument> getOperationTypeDistributionInstruments() {
        return operationTypeDistributionInstruments;
    }

}
