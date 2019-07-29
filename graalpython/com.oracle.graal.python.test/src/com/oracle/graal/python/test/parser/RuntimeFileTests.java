/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author petr
 */
public class RuntimeFileTests extends ParserTestBase {
    
//    @Test
//    public void builtins() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void _sitebuiltins() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void site() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void initCollections() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void sre_compile() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void functions() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void traceback() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void re() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void enumt() throws Exception {
//        checkScopeAndTree();
//    }
    
    @Test
    public void enumMeta() throws Exception {
        checkScopeAndTree();
    }
    
    private void checkScopeAndTree()  throws Exception{
        File testFile = getTestFileFromTestAndTestMethod();
        saveNewScope(testFile, true);
        checkScopeFromFile(testFile, true);
        saveNewTreeResult(testFile, true);
        correctSourceSections(testFile);
        checkTreeFromFile(testFile, true);
        
    }
    
    private void correctSourceSections(File testFile) throws Exception {
        String SS_TEXT = "SourceSection:";
        String LINE_TEXT = "\n";
        File goldenFile = new File(testFile.getParentFile(), getFileName(testFile) + GOLDEN_FILE_EXT);
        if (!goldenFile.exists()) {
            return;
        }
        String oldContent = readFile(goldenFile);
        File newResult = new File(testFile.getParentFile(), getFileName(testFile) + NEW_GOLDEN_FILE_EXT);
        String newContent = readFile(newResult);
        
        List<String> oldLines = Arrays.asList(oldContent.split("\n"));
        List<String> newLines = Arrays.asList(newContent.split("\n"));
        
        String beforeSS;
        String afterSS;
        int ssIndex;
        int oldLineIndex = 0;
        int newLineIndex = 0;
        StringBuilder corrected = new StringBuilder();
        String oldLine;
        String newLine;
        
        while (oldLineIndex < oldLines.size() && newLineIndex < newLines.size()) {
            oldLine = oldLines.get(oldLineIndex);
            newLine = newLines.get(newLineIndex);
            if (oldLine.equals(newLine)) {
                // the same lines
                corrected.append(oldLine).append(LINE_TEXT);
                oldLineIndex++;
                newLineIndex++;
            } else {
                ssIndex = oldLine.indexOf(SS_TEXT);
                if (ssIndex != -1) { 
                    if (newLine.startsWith(oldLine.substring(0, ssIndex))) {
                        // the same line but different source section
                        corrected.append(newLine).append(LINE_TEXT);
                        oldLineIndex++;
                        newLineIndex++;
                    } else {
                        // different lines
                        corrected.append(oldLine).append(LINE_TEXT);
                        oldLineIndex++;
                    }
                } else {
                    if (newLine.indexOf(SS_TEXT) == -1) {
                        corrected.append(oldLine).append(LINE_TEXT);
                        oldLineIndex++;
                        newLineIndex++;
                    } else {
                        corrected.append(oldLine).append(LINE_TEXT);
                        oldLineIndex++;
                    }
                }
            }
        }
        
        for (; oldLineIndex < oldLines.size(); oldLineIndex++) {
            corrected.append(oldLines.get(oldLineIndex)).append(LINE_TEXT);
        }
        
        File correctedFile = new File(testFile.getParentFile(), getFileName(testFile) + ".corrected.tast");
        FileWriter fw = new FileWriter(correctedFile);
        try {
            fw.write(corrected.toString());
        }
        finally{
            fw.close();
        }
         
    }
    
}
