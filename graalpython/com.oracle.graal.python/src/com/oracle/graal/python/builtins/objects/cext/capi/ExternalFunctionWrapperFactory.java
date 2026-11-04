/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.function.Supplier;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.WrapperBase;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/** Creates configured native wrapper bodies that read their own frame arguments. */
final class ExternalFunctionWrapperFactory implements NodeFactory<WrapperBase> {
    private final Class<? extends WrapperBase> nodeClass;
    private final Signature signature;
    private final Builtin builtin;
    private final Supplier<? extends WrapperBase> constructor;

    ExternalFunctionWrapperFactory(Class<? extends WrapperBase> nodeClass, Signature signature, TruffleString name,
                    Supplier<? extends WrapperBase> constructor) {
        this.nodeClass = nodeClass;
        this.signature = signature;
        this.builtin = new WrapperBuiltin(name.toJavaStringUncached());
        this.constructor = constructor;
    }

    BuiltinFunctionRootNode createRoot(PythonLanguage language) {
        return new BuiltinFunctionRootNode(language, signature, builtin, this, true, null);
    }

    @Override
    public WrapperBase createNode(Object... arguments) {
        assert arguments.length == 0;
        WrapperBase body = constructor.get();
        assert nodeClass.isInstance(body);
        return body;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<WrapperBase> getNodeClass() {
        return (Class<WrapperBase>) nodeClass;
    }

    @Override
    public List<List<Class<?>>> getNodeSignatures() {
        return List.of(List.of());
    }

    @Override
    public List<Class<? extends Node>> getExecutionSignature() {
        return List.of();
    }

    /** Builtin metadata for native wrappers, whose argument layout is defined by the signature. */
    private record WrapperBuiltin(String name) implements Builtin {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Builtin.class;
        }

        @Override
        public String doc() {
            return "";
        }

        @Override
        public PythonOS os() {
            return PythonOS.PLATFORM_ANY;
        }

        @Override
        public int minNumOfPositionalArgs() {
            return 0;
        }

        @Override
        public int maxNumOfPositionalArgs() {
            return -1;
        }

        @Override
        public int numOfPositionalOnlyArgs() {
            return -1;
        }

        @Override
        public boolean isGetter() {
            return false;
        }

        @Override
        public boolean isSetter() {
            return false;
        }

        @Override
        public boolean allowsDelete() {
            return false;
        }

        @Override
        public boolean takesVarArgs() {
            return false;
        }

        @Override
        public boolean takesVarKeywordArgs() {
            return false;
        }

        @Override
        public String[] parameterNames() {
            return new String[0];
        }

        @Override
        public String[] keywordOnlyNames() {
            return new String[0];
        }

        @Override
        public boolean isClassmethod() {
            return false;
        }

        @Override
        public boolean isStaticmethod() {
            return false;
        }

        @Override
        public boolean needsFrame() {
            return true;
        }

        @Override
        public int callerFlags() {
            return 0;
        }

        @Override
        public boolean declaresExplicitSelf() {
            return true;
        }

        @Override
        public String raiseErrorName() {
            return "";
        }

        @Override
        public boolean forceSplitDirectCalls() {
            return false;
        }

        @Override
        public boolean autoRegister() {
            return false;
        }
    }
}
