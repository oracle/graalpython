/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.util.BadOPCodeNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class CodeNodes {

    public static class CreateCodeNode extends PNodeWithContext implements IndirectCallNode {
        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        public PCode execute(VirtualFrame frame, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codedata, Object[] constants, TruffleString[] names,
                        TruffleString[] varnames, TruffleString[] freevars, TruffleString[] cellvars,
                        TruffleString filename, TruffleString name, int firstlineno,
                        byte[] linetable) {

            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return createCode(language, context, argcount,
                                posonlyargcount, kwonlyargcount, nlocals, stacksize, flags, codedata,
                                constants, names, varnames, freevars, cellvars, filename, name, firstlineno, linetable);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private static PCode createCode(PythonLanguage language, PythonContext context, @SuppressWarnings("unused") int argcount,
                        @SuppressWarnings("unused") int posonlyargcount, @SuppressWarnings("unused") int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codedata, Object[] constants, TruffleString[] names,
                        TruffleString[] varnames, TruffleString[] freevars, TruffleString[] cellvars,
                        TruffleString filename, TruffleString name, int firstlineno,
                        byte[] linetable) {

            RootCallTarget ct;
            if (codedata.length == 0) {
                ct = language.createCachedCallTarget(l -> new BadOPCodeNode(l, name), BadOPCodeNode.class, filename, name);
            } else {
                ct = create().deserializeForBytecodeInterpreter(language, codedata, cellvars, freevars);
            }
            if (filename != null) {
                context.setCodeFilename(ct, filename);
            }
            PythonObjectFactory factory = context.factory();
            return factory.createCode(ct, ((PRootNode) ct.getRootNode()).getSignature(), nlocals, stacksize, flags, constants, names, varnames, freevars, cellvars, filename, name,
                            firstlineno, linetable);
        }

        @SuppressWarnings("static-method")
        private RootCallTarget deserializeForBytecodeInterpreter(PythonLanguage language, byte[] data, TruffleString[] cellvars, TruffleString[] freevars) {
            CodeUnit code = MarshalModuleBuiltins.deserializeCodeUnit(data);
            if (cellvars != null && !Arrays.equals(code.cellvars, cellvars) || freevars != null && !Arrays.equals(code.freevars, freevars)) {
                code = new CodeUnit(code.name, code.qualname, code.argCount, code.kwOnlyArgCount, code.positionalOnlyArgCount, code.stacksize, code.code,
                                code.srcOffsetTable, code.flags, code.names, code.varnames,
                                cellvars != null ? cellvars : code.cellvars, freevars != null ? freevars : code.freevars,
                                code.cell2arg, code.constants, code.primitiveConstants, code.exceptionHandlerRanges, code.conditionProfileCount,
                                code.startLine, code.startColumn, code.endLine, code.endColumn,
                                code.outputCanQuicken, code.variableShouldUnbox,
                                code.generalizeInputsMap, code.generalizeVarsMap);
            }
            RootNode rootNode = PBytecodeRootNode.create(language, code, PythonUtils.createFakeSource());
            if (code.isGeneratorOrCoroutine()) {
                rootNode = new PBytecodeGeneratorFunctionRootNode(language, rootNode.getFrameDescriptor(), (PBytecodeRootNode) rootNode, code.name);
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

        public static CreateCodeNode create() {
            return new CreateCodeNode();
        }
    }

    public static final class GetCodeCallTargetNode extends Node {
        private static final GetCodeCallTargetNode UNCACHED = new GetCodeCallTargetNode(false);

        private final boolean isAdoptable;
        @CompilationFinal private ConditionProfile hasCtProfile;
        @CompilationFinal private PCode cachedCode1;
        @CompilationFinal private PCode cachedCode2;
        @CompilationFinal private RootCallTarget cachedCt1;
        @CompilationFinal private RootCallTarget cachedCt2;

        private GetCodeCallTargetNode(boolean isAdoptable) {
            this.isAdoptable = isAdoptable;
        }

        public final RootCallTarget execute(PCode code) {
            if (isAdoptable) {
                if (hasCtProfile == null) {
                    if (PythonLanguage.get(this).isSingleContext()) {
                        if (cachedCode1 == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            cachedCode1 = code;
                            cachedCt1 = code.initializeCallTarget();
                            return cachedCt1;
                        }
                        if (cachedCode1 == code) {
                            return cachedCt1;
                        }
                        if (cachedCode2 == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            cachedCode2 = code;
                            cachedCt2 = code.initializeCallTarget();
                            return cachedCt2;
                        }
                        if (cachedCode2 == code) {
                            return cachedCt2;
                        }
                    }
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedCode1 = cachedCode2 = null;
                    cachedCt1 = cachedCt2 = null;
                    hasCtProfile = ConditionProfile.createBinaryProfile();
                }
                RootCallTarget ct = code.callTarget;
                if (hasCtProfile.profile(ct == null)) {
                    ct = code.initializeCallTarget();
                }
                return ct;
            } else {
                RootCallTarget ct = code.callTarget;
                if (ct == null) {
                    ct = code.initializeCallTarget();
                }
                return ct;
            }
        }

        public static GetCodeCallTargetNode create() {
            return new GetCodeCallTargetNode(true);
        }

        public static GetCodeCallTargetNode getUncached() {
            return UNCACHED;
        }
    }

    @GenerateUncached
    public abstract static class GetCodeSignatureNode extends PNodeWithContext {
        public abstract Signature execute(PCode code);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSingleContext()", "cachedCode == code"}, limit = "2")
        protected static Signature doCached(PCode code,
                        @Cached("code") PCode cachedCode,
                        @Cached("code.initializeCallTarget()") RootCallTarget ct,
                        @Cached("code.initializeSignature(ct)") Signature signature) {
            return signature;
        }

        @Specialization(replaces = "doCached")
        protected static Signature doCode(PCode code,
                        @Cached ConditionProfile signatureProfile,
                        @Cached ConditionProfile ctProfile) {
            Signature signature = code.signature;
            if (signatureProfile.profile(signature == null)) {
                RootCallTarget ct = code.callTarget;
                if (ctProfile.profile(ct == null)) {
                    ct = code.initializeCallTarget();
                }
                signature = code.initializeSignature(ct);
            }
            return signature;
        }
    }

    public static final class GetCodeRootNode extends Node {
        private static final GetCodeRootNode UNCACHED = new GetCodeRootNode(false);

        private final boolean isAdoptable;
        @Child private GetCodeCallTargetNode getCodeCallTargetNode;

        private GetCodeRootNode(boolean isAdoptable) {
            this.isAdoptable = isAdoptable;
            if (!isAdoptable) {
                getCodeCallTargetNode = GetCodeCallTargetNode.getUncached();
            }
        }

        @Override
        public boolean isAdoptable() {
            return isAdoptable;
        }

        public final RootNode execute(PCode code) {
            if (getCodeCallTargetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getCodeCallTargetNode = insert(GetCodeCallTargetNode.create());
            }
            return getCodeCallTargetNode.execute(code).getRootNode();
        }

        public static GetCodeRootNode create() {
            return new GetCodeRootNode(true);
        }

        public static GetCodeRootNode getUncached() {
            return UNCACHED;
        }
    }
}
