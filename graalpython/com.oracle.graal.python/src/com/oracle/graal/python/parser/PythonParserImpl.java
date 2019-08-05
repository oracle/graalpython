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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

import com.oracle.graal.python.parser.antlr.Builder;
import com.oracle.graal.python.parser.antlr.BuilderNew;
import com.oracle.graal.python.parser.antlr.Python3NewLexer;
import com.oracle.graal.python.parser.antlr.Python3NewParser;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.sst.BlockSSTNode;
import com.oracle.graal.python.parser.sst.FactorySSTVisitor;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.HashSet;

public final class PythonParserImpl implements PythonParser {

    private static Python3Parser getPython3Parser(String string) {
        Python3Parser parser = Builder.createParser(CharStreams.fromString(string));
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }
    
    private static Python3NewParser getPython3NewParser(String string) {
        Python3NewParser parser = BuilderNew.createParser(CharStreams.fromString(string));
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }

    private static Python3NewParser getPython3NewParser(Source source, ParserErrorCallback errors) {
        Python3NewLexer lexer = new Python3NewLexer(CharStreams.fromString(source.getCharacters().toString()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(Builder.ERROR_LISTENER);
        Python3NewParser parser = new Python3NewParser(new CommonTokenStream(lexer));
        parser.factory = new PythonNodeFactory(errors.getLanguage(), source);
        parser.removeErrorListeners();
        parser.addErrorListener(Builder.ERROR_LISTENER);
        parser.setErrorHandler(new PythonErrorStrategy());
        return parser;
    }
    
    public static void main(String[] args) {

        System.out.println(Integer.MAX_VALUE);
        System.out.println(-Integer.MAX_VALUE);
//        System.out.println(--Integer.MAX_VALUE);
        String source = "1234 + 4321 + 1234.44 + 0x1f + 0xffffffffffffffff1 + 0o0 + 0O1 + 0b1010101 + 1234j + 4321J\n";
//        getPython3NewParser(source).single_input(false, null);
        source = "raise 'asdf'\n";
//        getPython3NewParser(source).single_input(false, null);
    
//        PythonParserImpl parserImpl = new PythonParserImpl();
//        Python3Core core = new Python3Core(parserImpl);
//        Python3Parser parser = getPython3Parser("help('modules')");
//        ParserRuleContext input = parser.single_input();
//        // prepare scope translator
//        TranslationEnvironment environment = new TranslationEnvironment(core.getLanguage());
//        FrameDescriptor inlineLocals =  null;
//        ScopeTranslator<Object> defineScopes = new ScopeTranslator<>(core, environment, false, inlineLocals);
//        // first pass of the scope translator -> define the scopes
//        input.accept(defineScopes);
//        // create frame slots for cell and free vars
//        
//        defineScopes.setFreeVarsInRootScope(null);
//        defineScopes.createFrameSlotsForCellAndFreeVars();
//
//        // create Truffle ASTs
//        long startTranslate = System.currentTimeMillis();
//        Node translate = PythonTreeTranslator.translate(core, "AHoj", input, environment, null, ParserMode.File);
//        long end = System.currentTimeMillis();
////        
////        String source = "1234 + 4321 + 1234.44 + 0x1f + 0xffffffffffffffff1 + 0o0 + 0O1 + 0b1010101 + 1234j + 4321J\n";
////        getPython3NewParser(source).single_input(false, null);
////        source = "raise 'asdf'\n";
////        getPython3NewParser(source).single_input(false, null);
        
    }
    
    private static int parsedCount = 0;
    private static int parsedFileCount = 0;
    private static long parsedTime = 0;
    private static long parsedFileTime = 0;
    private static long translateTime = 0;
    private static long translateFileTime = 0;
    private static long prepareTranslationTime = 0;
    private static HashSet<String> cache= new HashSet<String>();
    private static int cachedFiles = 0;
    
    @TruffleBoundary
    public Node parseWithNew(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
        FrameDescriptor inlineLocals = mode == ParserMode.InlineEvaluation ? currentFrame.getFrameDescriptor() : null;
        // ANTLR parsing
//        long start = System.currentTimeMillis();
//        Python3NewParser newParser = getPython3NewParser(source.getCharacters().toString());
        Python3NewParser newParser = getPython3NewParser(source, errors);
        ParserRuleContext input;
        SSTNode parserResult;
        try {
            switch (mode) {
                case Eval:
//                    parserResult = newParser.eval_input().result;
                    break;
                case File:
            // System.out.println("===\n" + source.getCharacters() + "\n---\n");

                    Python3NewParser.File_inputContext file_input = newParser.file_input();
                    parserResult = file_input.result;
                    PythonLanguage lang = errors.getLanguage();
                    PythonNodeFactory factory = newParser.factory;
                    lastGlobalScope = factory.getScopeEnvironment().getGlobalScope();
                    factory.getScopeEnvironment().setCurrentScope(lastGlobalScope);
                    FactorySSTVisitor factoryVisitor = new FactorySSTVisitor(errors, factory.getScopeEnvironment(), lang.getNodeFactory(), source);
                    ExpressionNode body = factoryVisitor.asExpression((BlockSSTNode)parserResult);
                    ModuleRootNode mrn = lang.getNodeFactory().createModuleRoot(source.getName(), factory.getModuleDoc(body), body, file_input.scope.getFrameDescriptor());
                    
//                    long start = System.currentTimeMillis();
//                    parser.file_input();
//                    long end = System.currentTimeMillis();
//                    System.out.println("parsing wiht new tooks: " + (end - start));
                    return mrn;
                case InteractiveStatement:
                case InlineEvaluation:
                case Statement:
//                    parserResult = newParser.single_input(source.isInteractive(), inlineLocals).result;
                    break;
                default:
                    throw new RuntimeException("unexpected mode: " + mode);
            }
            
        } catch (Exception e) {
//            e.printStackTrace();
            if (mode == ParserMode.InteractiveStatement || mode == ParserMode.Statement || mode == ParserMode.InlineEvaluation) {
                try {
                    newParser.reset();
                    input = newParser.eval_input();
                } catch (Exception e2) {
                    throw handleParserError(errors, source, e);
                }
            } else {
                throw handleParserError(errors, source, e);
            }
        }
        
//        long startTranslate = System.currentTimeMillis();
//        // prepare scope translator
//        TranslationEnvironment environment = new TranslationEnvironment(errors.getLanguage());
//        ScopeTranslator<Object> defineScopes = new ScopeTranslator<>(errors, environment, source.isInteractive(), inlineLocals);
//        // first pass of the scope translator -> define the scopes
//        input.accept(defineScopes);
//        // create frame slots for cell and free vars
//        defineScopes.setFreeVarsInRootScope(currentFrame);
//        defineScopes.createFrameSlotsForCellAndFreeVars();
//        long endPreparing = System.currentTimeMillis();
//        // create Truffle ASTs
//        Node translate = PythonTreeTranslator.translate(errors, source.getName(), input, environment, source, mode);
//        long end = System.currentTimeMillis();
//        
//        parsedCount++;
//        parsedTime += (end - start);
//        translateTime += (end - startTranslate);
//        prepareTranslationTime =+ endPreparing - startTranslate;
//        if (source.getPath() != null) {
//            parsedFileCount++;
//            parsedFileTime += (end - start);
//            translateFileTime += (end - startTranslate);
//            if (cache.contains(source.getPath())) {
//                cachedFiles++;
//                System.out.println("cached " + source.getPath());
//            } else {
//                cache.add(source.getPath());
//            }
//        }
//        if (parsedCount % 10 == 0) {
//            System.out.println("Parsed " + parsedCount + " codes took " + parsedTime + "ms  avarage parse time per code: " + (parsedTime/parsedCount) + ", translattion time: " + translateTime + ", per one parsing: " +(translateTime/parsedCount));
//            System.out.println("    from these " + parsedFileCount + " files parsed took " + parsedFileTime + "ms, avarage parse time per file: " + (parsedFileTime / parsedFileCount) + ", translation time: " + (translateFileTime/parsedFileCount));
//            System.out.println("    cached results: " + cachedFiles);
//        }
//        return translate;
        return null;
    }
    
    private ParserRuleContext lastTree;
    
    public ParserRuleContext getLastAntlrTree() {
        return lastTree;
    }
    
    private ScopeInfo lastGlobalScope;
    
    public ScopeInfo getLastGlobaScope() {
        return lastGlobalScope;
    }
    
    private boolean useNewParser = false;
    private boolean logFiles = false;
    private boolean timeMeasure = true;
    private long timeInParser = 0;
    private long numberOfFiles = 0;
    private long lastMeasurement = System.currentTimeMillis();
    
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
        
        long start = System.currentTimeMillis();
        Node result;
        if (useNewParser /*&& PythonLanguage.getCore().isInitialized()*/) {
            if(logFiles) {
                System.out.println(" with new parser.");
            }
            result = parseN(mode, errors, source, currentFrame);
        } else {
            if (logFiles) {
                System.out.println(" with old parser.");
            }
            result = parseO(mode, errors, source, currentFrame);
        }
        long end = System.currentTimeMillis();
        if (timeMeasure) {
            timeInParser = timeInParser + (end - start);
            numberOfFiles++;
            if ((end - lastMeasurement) > 2000 || numberOfFiles % 50 == 0) {
                System.out.println("Parsed " + numberOfFiles + " in " + timeInParser + "ms.");
                
            }
            lastMeasurement = end;
        }
        return result;
    }
    
//    @Override
    @TruffleBoundary
    public Node parseN(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
        FrameDescriptor inlineLocals = mode == ParserMode.InlineEvaluation ? currentFrame.getFrameDescriptor() : null;
        // ANTLR parsing
//        Python3NewParser parser = getPython3NewParser(source.getCharacters().toString());
        Python3NewParser parser = getPython3NewParser(source, errors);
        parser.factory = new PythonNodeFactory(errors.getLanguage(), source);
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
            if (mode == ParserMode.InteractiveStatement || mode == ParserMode.Statement || mode == ParserMode.InlineEvaluation) {
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
        
        lastGlobalScope = parser.factory.getScopeEnvironment().getGlobalScope();
        return parser.factory.createParserResult(parserSSTResult, mode, errors, source, currentFrame);
        
    }
    
    
//    @Override
    @TruffleBoundary
    public Node parseO(ParserMode mode, ParserErrorCallback errors, Source source, Frame currentFrame) {
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
