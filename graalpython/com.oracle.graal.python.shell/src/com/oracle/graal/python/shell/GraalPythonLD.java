/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import jline.internal.InputStreamReader;

public class GraalPythonLD extends GraalPythonCompiler {
    private static List<String> linkPrefix = Arrays.asList(new String[]{
                    "llvm-link",
                    "-o",
    });

    static void main(String[] args) {
        new GraalPythonLD().run(args);
    }

    private String outputFilename;
    private List<String> fileInputs;
    private List<String> ldArgs;

    private void run(String[] args) {
        parseOptions(args);
        launchLD();
    }

    private void parseOptions(String[] args) {
        outputFilename = A_OUT;
        ldArgs = new ArrayList<>(linkPrefix);
        fileInputs = new ArrayList<>();
        List<String> droppedArgs = new ArrayList<>();
        List<String> libraryDirs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-o":
                    i++;
                    if (i >= args.length) {
                        throw new RuntimeException("-o needs an argument");
                    }
                    outputFilename = args[i];
                    break;
                case "-v":
                    verbose = true;
                    break;
                default:
                    if (arg.endsWith(".o") || arg.endsWith(".bc")) {
                        fileInputs.add(arg);
                    } else if (arg.startsWith("-L")) {
                        libraryDirs.add(arg.substring(2));
                    } else if (arg.startsWith("-l")) {
                        List<String> bcFiles = searchLib(libraryDirs, arg.substring(2));
                        for (String bcFile : bcFiles) {
                            try {
                                if (Files.probeContentType(Paths.get(bcFile)).contains("llvm-ir-bitcode")) {
                                    logV("library input:", bcFile);
                                    fileInputs.add(bcFile);
                                } else {
                                    droppedArgs.add(bcFile + "(dropped as library input)");
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } else {
                        droppedArgs.add(arg);
                    }
            }
        }
        // we only use llvm-link, which doesn't support any ld flags, so we only parse out the
        // object files given on the commandline and see if these are bytecode files we can work
        // with
        logV("Dropped args: ", droppedArgs);
    }

    private List<String> searchLib(List<String> libraryDirs, String lib) {
        List<String> bcFiles = new ArrayList<>();
        String[] suffixes = new String[]{".bc", ".o", ".a", ".so"};
        String[] prefixes = new String[]{"lib", ""};
        for (String libdir : libraryDirs) {
            for (String prefix : prefixes) {
                for (String suffix : suffixes) {
                    Path path = Paths.get(libdir, prefix + lib + suffix);
                    String pathString = path.toAbsolutePath().toString();
                    logV("Checking for library:", pathString);
                    if (Files.exists(path)) {
                        if (suffix.equals(".a")) {
                            // extract members
                            try {
                                bcFiles.addAll(arMembers(pathString));
                            } catch (IOException | InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            bcFiles.add(pathString);
                            break;
                        }
                    }
                }
            }
        }
        logV("Found bitcode files for library:", bcFiles);
        return bcFiles;
    }

    private Collection<? extends String> arMembers(String path) throws IOException, InterruptedException {
        List<String> members = new ArrayList<>();
        File temp = File.createTempFile(path, Long.toString(System.nanoTime()));
        temp.delete();
        temp.mkdir();
        temp.deleteOnExit();

        ProcessBuilder extractAr = new ProcessBuilder();
        extractAr.redirectInput(Redirect.INHERIT);
        extractAr.redirectError(Redirect.INHERIT);
        extractAr.redirectOutput(Redirect.PIPE);
        extractAr.directory(temp);
        logV("ar", path);
        // "ar t" lists the members one per line
        extractAr.command("ar", "t", path);
        Process start = extractAr.start();
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(start.getInputStream()))) {
            String line = null;
            while ((line = buffer.readLine()) != null) {
                members.add(temp.getAbsolutePath() + File.separator + line);
            }
        }
        start.waitFor();
        // actually extract them now
        extractAr.redirectOutput(Redirect.INHERIT);
        extractAr.command("ar", "xv", path);
        extractAr.start().waitFor();
        return members;
    }

    private void launchLD() {
        ldArgs.add(outputFilename);
        ldArgs.addAll(fileInputs);
        exec(ldArgs);
    }
}
