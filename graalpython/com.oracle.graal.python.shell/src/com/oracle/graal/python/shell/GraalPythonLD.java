/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jline.internal.InputStreamReader;

public class GraalPythonLD extends GraalPythonCompiler {
    private static final String LLVM_NM = "llvm-nm";
    private static final long BC_MAGIC_WORD = 0xdec04342L; // 'BC' c0de
    private static final long WRAPPER_MAGIC_WORD = 0x0B17C0DEL;

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

    private Set<String> definedSymbols = new HashSet<>();
    private Set<String> undefinedSymbols = new HashSet<>();

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
                        addFile(arg);
                    } else if (arg.startsWith("-L")) {
                        libraryDirs.add(arg.substring(2));
                    } else if (arg.startsWith("-l")) {
                        List<String> bcFiles = searchLib(libraryDirs, arg.substring(2));
                        for (String bcFile : bcFiles) {
                            if (probeContentType(Paths.get(bcFile))) {
                                logV("library input:", bcFile);
                                addFile(bcFile);
                            } else {
                                droppedArgs.add(bcFile + "(dropped as library input)");
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

    void addFile(String f) {
        fileInputs.add(f);

        try {
            // symbols defined up to here
            ProcessBuilder nm = new ProcessBuilder();
            nm.command(LLVM_NM, "-g", "--defined-only", f);
            nm.redirectInput(Redirect.INHERIT);
            nm.redirectError(Redirect.INHERIT);
            nm.redirectOutput(Redirect.PIPE);
            Process nmProc = nm.start();
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(nmProc.getInputStream()))) {
                String line = null;
                while ((line = buffer.readLine()) != null) {
                    String[] symboldef = line.split(" ");
                    if (symboldef.length >= 2) {
                        definedSymbols.add(symboldef[symboldef.length - 1]);
                    }
                }
            }
            nmProc.waitFor();

            // remove now resolved symbols
            undefinedSymbols.removeAll(definedSymbols);

            // add symbols undefined now
            nm.command(LLVM_NM, "-u", f);
            nm.redirectInput(Redirect.INHERIT);
            nm.redirectError(Redirect.INHERIT);
            nm.redirectOutput(Redirect.PIPE);
            nmProc = nm.start();
            try (BufferedReader buffer = new BufferedReader(new InputStreamReader(nmProc.getInputStream()))) {
                String line = null;
                while ((line = buffer.readLine()) != null) {
                    String[] symboldef = line.split(" ");
                    if (symboldef.length >= 2) {
                        String sym = symboldef[symboldef.length - 1];
                        if (!definedSymbols.contains(sym)) {
                            undefinedSymbols.add(sym);
                        }
                    }
                }
            }
            nmProc.waitFor();
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
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
                                Thread.currentThread().interrupt();
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
        Path temp = Files.createTempDirectory(Long.toString(System.nanoTime()));

        ProcessBuilder extractAr = new ProcessBuilder();
        extractAr.redirectInput(Redirect.INHERIT);
        extractAr.redirectError(Redirect.INHERIT);
        extractAr.redirectOutput(Redirect.PIPE);
        extractAr.directory(temp.toFile());
        logV("ar", path);
        // "ar t" lists the members one per line
        extractAr.command("ar", "t", path);
        Process start = extractAr.start();
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(start.getInputStream()))) {
            String line = null;
            while ((line = buffer.readLine()) != null) {
                members.add(temp.toFile().getAbsolutePath() + File.separator + line);
            }
        }
        start.waitFor();
        // actually extract them now
        extractAr.redirectOutput(Redirect.INHERIT);
        extractAr.command("ar", "xv", path);
        extractAr.start().waitFor();

        // ar has special semantics w.r.t ordering of included symbols. we try to emulate the smarts
        // of GNU ld by listing all undefined symbols until here, extracting only those that we are
        // still missing, and adding them to a bitcode file that will only add these to the linked
        // product.
        // According to some emscripten documentation and ML discussions, this is actually an error
        // in the build process, because such a smart linker should not be assumed for POSIX, but it
        // seems ok to emulate this at least for the very common case of ar archives with symbol
        // definitions that overlap what's defined in explicitly include .o files
        outer: for (String f : members) {
            if (probeContentType(Paths.get(f))) {
                HashSet<String> definedFuncs = new HashSet<>();
                HashSet<String> definedGlobals = new HashSet<>();

                ProcessBuilder nm = new ProcessBuilder();
                nm.command(LLVM_NM, "--defined-only", f);
                nm.redirectInput(Redirect.INHERIT);
                nm.redirectError(Redirect.INHERIT);
                nm.redirectOutput(Redirect.PIPE);
                Process nmProc = nm.start();
                try (BufferedReader buffer = new BufferedReader(new InputStreamReader(nmProc.getInputStream()))) {
                    String line = null;
                    while ((line = buffer.readLine()) != null) {
                        String[] symboldef = line.split(" ");
                        if (symboldef.length == 3) {
                            // format is ------- CHAR FUNCNAME
                            if (symboldef[1].toLowerCase().equals("t")) {
                                definedFuncs.add(symboldef[2].trim());
                            } else if (symboldef[1].toLowerCase().equals("d")) {
                                definedGlobals.add(symboldef[2].trim());
                            } else {
                                // keep all if we have symbols that we wouldn't know what to do with
                                logV("Not extracting from ", f, " because there are non-strong function or global symbols");
                                continue outer;
                            }
                        }
                    }
                }
                nmProc.waitFor();

                ArrayList<String> extractCmd = new ArrayList<>();
                extractCmd.add("llvm-extract");
                for (String def : definedFuncs) {
                    if (!definedSymbols.contains(def)) {
                        definedSymbols.add(def);
                        undefinedSymbols.remove(def);
                        extractCmd.add("-func");
                        extractCmd.add(def);
                    }
                }
                for (String def : definedGlobals) {
                    if (!definedSymbols.contains(def)) {
                        definedSymbols.add(def);
                        undefinedSymbols.remove(def);
                        extractCmd.add("-glob");
                        extractCmd.add(def);
                    }
                }
                extractCmd.add(f);
                extractCmd.add("-o");
                extractCmd.add(f);
                exec(extractCmd);
            }
        }

        return members;
    }

    private void launchLD() {
        ldArgs.add(outputFilename);
        ldArgs.addAll(fileInputs);
        exec(ldArgs);
    }

    private static boolean probeContentType(Path path) {
        long magicWord = readMagicWord(path);
        if (magicWord == BC_MAGIC_WORD || magicWord == WRAPPER_MAGIC_WORD) {
            return true;
        }
        return false;
    }

    private static long readMagicWord(Path path) {
        try (InputStream is = new FileInputStream(path.toString())) {
            byte[] buffer = new byte[4];
            if (is.read(buffer) != buffer.length) {
                return 0;
            }
            return Integer.toUnsignedLong(ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder()).getInt());
        } catch (IOException e) {
            return 0;
        }
    }
}
