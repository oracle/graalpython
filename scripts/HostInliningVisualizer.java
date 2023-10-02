/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
// run simply as: java path/to/HostInliningVisualizer.java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class HostInliningVisualizer extends JFrame {
    private JTree tree;
    private DefaultTreeModel treeModel;

    private static DefaultMutableTreeNode parse(String filePath) throws IOException {
        DefaultMutableTreeNode logData = new DefaultMutableTreeNode("Root");
        int[] indentation = new int[100];
        DefaultMutableTreeNode[] levels = new DefaultMutableTreeNode[100];
        indentation[0] = 0;
        levels[0] = logData;

        for (String rawLine : Files.readAllLines(Paths.get(filePath))) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(line);
            int currentIndentation = rawLine.length() - rawLine.trim().length();
            for (int i = 0; i < indentation.length; i++) {
                if (levels[i] == null || indentation[i] == currentIndentation) {
                    levels[i] = newNode;
                    levels[i - 1].add(newNode);
                    indentation[i] = currentIndentation;
                    break;
                }
                if (indentation[i] > currentIndentation) {
                    System.err.println("Looks like wronlgy indented line: " + line);
                }
            }
        }
        return logData;
    }

    private void initGui(String title, DefaultMutableTreeNode root) {
        treeModel = new DefaultTreeModel(root);
        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);

        JScrollPane scrollPane = new JScrollPane(tree);
        JPanel searchPanel = createPanel();

        getContentPane().add(searchPanel, "North");
        getContentPane().add(scrollPane);
        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 300);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createPanel() {
        JTextField searchField = new JTextField(20);
        searchField.addActionListener(e -> searchNodes(searchField.getText()));

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchNodes(searchField.getText()));

        JButton collapseButton = new JButton("Collapse All");
        collapseButton.addActionListener(e -> collapseAllNodes());

        JPanel searchPanel = new JPanel();
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(collapseButton);
        return searchPanel;
    }

    private void collapseAllNodes() {
        int row = tree.getRowCount() - 1;
        while (row >= 0) {
            tree.collapseRow(row);
            row--;
        }
    }

    private void searchNodes(String searchText) {
        if (searchText.isEmpty()) {
            tree.clearSelection();
            return;
        }
        searchInNode((DefaultMutableTreeNode) treeModel.getRoot(), searchText);
    }

    private void searchInNode(DefaultMutableTreeNode node, String searchText) {
        if (node.toString().toLowerCase().contains(searchText.toLowerCase())) {
            TreePath path = new TreePath(node.getPath());
            tree.addSelectionPath(path);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            searchInNode(childNode, searchText);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Error: expects one argument - filename with host inlining log");
            System.exit(1);
        }
        var app = new HostInliningVisualizer();
        try {
            DefaultMutableTreeNode logData = parse(args[0]);
            SwingUtilities.invokeLater(() -> app.initGui(args[0], logData));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
