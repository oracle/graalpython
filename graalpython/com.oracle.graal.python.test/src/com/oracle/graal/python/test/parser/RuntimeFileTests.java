/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RuntimeFileTests extends ParserTestBase {
    
    @Test
    public void builtins() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void _sitebuiltins() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void site() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void initCollectionsPart1() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void initCollectionsPart2() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void sre_compile() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void heapq() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functions() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functools() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void traceback() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void re() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void enumt() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void _collections_abc() throws Exception {
        checkScopeAndTree();
    }
    
    private void checkScopeAndTree()  throws Exception{
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
    
    private void checkScopeAndTreeWithCorrections()  throws Exception{
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
                if (oldLine.contains("Frame: [") && newLine.contains("Frame: [")) {
                    corrected.append(correctFrame(oldLine, newLine)).append(LINE_TEXT);
                    oldLineIndex++;
                    newLineIndex++;
                } else if (oldLine.contains("flagSlot:") && newLine.contains("flagSlot:")) {
                    corrected.append(correctSlot(oldLine, newLine)).append(LINE_TEXT);
                    oldLineIndex++;
                    newLineIndex++;
                } else if (oldLine.contains("FrameDescriptor:") && newLine.contains("FrameDescriptor:")) {
                    corrected.append(correctFrameDesc(oldLine, newLine)).append(LINE_TEXT);
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
    
    private String correctFrame(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("Frame: [");
        int newStart = newLine.indexOf("Frame: [");
        if (oldStart != newStart) {
            return oldLine;
        }
        int oldComma = oldLine.indexOf(',', oldStart);
        int newComma = newLine.indexOf(',', oldStart);
        String oldRest = oldLine.substring(oldComma);
        String newRest = newLine.substring(newComma);
        if (oldRest.equals(newRest)) {
            return newLine;
        }
        return oldLine;
    }
    
    private String correctSlot(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("flagSlot:");
        int newStart = newLine.indexOf("flagSlot:");
        if (oldStart != newStart) {
            return oldLine;
        }
        return newLine;
    }
    
    private String correctFrameDesc(String oldLine, String newLine) {
        int oldStart = oldLine.indexOf("FrameDescriptor:");
        int newStart = newLine.indexOf("FrameDescriptor:");
        if (oldStart != newStart) {
            return oldLine;
        }
        int oldBracket = oldLine.indexOf('[', oldStart);
        int newBracket = newLine.indexOf('[', oldStart);
        String oldPart = oldLine.substring(0, oldBracket);
        String newPart = newLine.substring(0, newBracket);
        if (oldPart.equals(newPart)) {
            return newLine;
        }
        return oldLine;
    }
    
}
