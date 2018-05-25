/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class, PythonOptions.class, SpecialMethodNames.class, SpecialAttributeNames.class, BuiltinNames.class})
@GenerateWrapper
public abstract class PNode extends PBaseNode implements InstrumentableNode {
    @CompilationFinal private SourceSection sourceSection;
    @CompilationFinal private boolean isStmt = false;
    @CompilationFinal private boolean isRoot = false;
    @CompilationFinal private boolean isTryBlock = false;

    @Override
    public String toString() {
        if (getSourceSection() != null)
            return getSourceSection().getSource().getName() + ":" + getSourceSection().getStartLine();
        else
            return super.toString();
    }

    @Override
    public SourceSection getSourceSection() {
        return this.sourceSection;
    }

    public abstract Object execute(VirtualFrame frame);

    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        Object value = execute(frame);
        if (value instanceof Integer) {
            return (int) value;
        }
        throw new UnexpectedResultException(value);
    }

    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        Object value = execute(frame);
        if (value instanceof Long) {
            return (long) value;
        }
        throw new UnexpectedResultException(value);
    }

    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        Object o = execute(frame);
        if (o instanceof Double) {
            return (double) o;
        }
        throw new UnexpectedResultException(o);
    }

    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        Object o = execute(frame);
        if (o instanceof Boolean) {
            return (boolean) o;
        }
        throw new UnexpectedResultException(o);
    }

    public void executeVoid(VirtualFrame frame) {
        execute(frame);
    }

    public boolean hasSideEffectAsAnExpression() {
        return false;
    }

    public void clearSourceSection() {
        this.sourceSection = null;
    }

    public void assignSourceSection(SourceSection source) {
        this.sourceSection = source;
    }

    public boolean hasTag(Class<? extends Tag> tag) {
        return (isStmt && tag == StandardTags.StatementTag.class) || (isRoot && tag == StandardTags.RootTag.class) || (isTryBlock && tag == StandardTags.TryBlockTag.class);
    }

    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new PNodeWrapper(this, probeNode);
    }

    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    public boolean isStatement() {
        return isStmt;
    }

    public void markAsStatement() {
        isStmt = true;
    }

    public void markAsRoot() {
        isRoot = true;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void markAsTryBlock() {
        isTryBlock = true;
    }

    public boolean isTryBlock() {
        return isTryBlock;
    }

    public Object getNodeObject() {
        if (isTryBlock) {
            if (this.getParent() instanceof TryExceptNode) {
                return this.getParent();
            } else if (this.getParent() instanceof PNodeWrapper) {
                assert this.getParent().getParent() instanceof TryExceptNode;
                return this.getParent().getParent();
            }
        }
        return null;
    }
}
