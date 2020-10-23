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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public interface PythonParser {

    public enum ParserMode {
        /**
         * Parse the given input as a single statement, as required for Python's compile with
         * mode='single'.
         */
        Statement,
        /**
         * Parse the given input as a single statement or expression, and throw
         * {@link PIncompleteSourceException} if appropriate. Return a {@link RootNode} that is
         * ready for interpretation. This method tries both file ('exec') and expression ('eval')
         * inputs.
         */
        InteractiveStatement,
        /**
         * Parse the given input as a single statement or expression and return a {@link PNode}.
         * This allows to parse code and use it for inline evaluation like in the debugger.
         * Therefore, you need to provide the environment, i.e., the scope, for the code. This
         * method uses single input if the source is interactive and additionally tries file
         * ('exec') and expression ('eval') inputs as fallback.
         */
        InlineEvaluation,
        /**
         * Parse the given input as a file, as required for Python's compile with mode='exec'.
         */
        File,
        /**
         * Parse the given input as an expression, as required for Python's compile with
         * mode='eval'.
         */
        Eval,
        /**
         * Used for parsing expressions inside f-strings. Such expression should have the same scope
         * as the f-string itself.
         */
        FStringExpression,
        /**
         * Used for building Truffle tree from deserialized ANTLR parser result. The result
         * expression or statement is not wrapped in any expression.
         */
        Deserialization,
        /**
         * Handle situation, when there is request to parse with arguments from TruffleAPI.
         */
        WithArguments;
    }

    enum ErrorType {
        Generic,
        Indentation,
        Tab
    }

    public interface ParserErrorCallback {
        RuntimeException raise(PythonBuiltinClassType type, String message, Object... args);

        default RuntimeException raiseInvalidSyntax(Source source, SourceSection section, String message, Object... arguments) {
            return raiseInvalidSyntax(ErrorType.Generic, source, section, message, arguments);
        }

        RuntimeException raiseInvalidSyntax(ErrorType type, Source source, SourceSection section, String message, Object... arguments);

        default RuntimeException raiseInvalidSyntax(Node location, String message, Object... arguments) {
            return raiseInvalidSyntax(ErrorType.Generic, location, message, arguments);
        }

        RuntimeException raiseInvalidSyntax(ErrorType type, Node location, String message, Object... arguments);

        default RuntimeException raiseInvalidSyntax(Source source, SourceSection section) {
            return raiseInvalidSyntax(source, section, ErrorMessages.INVALID_SYNTAX, PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        void warn(PythonBuiltinClassType type, String format, Object... args);

        PythonLanguage getLanguage();
    }

    /**
     * Parses the given {@link Source} object according to the requested {@link ParserMode}. Also
     * according the TruffleLanguage.ParsingRequest can be influence the result of parsing if there
     * are provided argumentsNames.
     *
     * @return {@link PNode} for {@link ParserMode#InlineEvaluation}, and otherwise {@link RootNode}
     */
    Node parse(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame, String[] arguments);

    /**
     * Check if an expression can be parsed as an identifier
     */
    boolean isIdentifier(PythonCore core, String snippet);

    /**
     * Unescape Python escapes from a Java string
     */
    public abstract String unescapeJavaString(PythonCore core, String str);

    /**
     * Runtime exception used to indicate incomplete source code during parsing.
     */
    @ExportLibrary(InteropLibrary.class)
    class PIncompleteSourceException extends AbstractTruffleException {

        private static final long serialVersionUID = 4393080397807767467L;

        private Source source;
        private final int line;

        public PIncompleteSourceException(String message, Throwable cause, int line) {
            super(message, cause, UNLIMITED_STACK_TRACE, null);
            this.line = line;
        }

        public void setSource(Source source) {
            this.source = source;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isException() {
            return true;
        }

        @ExportMessage
        RuntimeException throwException() {
            throw this;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.PARSE_ERROR;
        }

        @ExportMessage
        boolean isExceptionIncompleteSource() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasExceptionMessage() {
            return true;
        }

        @ExportMessage
        String getExceptionMessage() {
            return getMessage();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage(name = "getSourceLocation")
        @TruffleBoundary
        SourceSection getExceptionSourceLocation() {
            if (line > 0 && line < source.getLineCount()) {
                return source.createSection(line);
            }
            return source.createUnavailableSection();
        }
    }
}
