/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.HiddenKey;

@CoreFunctions(defineModule = "readline")
public class ReadlineModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey DATA = new HiddenKey("__data__");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReadlineModuleBuiltinsFactory.getFactories();
    }

    private static final class LocalData {
        private final HashMap<String, String> bindings = new HashMap<>();
        private final List<String> history = new ArrayList<>();
        protected Object completer = null;
        protected boolean autoHistory = true;
        protected String completerDelims = null;
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        core.lookupBuiltinModule("readline").setAttribute(DATA, new LocalData());
    }

    @Builtin(name = "get_completer", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetCompleterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getCompleter(PythonModule self,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
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
        PNone setCompleter(PythonModule self, Object callable,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            data.completer = callable;
            return PNone.NONE;
        }
    }

    @Builtin(name = "parse_and_bind", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ParseAndBindNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone setCompleter(PythonModule self, String spec,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            if (spec.startsWith("tab:")) {
                LocalData data = (LocalData) readNode.execute(self, DATA);
                data.bindings.put("tab", spec.split(":")[1].trim());
                return PNone.NONE;
            } else {
                throw raise(PythonBuiltinClassType.NotImplementedError, "any other binding than 'tab'");
            }
        }
    }

    @Builtin(name = "read_init_file", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ReadInitNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone setCompleter(@SuppressWarnings("unused") PythonModule self) {
            throw raise(PythonErrorType.OSError, "not implemented");
        }
    }

    @Builtin(name = "get_current_history_length", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetHistoryLengthNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int setCompleter(PythonModule self,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            return data.history.size();
        }
    }

    @Builtin(name = "get_history_item", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetHistoryLengthNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String setCompleter(PythonModule self, int index,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            try {
                return data.history.get(index);
            } catch (IndexOutOfBoundsException e) {
                throw raise(PythonErrorType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @Builtin(name = "replace_history_item", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ReplaceItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        String setCompleter(PythonModule self, int index, PString string,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            return setCompleter(self, index, string.getValue(), readNode);
        }

        @Specialization
        @TruffleBoundary
        String setCompleter(PythonModule self, int index, String string,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            try {
                return data.history.set(index, string);
            } catch (IndexOutOfBoundsException e) {
                throw raise(PythonErrorType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @Builtin(name = "remove_history_item", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class DeleteItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String setCompleter(PythonModule self, int index,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            try {
                return data.history.remove(index);
            } catch (IndexOutOfBoundsException e) {
                throw raise(PythonErrorType.IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS);
            }
        }
    }

    @Builtin(name = "add_history", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class AddHistoryNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone addHistory(PythonModule self, PString item,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            return addHistory(self, item.getValue(), readNode);
        }

        @Specialization
        @TruffleBoundary
        PNone addHistory(PythonModule self, String item,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            data.history.add(item);
            return PNone.NONE;
        }
    }

    @Builtin(name = "read_history_file", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ReadHistoryFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleter(PythonModule self, PString path,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            return setCompleter(self, path.getValue(), readNode);
        }

        @Specialization
        @TruffleBoundary
        PNone setCompleter(PythonModule self, String path,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            try {
                BufferedReader reader = getContext().getEnv().getPublicTruffleFile(path).newBufferedReader();
                String line;
                while ((line = reader.readLine()) != null) {
                    data.history.add(line);
                }
                reader.close();
            } catch (IOException e) {
                throw raise(PythonErrorType.IOError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "write_history_file", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class WriteHistoryFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleter(PythonModule self, PString path,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            return setCompleter(self, path.getValue(), readNode);
        }

        @Specialization
        @TruffleBoundary
        PNone setCompleter(PythonModule self, String path,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            try {
                BufferedWriter writer = getContext().getEnv().getPublicTruffleFile(path).newBufferedWriter(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                for (String l : data.history) {
                    writer.write(l);
                    writer.newLine();
                }
                writer.close();
            } catch (IOException e) {
                throw raise(PythonErrorType.IOError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "clear_history", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ClearNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone setCompleter(PythonModule self,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            data.history.clear();
            return PNone.NONE;
        }
    }

    @Builtin(name = "insert_text", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InsertTextNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone setCompleter(@SuppressWarnings("unused") Object text) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "redisplay", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class RedisplayNode extends PythonBuiltinNode {
        @Specialization
        PNone setCompleter() {
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_auto_history", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetAutoHistoryNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean setCompleter(PythonModule self,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            return data.autoHistory;
        }
    }

    @Builtin(name = "set_auto_history", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetAutoHistoryNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleter(PythonModule self, boolean enabled,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            data.autoHistory = enabled;
            return PNone.NONE;
        }
    }

    @Builtin(name = "set_completer_delims", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class SetCompleterDelimsNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone setCompleterDelims(PythonModule self, String completerDelims,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            data.completerDelims = completerDelims;
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_completer_delims", minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetCompleterDelimsNode extends PythonBuiltinNode {
        @Specialization
        Object getCompleterDelims(PythonModule self,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            LocalData data = (LocalData) readNode.execute(self, DATA);
            return (data.completerDelims != null) ? data.completerDelims : PNone.NONE;
        }
    }
}
