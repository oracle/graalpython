/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import org.junit.Assume;
import org.junit.Test;

public class Performance2Tests extends ParserTestBase {
    
    static boolean isWarm = false;
    
    @Test
    public void trueFalse() throws Exception {
        Assume.assumeTrue("/home/petr".equals(System.getProperty("user.home")));
        measureFile(new File("/home/petr/labs/parser/tests/performance/trueFalse01.py"), 100);
        measureFile(new File("/home/petr/labs/parser/tests/performance/functions01.py"), 100);
        measureFile(new File("/home/petr/labs/parser/tests/performance/assignment.py"), 100);
        measureFile(new File("/home/petr/labs/parser/tests/performance/classes01.py"), 100);
        measureFile(new File("/home/petr/labs/parser/tests/performance/posnumber01.py"), 100);
        measureFile(new File("/home/petr/labs/parser/tests/performance/negnumber01.py"), 100);
    }
    
    private void measureFile(File file, int count) throws Exception {
        Source source = createSource(file);
        
        warmup(source);
        long[] oldParserTimes = new long[count];
        long[] newParserTimes = new long[count];
        
        long start;
        long end;
        
        long avgOld = 0;
        long avgNew = 0;
        
        System.out.print(file.getAbsolutePath());        
        for (int i = 0; i < count; i++) {
//            System.out.print(i);
            System.out.print(".");
            start = System.currentTimeMillis();
            parseOld(source, PythonParser.ParserMode.File);
            end = System.currentTimeMillis();
            oldParserTimes[i] = end-start;
            avgOld += oldParserTimes[i];
//            System.out.print("\t\t" + oldParserTimes[i]);
            start = System.currentTimeMillis();
            parseNew(source, PythonParser.ParserMode.File);
            end = System.currentTimeMillis();
            newParserTimes[i] = end-start;
//            System.out.println("\t\t" + newParserTimes[i]);
            avgNew += newParserTimes[i];
        }
//        System.out.println("--------------------------------------------------------------");
        System.out.println(" old: " + avgOld/count + " new: " + avgNew/count);
        
    }
    
    private void warmup(Source source) throws Exception{
        
        if (!isWarm) {
            System.out.print("Warming ");
            for (int i = 0; i < 7; i++) {
                parseOld(source, PythonParser.ParserMode.File);
                parseNew(source, PythonParser.ParserMode.File);
                System.out.print(".");
            }
            isWarm = true;
            System.out.println(" done");
        }
    }
}
