/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J_GET_REGISTERED_INTEROP_BEHAVIOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_INTEROP_BEHAVIOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_JAVA_INTEROP_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REGISTER_INTEROP_BEHAVIOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REMOVE_JAVA_INTEROP_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_REGISTER_INTEROP_BEHAVIOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REGISTER_JAVA_INTEROP_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_REGISTER_JAVA_INTEROP_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_BE_NUMBER;
import static com.oracle.graal.python.nodes.ErrorMessages.INTEROP_TYPE_ALREADY_REGISTERED;
import static com.oracle.graal.python.nodes.ErrorMessages.INTEROP_TYPE_NOT_REGISTERED;
import static com.oracle.graal.python.nodes.ErrorMessages.S_ARG_MUST_BE_S_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.S_CANNOT_HAVE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.S_DOES_NOT_TAKE_VARARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_EXACTLY_D_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_VARARGS;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_BIG_INTEGER;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_BYTE;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_DOUBLE;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_FLOAT;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_INT;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_LONG;
import static com.oracle.graal.python.nodes.InteropMethodNames.J_FITS_IN_SHORT;
import static com.oracle.graal.python.nodes.StringLiterals.T_READABLE;
import static com.oracle.graal.python.nodes.StringLiterals.T_WRITABLE;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetAttrO;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.InteropBehavior;
import com.oracle.graal.python.nodes.interop.InteropBehaviorMethod;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "polyglot")
public final class PolyglotModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_READ_SIDE_EFFECTS = tsLiteral("read-side-effects");
    private static final TruffleString T_WRITE_SIDE_EFFECTS = tsLiteral("write-side-effects");
    private static final TruffleString T_EXISTS = tsLiteral("exists");
    private static final TruffleString T_INSERTABLE = tsLiteral("insertable");
    private static final TruffleString T_REMOVABLE = tsLiteral("removable");
    private static final TruffleString T_MODIFIABLE = tsLiteral("modifiable");
    private static final TruffleString T_INVOKABLE = tsLiteral("invokable");
    private static final TruffleString T_INTERNAL = tsLiteral("internal");

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PolyglotModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);

        PythonContext context = core.getContext();
        Env env = context.getEnv();
        TruffleString coreHome = context.getCoreHome();
        try {
            TruffleFile coreDir = env.getInternalTruffleFile(coreHome.toJavaStringUncached());
            TruffleFile docDir = coreDir.resolveSibling("docs");
            if (docDir.exists() || docDir.getParent() != null && (docDir = coreDir.getParent().resolveSibling("docs")).exists()) {
                addBuiltinConstant(SpecialAttributeNames.T___DOC__, new String(docDir.resolve("user").resolve("Interoperability.md").readAllBytes()));
            }
        } catch (SecurityException | IOException e) {
        }
    }

    @Builtin(name = "import_value", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @GenerateNodeFactory
    public abstract static class ImportNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object importSymbol(TruffleString name,
                        @Cached PForeignToPTypeNode convert) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotBindingsAccessAllowed()) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.NotImplementedError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED);
            }
            Object object = env.importSymbol(name.toJavaStringUncached());
            if (object == null) {
                return PNone.NONE;
            }
            return convert.executeConvert(object);
        }
    }

    @Builtin(name = "eval", minNumOfPositionalArgs = 0, parameterNames = {"path", "string", "language"})
    @GenerateNodeFactory
    abstract static class EvalInteropNode extends PythonTernaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object eval(Object pathObj, Object stringObj, Object languageObj) {
            if (languageObj instanceof PNone) {
                throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.POLYGLOT_EVAL_MUST_PASS_LANG);
            }
            boolean hasString = !(stringObj instanceof PNone);
            boolean hasPath = !(pathObj instanceof PNone);
            if (!hasString && !hasPath || hasString && hasPath) {
                throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.POLYGLOT_EVAL_MUST_PASS_STRING_OR_PATH);
            }
            String languageName = toJavaString(languageObj, "language");
            String string = hasString ? toJavaString(stringObj, "string") : null;
            String path = hasPath ? toJavaString(pathObj, "path") : null;
            Env env = getContext().getEnv();
            if (!env.isPolyglotEvalAllowed(null)) {
                throw PRaiseNode.raiseUncached(this, RuntimeError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED);
            }
            Map<String, LanguageInfo> languages = env.getPublicLanguages();
            String mimeType = null;
            if (isMimeType(languageName)) {
                mimeType = languageName;
                languageName = findLanguageByMimeType(languages, languageName);
            }
            LanguageInfo language = languages.get(languageName);
            if (language == null) {
                throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.POLYGLOT_LANGUAGE_S_NOT_FOUND, languageName);
            }
            if (!env.isPolyglotEvalAllowed(language)) {
                throw PRaiseNode.raiseUncached(this, RuntimeError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED_FOR_LANGUAGE_S, languageName);
            }
            try {
                SourceBuilder builder;
                if (hasString) {
                    builder = Source.newBuilder(languageName, string, string);
                } else {
                    builder = Source.newBuilder(languageName, env.getPublicTruffleFile(path)).name(path);
                }
                if (mimeType != null) {
                    builder = builder.mimeType(mimeType);
                }
                Object result = env.parsePublic(builder.build()).call();
                return PForeignToPTypeNode.getUncached().executeConvert(result);
            } catch (AbstractTruffleException e) {
                throw e;
            } catch (IOException e) {
                throw PRaiseNode.raiseUncached(this, OSError, ErrorMessages.S, e);
            } catch (RuntimeException e) {
                throw PRaiseNode.raiseUncached(this, RuntimeError, e);
            }
        }

        private String toJavaString(Object object, String parameterName) {
            try {
                return CastToJavaStringNode.getUncached().execute(object);
            } catch (CannotCastException e) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.S_BRACKETS_ARG_S_MUST_BE_S_NOT_P, "polyglot.eval", parameterName, "str", object);
            }
        }

        private static String findLanguageByMimeType(Map<String, LanguageInfo> languages, String mimeType) {
            for (String language : languages.keySet()) {
                for (String registeredMimeType : languages.get(language).getMimeTypes()) {
                    if (mimeType.equals(registeredMimeType)) {
                        return language;
                    }
                }
            }
            return null;
        }

        private static boolean isMimeType(String lang) {
            return lang.contains("/");
        }
    }

    @Builtin(name = "export_value", minNumOfPositionalArgs = 1, parameterNames = {"name", "value"})
    @GenerateNodeFactory
    public abstract static class ExportSymbolNode extends PythonBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(ExportSymbolNode.class);

        @Specialization(guards = "!isString(value)")
        @TruffleBoundary
        Object exportSymbolKeyValue(TruffleString name, Object value) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotBindingsAccessAllowed()) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.NotImplementedError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED);
            }
            env.exportSymbol(name.toJavaStringUncached(), value);
            return value;
        }

        @Specialization(guards = "!isString(value)")
        @TruffleBoundary
        Object exportSymbolValueKey(Object value, TruffleString name) {
            LOGGER.warning("[deprecation] polyglot.export_value(value, name) is deprecated " +
                            "and will be removed. Please swap the arguments.");
            return exportSymbolKeyValue(name, value);
        }

        @Specialization(guards = "isString(arg1)")
        @TruffleBoundary
        Object exportSymbolAmbiguous(Object arg1, TruffleString arg2) {
            LOGGER.warning("[deprecation] polyglot.export_value(str, str) is ambiguous. In the future, this will " +
                            "default to using the first argument as the name and the second as value, but now it " +
                            "uses the first argument as value and the second as the name.");
            return exportSymbolValueKey(arg1, arg2);
        }

        @Specialization
        @TruffleBoundary
        Object exportSymbol(PFunction fun, @SuppressWarnings("unused") PNone name) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotBindingsAccessAllowed()) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.NotImplementedError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED);
            }
            env.exportSymbol(fun.getName().toJavaStringUncached(), fun);
            return fun;
        }

        @Specialization
        @TruffleBoundary
        Object exportSymbol(PBuiltinFunction fun, @SuppressWarnings("unused") PNone name) {
            Env env = getContext().getEnv();
            if (!env.isPolyglotBindingsAccessAllowed()) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.NotImplementedError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED);
            }
            env.exportSymbol(fun.getName().toJavaStringUncached(), fun);
            return fun;
        }

        @Specialization(guards = "isModuleMethod(fun)")
        static Object exportSymbol(VirtualFrame frame, Object fun, @SuppressWarnings("unused") PNone name,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T___NAME__)") GetAttributeNode.GetFixedAttributeNode getNameAttributeNode,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object attrNameValue = getNameAttributeNode.executeObject(frame, fun);
            String methodName;
            try {
                methodName = castToStringNode.execute(attrNameValue);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.METHOD_NAME_MUST_BE, attrNameValue);
            }
            export(inliningTarget, methodName, fun);
            return fun;
        }

        @Fallback
        static Object exportSymbol(Object value, Object name,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_ARG_TYPES_S_S_BUT_NOT_P_P, "function", "object, str", value, name);
        }

        protected static boolean isModuleMethod(Object o) {
            return (o instanceof PMethod m && m.getSelf() instanceof PythonModule) || (o instanceof PBuiltinMethod bm && bm.getSelf() instanceof PythonModule);
        }

        @TruffleBoundary
        private static void export(Node raisingNode, String name, Object obj) {
            Env env = PythonContext.get(raisingNode).getEnv();
            if (!env.isPolyglotBindingsAccessAllowed()) {
                throw PRaiseNode.raiseUncached(raisingNode, PythonErrorType.NotImplementedError, ErrorMessages.POLYGLOT_ACCESS_NOT_ALLOWED);
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

    abstract static class FitsInNumberNode extends PythonUnaryBuiltinNode {
        static boolean isSupportedNumber(Object number) {
            return number instanceof Number || number instanceof PInt;
        }

        static boolean isWhole(double number) {
            return !(number % 1.0 > 0);
        }

        @Specialization(guards = {"!isSupportedNumber(number)"})
        static boolean unsupported(PythonAbstractObject number,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ARG_MUST_BE_NUMBER, "given", number);
        }
    }

    @Builtin(name = J_FITS_IN_BYTE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInByteNode extends FitsInNumberNode {
        static boolean fits(long number) {
            return number >= 0 && number < 256;
        }

        @Specialization
        static boolean check(int number) {
            return fits(number);
        }

        @Specialization
        static boolean check(long number) {
            return fits(number);
        }

        @Specialization
        static boolean check(double number) {
            if (isWhole(number)) {
                return fits((long) number);
            }
            return false;
        }

        @Specialization
        static boolean check(PInt number,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return ilib.fitsInByte(number);
        }
    }

    @Builtin(name = J_FITS_IN_SHORT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInShortNode extends FitsInNumberNode {
        static boolean fits(long number) {
            return number >= Short.MIN_VALUE && number < Short.MAX_VALUE;
        }

        @Specialization
        static boolean check(int number) {
            return fits(number);
        }

        @Specialization
        static boolean check(long number) {
            return fits(number);
        }

        @Specialization
        static boolean check(double number) {
            if (isWhole(number)) {
                return fits((long) number);
            }
            return false;
        }

        @Specialization
        static boolean check(PInt number,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return ilib.fitsInShort(number);
        }
    }

    @Builtin(name = J_FITS_IN_INT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInIntNode extends FitsInNumberNode {
        static boolean fits(long number) {
            return number >= Integer.MIN_VALUE && number < Integer.MAX_VALUE;
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") int number) {
            return true;
        }

        @Specialization
        static boolean check(long number) {
            return fits(number);
        }

        @Specialization
        static boolean check(double number) {
            if (isWhole(number)) {
                return fits((long) number);
            }
            return false;
        }

        @Specialization
        static boolean check(PInt number,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return ilib.fitsInInt(number);
        }
    }

    @Builtin(name = J_FITS_IN_LONG, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInLongNode extends FitsInNumberNode {

        @Specialization
        static boolean check(@SuppressWarnings("unused") int number) {
            return true;
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") long number) {
            return true;
        }

        @Specialization
        static boolean check(double number) {
            return isWhole(number);
        }

        @Specialization
        static boolean check(PInt number,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return ilib.fitsInLong(number);
        }
    }

    @Builtin(name = J_FITS_IN_BIG_INTEGER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInBigIntegerNode extends FitsInNumberNode {

        @Specialization
        static boolean check(@SuppressWarnings("unused") int number) {
            return true;
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") long number) {
            return true;
        }

        @Specialization
        static boolean check(double number) {
            return isWhole(number);
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") PInt number) {
            return true;
        }
    }

    @Builtin(name = J_FITS_IN_FLOAT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInFloatNode extends FitsInNumberNode {
        static int PRECISION = 24;
        static long MIN = -(long) Math.pow(2, PRECISION);
        static long MAX = (long) Math.pow(2, PRECISION) - 1;

        static boolean fits(long number) {
            return number >= MIN && number <= MAX;
        }

        @Specialization
        static boolean check(int number) {
            return fits(number);
        }

        @Specialization
        static boolean check(long number) {
            return fits(number);
        }

        @Specialization
        static boolean check(double number) {
            return !Double.isFinite(number) || (float) number == number;
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") PInt number,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return ilib.fitsInFloat(number);
        }
    }

    @Builtin(name = J_FITS_IN_DOUBLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FitsInDoubleNode extends FitsInNumberNode {
        static int PRECISION = 53;
        static long MIN = -(long) Math.pow(2, PRECISION);
        static long MAX = (long) Math.pow(2, PRECISION) - 1;

        static boolean fits(long number) {
            return number >= MIN && number <= MAX;
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") int number) {
            return true;
        }

        @Specialization
        static boolean check(long number) {
            return fits(number);
        }

        @Specialization
        static boolean check(@SuppressWarnings("true") double number) {
            return true;
        }

        @Specialization
        static boolean check(@SuppressWarnings("unused") PInt number,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            return ilib.fitsInDouble(number);
        }
    }

    @Builtin(name = J_GET_REGISTERED_INTEROP_BEHAVIOR, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetRegisteredInteropBehaviorNode extends PythonUnaryBuiltinNode {
        @Specialization
        PList get(PythonAbstractObject klass,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode,
                        @Cached HiddenAttr.ReadNode readHiddenAttrNode) {
            if (isTypeNode.execute(inliningTarget, klass)) {
                Object value = readHiddenAttrNode.execute(inliningTarget, klass, HiddenAttr.HOST_INTEROP_BEHAVIOR, null);
                if (value instanceof InteropBehavior behavior) {
                    return factory.createList(behavior.getDefinedMethods());
                }
                return factory.createList();
            }
            throw raiseNode.raise(ValueError, S_ARG_MUST_BE_S_NOT_P, "first", "a type", klass);
        }
    }

    @Builtin(name = J_INTEROP_BEHAVIOR, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class InteropBehaviorDecoratorNode extends PythonUnaryBuiltinNode {
        static final TruffleString WRAPPER = tsLiteral("wrapper");
        public static final TruffleString KW_RECEIVER = tsLiteral("$receiver");

        static class RegisterWrapperRootNode extends PRootNode {
            static final TruffleString[] KEYWORDS_HIDDEN_RECEIVER = new TruffleString[]{KW_RECEIVER};
            private static final Signature SIGNATURE = new Signature(1, false, -1, false, tsArray("supplierClass"), KEYWORDS_HIDDEN_RECEIVER);
            private static final TruffleString MODULE_POLYGLOT = tsLiteral("polyglot");
            @Child private ExecutionContext.CalleeContext calleeContext = ExecutionContext.CalleeContext.create();
            @Child private PRaiseNode raiseNode = PRaiseNode.create();
            @Child private TypeBuiltins.DirNode dirNode = TypeBuiltins.DirNode.create();
            @Child private PyObjectGetAttr getAttr = PyObjectGetAttr.create();
            @Child private CallVarargsMethodNode callVarargsMethod = CallVarargsMethodNode.create();

            protected RegisterWrapperRootNode(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                calleeContext.enter(frame);
                Object[] frameArguments = frame.getArguments();
                Object supplierClass = PArguments.getArgument(frameArguments, 0);
                // note: the hidden kwargs are stored at the end of the positional args
                Object receiver = PArguments.getArgument(frameArguments, 1);
                try {
                    if (supplierClass instanceof PythonClass klass) {
                        // extract methods and do the registration on the receiver type
                        PSet names = dirNode.execute(frame, klass);
                        PKeyword[] kwargs = getFunctionsAsKwArgs(names.getDictStorage(), supplierClass);
                        PythonModule polyglotModule = PythonContext.get(this).lookupBuiltinModule(MODULE_POLYGLOT);
                        Object register = getAttr.executeCached(frame, polyglotModule, T_REGISTER_INTEROP_BEHAVIOR);
                        callVarargsMethod.execute(frame, register, new Object[]{receiver}, kwargs);
                        return klass;
                    }
                    throw raiseNode.raise(ValueError, S_ARG_MUST_BE_S_NOT_P, "first", "a python class", supplierClass);
                } finally {
                    calleeContext.exit(frame, this);
                }
            }

            @TruffleBoundary
            private PKeyword[] getFunctionsAsKwArgs(HashingStorage namesStorage, Object supplierClass) {
                ArrayList<PKeyword> functions = new ArrayList<>();
                HashingStorageNodes.HashingStorageIterator iterator = HashingStorageNodes.HashingStorageGetIterator.executeUncached(namesStorage);
                while (HashingStorageNodes.HashingStorageIteratorNext.executeUncached(namesStorage, iterator)) {
                    Object name = HashingStorageNodes.HashingStorageIteratorKey.executeUncached(namesStorage, iterator);
                    Object value = PyObjectGetAttrO.executeUncached(supplierClass, name);
                    if (value instanceof PFunction function) {
                        functions.add(new PKeyword(CastToTruffleStringNode.executeUncached(name), function));
                    }
                }
                return functions.toArray(PKeyword.EMPTY_KEYWORDS);
            }

            @Override
            public Signature getSignature() {
                return SIGNATURE;
            }

            @Override
            public boolean isPythonInternal() {
                return true;
            }

            @Override
            public boolean isInternal() {
                return true;
            }

            @Override
            public boolean setsUpCalleeContext() {
                return true;
            }
        }

        @Specialization
        @TruffleBoundary
        public Object decorate(PythonAbstractObject receiver,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PythonObjectFactory factory) {
            if (isTypeNode.execute(inliningTarget, receiver)) {
                RootCallTarget callTarget = getContext().getLanguage().createCachedCallTarget(RegisterWrapperRootNode::new, RegisterWrapperRootNode.class);
                return factory.createBuiltinFunction(WRAPPER, null, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(receiver), 0, callTarget);
            }
            throw raiseNode.raise(ValueError, S_ARG_MUST_BE_S_NOT_P, "first", "a type", receiver);
        }

        public static PKeyword[] createKwDefaults(Object receiver) {
            // the receiver is passed in a hidden keyword argument
            // in a pure python decorator this would be passed as a cell
            return new PKeyword[]{new PKeyword(KW_RECEIVER, receiver)};
        }
    }

    @Builtin(name = J_REGISTER_JAVA_INTEROP_TYPE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 2, takesVarKeywordArgs = true, keywordOnlyNames = {"overwrite" }, doc = """
        register_java_interop_type(javaClassName, pythonClass, overwrite=None)
        
        Example registering a custom interop type for the Java ArrayList

        >>> from polyglot import register_java_interop_type

        >>> class jArrayList(__graalpython__.ForeignType):
        ...     def append(self, element):
        ...         self.add(element)

        >>> register_java_interop_type("java.util.ArrayList", jArrayList)
        
        For subsequent registrations with overwrite behavior use
        >>> register_java_interop_type("java.util.ArrayList", newJArrayList, overwrite=True)
        """)
    @GenerateNodeFactory
    public abstract static class RegisterJavaInteropTypeNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object register(TruffleString javaClassName, PythonClass pythonClass, Object overwrite,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isClassTypeNode,
                        @Cached PRaiseNode raiseNode) {
            if (!isClassTypeNode.execute(inliningTarget, pythonClass)) {
                throw raiseNode.raise(ValueError, S_ARG_MUST_BE_S_NOT_P, "second", "a python class", pythonClass);
            }
            // Get registry for custom interop types from PythonContext
            Map<Object, PythonClass> interopTypeRegistry = PythonContext.get(this).getInteropTypeRegistry();
            String javaClassNameAsString = javaClassName.toString();
            // Check if already registered and if overwrite is configured
            if (interopTypeRegistry.containsKey(javaClassNameAsString) && !Boolean.TRUE.equals(overwrite)) {
                throw raiseNode.raise(KeyError, INTEROP_TYPE_ALREADY_REGISTERED, javaClassNameAsString);
            }
            interopTypeRegistry.put(javaClassNameAsString, pythonClass);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_REMOVE_JAVA_INTEROP_TYPE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, doc = """
        remove_java_interop_type(javaClassName)
        
        Remove registration of java interop type. Future registration don't need overwrite flag anymore.
        Example removes the custom interop type for the ArrayList
        
        >>> from polyglot import remove_java_interop_type
        
        >>> remove_java_interop_type("java.util.ArrayList")
        """)
    @GenerateNodeFactory
    public abstract static class RemoveJavaInteropTypeNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object register(TruffleString javaClassName,
                        @Cached PRaiseNode raiseNode) {
            // Get registry for custom interop types from PythonContext
            Map<Object, PythonClass> interopTypeRegistry = PythonContext.get(this).getInteropTypeRegistry();
            String javaClassNameAsString = javaClassName.toString();
            // Check if already registered and if overwrite is configured
            if (!interopTypeRegistry.containsKey(javaClassNameAsString)) {
                throw raiseNode.raise(KeyError, INTEROP_TYPE_NOT_REGISTERED, javaClassNameAsString);
            }
            interopTypeRegistry.remove(javaClassNameAsString);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_JAVA_INTEROP_TYPE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, takesVarKeywordArgs = true, keywordOnlyNames = {"overwrite"}, doc = """
       @java_interop_type(javaClassName, overwrite=None)
        
       Example registering a custom interop type for the Java ArrayList

       >>> from polyglot import register_java_interop_type

       >>> @java_interop_type("java.util.ArrayList")
       ... class jArrayList(__graalpython__.ForeignType):
       ...     def append(self, element):
       ...         self.add(element)
        
       For subsequent registrations with overwrite behavior use
       >>> @java_interop_type("java.util.ArrayList", overwrite=True)
       ... class jArrayList(__graalpython__.ForeignType):
       ...     pass
       """)
    @GenerateNodeFactory
    public abstract static class JavaInteropTypeDecoratorNode extends PythonBuiltinNode {
        static final TruffleString WRAPPER = tsLiteral("wrapper");
        public static final TruffleString KW_J_CLASS_NAME = tsLiteral("javaClassName");

        public static final TruffleString KW_OVERWRITE = tsLiteral("overwrite");

        static class RegisterWrapperRootNode extends PRootNode {
            static final TruffleString[] KEYWORDS_HIDDEN_RECEIVER = new TruffleString[]{KW_J_CLASS_NAME, KW_OVERWRITE};
            private static final Signature SIGNATURE = new Signature(1, false, -1, false, tsArray("pythonClass"), KEYWORDS_HIDDEN_RECEIVER);
            private static final TruffleString MODULE_POLYGLOT = tsLiteral("polyglot");
            @Child private ExecutionContext.CalleeContext calleeContext = ExecutionContext.CalleeContext.create();
            @Child private PRaiseNode raiseNode = PRaiseNode.create();
            @Child private PyObjectGetAttr getAttr = PyObjectGetAttr.create();
            @Child private CallVarargsMethodNode callVarargsMethod = CallVarargsMethodNode.create();

            protected RegisterWrapperRootNode(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                calleeContext.enter(frame);
                Object[] frameArguments = frame.getArguments();
                Object pythonClass = PArguments.getArgument(frameArguments, 0);
                // note: the hidden kwargs are stored at the end of the positional args
                Object javaClassName = PArguments.getArgument(frameArguments, 1);
                Object overwrite = PArguments.getArgument(frameArguments, 2);
                try {
                    if (pythonClass instanceof PythonClass klass) {
                        PythonModule polyglotModule = PythonContext.get(this).lookupBuiltinModule(MODULE_POLYGLOT);
                        Object register = getAttr.executeCached(frame, polyglotModule, T_REGISTER_JAVA_INTEROP_TYPE);
                        callVarargsMethod.execute(frame, register, new Object[]{javaClassName, pythonClass}, new PKeyword[]{new PKeyword(KW_OVERWRITE, overwrite)});
                        return klass;
                    }
                    throw raiseNode.raise(ValueError, S_ARG_MUST_BE_S_NOT_P, "first", "a python class", pythonClass);
                } finally {
                    calleeContext.exit(frame, this);
                }
            }

            @Override
            public Signature getSignature() {
                return SIGNATURE;
            }

            @Override
            public boolean isPythonInternal() {
                return true;
            }

            @Override
            public boolean isInternal() {
                return true;
            }

            @Override
            public boolean setsUpCalleeContext() {
                return true;
            }
        }

        @Specialization
        @TruffleBoundary
        public Object decorate(TruffleString receiver, Object overwrite,
                               @Cached PythonObjectFactory factory) {

            RootCallTarget callTarget = getContext().getLanguage().createCachedCallTarget(RegisterWrapperRootNode::new, RegisterWrapperRootNode.class);
            return factory.createBuiltinFunction(WRAPPER, null, PythonUtils.EMPTY_OBJECT_ARRAY, createKwDefaults(receiver, overwrite), 0, callTarget);

        }

        public static PKeyword[] createKwDefaults(Object receiver, Object overwrite) {
            // the receiver is passed in a hidden keyword argument
            // in a pure python decorator this would be passed as a cell
            return new PKeyword[]{new PKeyword(KW_J_CLASS_NAME, receiver), new PKeyword(KW_OVERWRITE, overwrite)};
        }
    }

    @Builtin(name = J_REGISTER_INTEROP_BEHAVIOR, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, takesVarKeywordArgs = true, keywordOnlyNames = {"is_boolean", "is_date",
                    "is_duration", "is_iterator", "is_number", "is_string", "is_time", "is_time_zone", "is_executable", "fits_in_big_integer", "fits_in_byte", "fits_in_double", "fits_in_float",
                    "fits_in_int", "fits_in_long", "fits_in_short", "as_big_integer", "as_boolean", "as_byte", "as_date", "as_double", "as_duration", "as_float", "as_int", "as_long", "as_short",
                    "as_string", "as_time", "as_time_zone", "execute", "read_array_element", "get_array_size", "has_array_elements", "is_array_element_readable", "is_array_element_modifiable",
                    "is_array_element_insertable", "is_array_element_removable", "remove_array_element", "write_array_element", "has_iterator", "has_iterator_next_element", "get_iterator",
                    "get_iterator_next_element", "has_hash_entries", "get_hash_entries_iterator", "get_hash_keys_iterator", "get_hash_size", "get_hash_values_iterator", "is_hash_entry_readable",
                    "is_hash_entry_modifiable", "is_hash_entry_insertable", "is_hash_entry_removable", "read_hash_value", "write_hash_entry",
                    "remove_hash_entry"}, doc = """
                                    register_interop_behavior(type, is_boolean=None, is_date=None, is_duration=None, is_iterator=None, is_number=None, is_string=None, is_time=None,
                                    is_time_zone=None, is_executable=None, fits_in_big_integer=None, fits_in_byte=None, fits_in_double=None, fits_in_float=None, fits_in_int=None,
                                    fits_in_long=None, fits_in_short=None, as_big_integer=None, as_boolean=None, as_byte=None, as_date=None, as_double=None, as_duration=None, as_float=None,
                                    as_int=None, as_long=None, as_short=None, as_string=None, as_time=None, as_time_zone=None, execute=None, read_array_element=None, get_array_size=None,
                                    has_array_elements=None, is_array_element_readable=None, is_array_element_modifiable=None, is_array_element_insertable=None, is_array_element_removable=None,
                                    remove_array_element=None, write_array_element=None, has_iterator=None, has_iterator_next_element=None, get_iterator=None, get_iterator_next_element=None,
                                    has_hash_entries=None, get_hash_entries_iterator=None, get_hash_keys_iterator=None, get_hash_size=None, get_hash_values_iterator=None, is_hash_entry_readable=None,
                                    is_hash_entry_modifiable=None, is_hash_entry_insertable=None, is_hash_entry_removable=None, read_hash_value=None, write_hash_entry=None, remove_hash_entry=None)

                                    Registers the specified interop behavior with the passed type. The extensions are directly mapped to the Truffle (host) Interop 2.0 protocol.
                                    Most Truffle InteropLibrary messages (http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html) are supported (see keyword parameter list).

                                    Example extending the interop behavior for iterators:

                                    >>> from polyglot import register_interop_behavior

                                    >>> class MyType(object):
                                    ...     data = [0,1,2]

                                    >>> register_interop_behavior(MyType, is_iterator=False, has_iterator=True, get_iterator=lambda t: iter(t.data))
                                    """)
    @GenerateNodeFactory
    public abstract static class RegisterInteropBehaviorNode extends PythonBuiltinNode {

        void handleArg(Object value, InteropBehaviorMethod method, InteropBehavior interopBehavior, PRaiseNode raiseNode) {
            if (value instanceof Boolean boolValue) {
                interopBehavior.defineBehavior(method, boolValue);
            } else if (value instanceof PFunction function) {
                interopBehavior.defineBehavior(method, function);
                Signature signature = function.getCode().getSignature();
                // validate the function
                if (function.getKwDefaults().length != 0) {
                    throw raiseNode.raise(ValueError, S_TAKES_NO_KEYWORD_ARGS, method.name);
                } else if (function.getCode().getCellVars().length != 0) {
                    throw raiseNode.raise(ValueError, S_CANNOT_HAVE_S, method.name, "cell vars");
                } else if (function.getCode().getFreeVars().length != 0) {
                    throw raiseNode.raise(ValueError, S_CANNOT_HAVE_S, method.name, "free vars");
                } else {
                    // check signature
                    if (method.takesVarArgs != signature.takesVarArgs()) {
                        throw raiseNode.raise(ValueError, method.takesVarArgs ? S_TAKES_VARARGS : S_DOES_NOT_TAKE_VARARGS, method.name);
                    } else if (signature.getMaxNumOfPositionalArgs() != method.getNumPositionalArguments()) {
                        throw raiseNode.raise(ValueError, S_TAKES_EXACTLY_D_ARGS, method.name, method.getNumPositionalArguments(), signature.getMaxNumOfPositionalArgs());
                    }
                }
            }
        }

        @Specialization
        @TruffleBoundary
        Object register(PythonAbstractObject receiver, Object is_boolean, Object is_date, Object is_duration, Object is_iterator, Object is_number, Object is_string, Object is_time,
                        Object is_time_zone, Object is_executable, Object fits_in_big_integer, Object fits_in_byte, Object fits_in_double, Object fits_in_float, Object fits_in_int,
                        Object fits_in_long, Object fits_in_short, Object as_big_integer, Object as_boolean, Object as_byte, Object as_date, Object as_double, Object as_duration, Object as_float,
                        Object as_int, Object as_long, Object as_short, Object as_string, Object as_time, Object as_time_zone, Object execute, Object read_array_element, Object get_array_size,
                        Object has_array_elements, Object is_array_element_readable, Object is_array_element_modifiable, Object is_array_element_insertable, Object is_array_element_removable,
                        Object remove_array_element, Object write_array_element, Object has_iterator, Object has_iterator_next_element, Object get_iterator, Object get_iterator_next_element,
                        Object has_hash_entries, Object get_hash_entries_iterator, Object get_hash_keys_iterator, Object get_hash_size, Object get_hash_values_iterator, Object is_hash_entry_readable,
                        Object is_hash_entry_modifiable, Object is_hash_entry_insertable, Object is_hash_entry_removable, Object read_hash_value, Object write_hash_entry, Object remove_hash_entry,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached PRaiseNode raiseNode) {
            if (isTypeNode.execute(inliningTarget, receiver)) {
                final InteropBehavior interopBehavior = new InteropBehavior(receiver);

                handleArg(is_boolean, InteropBehaviorMethod.is_boolean, interopBehavior, raiseNode);
                handleArg(is_date, InteropBehaviorMethod.is_date, interopBehavior, raiseNode);
                handleArg(is_duration, InteropBehaviorMethod.is_duration, interopBehavior, raiseNode);
                handleArg(is_iterator, InteropBehaviorMethod.is_iterator, interopBehavior, raiseNode);
                handleArg(is_number, InteropBehaviorMethod.is_number, interopBehavior, raiseNode);
                handleArg(is_string, InteropBehaviorMethod.is_string, interopBehavior, raiseNode);
                handleArg(is_time, InteropBehaviorMethod.is_time, interopBehavior, raiseNode);
                handleArg(is_time_zone, InteropBehaviorMethod.is_time_zone, interopBehavior, raiseNode);
                handleArg(is_executable, InteropBehaviorMethod.is_executable, interopBehavior, raiseNode);
                handleArg(fits_in_big_integer, InteropBehaviorMethod.fits_in_big_integer, interopBehavior, raiseNode);
                handleArg(fits_in_byte, InteropBehaviorMethod.fits_in_byte, interopBehavior, raiseNode);
                handleArg(fits_in_double, InteropBehaviorMethod.fits_in_double, interopBehavior, raiseNode);
                handleArg(fits_in_float, InteropBehaviorMethod.fits_in_float, interopBehavior, raiseNode);
                handleArg(fits_in_int, InteropBehaviorMethod.fits_in_int, interopBehavior, raiseNode);
                handleArg(fits_in_long, InteropBehaviorMethod.fits_in_long, interopBehavior, raiseNode);
                handleArg(fits_in_short, InteropBehaviorMethod.fits_in_short, interopBehavior, raiseNode);
                handleArg(as_big_integer, InteropBehaviorMethod.as_big_integer, interopBehavior, raiseNode);
                handleArg(as_boolean, InteropBehaviorMethod.as_boolean, interopBehavior, raiseNode);
                handleArg(as_byte, InteropBehaviorMethod.as_byte, interopBehavior, raiseNode);
                handleArg(as_date, InteropBehaviorMethod.as_date, interopBehavior, raiseNode);
                handleArg(as_double, InteropBehaviorMethod.as_double, interopBehavior, raiseNode);
                handleArg(as_duration, InteropBehaviorMethod.as_duration, interopBehavior, raiseNode);
                handleArg(as_float, InteropBehaviorMethod.as_float, interopBehavior, raiseNode);
                handleArg(as_int, InteropBehaviorMethod.as_int, interopBehavior, raiseNode);
                handleArg(as_long, InteropBehaviorMethod.as_long, interopBehavior, raiseNode);
                handleArg(as_short, InteropBehaviorMethod.as_short, interopBehavior, raiseNode);
                handleArg(as_string, InteropBehaviorMethod.as_string, interopBehavior, raiseNode);
                handleArg(as_time, InteropBehaviorMethod.as_time, interopBehavior, raiseNode);
                handleArg(as_time_zone, InteropBehaviorMethod.as_time_zone, interopBehavior, raiseNode);
                handleArg(execute, InteropBehaviorMethod.execute, interopBehavior, raiseNode);
                handleArg(read_array_element, InteropBehaviorMethod.read_array_element, interopBehavior, raiseNode);
                handleArg(get_array_size, InteropBehaviorMethod.get_array_size, interopBehavior, raiseNode);
                handleArg(has_array_elements, InteropBehaviorMethod.has_array_elements, interopBehavior, raiseNode);
                handleArg(is_array_element_readable, InteropBehaviorMethod.is_array_element_readable, interopBehavior, raiseNode);
                handleArg(is_array_element_modifiable, InteropBehaviorMethod.is_array_element_modifiable, interopBehavior, raiseNode);
                handleArg(is_array_element_insertable, InteropBehaviorMethod.is_array_element_insertable, interopBehavior, raiseNode);
                handleArg(is_array_element_removable, InteropBehaviorMethod.is_array_element_removable, interopBehavior, raiseNode);
                handleArg(remove_array_element, InteropBehaviorMethod.remove_array_element, interopBehavior, raiseNode);
                handleArg(write_array_element, InteropBehaviorMethod.write_array_element, interopBehavior, raiseNode);
                handleArg(has_iterator, InteropBehaviorMethod.has_iterator, interopBehavior, raiseNode);
                handleArg(has_iterator_next_element, InteropBehaviorMethod.has_iterator_next_element, interopBehavior, raiseNode);
                handleArg(get_iterator, InteropBehaviorMethod.get_iterator, interopBehavior, raiseNode);
                handleArg(get_iterator_next_element, InteropBehaviorMethod.get_iterator_next_element, interopBehavior, raiseNode);
                handleArg(has_hash_entries, InteropBehaviorMethod.has_hash_entries, interopBehavior, raiseNode);
                handleArg(get_hash_entries_iterator, InteropBehaviorMethod.get_hash_entries_iterator, interopBehavior, raiseNode);
                handleArg(get_hash_keys_iterator, InteropBehaviorMethod.get_hash_keys_iterator, interopBehavior, raiseNode);
                handleArg(get_hash_size, InteropBehaviorMethod.get_hash_size, interopBehavior, raiseNode);
                handleArg(get_hash_values_iterator, InteropBehaviorMethod.get_hash_values_iterator, interopBehavior, raiseNode);
                handleArg(is_hash_entry_readable, InteropBehaviorMethod.is_hash_entry_readable, interopBehavior, raiseNode);
                handleArg(is_hash_entry_modifiable, InteropBehaviorMethod.is_hash_entry_modifiable, interopBehavior, raiseNode);
                handleArg(is_hash_entry_insertable, InteropBehaviorMethod.is_hash_entry_insertable, interopBehavior, raiseNode);
                handleArg(is_hash_entry_removable, InteropBehaviorMethod.is_hash_entry_removable, interopBehavior, raiseNode);
                handleArg(read_hash_value, InteropBehaviorMethod.read_hash_value, interopBehavior, raiseNode);
                handleArg(write_hash_entry, InteropBehaviorMethod.write_hash_entry, interopBehavior, raiseNode);
                handleArg(remove_hash_entry, InteropBehaviorMethod.remove_hash_entry, interopBehavior, raiseNode);

                HiddenAttr.WriteNode.executeUncached(receiver, HiddenAttr.HOST_INTEROP_BEHAVIOR, interopBehavior);
                return PNone.NONE;
            }
            throw raiseNode.raise(ValueError, S_ARG_MUST_BE_S_NOT_P, "first", "a type", receiver);
        }
    }

    @Builtin(name = "__read__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReadNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object read(Object receiver, Object key) {
            try {
                if (key instanceof TruffleString) {
                    return getInterop().readMember(receiver, ((TruffleString) key).toJavaStringUncached());
                } else if (isJavaString(key)) {
                    return getInterop().readMember(receiver, (String) key);
                } else if (key instanceof Number) {
                    return getInterop().readArrayElement(receiver, ((Number) key).longValue());
                } else {
                    throw PRaiseNode.raiseUncached(this, PythonErrorType.AttributeError, ErrorMessages.UNKNOWN_ATTR, key);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__write__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object write(Object receiver, Object key, Object value) {
            try {
                if (key instanceof TruffleString) {
                    getInterop().writeMember(receiver, ((TruffleString) key).toJavaStringUncached(), value);
                } else if (isJavaString(key)) {
                    getInterop().writeMember(receiver, (String) key, value);
                } else if (key instanceof Number) {
                    getInterop().writeArrayElement(receiver, ((Number) key).longValue(), value);
                } else {
                    throw PRaiseNode.raiseUncached(this, PythonErrorType.AttributeError, ErrorMessages.UNKNOWN_ATTR, key);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.AttributeError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "__remove__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class removeNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object remove(Object receiver, Object key) {
            try {
                if (key instanceof TruffleString) {
                    getInterop().removeMember(receiver, ((TruffleString) key).toJavaStringUncached());
                } else if (isJavaString(key)) {
                    getInterop().removeMember(receiver, (String) key);
                } else if (key instanceof Number) {
                    getInterop().removeArrayElement(receiver, ((Number) key).longValue());
                } else {
                    throw PRaiseNode.raiseUncached(this, PythonErrorType.AttributeError, ErrorMessages.UNKNOWN_ATTR, key);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | InvalidArrayIndexException e) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.AttributeError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "__execute__", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class executeNode extends PythonBuiltinNode {
        @Specialization
        static Object exec(Object receiver, Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return getInterop().execute(receiver, arguments);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__new__", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class newNode extends PythonBuiltinNode {
        @Specialization
        static Object instantiate(Object receiver, Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return getInterop().instantiate(receiver, arguments);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__invoke__", minNumOfPositionalArgs = 2, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class invokeNode extends PythonBuiltinNode {
        @Specialization
        static Object invoke(Object receiver, TruffleString key, Object[] arguments,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return getInterop().invokeMember(receiver, toJavaStringNode.execute(key), arguments);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, e);
            }
        }
    }

    @Builtin(name = "__is_null__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsNullNode extends PythonBuiltinNode {
        @Specialization
        static boolean isNull(Object receiver) {
            return getInterop().isNull(receiver);
        }
    }

    @Builtin(name = "__has_size__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HasSizeNode extends PythonBuiltinNode {
        @Specialization
        static boolean hasSize(Object receiver) {
            return getInterop().hasArrayElements(receiver);
        }
    }

    @Builtin(name = "__get_size__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetSizeNode extends PythonBuiltinNode {
        @Specialization
        static Object getSize(Object receiver,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return getInterop().getArraySize(receiver);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, e);
            }
        }
    }

    @Builtin(name = "__is_boxed__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsBoxedNode extends PythonBuiltinNode {
        @Specialization
        static boolean isBoxed(Object receiver) {
            return getInterop().isString(receiver) || getInterop().fitsInDouble(receiver) || getInterop().fitsInLong(receiver);
        }
    }

    @Builtin(name = "__has_keys__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HasKeysNode extends PythonBuiltinNode {
        @Specialization
        static boolean hasKeys(Object receiver) {
            return getInterop().hasMembers(receiver);
        }
    }

    @Builtin(name = "__key_info__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class KeyInfoNode extends PythonBuiltinNode {
        @Specialization
        static boolean keyInfo(Object receiver, TruffleString tmember, TruffleString info,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.EqualNode equalNode) {
            String member = toJavaStringNode.execute(tmember);
            if (equalNode.execute(info, T_READ_SIDE_EFFECTS, TS_ENCODING)) {
                return getInterop().hasMemberReadSideEffects(receiver, member);
            } else if (equalNode.execute(info, T_WRITE_SIDE_EFFECTS, TS_ENCODING)) {
                return getInterop().hasMemberWriteSideEffects(receiver, member);
            } else if (equalNode.execute(info, T_EXISTS, TS_ENCODING)) {
                return getInterop().isMemberExisting(receiver, member);
            } else if (equalNode.execute(info, T_READABLE, TS_ENCODING)) {
                return getInterop().isMemberReadable(receiver, member);
            } else if (equalNode.execute(info, T_WRITABLE, TS_ENCODING)) {
                return getInterop().isMemberWritable(receiver, member);
            } else if (equalNode.execute(info, T_INSERTABLE, TS_ENCODING)) {
                return getInterop().isMemberInsertable(receiver, member);
            } else if (equalNode.execute(info, T_REMOVABLE, TS_ENCODING)) {
                return getInterop().isMemberRemovable(receiver, member);
            } else if (equalNode.execute(info, T_MODIFIABLE, TS_ENCODING)) {
                return getInterop().isMemberModifiable(receiver, member);
            } else if (equalNode.execute(info, T_INVOKABLE, TS_ENCODING)) {
                return getInterop().isMemberInvocable(receiver, member);
            } else if (equalNode.execute(info, T_INTERNAL, TS_ENCODING)) {
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
        static Object remove(Object receiver,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                return getInterop().getMembers(receiver);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, e);
            }
        }
    }

    @Builtin(name = "__element_info__", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ArrayElementInfoNode extends PythonBuiltinNode {
        @Specialization
        static boolean keyInfo(Object receiver, long member, TruffleString info,
                        @Cached TruffleString.EqualNode equalNode) {
            if (equalNode.execute(info, T_EXISTS, TS_ENCODING)) {
                return getInterop().isArrayElementExisting(receiver, member);
            } else if (equalNode.execute(info, T_READABLE, TS_ENCODING)) {
                return getInterop().isArrayElementReadable(receiver, member);
            } else if (equalNode.execute(info, T_WRITABLE, TS_ENCODING)) {
                return getInterop().isArrayElementWritable(receiver, member);
            } else if (equalNode.execute(info, T_INSERTABLE, TS_ENCODING)) {
                return getInterop().isArrayElementInsertable(receiver, member);
            } else if (equalNode.execute(info, T_REMOVABLE, TS_ENCODING)) {
                return getInterop().isArrayElementRemovable(receiver, member);
            } else if (equalNode.execute(info, T_MODIFIABLE, TS_ENCODING)) {
                return getInterop().isArrayElementModifiable(receiver, member);
            } else {
                return false;
            }
        }
    }

    @Builtin(name = "storage", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StorageNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object doSequence(PSequence seq,
                        @Bind("this") Node inliningTarget) {
            SequenceStorage storage = seq.getSequenceStorage();
            Object arrayObject;
            if (storage instanceof EmptySequenceStorage) {
                arrayObject = seq instanceof PBytesLike ? EMPTY_BYTE_ARRAY : EMPTY_OBJECT_ARRAY;
            } else if (storage instanceof ArrayBasedSequenceStorage basicStorage) {
                arrayObject = basicStorage.getInternalArrayObject();
            } else {
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.NotImplementedError, ErrorMessages.GETTING_POLYGLOT_STORAGE_FOR_NATIVE_STORAGE_NOT_IMPLEMENTED);
            }
            return PythonContext.get(inliningTarget).getEnv().asGuestValue(arrayObject);
        }

        @Fallback
        static Object doError(Object object,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_P, object);
        }
    }

}
