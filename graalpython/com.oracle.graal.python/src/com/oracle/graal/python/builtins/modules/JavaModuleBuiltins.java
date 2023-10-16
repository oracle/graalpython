/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.StringLiterals.J_JAVA;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.interop.InteropByteArray;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J_JAVA)
public final class JavaModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T_JAR = tsLiteral(".jar");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JavaModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("__path__", "java!");
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule javaModule = core.lookupBuiltinModule(T_JAVA);
        javaModule.setAttribute(T___GETATTR__, javaModule.getAttribute(GetAttrNode.T_JAVA_GETATTR));
    }

    @Builtin(name = "type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TypeNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isPString(name) || isTruffleString(name)")
        static Object type(Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaStringNode castToStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Env env = PythonContext.get(inliningTarget).getEnv();
            if (!env.isHostLookupAllowed()) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.NotImplementedError, ErrorMessages.HOST_LOOKUP_NOT_ALLOWED);
            }
            String javaString = castToStringNode.execute(name);
            Object hostValue;
            try {
                hostValue = env.lookupHostSymbol(javaString);
            } catch (RuntimeException e) {
                hostValue = null;
            }
            if (hostValue == null) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.KeyError, ErrorMessages.HOST_SYM_NOT_DEFINED, javaString);
            } else {
                return hostValue;
            }
        }

        @Fallback
        static Object doError(Object object,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_P, object);
        }
    }

    @Builtin(name = "add_to_classpath", takesVarArgs = true, doc = "Add all arguments to the classpath.")
    @GenerateNodeFactory
    abstract static class AddToClassPathNode extends PythonBuiltinNode {
        @Specialization
        PNone add(Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToString) {
            Env env = getContext().getEnv();
            if (!env.isHostLookupAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, ErrorMessages.HOST_ACCESS_NOT_ALLOWED);
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                TruffleString entry = null;
                try {
                    entry = castToString.execute(inliningTarget, arg);
                    // Always allow accessing JAR files in the language home; folders are allowed
                    // implicitly
                    env.addToHostClassPath(getContext().getPublicTruffleFileRelaxed(entry, T_JAR));
                } catch (CannotCastException e) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CLASSPATH_ARG_MUST_BE_STRING, i + 1, arg);
                } catch (SecurityException e) {
                    throw raise(TypeError, ErrorMessages.INVALD_OR_UNREADABLE_CLASSPATH, entry, e);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "is_function", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsFunctionNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean check(Object object) {
            Env env = getContext().getEnv();
            return env.isHostFunction(object);
        }
    }

    @Builtin(name = "is_object", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsObjectNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean check(Object object) {
            Env env = getContext().getEnv();
            return env.isHostObject(object);
        }
    }

    @Builtin(name = "is_symbol", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSymbolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean check(Object object) {
            Env env = getContext().getEnv();
            return env.isHostSymbol(object);
        }
    }

    @Builtin(name = "is_type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsTypeNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isType(Object object) {
            Env env = getContext().getEnv();
            return env.isHostObject(object) && env.asHostObject(object) instanceof Class<?>;
        }
    }

    @Builtin(name = "instanceof", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstanceOfNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"!isForeign1.execute(inliningTarget, object)", "isForeign2.execute(inliningTarget, klass)"})
        static boolean check(Object object, Object klass,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isForeign1") @Cached IsForeignObjectNode isForeign1,
                        @SuppressWarnings("unused") @Shared("isForeign2") @Cached IsForeignObjectNode isForeign2,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Env env = PythonContext.get(inliningTarget).getEnv();
            try {
                Object hostKlass = env.asHostObject(klass);
                if (hostKlass instanceof Class<?>) {
                    return ((Class<?>) hostKlass).isInstance(object);
                }
            } catch (ClassCastException cce) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.KLASS_ARG_IS_NOT_HOST_OBJ, klass);
            }
            return false;
        }

        @Specialization(guards = {"isForeign1.execute(inliningTarget, object)", "isForeign2.execute(inliningTarget, klass)"})
        static boolean checkForeign(Object object, Object klass,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isForeign1") @Cached IsForeignObjectNode isForeign1,
                        @SuppressWarnings("unused") @Shared("isForeign2") @Cached IsForeignObjectNode isForeign2,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Env env = PythonContext.get(inliningTarget).getEnv();
            try {
                Object hostObject = env.asHostObject(object);
                Object hostKlass = env.asHostObject(klass);
                if (hostKlass instanceof Class<?>) {
                    return ((Class<?>) hostKlass).isInstance(hostObject);
                }
            } catch (ClassCastException cce) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.OBJ_OR_KLASS_ARGS_IS_NOT_HOST_OBJ, object, klass);
            }
            return false;
        }

        @Fallback
        static boolean fallback(Object object, Object klass,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.UNSUPPORTED_INSTANCEOF, object, klass);
        }
    }

    @Builtin(name = GetAttrNode.J_JAVA_GETATTR, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class GetAttrNode extends PythonBuiltinNode {

        protected static final String J_JAVA_GETATTR = "java_getattr";
        protected static final TruffleString T_JAVA_GETATTR = tsLiteral(J_JAVA_GETATTR);
        private static final TruffleString T_JAVA_PKG_LOADER = tsLiteral("JavaPackageLoader");
        private static final TruffleString T_MAKE_GETATTR = tsLiteral("_make_getattr");

        @CompilationFinal protected Object getAttr;

        private Object getAttr(VirtualFrame frame, PythonModule mod) {
            if (getAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // Note: passing VirtualFrame to TruffleBoundary methods (uncached execute) is fine,
                // because this branch will never be compiled
                Object javaLoader = PyObjectGetAttr.getUncached().execute(frame, null, mod, T_JAVA_PKG_LOADER);
                getAttr = PyObjectCallMethodObjArgs.executeUncached(frame, javaLoader, T_MAKE_GETATTR, T_JAVA);
            }
            return getAttr;
        }

        @Specialization
        Object none(VirtualFrame frame, PythonModule mod, Object name,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, getAttr(frame, mod), name);
        }
    }

    @Builtin(name = "as_java_byte_array", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AsJavaByteArrayNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doBytesByteStorage(PBytesLike object) {
            return new PUnsignedBytesWrapper(object);
        }

        @Specialization(guards = "!isBytes(object)", limit = "3")
        Object doBuffer(VirtualFrame frame, Object object,
                        @CachedLibrary("object") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            Object buffer = acquireLib.acquireReadonly(object, frame, this);
            try {
                return new InteropByteArray(bufferLib.getCopiedByteArray(object));
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }
    }

    /**
     * A simple wrapper object that bit-casts an integer in range {@code 0-255} to a Java
     * {@code byte}. This can be used to expose a bytes-like object to Java as {@code byte[]}.
     */
    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    @SuppressWarnings("static-method")
    static final class PUnsignedBytesWrapper implements TruffleObject {
        final PBytesLike delegate;

        PUnsignedBytesWrapper(PBytesLike delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        boolean hasArrayElements(
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib) {
            return delegateLib.hasArrayElements(delegate);
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib) {
            return delegateLib.isArrayElementReadable(delegate, index);
        }

        @ExportMessage
        long getArraySize(
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib) throws UnsupportedMessageException {
            return delegateLib.getArraySize(delegate);
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this.delegate") InteropLibrary delegateLib,
                        @CachedLibrary(limit = "1") InteropLibrary elementLib,
                        @Cached GilNode gil) throws InvalidArrayIndexException, UnsupportedMessageException {
            boolean mustRelease = gil.acquire();
            try {
                Object element = delegateLib.readArrayElement(delegate, index);
                if (elementLib.fitsInLong(element)) {
                    long i = elementLib.asLong(element);
                    if (compareUnsigned(i, Byte.MAX_VALUE) <= 0) {
                        return (byte) i;
                    } else if (compareUnsigned(i, 0xFF) <= 0) {
                        return (byte) -(-i & 0xFF);
                    }
                }
                throw CompilerDirectives.shouldNotReachHere("bytes object contains non-byte values");
            } finally {
                gil.release(mustRelease);
            }
        }

        /**
         * This is taken from {@link Long#compare(long, long)}} (just to avoid a
         * {@code TruffleBoundary}).
         */
        private static int compare(long x, long y) {
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }

        /**
         * This is taken from {@link Long#compareUnsigned(long, long)}} (just to avoid a
         * {@code TruffleBoundary}).
         */
        private static int compareUnsigned(long x, long y) {
            return compare(x + Long.MIN_VALUE, y + Long.MIN_VALUE);
        }
    }
}
