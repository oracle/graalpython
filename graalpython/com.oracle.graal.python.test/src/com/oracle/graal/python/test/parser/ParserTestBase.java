/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.test.parser;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.rules.TestName;

public class ParserTestBase {
    private PythonContext context;
    private final static String GOLDEN_FILE_EXT = ".tast";
    private final static String SCOPE_FILE_EXT = ".scope";
    
    @Rule public TestName name = new TestName();

    private ScopeInfo lastGlobalScope;
    
    public ParserTestBase() {
        PythonTests.enterContext();
        context = PythonLanguage.getContextRef().get();
    }
    
    
    protected RootNode parseOld(String src, String moduleName) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, moduleName).build();
        PythonParser parser = context.getCore().getParser();
        RootNode result = (RootNode) parser.parse(PythonParser.ParserMode.File, context.getCore(), source, null);
        lastGlobalScope = ((PythonParserImpl)parser).getLastGlobaScope();
        return result;
    }
    
    protected RootNode parseOld(Source source) {
        PythonParser parser = context.getCore().getParser();
        RootNode result = (RootNode) parser.parse(PythonParser.ParserMode.File, context.getCore(), source, null);
        lastGlobalScope = ((PythonParserImpl)parser).getLastGlobaScope();
        return result;
    }
    
    protected Node parseNew(String src, String moduleName) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, moduleName).build();
        PythonParser parser = context.getCore().getParser();
        Node result = ((PythonParserImpl)parser).parseN(PythonParser.ParserMode.File, context.getCore(), source, null);
        lastGlobalScope = ((PythonParserImpl)parser).getLastGlobaScope();
        return result;
    }
    
    protected Node parseNew(Source source) {
        PythonParser parser = context.getCore().getParser();
        Node result = ((PythonParserImpl)parser).parseN(PythonParser.ParserMode.File, context.getCore(), source, null);
        lastGlobalScope = ((PythonParserImpl)parser).getLastGlobaScope();
        return result;
    }
    
    protected ScopeInfo getLastGlobalScope() {
        return lastGlobalScope;
    }
    
    public void checkSyntaxError(String source) throws Exception {
        boolean thrown = false;
        try {
            Node resultNew = parseNew(source, name.getMethodName());
        } catch (PException e) {
            thrown = e.isSyntaxError();
        }
        
        assertTrue("Expected SyntaxError was not thrown.", thrown);
    }
    
    public void checkTreeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        TruffleFile src = context.getEnv().getTruffleFile(testFile.getAbsolutePath());
        Source source = context.getLanguage().newSource(context, src, getFileName(testFile));
        Node resultNew = parseNew(source);
        String tree = printTreeToString(resultNew);
        File goldenFile = goldenFileNextToTestFile 
                ? new File(testFile.getParentFile(), getFileName(testFile) + GOLDEN_FILE_EXT)
                : getGoldenFile(GOLDEN_FILE_EXT);
        if (!goldenFile.exists()) {
            // parse it with old parser and create golden file with this result
            // TODO, when the new parser will work, it has to be removed
            RootNode resultOld = parseOld(source);
            String oldTree = printTreeToString(resultOld);
            FileWriter fw = new FileWriter(goldenFile);
            try {
                fw.write(oldTree);
            }
            finally{
                fw.close();
            }
            
        }
        assertDescriptionMatches(tree, goldenFile);
    }
    
    public void checkScopeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        TruffleFile src = context.getEnv().getTruffleFile(testFile.getAbsolutePath());
        Source source = context.getLanguage().newSource(context, src, getFileName(testFile));
        parseNew(source);
        ScopeInfo scopeNew = getLastGlobalScope();
        StringBuilder scopes = new StringBuilder();
        scopeNew.debugPrint(scopes, 0);
        File goldenScopeFile = goldenFileNextToTestFile 
                ? new File(testFile.getParentFile(), getFileName(testFile) + SCOPE_FILE_EXT)
                : getGoldenFile(SCOPE_FILE_EXT);
        if (!goldenScopeFile.exists()) {
            parseOld(source);
            StringBuilder oldScope = new StringBuilder();
            getLastGlobalScope().debugPrint(oldScope, 0);
            FileWriter fw = new FileWriter(goldenScopeFile);
            try {
                fw.write(oldScope.toString());
            }
            finally{
                fw.close();
            }
            
        }
        assertDescriptionMatches(scopes.toString(), goldenScopeFile);
    }
    
    public void checkTreeResult(String source) throws Exception {
        Node resultNew = parseNew(source, name.getMethodName());
        String tree = printTreeToString(resultNew);
        File goldenFile = getGoldenFile(GOLDEN_FILE_EXT);
        if (!goldenFile.exists()) {
            // parse it with old parser and create golden file with this result
            // TODO, when the new parser will work, it has to be removed
            RootNode resultOld = parseOld(source, name.getMethodName());
            String oldTree = printTreeToString(resultOld);
            FileWriter fw = new FileWriter(goldenFile);
            try {
                fw.write(oldTree);
            }
            finally{
                fw.close();
            }
            
        }
        assertDescriptionMatches(tree, goldenFile);
    }
    
    public void checkScopeResult(String source) throws Exception {
        parseNew(source, name.getMethodName());
        ScopeInfo scopeNew = getLastGlobalScope();
        StringBuilder scopes = new StringBuilder();
        scopeNew.debugPrint(scopes, 0);
        File goldenScopeFile = getGoldenFile(SCOPE_FILE_EXT);
        if (!goldenScopeFile.exists()) {
            parseOld(source, name.getMethodName());
            StringBuilder oldScope = new StringBuilder();
            getLastGlobalScope().debugPrint(oldScope, 0);
            FileWriter fw = new FileWriter(goldenScopeFile);
            try {
                fw.write(oldScope.toString());
            }
            finally{
                fw.close();
            }
            
        }
        assertDescriptionMatches(scopes.toString(), goldenScopeFile);
    }
    
    private String printTreeToString(Node node) {
        ParserTreePrinter visitor = new ParserTreePrinter();
        node.accept(visitor);
        return visitor.getTree();
    }
    
    protected void assertDescriptionMatches(String actual, File goldenFile) throws IOException {
        if (!goldenFile.exists()) {
            if (!goldenFile.createNewFile()) {
               assertTrue("Cannot create file " + goldenFile.getAbsolutePath(), false);
            }
            FileWriter fw = new FileWriter(goldenFile);
            try {
                fw.write(actual);
            }
            finally{
                fw.close();
            }
            assertTrue("Created generated golden file " + goldenFile.getAbsolutePath() + "\nPlease re-run the test.", false);
        } 
        String expected = readFile(goldenFile);
        
        final String expectedTrimmed = expected.trim();
        final String actualTrimmed = actual.trim();
        
        if (expectedTrimmed.equals(actualTrimmed)) {
            return; // Actual and expected content are equals --> Test passed
        } else {
            // We want to ignore different line separators (like \r\n against \n) because they
            // might be causing failing tests on a different operation systems like Windows :]
            final String expectedUnified = expectedTrimmed.replaceAll("\r", "");
            final String actualUnified = actualTrimmed.replaceAll("\r", "");

            if (expectedUnified.equals(actualUnified)) {
                return; // Only difference is in line separation --> Test passed
            }

            // There are some diffrerences between expected and actual content --> Test failed

            assertTrue("Not matching goldenfile: " + goldenFile.getName() + lineSeparator(2) + getContentDifferences(expectedUnified, actualUnified), false);
        }
    }
    
    public static String readFile(File f) throws IOException {
        FileReader r = new FileReader(f);
        int fileLen = (int)f.length();
        CharBuffer cb = CharBuffer.allocate(fileLen);
        r.read(cb);
        cb.rewind();
        return cb.toString();
    }
    
    public File getTestFilesDir() {
        String dataDirPath = System.getProperty("python.home");
        dataDirPath += "/com.oracle.graal.python.test/testData/testFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The test files folder, was not found.", dataDir.exists());
        return dataDir;
    }
    
    public File getTestFileFromTestAndTestMethod() {
        File testFilesDir = getTestFilesDir();
        File testDir = new File(testFilesDir  + "/" + this.getClass().getSimpleName() );
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        File testFile = new File(testDir, name.getMethodName() + ".py");
        return testFile;
    }
    
    public File getGoldenDataDir() {
        String dataDirPath = System.getProperty("python.home");
        dataDirPath += "/com.oracle.graal.python.test/testData/goldenFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The golden files folder, was not found.", dataDir.exists());
        return dataDir;
    }
    
    public File getGoldenFile(String ext) {
        File goldenDir = getGoldenDataDir();
        File testDir = new File(goldenDir  + "/" + this.getClass().getSimpleName() );
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        File goldenFile = new File(testDir, name.getMethodName() + ext);
        return goldenFile;
    }
    
    private String getFileName(File file) {
        return file.getName().substring(0, file.getName().lastIndexOf('.'));
    }
    
    private String getContentDifferences(String expected, String actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected content is:").
           append(lineSeparator(2)).
           append(expected).
           append(lineSeparator(2)).
           append("but actual is:").
           append(lineSeparator(2)).
           append(actual).
           append(lineSeparator(2)).
           append("It differs in the following things:").
           append(lineSeparator(2));

        List<String> expectedLines = Arrays.asList(expected.split("\n"));
        List<String> actualLines = Arrays.asList(actual.split("\n"));

        if (expectedLines.size() != actualLines.size()) {
            sb.append("Number of lines: \n\tExpected: ").append(expectedLines.size()).append("\n\tActual: ").append(actualLines.size()).append("\n\n");
        }

        // Appending lines which are missing in expected content and are present in actual content
        boolean noErrorInActual = true;
        for (String actualLine : actualLines) {
            if (expectedLines.contains(actualLine) == false) {
                if (noErrorInActual) {
                    sb.append("Actual content contains following lines which are missing in expected content: ").append(lineSeparator(1));
                    noErrorInActual = false;
                }
                sb.append("\t").append(actualLine).append(lineSeparator(1));
            }
        }

        // Appending lines which are missing in actual content and are present in expected content
        boolean noErrorInExpected = true;
        for (String expectedLine : expectedLines) {
            if (actualLines.contains(expectedLine) == false) {
                // If at least one line missing in actual content we want to append header line
                if (noErrorInExpected) {
                    sb.append("Expected content contains following lines which are missing in actual content: ").append(lineSeparator(1));
                    noErrorInExpected = false;
                }
                sb.append("\t").append(expectedLine).append(lineSeparator(1));
            }
        }

        // If both values are true it means the content is the same, but some lines are
        // placed on a different line number in actual and expected content
        if (noErrorInActual && noErrorInExpected && expectedLines.size() == actualLines.size()) {
            for (int lineNumber = 0; lineNumber < expectedLines.size(); lineNumber++) {
                String expectedLine = expectedLines.get(lineNumber);
                String actualLine = actualLines.get(lineNumber);

                if (!expectedLine.equals(actualLine)) {
                    sb.append("Line ").
                        append(lineNumber).
                        append(" contains different content than expected: ").
                        append(lineSeparator(1)).
                        append("Expected: \t").
                        append(expectedLine).
                        append(lineSeparator(1)).
                        append("Actual:  \t").
                        append(actualLine).
                        append(lineSeparator(2));

                }
            }
        }

        return sb.toString();
    }
    
    private String lineSeparator(int number) {
        final String lineSeparator = System.getProperty("line.separator");
        if (number > 1) {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < number; i++) {
                sb.append(lineSeparator);
            }
            return sb.toString();
        }
        return lineSeparator;
    }
    
    public void checkScopeAndTree(String source) throws Exception{
        checkScopeResult(source);
        checkTreeResult(source);
    }
    
    public void checkScopeAndTreeFromFile(File testFile) throws Exception{
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
