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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.oracle.graal.python.parser.antlr.DescriptiveBailErrorListener;
import com.oracle.graal.python.parser.antlr.Python3Lexer;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.StringUtils;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.antlr.v4.runtime.Token;

public final class PythonParserImpl implements PythonParser {

    private final boolean logFiles;
    private final int timeStatistics;
    private long timeInParser = 0;
    private long numberOfFiles = 0;

    public static final DescriptiveBailErrorListener ERROR_LISTENER = new DescriptiveBailErrorListener();

    public PythonParserImpl(Env env) {
        this.logFiles = env.getOptions().get(PythonOptions.ParserLogFiles);
        this.timeStatistics = env.getOptions().get(PythonOptions.ParserStatistics);
    }

    private static Python3Parser getPython3Parser(Source source, ParserErrorCallback errors) {
        Python3Lexer lexer = new Python3Lexer(CharStreams.fromString(source.getCharacters().toString()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ERROR_LISTENER);
        Python3Parser parser = new Python3Parser(new CommonTokenStream(lexer));
        parser.setBuildParseTree(false);
        parser.setFactory(new PythonSSTNodeFactory(errors, source));
        parser.removeErrorListeners();
        parser.addErrorListener(ERROR_LISTENER);
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }

    private ScopeInfo lastGlobalScope;

    public ScopeInfo getLastGlobaScope() {
        return lastGlobalScope;
    }

    @Override
    public Node parse(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
        if (logFiles) {
            if (source.getPath() == null) {
                System.out.println("Parsing source without path " + source.getCharacters().length());
                CharSequence chars = source.getCharacters();
                System.out.println(chars.length() < 200
                                ? chars.toString()
                                : chars.subSequence(0, 197).toString() + "...");
            } else {
                System.out.print("Parsing: " + source.getPath());
            }
        }

        Node result;
        if (timeStatistics <= 0) {
            result = parseN(mode, errors, source, currentFrame);
        } else {
            long start = System.currentTimeMillis();
            result = parseN(mode, errors, source, currentFrame);
            long end = System.currentTimeMillis();
            if (timeStatistics > 0) {
                timeInParser = timeInParser + (end - start);
                if (logFiles) {
                    System.out.println(" took " + timeInParser + "ms.");
                }
                numberOfFiles++;
                if (numberOfFiles % timeStatistics == 0) {
                    System.out.println("Parsed " + numberOfFiles + " in " + timeInParser + "ms.");
                }
            }
        }
        return result;
    }

    @TruffleBoundary
    public Node parseN(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
        FrameDescriptor inlineLocals = mode == ParserMode.InlineEvaluation ? currentFrame.getFrameDescriptor() : null;
        // ANTLR parsing
        Python3Parser parser = getPython3Parser(source, errors);
        PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(errors, source);
        parser.setFactory(sstFactory);
        SSTNode parserSSTResult = null;

        try {
            switch (mode) {
                case Eval:
                    parserSSTResult = parser.eval_input().result;
                    break;
                case File:
                    parserSSTResult = parser.file_input().result;
                    break;
                case InteractiveStatement:
                case InlineEvaluation:
                case Statement:
                    parserSSTResult = parser.single_input(source.isInteractive(), inlineLocals).result;
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
                    parserSSTResult = parser.eval_input().result;
                } catch (Exception e2) {
                    throw handleParserError(errors, source, e, !(mode == ParserMode.InteractiveStatement || mode == ParserMode.Statement));
                }
            } else {
                throw handleParserError(errors, source, e, !(mode == ParserMode.InteractiveStatement || mode == ParserMode.Statement));
            }
        }

        lastGlobalScope = sstFactory.getScopeEnvironment().getGlobalScope();
        try {
            return sstFactory.createParserResult(parserSSTResult, mode, currentFrame);
        } catch (Exception e) {
            throw handleParserError(errors, source, e, !(mode == ParserMode.InteractiveStatement || mode == ParserMode.Statement));
        }
    }

    @Override
    @TruffleBoundary
    public boolean isIdentifier(PythonCore core, String snippet) {
        if (snippet.length() != snippet.trim().length()) {
            // identifier cannot start or end with any whitspace
            return false;
        }
        Python3Lexer lexer = new Python3Lexer(CharStreams.fromString(snippet));
        Token t = lexer.nextToken();
        if (t.getType() == Python3Lexer.NAME) {
            // the first token is identifier
            t = lexer.nextToken();
            if (t.getType() == Python3Lexer.NEWLINE) {
                // lexer alwayes add new line at the end
                t = lexer.nextToken();
                if (t.getType() == Python3Lexer.EOF) {
                    // now we are sure that this is identifer
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @TruffleBoundary
    public String unescapeJavaString(String str) {
        return StringUtils.unescapeJavaString(str);
    }

    private static PException handleParserError(ParserErrorCallback errors, Source source, Exception e, boolean showBadLine) {
        if (e instanceof TruffleException && ((TruffleException) e).isSyntaxError() && e instanceof PException) {
            if (!showBadLine) {
                PBaseException instance = ((PException) e).getExceptionObject();
                // In cpython shell the line with the error is not displayed, so we should do it in
                // the same way.
                // This rely on implementation in traceback.py file. See comment in
                // Python3Core.raiseInvalidSyntax method
                instance.setAttribute("text", PNone.NONE);
            }
            return (PException) e;
        }

        SourceSection section = showBadLine ? PythonErrorStrategy.getPosition(source, e) : source.createUnavailableSection();
        // from parser we are getting RuntimeExceptions
        String message = e instanceof RuntimeException && e.getMessage() != null ? e.getMessage() : "invalid syntax";
        throw errors.raiseInvalidSyntax(source, section, message);
    }
}
