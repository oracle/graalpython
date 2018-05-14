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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.ParserRuleContext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.parser.antlr.Builder;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParseResult;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class PythonParserImpl implements PythonParser {
    private static final Map<String, ParserRuleContext> cachedParseTrees = new HashMap<>();

    private static Python3Parser getPython3Parser(CodePointCharStream fromString) {
        Python3Parser parser = new Builder.Parser(fromString).build();
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }

    private static Python3Parser getPython3Parser(String string) {
        Python3Parser parser = new Builder.Parser(string).build();
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }

    @TruffleBoundary
    private static ParserRuleContext preParseWithAntlr(PythonCore core, Source source) {
        String path = source.getURI().toString();
        String[] pathParts = path.split(Pattern.quote(PythonCore.FILE_SEPARATOR));
        String fileDirAndName = pathParts[pathParts.length - 2] + PythonCore.FILE_SEPARATOR + pathParts[pathParts.length - 1];
        CodePointCharStream fromString = CharStreams.fromString(source.getCharacters().toString(), fileDirAndName);
        Python3Parser parser = getPython3Parser(fromString);
        ParserRuleContext input;
        if (!core.isInitialized()) {
            input = cachedParseTrees.get(fileDirAndName);
            if (input == null) {
                input = parser.file_input();
                cachedParseTrees.put(fileDirAndName, input);
            }
        } else {
            try {
                if (source.isInteractive()) {
                    input = parser.single_input();
                } else {
                    input = parser.file_input();
                }
            } catch (Throwable e) {
                try {
                    parser.reset();
                    input = parser.eval_input();
                } catch (Throwable e2) {
                    if (source.isInteractive() && e instanceof PIncompleteSourceException) {
                        ((PIncompleteSourceException) e).setSource(source);
                        throw e;
                    }
                    throw core.raise(SyntaxError, e.getMessage());
                }
            }
        }
        return input;
    }

    @TruffleBoundary
    private static ParserRuleContext preParseInlineWithAntlr(PythonCore core, Source source) {
        Python3Parser parser = getPython3Parser(source.getCharacters().toString());
        ParserRuleContext input;
        try {
            input = parser.single_input();
        } catch (Throwable e) {
            try {
                parser.reset();
                input = parser.eval_input();
            } catch (Throwable e2) {
                throw core.raise(SyntaxError, e.getMessage());
            }
        }
        return input;
    }

    private static Node getLocation(Source source, int line) {
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
    @TruffleBoundary
    public PythonParseResult parse(PythonCore core, Source source) {
        return translateParseResult(core, source.getName(), preParseWithAntlr(core, source), source);
    }

    @Override
    @TruffleBoundary
    public PNode parseInline(PythonCore core, Source source, Frame curFrame) {
        return translateInlineParseResult(core, source.getName(), preParseInlineWithAntlr(core, source), source, curFrame);
    }

    @Override
    @TruffleBoundary
    public PythonParseResult parseEval(PythonCore core, String expression, String filename) {
        Python3Parser parser = getPython3Parser(expression);
        ParserRuleContext input;
        try {
            input = parser.eval_input();
        } catch (Throwable e) {
            throw handleParserError(core, e);
        }
        Source source = Source.newBuilder(expression).name(filename).mimeType(PythonLanguage.MIME_TYPE).build();
        Frame callerFrame = Truffle.getRuntime().getCallerFrame().getFrame(FrameInstance.FrameAccess.READ_ONLY);
        return translateParseResult(core, filename, input, source, callerFrame);
    }

    @Override
    @TruffleBoundary
    public PythonParseResult parseExec(PythonCore core, String expression, String filename) {
        Python3Parser parser = getPython3Parser(expression);
        ParserRuleContext input;
        try {
            input = parser.file_input();
        } catch (Throwable e) {
            throw handleParserError(core, e);
        }
        Source source = Source.newBuilder(expression).name(filename).mimeType(PythonLanguage.MIME_TYPE).build();
        return translateParseResult(core, filename, input, source);
    }

    @Override
    @TruffleBoundary
    public PythonParseResult parseSingle(PythonCore core, String expression, String filename) {
        Python3Parser parser = getPython3Parser(expression);
        ParserRuleContext input;
        try {
            input = parser.single_input();
        } catch (Throwable e) {
            throw handleParserError(core, e);
        }
        Source source = Source.newBuilder(expression).name(filename).mimeType(PythonLanguage.MIME_TYPE).build();
        return translateParseResult(core, filename, input, source);
    }

    @Override
    @TruffleBoundary
    public boolean isIdentifier(PythonCore core, String snippet) {
        Python3Parser parser = getPython3Parser(snippet);
        Python3Parser.AtomContext input;
        try {
            input = parser.atom();
        } catch (Throwable e) {
            return false;
        }
        return input.NAME() != null;
    }

    private static PException handleParserError(PythonCore core, Throwable e) {
        return core.raise(SyntaxError, e.getMessage());
    }

    private static PythonParseResult translateParseResult(PythonCore core, String name, ParserRuleContext input, Source source) {
        return translateParseResult(core, name, input, source, null);
    }

    private static PythonParseResult translateParseResult(PythonCore core, String name, ParserRuleContext input, Source source, Frame frame) {
        TranslationEnvironment environment = new TranslationEnvironment(core.getLanguage());
        ScopeTranslator.accept(input, environment,
                        (env, trackCells) -> new ScopeTranslator<>(core, env, source.isInteractive(), trackCells),
                        (env) -> env.setFreeVarsInRootScope(frame));

        PythonTreeTranslator treeTranslator = new PythonTreeTranslator(core, name, input, environment, source);
        return treeTranslator.getTranslationResult();
    }

    private static PNode translateInlineParseResult(PythonCore core, String name, ParserRuleContext input, Source source, Frame currentFrame) {
        TranslationEnvironment environment = new TranslationEnvironment(core.getLanguage());
        ScopeTranslator.accept(input, environment,
                        (env, trackCells) -> new InlineScopeTranslator<>(core, env, currentFrame.getFrameDescriptor(), trackCells),
                        (env) -> env.setFreeVarsInRootScope(currentFrame));

        PythonInlineTreeTranslator pythonInlineTreeTranslator = new PythonInlineTreeTranslator(core, name, input, environment, source);
        return pythonInlineTreeTranslator.getTranslationResult();
    }

}
