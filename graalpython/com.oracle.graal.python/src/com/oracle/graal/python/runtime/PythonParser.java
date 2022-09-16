/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
// skip GIL
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.runtime.exception.PIncompleteSourceException;
import com.oracle.truffle.api.nodes.RootNode;

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
         * Parse the given input as required for Python's compile with mode='func_type'.
         */
        FuncType,
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

}
