/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.J_READLINE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_READLINE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J_READLINE)
public final class ReadlineModuleBuiltins extends PythonBuiltins {

    public static final TruffleLogger LOGGER = PythonLanguage.getLogger(ReadlineModuleBuiltins.class);
    public static final TruffleString T__CONSOLE_HANDLER = tsLiteral("_console_handler");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReadlineModuleBuiltinsFactory.getFactories();
    }

    private static final class LocalData {
        private Object consoleHandler;
        private final List<TruffleString> history = new ArrayList<>();
        private Object completer = null;
        private boolean autoHistory = true;
        private TruffleString completerDelims = null;
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule module = core.lookupBuiltinModule(T_READLINE);
        LocalData moduleState = new LocalData();
        module.setModuleState(moduleState);
        Object consoleHandler = core.lookupBuiltinModule(T_SYS).getAttribute(T__CONSOLE_HANDLER);
        if (consoleHandler != PNone.NO_VALUE) {
            InteropLibrary lib = InteropLibrary.getUncached(consoleHandler);
            try {
                if (lib.isMemberInvocable(consoleHandler, "initializeReadline")) {
                    lib.invokeMember(consoleHandler, "initializeReadline", module);
                    moduleState.consoleHandler = consoleHandler;
                }
            } catch (Exception e) {
                throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.RuntimeError, ErrorMessages.M, e);
            }
        }
    }

    public static Object getConsoleHandlerIfInitialized(PythonContext context) {
        PythonModule module = context.lookupBuiltinModule(T_READLINE);
        if (module != null) {
            LocalData moduleState = module.getModuleState(LocalData.class);
            if (moduleState != null) {
                return moduleState.consoleHandler;
            }
        }
        return null;
    }

    @Builtin(name = "get_completer", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetCompleterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getCompleter(PythonModule self) {
            LocalData data = self.getModuleState(LocalData.class);
            if (data.completer != null) {
                return data.completer;
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "set_completer", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetCompleterNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleter(PythonModule self, Object callable) {
            LocalData data = self.getModuleState(LocalData.class);
            data.completer = callable;
            return PNone.NONE;
        }
    }

    @Builtin(name = "parse_and_bind", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ParseAndBindNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone parseAndBind(@SuppressWarnings("unused") PythonModule self, @SuppressWarnings("unused") TruffleString tspec) {
            // TODO implement
            return PNone.NONE;
        }
    }

    @Builtin(name = "read_init_file", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ReadInitNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone setCompleter(@SuppressWarnings("unused") PythonModule self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.OSError, ErrorMessages.NOT_IMPLEMENTED);
        }
    }

    @Builtin(name = "get_current_history_length", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetHistoryLengthNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int setCompleter(PythonModule self) {
            LocalData data = self.getModuleState(LocalData.class);
            return data.history.size();
        }
    }

    @Builtin(name = "get_history_item", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetHistoryLengthNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        TruffleString setCompleter(PythonModule self, int index) {
            LocalData data = self.getModuleState(LocalData.class);
            try {
                return data.history.get(index);
            } catch (IndexOutOfBoundsException e) {
                throw PRaiseNode.raiseStatic(this, PythonErrorType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @Builtin(name = "replace_history_item", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ReplaceItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        TruffleString setCompleter(PythonModule self, int index, PString string,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return setCompleter(self, index, castToStringNode.execute(inliningTarget, string));
        }

        @Specialization
        @TruffleBoundary
        TruffleString setCompleter(PythonModule self, int index, TruffleString string) {
            LocalData data = self.getModuleState(LocalData.class);
            try {
                return data.history.set(index, string);
            } catch (IndexOutOfBoundsException e) {
                throw PRaiseNode.raiseStatic(this, PythonErrorType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @Builtin(name = "remove_history_item", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class DeleteItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        TruffleString setCompleter(PythonModule self, int index) {
            LocalData data = self.getModuleState(LocalData.class);
            try {
                return data.history.remove(index);
            } catch (IndexOutOfBoundsException e) {
                throw PRaiseNode.raiseStatic(this, PythonErrorType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @Builtin(name = "add_history", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class AddHistoryNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone addHistory(PythonModule self, PString item,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return addHistory(self, castToStringNode.execute(inliningTarget, item));
        }

        @Specialization
        @TruffleBoundary
        static PNone addHistory(PythonModule self, TruffleString item) {
            LocalData data = self.getModuleState(LocalData.class);
            data.history.add(item);
            return PNone.NONE;
        }
    }

    @Builtin(name = "read_history_file", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ReadHistoryFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleter(PythonModule self, PString path,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return setCompleter(self, castToStringNode.execute(inliningTarget, path));
        }

        @Specialization
        @TruffleBoundary
        @SuppressWarnings("try")
        PNone setCompleter(PythonModule self, TruffleString path) {
            LocalData data = self.getModuleState(LocalData.class);
            try (GilNode.UncachedRelease gil = GilNode.uncachedRelease()) {
                BufferedReader reader = getContext().getEnv().getPublicTruffleFile(path.toJavaStringUncached()).newBufferedReader();
                String line;
                while ((line = reader.readLine()) != null) {
                    data.history.add(toTruffleStringUncached(line));
                }
                reader.close();
            } catch (IOException e) {
                throw PRaiseNode.raiseStatic(this, PythonErrorType.IOError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "write_history_file", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class WriteHistoryFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleter(PythonModule self, PString path,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return setCompleter(self, castToStringNode.execute(inliningTarget, path));
        }

        @Specialization
        @TruffleBoundary
        PNone setCompleter(PythonModule self, TruffleString path) {
            LocalData data = self.getModuleState(LocalData.class);
            try {
                BufferedWriter writer = getContext().getEnv().getPublicTruffleFile(path.toJavaStringUncached()).newBufferedWriter(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                for (TruffleString l : data.history) {
                    writer.write(l.toJavaStringUncached());
                    writer.newLine();
                }
                writer.close();
            } catch (IOException e) {
                throw PRaiseNode.raiseStatic(this, PythonErrorType.IOError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "clear_history", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ClearNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PNone setCompleter(PythonModule self) {
            LocalData data = self.getModuleState(LocalData.class);
            data.history.clear();
            return PNone.NONE;
        }
    }

    @Builtin(name = "insert_text", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InsertTextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone setCompleter(@SuppressWarnings("unused") Object text) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "redisplay", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class RedisplayNode extends PythonBuiltinNode {
        @Specialization
        static PNone setCompleter() {
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_auto_history", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetAutoHistoryNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean setCompleter(PythonModule self) {
            LocalData data = self.getModuleState(LocalData.class);
            return data.autoHistory;
        }
    }

    @Builtin(name = "set_auto_history", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetAutoHistoryNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone setCompleter(PythonModule self, boolean enabled) {
            LocalData data = self.getModuleState(LocalData.class);
            data.autoHistory = enabled;
            return PNone.NONE;
        }
    }

    @Builtin(name = "set_completer_delims", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetCompleterDelimsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone setCompleterDelims(PythonModule self, TruffleString completerDelims) {
            LocalData data = self.getModuleState(LocalData.class);
            data.completerDelims = completerDelims;
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_completer_delims", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetCompleterDelimsNode extends PythonBuiltinNode {
        @Specialization
        static Object getCompleterDelims(PythonModule self) {
            LocalData data = self.getModuleState(LocalData.class);
            return (data.completerDelims != null) ? data.completerDelims : PNone.NONE;
        }
    }

    @TruffleBoundary
    static Object getCompletionStateMember(PythonModule self, String member) {
        Object consoleHandler = self.getModuleState(LocalData.class).consoleHandler;
        if (consoleHandler != null) {
            try {
                InteropLibrary lib = InteropLibrary.getUncached();
                Object completionState = lib.invokeMember(consoleHandler, "getCompletionState");
                if (!lib.isNull(completionState)) {
                    Object obj = lib.readMember(completionState, member);
                    if (!lib.isNull(obj)) {
                        return obj;
                    }
                }
            } catch (InteropException e) {
                LOGGER.fine(() -> PythonUtils.formatJString("Exception in getting completion state member %s: %s", member, e));
            }
        }
        return null;
    }

    @TruffleBoundary
    static int getCompletionStateIntMember(PythonModule self, String member, int defaultValue) {
        Object idx = getCompletionStateMember(self, member);
        if (idx != null) {
            try {
                return InteropLibrary.getUncached().asInt(idx);
            } catch (InteropException e) {
                LOGGER.fine(() -> PythonUtils.formatJString("Exception in getting completion state member %s: %s", member, e));
            }
        }
        return defaultValue;
    }

    @Builtin(name = "get_line_buffer", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetLineBuffer extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object get(PythonModule self) {
            Object lineObj = getCompletionStateMember(self, "line");
            if (lineObj != null) {
                try {
                    return InteropLibrary.getUncached().asTruffleString(lineObj);
                } catch (InteropException e) {
                    LOGGER.fine(() -> PythonUtils.formatJString("Exception in getting completion state member %s: %s", "line", e));
                }
            }
            return T_EMPTY_STRING;
        }
    }

    @Builtin(name = "get_begidx", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetBegidx extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object get(PythonModule self) {
            return getCompletionStateIntMember(self, "beginIdx", 0);
        }
    }

    @Builtin(name = "get_endidx", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetEndidx extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object get(PythonModule self) {
            return getCompletionStateIntMember(self, "endIdx", 0);
        }
    }
}
