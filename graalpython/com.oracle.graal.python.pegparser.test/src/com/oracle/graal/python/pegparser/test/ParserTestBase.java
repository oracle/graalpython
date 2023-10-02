/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.Parser;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.oracle.graal.python.pegparser.AbstractParser.Flags;
import com.oracle.graal.python.pegparser.scope.ScopeEnvironment;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.test.sst.SSTTreePrinterVisitor;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public class ParserTestBase {
    private static final int FEATURE_VERSION = 10;
    protected static final String GOLDEN_FILE_EXT = ".tast";
    private static final String SCOPE_FILE_EXT = ".scope";

    private static final EnumSet<FutureFeature> EMPTY_FUTURE = EnumSet.noneOf(FutureFeature.class);

    protected boolean printDifferenceDetails = false;
    protected int printOnlyDiffIfLenIsBigger = 1000;

    private static final boolean REGENERATE_TREE = false;

    protected TestErrorCallbackImpl errorCallback;

    @Rule public TestName name = new TestName();

    protected ParserTestBase() {
    }

    private String getFileName() {
        return name.getMethodName();
    }

    @SuppressWarnings("unused")
    public ModTy parse(String src, String moduleName, InputType inputType) {
        return parse(src, moduleName, inputType, false);
    }

    @SuppressWarnings("unused")
    public ModTy parse(String src, String moduleName, InputType inputType, boolean interactiveTerminal) {
        errorCallback = new TestErrorCallbackImpl();
        EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);
        if (interactiveTerminal) {
            flags.add(Flags.INTERACTIVE_TERMINAL);
        }
        Parser parser = new Parser(src, new DefaultStringFactoryImpl(), errorCallback, inputType, flags, FEATURE_VERSION);
        return (ModTy) parser.parse();
    }

    public void checkSyntaxError(String source) {
        ModTy node = parse(source, getFileName(), InputType.FILE);
        if (node != null) {
            ScopeEnvironment.analyze(node, errorCallback, EMPTY_FUTURE);
        }
        assertTrue("Expected Error.", errorCallback.hasErrors());
        assertSame("Expected SyntaxError", errorCallback.getErrors().get(0).getType(), ErrorCallback.ErrorType.Syntax);
    }

    public void checkSyntaxErrorMessageContains(String source, String expectedMessage) {
        ModTy node = parse(source, getFileName(), InputType.FILE);
        if (node != null) {
            ScopeEnvironment.analyze(node, errorCallback, EMPTY_FUTURE);
        }
        assertTrue("Expected Error.", errorCallback.hasErrors());
        TestErrorCallbackImpl.Error error = errorCallback.getErrors().get(0);
        assertSame("Expected SyntaxError not " + error.getType(), error.getType(), ErrorCallback.ErrorType.Syntax);
        assertTrue("The expected message:\n\"" + expectedMessage + "\"\nwas not found in\n\"" + error.getMessage() + "\"", error.getMessage().contains(expectedMessage));
    }

    public void checkSyntaxErrorMessage(String source, String expectedMessage) {
        checkSyntaxErrorMessage(source, expectedMessage, EMPTY_FUTURE);
    }

    public void checkSyntaxErrorMessage(String source, String expectedMessage, EnumSet<FutureFeature> futureFeatures) {
        checkSyntaxErrorMessage(source, expectedMessage, InputType.FILE, futureFeatures);
    }

    public void checkSyntaxErrorMessage(String source, String expectedMessage, InputType inputType) {
        checkSyntaxErrorMessage(source, expectedMessage, inputType, EMPTY_FUTURE);
    }

    public void checkSyntaxErrorMessage(String source, String expectedMessage, InputType inputType, EnumSet<FutureFeature> futureFeatures) {
        ModTy node = parse(source, getFileName(), inputType);
        if (node != null) {
            ScopeEnvironment.analyze(node, errorCallback, futureFeatures);
        }
        assertTrue("Expected Error.", errorCallback.hasErrors());
        TestErrorCallbackImpl.Error error = errorCallback.getErrors().get(0);
        assertSame("Expected SyntaxError not " + error.getType(), error.getType(), ErrorCallback.ErrorType.Syntax);
        assertEquals(expectedMessage, error.getMessage());
    }

    public void checkDeprecationWarning(String source, String expectedMessage) {
        ModTy node = parse(source, getFileName(), InputType.FILE);
        if (node != null) {
            ScopeEnvironment.analyze(node, errorCallback, EMPTY_FUTURE);
        }
        assertTrue("Expected a DeprecationWarning.", errorCallback.hasWarnings());
        assertEquals(expectedMessage, errorCallback.getWarnings().get(0).getMessage());
    }

    public void checkIndentationError(String source) {
        ModTy node = parse(source, getFileName(), InputType.FILE);
        if (node != null) {
            ScopeEnvironment.analyze(node, errorCallback, EMPTY_FUTURE);
        }
        assertTrue("Expected Error.", errorCallback.hasErrors());
        assertSame("Expected IndentationError", errorCallback.getErrors().get(0).getType(), ErrorCallback.ErrorType.Indentation);
    }

    public void checkIndentationErrorMessage(String source, String expectedMessage) {
        ModTy node = parse(source, getFileName(), InputType.FILE);
        if (node != null) {
            ScopeEnvironment.analyze(node, errorCallback, EMPTY_FUTURE);
        }
        assertTrue("Expected Error.", errorCallback.hasErrors());
        TestErrorCallbackImpl.Error error = errorCallback.getErrors().get(0);
        assertSame("Expected IndentationError not " + error.getType(), error.getType(), ErrorCallback.ErrorType.Indentation);
        assertEquals(expectedMessage, error.getMessage());
    }

    public void checkTreeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        String source = readFile(testFile);
        SSTNode resultNew = parse(source, "Test", InputType.FILE);
        String tree = printTreeToString(source, resultNew);
        File goldenFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + GOLDEN_FILE_EXT)
                        : getGoldenFile(GOLDEN_FILE_EXT);
        writeGoldenFileIfMissing(goldenFile, tree);
        assertDescriptionMatches(tree, goldenFile);
    }

    public void checkScopeFromFile(File testFile, boolean goldenFileNextToTestFile) throws Exception {
        assertTrue("The test files " + testFile.getAbsolutePath() + " was not found.", testFile.exists());
        String source = readFile(testFile);
        ModTy mod = parse(source, "<module>", InputType.FILE);
        ScopeEnvironment env = ScopeEnvironment.analyze(mod, errorCallback, EMPTY_FUTURE);
        File goldenScopeFile = goldenFileNextToTestFile
                        ? new File(testFile.getParentFile(), getFileName(testFile) + SCOPE_FILE_EXT)
                        : getGoldenFile(SCOPE_FILE_EXT);
        writeGoldenFileIfMissing(goldenScopeFile, env.toString());
        assertDescriptionMatches(env.toString(), goldenScopeFile);
    }

    public void checkTreeResult(String source, InputType inputType/* , Frame frame */) throws Exception {
        SSTNode resultNew = parse(source, getFileName(), inputType/* , frame */);
        String tree = printTreeToString(source, resultNew);
        File goldenFile = getGoldenFile(GOLDEN_FILE_EXT);
        writeGoldenFileIfMissing(goldenFile, tree);
        assertDescriptionMatches(tree, goldenFile);
    }

    public void checkError(String source, String... expectedErrors) {
        ArrayList<String> errors = new ArrayList<>();
        ErrorCallback errorCb = new ErrorCallback() {
            @Override
            public void onError(ErrorCallback.ErrorType type, SourceRange sourceRange, String message) {
                errors.add(String.format("%s[%d:%d-%d:%d]:%s", type.name(), sourceRange.startLine, sourceRange.startColumn, sourceRange.endLine, sourceRange.endColumn, message));
            }

            @Override
            public void onWarning(WarningType warningType, SourceRange sourceRange, String message) {
                fail("Unexpected " + warningType + " warning");
            }

            @Override
            public void reportIncompleteSource(int line) {
                fail("Unexpected call to reportIncompleteSource");
            }
        };
        Parser parser = new Parser(source, new DefaultStringFactoryImpl(), errorCb, InputType.FILE, EnumSet.noneOf(Flags.class), FEATURE_VERSION);
        parser.parse();
        assertEquals(Arrays.asList(expectedErrors), errors);
    }

    public void checkScopeResult(String source, InputType inputType) throws Exception {
        ModTy mod = parse(source, "<module>", inputType);
        File goldenScopeFile = getGoldenFile(SCOPE_FILE_EXT);
        ScopeEnvironment env = ScopeEnvironment.analyze(mod, errorCallback, EMPTY_FUTURE);
        writeGoldenFileIfMissing(goldenScopeFile, env.toString());
        assertDescriptionMatches(env.toString(), goldenScopeFile);
    }

    private static void writeGoldenFileIfMissing(File goldenFile, String contents) throws IOException {
        if (REGENERATE_TREE || !goldenFile.exists()) {
            if (System.getenv("CI") != null) {
                fail("Missing golden file " + goldenFile);
            } else {
                try (FileWriter fw = new FileWriter(goldenFile)) {
                    fw.write(contents);
                }
            }
        }
    }

// public void checkSSTNodeOffsets(SSTNode node) {
// SSTCheckOffsetVisitor checker = new SSTCheckOffsetVisitor(node);
// boolean result = node.accept(checker);
// assertTrue(checker.getMessage(), result);
// }

    protected String printTreeToString(String source, SSTNode node) {
        SSTTreePrinterVisitor visitor = new SSTTreePrinterVisitor();
        return String.format("Input:\n------\n%s\n\nOutput:\n-------\n%s", source, node.accept(visitor));
    }

    protected void assertDescriptionMatches(String actual, File goldenFile) throws Exception {
        String expected = readFile(goldenFile);
        assertDescriptionMatches(actual, expected, goldenFile.getName());
    }

    protected void assertDescriptionMatches(String actual, String expected, String someName) {
        final String expectedTrimmed = expected.trim();
        final String actualTrimmed = actual.trim();

        if (expectedTrimmed.equals(actualTrimmed)) {
            // Actual and expected content are equals --> Test passed

        } else {
            // We want to ignore different line separators (like \r\n against \n) because they
            // might be causing failing tests on a different operating systems like Windows :]
            final String expectedUnified = expectedTrimmed.replaceAll("\r", "");
            final String actualUnified = actualTrimmed.replaceAll("\r", "");

            if (expectedUnified.equals(actualUnified)) {
                return; // Only difference is in line separation --> Test passed
            }

            // There are some differences between expected and actual content --> Test failed

            fail("Not matching results: " + (someName == null ? "" : someName) + lineSeparator(2) + getContentDifferences(expectedUnified, actualUnified));
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
        String dataDirPath = "graalpython/com.oracle.graal.python.pegparser.test/testData/parser/testFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The test files folder, was not found.", dataDir.exists());
        return dataDir;
    }

    public File getTestFileFromTestAndTestMethod() {
        File testFilesDir = getTestFilesDir();
        File testDir = new File(testFilesDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        return new File(testDir, getFileName() + ".py");
    }

    public File getGoldenDataDir() {
        String dataDirPath = "graalpython/com.oracle.graal.python.pegparser.test/testData/parser/goldenFiles";
        File dataDir = new File(dataDirPath);
        assertTrue("The golden files folder, was not found.", dataDir.exists());
        return dataDir;
    }

    public File getGoldenFile(String ext) {
        File goldenDir = getGoldenDataDir();
        File testDir = new File(goldenDir + "/" + this.getClass().getSimpleName());
        if (!testDir.exists()) {
            testDir.mkdir();
        }
        return new File(testDir, getFileName() + ext);
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
            if (!expectedLines.contains(actualLine)) {
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
            if (!actualLines.contains(expectedLine)) {
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

// public void checkScopeAndTree(String source, PythonParser.ParserMode mode) throws Exception {
// checkScopeResult(source, mode);
// checkSSTNodeOffsets(lastSST);
// checkTreeResult(source, mode);
// }
//
    public void checkScopeAndTree(String source) throws Exception {
        checkScopeAndTree(source, InputType.FILE);
    }

    public void checkScopeAndTree(String source, InputType inputType) throws Exception {
        checkScopeResult(source, inputType);
        checkTreeResult(source);
    }

    public void checkTreeResult(String source) throws Exception {
        checkTreeResult(source, InputType.FILE);
// checkSSTNodeOffsets(lastSST);
    }

// public void checkScopeAndTreeFromFile(File testFile) throws Exception {
// checkScopeFromFile(testFile, true);
// checkSSTNodeOffsets(lastSST);
// checkTreeFromFile(testFile, true);
// }
}
