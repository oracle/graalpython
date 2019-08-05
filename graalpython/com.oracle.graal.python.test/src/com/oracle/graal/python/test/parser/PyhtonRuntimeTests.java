/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import com.oracle.graal.python.runtime.PythonParser;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author petr
 */
public class PyhtonRuntimeTests extends ParserTestBase {
    
    private int numOfFiles = 0;
    private List<String> failingFiles = new ArrayList<>();
    
    @Test
    public void testSingleFile() throws Exception {
        String path = "/home/petr/labs/sstparser/graalpython/graalpython/lib-graalpython/functions.py";
        File file = new File(path);
        String source = readFile(file);
        parseNew(source,file.getName(), PythonParser.ParserMode.File);
    }
    
//    @Test
    public void libGraalPyhton() throws Exception {
        String pathGraalLib = "/home/petr/labs/sstparser/graalpython/graalpython/lib-graalpython";
        File folderGraalLib = new File(pathGraalLib);
        assertTrue("The " + pathGraalLib + " doesn't exists!", folderGraalLib.exists());
        
        String pathPythonLib = "/home/petr/labs/sstparser/graalpython/graalpython/lib-python";
        File folderPythonLib = new File(pathPythonLib);
        assertTrue("The " + pathPythonLib + " doesn't exists!", folderGraalLib.exists());
        numOfFiles = 0;
        
        long start = System.currentTimeMillis();
        checkFolder(folderGraalLib);
        checkFolder(folderPythonLib);
        long end = System.currentTimeMillis();
        if (!failingFiles.isEmpty()) {
            for (String path : failingFiles) {
                System.out.println(path);
            }
            System.out.println("Number of failing parsing: " + failingFiles.size());
            System.out.println("Correctly parsed files: " + (numOfFiles - failingFiles.size()));
        }
        System.out.println("Parsing " + numOfFiles + " takes " + (end - start) + "ms.");
    }
    
    
    private void checkFolder(File folder) throws Exception {
        assertTrue("The " + folder.getAbsolutePath() + " is not directory!", folder.isDirectory());
        for (File file : folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() || pathname.getName().endsWith(".py");
            }
        })) {
            if (file.isFile()) {
                String name = file.getName();
//                if (!file.getPath().endsWith(name)){
                    String source = readFile(file);
                    
                    try {
                        parseNew(source,file.getName(), PythonParser.ParserMode.File);
//                        parseOld(source,file.getName());
                    } catch (Exception se) {
                        failingFiles.add(file.getAbsolutePath());
                    } 
                    numOfFiles++;
//                }
            } else {
                if (!file.getAbsolutePath().contains("lib2to3/tests")) {
                    checkFolder(file);
                }
            }
        }
    }
    
}
