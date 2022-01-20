/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetCallTargetNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetDefaultsNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetKeywordDefaultsNodeGen;
import com.oracle.graal.python.nodes.builtins.FunctionNodesFactory.GetSignatureNodeGen;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class FunctionNodes {
    @GenerateUncached
    public abstract static class GetFunctionDefaultsNode extends PNodeWithContext {

        public abstract Object[] execute(PFunction function);

        @Specialization(guards = {"isSingleContext()", "self == cachedSelf"}, assumptions = "defaultsStableAssumption")
        static Object[] getDefaultsCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached(value = "self.getDefaults()", dimensions = 1) Object[] cachedDefaults,
                        @SuppressWarnings("unused") @Cached("self.getDefaultsStableAssumption()") Assumption defaultsStableAssumption) {
            return cachedDefaults;
        }

        @Specialization(replaces = "getDefaultsCached")
        static Object[] getDefaults(PFunction self) {
            return self.getDefaults();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetDefaultsNode extends PNodeWithContext {

        public abstract Object[] execute(Object function);

        @Specialization
        static Object[] doFunction(PFunction function,
                        @Shared("get") @Cached GetFunctionDefaultsNode getFunctionDefaultsNode) {
            return getFunctionDefaultsNode.execute(function);
        }

        @Specialization
        static Object[] doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getDefaults();
        }

        @Specialization(guards = "isPFunction(function)")
        static Object[] doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function,
                        @Shared("get") @Cached GetFunctionDefaultsNode getFunctionDefaultsNode) {
            return getFunctionDefaultsNode.execute((PFunction) function);
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static Object[] doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getDefaults();
        }

        @Specialization
        static Object[] doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getFunction().getDefaults();
        }

        public static GetDefaultsNode create() {
            return GetDefaultsNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class GetFunctionKeywordDefaultsNode extends PNodeWithContext {

        public abstract PKeyword[] execute(PFunction function);

        @Specialization(guards = {"isSingleContext()", "self == cachedSelf"}, assumptions = "defaultsStableAssumption")
        static PKeyword[] getKwDefaultsCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached(value = "self.getKwDefaults()", dimensions = 1) PKeyword[] cachedKeywordDefaults,
                        @SuppressWarnings("unused") @Cached("self.getDefaultsStableAssumption()") Assumption defaultsStableAssumption) {
            return cachedKeywordDefaults;
        }

        @Specialization(replaces = "getKwDefaultsCached")
        static PKeyword[] getKwDefaults(PFunction self) {
            return self.getKwDefaults();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetKeywordDefaultsNode extends PNodeWithContext {

        public abstract PKeyword[] execute(Object function);

        @Specialization
        static PKeyword[] doFunction(PFunction function,
                        @Shared("get") @Cached GetFunctionKeywordDefaultsNode getFunctionKeywordDefaultsNode) {
            return getFunctionKeywordDefaultsNode.execute(function);
        }

        @Specialization
        static PKeyword[] doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getKwDefaults();
        }

        @Specialization(guards = "isPFunction(function)")
        static PKeyword[] doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function,
                        @Shared("get") @Cached GetFunctionKeywordDefaultsNode getFunctionKeywordDefaultsNode) {
            return getFunctionKeywordDefaultsNode.execute((PFunction) function);
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static PKeyword[] doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getKwDefaults();
        }

        @Specialization
        static PKeyword[] doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getFunction().getKwDefaults();
        }

        public static GetKeywordDefaultsNode create() {
            return GetKeywordDefaultsNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class GetFunctionCodeNode extends PNodeWithContext {

        public abstract PCode execute(PFunction function);

        @Specialization(guards = {"isSingleContext()", "self == cachedSelf"}, assumptions = "codeStableAssumption")
        static PCode getCodeCached(@SuppressWarnings("unused") PFunction self,
                        @SuppressWarnings("unused") @Cached("self") PFunction cachedSelf,
                        @Cached("self.getCode()") PCode cachedCode,
                        @SuppressWarnings("unused") @Cached("self.getCodeStableAssumption()") Assumption codeStableAssumption) {
            return cachedCode;
        }

        @Specialization(replaces = "getCodeCached")
        static PCode getCodeUncached(PFunction self) {
            return self.getCode();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetSignatureNode extends PNodeWithContext {

        public abstract Signature execute(Object function);

        @Specialization
        static Signature doFunction(PFunction function,
                        @Shared("get") @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("getSignature") @Cached CodeNodes.GetCodeSignatureNode getSignature) {
            return getSignature.execute(getFunctionCodeNode.execute(function));
        }

        @Specialization
        static Signature doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getSignature();
        }

        @Specialization(guards = "isPFunction(function)")
        static Signature doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function,
                        @Shared("get") @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("getSignature") @Cached CodeNodes.GetCodeSignatureNode getSignature) {
            return getSignature.execute(getFunctionCodeNode.execute((PFunction) function));
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static Signature doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getSignature();
        }

        @Specialization
        static Signature doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getFunction().getSignature();
        }

        public static GetSignatureNode create() {
            return GetSignatureNodeGen.create();
        }

        public static GetSignatureNode getUncached() {
            return GetSignatureNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetCallTargetNode extends PNodeWithContext {

        public abstract RootCallTarget execute(Object function);

        @Specialization
        static RootCallTarget doFunction(PFunction function,
                        @Shared("getCode") @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            return getCt.execute(getFunctionCodeNode.execute(function));
        }

        @Specialization
        static RootCallTarget doBuiltinFunction(PBuiltinFunction builtinFunction) {
            return builtinFunction.getCallTarget();
        }

        @Specialization(guards = "isPFunction(function)")
        static RootCallTarget doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function,
                        @Shared("getCode") @Cached GetFunctionCodeNode getFunctionCodeNode,
                        @Shared("getCt") @Cached CodeNodes.GetCodeCallTargetNode getCt) {
            return getCt.execute(getFunctionCodeNode.execute((PFunction) function));
        }

        @Specialization(guards = "isPBuiltinFunction(method.getFunction())")
        static RootCallTarget doMethod(@SuppressWarnings("unused") PMethod method,
                        @Bind("method.getFunction()") Object function) {
            return ((PBuiltinFunction) function).getCallTarget();
        }

        @Specialization
        static RootCallTarget doBuiltinMethod(PBuiltinMethod builtinMethod) {
            return builtinMethod.getFunction().getCallTarget();
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
