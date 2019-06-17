/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import static com.oracle.graal.python.test.parser.ParserTestBase.readFile;
import java.io.File;
import org.junit.Test;

public class PerformanceTests extends ParserTestBase {
    
    @Test
    public void assignment01() throws Exception {
        File file = getTestFileFromTestAndTestMethod();
        String source = readFile(file);
        int count = 1000;
//        Thread.sleep(10000);
        System.out.println("count:" + count);
        long start = System.currentTimeMillis();
        for(int i = 0; i < count; i++) {
            parseOld(source, name.getMethodName());
        }
        
        long end = System.currentTimeMillis();
        System.out.println("Old parsing took: " + (end - start));
//        Thread.sleep(15000);

        for(int i = 0; i < 30; i++) {
            parseNew(source,name.getMethodName());
        }
        System.out.println("count:" + count);
        start = System.currentTimeMillis();
        for(int i = 0; i < count; i++) {
            parseNew(source, name.getMethodName());
        }
        end = System.currentTimeMillis();
        System.out.println("New parsing took: " + (end - start));
        
        for(int i = 0; i < count; i++) {
            parseNew(source, name.getMethodName());
        }
//        Thread.sleep(20000);
        
        
        
//        checkTreeFromFile();
    }
}
