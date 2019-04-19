/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.test.grammar;

import static org.junit.Assert.assertTrue;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.InnerRootNode;
import com.oracle.graal.python.parser.PythonParserImpl;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.*;
import org.junit.rules.TestName;

public class Parser2Tests {
    PythonContext context;
    ParserRuleContext lastAntrlTree;
    
    @Rule public TestName name = new TestName();
    
    public Parser2Tests() {
        PythonTests.enterContext();
        context = PythonLanguage.getContextRef().get();
    }
    
    RootNode parse(String src) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, "foo").build();
        PythonParser parser = context.getCore().getParser();
        RootNode result = (RootNode) parser.parse(PythonParser.ParserMode.File, context.getCore(), source, null);
        lastAntrlTree = ((PythonParserImpl)parser).getLastAntlrTree();
        return result;
    }
    
    Node parseNew(String src) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, "foo").build();
        return ((PythonParserImpl)context.getCore().getParser()).parseWithNew(PythonParser.ParserMode.File, context.getCore(), source, null);
        //return (RootNode) ((PythonParserImpl)context.getCore().getParser()).getParsergetPython3NewParser(PythonParser.ParserMode.File, context.getCore(), source, null);
    }

    String parseToString(String src) {
        RootNode result = parse(src);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NodeUtil.printCompactTree(out, result);
        
        return out.toString();
    }
    
    @Test
    public void SimpleExpression01() throws Exception {
        checkTreeResult("'ahoj'");
    }
    
    @Test
    public void SimpleExpression02() throws Exception {
        checkTreeResult("'ahoj'; 2");
    }
    
    @Test
    public void SimpleExpression03() throws Exception {
        checkTreeResult("'ahoj'; 2; 1.0");
    }
    
    @Test
    public void LongString01() throws Exception {
        checkTreeResult("'''ahoj'''");
    }
    
    @Test
    public void LongString02() throws Exception {
        checkTreeResult("'''\n"
                + "ahoj\n"
                + "hello\n"
                + "good bye\n"
                + "'''");
    }
    
    @Test
    public void BinaryOp01() throws Exception {
        checkTreeResult("1 + 10");
    }
    
    @Test
    public void BinaryOp02() throws Exception {
        checkTreeResult("'ahoj' + 10");
    }
    
    @Test
    public void BinaryOp03() throws Exception {
        checkTreeResult("3 ** 2");
    }
    
    @Test
    public void BinaryOp04() throws Exception {
        checkTreeResult("3 ** 2 ** 2");
    }
    
    @Test
    public void Comparision01() throws Exception {
        checkTreeResult("3 < 10");
    }
    
    @Test
    public void Comparision02() throws Exception {
        checkTreeResult("1 < '10' > True");
    }
    
    @Test
    public void Comparision03() throws Exception {
        checkTreeResult("1 < '10' > True != 1.0");
    }
    
    @Test
    public void If01() throws Exception {
        checkTreeResult(
                "if False: \n"
              + "  10");
    }
    
    @Test
    public void Assignment01() throws Exception {
        checkTreeResult("a = 1");
    }
    
    @Test
    public void Assignment02() throws Exception {
        checkTreeResult("a = b = 1");
    }
    
    @Test
    public void Call01() throws Exception {
        checkTreeResult("foo()");
    }
    
    @Test
    public void Call02() throws Exception {
        checkTreeResult("foo(1)");
    }
    
    @Test
    public void Call03() throws Exception {
        checkTreeResult("foo(arg = 1)");
    }
    
    @Test
    public void Call04() throws Exception {
        checkSyntaxError("foo(1+arg = 1)");
    }
    
    @Test
    public void Call05() throws Exception {
        checkSyntaxError("foo(arg + 1 = 1)");
    }
    
    @Test
    public void Call06() throws Exception {
        checkTreeResult("foo(arg1 = 1, arg2 = 2)");
    }
 
    @Test
    public void Call07() throws Exception {
        checkTreeResult("foo('ahoj', arg1 = 1, arg2 = 2)");
    }
    
    @Test
    public void Call08() throws Exception {
        checkTreeResult("foo('ahoj', arg1 = 1, arg2 = 2)");
    }
    
    @Test
    public void Call09() throws Exception {
        checkTreeResult("foo(*mylist)");
    }
    
    @Test
    public void Call10() throws Exception {
        checkTreeResult("foo(*mylist1, *mylist2)");
    }
    
    @Test
    public void Call11() throws Exception {
        checkTreeResult("foo(**mydict)");
    }
    
    @Test
    public void Call12() throws Exception {
        checkTreeResult("foo(**mydict1, **mydict2)");
    }
    
    @Test
    public void Call13() throws Exception {
        checkSyntaxError("foo(**mydict1, *mylist)");
    }
    
    @Test
    public void Call14() throws Exception {
        checkSyntaxError("foo(**mydict1, 1)");
    }
    
    @Test
    public void Call15() throws Exception {
        checkSyntaxError("foo(arg1=1, 1)");
    }
    
    @Test
    public void functionDef01() throws Exception {
        checkTreeResult("def foo(): pass");
    }
    
    @Test
    public void functionDef02() throws Exception {
        checkTreeResult("def foo(): \n"
                + "  return 10\n");
    }
    
    @Test
    public void SimpleTest() throws Exception{
        String src = "def foo(): \n"
                + "  a = 10\n"
                + "  return a\n";
//        String src = 
//                  "if true: \n"
//                + "  'ahoj'\n"
//                + "  10";
        System.out.println("old parser");
        
        
        System.out.println("Result");
//      System.out.println("----------old truffle tree-----------");
        RootNode result = parse(src);
        System.out.println(NodeUtil.printCompactTreeToString(result)); 
        
//        System.out.println("-----------Antlr AST-------------");
//        StringBuilder sb = new StringBuilder();
//        (new OldParserVisitor(sb)).visit(lastAntrlTree);
//        System.out.println(sb.toString());
        
        System.out.println("------new------");
        Node resultNew = parseNew(src);
        System.out.println("----new truffle tree---");
        System.out.println(NodeUtil.printCompactTreeToString(resultNew));
        System.out.println("----new our truffle tree---");
        TreeVisitor visitor = new TreeVisitor();
        result.accept(visitor);
        System.out.println(visitor.getTree());
    }
    
    private void checkTreeResult(String source) throws Exception {
        Node resultNew = parseNew(source);
        String tree = printTreeToString(resultNew);
        File goldenFile = getGoldenFile(".tast1");
        if (!goldenFile.exists()) {
            // parse it with old parser and create golden file with this result
            // TODO, when the new parser will work, it has to be removed
            RootNode resultOld = parse(source);
            String oldTree = printTreeToString(resultOld);
            FileWriter fw = new FileWriter(goldenFile);
            try {
                fw.write(oldTree);
            }
            finally{
                fw.close();
            }
            
        }
        assertDescriptionMatches(tree, ".tast1");
    }
    
    private String printTreeToString(Node node) {
//        return NodeUtil.printCompactTreeToString(node);
        TreeVisitor visitor = new TreeVisitor();
        node.accept(visitor);
        return visitor.getTree();
    }
    
    private static class TreeVisitor implements NodeVisitor {
        private final int MAX_SOURCESECTION_LENGTH = 20;
        private final StringBuilder sb;
        private int level;
        
        public TreeVisitor() {
            this.sb = new StringBuilder();
            this.level = 0;
        }
        
        public String getTree() {
            return sb.toString();
        }
        
        
        public boolean visit(ModuleRootNode module) {
            nodeHeader(module, module.getName());
            return true;
        } 
        
        public boolean detail(InnerRootNode module) {
            return true;
        }
        
        public boolean visit(FunctionDefinitionNode node) {
            nodeHeader(node, node.getFunctionName());
            level++;
            indent(level); sb.append("Arguments:"); 
            if(node.getDefaults() == null || node.getDefaults().length == 0) {
                sb.append(" None\n");
            } else {
                level++;
                for (ExpressionNode arg : node.getDefaults()) {
                    visit(arg);
                }
                level--;
            }
            indent(level); sb.append("KwArguments:"); 
            if(node.getKwDefaults() == null || node.getKwDefaults().length == 0) {
                sb.append(" None\n");
            } else {
                level++;
                for (ExpressionNode arg : node.getKwDefaults()) {
                    visit(arg);
                }
                level--;
            }
            visit(node.getFunctionRoot());
            level--;
            return false;
        }
        
        public boolean visit (ExpressionNode.ExpressionWithSideEffect node) {
            nodeHeader(node);
            level++;
            indent(level);sb.append("Expression: ");
            visit(node.getExpression());;
            indent(level);sb.append("SideEffect: ");
            visit(node.getSideEffect());
            level--;
            return false;
        }
        
        private boolean visitChildren(Node node) {
            for (Node child : node.getChildren()) {
                visit(child);
            }
            return true;
        }
        
        private void nodeHeader(Node node) {
            addNodeClassName(node);
            sb.append(" ");
            addSourceSection(node.getSourceSection());
            sb.append("\n");
        }
        
        private void nodeHeader(Node node, String name) {
            addNodeClassName(node);
            sb.append(" Name: ").append(name).append(" ");
            addSourceSection(node.getSourceSection());
            sb.append("\n");
        }
        
        private void addNodeClassName(Node node) {
            sb.append(node.getClass().getSimpleName());
        }
        
        private void addSourceSection(SourceSection ss) {
            if (ss != null) {
                sb.append("SourceSection: [").append(ss.getCharIndex()).append(",").append(ss.getCharEndIndex()).append("]");
                CharSequence chars = ss.getCharacters();
                String textToPrint = chars.length() < MAX_SOURCESECTION_LENGTH ? chars.toString() : chars.subSequence(0, MAX_SOURCESECTION_LENGTH).toString() + "...";
                textToPrint = textToPrint.replaceAll("\\r\\n|\\r|\\n", "\u21b5");
                sb.append(" `").append(textToPrint).append("`");
            } else {
                sb.append("SourceSection: None");
            }
        }
        
        @Override
        public boolean visit(Node node) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                indent(level);
            }
            boolean visitChildren = true;
            if (node instanceof ModuleRootNode) {
                visitChildren = visit((ModuleRootNode) node);
            } else if (node instanceof FunctionDefinitionNode) {
                visitChildren = visit((FunctionDefinitionNode) node);
            } else if (node instanceof ExpressionNode.ExpressionWithSideEffect) {
                visitChildren = visit((ExpressionNode.ExpressionWithSideEffect)node);
            } else {
                nodeHeader(node);
            }
            if (visitChildren) {
                level++;
                visitChildren(node);
                level--;
            }
            return false;
        }
        
        private void indent(int level) {
            for (int i = 0; i < level; i++) {
                sb.append("  ");
            }
        }
        
        
    }
    
    private void checkSyntaxError(String source) throws Exception {
        boolean thrown = false;
        try {
            Node resultNew = parseNew(source);
        } catch (PException e) {
            thrown = e.isSyntaxError();
        }
        
        assertTrue("Expected SyntaxError was not thrown.", thrown);
    }
    
    protected void assertDescriptionMatches(String actual, String goldenFileExt) throws IOException {
        File goldenFile = getGoldenFile(goldenFileExt);
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
    
    public File getGoldenDataDir() {
        String dataDirPath = System.getProperty("python.home");
        dataDirPath += "/com.oracle.graal.python.test/goldenData";
        File dataDir = new File(dataDirPath);
        assertTrue("The golden data folder, was not found.", dataDir.exists());
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
    
    public static class OldParserVisitor extends AbstractParseTreeVisitor<Object> {
        private StringBuilder builder;
        private int indent = 0;
        String [] ruleNames = Python3Parser.ruleNames;
        
        public OldParserVisitor(StringBuilder builder) {
            super();
            this.builder = builder;
        }
        
        
        
        @Override
        public Object visit(ParseTree tree) {
            
            builder.append("Tree: " + tree).append("\n");
            System.out.println(tree.getText());
            return super.visit(tree);
        }

        @Override
        public Object visitChildren(RuleNode node) {
            for (int i = 0; i < indent; i++) {
                builder.append("  ");
            }
            builder.append(ruleNames[node.getRuleContext().getRuleIndex()]);
            builder.append("[");
            builder.append(((ParserRuleContext)node).getStart().getStartIndex());
            builder.append(",");
            builder.append(((ParserRuleContext)node).getStop().getStopIndex());
            builder.append("]");
            builder.append(node.getText());
            builder.append("\n");
            indent++;
            Object result = super.visitChildren(node); 
            indent--;
            return result;
        }

        
        @Override
        public Object visitTerminal(TerminalNode node) {
           // builder.append("Terminal: " + node).append("\n");
            return super.visitTerminal(node); //To change body of generated methods, choose Tools | Templates.
        }
        
        
        
    }
    
}
