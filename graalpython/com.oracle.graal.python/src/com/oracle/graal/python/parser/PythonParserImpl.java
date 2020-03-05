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
package com.oracle.graal.python.parser;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.oracle.graal.python.parser.antlr.DescriptiveBailErrorListener;
import com.oracle.graal.python.parser.antlr.Python3Lexer;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.sst.SSTDeserializer;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.SSTSerializerVisitor;
import com.oracle.graal.python.parser.sst.SerializationUtils;
import com.oracle.graal.python.parser.sst.StringUtils;
import com.oracle.graal.python.runtime.PythonCodeSerializer;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.antlr.v4.runtime.Token;

public final class PythonParserImpl implements PythonParser, PythonCodeSerializer {

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
        Python3Lexer lexer;
        try {
            lexer = source.getPath() == null
                            ? new Python3Lexer(CharStreams.fromString(source.getCharacters().toString()))
                            : new Python3Lexer(CharStreams.fromFileName(source.getPath()));
        } catch (IOException ex) {
            lexer = new Python3Lexer(CharStreams.fromString(source.getCharacters().toString()));
        }
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

    @Override
    public byte[] serialize(Source source) {
        // System.out.println("serialize: " + source.getPath() + " is the last parsing source: " +
        // source.equals(lastParsing.source));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(source.getLength() * 2);
        DataOutputStream dos = new DataOutputStream(baos);
        if (!source.equals(lastParsing.source)) {
            // we need to parse the source again, but this should not happened in current
            // implementation
            parseN(ParserMode.File, PythonLanguage.getCore(), source, null);
        }
        try {
            dos.writeByte(SerializationUtils.VERSION);
            byte[] bytes = source.getCharacters().toString().getBytes();
            dos.writeInt(bytes.length);
            dos.write(bytes);
            dos.write(lastParsing.serializedGlobalScope);
            lastParsing.antlrResult.accept(new SSTSerializerVisitor(dos));
            dos.close();
        } catch (IOException e) {
            throw PythonLanguage.getCore().raise(PythonBuiltinClassType.ValueError, "Is not possible save data during serialization.");
        }

        return baos.toByteArray();
    }

    @Override
    public RootNode deserialize(TruffleFile tFile, byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        String code = null;
        ScopeInfo globalScope = null;
        SSTNode sstNode = null;
        Source source = null;
        try {
            byte version = dis.readByte();
            if (version != SerializationUtils.VERSION) {
                assert true : "It looks like that there is used old version of data serialization in .pyc files. It can happen, if you use developement vertsion of GraalPython. Remove them.";
                throw PythonLanguage.getCore().raise(PythonBuiltinClassType.ValueError, "Bad data of serialization");
            }
            int len = dis.readInt();
            byte[] bytes = new byte[len];
            int readedLen = dis.read(bytes);
            code = new String(bytes);
            globalScope = ScopeInfo.read(dis, null);
            source = tFile == null
                            ? Source.newBuilder(PythonLanguage.ID, code, "<expression>").build()
                            : Source.newBuilder(PythonLanguage.ID, tFile).content(code).build();
            sstNode = new SSTDeserializer(dis, globalScope, source).readNode();
        } catch (IOException e) {
            throw PythonLanguage.getCore().raise(PythonBuiltinClassType.ValueError, "Is not possible get correct data from " + tFile.getPath());
        }
        PythonCore core = PythonLanguage.getCore();
        PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(core, source);
        sstFactory.getScopeEnvironment().setGlobalScope(globalScope);
        try {
            return (RootNode) sstFactory.createParserResult(sstNode, ParserMode.File, null);
        } catch (Exception e) {
            throw handleParserError(core, source, e, true);
        }
    }

    byte[] serializeScope(ScopeInfo scope) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            ScopeInfo.write(dos, scope);
            dos.close();
        } catch (IOException e) {
            throw PythonLanguage.getCore().raise(PythonBuiltinClassType.ValueError, "Is not possible save datad during serialization.");
        }
        return baos.toByteArray();
    }

    private static class CacheItem {
        Source source;
        SSTNode antlrResult;
        ScopeInfo globalScope;
        byte[] serializedGlobalScope;
    }

    private final CacheItem lastParsing = new CacheItem();

    public ScopeInfo getLastGlobaScope() {
        return lastParsing.globalScope;
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

    private CacheItem parseWithANTLR(ParserMode mode, ParserErrorCallback errors, PythonSSTNodeFactory sstFactory, Source source, Frame currentFrame) {
        FrameDescriptor inlineLocals = mode == ParserMode.InlineEvaluation ? currentFrame.getFrameDescriptor() : null;
        // ANTLR parsing
        Python3Parser parser = getPython3Parser(source, errors);
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

        lastParsing.globalScope = sstFactory.getScopeEnvironment().getGlobalScope();
        lastParsing.antlrResult = parserSSTResult;
        lastParsing.source = source;
        lastParsing.serializedGlobalScope = serializeScope(lastParsing.globalScope);
        return lastParsing;
    }

    @TruffleBoundary
    public Node parseN(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
        PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(errors, source);
        CacheItem parserSSTResult = parseWithANTLR(mode, errors, sstFactory, source, currentFrame);
        try {
            return sstFactory.createParserResult(parserSSTResult.antlrResult, mode, currentFrame);
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
