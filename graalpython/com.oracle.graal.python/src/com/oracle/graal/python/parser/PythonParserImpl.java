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
package com.oracle.graal.python.parser;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.util.function.Consumer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;

import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.parser.ScopeTranslator.ScopeTranslatorFactory;
import com.oracle.graal.python.parser.antlr.Builder;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
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
    public Node parse(ParserMode mode, PythonCore core, Source source, Frame currentFrame) {
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
            if (mode == ParserMode.InteractiveStatement || mode == ParserMode.InlineEvaluation) {
                try {
                    parser.reset();
                    input = parser.eval_input();
                } catch (Exception e2) {
                    if (mode == ParserMode.InteractiveStatement && e instanceof PIncompleteSourceException) {
                        ((PIncompleteSourceException) e).setSource(source);
                        throw e;
                    }
                    throw handleParserError(core, source, e);
                }
            } else {
                throw handleParserError(core, source, e);
            }
        }
        // ensure builtins patches are loaded before parsing
        core.loadBuiltinsPatches();
        TranslationEnvironment environment = new TranslationEnvironment(core.getLanguage());
        Node result;
        Consumer<TranslationEnvironment> environmentConsumer = (env) -> env.setFreeVarsInRootScope(currentFrame);
        if (mode == ParserMode.InlineEvaluation) {
            ScopeTranslatorFactory scopeTranslator = (env, trackCells) -> new InlineScopeTranslator<>(core, env, currentFrame.getFrameDescriptor(), trackCells);
            ScopeTranslator.accept(input, environment, scopeTranslator, environmentConsumer);
            result = new PythonInlineTreeTranslator(core, source.getName(), input, environment, source).getTranslationResult();
        } else {
            ScopeTranslatorFactory scopeTranslator = (env, trackCells) -> new ScopeTranslator<>(core, env, source.isInteractive(), trackCells);
            ScopeTranslator.accept(input, environment, scopeTranslator, environmentConsumer);
            result = new PythonTreeTranslator(core, source.getName(), input, environment, source).getTranslationResult();
        }
        return result;
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

    private static PException handleParserError(PythonCore core, Source source, Exception e) {
        SourceSection section = PythonErrorStrategy.getPosition(source, e);
        Node location = new Node() {
            @Override
            public SourceSection getSourceSection() {
                return section;
            }
        };
        PBaseException instance;
        PythonClass exceptionType = core.getErrorClass(SyntaxError);
        instance = PythonObjectFactory.get().createBaseException(exceptionType, "invalid syntax", new Object[0]);
        String path = source.getPath();
        instance.setAttribute("filename", path != null ? path : source.getName() != null ? source.getName() : "<string>");
        instance.setAttribute("text", section.isAvailable() ? source.getCharacters(section.getStartLine()) : "");
        instance.setAttribute("lineno", section.getStartLine());
        instance.setAttribute("offset", section.getStartColumn());
        instance.setAttribute("msg", section.getCharIndex() == source.getLength() ? "unexpected EOF while parsing" : "invalid syntax");
        throw core.raise(instance, location);
    }
}
