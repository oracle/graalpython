/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.interop.PythonScopes;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class, PythonOptions.class, SpecialMethodNames.class, SpecialAttributeNames.class, BuiltinNames.class})
@ExportLibrary(NodeLibrary.class)
public abstract class PNode extends PNodeWithContext implements InstrumentableNode {
    public static final PNode[] EMPTY_ARRAY = new PNode[0];
    @CompilationFinal private SourceSection sourceSection;

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (getSourceSection() != null) {
            return getSourceSection().getSource().getName() + ":" + getSourceSection().getStartLine();
        } else {
            return super.toString();
        }
    }

    @Override
    public SourceSection getSourceSection() {
        return this.sourceSection;
    }

    public void clearSourceSection() {
        this.sourceSection = null;
    }

    public void assignSourceSection(SourceSection source) {
        this.sourceSection = source;
    }

    @Override
    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    // NodeLibrary

    @ExportMessage
    boolean accepts(
                    @Cached(value = "this", adopt = false) PNode cachedNode) {
        return this == cachedNode;
    }

    @ExportMessage
    final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        // hasScope == isAdoptable(), getParent() != null is a fast way to check if adoptable.
        return this.getParent() != null;
    }

    @ExportMessage
    final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) throws UnsupportedMessageException {
        if (hasScope(frame)) {
            return PythonScopes.create(this, frame != null ? frame.materialize() : null);
        } else {
            throw UnsupportedMessageException.create();
        }
    }
}
