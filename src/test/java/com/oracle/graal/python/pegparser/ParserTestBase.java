/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.SSTTreePrinterVisitor;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;


import com.oracle.graal.python.pegparser.sst.SSTNode;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class ParserTestBase {
    protected static final String GOLDEN_FILE_EXT = ".tast";
    protected static final String NEW_GOLDEN_FILE_EXT = ".new.tast";
    private static final String SCOPE_FILE_EXT = ".scope";
    private static final String NEW_SCOPE_FILE_EXT = ".new.scope";

    protected boolean printDifferenceDetails = false;
    protected boolean printFormatStringLiteralValues = false;
    protected int printOnlyDiffIfLenIsBigger = 1000;

    private static final boolean REGENERATE_TREE = false;

    public TestInfo testInfo;

    private SSTNode lastSST;

    public ParserTestBase() {
    }

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    @AfterEach
    public void tearDown() {

    }

    private String getFileName() {
        return testInfo.getTestMethod().get().getName();
    }

//    protected Source createSource(File testFile) throws Exception {
//        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
//        return PythonLanguage.newSource(context, src, getFileName(testFile));
//    }

    public SSTNode parse(String src, String moduleName, int mode) {

        ParserTokenizer tokenizer = new ParserTokenizer(src);
        NodeFactory factory = new NodeFactoryImp();
        ParserErrorCallback errorCb = new ParserErrorCallback() {
            @Override
            public void onError(ParserErrorCallback.ErrorType type, int start, int end, String message) {
                System.err.println(String.format("TODO: %s[%d:%d]: %s", type.name(), start, end, message));
            }
        };
        FExprParser fexpParser = new FExprParser() {
            @Override
            public SSTNode parse(String code) {
                ParserTokenizer tok = new ParserTokenizer(code);
                return new Parser(tok, factory, this, errorCb).fstring_rule();
            }
        };
        Parser parser = new Parser(tokenizer, factory, fexpParser, errorCb);
        SSTNode result = parser.file_rule();

//        lastGlobalScope = ((PythonParserImpl) parser).getLastGlobaScope();
//        lastSST = ((PythonParserImpl) parser).getLastSST();
        return result;
    }

//    public SSTNode parse(String src, String moduleName, PythonParser.ParserMode mode) {
//        return parse(src, moduleName, mode, null);
//    }

//    public Node parse(Source source, PythonParser.ParserMode mode) {
//        PythonParser parser = context.getCore().getParser();
//        Node result = ((PythonParserImpl) parser).parseN(mode, 0, context.getCore(), source, null, null);
//        lastGlobalScope = ((PythonParserImpl) parser).getLastGlobaScope();
//        lastSST = ((PythonParserImpl) parser).getLastSST();
//        return result;
//    }

    protected SSTNode getLastSST() {
        return lastSST;
    }

    public void checkSyntaxError(String source) throws Exception {
        boolean thrown = false;
        try {
            parse(source, getFileName(), 1);
        } catch (Exception e) {
            thrown = isSyntaxError(e);
        }

        assertTrue( thrown, "Expected SyntaxError was not thrown.");
    }

    public void checkSyntaxErrorMessageContains(String source, String expectedMessage) throws Exception {
        boolean thrown = false;
        try {
            parse(source, getFileName(), 1);
        } catch (Exception e) {
            thrown = isSyntaxError(e);
//            String exceptionMessage = PythonTests.getExceptionMessage(e);
//            Assert.assertTrue("The expected message:\n\"" + expectedMessage + "\"\nwas not found in\n\"" + exceptionMessage + "\"", exceptionMessage.contains(expectedMessage));
        }

        assertTrue(thrown, "Expected SyntaxError was not thrown.");
    }

    public void checkSyntaxErrorMessage(String source, String expectedMessage) throws Exception {
        boolean thrown = false;
        try {
            parse(source, getFileName(), 1);
        } catch (Exception e) {
            thrown = isSyntaxError(e);
//            Assert.assertEquals(expectedMessage, PythonTests.getExceptionMessage(e));
        }

        assertTrue(thrown, "Expected SyntaxError was not thrown.");
    }

    protected static boolean isSyntaxError(Exception e) throws Exception {
//        return InteropLibrary.getUncached().getExceptionType(e) == ExceptionType.PARSE_ERROR;
        return false;
    }

    public void saveNewTreeResult(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue(testFile.exists(), "The test files " + testFile.getAbsolutePath() + " was not found.");
        String source = readFile(testFile);
        SSTNode resultNew = parse(source, "Test", 1);
        String tree = printTreeToString(resultNew);
        File newGoldenFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + NEW_GOLDEN_FILE_EXT)
                        : getGoldenFile(NEW_GOLDEN_FILE_EXT);
        if (REGENERATE_TREE || !newGoldenFile.exists()) {
            try (FileWriter fw = new FileWriter(newGoldenFile)) {
                fw.write(tree);
            }

        }
    }

    public void checkTreeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue(testFile.exists(), "The test files " + testFile.getAbsolutePath() + " was not found.");
        String source = readFile(testFile);
        SSTNode resultNew = parse(source, "Test",  1);
        String tree = printTreeToString(resultNew);
        File goldenFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + GOLDEN_FILE_EXT)
                        : getGoldenFile(GOLDEN_FILE_EXT);
        if (REGENERATE_TREE || !goldenFile.exists()) {
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(tree);
            }

        }
        assertDescriptionMatches(tree, goldenFile);
    }

//    public void saveNewScope(File testFile, boolean goldenFileNextToTestFile) throws Exception {
//        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
//        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
//        Source source = PythonLanguage.newSource(context, src, getFileName(testFile));
//        parse(source, PythonParser.ParserMode.File);
//        ScopeInfo scopeNew = getLastGlobalScope();
//        StringBuilder scopes = new StringBuilder();
//        scopeNew.debugPrint(scopes, 0);
//        File newScopeFile = goldenFileNextToTestFile
//                        ? new File(testFile.getParentFile(), getFileName(testFile) + NEW_SCOPE_FILE_EXT)
//                        : getGoldenFile(NEW_SCOPE_FILE_EXT);
//        if (REGENERATE_TREE || !newScopeFile.exists()) {
//            try (FileWriter fw = new FileWriter(newScopeFile)) {
//                fw.write(scopes.toString());
//            }
//        }
//    }

//    public void checkScopeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
//        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
//        TruffleFile src = context.getEnv().getInternalTruffleFile(testFile.getAbsolutePath());
//        Source source = PythonLanguage.newSource(context, src, getFileName(testFile));
//        parse(source, PythonParser.ParserMode.File);
//        ScopeInfo scopeNew = getLastGlobalScope();
//        StringBuilder scopes = new StringBuilder();
//        scopeNew.debugPrint(scopes, 0);
//        File goldenScopeFile = goldenFileNextToTestFile
//                        ? new File(testFile.getParentFile(), getFileName(testFile) + SCOPE_FILE_EXT)
//                        : getGoldenFile(SCOPE_FILE_EXT);
//        if (REGENERATE_TREE || !goldenScopeFile.exists()) {
//            try (FileWriter fw = new FileWriter(goldenScopeFile)) {
//                fw.write(scopes.toString());
//            }
//
//        }
//        assertDescriptionMatches(scopes.toString(), goldenScopeFile);
//    }

    public void checkTreeResult(String source, int mode/*, Frame frame*/) throws Exception {
        SSTNode resultNew = parse(source, getFileName(), mode/*, frame*/);
        String tree = printTreeToString(resultNew);
        File goldenFile = getGoldenFile(GOLDEN_FILE_EXT);
        if (REGENERATE_TREE || !goldenFile.exists()) {
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(tree);
            }

        }
        assertDescriptionMatches(tree, goldenFile);
    }

    public void checkError(String source, String... expectedErrors) {
        ParserTokenizer tokenizer = new ParserTokenizer(source);
        NodeFactory factory = new NodeFactoryImp();
        ArrayList<String> errors = new ArrayList<>();
        ParserErrorCallback errorCb = new ParserErrorCallback() {
            @Override
            public void onError(ParserErrorCallback.ErrorType type, int start, int end, String message) {
                errors.add(String.format("%s[%d:%d]:%s", type.name(), start, end, message));
            }
        };
        FExprParser fexpParser = new FExprParser() {
            @Override
            public SSTNode parse(String code) {
                ParserTokenizer tok = new ParserTokenizer(code);
                return new Parser(tok, factory, this, errorCb).fstring_rule();
            }
        };
        Parser parser = new Parser(tokenizer, factory, fexpParser, errorCb);
        parser.file_rule();
        assertEquals(Arrays.asList(expectedErrors), errors);
    }

//    public void checkTreeResult(String source, PythonParser.ParserMode mode) throws Exception {
//        checkTreeResult(source, mode, null);
//    }

//    public void checkScopeResult(String source, PythonParser.ParserMode mode) throws Exception {
//        parse(source, name.getMethodName(), mode);
//        ScopeInfo scopeNew = getLastGlobalScope();
//        StringBuilder scopes = new StringBuilder();
//        scopeNew.debugPrint(scopes, 0);
//        File goldenScopeFile = getGoldenFile(SCOPE_FILE_EXT);
//        if (REGENERATE_TREE || !goldenScopeFile.exists()) {
//            try (FileWriter fw = new FileWriter(goldenScopeFile)) {
//                fw.write(scopes.toString());
//            }
//
//        }
//        assertDescriptionMatches(scopes.toString(), goldenScopeFile);
//    }

//    public void checkSSTNodeOffsets(SSTNode node) {
//        SSTCheckOffsetVisitor checker = new SSTCheckOffsetVisitor(node);
//        boolean result = node.accept(checker);
//        assertTrue(checker.getMessage(), result);
//    }

    protected String printTreeToString(SSTNode node) {
        return printTreeToString(node, true);
    }

    protected String printTreeToString(SSTNode node, boolean printTmpSlots) {
        SSTTreePrinterVisitor visitor = new SSTTreePrinterVisitor();
        return node.accept(visitor);
    }

    protected void assertDescriptionMatches(String actual, File goldenFile) throws Exception {
        if (!goldenFile.exists()) {
            if (!goldenFile.createNewFile()) {
                assertTrue(false, "Cannot create file " + goldenFile.getAbsolutePath());
            }
            try (FileWriter fw = new FileWriter(goldenFile)) {
                fw.write(actual);
            }
            assertTrue(false, "Created generated golden file " + goldenFile.getAbsolutePath() + "\nPlease re-run the test.");
        }
        String expected = readFile(goldenFile);

        assertDescriptionMatches(actual, expected, goldenFile.getName());
    }

    protected void assertDescriptionMatches(String actual, String expected, String someName) throws Exception {
        final String expectedTrimmed = expected.trim();
        final String actualTrimmed = actual.trim();

        if (expectedTrimmed.equals(actualTrimmed)) {
            // Actual and expected content are equals --> Test passed

        } else {
            // We want to ignore different line separators (like \r\n against \n) because they
            // might be causing failing tests on a different operation systems like Windows :]
            final String expectedUnified = expectedTrimmed.replaceAll("\r", "");
            final String actualUnified = actualTrimmed.replaceAll("\r", "");

            if (expectedUnified.equals(actualUnified)) {
                return; // Only difference is in line separation --> Test passed
            }

            // There are some diffrerences between expected and actual content --> Test failed

            assertTrue(false, "Not matching results: " + (someName == null ? "" : someName) + lineSeparator(2) + getContentDifferences(expectedUnified, actualUnified));
        }
    }

    public static String readFile(File f) throws IOException {
        FileReader r = new FileReader(f);
        int fileLen = (int) f.length();
        CharBuffer cb = CharBuffer.allocate(fileLen);
        r.read(cb);
        cb.rewind();
        return cb.toString();
    }

    public File getTestFilesDir() {
        String dataDirPath = System.getProperty("org.graalvm.language.python.home");
        dataDirPath += "/com.oracle.graal.python.test/testData/testFiles";
        File dataDir = new File(dataDirPath);
        assertTrue(dataDir.exists(), "The test files folder, was not found.");
        return dataDir;
    }

    public File getTestFileFromTestAndTestMethod() {
        File testFilesDir = getTestFilesDir();
        File testDir = new File(testFilesDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        File testFile = new File(testDir, getFileName() + ".py");
        return testFile;
    }

    public File getGoldenDataDir() {
//        String dataDirPath = System.getProperty("org.graalvm.language.python.home");
//        dataDirPath += "/com.oracle.graal.python.test/testData/goldenFiles";
        String dataDirPath = "src/test/resources/parser/goldenFiles";
        File dataDir = new File(dataDirPath);
        assertTrue(dataDir.exists(), "The golden files folder, was not found.");
        return dataDir;
    }

    public File getGoldenFile(String ext) {
        File goldenDir = getGoldenDataDir();
        File testDir = new File(goldenDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        File goldenFile = new File(testDir, getFileName() + ext);
        return goldenFile;
    }

    protected String getFileName(File file) {
        return file.getName().substring(0, file.getName().lastIndexOf('.'));
    }

    private String getContentDifferences(String expected, String actual) {
        StringBuilder sb = new StringBuilder();
        if (printDifferenceDetails || (expected.length() < printOnlyDiffIfLenIsBigger && actual.length() < printOnlyDiffIfLenIsBigger)) {
            sb.append("Expected content is:").append(lineSeparator(2)).append(expected).append(lineSeparator(2)).append("but actual is:").append(lineSeparator(2)).append(actual).append(
                            lineSeparator(2)).append("It differs in the following things:").append(lineSeparator(2));
        } else {
            sb.append("Expected and actual differ in the following things:").append(lineSeparator(2));
        }

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
                    sb.append("Line ").append(lineNumber).append(" contains different content than expected: ").append(lineSeparator(1)).append("Expected: \t").append(expectedLine).append(
                                    lineSeparator(1)).append("Actual:  \t").append(actualLine).append(lineSeparator(2));

                }
            }
        }

        return sb.toString();
    }

    private static String lineSeparator(int number) {
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

//    public void checkScopeAndTree(String source, PythonParser.ParserMode mode) throws Exception {
//        checkScopeResult(source, mode);
//        checkSSTNodeOffsets(lastSST);
//        checkTreeResult(source, mode);
//    }
//
   public void checkScopeAndTree(String source) throws Exception {
       // checkScopeAndTree(source, PythonParser.ParserMode.File);
       checkTreeResult(source);
   }

    public void checkTreeResult(String source) throws Exception {
        checkTreeResult(source, 1);
//        checkSSTNodeOffsets(lastSST);
    }

//    public void checkScopeAndTreeFromFile(File testFile) throws Exception {
//        checkScopeFromFile(testFile, true);
//        checkSSTNodeOffsets(lastSST);
//        checkTreeFromFile(testFile, true);
//    }
}
