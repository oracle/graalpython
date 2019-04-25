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

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PClosureRootNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.InnerRootNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Set;


public class ParserTreePrinter implements NodeVisitor {
    private final int MAX_TEXT_LENGTH = 20;
        private final StringBuilder sb;
        private int level;
        
        public ParserTreePrinter() {
            this.sb = new StringBuilder();
            this.level = 0;
        }
        
        public String getTree() {
            return sb.toString();
        }
        
        
        public boolean visit(ModuleRootNode module) {
            nodeHeader(module, module.getName());
            level++;
            addSignature(module.getSignature());
            addInfoPCloserRootNode(module);
            indent(level); sb.append("Documentation: ");
            add(module.getDoc()); newLine();
            level--;
            return true;
        } 
        
        private void addInfoPCloserRootNode(PClosureRootNode node) {
            indent(level);sb.append("FreeVars: "); add(node.getFreeVars()); newLine();
            addInfoPRootNode(node);
        }
        
        private void addInfoPRootNode(PRootNode node) {
            indent(level); sb.append("NeedsCellFrame: "); 
            add(node.needsCallerFrame()); newLine();
            addInfoRootNode(node);
        }
        
        private void addInfoRootNode(RootNode node) {
            addFrameDescriptor(node.getFrameDescriptor());
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
        
        public boolean visit (FunctionRootNode node) {
            nodeHeader(node);
            level++;
            addSignature(node.getSignature());
            addFrameDescriptor(node.getFrameDescriptor());
            indent(level);sb.append("CellVars: ");
            add(node.getCellVars());
            sb.append("\n");
            PCell[] cells = node.getCells();
            indent(level);sb.append("Cells: ");
            if (cells == null || cells.length == 0) {
                sb.append("None");
            }
            for (PCell cell: cells) {
                sb.append(cell.toString()).append(", ");
            }
            sb.append("\n");
            ExecutionCellSlots executionCellSlots = node.getExecutionCellSlots();
            FrameSlot[] freeVarSlots = executionCellSlots.getFreeVarSlots();
            indent(level);sb.append("FreeVars: ");
            if (freeVarSlots == null || freeVarSlots.length == 0) {
                sb.append("None");
            }
            for(FrameSlot slot: freeVarSlots) {
                sb.append((String)slot.getIdentifier()).append(", ");
            }
            sb.append("\n");
            level--;
            return true;
        }
        
        public boolean visit (ExpressionNode.ExpressionWithSideEffect node) {
            nodeHeader(node);
            level += 2;
            indent(level-1);sb.append("Expression:"); newLine();
            visit(node.getExpression());;
            indent(level-1);sb.append("SideEffect:"); newLine();
            visit(node.getSideEffect());
            level -= 2;
            return false;
        }
        
        public boolean visit (ReturnTargetNode node) {
            nodeHeader(node);
            level++;
            indent(level);sb.append("Body: ");
            visit(node.getBody());
            indent(level);sb.append("Return Expresssion: ");
            visit(node.getReturn());
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
            newLine();
        }
        
        private void nodeHeader(Node node, String name) {
            addNodeClassName(node);
            sb.append(" Name: ").append(name).append(" ");
            addSourceSection(node.getSourceSection());
            newLine();
        }
        
        private void addNodeClassName(Node node) {
            sb.append(node.getClass().getSimpleName());
        }
        
        private void addSourceSection(SourceSection ss) {
            if (ss != null) {
                sb.append("SourceSection: [").append(ss.getCharIndex()).append(",").append(ss.getCharEndIndex()).append("]");
                add(ss.getCharacters().toString());
            } else {
                sb.append("SourceSection: None");
            }
        }
        
        private void addSignature(Signature signature) {
            indent(level);sb.append("Signature: ");
            sb.append("varArgs=");add(signature.takesVarArgs());
            sb.append(", varKeywordArgs=");add(signature.takesVarKeywordArgs());
            sb.append(", noArguments=");add(signature.takesNoArguments());
            sb.append(", positionalOnly=");add(signature.takesPositionalOnly());
            sb.append(", requiresKeywordArgs=");add(signature.takesRequiredKeywordArgs());
            newLine();
            level++;
            if (signature.takesVarArgs()) {
                indent(level); sb.append("Param Names: ");
                add(signature.getParameterIds());
                newLine();
            }
            if (signature.takesKeywordArgs()) {
                indent(level);sb.append("Keyword Names: ");
                add(signature.getKeywordNames());
                newLine();
            }
            level--;
        }
        
        private void addFrameDescriptor(FrameDescriptor fd) {
            indent(level);sb.append("FrameDescriptor: ");
            if (fd.getSlots().size() == 0) {
                sb.append(" Empty"); newLine();
                return;
            }
            sb.append(fd.getSlots().size()).append(" slots [");
            Set<Object> identifiers = fd.getIdentifiers();
            String[] names = new String[identifiers.size()];
            int i = 0;
            for(Object identifier: identifiers) {
                names[i++] = (String)identifier;
            }
            add(names);
            sb.append("]");
            newLine();
        }
        
        private void add(String text) {
            if (text != null && text.length() > 0) {
                String textToPrint = text.length() < MAX_TEXT_LENGTH ? text : text.subSequence(0, MAX_TEXT_LENGTH).toString() + "...";
                textToPrint = textToPrint.replaceAll("\\r\\n|\\r|\\n", "\u21b5");
                sb.append("`").append(textToPrint).append("`");
            } else {
                sb.append("None");
            }
        }
        private void add(String[] array) {
            if (array == null || array.length == 0) {
                sb.append("None");
            } else {
                boolean first = true;
                for (String text : array) {
                    if (!first) {
                        sb.append(", ");
                    } else {
                        first = false;
                    }
                    sb.append(text);
                }
            }
        }
        
        private void add(boolean value) {
            if (value) {
                sb.append("True");
            } else {
                sb.append("False");
            }
        }
        
        private void newLine() {
            sb.append("\n");
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
            } else if (node instanceof FunctionRootNode) {
                visitChildren = visit((FunctionRootNode) node);
            } else if (node instanceof ReturnTargetNode) {
                visitChildren = visit((ReturnTargetNode) node);
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
