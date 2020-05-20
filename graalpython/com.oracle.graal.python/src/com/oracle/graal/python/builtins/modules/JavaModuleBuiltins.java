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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "java")
public class JavaModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JavaModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("__path__", "java!");
    }

    @Builtin(name = "type", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TypeNode extends PythonUnaryBuiltinNode {
        private Object get(String name) {
            Env env = getContext().getEnv();
            if (!env.isHostLookupAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "host lookup is not allowed");
            }
            Object hostValue;
            try {
                hostValue = env.lookupHostSymbol(name);
            } catch (RuntimeException e) {
                hostValue = null;
            }
            if (hostValue == null) {
                throw raise(PythonErrorType.KeyError, "host symbol %s is not defined or access has been denied", name);
            } else {
                return hostValue;
            }
        }

        @Specialization
        Object type(String name) {
            return get(name);
        }

        @Specialization
        Object type(PString name) {
            return get(name.getValue());
        }

        @Fallback
        Object doError(Object object) {
            throw raise(PythonBuiltinClassType.TypeError, "unsupported operand '%p'", object);
        }
    }

    @Builtin(name = "add_to_classpath", takesVarArgs = true, doc = "Add all arguments to the classpath.")
    @GenerateNodeFactory
    abstract static class AddToClassPathNode extends PythonBuiltinNode {
        @Specialization
        PNone add(Object[] args,
                        @Cached CastToJavaStringNode castToString) {
            Env env = getContext().getEnv();
            if (!env.isHostLookupAllowed()) {
                throw raise(PythonErrorType.NotImplementedError, "host access is not allowed");
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                String entry = null;
                try {
                    entry = castToString.execute(arg);
                    // Always allow accessing JAR files in the language home; folders are allowed
                    // implicitly
                    env.addToHostClassPath(getContext().getPublicTruffleFileRelaxed(entry, ".jar"));
                } catch (CannotCastException e) {
                    throw raise(PythonBuiltinClassType.TypeError, "classpath argument %d must be string, not %p", i + 1, arg);
                } catch (SecurityException e) {
                    throw raise(TypeError, "invalid or unreadable classpath: '%s' - %m", entry, e);
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

    @Builtin(name = "instanceof", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstanceOfNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"!iLibObject.isForeignObject(object)", "iLibKlass.isForeignObject(klass)"}, limit = "3")
        boolean check(Object object, TruffleObject klass,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary iLibObject,
                        @SuppressWarnings("unused") @CachedLibrary("klass") PythonObjectLibrary iLibKlass) {
            Env env = getContext().getEnv();
            try {
                Object hostKlass = env.asHostObject(klass);
                if (hostKlass instanceof Class<?>) {
                    return ((Class<?>) hostKlass).isInstance(object);
                }
            } catch (ClassCastException cce) {
                throw raise(ValueError, "klass argument '%p' is not a host object", klass);
            }
            return false;
        }

        @Specialization(guards = {"iLibObject.isForeignObject(object)", "iLibKlass.isForeignObject(klass)"}, limit = "3")
        boolean checkForeign(Object object, TruffleObject klass,
                        @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary iLibObject,
                        @SuppressWarnings("unused") @CachedLibrary("klass") PythonObjectLibrary iLibKlass) {
            Env env = getContext().getEnv();
            try {
                Object hostObject = env.asHostObject(object);
                Object hostKlass = env.asHostObject(klass);
                if (hostKlass instanceof Class<?>) {
                    return ((Class<?>) hostKlass).isInstance(hostObject);
                }
            } catch (ClassCastException cce) {
                throw raise(ValueError, "the object '%p' or klass '%p' arguments is not a host object", object, klass);
            }
            return false;
        }

        @Fallback
        boolean fallback(Object object, Object klass) {
            throw raise(TypeError, "unsupported instanceof(%p, %p)", object, klass);
        }
    }
}
