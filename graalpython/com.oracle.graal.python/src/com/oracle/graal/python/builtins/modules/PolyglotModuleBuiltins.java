/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.LiteralBuilder;
import com.oracle.truffle.api.source.Source.SourceBuilder;

@CoreFunctions(defineModule = "polyglot")
public final class PolyglotModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return PolyglotModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);

        PythonContext context = core.getContext();
        Env env = context.getEnv();
        String coreHome = context.getCoreHome();
        try {
            TruffleFile coreDir = env.getTruffleFile(coreHome);
            TruffleFile docDir = coreDir.resolveSibling("doc");
            if (docDir.exists() || docDir.getParent() != null && (docDir = coreDir.getParent().resolveSibling("doc")).exists()) {
                builtinConstants.put(SpecialAttributeNames.__DOC__, new String(docDir.resolve("INTEROP.md").readAllBytes()));
            }
        } catch (SecurityException | IOException e) {
        }
    }

    @Builtin(name = "import_value", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @GenerateNodeFactory
    public abstract static class ImportNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object importSymbol(String name) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            Object object = env.importSymbol(name);
            if (object == null) {
                return PNone.NONE;
            }
            return object;
        }
    }

    @Builtin(name = "eval", minNumOfPositionalArgs = 0, parameterNames = {"path", "string", "language"})
    @GenerateNodeFactory
    abstract static class EvalInteropNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object evalString(@SuppressWarnings("unused") PNone path, String value, String langOrMimeType) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            try {
                boolean mimeType = isMimeType(langOrMimeType);
                String lang = mimeType ? findLanguageByMimeType(env, langOrMimeType) : langOrMimeType;
                raiseIfInternal(env, lang);
                LiteralBuilder newBuilder = Source.newBuilder(lang, value, value);
                if (mimeType) {
                    newBuilder = newBuilder.mimeType(langOrMimeType);
                }
                return env.parse(newBuilder.build()).call();
            } catch (RuntimeException e) {
                throw raise(NotImplementedError, e);
            }
        }

        private void raiseIfInternal(Env env, String lang) {
            LanguageInfo languageInfo = env.getLanguages().get(lang);
            if (languageInfo != null && languageInfo.isInternal()) {
                throw raise(NotImplementedError, "access to internal language %s is not permitted", lang);
            }
        }

        @TruffleBoundary
        @Specialization
        Object evalFile(String path, @SuppressWarnings("unused") PNone string, String langOrMimeType) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            try {
                boolean mimeType = isMimeType(langOrMimeType);
                String lang = mimeType ? findLanguageByMimeType(env, langOrMimeType) : langOrMimeType;
                raiseIfInternal(env, lang);
                SourceBuilder newBuilder = Source.newBuilder(lang, env.getTruffleFile(path));
                if (mimeType) {
                    newBuilder = newBuilder.mimeType(langOrMimeType);
                }
                return getContext().getEnv().parse(newBuilder.name(path).build()).call();
            } catch (IOException e) {
                throw raise(OSError, "%s", e);
            } catch (RuntimeException e) {
                throw raise(NotImplementedError, e);
            }
        }

        @TruffleBoundary
        @Specialization
        Object evalFile(String path, @SuppressWarnings("unused") PNone string, @SuppressWarnings("unused") PNone lang) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            try {
                return getContext().getEnv().parse(Source.newBuilder(PythonLanguage.ID, env.getTruffleFile(path)).name(path).build()).call();
            } catch (IOException e) {
                throw raise(OSError, "%s", e);
            } catch (RuntimeException e) {
                throw raise(NotImplementedError, e);
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        Object evalStringWithoutLang(PNone path, String string, PNone lang) {
            throw raise(ValueError, "polyglot.eval with a string argument must pass a language or mime-type");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object evalWithoutContent(Object path, Object string, Object lang) {
            throw raise(ValueError, "polyglot.eval must pass strings as either 'path' or a 'string' keyword");
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static String findLanguageByMimeType(Env env, String mimeType) {
            Map<String, LanguageInfo> languages = env.getLanguages();
            for (String language : languages.keySet()) {
                for (String registeredMimeType : languages.get(language).getMimeTypes()) {
                    if (mimeType.equals(registeredMimeType)) {
                        return language;
                    }
                }
            }
            return null;
        }

        protected boolean isMimeType(String lang) {
            return lang.contains("/");
        }
    }

    @Builtin(name = "export_value", minNumOfPositionalArgs = 1, parameterNames = {"name", "value"})
    @GenerateNodeFactory
    public abstract static class ExportSymbolNode extends PythonBuiltinNode {
        @Child private GetAttributeNode getNameAttributeNode;
        @Child private CastToStringNode castToStringNode;

        @Specialization(guards = "!isString(value)")
        @TruffleBoundary
        public Object exportSymbolKeyValue(String name, Object value) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            env.exportSymbol(name, value);
            return value;
        }

        @Specialization(guards = "!isString(value)")
        @TruffleBoundary
        public Object exportSymbolValueKey(Object value, String name) {
            PythonLanguage.getLogger().warning("[deprecation] polyglot.export_value(value, name) is deprecated " +
                            "and will be removed. Please swap the arguments.");
            return exportSymbolKeyValue(name, value);
        }

        @Specialization(guards = "isString(arg1)")
        @TruffleBoundary
        public Object exportSymbolAmbiguous(Object arg1, String arg2) {
            PythonLanguage.getLogger().warning("[deprecation] polyglot.export_value(str, str) is ambiguous. In the future, this will " +
                            "default to using the first argument as the name and the second as value, but now it " +
                            "uses the first argument as value and the second as the name.");
            return exportSymbolValueKey(arg1, arg2);
        }

        @Specialization
        @TruffleBoundary
        public Object exportSymbol(PFunction fun, @SuppressWarnings("unused") PNone name) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            env.exportSymbol(fun.getName(), fun);
            return fun;
        }

        @Specialization
        @TruffleBoundary
        public Object exportSymbol(PBuiltinFunction fun, @SuppressWarnings("unused") PNone name) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            env.exportSymbol(fun.getName(), fun);
            return fun;
        }

        @Specialization(guards = "isModule(fun.getSelf())")
        Object exportSymbol(VirtualFrame frame, PMethod fun, @SuppressWarnings("unused") PNone name) {
            export(getMethodName(frame, fun), fun);
            return fun;
        }

        @Specialization(guards = "isModule(fun.getSelf())")
        Object exportSymbol(VirtualFrame frame, PBuiltinMethod fun, @SuppressWarnings("unused") PNone name) {
            export(getMethodName(frame, fun), fun);
            return fun;
        }

        @Fallback
        public Object exportSymbol(Object value, Object name) {
            throw raise(PythonBuiltinClassType.TypeError, "expected argument types (function) or (object, str) but not (%p, %p)", value, name);
        }

        private String getMethodName(VirtualFrame frame, Object o) {
            if (getNameAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameAttributeNode = insert(GetAttributeNode.create(SpecialAttributeNames.__NAME__, null));
            }
            if (castToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToStringNode = insert(CastToStringNode.create());
            }
            return castToStringNode.execute(frame, getNameAttributeNode.executeObject(frame, o));
        }

        protected static boolean isModule(Object o) {
            return o instanceof PythonModule;
        }

        @TruffleBoundary
        private void export(String name, Object obj) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotAccessAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "polyglot access is not allowed");
            }
            env.exportSymbol(name, obj);
        }
    }

    @CompilationFinal static InteropLibrary UNCACHED_INTEROP;

    static InteropLibrary getInterop() {
        if (UNCACHED_INTEROP == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            UNCACHED_INTEROP = InteropLibrary.getFactory().getUncached();
        }
        return UNCACHED_INTEROP;
    }

    @Builtin(name = "__read__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReadNode extends PythonBuiltinNode {
        @Specialization
        Object read(Object receiver, Object key) {
            try {
                if (key instanceof String) {
                    return getInterop().readMember(receiver, (String) key);
                } else if (key instanceof Number) {
                    return getInterop().readArrayElement(receiver, ((Number) key).longValue());
                } else {
                    throw raise(PythonErrorType.AttributeError, "Unknown attribute: '%s'", key);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                throw raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__write__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBuiltinNode {
        @Specialization
        Object write(Object receiver, Object key, Object value) {
            try {
                if (key instanceof String) {
                    getInterop().writeMember(receiver, (String) key, value);
                } else if (key instanceof Number) {
                    getInterop().writeArrayElement(receiver, ((Number) key).longValue(), value);
                } else {
                    throw raise(PythonErrorType.AttributeError, "Unknown attribute: '%s'", key);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw raise(PythonErrorType.AttributeError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "__remove__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class removeNode extends PythonBuiltinNode {
        @Specialization
        Object remove(Object receiver, Object key) {
            try {
                if (key instanceof String) {
                    getInterop().removeMember(receiver, (String) key);
                } else if (key instanceof Number) {
                    getInterop().removeArrayElement(receiver, ((Number) key).longValue());
                } else {
                    throw raise(PythonErrorType.AttributeError, "Unknown attribute: '%s'", key);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                throw raise(PythonErrorType.AttributeError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "__execute__", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class executeNode extends PythonBuiltinNode {
        @Specialization
        Object exec(Object receiver, Object[] arguments) {
            try {
                return getInterop().execute(receiver, arguments);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__new__", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class newNode extends PythonBuiltinNode {
        @Specialization
        Object instantiate(Object receiver, Object[] arguments) {
            try {
                return getInterop().instantiate(receiver, arguments);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__invoke__", minNumOfPositionalArgs = 2, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class invokeNode extends PythonBuiltinNode {
        @Specialization
        Object invoke(Object receiver, String key, Object[] arguments) {
            try {
                return getInterop().invokeMember(receiver, key, arguments);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__is_null__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsNullNode extends PythonBuiltinNode {
        @Specialization
        boolean isNull(Object receiver) {
            return getInterop().isNull(receiver);
        }
    }

    @Builtin(name = "__has_size__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HasSizeNode extends PythonBuiltinNode {
        @Specialization
        boolean hasSize(Object receiver) {
            return getInterop().hasArrayElements(receiver);
        }
    }

    @Builtin(name = "__get_size__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetSizeNode extends PythonBuiltinNode {
        @Specialization
        Object getSize(Object receiver) {
            try {
                return getInterop().getArraySize(receiver);
            } catch (UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, e);
            }
        }
    }

    @Builtin(name = "__is_boxed__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsBoxedNode extends PythonBuiltinNode {
        @Specialization
        boolean isBoxed(Object receiver) {
            return getInterop().isString(receiver) || getInterop().fitsInDouble(receiver) || getInterop().fitsInLong(receiver);
        }
    }

    @Builtin(name = "__has_keys__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HasKeysNode extends PythonBuiltinNode {
        @Specialization
        boolean hasKeys(Object receiver) {
            return getInterop().hasMembers(receiver);
        }
    }

    @Builtin(name = "__key_info__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class KeyInfoNode extends PythonBuiltinNode {
        @Specialization
        boolean keyInfo(Object receiver, String member, String info) {
            if (info.equals("read-side-effects")) {
                return getInterop().hasMemberReadSideEffects(receiver, member);
            } else if (info.equals("write-side-effects")) {
                return getInterop().hasMemberWriteSideEffects(receiver, member);
            } else if (info.equals("exists")) {
                return getInterop().isMemberExisting(receiver, member);
            } else if (info.equals("readable")) {
                return getInterop().isMemberReadable(receiver, member);
            } else if (info.equals("writable")) {
                return getInterop().isMemberWritable(receiver, member);
            } else if (info.equals("insertable")) {
                return getInterop().isMemberInsertable(receiver, member);
            } else if (info.equals("removable")) {
                return getInterop().isMemberRemovable(receiver, member);
            } else if (info.equals("modifiable")) {
                return getInterop().isMemberModifiable(receiver, member);
            } else if (info.equals("invokable")) {
                return getInterop().isMemberInvocable(receiver, member);
            } else if (info.equals("internal")) {
                return getInterop().isMemberInternal(receiver, member);
            } else {
                return false;
            }
        }
    }

    @Builtin(name = "__keys__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonBuiltinNode {
        @Specialization
        Object remove(Object receiver) {
            try {
                return getInterop().getMembers(receiver);
            } catch (UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, e);
            }
        }
    }

    @Builtin(name = "__element_info__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ArrayElementInfoNode extends PythonBuiltinNode {
        @Specialization
        boolean keyInfo(Object receiver, long member, String info) {
            if (info.equals("exists")) {
                return getInterop().isArrayElementExisting(receiver, member);
            } else if (info.equals("readable")) {
                return getInterop().isArrayElementReadable(receiver, member);
            } else if (info.equals("writable")) {
                return getInterop().isArrayElementWritable(receiver, member);
            } else if (info.equals("insertable")) {
                return getInterop().isArrayElementInsertable(receiver, member);
            } else if (info.equals("removable")) {
                return getInterop().isArrayElementRemovable(receiver, member);
            } else if (info.equals("modifiable")) {
                return getInterop().isArrayElementModifiable(receiver, member);
            } else {
                return false;
            }
        }
    }

}
