/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;

@GenerateWrapper
public class ImportNode extends AbstractImportNode {
    private final String moduleName;

    public ImportNode(String moduleName) {
        this.moduleName = moduleName;
    }

    public ImportNode(ImportNode original) {
        this.moduleName = original.moduleName;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        importModule(frame, moduleName);
    }

    public static final class ImportExpression extends ExpressionNode {
        @Child ImportNode importNode;

        private ImportExpression(ImportNode importNode) {
            this.importNode = importNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return importNode.importModule(frame, importNode.moduleName);
        }

    }

    public ImportExpression asExpression() {
        return new ImportExpression(this);
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new ImportNodeWrapper(this, this, probe);
    }

    public String getModuleName() {
        return moduleName;
    }
}
