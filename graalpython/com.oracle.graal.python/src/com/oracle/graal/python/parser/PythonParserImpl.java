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
package com.oracle.graal.python.parser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;

import com.oracle.graal.python.parser.antlr.Builder;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class PythonParserImpl implements PythonParser {

    private static Python3Parser getPython3Parser(String string) {
        Python3Parser parser = Builder.createParser(CharStreams.fromString(string));
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }

    @Override
    @TruffleBoundary
    public Node parse(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
        // ANTLR parsing
        Python3Parser parser = getPython3Parser(source.getCharacters().toString());
        ParserRuleContext input;
        try {
            switch (mode) {
                case Eval:
                    input = parser.eval_input();
                    break;
                case File:
                    input = parser.file_input();
                    break;
                case InteractiveStatement:
                case InlineEvaluation:
                case Statement:
                    input = parser.single_input();
                    break;
                default:
                    throw new RuntimeException("unexpected mode: " + mode);
            }
        } catch (Exception e) {
            if ((mode == ParserMode.InteractiveStatement || mode == ParserMode.Statement) && e instanceof PIncompleteSourceException) {
                ((PIncompleteSourceException) e).setSource(source);
                throw e;
            } else if (mode == ParserMode.InlineEvaluation) {
                try {
                    parser.reset();
                    input = parser.eval_input();
                } catch (Exception e2) {
                    throw handleParserError(errors, source, e);
                }
            } else {
                throw handleParserError(errors, source, e);
            }
        }

        // prepare scope translator
        TranslationEnvironment environment = new TranslationEnvironment(errors.getLanguage());
        FrameDescriptor inlineLocals = mode == ParserMode.InlineEvaluation ? currentFrame.getFrameDescriptor() : null;
        ScopeTranslator<Object> defineScopes = new ScopeTranslator<>(errors, environment, source.isInteractive(), inlineLocals);
        // first pass of the scope translator -> define the scopes
        input.accept(defineScopes);
        // create frame slots for cell and free vars
        defineScopes.setFreeVarsInRootScope(currentFrame);
        defineScopes.createFrameSlotsForCellAndFreeVars();

        // create Truffle ASTs
        return PythonTreeTranslator.translate(errors, source.getName(), input, environment, source, mode);
    }

    @Override
    @TruffleBoundary
    public boolean isIdentifier(PythonCore core, String snippet) {
        Python3Parser parser = getPython3Parser(snippet);
        Python3Parser.AtomContext input;
        try {
            input = parser.atom();
        } catch (Exception e) {
            return false;
        }
        return input.NAME() != null;
    }

    @Override
    @TruffleBoundary
    public String unescapeJavaString(String str) {
        return PythonTreeTranslator.unescapeJavaString(str);
    }

    private static PException handleParserError(ParserErrorCallback errors, Source source, Exception e) {
        SourceSection section = PythonErrorStrategy.getPosition(source, e);
        throw errors.raiseInvalidSyntax(source, section);
    }
}
