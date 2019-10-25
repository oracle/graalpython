/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Map;

public class FunctionDefinitionNode extends ExpressionDefinitionNode {
    protected final ContextReference<PythonContext> contextRef;
    protected final String functionName;
    protected final String enclosingClassName;
    protected final RootCallTarget callTarget;

    @Children protected ExpressionNode[] defaults;
    @Children protected KwDefaultExpressionNode[] kwDefaults;
    @Child private ExpressionNode doc;
    @Child private WriteAttributeToObjectNode writeAttrNode = WriteAttributeToObjectNode.create();
    @Child private WriteAttributeToDynamicObjectNode writeNameNode = WriteAttributeToDynamicObjectNode.create();
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();

    @CompilerDirectives.CompilationFinal(dimensions = 1)private final String[] annotationNames;
    @Children private ExpressionNode[] annotationTypes;

    private final Assumption sharedCodeStableAssumption = Truffle.getRuntime().createAssumption("shared code stable assumption");
    private final Assumption sharedDefaultsStableAssumption = Truffle.getRuntime().createAssumption("shared defaults stable assumption");

    public FunctionDefinitionNode(String functionName, String enclosingClassName, ExpressionNode doc, ExpressionNode[] defaults, KwDefaultExpressionNode[] kwDefaults,
                    RootCallTarget callTarget,
                    DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots,
                    Map<String, ExpressionNode> annotations) {
        super(definitionCellSlots, executionCellSlots);
        this.contextRef = PythonLanguage.getContextRef();
        this.functionName = functionName;
        this.enclosingClassName = enclosingClassName;
        this.doc = doc;
        this.callTarget = callTarget;
        assert defaults == null || noNullElements(defaults);
        this.defaults = defaults;
        assert kwDefaults == null || noNullElements(kwDefaults);
        this.kwDefaults = kwDefaults;
        if (annotations != null) {
            this.annotationNames = annotations.keySet().toArray(new String[annotations.size()]);
            this.annotationTypes = annotations.values().toArray(new ExpressionNode[annotations.size()]);
        } else {
            this.annotationNames = null;
            this.annotationTypes = null;
        }
    }

    protected PythonContext getContext() {
        return contextRef.get();
    }

    protected PythonObjectFactory factory() {
        return factory;
    }

    private static boolean noNullElements(Object[] array) {
        for (Object element : array) {
            if (element == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] defaultValues = computeDefaultValues(frame);
        PKeyword[] kwDefaultValues = computeKwDefaultValues(frame);
        PCell[] closure = getClosureFromGeneratorOrFunctionLocals(frame);
        Assumption codeStableAssumption;
        Assumption defaultsStableAssumption;

        if (CompilerDirectives.inCompiledCode()) {
            codeStableAssumption = getSharedCodeStableAssumption();
            defaultsStableAssumption = getSharedDefaultsStableAssumption();
        } else {
            codeStableAssumption = Truffle.getRuntime().createAssumption();
            defaultsStableAssumption = Truffle.getRuntime().createAssumption();
        }
        PFunction func = withDocString(frame,
                        factory().createFunction(functionName, enclosingClassName, callTarget, PArguments.getGlobals(frame), defaultValues, kwDefaultValues, closure, writeNameNode,
                                        codeStableAssumption, defaultsStableAssumption));

        // Processing annotated arguments.
        // The __annotations__ dictionary is created even there are is not any annotated arg.
        PDict annotations = factory().createDict();
        writeAttrNode.execute(func, __ANNOTATIONS__, annotations);
        if (annotationNames != null) {
            writeAnnotations(frame, annotations);
        }
        return func;
    }

    private Assumption getSharedDefaultsStableAssumption() {
        return sharedDefaultsStableAssumption;
    }

    private Assumption getSharedCodeStableAssumption() {
        return sharedCodeStableAssumption;
    }

    @ExplodeLoop
    private PKeyword[] computeKwDefaultValues(VirtualFrame frame) {
        PKeyword[] kwDefaultValues = null;
        if (kwDefaults != null) {
            kwDefaultValues = new PKeyword[kwDefaults.length];
            for (int i = 0; i < kwDefaults.length; i++) {
                kwDefaultValues[i] = new PKeyword(kwDefaults[i].name, kwDefaults[i].execute(frame));
            }
        }
        return kwDefaultValues;
    }

    @ExplodeLoop
    private Object[] computeDefaultValues(VirtualFrame frame) {
        Object[] defaultValues = null;
        if (defaults != null) {
            defaultValues = new Object[defaults.length];
            for (int i = 0; i < defaults.length; i++) {
                defaultValues[i] = defaults[i].execute(frame);
            }
        }
        return defaultValues;
    }
    
    @ExplodeLoop
    private void writeAnnotations(VirtualFrame frame, PDict annotations) {
        for (int i = 0; i < annotationNames.length; i++) {
            // compute the types of the arg
            Object type = annotationTypes[i].execute(frame);
            // set the annotations
            annotations.setItem(annotationNames[i], type);
        }
    }

    protected final <T extends PFunction> T withDocString(VirtualFrame frame, T func) {
        if (doc != null) {
            writeAttrNode.execute(func, SpecialAttributeNames.__DOC__, doc.execute(frame));
        }
        return func;
    }

    public String getFunctionName() {
        return functionName;
    }

    public RootNode getFunctionRoot() {
        return callTarget.getRootNode();
    }

    public ExpressionNode[] getDefaults() {
        return defaults;
    }

    public KwDefaultExpressionNode[] getKwDefaults() {
        return kwDefaults;
    }

    public static final class KwDefaultExpressionNode extends ExpressionNode {
        @Child public ExpressionNode exprNode;

        public final String name;

        public KwDefaultExpressionNode(String name, ExpressionNode exprNode) {
            this.name = name;
            this.exprNode = exprNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return exprNode.execute(frame);
        }

        public static KwDefaultExpressionNode create(String name, ExpressionNode exprNode) {
            return new KwDefaultExpressionNode(name, exprNode);
        }
    }

    public ExpressionNode getDoc() {
        return doc;
    }

}
