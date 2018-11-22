/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

import jline.console.UserInterruptException;

public class GraalPythonMain extends AbstractLanguageLauncher {
    public static void main(String[] args) {
        new GraalPythonMain().launch(args);
    }

    private static final String LANGUAGE_ID = "python";
    private static final String MIME_TYPE = "text/x-python";

    private ArrayList<String> programArgs = null;
    private String commandString = null;
    private String inputFile = null;
    private String module = null;
    private boolean ignoreEnv = false;
    private boolean inspectFlag = false;
    private boolean verboseFlag = false;
    private boolean quietFlag = false;
    private boolean noUserSite = false;
    private boolean noSite = false;
    private boolean stdinIsInteractive = System.console() != null;
    private boolean runCC = false;
    private boolean runLD = false;
    private VersionAction versionAction = VersionAction.None;

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        List<String> inputArgs = new ArrayList<>(arguments);
        List<String> subprocessArgs = new ArrayList<>();
        programArgs = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            switch (arg) {
                case "-B":
                    System.out.println("Warning: " + arg + " does nothing on GraalPython.");
                    break;
                case "-c":
                    i += 1;
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
                    i += 1;
                    if (i < arguments.size()) {
                        module = arguments.get(i);
                    } else {
                        print("Argument expected for the -m option");
                        printShortHelp();
                    }
                    break;
                case "-O":
                case "-OO":
                    System.out.println("Warning: " + arg + " does nothing on GraalPython.");
                    break;
                case "-q":
                    quietFlag = true;
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
                case "-CC":
                    runCC = true;
                    programArgs.addAll(arguments.subList(i + 1, arguments.size()));
                    return unrecognized;
                case "-LD":
                    runLD = true;
                    programArgs.addAll(arguments.subList(i + 1, arguments.size()));
                    return unrecognized;
                case "-debug-perf":
                    subprocessArgs.add("Dgraal.TraceTruffleCompilation=true");
                    subprocessArgs.add("Dgraal.TraceTrufflePerformanceWarnings=true");
                    subprocessArgs.add("Dgraal.TruffleCompilationExceptionsArePrinted=true");
                    subprocessArgs.add("Dgraal.TraceTruffleInlining=true");
                    subprocessArgs.add("Dgraal.TruffleTraceSplittingSummary=true");
                    inputArgs.remove("-debug-perf");
                    break;
                case "-dump":
                    subprocessArgs.add("Dgraal.Dump=");
                    inputArgs.remove("-dump");
                    break;
                case "-compile-truffle-immediately":
                    subprocessArgs.add("Dgraal.TruffleCompileImmediately=true");
                    subprocessArgs.add("Dgraal.TruffleCompilationExceptionsAreThrown=true");
                    inputArgs.remove("-compile-truffle-immediately");
                    break;
                default:
                    if (!arg.startsWith("-")) {
                        inputFile = arg;
                        programArgs.add(inputFile);
                        break;
                    } else {
                        unrecognized.add(arg);
                    }
            }

            if (inputFile != null || commandString != null || module != null) {
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

    private static void printShortHelp() {
        print("usage: python [option] ... [-c cmd | -m mod | file | -] [arg] ...\n" +
                        "Try `python -h' for more information.");
    }

    private static void print(String string) {
        System.out.println(string);
    }

    @Override
    protected void launch(Builder contextBuilder) {
        if (runLD) {
            launchLD();
            return;
        } else if (runCC) {
            launchCC();
            return;
        }

        if (!ignoreEnv) {
            String pythonpath = System.getenv("PYTHONPATH");
            if (pythonpath != null) {
                contextBuilder.option("python.PythonPath", pythonpath);
            }
            inspectFlag = inspectFlag || System.getenv("PYTHONINSPECT") != null;
            noUserSite = noUserSite || System.getenv("PYTHONNOUSERSITE") != null;
            verboseFlag = verboseFlag || System.getenv("PYTHONVERBOSE") != null;
        }

        // setting this to make sure our TopLevelExceptionHandler calls the excepthook
        // to print Python exceptions
        contextBuilder.option("python.AlwaysRunExcepthook", "true");
        contextBuilder.option("python.InspectFlag", Boolean.toString(inspectFlag));
        contextBuilder.option("python.VerboseFlag", Boolean.toString(verboseFlag));
        if (verboseFlag) {
            contextBuilder.option("log.python.level", "FINE");
        }
        contextBuilder.option("python.QuietFlag", Boolean.toString(quietFlag));
        contextBuilder.option("python.NoUserSiteFlag", Boolean.toString(noUserSite));
        contextBuilder.option("python.NoSiteFlag", Boolean.toString(noSite));

        ConsoleHandler consoleHandler = createConsoleHandler(System.in, System.out);
        contextBuilder.arguments(getLanguageId(), programArgs.toArray(new String[0])).in(consoleHandler.createInputStream());

        int rc = 1;
        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());

            if (!quietFlag && (verboseFlag || (commandString == null && inputFile == null && module == null && stdinIsInteractive))) {
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
            src = Source.newBuilder(getLanguageId(), new File(inputFile)).mimeType(MIME_TYPE).build();
        }
        context.eval(src);
    }

    private static String[] clangPrefix = {
                    "clang",
                    "-emit-llvm",
                    "-fPIC",
                    "-Wno-int-to-void-pointer-cast",
                    "-Wno-int-conversion",
                    "-Wno-incompatible-pointer-types-discards-qualifiers",
                    "-ggdb",
                    "-O1",
    };
    private static String[] optPrefix = {
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
    };
    private static String[] linkPrefix = {
                    "llvm-link",
                    "-o",
    };

    private static String[] combine(String[] prefix, String[] args) {
        String[] combined = Arrays.copyOf(prefix, prefix.length + args.length);
        System.arraycopy(args, 0, combined, prefix.length, args.length);
        return combined;
    }

    private static void exec(String[] cmdarray) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.inheritIO();
            processBuilder.command(cmdarray);
            int status = processBuilder.start().waitFor();
            if (status != 0) {
                System.exit(status);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void launchCC() {
        String targetFlags = System.getenv("LLVM_TARGET_FLAGS");

        // if we are started without -c, we need to run the linker later
        boolean runLinker = false;
        if (!programArgs.contains("-c")) {
            runLinker = true;
            programArgs.add(0, "-c");
        }

        // run the clang compiler to generate bc files
        String[] args = programArgs.toArray(new String[0]);
        if (targetFlags != null && !targetFlags.isEmpty()) {
            String[] flags = targetFlags.split(" ");
            args = combine(flags, args);
        }
        String[] combine = combine(clangPrefix, args);

        exec(combine);

        String output = getOutputFilename();
        ArrayList<String> outputFiles = new ArrayList<>();
        if (output == null) {
            // if no explicit output filename was given, we search the commandline for files for
            // which now
            // have a bc file and optimize those
            for (String f : programArgs) {
                if (Files.exists(Paths.get(f))) {
                    String bcFile = bcFileFromFilename(f);
                    if (Files.exists(Paths.get(bcFile))) {
                        outputFiles.add(bcFile);
                        exec(combine(optPrefix, new String[]{bcFile, bcFile}));
                    }
                }
            }
        } else {
            // if an explicit output filename was given, just optimize it
            if (Files.exists(Paths.get(output))) {
                outputFiles.add(output);
                exec(combine(optPrefix, new String[]{output, output}));
            }
        }
        if (runLinker) {
            link(outputFiles);
        }
    }

    private static String bcFileFromFilename(String f) {
        return f.substring(0, f.lastIndexOf('.') + 1) + "bc";
    }

    private void launchLD() {
        ArrayList<String> objectFiles = new ArrayList<>();
        StringBuilder droppedArgs = new StringBuilder();
        // we only use llvm-link, which doesn't support any ld flags,
        // so we only parse out the object files given on the commandline
        // and see if these are bytecode files we can work with
        for (int i = 0; i < programArgs.size(); i++) {
            String f = programArgs.get(i);
            if (f.endsWith(".o")) {
                String bcFile = bcFileFromFilename(f);
                if (Files.exists(Paths.get(bcFile))) {
                    objectFiles.add(bcFile);
                } else {
                    objectFiles.add(f);
                }
            } else if (f.endsWith(".bc")) {
                objectFiles.add(f);
            } else if (f.equals("-o")) {
                i++; // skip output file
            } else {
                droppedArgs.append(' ').append(f);
            }
        }
        if (droppedArgs.length() > 0) {
            System.err.print("Dropped linker arguments because we're using llvm-link:");
            System.err.println(droppedArgs.toString());
        }
        link(objectFiles);
    }

    private void link(ArrayList<String> objectFiles) {
        String output = getOutputFilename();
        if (output == null) {
            output = "a.out";
        }
        exec(combine(combine(linkPrefix, new String[]{output}), objectFiles.toArray(new String[0])));
    }

    private String getOutputFilename() {
        int idx = programArgs.indexOf("-o");
        if (idx >= 0) {
            return programArgs.get(idx + 1);
        }
        return null;
    }

    @Override
    protected String getLanguageId() {
        return LANGUAGE_ID;
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        print("usage: python [option] ... (-c cmd | file) [arg] ...\n" +
                        "Options and arguments (and corresponding environment variables):\n" +
                        "-B     : don't write .py[co] files on import; also PYTHONDONTWRITEBYTECODE=x\n" +
                        "-c cmd : program passed in as string (terminates option list)\n" +
                        // "-d : debug output from parser; also PYTHONDEBUG=x\n" +
                        // "-E : ignore PYTHON* environment variables (such as PYTHONPATH)\n" +
                        "-h     : print this help message and exit (also --help)\n" +
                        "-i     : inspect interactively after running script; forces a prompt even\n" +
                        "         if stdin does not appear to be a terminal; also PYTHONINSPECT=x\n" +
                        "-m mod : run library module as a script (terminates option list)\n" +
                        "-O     : optimize generated bytecode slightly; also PYTHONOPTIMIZE=x\n" +
                        "-OO    : remove doc-strings in addition to the -O optimizations\n" +
                        // "-R : use a pseudo-random salt to make hash() values of various types
                        // be\n" +
                        // " unpredictable between separate invocations of the interpreter, as\n" +
                        // " a defense against denial-of-service attacks\n" +
                        // "-Q arg : division options: -Qold (default), -Qwarn, -Qwarnall, -Qnew\n"
                        // +
                        "-q     : don't print version and copyright messages on interactive startup\n" +
                        "-s     : don't add user site directory to sys.path; also PYTHONNOUSERSITE\n" +
                        "-S     : don't imply 'import site' on initialization\n" +
                        // "-t : issue warnings about inconsistent tab usage (-tt: issue errors)\n"
                        // +
                        // "-u : unbuffered binary stdout and stderr; also PYTHONUNBUFFERED=x\n" +
                        // " see man page for details on internal buffering relating to '-u'\n" +
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
                        // "- : program read from stdin (default; interactive mode if a tty)\n" +
                        "arg ...: arguments passed to program in sys.argv[1:]\n" +
                        "\n" +
                        "Arguments specific to GraalPython.\n" +
                        "--show-version               : print the Python version number and continue.\n" +
                        "-CC                          : run the C compiler used for generating GraalPython C extensions.\n" +
                        "                               All following arguments are passed to the compiler.\n" +
                        "-LD                          : run the linker used for generating GraalPython C extensions.\n" +
                        "                               All following arguments are passed to the linker.\n" +
                        "-debug-perf                  : Enable tracing of Truffle compilations and its warnings\n" +
                        "-dump                        : Enable dumping of compilation graphs to IGV\n" +
                        "-compile-truffle-immediately : Start compiling on first invocation and throw compilation exceptions\n" +
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
                        "   in the range [0,4294967295] to get hash values with a predictable seed.");
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
                                // read another line of input
                                consoleHandler.setPrompt(continuePrompt);
                                String additionalInput = consoleHandler.readLine();
                                if (additionalInput == null) {
                                    throw new EOFException();
                                }
                                sb.append(additionalInput).append('\n');
                                // The only continuation in the while loop
                                continue;
                            } else if (e.isExit()) {
                                // usually from quit
                                throw new ExitException(e.getExitStatus());
                            } else if (e.isHostException() || e.isInternalError()) {
                                // we continue the repl even though the system may be broken
                                lastStatus = 1;
                                System.out.println(e.getMessage());
                            } else if (e.isGuestException()) {
                                // drop through to continue REPL and remember last eval was an error
                                lastStatus = 1;
                            }
                        }
                        break;
                    }
                } catch (EOFException e) {
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
                assert subProcArg.startsWith("D");
                cmd.add("-" + subProcArg);
            }
            cmd.add(GraalPythonMain.class.getName());
        }

        cmd.addAll(args);
        try {
            System.exit(new ProcessBuilder(cmd.toArray(new String[0])).inheritIO().start().waitFor());
        } catch (IOException | InterruptedException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    private static final class ExitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int code;

        ExitException(int code) {
            this.code = code;
        }
    }

    private static boolean doEcho(@SuppressWarnings("unused") Context context) {
        return true;
    }
}
