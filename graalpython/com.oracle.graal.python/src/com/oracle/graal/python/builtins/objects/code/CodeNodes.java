package com.oracle.graal.python.builtins.objects.code;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

public abstract class CodeNodes {

    public static class CreateCodeNode extends PNodeWithContext {

        @Child private HashingStorageNodes.GetItemNode getItemNode;

        @TruffleBoundary
        public PCode execute(LazyPythonClass cls, int argcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        byte[] codestring, Object[] constants, Object[] names,
                        Object[] varnames, Object[] freevars, Object[] cellvars,
                        String filename, String name, int firstlineno,
                        byte[] lnotab) {

            RootCallTarget callTarget = null;

            // Derive a new call target from the code string, if we can
            RootNode rootNode = null;
            if (codestring.length > 0) {
                if ((flags & PCode.FLAG_MODULE) == 0) {
                    // we're looking for the function, not the module
                    String funcdef;
                    funcdef = createFuncdef(codestring, freevars, name);

                    rootNode = (RootNode) getCore().getParser().parse(ParserMode.File, getCore(), Source.newBuilder(PythonLanguage.ID, funcdef, name).build(), null);
                    Object[] args = PArguments.create();
                    PDict globals = factory().createDict();
                    PArguments.setGlobals(args, globals);
                    Truffle.getRuntime().createCallTarget(rootNode).call(args);
                    Object function = ensureGetItemNode().execute(globals.getDictStorage(), name);
                    if (function instanceof PFunction) {
                        rootNode = ((PFunction) function).getFunctionRootNode();
                    } else {
                        throw raise(PythonBuiltinClassType.ValueError, "got an invalid codestring trying to create a function code object");
                    }
                } else {
                    MaterializedFrame frame = null;
                    if (freevars.length > 0) {
                        FrameDescriptor frameDescriptor = new FrameDescriptor();
                        frame = Truffle.getRuntime().createMaterializedFrame(new Object[0], frameDescriptor);
                        for (int i = 0; i < freevars.length; i++) {
                            Object ident = freevars[i];
                            FrameSlot slot = frameDescriptor.addFrameSlot(ident);
                            frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Object);
                            frame.setObject(slot, new PCell());
                        }
                    }
                    rootNode = (RootNode) getCore().getParser().parse(ParserMode.File, getCore(), Source.newBuilder(PythonLanguage.ID, new String(codestring), name).build(), frame);
                    assert rootNode instanceof ModuleRootNode;
                }
                callTarget = Truffle.getRuntime().createCallTarget(rootNode);
            } else {
                callTarget = Truffle.getRuntime().createCallTarget(new RootNode(PythonLanguage.getCurrent()) {
                    @Override
                    public Object execute(VirtualFrame frame) {
                        return PNone.NONE;
                    }
                });
            }

            Arity arity = createArity(flags, argcount, kwonlyargcount, varnames);

            return factory().createCode(cls, callTarget, arity, nlocals, stacksize, flags, codestring, constants, names, varnames, freevars, cellvars, filename, name, firstlineno, lnotab);
        }

        private static String createFuncdef(byte[] codestring, Object[] freevars, String name) {
            CompilerAsserts.neverPartOfCompilation();
            if (freevars.length > 0) {
                // we build an outer function to provide the initial scoping
                String outernme = "_____" + System.nanoTime();
                StringBuilder sb = new StringBuilder();
                sb.append("def ").append(outernme).append("():\n");
                for (Object freevar : freevars) {
                    String v;
                    if (freevar instanceof PString) {
                        v = ((PString) freevar).getValue();
                    } else if (freevar instanceof String) {
                        v = (String) freevar;
                    } else {
                        continue;
                    }
                    sb.append(" ").append(v).append(" = None\n");
                }
                sb.append(" global ").append(name).append("\n");
                sb.append(" ").append(new String(codestring));
                sb.append("\n\n").append(outernme).append("()");
                return sb.toString();
            } else {
                return new String(codestring);
            }
        }

        private static Arity createArity(int flags, int argcount, int kwonlyargcount, Object[] varnames) {
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
            return new Arity(PCode.takesVarKeywordArgs(flags), PCode.takesVarArgs(flags) ? argcount : -1, !PCode.takesVarArgs(flags) && kwonlyargcount > 0, paramNames, kwNames);
        }

        private HashingStorageNodes.GetItemNode ensureGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(HashingStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }

        public static CreateCodeNode create() {
            return new CreateCodeNode();
        }
    }
}
