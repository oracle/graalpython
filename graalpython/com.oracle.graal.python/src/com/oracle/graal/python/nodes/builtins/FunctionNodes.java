/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetDefaultsNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetFunctionCodeNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetFunctionDefaultsNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetFunctionKeywordDefaultsNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetKeywordDefaultsNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetSignatureNodeGen;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class FunctionNodes {
    @GenerateUncached
    public abstract static class GetFunctionDefaultsNode extends PNodeWithContext {
        public abstract Object[] execute(PFunction function);

        @Specialization(guards = {"self == cachedSelf"}, assumptions = {"singleContextAssumption()", "defaultsStableAssumption"})
        Object[] getDefaultsCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached(value = "self.getDefaults()", dimensions = 1) Object[] cachedDefaults,
                        @SuppressWarnings("unused") @Cached("self.getDefaultsStableAssumption()") Assumption defaultsStableAssumption) {
            return cachedDefaults;
        }

        @Specialization(replaces = "getDefaultsCached")
        Object[] getDefaults(PFunction self) {
            return self.getDefaults();
        }

        public static GetFunctionDefaultsNode create() {
            return GetFunctionDefaultsNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetDefaultsNode extends PNodeWithContext {
        private static Object[] doFunctionInternal(GetFunctionDefaultsNode getFunctionDefaultsNode, PFunction function) {
            return getFunctionDefaultsNode.execute(function);
        }

        public abstract Object[] execute(Object function);

        @Specialization
        Object[] doFunction(PFunction function,
                        @Cached("create()") GetFunctionDefaultsNode getFunctionDefaultsNode) {
            return doFunctionInternal(getFunctionDefaultsNode, function);
        }

        @Specialization
        Object[] doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getDefaults();
        }

        @Specialization(guards = "!isSameObject(method, method.getFunction())")
        Object[] doMethod(PMethod method,
                        @Cached("create()") GetDefaultsNode getDefaultsNode) {
            return getDefaultsNode.execute(method.getFunction());
        }

        @Specialization(guards = "!isSameObject(builtinMethod, builtinMethod.getFunction())")
        Object[] doBuiltinMethod(PBuiltinMethod builtinMethod,
                        @Cached("create()") GetDefaultsNode getDefaultsNode) {
            return getDefaultsNode.execute(builtinMethod.getFunction());
        }

        public static GetDefaultsNode create() {
            return GetDefaultsNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class GetFunctionKeywordDefaultsNode extends PNodeWithContext {
        public abstract PKeyword[] execute(PFunction function);

        @Specialization(guards = {"self == cachedSelf"}, assumptions = {"singleContextAssumption()", "defaultsStableAssumption"})
        PKeyword[] getKwDefaultsCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached(value = "self.getKwDefaults()", dimensions = 1) PKeyword[] cachedKeywordDefaults,
                        @SuppressWarnings("unused") @Cached("self.getDefaultsStableAssumption()") Assumption defaultsStableAssumption) {
            return cachedKeywordDefaults;
        }

        @Specialization(replaces = "getKwDefaultsCached")
        PKeyword[] getKwDefaults(PFunction self) {
            return self.getKwDefaults();
        }

        public static GetFunctionKeywordDefaultsNode create() {
            return GetFunctionKeywordDefaultsNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetKeywordDefaultsNode extends PNodeWithContext {
        private static PKeyword[] doFunctionInternal(GetFunctionKeywordDefaultsNode getFunctionKeywordDefaultsNode, PFunction function) {
            return getFunctionKeywordDefaultsNode.execute(function);
        }

        public abstract PKeyword[] execute(Object function);

        @Specialization
        PKeyword[] doFunction(PFunction function,
                        @Cached("create()") GetFunctionKeywordDefaultsNode getFunctionKeywordDefaultsNode) {
            return doFunctionInternal(getFunctionKeywordDefaultsNode, function);
        }

        @Specialization
        PKeyword[] doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getKwDefaults();
        }

        @Specialization(guards = "!isSameObject(method, method.getFunction())")
        PKeyword[] doMethod(PMethod method,
                        @Cached("create()") GetKeywordDefaultsNode getKeywordDefaultsNode) {
            return getKeywordDefaultsNode.execute(method.getFunction());
        }

        @Specialization(guards = "!isSameObject(builtinMethod, builtinMethod.getFunction())")
        PKeyword[] doBuiltinMethod(PBuiltinMethod builtinMethod,
                        @Cached("create()") GetKeywordDefaultsNode getKeywordDefaultsNode) {
            return getKeywordDefaultsNode.execute(builtinMethod.getFunction());
        }

        public static GetKeywordDefaultsNode create() {
            return GetKeywordDefaultsNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class GetFunctionCodeNode extends PNodeWithContext {
        public abstract PCode execute(PFunction function);

        @Specialization(guards = {"self == cachedSelf"}, assumptions = {"singleContextAssumption()", "codeStableAssumption"})
        PCode getCodeCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached("self.getCode()") PCode cachedCode,
                        @SuppressWarnings("unused") @Cached("self.getCodeStableAssumption()") Assumption codeStableAssumption) {
            return cachedCode;
        }

        @Specialization(replaces = "getCodeCached")
        PCode getCodeUncached(PFunction self) {
            return self.getCode();
        }

        public static GetFunctionCodeNode create() {
            return GetFunctionCodeNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetSignatureNode extends PNodeWithContext {
        private static Signature doFunctionInternal(GetFunctionCodeNode getFunctionCodeNode, PFunction function) {
            PCode code = getFunctionCodeNode.execute(function);
            return code.getSignature();
        }

        public abstract Signature execute(Object function);

        @Specialization
        Signature doFunction(PFunction function,
                        @Cached("create()") GetFunctionCodeNode getFunctionCodeNode) {
            return doFunctionInternal(getFunctionCodeNode, function);
        }

        @Specialization
        Signature doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getSignature();
        }

        @Specialization(guards = "!isSameObject(method, method.getFunction())")
        Signature doMethod(PMethod method,
                        @Cached("create()") GetSignatureNode getSignatureNode) {
            return getSignatureNode.execute(method.getFunction());
        }

        @Specialization(guards = "!isSameObject(builtinMethod, builtinMethod.getFunction())")
        Signature doBuiltinMethod(PBuiltinMethod builtinMethod,
                        @Cached("create()") GetSignatureNode getSignatureNode) {
            return getSignatureNode.execute(builtinMethod.getFunction());
        }

        public static GetSignatureNode create() {
            return GetSignatureNodeGen.create();
        }
    }
}
