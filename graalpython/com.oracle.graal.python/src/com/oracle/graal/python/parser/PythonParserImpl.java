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
package com.oracle.graal.python.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.frame.MaterializedFrame;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonFileDetector;
import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.parser.PythonSSTNodeFactory.FStringExprParser;
import com.oracle.graal.python.parser.antlr.DescriptiveBailErrorListener;
import com.oracle.graal.python.parser.antlr.Python3Lexer;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.sst.BlockSSTNode;
import com.oracle.graal.python.parser.sst.SSTDeserializer;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.SSTNodeUtils;
import com.oracle.graal.python.parser.sst.SSTNodeWithScope;
import com.oracle.graal.python.parser.sst.SSTNodeWithScopeFinder;
import com.oracle.graal.python.parser.sst.SSTSerializerVisitor;
import com.oracle.graal.python.parser.sst.SerializationUtils;
import com.oracle.graal.python.runtime.PythonCodeSerializer;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;

public final class PythonParserImpl implements PythonParser, PythonCodeSerializer, FStringExprParser {

    private static final String HOME_PREFIX = "%/";
    private final boolean logFiles;
    private final int timeStatistics;
    private long timeInParser = 0;
    private long numberOfFiles = 0;
    private static final boolean IN_IMAGE_BUILD_TIME = ImageInfo.inImageBuildtimeCode();

    private static final Pattern START_INDENT_REGEX = Pattern.compile("^([ \t]+)[^#\r\n\f\t ]");

    public static final DescriptiveBailErrorListener ERROR_LISTENER = new DescriptiveBailErrorListener();

    public PythonParserImpl(Env env) {
        this.logFiles = env.getOptions().get(PythonOptions.ParserLogFiles);
        this.timeStatistics = env.getOptions().get(PythonOptions.ParserStatistics);
    }

    private static Python3Parser getPython3Parser(String sourceText) {
        Python3Lexer lexer = new Python3Lexer(CharStreams.fromString(sourceText));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ERROR_LISTENER);
        Python3Parser parser = new Python3Parser(new CommonTokenStream(lexer));
        parser.setBuildParseTree(false);
        parser.removeErrorListeners();
        parser.addErrorListener(ERROR_LISTENER);
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }

    @Override
    public SSTNode parseExpression(ParserErrorCallback errorCallback, String text, PythonSSTNodeFactory nodeFactory, boolean fromInteractiveSource) {
        Source source = Source.newBuilder(PythonLanguage.ID, text, "<fstring-expr>").interactive(fromInteractiveSource).build();
        return parseWithANTLR(ParserMode.FStringExpression, 0, errorCallback.getContext(), nodeFactory, source, null,
                        null).antlrResult;
    }

    public static byte[] serialize(SourceSection section, SSTNode node, ScopeInfo scope, boolean isModule) {
        Source source = section.getSource();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeByte(SerializationUtils.VERSION);
            dos.writeUTF(source.getName() == null ? "" : encodeHome(source.getName()));
            dos.writeUTF(source.getPath() == null ? "" : encodeHome(source.getPath()));
            byte[] bytes = section.getCharacters().toString().getBytes(StandardCharsets.UTF_8);
            dos.writeInt(bytes.length);
            dos.write(bytes);
            ScopeInfo.write(dos, scope);
            dos.writeInt(isModule ? 0 : node.getStartOffset());
            node.accept(new SSTSerializerVisitor(dos));
            dos.close();
        } catch (IOException e) {
            throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.ValueError, "Error during serialization: %s", e.getMessage());
        }

        return baos.toByteArray();
    }

    private static String encodeHome(String path) {
        String home = PythonLanguage.get(null).getHome();  // behind @TruffleBoundary
        if (path.startsWith(home)) {
            return HOME_PREFIX + path.substring(home.length());
        }
        return path;
    }

    private static String decodeHome(String path) {
        if (path.startsWith(HOME_PREFIX)) {
            String home = PythonLanguage.get(null).getHome();  // behind @TruffleBoundary
            return home + path.substring(HOME_PREFIX.length());
        }
        return path;
    }

    @Override
    @TruffleBoundary
    public byte[] serialize(ParserErrorCallback errorCallback, RootNode rootNode) {
        Source source = rootNode.getSourceSection().getSource();
        assert source != null;
        CacheItem lastParserResult = cachedLastAntlrResult;
        if (source != lastParserResult.source && !source.equals(lastParserResult.source)) {
            // we need to parse the source again
            PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(errorCallback, source, this);
            lastParserResult = parseWithANTLR(ParserMode.File, 0, errorCallback, sstFactory, source, null, null);
        }
        if (rootNode instanceof ModuleRootNode) {
            // serialize whole module
            return serialize(rootNode.getSourceSection(), lastParserResult.antlrResult, lastParserResult.globalScope, true);
        } else {
            // serialize just the part
            SSTNodeWithScopeFinder finder = new SSTNodeWithScopeFinder(rootNode.getSourceSection().getCharIndex(), rootNode.getSourceSection().getCharEndIndex());
            SSTNodeWithScope rootSST = lastParserResult.antlrResult.accept(finder);
            // store with parent scope
            return serialize(rootNode.getSourceSection(), rootSST, rootSST.getScope().getParent(), false);
        }
    }

    @Override
    public RootNode deserialize(ParserErrorCallback errorCallback, byte[] data) {
        return deserialize(errorCallback, data, null, null);
    }

    @Override
    @TruffleBoundary
    public RootNode deserialize(ParserErrorCallback errorCallback, byte[] data, String[] cellvars, String[] freevars) {
        assert data.length > 0 : "should be caught earlier";

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        ScopeInfo globalScope;
        Source source;
        SSTNode sstNode;
        try {
            // Just to be sure that the serialization version is ok.
            byte version = dis.readByte();
            if (version != SerializationUtils.VERSION) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.ValueError, "Bad data of serialization");
            }
            String name = decodeHome(dis.readUTF()).intern();
            String path = decodeHome(dis.readUTF()).intern();
            byte[] bytes = new byte[dis.readInt()];
            dis.readFully(bytes);
            String contents = new String(bytes, StandardCharsets.UTF_8);
            globalScope = ScopeInfo.read(dis, null);
            int offset = dis.readInt();

            SourceBuilder sb;
            if (path.isEmpty() || offset != 0) {
                sb = Source.newBuilder(PythonLanguage.ID, contents, name);
            } else {
                sb = Source.newBuilder(PythonLanguage.ID, PythonContext.get(null).getEnv().getPublicTruffleFile(path)).content(contents).name(name);
            }
            if (!errorCallback.getContext().getCore().isCoreInitialized()) {
                sb = sb.internal(true).cached(false);
            }
            source = sb.build();
            sstNode = new SSTDeserializer(dis, globalScope, offset).readNode();
        } catch (IOException e) {
            throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.ValueError, "Is not possible get correct bytecode data %s, %s", e.getClass().getSimpleName(), e.getMessage());
        }
        if ((cellvars != null || freevars != null) && (sstNode instanceof SSTNodeWithScope)) {
            ScopeInfo rootScope = ((SSTNodeWithScope) sstNode).getScope();
            if (cellvars != null) {
                rootScope.setCellVars(cellvars);
            }
            if (freevars != null) {
                rootScope.setFreeVars(freevars);
            }
        }
        PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(errorCallback, source, this);

        sstFactory.getScopeEnvironment().setGlobalScope(globalScope);
        ParserMode mode = sstNode instanceof BlockSSTNode ? ParserMode.File : ParserMode.Deserialization;
        try {
            Node result = sstFactory.createParserResult(sstNode, mode, null, null);
            if (mode == ParserMode.Deserialization) {
                // find function RootNode
                final Node[] fromVisitor = new Node[1];
                result.accept((Node node) -> {
                    if (node instanceof GeneratorFunctionDefinitionNode) {
                        fromVisitor[0] = ((GeneratorFunctionDefinitionNode) node).getGeneratorFunctionRootNode(PythonLanguage.get(node));
                        return false;
                    } else if (node instanceof FunctionDefinitionNode) {
                        fromVisitor[0] = ((FunctionDefinitionNode) node).getFunctionRoot();
                        return false;
                    }
                    return true;
                });
                result = fromVisitor[0];
            }
            ((PRootNode) result).setCode(data);
            return (RootNode) result;
        } catch (Exception e) {
            throw handleParserError(errorCallback, source, e);
        }
    }

    public static class CacheItem {
        Source source;
        SSTNode antlrResult;
        ScopeInfo globalScope;

        public CacheItem(Source source, SSTNode antlrResult, ScopeInfo globalScope) {
            this.source = source;
            this.antlrResult = antlrResult;
            this.globalScope = globalScope;
        }

        public Source getSource() {
            return source;
        }

        public SSTNode getAntlrResult() {
            return antlrResult;
        }

        public ScopeInfo getGlobalScope() {
            return globalScope;
        }

        public CacheItem copy() {
            return new CacheItem(source, antlrResult, globalScope);
        }

    }

    private final CacheItem cachedLastAntlrResult = new CacheItem(null, null, null);

    public ScopeInfo getLastGlobaScope() {
        return cachedLastAntlrResult.globalScope;
    }

    // for test purposes.
    public SSTNode getLastSST() {
        return cachedLastAntlrResult.antlrResult;
    }

    @Override
    public Node parse(ParserMode mode, int optimizeLevel, ParserErrorCallback errors, Source source, Frame currentFrame, String[] argumentNames) {
        if (logFiles) {
            if (source.getPath() == null) {
                System.out.println("Parsing source without path " + source.getCharacters().length());
                CharSequence chars = source.getCharacters();
                System.out.println(chars.length() < 200
                                ? chars.toString()
                                : chars.subSequence(0, 197).toString() + "...");
            } else {
                System.out.println("Parsing: " + source.getPath());
            }
        }

        Node result;
        MaterializedFrame materializedFrame = currentFrame != null ? currentFrame.materialize() : null;
        if (timeStatistics <= 0) {
            result = parseN(mode, optimizeLevel, errors, source, materializedFrame, argumentNames);
        } else {
            long start = System.currentTimeMillis();
            result = parseN(mode, optimizeLevel, errors, source, materializedFrame, argumentNames);
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

    public CacheItem parseWithANTLR(ParserMode mode, int optimizeLevel, ParserErrorCallback errors, PythonSSTNodeFactory sstFactory, Source source, Frame currentFrame, String[] argumentNames) {
        FrameDescriptor inlineLocals = mode == ParserMode.InlineEvaluation ? currentFrame.getFrameDescriptor() : null;
        String sourceText = source.getCharacters().toString();
        // Preprocessing

        // Check that declared encoding (if any) is valid. The file detector picks an encoding
        // for the file, but it doesn't have a means of communicating that the declared encoding
        // wasn't valid or supported, so in that case it defaults to Latin-1 and we have to
        // recheck it here.
        // msimacek: The encoding check should happen only when the source encoding was
        // determined by PythonFileDetector. But we currently have no way to tell, so we
        // assume that it is the case when it is a file.
        if (source.getURI().getScheme() != null && source.getURI().getScheme().equals("file")) {
            try {
                PythonFileDetector.findEncodingStrict(sourceText);
            } catch (PythonFileDetector.InvalidEncodingException e) {
                throw errors.raiseInvalidSyntax(source, source.createUnavailableSection(), "encoding problem: %s", e.getEncodingName());
            }
        }
        // We need to reject inputs starting with indent, but doing it in ANTLR is expensive, so we
        // do it here manually
        Matcher matcher = START_INDENT_REGEX.matcher(sourceText);
        if (matcher.find()) {
            throw errors.raiseInvalidSyntax(ErrorType.Indentation, source, source.createSection(0, matcher.end(1)), "unexpected indent");
        }
        // ANTLR parsing
        Python3Parser parser = getPython3Parser(sourceText);
        parser.setFactory(sstFactory);
        parser.setParserMode(mode);
        parser.setOptimizeLevel(optimizeLevel);
        SSTNode parserSSTResult = null;

        try {
            switch (mode) {
                case Eval:
                case FStringExpression:
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
                case WithArguments:
                    // at the first, create global scope
                    ScopeInfo globalScope = sstFactory.getScopeEnvironment().pushScope("module", ScopeInfo.ScopeKind.Module, currentFrame == null ? null : currentFrame.getFrameDescriptor());
                    // we expect that the source is the body of the result function
                    parserSSTResult = parser.withArguments_input(false, new FrameDescriptor()).result;
                    // wrap the result with function definition
                    ScopeInfo functionScope = globalScope.getFirstChildScope();
                    parserSSTResult = SSTNodeUtils.createFunctionDefWithArguments(source.getName(), functionScope, parserSSTResult, argumentNames);
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
                    throw handleParserError(errors, source, e);
                }
            } else {
                throw handleParserError(errors, source, e);
            }
        }

        if (!IN_IMAGE_BUILD_TIME) {
            cachedLastAntlrResult.globalScope = sstFactory.getScopeEnvironment().getGlobalScope();
            cachedLastAntlrResult.antlrResult = parserSSTResult;
            cachedLastAntlrResult.source = source;
            return cachedLastAntlrResult;
        } else {
            return new CacheItem(source, parserSSTResult, sstFactory.getScopeEnvironment().getGlobalScope());
        }
    }

    @TruffleBoundary
    public Node parseN(ParserMode mode, int optimizeLevel, ParserErrorCallback errors, Source source, MaterializedFrame currentFrame, String[] argumentNames) {
        ArrayList<String> warnings = new ArrayList<>();
        // this wrapper tracks the warnings in a list instead of raising them immediately
        ParserErrorCallback collectWarnings = new ParserErrorCallback() {

            @Override
            public RuntimeException raiseInvalidSyntax(ErrorType type, Source src, SourceSection section, String message, Object... arguments) {
                return errors.raiseInvalidSyntax(type, src, section, message, arguments);
            }

            @Override
            public RuntimeException raiseInvalidSyntax(ErrorType type, Node location, String message, Object... arguments) {
                return errors.raiseInvalidSyntax(type, location, message, arguments);
            }

            @Override
            public void warn(PythonBuiltinClassType type, String format, Object... args) {
                assert type == PythonBuiltinClassType.DeprecationWarning;
                warnings.add(String.format(format, args));
            }

            @Override
            public PythonContext getContext() {
                return errors.getContext();
            }

        };
        PythonSSTNodeFactory sstFactory = new PythonSSTNodeFactory(collectWarnings, source, this);
        CacheItem parserSSTResult = parseWithANTLR(mode, optimizeLevel, collectWarnings, sstFactory, source, currentFrame, argumentNames);
        try {
            return sstFactory.createParserResult(parserSSTResult.antlrResult, mode, currentFrame, warnings);
        } catch (Exception e) {
            throw handleParserError(errors, source, e);
        }
    }

    public static PException handleParserError(ParserErrorCallback errors, Source source, Exception e) {
        String message = null;
        Object[] messageArgs = PythonUtils.EMPTY_OBJECT_ARRAY;
        if (e instanceof PException) {
            PException pException = (PException) e;
            try {
                InteropLibrary uncached = InteropLibrary.getUncached(pException);
                // If the exception is already a syntax error then just re-throw.
                if (uncached.getExceptionType(pException) == ExceptionType.PARSE_ERROR) {
                    throw pException;
                }
            } catch (UnsupportedMessageException unsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            /*
             * If we got a Python exception but it's not a syntax error then we need to pass the
             * message format AND the message arguments. Also, there is no need to reify the Python
             * exception since we just drop it.
             */
            PBaseException unreifiedException = pException.getUnreifiedException();
            if (unreifiedException.hasMessageFormat()) {
                message = unreifiedException.getMessageFormat();
                messageArgs = unreifiedException.getMessageArgs();
            }
        } else {
            // from parser we are getting RuntimeExceptions
            message = e instanceof RuntimeException ? e.getMessage() : null;
        }
        SourceSection section = PythonErrorStrategy.getPosition(source, e);
        ErrorType errorType = PythonErrorStrategy.getErrorType(e, section);
        throw errors.raiseInvalidSyntax(errorType, source, section, message != null ? message : "invalid syntax", messageArgs);
    }
}
