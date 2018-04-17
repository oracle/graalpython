/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public interface PythonParser {
    /**
     * Parse the source and return a {@link PythonParseResult} that includes a
     * {@link com.oracle.truffle.api.nodes.RootNode} ready for interpretation. This method tries
     * both file ('exec') and expression ('eval') inputs.
     */
    PythonParseResult parse(PythonCore core, Source source);

    /**
     * Parse the source in the given environment and return a {@link PNode}. This allows to parse
     * code and use it for inline evaluation like in the debugger. Therefore, you need to provide
     * the environment, i.e., the scope, for the code. This method uses single input if the source
     * is interactive and additionally tries file ('exec') and expression ('eval') inputs as
     * fallback.
     */
    PNode parseInline(PythonCore core, Source source, Frame curFrame);

    /**
     * Parse an expression through Python's compile with mode='eval'.
     */
    PythonParseResult parseEval(PythonCore core, String expression, String filename);

    /**
     * Parse code through Python's compile with mode='exec'.
     */
    PythonParseResult parseExec(PythonCore core, String code, String filename);

    /**
     * Parse an expression through Python's compile with mode='single'.
     */
    PythonParseResult parseSingle(PythonCore core, String snippet, String filename);

    /**
     * Check if an expression can be parsed as an identifier
     */
    boolean isIdentifier(PythonCore core, String snippet);

    /**
     * Runtime exception used to indicate incomplete source code during parsing.
     */
    public static class PIncompleteSourceException extends RuntimeException implements TruffleException {

        private static final long serialVersionUID = 4393080397807767467L;

        private Source source;
        private final int line;

        public PIncompleteSourceException(String message, Throwable cause, int line) {
            super(message, cause);
            this.line = line;
        }

        @Override
        public Node getLocation() {
            if (line <= 0 || line > source.getLineCount()) {
                return null;
            } else {
                SourceSection section = source.createSection(line);
                return new Node() {
                    @Override
                    public SourceSection getSourceSection() {
                        return section;
                    }
                };
            }
        }

        @Override
        public boolean isSyntaxError() {
            return true;
        }

        @Override
        public boolean isIncompleteSource() {
            return true;
        }

        public void setSource(Source source) {
            this.source = source;
        }
    }
}
