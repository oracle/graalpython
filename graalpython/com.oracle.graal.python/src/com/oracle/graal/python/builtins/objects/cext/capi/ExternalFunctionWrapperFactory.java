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
        this.builtin = new NamedBuiltin(name.toJavaStringUncached(), WrapperBase.class.getAnnotation(Builtin.class));
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

    /** Give the common wrapper metadata the enclosing root's runtime name. */
    private record NamedBuiltin(String name, Builtin annotation) implements Builtin {
        NamedBuiltin {
            assert annotation != null;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Builtin.class;
        }

        @Override
        public String doc() {
            return annotation.doc();
        }

        @Override
        public PythonOS os() {
            return annotation.os();
        }

        @Override
        public int minNumOfPositionalArgs() {
            return annotation.minNumOfPositionalArgs();
        }

        @Override
        public int maxNumOfPositionalArgs() {
            return annotation.maxNumOfPositionalArgs();
        }

        @Override
        public int numOfPositionalOnlyArgs() {
            return annotation.numOfPositionalOnlyArgs();
        }

        @Override
        public boolean isGetter() {
            return annotation.isGetter();
        }

        @Override
        public boolean isSetter() {
            return annotation.isSetter();
        }

        @Override
        public boolean allowsDelete() {
            return annotation.allowsDelete();
        }

        @Override
        public boolean takesVarArgs() {
            return annotation.takesVarArgs();
        }

        @Override
        public boolean takesVarKeywordArgs() {
            return annotation.takesVarKeywordArgs();
        }

        @Override
        public String[] parameterNames() {
            return annotation.parameterNames();
        }

        @Override
        public String[] keywordOnlyNames() {
            return annotation.keywordOnlyNames();
        }

        @Override
        public boolean isClassmethod() {
            return annotation.isClassmethod();
        }

        @Override
        public boolean isStaticmethod() {
            return annotation.isStaticmethod();
        }

        @Override
        public boolean needsFrame() {
            return annotation.needsFrame();
        }

        @Override
        public int callerFlags() {
            return annotation.callerFlags();
        }

        @Override
        public boolean declaresExplicitSelf() {
            return annotation.declaresExplicitSelf();
        }

        @Override
        public String raiseErrorName() {
            return annotation.raiseErrorName();
        }

        @Override
        public boolean forceSplitDirectCalls() {
            return annotation.forceSplitDirectCalls();
        }

        @Override
        public boolean autoRegister() {
            return annotation.autoRegister();
        }
    }
}
