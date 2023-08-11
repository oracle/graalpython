/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.builtins;

import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.method.PMethodBase;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetCallTargetNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetSignatureNodeGen;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class FunctionNodes {

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetDefaultsNode extends PNodeWithContext {

        public abstract Object[] execute(Node inliningTarget, Object function);

        public abstract Object[] execute(Node inliningTarget, PMethodBase function);

        /**
         * Fast-path if method is a partial evaluation constant.
         */
        public static Object[] getMethodDefaults(PMethodBase method) {
            CompilerAsserts.partialEvaluationConstant(method);
            return getFunctionDefaults(method.getFunction());
        }

        /**
         * Fast-path if method is a partial evaluation constant.
         */
        public static Object[] getFunctionDefaults(Object fun) {
            CompilerAsserts.partialEvaluationConstant(fun);
            if (fun instanceof PFunction f) {
                return f.getDefaults();
            } else if (fun instanceof PBuiltinFunction f) {
                return f.getDefaults();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        static Object[] doFunction(PFunction function) {
            return function.getDefaults();
        }

        @Specialization
        static Object[] doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getDefaults();
        }

        @Specialization(guards = "isPFunction(function)")
        static Object[] doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PFunction) function).getDefaults();
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static Object[] doMethodBuiltin(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getDefaults();
        }

        @Specialization
        static Object[] doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getBuiltinFunction().getDefaults();
        }

    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetKeywordDefaultsNode extends PNodeWithContext {

        public abstract PKeyword[] execute(Node inliningTarget, Object function);

        public abstract PKeyword[] execute(Node inliningTarget, PMethodBase method);

        /**
         * Fast-path if method is a partial evaluation constant.
         */
        public static PKeyword[] getMethodKeywords(PMethodBase method) {
            CompilerAsserts.partialEvaluationConstant(method);
            return getFunctionKeywords(method.getFunction());
        }

        /**
         * Fast-path if the function is a partial evaluation constant.
         */
        public static PKeyword[] getFunctionKeywords(Object fun) {
            CompilerAsserts.partialEvaluationConstant(fun);
            if (fun instanceof PFunction f) {
                return f.getKwDefaults();
            } else if (fun instanceof PBuiltinFunction f) {
                return f.getKwDefaults();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        static PKeyword[] doFunction(PFunction function) {
            return function.getKwDefaults();
        }

        @Specialization
        static PKeyword[] doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getKwDefaults();
        }

        @Specialization(guards = "isPFunction(function)")
        static PKeyword[] doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PFunction) function).getKwDefaults();
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static PKeyword[] doMethodBuiltin(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getKwDefaults();
        }

        @Specialization
        static PKeyword[] doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getBuiltinFunction().getKwDefaults();
        }

    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetFunctionCodeNode extends PNodeWithContext {

        public abstract PCode execute(Node inliningTarget, PFunction function);

        @Specialization(guards = {"isSingleContext()", "self == cachedSelf"}, assumptions = "cachedSelf.getCodeStableAssumption()", limit = "3")
        static PCode getCodeCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached("self.getCode()") PCode cachedCode) {
            return cachedCode;
        }

        @Specialization(replaces = "getCodeCached")
        static PCode getCodeUncached(PFunction self) {
            return self.getCode();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetSignatureNode extends PNodeWithContext {

        public static Signature executeUncached(Object function) {
            return GetSignatureNodeGen.getUncached().execute(null, function);
        }

        // We assume the caller has IC on "function" if folding is desired
        public abstract Signature execute(Node inliningTarget, Object function);

        public abstract Signature execute(Node inliningTarget, PMethodBase function);

        /**
         * Fast-path if method is a partial evaluation constant, and we are in single context mode.
         */
        public static Signature getMethodSignatureSingleContext(PMethodBase method, Node inliningTarget) {
            CompilerAsserts.partialEvaluationConstant(method);
            return getFunctionSignatureSingleContext(inliningTarget, method.getFunction());
        }

        /**
         * Fast-path if method is a partial evaluation constant, and we are in single context mode.
         */
        public static Signature getFunctionSignatureSingleContext(Node inliningTarget, Object fun) {
            CompilerAsserts.partialEvaluationConstant(fun);
            if (fun instanceof PFunction f) {
                return CodeNodes.GetCodeSignatureNode.getInSingleContextMode(inliningTarget, f);
            } else if (fun instanceof PBuiltinFunction f) {
                return f.getSignature();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        static Signature doFunction(Node inliningTarget, PFunction function,
                        @Shared("getSignature") @Cached CodeNodes.GetCodeSignatureNode getSignature) {
            return getSignature.execute(inliningTarget, function.getCode());
        }

        @Specialization
        static Signature doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getSignature();
        }

        @Specialization(guards = "isPFunction(function)")
        static Signature doMethod(Node inliningTarget, @SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function,
                        @Shared("getSignature") @Cached CodeNodes.GetCodeSignatureNode getSignature) {
            return getSignature.execute(inliningTarget, ((PFunction) function).getCode());
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static Signature doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getSignature();
        }

        @Specialization
        static Signature doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getBuiltinFunction().getSignature();
        }

        public static GetSignatureNode getUncached() {
            return GetSignatureNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @GenerateInline(false) // used lazily
    public abstract static class GetCallTargetNode extends PNodeWithContext {

        public abstract RootCallTarget execute(Object function);

        @Specialization
        static RootCallTarget doFunction(PFunction function,
                        @Bind("this") Node inliningTarget,
                        @Shared("getCode") @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            return getCt.execute(inliningTarget, getFunctionCodeNode.execute(inliningTarget, function));
        }

        @Specialization
        static RootCallTarget doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getCallTarget();
        }

        @Specialization(guards = "isPFunction(function)")
        static RootCallTarget doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("this") Node inliningTarget,
                        @Bind("method.getFunction()") Object function,
                        @Shared("getCode") @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            return getCt.execute(inliningTarget, getFunctionCodeNode.execute(inliningTarget, (PFunction) function));
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static RootCallTarget doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getCallTarget();
        }

        @Specialization
        static RootCallTarget doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getBuiltinFunction().getCallTarget();
        }

        @Fallback
        static RootCallTarget fallback(@SuppressWarnings("unused") Object callable) {
            return null;
        }

        public static GetCallTargetNode getUncached() {
            return GetCallTargetNodeGen.getUncached();
        }
    }
}
