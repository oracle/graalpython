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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class GraalPythonCC extends GraalPythonCompiler {
    private String outputFilename;
    private boolean linkExecutable;
    private boolean link;
    private boolean linkLLI;
    private Boolean compile;
    private List<String> clangArgs;
    private List<String> execLinkArgs;
    private List<String> fileInputs;
    private boolean isCpp;
    private boolean allowCpp;

    GraalPythonCC() {
    }

    private static List<String> execLinkPrefix = Arrays.asList(new String[]{
                    "clang",
                    "-fembed-bitcode",
                    "-fPIC",
                    "-ggdb",
                    "-O1",
    });
    private static List<String> clangPrefix = Arrays.asList(new String[]{
                    "clang",
                    "-emit-llvm",
                    "-fPIC",
                    "-Wno-int-to-void-pointer-cast",
                    "-Wno-int-conversion",
                    "-Wno-incompatible-pointer-types-discards-qualifiers",
                    "-ggdb",
                    "-O1",
    });
    private static List<String> optPrefix = Arrays.asList(new String[]{
                    "opt",
                    "-mem2reg",
                    "-globalopt",
                    "-simplifycfg",
                    "-constprop",
                    "-always-inline",
                    "-instcombine",
                    "-dse",
                    "-loop-simplify",
                    "-reassociate",
                    "-licm",
                    "-gvn",
                    "-o",
    });

    static void main(String[] args) {
        new GraalPythonCC().run(args);
    }

    private void run(String[] args) {
        parseOptions(args);
        if (!allowCpp && isCpp) {
            // cannot use streaming API anyMatch for this on SVM
            for (String s : clangArgs) {
                if (s.contains("--sysroot")) {
                    // nasty, nasty
                    logV("Refusing to compile C++ code in sandboxed mode, because we cannot actually do it");
                    try {
                        Files.createFile(Paths.get(outputFilename));
                    } catch (IOException e) {
                    }
                    return;
                }
            }
        }
        launchCC();
    }

    private void parseOptions(String[] args) {
        outputFilename = A_OUT;
        linkExecutable = true;
        linkLLI = false;
        link = true;
        verbose = false;
        compile = null;
        clangArgs = new ArrayList<>(clangPrefix);
        execLinkArgs = new ArrayList<>(execLinkPrefix);
        fileInputs = new ArrayList<>();
        isCpp = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-o":
                    clangArgs.add(arg);
                    i++;
                    if (i >= args.length) {
                        throw new RuntimeException("-o needs an argument");
                    }
                    outputFilename = arg = args[i];
                    break;
                case "-shared":
                    linkExecutable = false;
                    break;
                case "-c":
                    link = false;
                    linkExecutable = false;
                    break;
                case "--link-lli-scripts":
                    linkLLI = true;
                    continue; // skip adding this to clang args
                case "-v":
                    if (!verbose) {
                        verbose = true;
                        continue; // the first verbose is not passed on to clang
                    }
                    break;
                case "-allowcpp":
                    allowCpp = true;
                    continue;
                default:
                    if (arg.endsWith(".o") || arg.endsWith(".bc")) {
                        if (compile == null) {
                            compile = false;
                        } else if (compile != false) {
                            throw new RuntimeException("cannot mix source and compiled sources");
                        }
                        fileInputs.add(arg);
                    } else if (arg.endsWith(".c") || arg.endsWith(".cc") || arg.endsWith(".cpp") || arg.endsWith(".cxx")) {
                        if (arg.endsWith(".cpp") || arg.endsWith(".cxx")) {
                            isCpp = true;
                        }
                        if (compile == null) {
                            compile = true;
                        } else if (compile != true) {
                            throw new RuntimeException("cannot mix source and compiled sources");
                        }
                        fileInputs.add(arg);
                    } else {
                        execLinkArgs.add(arg);
                    }
            }
            clangArgs.add(arg);
        }
        String targetFlags = System.getenv("LLVM_TARGET_FLAGS");
        if (targetFlags != null) {
            clangArgs.addAll(Arrays.asList(targetFlags.split(" ")));
        }
        if (isCpp) {
            clangArgs.add("-stdlib=libc++");
        }
    }

    private void launchCC() {
        // run the clang compiler to generate bc files
        try {
            Files.delete(Paths.get(outputFilename));
        } catch (IOException e) {
            // no matter;
        }

        if (compile) {
            exec(clangArgs);
            logV("opt: ", fileInputs);
            if (!Files.exists(Paths.get(outputFilename))) {
                // if no explicit output filename was given or produced, we search the commandline
                // for files for which now have a bc file and optimize those. This happens when you
                // pass multiple .c files to clang and ask it to emit llvm bitcode
                for (String f : fileInputs) {
                    String bcFile = bcFileFromFilename(f);
                    assert Files.exists(Paths.get(bcFile));
                    optFile(bcFile);
                    try {
                        String objFile = objectFileFromFilename(f);
                        logV("Optimized:", bcFile, "->", objFile);
                        Files.move(Paths.get(bcFile), Paths.get(objFile));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                optFile(outputFilename);
            }
        }

        if (link) {
            linkShared(fileInputs);
            if (linkExecutable) {
                try {
                    Path linkedBCfile = Files.move(Paths.get(outputFilename), Paths.get(bcFileFromFilename(outputFilename)), StandardCopyOption.REPLACE_EXISTING);
                    linkExecutable(outputFilename, linkedBCfile.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void linkExecutable(String executableScript, String linkedBcFile) throws IOException {
        if (linkLLI) {
            List<String> cmdline = GraalPythonMain.getCmdline(Arrays.asList(), Arrays.asList());
            cmdline.add("-LLI");
            cmdline.add(linkedBcFile);
            Path executablePath = Paths.get(executableScript);
            Files.write(executablePath, String.join(" ", cmdline).getBytes());
            HashSet<PosixFilePermission> perms = new HashSet<>(Arrays.asList(new PosixFilePermission[]{PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE}));
            perms.addAll(Files.getPosixFilePermissions(executablePath));
        } else {
            execLinkArgs.add(linkedBcFile);
            execLinkArgs.add("-o");
            execLinkArgs.add(executableScript);
            exec(execLinkArgs);
        }
    }

    private void linkShared(List<String> bitcodeFiles) {
        if (fileInputs.size() > 0) {
            ArrayList<String> ldCmd = new ArrayList<>(bitcodeFiles);
            if (verbose) {
                ldCmd.add("-v");
            }
            ldCmd.add("-o");
            ldCmd.add(outputFilename);
            GraalPythonLD.main(ldCmd.toArray(new String[0]));
        }
    }

    private void optFile(String bcFile) {
        List<String> opt = new ArrayList<>(optPrefix);
        opt.add(bcFile);
        opt.add(bcFile);
        exec(opt);
    }

    private static String bcFileFromFilename(String f) {
        int dotIdx = f.lastIndexOf('.');
        if (dotIdx > 1) {
            return f.substring(0, dotIdx + 1) + "bc";
        } else {
            return f + ".bc";
        }
    }

    private static String objectFileFromFilename(String f) {
        return bcFileFromFilename(f).replaceAll("\\.bc$", ".o");
    }
}
