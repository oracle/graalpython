/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.code;

import java.util.Arrays;

import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.objects.code.CodeNodesFactory.GetCodeCallTargetNodeGen;
import com.oracle.graal.python.builtins.objects.code.CodeNodesFactory.GetCodeRootNodeGen;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.util.BadOPCodeNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class CodeNodes {

    public static class CreateCodeNode extends PNodeWithContext {
        @SuppressWarnings("this-escape") // we only need the reference, doesn't matter that the
                                         // object may not yet be fully constructed
        private final IndirectCallData indirectCallData = IndirectCallData.createFor(this);

        public PCode execute(VirtualFrame frame, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codedata, Object[] constants, TruffleString[] names,
                        TruffleString[] varnames, TruffleString[] freevars, TruffleString[] cellvars,
                        TruffleString filename, TruffleString name, TruffleString qualname, int firstlineno,
                        byte[] linetable) {

            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return createCode(language, context, argcount,
                                posonlyargcount, kwonlyargcount, nlocals, stacksize, flags, codedata,
                                constants, names, varnames, freevars, cellvars,
                                filename, name, qualname, firstlineno, linetable);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private static PCode createCode(PythonLanguage language, PythonContext context, int argCount,
                        int positionalOnlyArgCount, int kwOnlyArgCount,
                        int nlocals, int stacksize, int flags,
                        byte[] codedata, Object[] constants, TruffleString[] names,
                        TruffleString[] varnames, TruffleString[] freevars, TruffleString[] cellvars,
                        TruffleString filename, TruffleString name, TruffleString qualname, int firstlineno,
                        byte[] linetable) {

            RootCallTarget ct;
            Signature signature;
            if (codedata.length == 0) {
                ct = language.createCachedCallTarget(l -> new BadOPCodeNode(l, name), BadOPCodeNode.class, filename, name);
                /*
                 * We need to create a proper signature because this code path is used to create
                 * fake code objects for duck-typed function-like objects, such as Cython functions.
                 * Even if the code object is not executable, it will be used for introspection when
                 * you call `inspect.signature()` on such function-like object.
                 */
                int posArgCount = argCount + positionalOnlyArgCount;
                TruffleString[] parameterNames, kwOnlyNames;
                if (varnames != null) {
                    parameterNames = Arrays.copyOf(varnames, posArgCount);
                    kwOnlyNames = Arrays.copyOfRange(varnames, posArgCount, posArgCount + kwOnlyArgCount);
                } else {
                    parameterNames = kwOnlyNames = PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
                }
                int varArgsIndex = (flags & PCode.CO_VARARGS) != 0 ? posArgCount : -1;
                signature = new Signature(positionalOnlyArgCount,
                                (flags & PCode.CO_VARKEYWORDS) != 0,
                                varArgsIndex,
                                positionalOnlyArgCount > 0,
                                parameterNames,
                                kwOnlyNames);
            } else {
                ct = create().deserializeForBytecodeInterpreter(language, context, codedata, cellvars, freevars);
                signature = ((PRootNode) ct.getRootNode()).getSignature();
            }
            if (filename != null) {
                context.setCodeFilename(ct, filename);
            }
            PythonObjectFactory factory = context.factory();
            return factory.createCode(ct, signature, nlocals, stacksize, flags, constants, names, varnames, freevars, cellvars, filename, name, qualname, firstlineno, linetable);
        }

        @SuppressWarnings("static-method")
        private RootCallTarget deserializeForBytecodeInterpreter(PythonLanguage language, PythonContext context, byte[] data, TruffleString[] cellvars, TruffleString[] freevars) {
            CodeUnit codeUnit = MarshalModuleBuiltins.deserializeCodeUnit(data);
            RootNode rootNode = null;

            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                BytecodeDSLCodeUnit code = (BytecodeDSLCodeUnit) codeUnit;
                rootNode = code.createRootNode(context, PythonUtils.createFakeSource());
                if (code.isGeneratorOrCoroutine()) {
                    rootNode = new PBytecodeDSLGeneratorFunctionRootNode(language, rootNode.getFrameDescriptor(), (PBytecodeDSLRootNode) rootNode, code.name);
                }
            } else {
                BytecodeCodeUnit code = (BytecodeCodeUnit) codeUnit;
                if (cellvars != null && !Arrays.equals(code.cellvars, cellvars) || freevars != null && !Arrays.equals(code.freevars, freevars)) {
                    code = new BytecodeCodeUnit(code.name, code.qualname, code.argCount, code.kwOnlyArgCount, code.positionalOnlyArgCount, code.flags, code.names,
                                    code.varnames, cellvars != null ? cellvars : code.cellvars, freevars != null ? freevars : code.freevars, code.cell2arg,
                                    code.constants, code.startLine,
                                    code.startColumn, code.endLine, code.endColumn, code.code, code.srcOffsetTable,
                                    code.primitiveConstants, code.exceptionHandlerRanges, code.stacksize, code.conditionProfileCount,
                                    code.outputCanQuicken, code.variableShouldUnbox,
                                    code.generalizeInputsMap, code.generalizeVarsMap);
                }
                rootNode = PBytecodeRootNode.create(language, code, PythonUtils.createFakeSource());
                if (code.isGeneratorOrCoroutine()) {
                    rootNode = new PBytecodeGeneratorFunctionRootNode(language, rootNode.getFrameDescriptor(), (PBytecodeRootNode) rootNode, code.name);
                }
            }
            return PythonUtils.getOrCreateCallTarget(rootNode);
        }

        @TruffleBoundary
        public static PCode createCode(PythonContext context, int flags, byte[] codedata, TruffleString filename, int firstlineno, byte[] lnotab) {
            boolean isNotAModule = (flags & PCode.CO_GRAALPYHON_MODULE) == 0;
            String jFilename = filename.toJavaStringUncached();
            PythonLanguage language = context.getLanguage();
            Supplier<CallTarget> createCode = () -> {
                ByteSequence bytes = ByteSequence.create(codedata);
                Source source = Source.newBuilder(PythonLanguage.ID, bytes, jFilename).mimeType(PythonLanguage.MIME_TYPE_BYTECODE).cached(!language.isSingleContext()).build();
                return context.getEnv().parsePublic(source);
            };

            PythonObjectFactory factory = context.factory();
            if (context.isCoreInitialized() || isNotAModule) {
                return factory.createCode(createCode, flags, firstlineno, lnotab, filename);
            } else {
                RootCallTarget ct = (RootCallTarget) language.cacheCode(filename, createCode);
                return factory.createCode(ct, flags, firstlineno, lnotab, filename);
            }
        }

        @NeverDefault
        public static CreateCodeNode create() {
            return new CreateCodeNode();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetCodeCallTargetNode extends PNodeWithContext {

        GetCodeCallTargetNode() {
        }

        public abstract RootCallTarget execute(Node inliningTarget, PCode code);

        public static RootCallTarget executeUncached(PCode code) {
            return GetCodeCallTargetNodeGen.getUncached().execute(null, code);
        }

        @Specialization(guards = {"cachedCode == code", "isSingleContext()"}, limit = "2")
        static RootCallTarget doCachedCode(@SuppressWarnings("unused") PCode code,
                        @SuppressWarnings("unused") @Cached(value = "code", weak = true) PCode cachedCode,
                        @Cached(value = "code.initializeCallTarget()", weak = true) RootCallTarget cachedRootCallTarget) {
            return cachedRootCallTarget;
        }

        @Specialization(replaces = "doCachedCode")
        static RootCallTarget doGeneric(Node inliningTarget, PCode code,
                        @Cached InlinedConditionProfile hasCtProfile) {
            RootCallTarget ct = code.callTarget;
            if (hasCtProfile.profile(inliningTarget, ct == null)) {
                ct = code.initializeCallTarget();
            }
            return ct;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetCodeSignatureNode extends PNodeWithContext {
        public abstract Signature execute(Node inliningTarget, PCode code);

        @Specialization(guards = {"cachedCode == code", "isSingleContext(inliningTarget)"}, limit = "2")
        static Signature doCached(Node inliningTarget, @SuppressWarnings("unused") PCode code,
                        @Cached("code") PCode cachedCode) {
            return getInSingleContextMode(inliningTarget, cachedCode);
        }

        public static Signature getInSingleContextMode(Node inliningTarget, PFunction fun) {
            assert isSingleContext(inliningTarget);
            CompilerAsserts.partialEvaluationConstant(fun);
            if (CompilerDirectives.inCompiledCode()) {
                return getInSingleContextMode(inliningTarget, fun.getCode());
            } else {
                PCode code = fun.getCode();
                return code.getSignature();
            }
        }

        public static Signature getInSingleContextMode(Node inliningTarget, PCode code) {
            assert isSingleContext(inliningTarget);
            CompilerAsserts.partialEvaluationConstant(code);
            return code.getSignature();
        }

        @Specialization(replaces = "doCached")
        static Signature doCode(Node inliningTarget, PCode code,
                        @Cached InlinedConditionProfile signatureProfile,
                        @Cached InlinedConditionProfile ctProfile) {
            return code.getSignature(inliningTarget, signatureProfile);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetCodeRootNode extends Node {

        public abstract RootNode execute(Node inliningTarget, PCode code);

        public static RootNode executeUncached(PCode code) {
            return GetCodeRootNodeGen.getUncached().execute(null, code);
        }

        @Specialization
        static RootNode doIt(Node inliningTarget, PCode code,
                        @Cached GetCodeCallTargetNode getCodeCallTargetNode) {
            return getCodeCallTargetNode.execute(inliningTarget, code).getRootNode();
        }
    }
}
