/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

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

import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

import jline.console.UserInterruptException;

public class GraalPythonMain extends AbstractLanguageLauncher {
    public static void main(String[] args) {
        new GraalPythonMain().launch(args);
    }

    private static final String LANGUAGE_ID = "python";
    private static final String MIME_TYPE = "text/x-python";

    // provided by GraalVM bash launchers, ignored in native image mode
    private static final String BASH_LAUNCHER_EXEC_NAME = System.getProperty("org.graalvm.launcher.executablename");

    private ArrayList<String> programArgs = null;
    private String commandString = null;
    private String inputFile = null;
    private boolean isolateFlag = false;
    private boolean ignoreEnv = false;
    private boolean inspectFlag = false;
    private boolean verboseFlag = false;
    private boolean quietFlag = false;
    private boolean noUserSite = false;
    private boolean noSite = false;
    private boolean stdinIsInteractive = System.console() != null;
    private boolean unbufferedIO = false;
    private boolean multiContext = false;
    private VersionAction versionAction = VersionAction.None;
    private List<String> givenArguments;
    private List<String> relaunchArgs;
    private boolean wantsExperimental = false;
    private Map<String, String> enginePolyglotOptions;
    private boolean dontWriteBytecode = ImageInfo.inImageBuildtimeCode();

    @Override
    protected List<String> preprocessArguments(List<String> givenArgs, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        List<String> defaultEnvironmentArgs = getDefaultEnvironmentArgs();
        ArrayList<String> inputArgs = new ArrayList<>(defaultEnvironmentArgs);
        inputArgs.addAll(givenArgs);
        givenArguments = new ArrayList<>(inputArgs);
        List<String> arguments = new ArrayList<>(inputArgs);
        List<String> subprocessArgs = new ArrayList<>();
        programArgs = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            switch (arg) {
                case "-B":
                    dontWriteBytecode = true;
                    break;
                case "-c":
                    i += 1;
                    programArgs.add(arg);
                    if (i < arguments.size()) {
                        commandString = arguments.get(i);
                    } else {
                        print("Argument expected for the -c option");
                        printShortHelp();
                    }
                    break;
                case "-E":
                    ignoreEnv = true;
                    break;
                case "-h":
                    unrecognized.add("--help");
                    break;
                case "-i":
                    inspectFlag = true;
                    break;
                case "-m":
                    if (i + 1 < arguments.size()) {
                        // don't increment i here so that we capture the correct args
                        String module = arguments.get(i + 1);
                        commandString = "import runpy; runpy._run_module_as_main('" + module + "')";
                    } else {
                        print("Argument expected for the -m option");
                        printShortHelp();
                    }
                    break;
                case "-O":
                case "-OO":
                case "-R":
                case "-d":
                    break;
                case "-q":
                    quietFlag = true;
                    break;
                case "-I":
                    noUserSite = true;
                    ignoreEnv = true;
                    isolateFlag = true;
                    break;
                case "-s":
                    noUserSite = true;
                    break;
                case "-S":
                    noSite = true;
                    break;
                case "-v":
                    verboseFlag = true;
                    break;
                case "-V":
                case "--version":
                    versionAction = VersionAction.PrintAndExit;
                    break;
                case "--show-version":
                    versionAction = VersionAction.PrintAndContinue;
                    break;
                case "-debug-java":
                    if (wantsExperimental) {
                        if (!isAOT()) {
                            subprocessArgs.add("Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=y");
                            inputArgs.remove("-debug-java");
                        }
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
                case "-debug-perf":
                    if (wantsExperimental) {
                        subprocessArgs.add("Dgraal.TraceTruffleCompilation=true");
                        subprocessArgs.add("Dgraal.TraceTrufflePerformanceWarnings=true");
                        subprocessArgs.add("Dgraal.TruffleCompilationExceptionsArePrinted=true");
                        subprocessArgs.add("Dgraal.TraceTruffleInlining=true");
                        subprocessArgs.add("Dgraal.TruffleTraceSplittingSummary=true");
                        subprocessArgs.add("Dgraal.TraceTruffleTransferToInterpreter=true");
                        subprocessArgs.add("Dgraal.TraceTruffleAssumptions=true");
                        inputArgs.remove("-debug-perf");
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
                case "-multi-context":
                    if (wantsExperimental) {
                        multiContext = true;
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
                case "-dump":
                    if (wantsExperimental) {
                        subprocessArgs.add("Dgraal.Dump=");
                        subprocessArgs.add("Dgraal.TraceTruffleCompilation=true");
                        subprocessArgs.add("Dgraal.TraceTruffleInlining=true");
                        subprocessArgs.add("Dgraal.TraceTruffleTransferToInterpreter=true");
                        subprocessArgs.add("Dgraal.TruffleBackgroundCompilation=false");
                        inputArgs.remove("-dump");
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
                case "-compile-truffle-immediately":
                    if (wantsExperimental) {
                        subprocessArgs.add("Dgraal.TruffleCompileImmediately=true");
                        subprocessArgs.add("Dgraal.TruffleCompilationExceptionsAreThrown=true");
                        inputArgs.remove("-compile-truffle-immediately");
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
                case "-llvm-path":
                    print(new Driver("llvm-ar").getLLVMBinDir().toString());
                    System.exit(0);
                    break;
                case "-u":
                    unbufferedIO = true;
                    break;
                case "--experimental-options":
                case "--experimental-options=true":
                    // this is the default Truffle experimental option flag. We also use it for
                    // our custom launcher options
                    wantsExperimental = true;
                    addRelaunchArg(arg);
                    unrecognized.add(arg);
                    break;
                default:
                    if (!arg.startsWith("-")) {
                        inputFile = arg;
                        programArgs.add(inputFile);
                        break;
                    } else if (!arg.startsWith("--") && arg.length() > 2) {
                        // short arguments can be given together
                        String[] split = arg.substring(1).split("");
                        for (int j = 0; j < split.length; j++) {
                            String optionChar = split[j];
                            arguments.add(i + 1 + j, "-" + optionChar);
                        }
                    } else {
                        if (arg.startsWith("--llvm.") ||
                                        arg.startsWith("--python.CoreHome") ||
                                        arg.startsWith("--python.StdLibHome") ||
                                        arg.startsWith("--python.CAPI")) {
                            addRelaunchArg(arg);
                        }
                        // possibly a polyglot argument
                        unrecognized.add(arg);
                    }
            }

            if (inputFile != null || commandString != null) {
                i += 1;
                if (i < arguments.size()) {
                    programArgs.addAll(arguments.subList(i, arguments.size()));
                }
                break;
            }
        }

        // According to CPython if no arguments are given, they contain an empty string.
        if (programArgs.isEmpty()) {
            programArgs.add("");
        }

        if (!subprocessArgs.isEmpty()) {
            subExec(inputArgs, subprocessArgs);
        }

        return unrecognized;
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

    private static void printShortHelp() {
        print("usage: python [option] ... [-c cmd | -m mod | file | -] [arg] ...\n" +
                        "Try `python -h' for more information.");
    }

    private static void print(String string) {
        System.out.println(string);
    }

    private String[] getExecutableList() {
        if (ImageInfo.inImageCode()) {
            return execListWithRelaunchArgs(ProcessProperties.getExecutableName());
        } else {
            if (BASH_LAUNCHER_EXEC_NAME != null) {
                return execListWithRelaunchArgs(BASH_LAUNCHER_EXEC_NAME);
            }
            StringBuilder sb = new StringBuilder();
            ArrayList<String> exec_list = new ArrayList<>();
            sb.append(System.getProperty("java.home")).append(File.separator).append("bin").append(File.separator).append("java");
            exec_list.add(sb.toString());
            String javaOptions = System.getenv("_JAVA_OPTIONS");
            String javaToolOptions = System.getenv("JAVA_TOOL_OPTIONS");
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.matches("-Xrunjdwp:transport=dt_socket,server=y,address=\\d+,suspend=y")) {
                    arg = arg.replace("suspend=y", "suspend=n");
                }
                if ((javaOptions != null && javaOptions.contains(arg)) || (javaToolOptions != null && javaToolOptions.contains(arg))) {
                    // both _JAVA_OPTIONS and JAVA_TOOL_OPTIONS are adeed during
                    // JVM startup automatically. We do not want to repeat these
                    // for subprocesses, because they should also pick up those
                    // variables.
                    continue;
                }
                exec_list.add(arg);
            }
            exec_list.add("-classpath");
            exec_list.add(System.getProperty("java.class.path"));
            exec_list.add(GraalPythonMain.class.getName());
            if (relaunchArgs != null) {
                exec_list.addAll(relaunchArgs);
            }
            return exec_list.toArray(new String[exec_list.size()]);
        }
    }

    private String getExecutable() {
        if (ImageInfo.inImageBuildtimeCode()) {
            return "";
        } else {
            if (BASH_LAUNCHER_EXEC_NAME != null) {
                return BASH_LAUNCHER_EXEC_NAME;
            }
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
        if (!ignoreEnv) {
            String pythonpath = System.getenv("PYTHONPATH");
            if (pythonpath != null) {
                contextBuilder.option("python.PythonPath", pythonpath);
            }
            inspectFlag = inspectFlag || System.getenv("PYTHONINSPECT") != null;
            noUserSite = noUserSite || System.getenv("PYTHONNOUSERSITE") != null;
            verboseFlag = verboseFlag || System.getenv("PYTHONVERBOSE") != null;
            unbufferedIO = unbufferedIO || System.getenv("PYTHONUNBUFFERED") != null;
            dontWriteBytecode = dontWriteBytecode || System.getenv("PYTHONDONTWRITEBYTECODE") != null;
        }

        String executable = getContextOptionIfSetViaCommandLine("python.Executable");
        if (executable != null) {
            contextBuilder.option("python.ExecutableList", executable);
        } else {
            contextBuilder.option("python.Executable", getExecutable());
            // The unlikely separator is used because options need to be
            // strings. See PythonOptions.getExecutableList()
            contextBuilder.option("python.ExecutableList", String.join("🏆", getExecutableList()));
        }

        // setting this to make sure our TopLevelExceptionHandler calls the excepthook
        // to print Python exceptions
        contextBuilder.option("python.AlwaysRunExcepthook", "true");
        contextBuilder.option("python.InspectFlag", Boolean.toString(inspectFlag));
        contextBuilder.option("python.VerboseFlag", Boolean.toString(verboseFlag));
        contextBuilder.option("python.IsolateFlag", Boolean.toString(isolateFlag));
        contextBuilder.option("python.DontWriteBytecodeFlag", Boolean.toString(dontWriteBytecode));
        if (verboseFlag) {
            contextBuilder.option("log.python.level", "FINE");
        }
        contextBuilder.option("python.QuietFlag", Boolean.toString(quietFlag));
        contextBuilder.option("python.NoUserSiteFlag", Boolean.toString(noUserSite));
        contextBuilder.option("python.NoSiteFlag", Boolean.toString(noSite));
        contextBuilder.option("python.IgnoreEnvironmentFlag", Boolean.toString(ignoreEnv));
        contextBuilder.option("python.UnbufferedIO", Boolean.toString(unbufferedIO));

        ConsoleHandler consoleHandler = createConsoleHandler(System.in, System.out);
        contextBuilder.arguments(getLanguageId(), programArgs.toArray(new String[0])).in(consoleHandler.createInputStream());
        contextBuilder.option("python.TerminalIsInteractive", Boolean.toString(stdinIsInteractive));
        contextBuilder.option("python.TerminalWidth", Integer.toString(consoleHandler.getTerminalWidth()));
        contextBuilder.option("python.TerminalHeight", Integer.toString(consoleHandler.getTerminalHeight()));

        if (multiContext) {
            contextBuilder.engine(Engine.newBuilder().allowExperimentalOptions(true).options(enginePolyglotOptions).build());
        }

        int rc = 1;
        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());

            if (!quietFlag && (verboseFlag || (commandString == null && inputFile == null && stdinIsInteractive))) {
                print("Python " + evalInternal(context, "import sys; sys.version + ' on ' + sys.platform").asString());
                if (!noSite) {
                    print("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.");
                }
            }
            if (!noSite) {
                evalInternal(context, "import site\n");
            }
            if (!quietFlag) {
                System.err.println("Please note: This Python implementation is in the very early stages, " +
                                "and can run little more than basic benchmarks at this point.");
            }
            consoleHandler.setContext(context);

            if (commandString != null || inputFile != null) {
                try {
                    evalNonInteractive(context);
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

    private String getContextOptionIfSetViaCommandLine(String key) {
        if (System.getProperty("polyglot." + key) != null) {
            return System.getProperty("polyglot." + key);
        }
        for (String f : givenArguments) {
            if (f.startsWith("--" + key)) {
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

    private void evalNonInteractive(Context context) throws IOException {
        Source src;
        if (commandString != null) {
            src = Source.newBuilder(getLanguageId(), commandString, "<string>").build();
        } else {
            assert inputFile != null;
            String mimeType = "";
            try {
                mimeType = Files.probeContentType(Paths.get(inputFile));
            } catch (IOException e) {
            }
            File f = new File(inputFile);
            if (f.isDirectory() || (mimeType != null && mimeType.equals("application/zip"))) {
                String runMod = String.format("import sys; sys.path.insert(0, '%s'); import runpy; runpy._run_module_as_main('__main__', False)", inputFile);
                src = Source.newBuilder(getLanguageId(), runMod, "<string>").build();
            } else {
                src = Source.newBuilder(getLanguageId(), f).mimeType(MIME_TYPE).build();
            }
        }
        context.eval(src);
    }

    @Override
    protected String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        print("usage: python [option] ... (-c cmd | file) [arg] ...\n" +
                        "Options and arguments (and corresponding environment variables):\n" +
                        "-B     : on CPython, this disables writing .py[co] files on import;\n" +
                        "         GraalPython does not use bytecode, and thus this flag has no effect\n" +
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
                        "-R     : on CPython, this enables the use of a pseudo-random salt to make\n" +
                        "         hash()values of various types be unpredictable between separate\n" +
                        "         invocations of the interpreter, as a defense against denial-of-service\n" +
                        "         attacks; GraalPython always enables this and the flag has no effect.\n" +
                        // "-Q arg : division options: -Qold (default), -Qwarn, -Qwarnall, -Qnew\n"
                        // +
                        "-q     : don't print version and copyright messages on interactive startup\n" +
                        "-I     : don't add user site and script directory to sys.path; also PYTHONNOUSERSITE\n" +
                        "-s     : don't add user site directory to sys.path; also PYTHONNOUSERSITE\n" +
                        "-S     : don't imply 'import site' on initialization\n" +
                        // "-t : issue warnings about inconsistent tab usage (-tt: issue errors)\n"
                        // +
                        "-u     : unbuffered binary stdout and stderr; also PYTHONUNBUFFERED=x\n" +
                        "-v     : verbose (trace import statements); also PYTHONVERBOSE=x\n" +
                        "         can be supplied multiple times to increase verbosity\n" +
                        "-V     : print the Python version number and exit (also --version)\n" +
                        "         when given twice, print more information about the build\n" +
                        // "-W arg : warning control; arg is
                        // action:message:category:module:lineno\n" +
                        // " also PYTHONWARNINGS=arg\n" +
                        // "-x : skip first line of source, allowing use of non-Unix forms of
                        // #!cmd\n" +
                        // "-3 : warn about Python 3.x incompatibilities that 2to3 cannot trivially
                        // fix\n" +
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
                        "GRAAL_PYTHON_ARGS: the value is added as arguments as if passed on the\n" +
                        "   commandline. There is one special case: any `$$' in the value is replaced\n" +
                        "   with the current process id. To pass a literal `$$', you must escape the\n" +
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

    public ConsoleHandler createConsoleHandler(InputStream inStream, OutputStream outStream) {
        if (inputFile != null || commandString != null) {
            return new DefaultConsoleHandler(inStream, outStream);
        }
        return new JLineConsoleHandler(inStream, outStream, false);
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
    public int readEvalPrint(Context context, ConsoleHandler consoleHandler) {
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
                    if (input.isEmpty() || input.charAt(0) == '#') {
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
                                String additionalInput = consoleHandler.readLine();
                                while (additionalInput != null && !additionalInput.isEmpty()) {
                                    sb.append(additionalInput).append('\n');
                                    consoleHandler.setPrompt(continuePrompt);
                                    additionalInput = consoleHandler.readLine();
                                }
                                if (additionalInput == null) {
                                    throw new EOFException();
                                }
                                // The only continuation in the while loop
                                continue;
                            } else if (e.isExit()) {
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
                            evalInternal(context, "import site; exit()\n");
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

    private Value evalInternal(Context context, String code) {
        return context.eval(Source.newBuilder(getLanguageId(), code, "<internal>").internal(true).buildLiteral());
    }

    private void setupREPL(Context context, ConsoleHandler consoleHandler) {
        // Then we can get the readline module and see if any completers were registered and use its
        // history feature
        evalInternal(context, "import sys\ngetattr(sys, '__interactivehook__', lambda: None)()\n");
        final Value readline = evalInternal(context, "import readline; readline");
        final Value completer = readline.getMember("get_completer").execute();
        final Value shouldRecord = readline.getMember("get_auto_history");
        final Value addHistory = readline.getMember("add_history");
        final Value getHistoryItem = readline.getMember("get_history_item");
        final Value setHistoryItem = readline.getMember("replace_history_item");
        final Value deleteHistoryItem = readline.getMember("remove_history_item");
        final Value clearHistory = readline.getMember("clear_history");
        final Value getHistorySize = readline.getMember("get_current_history_length");
        consoleHandler.setHistory(
                        () -> shouldRecord.execute().asBoolean(),
                        () -> getHistorySize.execute().asInt(),
                        (item) -> addHistory.execute(item),
                        (pos) -> getHistoryItem.execute(pos).asString(),
                        (pos, item) -> setHistoryItem.execute(pos, item),
                        (pos) -> deleteHistoryItem.execute(pos),
                        () -> clearHistory.execute());

        if (completer.canExecute()) {
            consoleHandler.addCompleter((buffer) -> {
                List<String> candidates = new ArrayList<>();
                Value candidate = completer.execute(buffer, candidates.size());
                while (candidate.isString()) {
                    candidates.add(candidate.asString());
                    candidate = completer.execute(buffer, candidates.size());
                }
                return candidates;
            });
        }
    }

    /**
     * Some system properties have already been read at this point, so to change them, we just
     * re-execute the process with the additional options.
     */
    private static void subExec(List<String> args, List<String> subProcessDefs) {
        List<String> cmd = getCmdline(args, subProcessDefs);
        try {
            System.exit(new ProcessBuilder(cmd.toArray(new String[0])).inheritIO().start().waitFor());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    static List<String> getCmdline(List<String> args, List<String> subProcessDefs) {
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
                    cmd.add("-d64");
                    break;
                case "Java HotSpot(TM) 64-Bit Client VM":
                    cmd.add("-client");
                    cmd.add("-d64");
                    break;
                default:
                    break;
            }
            cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
            cmd.add("-cp");
            cmd.add(ManagementFactory.getRuntimeMXBean().getClassPath());
            for (String subProcArg : subProcessDefs) {
                assert subProcArg.startsWith("D") || subProcArg.startsWith("X");
                cmd.add("-" + subProcArg);
            }
            cmd.add(GraalPythonMain.class.getName());
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
    }

    private static List<String> getDefaultEnvironmentArgs() {
        String pid;
        if (isAOT()) {
            pid = String.valueOf(ProcessProperties.getProcessID());
        } else {
            pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        }
        String envArgsOpt = System.getenv("GRAAL_PYTHON_ARGS");
        ArrayList<String> envArgs = new ArrayList<>();
        State s = State.NORMAL;
        StringBuilder sb = new StringBuilder();
        if (envArgsOpt != null) {
            for (char x : envArgsOpt.toCharArray()) {
                if (s == State.NORMAL && Character.isWhitespace(x)) {
                    addArgument(pid, envArgs, sb);
                } else {
                    if (x == '"') {
                        if (s == State.NORMAL) {
                            s = State.DOUBLE_QUOTE;
                        } else if (s == State.DOUBLE_QUOTE) {
                            s = State.NORMAL;
                        } else if (s == State.ESCAPE_DOUBLE_QUOTE) {
                            s = State.DOUBLE_QUOTE;
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
            addArgument(pid, envArgs, sb);
        }
        return envArgs;
    }

    private static void addArgument(String pid, ArrayList<String> envArgs, StringBuilder sb) {
        if (sb.length() > 0) {
            String arg = sb.toString().replace("$$", pid).replace("\\$", "$");
            envArgs.add(arg);
            sb.setLength(0);
        }
    }

    private static boolean doEcho(@SuppressWarnings("unused") Context context) {
        return true;
    }
}
