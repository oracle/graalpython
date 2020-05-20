/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.InvokeNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

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

        @CompilationFinal private ContextReference<PythonContext> contextRef;

        @SuppressWarnings("try")
        public PCode execute(VirtualFrame frame, LazyPythonClass cls, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codestring, Object[] constants, Object[] names,
                        Object[] varnames, Object[] freevars, Object[] cellvars,
                        String filename, String name, int firstlineno,
                        byte[] lnotab) {

            PythonContext context = getContextRef().get();
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return createCode(cls, argcount, posonlyargcount, kwonlyargcount, nlocals, stacksize, flags, codestring,
                                constants, names, varnames, freevars, cellvars, filename, name, firstlineno, lnotab);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }

        @TruffleBoundary
        private static PCode createCode(LazyPythonClass cls, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codestring, Object[] constants, Object[] names,
                        Object[] varnames, Object[] freevars, Object[] cellvars,
                        String filename, String name, int firstlineno,
                        byte[] lnotab) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            RootCallTarget callTarget = null;

            // Derive a new call target from the code string, if we can
            RootNode rootNode = null;
            if (codestring.length > 0) {
                PythonCore core = PythonLanguage.getCore();
                if ((flags & PCode.FLAG_MODULE) == 0) {
                    String funcdef = createFuncdef(argcount, kwonlyargcount, flags, codestring, varnames, freevars, name);
                    rootNode = getFunctionFromCode(core, factory, name, funcdef).getFunctionRootNode();
                    rootNode = patchConstantsAndGlobalNames(rootNode, constants, names);
                } else {
                    rootNode = (RootNode) core.getParser().parse(ParserMode.File, core, Source.newBuilder(PythonLanguage.ID, new String(codestring), name).build(), null);
                    assert rootNode instanceof ModuleRootNode;
                }
                callTarget = Truffle.getRuntime().createCallTarget(rootNode);
            } else {
                callTarget = Truffle.getRuntime().createCallTarget(new PRootNode(PythonLanguage.getCurrent()) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return PNone.NONE;
                    }

                    @Override
                    public Signature getSignature() {
                        return Signature.EMPTY;
                    }

                    @Override
                    public boolean isPythonInternal() {
                        return false;
                    }
                });
            }

            Signature signature = createSignature(flags, argcount, posonlyargcount, kwonlyargcount, varnames);

            return factory.createCode(cls, callTarget, signature, nlocals, stacksize, flags, codestring, constants, names, varnames, freevars, cellvars, filename, name, firstlineno, lnotab);
        }

        private static RootNode patchConstantsAndGlobalNames(RootNode rootNode, Object[] constants, Object[] names) {
            // TODO: fill in updated constants, names, filename, and firstlineno
            for (int i = 0; i < constants.length; i++) {
                // TODO: replace constants if they changed with the new ones
            }
            for (int i = 0; i < names.length; i++) {
                // TODO: replace global names if they changed
            }
            return rootNode;
        }

        private static PFunction getFunctionFromCode(PythonCore core, PythonObjectFactory factory, String name, String funcdef) {
            RootNode rootNode = (RootNode) core.getParser().parse(ParserMode.File, core, Source.newBuilder(PythonLanguage.ID, funcdef, name).build(), null);
            Object[] args = PArguments.create();
            PDict globals = factory.createDict();
            PArguments.setGlobals(args, globals);
            Object returnValue = InvokeNode.invokeUncached(Truffle.getRuntime().createCallTarget(rootNode), args);
            if (!(returnValue instanceof PFunction)) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.ValueError, "got an invalid codestring trying to create a function code object");
            }
            return (PFunction) returnValue;
        }

        private static String createFuncdef(int argcount, int kwonlyargcount, int flags, byte[] codestring, Object[] varnames, Object[] freevars, String name) {
            String indent = " ";
            StringBuilder funcdef = new StringBuilder();
            long outerName = System.nanoTime();
            funcdef.append("def outer").append(outerName).append("():\n");
            for (Object freevar : freevars) {
                funcdef.append(indent).append(castToString(freevar)).append(" = None\n");
            }

            boolean isLambda = (flags & PCode.FLAG_LAMBDA) != 0;
            if (isLambda) {
                funcdef.append(indent).append("return lambda ");
            } else {
                funcdef.append(indent).append("def ").append(name).append("(");
            }
            int varnameIdx = 0;
            for (; varnameIdx < argcount; varnameIdx++) {
                funcdef.append(castToString(varnames[varnameIdx])).append(",");
            }
            if ((flags & PCode.FLAG_VAR_ARGS) != 0) {
                // vararg name is after kwargs names
                funcdef.append("*").append(castToString(varnames[varnameIdx + kwonlyargcount])).append(",");
            }
            for (; varnameIdx < kwonlyargcount; varnameIdx++) {
                funcdef.append(castToString(varnames[varnameIdx])).append("=None,");
            }
            if ((flags & PCode.FLAG_VAR_KW_ARGS) != 0) {
                if ((flags & PCode.FLAG_VAR_ARGS) != 0) {
                    varnameIdx++;
                }
                funcdef.append("**").append(castToString(varnames[varnameIdx])).append(",");
            }

            if (isLambda) {
                funcdef.append(": ").append(new String(codestring)).append("\n");
            } else {
                funcdef.append("):\n");

                String[] lines = new String(codestring).split("\n");
                String functionIndent = " ";
                String firstLineIndent = "";

                // find indent inside the function to correctly insert nonlocal declarations
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        int i = 0;
                        char ch;
                        while (Character.isWhitespace(ch = line.charAt(i++))) {
                            sb.append(ch);
                        }
                        firstLineIndent = sb.toString();
                        break;
                    }
                }
                for (Object freevar : freevars) {
                    funcdef.append(indent).append(functionIndent).append(firstLineIndent).append("nonlocal ").append(castToString(freevar)).append("\n");
                }
                for (String line : lines) {
                    funcdef.append(indent).append(functionIndent).append(line).append("\n");
                }
                funcdef.append(indent).append("return ").append(name).append("\n");
            }

            funcdef.append("outer").append(outerName).append("()");
            return funcdef.toString();
        }

        private static String castToString(Object obj) {
            try {
                return CastToJavaStringNode.getUncached().execute(obj);
            } catch (CannotCastException e) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, "non-string found in code slot");
            }
        }

        private static Signature createSignature(int flags, int argcount, int posonlyargcount, int kwonlyargcount, Object[] varnames) {
            CompilerAsserts.neverPartOfCompilation();
            char paramNom = 'A';
            String[] paramNames = new String[argcount];
            for (int i = 0; i < paramNames.length; i++) {
                if (varnames.length > i) {
                    Object varname = varnames[i];
                    if (varname instanceof String) {
                        paramNames[i] = (String) varname;
                        continue;
                    }
                }
                paramNames[i] = Character.toString(paramNom++);
            }
            String[] kwNames = new String[kwonlyargcount];
            for (int i = 0; i < kwNames.length; i++) {
                if (varnames.length > i + argcount) {
                    Object varname = varnames[i + argcount];
                    if (varname instanceof String) {
                        kwNames[i] = (String) varname;
                        continue;
                    }
                }
                kwNames[i] = Character.toString(paramNom++);
            }
            return new Signature(posonlyargcount, PCode.takesVarKeywordArgs(flags), PCode.takesVarArgs(flags) ? argcount : -1, !PCode.takesVarArgs(flags) && kwonlyargcount > 0, paramNames, kwNames);
        }

        private ContextReference<PythonContext> getContextRef() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef;
        }

        public static CreateCodeNode create() {
            return new CreateCodeNode();
        }
    }
}
