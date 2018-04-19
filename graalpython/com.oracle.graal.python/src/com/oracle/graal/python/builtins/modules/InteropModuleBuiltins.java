/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.IOException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.Builder;

@CoreFunctions(defineModule = "polyglot")
public final class InteropModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return InteropModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "import_value", minNumOfArguments = 1, keywordArguments = {"name"})
    @GenerateNodeFactory
    public abstract static class ImportNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object importSymbol(String name) {
            Object object = getContext().getEnv().importSymbol(name);
            if (object == null) {
                return PNone.NONE;
            }
            return object;
        }
    }

    @Builtin(name = "eval", minNumOfArguments = 0, keywordArguments = {"path", "string", "language"})
    @GenerateNodeFactory
    abstract static class EvalInteropNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object evalString(@SuppressWarnings("unused") PNone path, String value, String lang) {
            try {
                return getContext().getEnv().parse(builderWithMimeType(lang, Source.newBuilder(value).name(value)).build()).call();
            } catch (RuntimeException e) {
                throw raise(NotImplementedError, e.getMessage());
            }
        }

        @TruffleBoundary
        @Specialization
        Object evalFile(String path, @SuppressWarnings("unused") PNone string, String lang) {
            Env env = getContext().getEnv();
            try {
                return getContext().getEnv().parse(builderWithMimeType(lang, env.newSourceBuilder(env.getTruffleFile(path)).name(path)).build()).call();
            } catch (IOException e) {
                throw raise(OSError, "%s", e);
            } catch (RuntimeException e) {
                throw raise(NotImplementedError, e.getMessage());
            }
        }

        @TruffleBoundary
        @Specialization
        Object evalFile(String path, @SuppressWarnings("unused") PNone string, @SuppressWarnings("unused") PNone lang) {
            Env env = getContext().getEnv();
            try {
                return getContext().getEnv().parse(env.newSourceBuilder(env.getTruffleFile(path)).name(path).build()).call();
            } catch (IOException e) {
                throw raise(OSError, "%s", e);
            } catch (RuntimeException e) {
                throw raise(NotImplementedError, e.getMessage());
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        Object evalStringWithoutLang(PNone path, String string, PNone lang) {
            throw raise(ValueError, "interop.eval with a string argument must pass a language or mime-type");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object evalWithoutContent(Object path, Object string, Object lang) {
            throw raise(ValueError, "interop.eval must pass strings as either 'path' or a 'string' keyword");
        }

        private static <T extends Exception, T2 extends Exception> Builder<T, RuntimeException, RuntimeException> builderWithMimeType(String lang,
                        Builder<T, T2, RuntimeException> baseBuilder) {
            Builder<T, RuntimeException, RuntimeException> builder;
            if (lang.contains("/")) {
                builder = baseBuilder.mimeType(lang);
            } else {
                builder = baseBuilder.language(lang);
            }
            return builder;
        }
    }

    @Builtin(name = "export_value", minNumOfArguments = 1, keywordArguments = {"name"})
    @GenerateNodeFactory
    public abstract static class ExportSymbolNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object exportSymbol(Object value, String name) {
            getContext().getEnv().exportSymbol(name, value);
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        public Object exportSymbol(PythonCallable value, PNone name) {
            getContext().getEnv().exportSymbol(value.getName(), value);
            return value;
        }
    }

    @Builtin(name = "__read__", fixedNumOfArguments = 2)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class ReadNode extends PythonBuiltinNode {
        @Specialization
        Object read(TruffleObject receiver, Object key,
                        @Cached("READ.createNode()") Node readNode) {
            try {
                return ForeignAccess.sendRead(readNode, receiver, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.AttributeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__write__", fixedNumOfArguments = 3)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBuiltinNode {
        @Specialization
        Object write(TruffleObject receiver, Object key, Object value,
                        @Cached("WRITE.createNode()") Node writeNode) {
            try {
                return ForeignAccess.sendWrite(writeNode, receiver, key, value);
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw raise(PythonErrorType.AttributeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__remove__", fixedNumOfArguments = 2)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class removeNode extends PythonBuiltinNode {
        @Specialization
        Object remove(TruffleObject receiver, Object key,
                        @Cached("REMOVE.createNode()") Node removeNode) {
            try {
                return ForeignAccess.sendRemove(removeNode, receiver, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.AttributeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__execute__", minNumOfArguments = 1, takesVariableArguments = true)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class executeNode extends PythonBuiltinNode {
        @Specialization
        Object remove(TruffleObject receiver, PTuple arguments,
                        @Cached("createExecute(0).createNode()") Node executeNode) {
            try {
                return ForeignAccess.sendExecute(executeNode, receiver, arguments.getArray());
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw raise(PythonErrorType.AttributeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__new__", minNumOfArguments = 1, takesVariableArguments = true)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class newNode extends PythonBuiltinNode {
        @Specialization
        Object remove(TruffleObject receiver, PTuple arguments,
                        @Cached("createNew(0).createNode()") Node executeNode) {
            try {
                return ForeignAccess.sendNew(executeNode, receiver, arguments.getArray());
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw raise(PythonErrorType.AttributeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__invoke__", minNumOfArguments = 2, takesVariableArguments = true)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class invokeNode extends PythonBuiltinNode {
        @Specialization
        Object remove(TruffleObject receiver, String key, PTuple arguments,
                        @Cached("createInvoke(0).createNode()") Node executeNode) {
            try {
                return ForeignAccess.sendInvoke(executeNode, receiver, key, arguments.getArray());
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw raise(PythonErrorType.AttributeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__is_null__", fixedNumOfArguments = 1)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class IsNullNode extends PythonBuiltinNode {
        @Specialization
        boolean remove(TruffleObject receiver,
                        @Cached("IS_NULL.createNode()") Node executeNode) {
            return ForeignAccess.sendIsNull(executeNode, receiver);
        }
    }

    @Builtin(name = "__has_size__", fixedNumOfArguments = 1)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class HasSizeNode extends PythonBuiltinNode {
        @Specialization
        boolean remove(@SuppressWarnings("unused") String receiver) {
            return true;
        }

        @Specialization
        boolean remove(TruffleObject receiver,
                        @Cached("HAS_SIZE.createNode()") Node executeNode) {
            return ForeignAccess.sendHasSize(executeNode, receiver);
        }
    }

    @Builtin(name = "__get_size__", fixedNumOfArguments = 1)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class GetSizeNode extends PythonBuiltinNode {
        @Specialization
        Object remove(TruffleObject receiver,
                        @Cached("GET_SIZE.createNode()") Node executeNode) {
            try {
                return ForeignAccess.sendGetSize(executeNode, receiver);
            } catch (UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, e.getMessage());
            }
        }
    }

    @Builtin(name = "__is_boxed__", fixedNumOfArguments = 1)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class IsBoxedNode extends PythonBuiltinNode {
        @Specialization
        boolean remove(TruffleObject receiver,
                        @Cached("IS_BOXED.createNode()") Node executeNode) {
            return ForeignAccess.sendIsBoxed(executeNode, receiver);
        }
    }

    @Builtin(name = "__has_keys__", fixedNumOfArguments = 1)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class HasKeysNode extends PythonBuiltinNode {
        @Specialization
        boolean remove(TruffleObject receiver,
                        @Cached("HAS_KEYS.createNode()") Node executeNode) {
            return ForeignAccess.sendHasKeys(executeNode, receiver);
        }

        @Fallback
        boolean remove(@SuppressWarnings("unused") Object receiver) {
            return false;
        }
    }

    @Builtin(name = "__key_info__", fixedNumOfArguments = 2)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class KeyInfoNode extends PythonBuiltinNode {
        @Specialization
        int remove(TruffleObject receiver, Object key,
                        @Cached("KEY_INFO.createNode()") Node executeNode) {
            return ForeignAccess.sendKeyInfo(executeNode, receiver, key);
        }
    }

    @Builtin(name = "__keys__", fixedNumOfArguments = 1)
    @ImportStatic(Message.class)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonBuiltinNode {
        @Specialization
        TruffleObject remove(TruffleObject receiver,
                        @Cached("KEYS.createNode()") Node executeNode) {
            try {
                return ForeignAccess.sendKeys(executeNode, receiver);
            } catch (UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, e.getMessage());
            }
        }
    }
}
