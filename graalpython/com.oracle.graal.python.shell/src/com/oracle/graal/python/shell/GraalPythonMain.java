/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.shell;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.shadowed.org.jline.reader.UserInterruptException;

public class GraalPythonMain extends AbstractLanguageLauncher {

    private static final boolean IS_WINDOWS = System.getProperty("os.name") != null && System.getProperty("os.name").toLowerCase().contains("windows");

    private static final String SHORT_HELP = "usage: python [option] ... [-c cmd | -m mod | file | -] [arg] ...\n" +
                    "Try `python -h' for more information.";

    private static final String STRING_LIST_DELIMITER = "🏆";

    // Duplicate of SysModuleBuiltins.INT_MAX_STR_DIGITS_THRESHOLD
    private static final int INT_MAX_STR_DIGITS_THRESHOLD = 640;

    /**
     * The first method called with the arguments by the thin launcher is
     * {@link #preprocessArguments}.
     */
    public static void main(String[] args) {
        new GraalPythonMain().launch(args);
    }

    private static final String LANGUAGE_ID = "python";

    private static final String J_PYENVCFG = "pyvenv.cfg";

    private static long startupWallClockTime = -1;
    private static long startupNanoTime = -1;

    private boolean verboseLauncher = false;
    private ArrayList<String> programArgs = null;
    private ArrayList<String> origArgs = null;
    private String commandString = null;
    private String inputFile = null;
    private boolean isolateFlag = false;
    private boolean ignoreEnv = false;
    private boolean safePath = false;
    private boolean inspectFlag = false;
    private boolean verboseFlag = false;
    private boolean quietFlag = false;
    private boolean noUserSite = false;
    private boolean noSite = false;
    private boolean unbufferedIO = false;
    private boolean multiContext = false;
    private boolean snaptshotStartup = false;
    private boolean warnDefaultEncoding = false;
    private int intMaxStrDigits = -1;
    private VersionAction versionAction = VersionAction.None;
    private List<String> givenArguments;
    private List<String> relaunchArgs;
    private boolean wantsExperimental = false;
    private Map<String, String> enginePolyglotOptions;
    private boolean dontWriteBytecode = false;
    private String warnOptions = null;
    private String checkHashPycsMode = "default";
    private String execName;

    public GraalPythonMain() {
        verboseLauncher = Boolean.parseBoolean(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));
    }

    protected static void setStartupTime() {
        if (GraalPythonMain.startupNanoTime == -1) {
            GraalPythonMain.startupNanoTime = System.nanoTime();
        }
        if (GraalPythonMain.startupWallClockTime == -1) {
            GraalPythonMain.startupWallClockTime = System.currentTimeMillis();
        }
    }

    private void polyglotGet(String exe, List<String> originalArgs) {
        List<String> args = new ArrayList<>();
        if (originalArgs.size() == 1 && !originalArgs.get(0).startsWith("-")) {
            args.add("-a");
        }
        args.addAll(originalArgs);
        if (!originalArgs.contains("-o")) {
            var binPath = Paths.get(exe).getParent();
            if (binPath != null) {
                args.add("-o");
                args.add(binPath.resolveSibling("modules").toString());
            }
        }
        if (!originalArgs.contains("-v")) {
            try (var tmpEngine = Engine.newBuilder().useSystemProperties(false).//
                            out(OutputStream.nullOutputStream()).//
                            err(OutputStream.nullOutputStream()).//
                            option("engine.WarnInterpreterOnly", "false").//
                            build()) {
                args.add("-v");
                args.add(tmpEngine.getVersion());
            }
        }
        try {
            org.graalvm.maven.downloader.Main.main(args.toArray(new String[0]));
        } catch (Exception e) {
            throw abort(e);
        }
        System.exit(0);
    }

    @Override
    protected List<String> preprocessArguments(List<String> givenArgs, Map<String, String> polyglotOptions) {
        String launcherName = getLauncherExecName();
        if (launcherName != null && (launcherName.endsWith("graalpy-polyglot-get") || launcherName.endsWith("graalpy-polyglot-get.exe"))) {
            polyglotGet(launcherName, givenArgs);
        }
        ArrayList<String> unrecognized = new ArrayList<>();
        List<String> defaultEnvironmentArgs = getDefaultEnvironmentArgs();
        ArrayList<String> inputArgs = new ArrayList<>(defaultEnvironmentArgs);
        inputArgs.addAll(givenArgs);
        givenArguments = new ArrayList<>(inputArgs);
        List<String> arguments = new ArrayList<>(inputArgs);
        List<String> subprocessArgs = new ArrayList<>();
        programArgs = new ArrayList<>();
        origArgs = new ArrayList<>();
        boolean posixBackendSpecified = false;
        boolean sha3BackendSpecified = false;
        boolean installSignalHandlersSpecified = false;
        for (Iterator<String> argumentIterator = arguments.iterator(); argumentIterator.hasNext();) {
            String arg = argumentIterator.next();
            origArgs.add(arg);
            if (arg.startsWith("-")) {
                if (arg.length() == 1) {
                    // Lone dash should just be skipped
                    continue;
                }
                /*
                 * Our internal options with single-dash `-long-option` format should be tried first
                 * to resolve ambiguity with short options taking arguments
                 */
                if (wantsExperimental) {
                    switch (arg) {
                        case "-debug-java":
                            if (!isAOT()) {
                                subprocessArgs.add("agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y");
                                inputArgs.remove("-debug-java");
                            }
                            continue;
                        case "-debug-subprocess-java-port":
                        case "-debug-subprocess-java":
                            int subprocessDebuggerPort = 8000;
                            if (arg.equals("-debug-subprocess-java-port")) {
                                subprocessDebuggerPort = Integer.parseInt(argumentIterator.next());
                            }
                            addRelaunchArg("-debug-subprocess-java-port");
                            addRelaunchArg(Integer.toString(subprocessDebuggerPort + 1));
                            addRelaunchArg("--vm.agentlib:jdwp=transport=dt_socket,server=y,address=" + subprocessDebuggerPort + ",suspend=y");
                            continue;
                        case "-debug-perf":
                            unrecognized.add("--engine.TraceCompilation");
                            unrecognized.add("--engine.TraceCompilationDetails");
                            unrecognized.add("--engine.TraceInlining");
                            unrecognized.add("--engine.TraceSplitting");
                            unrecognized.add("--engine.TraceCompilationPolymorphism");
                            unrecognized.add("--engine.TraceAssumptions");
                            unrecognized.add("--engine.TraceTransferToInterpreter");
                            unrecognized.add("--engine.TracePerformanceWarnings=all");
                            unrecognized.add("--engine.CompilationFailureAction=Print");
                            inputArgs.remove("-debug-perf");
                            continue;
                        case "-multi-context":
                            multiContext = true;
                            continue;
                        case "-dump":
                            subprocessArgs.add("Dgraal.Dump=");
                            inputArgs.add("--engine.BackgroundCompilation=false");
                            inputArgs.remove("-dump");
                            continue;
                        case "-snapshot-startup":
                            snaptshotStartup = true;
                            continue;
                    }
                }
                if (arg.startsWith("--")) {
                    // Long options
                    switch (arg) {
                        // --help gets passed through as unrecognized
                        case "--version":
                            versionAction = VersionAction.PrintAndExit;
                            continue;
                        case "--show-version":
                            versionAction = VersionAction.PrintAndContinue;
                            continue;
                        case "--experimental-options":
                        case "--experimental-options=true":
                            /*
                             * This is the default Truffle experimental option flag. We also use it
                             * for our custom launcher options
                             */
                            wantsExperimental = true;
                            addRelaunchArg(arg);
                            unrecognized.add(arg);
                            continue;
                        case "--check-hash-based-pycs":
                            if (!argumentIterator.hasNext()) {
                                throw abort("Argument expected for the --check-hash-based-pycs option\n" + SHORT_HELP, 2);
                            }
                            checkHashPycsMode = argumentIterator.next();
                            continue;
                        default:
                            if (arg.startsWith("--llvm.") ||
                                            matchesPythonOption(arg, "CoreHome") ||
                                            matchesPythonOption(arg, "StdLibHome") ||
                                            matchesPythonOption(arg, "CAPI") ||
                                            matchesPythonOption(arg, "PosixModuleBackend") ||
                                            matchesPythonOption(arg, "Sha3ModuleBackend")) {
                                addRelaunchArg(arg);
                            }
                            if (matchesPythonOption(arg, "PosixModuleBackend")) {
                                posixBackendSpecified = true;
                            }
                            if (matchesPythonOption(arg, "Sha3ModuleBackend")) {
                                sha3BackendSpecified = true;
                            }
                            if (matchesPythonOption(arg, "InstallSignalHandlers")) {
                                installSignalHandlersSpecified = true;
                            }
                            // possibly a polyglot argument
                            unrecognized.add(arg);
                            continue;
                    }
                }
                // Short options
                /*
                 * Multiple options can be clustered together (`-vE`). They can also be repeated
                 * (`-OO`). And some of them can take a parameter that may be in the next argument
                 * (`-m some_module`) or follow immediately (`-msome_module`).
                 */
                String remainder = arg.substring(1);
                shortOptionLoop: while (!remainder.isEmpty()) {
                    char option = remainder.charAt(0);
                    remainder = remainder.substring(1);
                    switch (option) {
                        case 'b':
                            // TODO implement
                            /*
                             * Issue warnings about str(bytes_instance), str(bytearray_instance) and
                             * comparing bytes/bytearray with str. (-bb: issue errors)
                             */
                            break;
                        case 'B':
                            dontWriteBytecode = true;
                            break;
                        case 'c':
                            programArgs.add("-c");
                            commandString = getShortOptionParameter(argumentIterator, remainder, 'c');
                            break shortOptionLoop;
                        case 'd':
                            // TODO implement
                            /* Turn on parser debugging output */
                            break;
                        case 'E':
                            ignoreEnv = true;
                            break;
                        case '?':
                        case 'h':
                            unrecognized.add("--help");
                            break;
                        case 'i':
                            inspectFlag = true;
                            break;
                        case 'I':
                            noUserSite = true;
                            ignoreEnv = true;
                            isolateFlag = true;
                            safePath = true;
                            break;
                        case 'P':
                            safePath = true;
                            break;
                        case 'm':
                            programArgs.add("-m");
                            String module = getShortOptionParameter(argumentIterator, remainder, 'm');
                            commandString = "import runpy; runpy._run_module_as_main('" + module + "')";
                            break shortOptionLoop;
                        case 'O':
                            // TODO implement
                            /*
                             * Remove assert statements and any code conditional on the value of
                             * __debug__; augment the filename for compiled (bytecode) files by
                             * adding .opt-1 before the .pyc extension.
                             */
                            break;
                        case 'R':
                            // TODO implement
                            break;
                        case 'q':
                            quietFlag = true;
                            break;
                        case 's':
                            noUserSite = true;
                            break;
                        case 'S':
                            noSite = true;
                            break;
                        case 't':
                            // Ignored even in CPython, for backwards compatibility
                            break;
                        case 'W':
                            if (warnOptions == null) {
                                warnOptions = "";
                            } else {
                                warnOptions += ",";
                            }
                            warnOptions += getShortOptionParameter(argumentIterator, remainder, 'W');
                            break shortOptionLoop;
                        case 'u':
                            unbufferedIO = true;
                            break;
                        case 'v':
                            verboseFlag = true;
                            break;
                        case 'V':
                            versionAction = VersionAction.PrintAndExit;
                            break;
                        case 'X':
                            // CPython ignores unknown/unsupported -X options, so we can do that too
                            String xOption = getShortOptionParameter(argumentIterator, remainder, 'X');
                            if ("warn_default_encoding".equals(xOption)) {
                                warnDefaultEncoding = true;
                            } else if (xOption.startsWith("int_max_str_digits")) {
                                int eq = xOption.indexOf('=');
                                if (eq > 0) {
                                    intMaxStrDigits = validateIntMaxStrDigits(xOption.substring(eq), "-X int_max_str_digits");
                                }
                            }
                            break shortOptionLoop;
                        default:
                            throw abort(String.format("Unknown option -%c\n", option) + SHORT_HELP, 2);
                    }
                }
            } else {
                // Not an option, has to be a file name
                inputFile = arg;
                programArgs.add(arg);
            }

            if (inputFile != null || commandString != null) {
                while (argumentIterator.hasNext()) {
                    String a = argumentIterator.next();
                    programArgs.add(a);
                    origArgs.add(a);
                }
                break;
            }
        }

        if (!ImageInfo.inImageCode()) {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.equals("-ea")) {
                    addRelaunchArg("--vm.ea");
                    break;
                }
            }
        }

        // According to CPython if no arguments are given, they contain an empty string.
        if (programArgs.isEmpty()) {
            programArgs.add("");
        }

        if (!subprocessArgs.isEmpty()) {
            subExec(inputArgs, subprocessArgs);
        }
        if (!posixBackendSpecified) {
            polyglotOptions.put("python.PosixModuleBackend", "native");
        }
        if (!sha3BackendSpecified) {
            polyglotOptions.put("python.Sha3ModuleBackend", "native");
        }
        if (!installSignalHandlersSpecified) {
            polyglotOptions.put("python.InstallSignalHandlers", "true");
        }
        // Never emit warnings that mess up the output
        unrecognized.add("--engine.WarnInterpreterOnly=false");
        return unrecognized;
    }

    private String getShortOptionParameter(Iterator<String> argumentIterator, String remainder, char option) {
        String ret;
        if (remainder.isEmpty()) {
            if (!argumentIterator.hasNext()) {
                throw abort(String.format("Argument expected for the -%c option\n", option) + SHORT_HELP, 2);
            }
            ret = argumentIterator.next();
        } else {
            ret = remainder;
        }
        origArgs.add(ret);
        return ret;
    }

    @Override
    protected AbortException abortUnrecognizedArgument(String argument) {
        throw abort(String.format("Unknown option %s\n", argument) + SHORT_HELP, 2);
    }

    @Override
    protected void validateArguments(Map<String, String> polyglotOptions) {
        if (multiContext) {
            // Hack to pass polyglot options to the shared engine, not to the context which would
            // refuse them
            this.enginePolyglotOptions = new HashMap<>(polyglotOptions);
            polyglotOptions.clear();
        }
    }

    private void addRelaunchArg(String arg) {
        if (relaunchArgs == null) {
            relaunchArgs = new ArrayList<>();
        }
        relaunchArgs.add(arg);
    }

    private String[] execListWithRelaunchArgs(String executableName) {
        if (relaunchArgs == null) {
            return new String[]{executableName};
        } else {
            ArrayList<String> execList = new ArrayList<>(relaunchArgs.size() + 1);
            execList.add(executableName);
            execList.addAll(relaunchArgs);
            return execList.toArray(new String[execList.size()]);
        }
    }

    private static void print(String string) {
        System.err.println(string);
    }

    private String getLauncherExecName() {
        if (execName != null) {
            return execName;
        }
        execName = getProgramName();
        log("initial executable name: ", execName);
        if (execName == null) {
            return null;
        }
        execName = calculateProgramFullPath(execName, Files::isExecutable, null);
        log("resolved executable name: ", execName);
        return execName;
    }

    /**
     * Follows the same semantics as CPython's {@code getpath.c:calculate_program_full_path} to
     * determine the full program path if we just got a non-absolute program name. This method
     * handles the following cases:
     * <dl>
     * <dt><b>Program name is an absolute path</b></dt>
     * <dd>Just return {@code program}.</dd>
     * <dt><b>Program name is a relative path</b></dt>
     * <dd>it will resolve it to an absolute path. E.g. {@code "./python3"} will become {@code
     * "<current_working_dir>/python3"}/dd>
     * <dt><b>Program name is neither an absolute nor a relative path</b></dt>
     * <dd>It will resolve the program name wrt. to the {@code PATH} env variable. Since it may be
     * that the {@code PATH} variable is not available, this method will return {@code null}</dd>
     * </dl>
     *
     * @param program The program name as passed in the process' argument vector (position 0).
     * @param isExecutable Check whether given {@link Path} exists and is executable (for testing).
     * @param envPath If non-null: value to be used as $PATH (for testing), otherwise
     *            {@link #getEnv(String)} is used to retrieve $PATH.
     * @return The absolute path to the program or {@code null}.
     */
    private String calculateProgramFullPath(String program, Function<Path, Boolean> isExecutable, String envPath) {
        Path programPath = Paths.get(program);

        // If this is an absolute path, we are already fine.
        if (programPath.isAbsolute()) {
            return program;
        }

        /*
         * If there is no slash in the arg[0] path, then we have to assume python is on the user's
         * $PATH, since there's no other way to find a directory to start the search from. If $PATH
         * isn't exported, you lose.
         */
        if (programPath.getNameCount() < 2) {
            // Resolve the program name with respect to the PATH variable.
            String path = envPath != null ? envPath : getEnv("PATH");
            if (path != null) {
                log("resolving the executable name in $PATH = ", path);
                int i, previous = 0;
                do {
                    i = path.indexOf(File.pathSeparatorChar, previous);
                    int end = i == -1 ? path.length() : i;
                    Path resolvedProgramName = Paths.get(path.substring(previous, end)).resolve(programPath);
                    if (isExecutable.apply(resolvedProgramName)) {
                        return resolvedProgramName.toString();
                    }

                    // On windows, the program name may be without the extension
                    if (IS_WINDOWS) {
                        String pathExtEnvvar = getEnv("PATHEXT");
                        if (pathExtEnvvar != null) {
                            // default extensions are defined
                            String resolvedStr = resolvedProgramName.toString();
                            if (resolvedStr.length() <= 3 || resolvedStr.charAt(resolvedStr.length() - 4) != '.') {
                                // program has no file extension
                                String[] pathExts = pathExtEnvvar.toLowerCase().split(";");
                                for (String pathExt : pathExts) {
                                    resolvedProgramName = Path.of(resolvedStr + pathExt);
                                    if (isExecutable.apply(resolvedProgramName)) {
                                        return resolvedProgramName.toString();
                                    }
                                }
                            }
                        }
                    }

                    // next start is the char after the separator because we have "path0:path1" and
                    // 'i' points to ':'
                    previous = i + 1;
                } while (i != -1);
            } else {
                log("executable name looks like it is from $PATH, but $PATH is not available.");
            }
            return null;
        }
        // It's seemingly a relative path, so we can just resolve it to an absolute one.
        assert !programPath.isAbsolute();
        /*
         * Another special case (see: CPython function "getpath.c:copy_absolute"): If the program
         * name starts with "./" (on Unix; or similar on other systems) then the path is
         * canonicalized.
         */
        if (".".equals(programPath.getName(0).toString())) {
            return programPath.toAbsolutePath().normalize().toString();
        }
        return programPath.toAbsolutePath().toString();
    }

    private String[] getExecutableList() {
        String launcherExecName = getLauncherExecName();
        if (launcherExecName != null) {
            return execListWithRelaunchArgs(launcherExecName);
        }

        // This should only be reached if this main is directly executed via Java.
        if (!ImageInfo.inImageCode()) {
            StringBuilder sb = new StringBuilder();
            ArrayList<String> exec_list = new ArrayList<>();
            sb.append(System.getProperty("java.home")).append(File.separator).append("bin").append(File.separator).append("java");
            exec_list.add(sb.toString());
            String javaOptions = getEnv("_JAVA_OPTIONS");
            String javaToolOptions = getEnv("JAVA_TOOL_OPTIONS");
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.matches("(-Xrunjdwp:|-agentlib:jdwp=).*suspend=y.*")) {
                    arg = arg.replace("suspend=y", "suspend=n");
                } else if (arg.matches(".*ThreadPriorityPolicy.*")) {
                    // skip this one, it may cause warnings
                    continue;
                } else if ((javaOptions != null && javaOptions.contains(arg)) || (javaToolOptions != null && javaToolOptions.contains(arg))) {
                    // both _JAVA_OPTIONS and JAVA_TOOL_OPTIONS are added during
                    // JVM startup automatically. We do not want to repeat these
                    // for subprocesses, because they should also pick up those
                    // variables.
                    continue;
                }
                exec_list.add(arg);
            }
            exec_list.add("-classpath");
            exec_list.add(System.getProperty("java.class.path"));
            exec_list.add(getMainClass());
            if (relaunchArgs != null) {
                exec_list.addAll(relaunchArgs);
            }
            return exec_list.toArray(new String[exec_list.size()]);
        }

        return new String[]{""};
    }

    private String getExecutable() {
        if (ImageInfo.inImageBuildtimeCode()) {
            return "";
        } else {
            String launcherExecName = getLauncherExecName();
            if (launcherExecName != null) {
                return launcherExecName;
            }
            log("cannot determine the executable path. Using java command invocation for executable.");
            String[] executableList = getExecutableList();
            for (int i = 0; i < executableList.length; i++) {
                if (executableList[i].matches("\\s")) {
                    executableList[i] = "'" + executableList[i].replace("'", "\\'") + "'";
                }
            }
            return String.join(" ", executableList);
        }
    }

    @Override
    protected void launch(Builder contextBuilder) {
        GraalPythonMain.setStartupTime();
        String cachePrefix = null;

        // prevent the use of System.out/err - they are PrintStreams which suppresses exceptions
        contextBuilder.out(new FileOutputStream(FileDescriptor.out));
        contextBuilder.err(new FileOutputStream(FileDescriptor.err));
        if (!ignoreEnv) {
            String pythonpath = getEnv("PYTHONPATH");
            if (pythonpath != null) {
                contextBuilder.option("python.PythonPath", pythonpath);
            }
            inspectFlag = inspectFlag || getBoolEnv("PYTHONINSPECT");
            noUserSite = noUserSite || getBoolEnv("PYTHONNOUSERSITE");
            safePath = safePath || getBoolEnv("PYTHONSAFEPATH");
            verboseFlag = verboseFlag || getBoolEnv("PYTHONVERBOSE");
            unbufferedIO = unbufferedIO || getBoolEnv("PYTHONUNBUFFERED");
            dontWriteBytecode = dontWriteBytecode || getBoolEnv("PYTHONDONTWRITEBYTECODE");
            String maxStrDigitsEnv = getEnv("PYTHONINTMAXSTRDIGITS");
            if (intMaxStrDigits < 0 && maxStrDigitsEnv != null) {
                intMaxStrDigits = validateIntMaxStrDigits(maxStrDigitsEnv, "PYTHONINTMAXSTRDIGITS");
            }

            String hashSeed = getEnv("PYTHONHASHSEED");
            if (hashSeed != null) {
                contextBuilder.option("python.HashSeed", hashSeed);
            }

            String envWarnOptions = getEnv("PYTHONWARNINGS");
            if (envWarnOptions != null && !envWarnOptions.isEmpty()) {
                if (warnOptions == null) {
                    warnOptions = envWarnOptions;
                } else {
                    warnOptions = envWarnOptions + "," + warnOptions;
                }
            }
            cachePrefix = getEnv("PYTHONPYCACHEPREFIX");

            String encoding = getEnv("PYTHONIOENCODING");
            if (encoding != null) {
                contextBuilder.option("python.StandardStreamEncoding", encoding);
            }
        }
        if (warnOptions == null || warnOptions.isEmpty()) {
            warnOptions = "";
        }
        String executable = getContextOptionIfSetViaCommandLine("Executable");
        if (executable != null) {
            contextBuilder.option("python.ExecutableList", toAbsolutePath(executable));
        } else {
            executable = getExecutable();
            contextBuilder.option("python.Executable", toAbsolutePath(executable));
            // The unlikely separator is used because options need to be
            // strings. See PythonOptions.getExecutableList()
            String[] executableList = getExecutableList();
            executableList[0] = toAbsolutePath(executableList[0]);
            contextBuilder.option("python.ExecutableList", String.join(STRING_LIST_DELIMITER, executableList));
            // We try locating and loading options from pyvenv.cfg according to PEP405 as long as
            // the user did not explicitly pass some options that would be otherwise loaded from
            // pyvenv.cfg. Notable usage of this feature is GraalPython venvs which generate a
            // launcher script that passes those options explicitly without relying on pyvenv.cfg
            boolean tryVenvCfg = !hasContextOptionSetViaCommandLine("SysPrefix") &&
                            !hasContextOptionSetViaCommandLine("PythonHome") &&
                            getEnv("GRAAL_PYTHONHOME") == null;
            if (tryVenvCfg) {
                findAndApplyVenvCfg(contextBuilder, executable);
            }
        }

        if (isLLVMToolchainLauncher()) {
            if (!hasContextOptionSetViaCommandLine("UseSystemToolchain")) {
                contextBuilder.option("python.UseSystemToolchain", "false");
            }
            if (!hasContextOptionSetViaCommandLine("NativeModules")) {
                contextBuilder.option("python.NativeModules", "false");
            }
        }

        if (relaunchArgs != null) {
            Iterator<String> it = origArgs.iterator();
            while (it.hasNext()) {
                if (relaunchArgs.contains(it.next())) {
                    it.remove();
                }
            }
        }
        origArgs.add(0, toAbsolutePath(executable));
        contextBuilder.option("python.OrigArgv", String.join(STRING_LIST_DELIMITER, origArgs));

        // setting this to make sure our TopLevelExceptionHandler calls the excepthook
        // to print Python exceptions
        contextBuilder.option("python.AlwaysRunExcepthook", "true");
        contextBuilder.option("python.InspectFlag", Boolean.toString(inspectFlag));
        contextBuilder.option("python.VerboseFlag", Boolean.toString(verboseFlag));
        contextBuilder.option("python.IsolateFlag", Boolean.toString(isolateFlag));
        contextBuilder.option("python.SafePathFlag", Boolean.toString(safePath));
        contextBuilder.option("python.WarnOptions", warnOptions);
        contextBuilder.option("python.WarnDefaultEncodingFlag", Boolean.toString(warnDefaultEncoding));
        if (intMaxStrDigits > 0) {
            contextBuilder.option("python.IntMaxStrDigits", Integer.toString(intMaxStrDigits));
        }
        contextBuilder.option("python.DontWriteBytecodeFlag", Boolean.toString(dontWriteBytecode));
        if (verboseFlag) {
            contextBuilder.option("log.python.level", "FINE");
        }
        contextBuilder.option("python.QuietFlag", Boolean.toString(quietFlag));
        contextBuilder.option("python.NoUserSiteFlag", Boolean.toString(noUserSite));
        contextBuilder.option("python.NoSiteFlag", Boolean.toString(noSite));
        if (!noSite) {
            contextBuilder.option("python.ForceImportSite", "true");
        }
        contextBuilder.option("python.SetupLLVMLibraryPaths", "true");
        contextBuilder.option("python.IgnoreEnvironmentFlag", Boolean.toString(ignoreEnv));
        contextBuilder.option("python.UnbufferedIO", Boolean.toString(unbufferedIO));

        ConsoleHandler consoleHandler = createConsoleHandler(System.in, System.out);
        contextBuilder.arguments(getLanguageId(), programArgs.toArray(new String[programArgs.size()]));
        contextBuilder.in(consoleHandler.createInputStream());
        contextBuilder.option("python.TerminalIsInteractive", Boolean.toString(isTTY()));
        contextBuilder.option("python.TerminalWidth", Integer.toString(consoleHandler.getTerminalWidth()));
        contextBuilder.option("python.TerminalHeight", Integer.toString(consoleHandler.getTerminalHeight()));

        contextBuilder.option("python.CheckHashPycsMode", checkHashPycsMode);
        contextBuilder.option("python.RunViaLauncher", "true");
        if (inputFile != null) {
            contextBuilder.option("python.InputFilePath", inputFile);
        }

        contextBuilder.option("python.DontWriteBytecodeFlag", Boolean.toString(dontWriteBytecode));
        if (cachePrefix != null) {
            contextBuilder.option("python.PyCachePrefix", cachePrefix);
        }

        if (IS_WINDOWS) {
            contextBuilder.option("python.PosixModuleBackend", "java");
        }

        if (multiContext) {
            contextBuilder.engine(Engine.newBuilder().allowExperimentalOptions(true).options(enginePolyglotOptions).build());
        }

        int rc = 1;
        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());

            if (snaptshotStartup) {
                evalInternal(context, "__graalpython__.startup_wall_clock_ts = " + startupWallClockTime + "; __graalpython__.startup_nano = " + startupNanoTime);
            }

            if (!quietFlag && (verboseFlag || (commandString == null && inputFile == null && isTTY()))) {
                print("Python " + evalInternal(context, "import sys; sys.version + ' on ' + sys.platform").asString());
                if (!noSite) {
                    print("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.");
                }
            }
            consoleHandler.setContext(context);

            if (commandString != null || inputFile != null || !isTTY()) {
                try {
                    evalNonInteractive(context, consoleHandler);
                    rc = 0;
                } catch (PolyglotException e) {
                    if (!e.isExit()) {
                        printPythonLikeStackTrace(e);
                    } else {
                        rc = e.getExitStatus();
                    }
                } catch (NoSuchFileException e) {
                    printFileNotFoundException(e);
                }
            }
            if ((commandString == null && inputFile == null) || inspectFlag) {
                inspectFlag = false;
                rc = readEvalPrint(context, consoleHandler);
            }
        } catch (IOException e) {
            rc = 1;
            e.printStackTrace();
        } finally {
            consoleHandler.setContext(null);
        }
        System.exit(rc);
    }

    private static boolean getBoolEnv(String var) {
        return getEnv(var) != null;
    }

    private static String getEnv(String var) {
        String value = System.getenv(var);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    private int validateIntMaxStrDigits(String input, String name) {
        try {
            int value = Integer.parseInt(input);
            if (value == 0 || value >= INT_MAX_STR_DIGITS_THRESHOLD) {
                return value;
            }
        } catch (NumberFormatException e) {
            // fallthrough
        }
        throw abort(String.format("%s: invalid limit; must be >= %d  or 0 for unlimited.", name, INT_MAX_STR_DIGITS_THRESHOLD), 1);
    }

    private static String toAbsolutePath(String executable) {
        if (executable.contains(":")) {
            // this is either already an absolute windows path, or not a single executable
            return executable;
        }
        return Paths.get(executable).toAbsolutePath().toString();
    }

    private void findAndApplyVenvCfg(Builder contextBuilder, String executable) {
        Path binDir;
        try {
            binDir = Paths.get(executable).getParent();
        } catch (InvalidPathException e) {
            log("cannot determine the parent directory of the executable");
            return;
        }
        if (binDir == null) {
            log("parent directory of the executable does not exist");
            return;
        }
        Path venvCfg = binDir.resolve(J_PYENVCFG);
        log("checking: ", venvCfg);
        if (!Files.exists(venvCfg)) {
            Path binParent = binDir.getParent();
            if (binParent == null) {
                return;
            }
            venvCfg = binParent.resolve(J_PYENVCFG);
            log("checking: ", venvCfg);
            if (!Files.exists(venvCfg)) {
                return;
            }
        }
        log("found: ", venvCfg);
        try (BufferedReader reader = Files.newBufferedReader(venvCfg)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String name = parts[0].trim();
                if (name.equals("home")) {
                    Path homeProperty = Paths.get(parts[1].trim());
                    /*
                     * (tfel): According to PEP 405, the home key is the directory of the Python
                     * executable from which this virtual environment was created, that is, it
                     * usually ends with "/bin" on a Unix system. On Windows, the base Python should
                     * be in the top-level directory or under "\Scripts". To support running from
                     * Maven artifacts where we don't have a working executable, we patched our
                     * shipped venv module to set the home path without a "/bin" or "\\Scripts"
                     * suffix, so we explicitly check for those two subfolder cases and otherwise
                     * assume the home key is directly pointing to the Python home.
                     */
                    if (homeProperty.endsWith("bin") || homeProperty.endsWith("Scripts")) {
                        homeProperty = homeProperty.getParent();
                    }
                    try {
                        contextBuilder.option("python.PythonHome", homeProperty.toString());
                    } catch (NullPointerException ex) {
                        // NullPointerException covers the possible null result of getParent()
                        warn("Could not set PYTHONHOME according to the pyvenv.cfg file.");
                    }
                    String sysPrefix = null;
                    try {
                        sysPrefix = venvCfg.getParent().toAbsolutePath().toString();
                    } catch (IOError | NullPointerException ex) {
                        // NullPointerException covers the possible null result of getParent()
                        warn("Could not set the sys.prefix according to the pyvenv.cfg file.");
                    }
                    if (sysPrefix != null) {
                        contextBuilder.option("python.SysPrefix", sysPrefix);
                    }
                    break;
                }
            }
        } catch (IOException ex) {
            throw abort("Could not read the pyvenv.cfg file.", 66);
        }
    }

    private static boolean matchesPythonOption(String arg, String key) {
        assert !key.startsWith("python.");
        return arg.startsWith("--python." + key) || arg.startsWith("--" + key);
    }

    private String getContextOptionIfSetViaCommandLine(String key) {
        if (System.getProperty("polyglot.python." + key) != null) {
            return System.getProperty("polyglot.python." + key);
        }
        for (String f : givenArguments) {
            if (matchesPythonOption(f, key)) {
                String[] splits = f.split("=", 2);
                if (splits.length > 1) {
                    return splits[1];
                } else {
                    return "true";
                }
            }
        }
        return null;
    }

    private boolean hasContextOptionSetViaCommandLine(String key) {
        if (System.getProperty("polyglot.python." + key) != null) {
            return System.getProperty("polyglot.python." + key) != null;
        }
        for (String f : givenArguments) {
            if (matchesPythonOption(f, key)) {
                return true;
            }
        }
        return false;
    }

    private static void printFileNotFoundException(NoSuchFileException e) {
        String reason = e.getReason();
        if (reason == null) {
            reason = "No such file or directory";
        }
        System.err.println(GraalPythonMain.class.getCanonicalName() + ": can't open file '" + e.getFile() + "': " + reason);
    }

    private static void printPythonLikeStackTrace(PolyglotException e) {
        // If we're running through the launcher and an exception escapes to here,
        // we didn't go through the Python code to print it. That may be because
        // it's an exception from another language. In this case, we still would
        // like to print it like a Python exception.
        ArrayList<String> stack = new ArrayList<>();
        for (StackFrame frame : e.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                StringBuilder sb = new StringBuilder();
                SourceSection sourceSection = frame.getSourceLocation();
                String rootName = frame.getRootName();
                if (sourceSection != null) {
                    sb.append("  ");
                    String path = sourceSection.getSource().getPath();
                    if (path != null) {
                        sb.append("File ");
                    }
                    sb.append('"');
                    sb.append(sourceSection.getSource().getName());
                    sb.append("\", line ");
                    sb.append(sourceSection.getStartLine());
                    sb.append(", in ");
                    sb.append(rootName);
                    stack.add(sb.toString());
                }
            }
        }
        System.err.println("Traceback (most recent call last):");
        ListIterator<String> listIterator = stack.listIterator(stack.size());
        while (listIterator.hasPrevious()) {
            System.err.println(listIterator.previous());
        }
        System.err.println(e.getMessage());
    }

    private void evalNonInteractive(Context context, ConsoleHandler consoleHandler) throws IOException {
        // We need to setup the terminal even when not running the REPL because code may request
        // input from the terminal.
        setupTerminal(consoleHandler);

        Source src;
        if (commandString != null) {
            src = Source.newBuilder(getLanguageId(), commandString, "<string>").build();
        } else {
            // the path is passed through a context option, may be empty when running from stdin
            src = Source.newBuilder(getLanguageId(), "__graalpython__.run_path()", "<internal>").internal(true).build();
        }
        context.eval(src);
    }

    @Override
    protected String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        if (isLLVMToolchainLauncher()) {
            System.out.println("GraalPy launcher to use LLVM toolchain and Sulong execution of native extensions.\n");
        }
        System.out.println("usage: python [option] ... (-c cmd | file) [arg] ...\n" +
                        "Options and arguments (and corresponding environment variables):\n" +
                        "-B     : this disables writing .py[co] files on import\n" +
                        "-c cmd : program passed in as string (terminates option list)\n" +
                        // "-d : debug output from parser; also PYTHONDEBUG=x\n" +
                        "-E     : ignore PYTHON* environment variables (such as PYTHONPATH)\n" +
                        "-h     : print this help message and exit (also --help)\n" +
                        "-i     : inspect interactively after running script; forces a prompt even\n" +
                        "         if stdin does not appear to be a terminal; also PYTHONINSPECT=x\n" +
                        "-m mod : run library module as a script (terminates option list)\n" +
                        "-O     : on CPython, this optimizes generated bytecode slightly;\n" +
                        "         GraalPython does not use bytecode, and thus this flag has no effect\n" +
                        "-OO    : remove doc-strings in addition to the -O optimizations;\n" +
                        "         GraalPython does not use bytecode, and thus this flag has no effect\n" +
                        "-P     : don't prepend a potentially unsafe path to sys.path; also PYTHONSAFEPATH" +
                        "-R     : on CPython, this enables the use of a pseudo-random salt to make\n" +
                        "         hash()values of various types be unpredictable between separate\n" +
                        "         invocations of the interpreter, as a defense against denial-of-service\n" +
                        "         attacks; GraalPython always enables this and the flag has no effect.\n" +
                        "-q     : don't print version and copyright messages on interactive startup\n" +
                        "-I     : don't add user site and script directory to sys.path; also PYTHONNOUSERSITE\n" +
                        "-s     : don't add user site directory to sys.path; also PYTHONNOUSERSITE\n" +
                        "-S     : don't imply 'import site' on initialization\n" +
                        "-u     : unbuffered binary stdout and stderr; also PYTHONUNBUFFERED=x\n" +
                        "-v     : verbose (trace import statements); also PYTHONVERBOSE=x\n" +
                        "         can be supplied multiple times to increase verbosity\n" +
                        "-V     : print the Python version number and exit (also --version)\n" +
                        "         when given twice, print more information about the build\n" +
                        "-X opt : CPython implementation-specific options. warn_default_encoding and int_max_str_digits are supported on GraalPy\n" +
                        "-W arg : warning control; arg is action:message:category:module:lineno\n" +
                        "         also PYTHONWARNINGS=arg\n" +
                        "file   : program read from script file\n" +
                        "-      : program read from stdin\n" +
                        "arg ...: arguments passed to program in sys.argv[1:]\n" +
                        "\n" +
                        "Other environment variables:\n" +
                        "PYTHONSTARTUP: file executed on interactive startup (no default)\n" +
                        "PYTHONPATH   : ':'-separated list of directories prefixed to the\n" +
                        "               default module search path.  The result is sys.path.\n" +
                        "PYTHONHOME   : alternate <prefix> directory (or <prefix>:<exec_prefix>).\n" +
                        "               The default module search path uses <prefix>/pythonX.X.\n" +
                        "PYTHONCASEOK : ignore case in 'import' statements (Windows).\n" +
                        "PYTHONIOENCODING: Encoding[:errors] used for stdin/stdout/stderr.\n" +
                        "PYTHONHASHSEED: if this variable is set to 'random', the effect is the same\n" +
                        "   as specifying the -R option: a random value is used to seed the hashes of\n" +
                        "   str, bytes and datetime objects.  It can also be set to an integer\n" +
                        "   in the range [0,4294967295] to get hash values with a predictable seed.\n" +
                        "PYTHONPYCACHEPREFIX: if this is set, GraalPython will write .pyc files in a mirror\n" +
                        "   directory tree at this path, instead of in __pycache__ directories within the source tree.\n" +
                        "GRAAL_PYTHON_ARGS: the value is added as arguments as if passed on the\n" +
                        "   commandline. Arguments are split on whitespace - you can use \" and/or ' as required to\n" +
                        "   group them. Alternatively, if the value starts with a vertical tab character, the entire\n" +
                        "   value is split at vertical tabs and the elements are used as arguments without any further\n" +
                        "   escaping. If the value ends with a vertical tab, it is also purged from the environment\n" +
                        "   when the interpreter runs, so that GraalPy subprocess will not pick it up\n" +
                        "   There are two special substitutions for this variable: any `$$' in the value is replaced\n" +
                        "   with the current process id, and any $UUID$ is replaced with random unique string\n" +
                        "   that may contain letters, digits, and '-'. To pass a literal `$$', you must escape the\n" +
                        "   second `$' like so: `$\\$'\n" +
                        (wantsExperimental ? "\nArguments specific to the Graal Python launcher:\n" +
                                        "--show-version : print the Python version number and continue.\n" +
                                        "-CC            : run the C compiler used for generating GraalPython C extensions.\n" +
                                        "                 All following arguments are passed to the compiler.\n" +
                                        "-LD            : run the linker used for generating GraalPython C extensions.\n" +
                                        "                 All following arguments are passed to the linker.\n" +
                                        "\nEnvironment variables specific to the Graal Python launcher:\n" +
                                        "SULONG_LIBRARY_PATH: Specifies the library path for Sulong.\n" +
                                        "   This is required when starting subprocesses of python.\n" : ""));
    }

    private boolean isLLVMToolchainLauncher() {
        String exeName = getResolvedExecutableName();
        return exeName != null && exeName.endsWith("-lt");
    }

    @Override
    protected String[] getDefaultLanguages() {
        return new String[]{getLanguageId(), "llvm", "regex"};
    }

    @Override
    protected void collectArguments(Set<String> options) {
        // This list of arguments is used when we are launched through the Polyglot
        // launcher
        options.add("-c");
        options.add("-h");
        options.add("-V");
        options.add("--version");
        options.add("--show-version");
    }

    private static ConsoleHandler createConsoleHandler(InputStream inStream, OutputStream outStream) {
        if (!isTTY()) {
            return new DefaultConsoleHandler(inStream);
        } else {
            return new JLineConsoleHandler(inStream, outStream, false);
        }
    }

    /**
     * The read-eval-print loop, which can take input from a console, command line expression or a
     * file. There are two ways the repl can terminate:
     * <ol>
     * <li>A {@code quit} command is executed successfully.</li>
     * <li>EOF on the input.</li>
     * </ol>
     * In case 2, we must implicitly execute a {@code quit("default, 0L, TRUE} command before
     * exiting. So,in either case, we never return.
     */
    private int readEvalPrint(Context context, ConsoleHandler consoleHandler) {
        int lastStatus = 0;
        try {
            setupREPL(context, consoleHandler);
            Value sys = evalInternal(context, "import sys; sys");

            while (true) { // processing inputs
                boolean doEcho = doEcho(context);
                consoleHandler.setPrompt(doEcho ? sys.getMember("ps1").asString() : null);

                try {
                    String input = consoleHandler.readLine();
                    if (input == null) {
                        throw new EOFException();
                    }
                    if (canSkipFromEval(input)) {
                        // nothing to parse
                        continue;
                    }

                    String continuePrompt = null;
                    StringBuilder sb = new StringBuilder(input).append('\n');
                    while (true) { // processing subsequent lines while input is incomplete
                        lastStatus = 0;
                        try {
                            context.eval(Source.newBuilder(getLanguageId(), sb.toString(), "<stdin>").interactive(true).buildLiteral());
                        } catch (PolyglotException e) {
                            if (continuePrompt == null) {
                                continuePrompt = doEcho ? sys.getMember("ps2").asString() : null;
                            }
                            if (e.isIncompleteSource()) {
                                // read more input until we get an empty line
                                consoleHandler.setPrompt(continuePrompt);
                                String additionalInput;
                                boolean isIncompleteCode = false; // this for cases like empty lines
                                                                  // in tripplecode, where the
                                                                  // additional input can be empty,
                                                                  // but we should still continue
                                do {
                                    additionalInput = consoleHandler.readLine();
                                    sb.append(additionalInput).append('\n');
                                    try {
                                        // We try to parse every time, when an additional input is
                                        // added to find out if there is continuation exception or
                                        // other error. If there is other error, we have to stop
                                        // to ask for additional input.
                                        context.parse(Source.newBuilder(getLanguageId(), sb.toString(), "<stdin>").interactive(true).buildLiteral());
                                        e = null;   // the parsing was ok -> try to eval
                                                    // the code in outer while loop
                                        isIncompleteCode = false;
                                    } catch (PolyglotException pe) {
                                        if (!pe.isIncompleteSource()) {
                                            e = pe;
                                            break;
                                        } else {
                                            isIncompleteCode = true;
                                        }
                                    }
                                } while (additionalInput != null && isIncompleteCode);
                                // Here we can be in these cases:
                                // The parsing of the code with additional code was ok
                                // The parsing of the code with additional code thrown an error,
                                // which is not IncompleteSourceException
                                if (additionalInput == null) {
                                    throw new EOFException();
                                }
                                if (e == null) {
                                    // the last source (with additional input) was parsed correctly,
                                    // so we can execute it.
                                    continue;
                                }
                            }
                            // process the exception from eval or from the last parsing of the input
                            // + additional source
                            if (e.isExit()) {
                                // usually from quit
                                throw new ExitException(e.getExitStatus());
                            } else if (e.isHostException()) {
                                // we continue the repl even though the system may be broken
                                lastStatus = 1;
                                System.out.println(e.getMessage());
                            } else if (e.isInternalError()) {
                                System.err.println("An internal error occurred:");
                                printPythonLikeStackTrace(e);

                                // we continue the repl even though the system may be broken
                                lastStatus = 1;
                            } else if (e.isGuestException()) {
                                // drop through to continue REPL and remember last eval was an error
                                lastStatus = 1;
                            }
                        }
                        break;
                    }
                } catch (EOFException e) {
                    if (!noSite) {
                        try {
                            context.eval(Source.newBuilder(getLanguageId(), "import site; exit()\n", "<internal>").internal(true).interactive(true).buildLiteral());
                        } catch (PolyglotException e2) {
                            if (e2.isExit()) {
                                // don't use the exit code from the PolyglotException
                                return lastStatus;
                            } else if (e2.isCancelled()) {
                                continue;
                            }
                            throw new RuntimeException("error while calling exit", e);
                        }
                    }
                    System.out.println();
                    return lastStatus;
                } catch (UserInterruptException e) {
                    // interrupted by ctrl-c
                }
            }
        } catch (ExitException e) {
            return e.code;
        }
    }

    private static boolean canSkipFromEval(String input) {
        String[] split = input.split("\n");
        for (String s : split) {
            if (!s.isEmpty() && !s.startsWith("#")) {
                return false;
            }
        }
        return true;
    }

    private Value evalInternal(Context context, String code) {
        return context.eval(Source.newBuilder(getLanguageId(), code, "<internal>").internal(true).buildLiteral());
    }

    private void setupREPL(Context context, ConsoleHandler consoleHandler) {
        // Then we can get the readline module and see if any completers were registered and use its
        // history feature
        evalInternal(context, "import sys\ngetattr(sys, '__interactivehook__', lambda: None)()\n");
        final Value readline = evalInternal(context, "import readline; readline");
        final Value getCompleter = readline.getMember("get_completer").execute();
        final Value shouldRecord = readline.getMember("get_auto_history");
        final Value addHistory = readline.getMember("add_history");
        final Value getHistoryItem = readline.getMember("get_history_item");
        final Value setHistoryItem = readline.getMember("replace_history_item");
        final Value deleteHistoryItem = readline.getMember("remove_history_item");
        final Value clearHistory = readline.getMember("clear_history");
        final Value getHistorySize = readline.getMember("get_current_history_length");

        Function<String, List<String>> completer = null;
        if (getCompleter.canExecute()) {
            completer = (buffer) -> {
                List<String> candidates = new ArrayList<>();
                Value candidate = getCompleter.execute(buffer, candidates.size());
                while (candidate.isString()) {
                    candidates.add(candidate.asString());
                    candidate = getCompleter.execute(buffer, candidates.size());
                }
                return candidates;
            };
        }
        consoleHandler.setupReader(
                        () -> shouldRecord.execute().asBoolean(),
                        () -> getHistorySize.execute().asInt(),
                        (item) -> addHistory.execute(item),
                        (pos) -> getHistoryItem.execute(pos).asString(),
                        (pos, item) -> setHistoryItem.execute(pos, item),
                        (pos) -> deleteHistoryItem.execute(pos),
                        () -> clearHistory.execute(),
                        completer);

    }

    private static void setupTerminal(ConsoleHandler consoleHandler) {
        consoleHandler.setupReader(() -> false, () -> 0, (item) -> {
        }, (pos) -> null, (pos, item) -> {
        }, (pos) -> {
        }, () -> {
        }, null);
    }

    /**
     * Some system properties have already been read at this point, so to change them, we just
     * re-execute the process with the additional options.
     */
    private void subExec(List<String> args, List<String> subProcessDefs) {
        List<String> cmd = getCmdline(args, subProcessDefs);
        try {
            System.exit(new ProcessBuilder(cmd.toArray(new String[0])).inheritIO().start().waitFor());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    protected String getResolvedExecutableName() {
        // Note that with thin launchers, both graalpy and graalpy-managed are actual executables
        // that load and start GraalPy from a shared library
        if (ImageInfo.inImageRuntimeCode()) {
            // This is the fastest, most straightforward way to get the name of the actual
            // executable, i.e., with symlinks resolved all the way down to either graalpy or
            // graalpy-managed.
            String executableName = ProcessProperties.getExecutableName();
            if (executableName != null) {
                return executableName;
            }
        }
        // The program name can be a symlink, or a command resolved via $PATH
        // We use getLauncherExecName, which should resolve $PATH commands for us
        String launcherExecName = getLauncherExecName();
        if (launcherExecName == null) {
            return null;
        }
        Path programPath = Paths.get(launcherExecName);
        Path realPath;
        try {
            // toRealPath resolves symlinks along the way
            realPath = programPath.toRealPath();
        } catch (IOException ex) {
            throw abort(String.format("Cannot determine the executable name from path: '%s'", programPath), 72);
        }
        Path f = realPath.getFileName();
        if (f == null) {
            throw abort(String.format("Cannot determine the executable name from path: '%s'", realPath), 73);
        }
        return f.toString();
    }

    private List<String> getCmdline(List<String> args, List<String> subProcessDefs) {
        List<String> cmd = new ArrayList<>();
        if (isAOT()) {
            cmd.add(ProcessProperties.getExecutableName());
            for (String subProcArg : subProcessDefs) {
                assert subProcArg.startsWith("D");
                cmd.add("--native." + subProcArg);
            }
        } else {
            cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
            switch (System.getProperty("java.vm.name")) {
                case "Java HotSpot(TM) 64-Bit Server VM":
                    cmd.add("-server");
                    break;
                case "Java HotSpot(TM) 64-Bit Client VM":
                    cmd.add("-client");
                    break;
                default:
                    break;
            }
            cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
            cmd.add("-cp");
            cmd.add(ManagementFactory.getRuntimeMXBean().getClassPath());
            for (String subProcArg : subProcessDefs) {
                assert subProcArg.startsWith("D") || subProcArg.startsWith("agent");
                cmd.add("-" + subProcArg);
            }
            cmd.add(getMainClass());
        }

        cmd.addAll(args);
        return cmd;
    }

    private static final class ExitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int code;

        ExitException(int code) {
            this.code = code;
        }
    }

    private static enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        ESCAPE_SINGLE_QUOTE,
        ESCAPE_DOUBLE_QUOTE,
        VTAB_DELIMITED,
    }

    private static List<String> getDefaultEnvironmentArgs() {
        String pid;
        if (isAOT()) {
            pid = String.valueOf(ProcessProperties.getProcessID());
        } else {
            pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        }
        String uuid = UUID.randomUUID().toString();
        String envArgsOpt = getEnv("GRAAL_PYTHON_ARGS");
        ArrayList<String> envArgs = new ArrayList<>();
        if (envArgsOpt != null) {
            State s = State.NORMAL;
            StringBuilder sb = new StringBuilder();
            char[] charArray = envArgsOpt.toCharArray();
            for (int i = 0; i < charArray.length; i++) {
                char x = charArray[i];
                if (i == 0 && x == '\013') {
                    s = State.VTAB_DELIMITED;
                } else if (s == State.VTAB_DELIMITED && x == '\013') {
                    addArgument(pid, uuid, envArgs, sb);
                } else if (s == State.VTAB_DELIMITED && x != '\013') {
                    sb.append(x);
                } else if (s == State.NORMAL && Character.isWhitespace(x)) {
                    addArgument(pid, uuid, envArgs, sb);
                } else {
                    if (x == '"') {
                        if (s == State.NORMAL) {
                            s = State.DOUBLE_QUOTE;
                        } else if (s == State.DOUBLE_QUOTE) {
                            s = State.NORMAL;
                        } else if (s == State.ESCAPE_DOUBLE_QUOTE) {
                            s = State.DOUBLE_QUOTE;
                            sb.append(x);
                        } else {
                            sb.append(x);
                        }
                    } else if (x == '\'') {
                        if (s == State.NORMAL) {
                            s = State.SINGLE_QUOTE;
                        } else if (s == State.SINGLE_QUOTE) {
                            s = State.NORMAL;
                        } else if (s == State.ESCAPE_SINGLE_QUOTE) {
                            s = State.SINGLE_QUOTE;
                            sb.append(x);
                        } else {
                            sb.append(x);
                        }
                    } else if (x == '\\') {
                        if (s == State.SINGLE_QUOTE) {
                            s = State.ESCAPE_SINGLE_QUOTE;
                        } else if (s == State.DOUBLE_QUOTE) {
                            s = State.ESCAPE_DOUBLE_QUOTE;
                        }
                    } else {
                        sb.append(x);
                    }
                }
            }
            addArgument(pid, uuid, envArgs, sb);
        }
        return envArgs;
    }

    private static void addArgument(String pid, String uuid, ArrayList<String> envArgs, StringBuilder sb) {
        if (sb.length() > 0) {
            String arg = sb.toString().replace("$UUID$", uuid).replace("$$", pid).replace("\\$", "$");
            envArgs.add(arg);
            sb.setLength(0);
        }
    }

    private static boolean doEcho(@SuppressWarnings("unused") Context context) {
        return true;
    }

    private void log(String message) {
        log(message, "");
    }

    private void log(String message1, Object message2) {
        if (verboseLauncher) {
            System.err.println("GraalPy launcher: " + message1 + message2);
        }
    }
}
