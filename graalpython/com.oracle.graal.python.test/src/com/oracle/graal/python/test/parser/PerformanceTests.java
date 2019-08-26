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

import com.oracle.graal.python.runtime.PythonParser;
import static com.oracle.graal.python.test.parser.ParserTestBase.readFile;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;

public class PerformanceTests extends ParserTestBase {
    int count = 1;

    private static String[] paths = new String[]{
                    "/home/petr/labs/sstparser/graalpython/graalpython/lib-graalpython",
                    "/home/petr/labs/sstparser/graalpython/graalpython/lib-python"
    };

    @Test
    public void folders01() throws Exception {
        Assume.assumeTrue("/home/petr/".equals(System.getProperty("user.home")));
        int numberOfOKParsed = 0;
        int numberOfWrongParsed = 0;
        long start;
        long end;
        long time = 0;
        long folderTime = 0;
        StringBuilder sb = new StringBuilder();
        int[] parseResult;

        for (String path : paths) {
            File folder = new File(path);
            assertTrue(path + " doesn't found.", folder.exists());
            assertTrue(path + " is not folder.", folder.isDirectory());
            start = System.currentTimeMillis();
            parseResult = parseFolder(folder);
            end = System.currentTimeMillis();
            folderTime = end - start;
            time += folderTime;
            numberOfOKParsed += parseResult[0];
            numberOfWrongParsed += parseResult[1];
            sb.append(path).append(" ").append(parseResult[0] + parseResult[1]).append(" files parsed in ").append(folderTime).append(" ms\n");
        }
        sb.append("\n Overall parsed ").append(numberOfOKParsed + numberOfWrongParsed).append(" (").append(numberOfWrongParsed).append(" was parsed with an error) in ").append(time).append("ms.\n");
        System.out.println(sb.toString());
        addRecordToStatisticFile("folders01 - " + (numberOfOKParsed + numberOfWrongParsed) + " (" + numberOfWrongParsed + " was parsed with and error ) in " + time + "ms.");
    }

    @Test
    public void folders02() throws Exception {
        Assume.assumeTrue("/home/petr/".equals(System.getProperty("user.home")));
        System.out.println("Test2");
        Thread.sleep(3000);
        int numberOfOKParsed = 0;
        int numberOfWrongParsed = 0;

        long time = 0;
        StringBuilder sb = new StringBuilder();
        long[] parseResult;

        for (String path : paths) {
            File folder = new File(path);
            assertTrue(path + " doesn't found.", folder.exists());
            assertTrue(path + " is not folder.", folder.isDirectory());
            parseResult = parseFolderOfSources(folder);
            numberOfOKParsed += parseResult[0];
            numberOfWrongParsed += parseResult[1];
            time += parseResult[2];
            sb.append(path).append(" ").append(parseResult[0] + parseResult[1]).append(" files parsed in ").append(parseResult[2]).append(" ms\n");
        }
        sb.append("\n Overall parsed ").append(numberOfOKParsed + numberOfWrongParsed).append(" (").append(numberOfWrongParsed).append(" was parsed with an error) in ").append(time).append("ms.\n");
        System.out.println(sb.toString());
        addRecordToStatisticFile("folders02 - " + (numberOfOKParsed + numberOfWrongParsed) + " (" + numberOfWrongParsed + " was parsed with and error ) in " + time + "ms.");
    }

    private void addRecordToStatisticFile(String record) throws Exception {
        String dataDirPath = System.getProperty("python.home");
        dataDirPath += "/com.oracle.graal.python.test/src/com/oracle/graal/python/test/parser/";
        File folder = new File(dataDirPath);
        File statistics = new File(folder, "PerformanceTests.statistics.txt");
        String content = statistics.exists() ? readFile(statistics) : "";
        content = content.trim();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        content = content + "\n" + formatter.format(date) + ": " + record;
        FileWriter fw = new FileWriter(statistics);
        try {
            fw.write(content);
        } finally {
            fw.close();
        }
    }

    private long[] parseFolderOfSources(File folder) throws Exception {
        int numberOfOKParsed = 0;
        int numberOfWrongParsed = 0;

        List<Source> sources = getSources(folder);

        long start = System.currentTimeMillis();
        for (Source source : sources) {
            if (parseSource(source)) {
                numberOfOKParsed++;
            } else {
                numberOfWrongParsed++;
            }
        }
        long end = System.currentTimeMillis();
        long time = end - start;

        for (File file : folder.listFiles(new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        })) {
            long[] result = parseFolderOfSources(file);
            numberOfOKParsed += result[0];
            numberOfWrongParsed += result[1];
            time += result[2];
        }
        return new long[]{numberOfOKParsed, numberOfWrongParsed, time};
    }

    private List<Source> getSources(File folder) throws Exception {
        List<Source> sources = new ArrayList<>();
        for (File file : folder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.getName().endsWith(".py");
            }
        })) {
            TruffleFile src = context.getEnv().getTruffleFile(file.getAbsolutePath());
            Source source = context.getLanguage().newSource(context, src, getFileName(file));
            sources.add(source);
        }
        return sources;
    }

    private int[] parseFolder(File folder) throws Exception {
        int numberOfOKParsed = 0;
        int numberOfWrongParsed = 0;
        for (File file : folder.listFiles(new FileFilter() {

            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().endsWith(".py");
            }
        })) {
            if (file.isDirectory()) {
                int[] result = parseFolder(file);
                numberOfOKParsed += result[0];
                numberOfWrongParsed += result[1];
            } else {
                if (parseFile(file)) {
                    numberOfOKParsed++;
                } else {
                    numberOfWrongParsed++;
                }
            }
        }
        return new int[]{numberOfOKParsed, numberOfWrongParsed};
    }

    private boolean parseFile(File file) throws Exception {
        TruffleFile src = context.getEnv().getTruffleFile(file.getAbsolutePath());
        Source source = context.getLanguage().newSource(context, src, getFileName(file));
        PythonParser parser = context.getCore().getParser();
        try {
            parser.parse(PythonParser.ParserMode.File, context.getCore(), source, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean parseSource(Source source) throws Exception {
        PythonParser parser = context.getCore().getParser();
        try {
            parser.parse(PythonParser.ParserMode.File, context.getCore(), source, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void executePerformanceTest(File file) throws Exception {
        String source = readFile(file);

        System.out.println(name.getMethodName() + " count:" + count);
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            parseOld(source, name.getMethodName(), PythonParser.ParserMode.File);
        }

        long end = System.currentTimeMillis();
        System.out.println(name.getMethodName() + " old parsing took: " + (end - start));

        for (int i = 0; i < 30; i++) {
            parseNew(source, name.getMethodName(), PythonParser.ParserMode.File);
        }
        System.out.println(name.getMethodName() + " count:" + count);
        start = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            parseNew(source, name.getMethodName(), PythonParser.ParserMode.File);
        }
        end = System.currentTimeMillis();
        System.out.println(name.getMethodName() + " new parsing took: " + (end - start));
    }

}
