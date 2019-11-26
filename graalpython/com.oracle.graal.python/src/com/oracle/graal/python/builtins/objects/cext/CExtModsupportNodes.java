package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode.IsSubtypeWithoutFrameNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

public abstract class CExtModsupportNodes {
    @GenerateUncached
    @ReportPolymorphism
    @ImportStatic(PGuards.class)
    public abstract static class ParseTupleAndKeywordsNode extends Node {

        public abstract int execute(Object argv, Object kwds, Object format, Object kwdnames, Object varargs);

        @Specialization(guards = {"isDictOrNull(kwds)", "cachedFormat.equals(format)", "cachedFormat.length() <= 8"}, limit = "5")
        int doSpecial(PTuple argv, Object kwds, String format, Object[] kwdnames, Object varargs,
                        @Cached(value = "format", allowUncached = true) String cachedFormat,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Cached ToJavaNode varargsToJavaNode,
                        @Cached GetArgNode getArgNode,
                        @Cached ExecuteConverterNode executeConverterNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode,
                        @Cached HashingStorageNodes.GetItemInteropNode getDictItemNode,
                        @Cached IsSubtypeWithoutFrameNode isSubtypeNode,
                        @Cached GetLazyClassNode getClassNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached WriteOutVarNode writeOutVarNode,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                doParsingExploded(argv, kwds, cachedFormat, kwdnames, varargs, varargsLib, varargsToJavaNode, getArgNode, executeConverterNode, isSubtypeNode, getClassNode, asNativePrimitiveNode,
                                asDoubleNode, transformExceptionToNativeNode, writeOutVarNode, raiseNode);
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                CompilerAsserts.neverPartOfCompilation();
                return 0;
            }
        }

        @Specialization(guards = "isDictOrNull(kwds)", replaces = "doSpecial", limit = "1")
        int doGeneric(PTuple argv, Object kwds, String format, Object[] kwdnames, Object varargs,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Cached ToJavaNode varargsToJavaNode,
                        @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached GetArgNode getArgNode,
                        @Cached ExecuteConverterNode executeConverterNode,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode,
                        @Cached HashingStorageNodes.GetItemInteropNode getDictItemNode,
                        @Cached IsSubtypeWithoutFrameNode isSubtypeNode,
                        @Cached GetLazyClassNode getClassNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached WriteOutVarNode writeOutVarNode,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                doParsingLoop(argv, kwds, format, kwdnames, varargs, varargsLib, varargsToJavaNode, getArgNode, executeConverterNode, isSubtypeNode, getClassNode, asNativePrimitiveNode, asDoubleNode,
                                transformExceptionToNativeNode, writeOutVarNode, raiseNode);
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                CompilerAsserts.neverPartOfCompilation();
                return 0;
            }
        }

        @ExplodeLoop
        private static void doParsingExploded(PTuple argv, Object kwds, String format, Object[] kwdnames, Object varargs,
                        InteropLibrary varargsLib,
                        ToJavaNode varargsToJavaNode,
                        GetArgNode getArgNode,
                        ExecuteConverterNode executeConverterNode,
                        IsSubtypeWithoutFrameNode isSubtypeNode,
                        GetLazyClassNode getClassNode,
                        AsNativePrimitiveNode asNativePrimitiveNode,
                        AsNativeDoubleNode asDoubleNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode,
                        WriteOutVarNode writeOutVarNode,
                        PRaiseNativeNode raiseNode)
                        throws InteropException, ParseArgumentsException {
            CompilerAsserts.partialEvaluationConstant(format.length());
            ParserState state = new ParserState();
            state.v = new PositionalArgStack(argv, null);
            for (int i = 0; i < format.length(); i++) {
                convertArg(state, kwds, format, i, kwdnames, varargs, varargsLib, varargsToJavaNode, getArgNode, executeConverterNode, isSubtypeNode, getClassNode, asNativePrimitiveNode, asDoubleNode,
                                transformExceptionToNativeNode, writeOutVarNode, raiseNode);
            }
        }

        private static void doParsingLoop(PTuple argv, Object kwds, String format, Object[] kwdnames, Object varargs,
                        InteropLibrary varargsLib,
                        ToJavaNode varargsToJavaNode,
                        GetArgNode getArgNode,
                        ExecuteConverterNode executeConverterNode,
                        IsSubtypeWithoutFrameNode isSubtypeNode,
                        GetLazyClassNode getClassNode,
                        AsNativePrimitiveNode asNativePrimitiveNode,
                        AsNativeDoubleNode asDoubleNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode,
                        WriteOutVarNode writeOutVarNode,
                        PRaiseNativeNode raiseNode)
                        throws InteropException, ParseArgumentsException {
            ParserState state = new ParserState();
            state.v = new PositionalArgStack(argv, null);
            for (int i = 0; i < format.length(); i++) {
                convertArg(state, kwds, format, i, kwdnames, varargs, varargsLib, varargsToJavaNode, getArgNode, executeConverterNode, isSubtypeNode, getClassNode, asNativePrimitiveNode, asDoubleNode,
                                transformExceptionToNativeNode, writeOutVarNode, raiseNode);
            }
        }

        private static void convertArg(ParserState state, Object kwds, String format, int format_idx, Object[] kwdnames, Object varargs,
                        InteropLibrary varargsLib,
                        ToJavaNode varargsToJavaNode,
                        GetArgNode getArgNode,
                        ExecuteConverterNode executeConverterNode,
                        IsSubtypeWithoutFrameNode isSubtypeNode,
                        GetLazyClassNode getClassNode,
                        AsNativePrimitiveNode asNativePrimitiveNode,
                        AsNativeDoubleNode asDoubleNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode,
                        WriteOutVarNode writeOutVarNode,
                        PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            Object arg;
            char c = format.charAt(format_idx);
            switch (c) {
                case 's':
                case 'z':
                case 'y':
                    break;
                case 'S':
                    break;
                case 'Y':
                    break;
                case 'u':
                case 'Z':
                    break;
                case 'U':
                    break;
                case 'e':
                    break;
                case 'b':
                    // C type: unsigned char
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            long ival = asNativePrimitiveNode.toInt64(arg);
                            if (ival < 0) {
                                CompilerDirectives.transferToInterpreter();
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.OverflowError, "unsigned byte integer is less than minimum");
                                throw ParseArgumentsException.raise();
                            } else if (ival > Byte.MAX_VALUE * 2 + 1) {
                                // TODO(fa) MAX_VALUE should be retrieved from Sulong
                                CompilerDirectives.transferToInterpreter();
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.OverflowError, "unsigned byte integer is greater than maximum");
                                throw ParseArgumentsException.raise();
                            }
                            writeOutVarNode.writeUInt8(varargs, state.out_index, ival);
                        } catch (PException e) {
                            CompilerDirectives.transferToInterpreter();
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'B':
                    // C type: unsigned char
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            long ival = asNativePrimitiveNode.toInt64(arg);
                            writeOutVarNode.writeUInt8(varargs, state.out_index, ival);
                        } catch (PException e) {
                            CompilerDirectives.transferToInterpreter();
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'h':
                    // C type: signed short int
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            long ival = asNativePrimitiveNode.toInt64(arg);
                            // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                            if (ival < Short.MIN_VALUE) {
                                CompilerDirectives.transferToInterpreter();
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.OverflowError, "signed short integer is less than minimum");
                                throw ParseArgumentsException.raise();
                            } else if (ival > Short.MAX_VALUE) {
                                CompilerDirectives.transferToInterpreter();
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.OverflowError, "signed short integer is greater than maximum");
                                throw ParseArgumentsException.raise();
                            }
                            writeOutVarNode.writeInt16(varargs, state.out_index, arg);
                        } catch (PException e) {
                            CompilerDirectives.transferToInterpreter();
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'H':
                    // C type: short int sized bitfield
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            long ival = asNativePrimitiveNode.toInt64(arg);
                            writeOutVarNode.writeInt16(varargs, state.out_index, arg);
                        } catch (PException e) {
                            CompilerDirectives.transferToInterpreter();
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'i':
                    // C type: signed int
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            long ival = asNativePrimitiveNode.toInt64(arg);
                            // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                            if (ival < Integer.MIN_VALUE) {
                                CompilerDirectives.transferToInterpreter();
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.OverflowError, "signed integer is less than minimum");
                                throw ParseArgumentsException.raise();
                            } else if (ival > Integer.MAX_VALUE) {
                                CompilerDirectives.transferToInterpreter();
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.OverflowError, "signed integer is greater than maximum");
                                throw ParseArgumentsException.raise();
                            }
                            writeOutVarNode.writeInt32(varargs, state.out_index, ival);
                        } catch (PException e) {
                            CompilerDirectives.transferToInterpreter();
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'I':
                    // C type: int sized bitfield
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            writeOutVarNode.writeUInt32(varargs, state.out_index, asNativePrimitiveNode.toInt64(arg));
                        } catch (PException e) {
                            CompilerDirectives.transferToInterpreter();
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'l':
                case 'k':
                case 'L':
                case 'K':
                case 'n':
                    // C type: signed short int
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        try {
                            writeOutVarNode.writeInt64(varargs, state.out_index, asNativePrimitiveNode.toInt64(arg));
                        } catch (PException e) {
                            transformExceptionToNativeNode.execute(null, e);
                            throw ParseArgumentsException.raise();
                        }
                    }
                    state.out_index++;
                    break;
                case 'c':
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {

                        writeOutVarNode.writeInt8(varargs, state.out_index, (float) asDoubleNode.execute(arg));
                    }
                    state.out_index++;
                    break;
                case 'C':
                    break;
                case 'f':
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        writeOutVarNode.writeFloat(varargs, state.out_index, (float) asDoubleNode.execute(arg));
                    }
                    state.out_index++;
                    break;
                case 'd':
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        writeOutVarNode.writeFloat(varargs, state.out_index, asDoubleNode.execute(arg));
                    }
                    state.out_index++;
                    break;
                case 'D':
                    // TODO implement complex case
                    raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.TypeError, "converting complex arguments not implemented, yet");
                    throw ParseArgumentsException.raise();
                case 'O':
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (isLookahead(format, format_idx, '!')) {
// format_idx++;
                        if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                            Object typeObject = PyTruffleVaArg(varargs, state.out_index, varargsLib, varargsToJavaNode);
                            state.out_index++;
                            assert PGuards.isClass(typeObject);
                            if (!isSubtypeNode.executeWithGlobalState(getClassNode.execute(arg), typeObject)) {
                                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.TypeError, "expected object of type %s, got %p", typeObject, arg);
                                throw ParseArgumentsException.raise();
                            }
                            writeOutVarNode.writeObject(varargs, state.out_index, arg);
                        }
                        state.out_index++;
                    } else if (isLookahead(format, format_idx, '&')) {
// format_idx++;
                        Object converter = PyTruffleVaArg(varargs, state.out_index, varargsLib, varargsToJavaNode);
                        state.out_index++;
                        if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                            Object output = PyTruffleVaArg(varargs, state.out_index, varargsLib, varargsToJavaNode);
                            executeConverterNode.execute(state.out_index, converter, arg, output);
                        }
                        state.out_index++;
                    } else {
                        if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                            writeOutVarNode.writeObject(varargs, state.out_index, arg);
                        }
                        state.out_index++;
                    }
                    break;
                case 'w': /* "w*": memory buffer, read-write access */
                    break;
                case 'p':
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (!PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        writeOutVarNode.writeInt32(varargs, state.out_index, castToBoolean(arg));
                    }
                    state.out_index++;
                    break;
                case '(':
                    arg = getArgNode.execute(state.v, kwds, kwdnames, state.rest_keywords_only);
                    if (PyTruffle_SkipOptionalArg(arg, state.rest_optional)) {
                        state.out_index++;
                    } else {
                        // n.b.: there is a small gap in this check: In theory, there could be
                        // native subclass of tuple. But since we do not support this anyway, the
                        // instanceof test is just the most efficient way to do it.
                        if (!(arg instanceof PTuple)) {
                            raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.TypeError, "expected tuple, got %p", arg);
                            throw ParseArgumentsException.raise();
                        }
                        PTuple nestedArgv = (PTuple) arg;
                        state.v = new PositionalArgStack(nestedArgv, state.v);
                    }
                    break;
                case ')':
                    if (state.v.prev == null) {
                        raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, "')' without '(' in argument parsing");
                        throw ParseArgumentsException.raise();
                    } else {
                        state.v = state.v.prev;
                    }
                    break;
                case '|':
                    state.rest_optional = true;
                    break;
                case '$':
                    state.rest_keywords_only = true;
                    break;
                case '!':
                case '&':
                    // always skip '!' and '&' because these will be handled in the look-ahead of
                    // the major specifiers like 'O'
                    break;
                case ':':
                    // TODO: adapt error message based on string after this
                    assert false : "got ':' but this should be trimmed from the format string";
                    return;
                case ';':
                    // TODO: adapt error message based on string after this
                    assert false : "got ';' but this should be trimmed from the format string";
                    return;
                default:
                    raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.TypeError, "unrecognized format char in arguments parsing: %c", c);
                    throw ParseArgumentsException.raise();
            }
        }

        private static boolean isLookahead(String format, int format_idx, char expected) {
            return format_idx + 1 < format.length() && format.charAt(format_idx + 1) == expected;
        }

        private static boolean PyTruffle_SkipOptionalArg(Object arg, boolean optional) {
            return arg == null && optional;
        }

        private static Object PyTruffleVaArg(Object varargs, int out_index, InteropLibrary varargsLib, ToJavaNode toJavaNode) throws InvalidArrayIndexException, UnsupportedMessageException {
            try {
                return toJavaNode.execute(varargsLib.readArrayElement(varargs, out_index));
            } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw e;
            }
        }

        private static Object castToBoolean(Object value) {
            // TODO refactor 'CastToBooleanNode' to provide uncached version and use it
            return LookupAndCallUnaryDynamicNode.getUncached().executeObject(value, SpecialMethodNames.__BOOL__);
        }

        @ValueType
        private static final class ParserState {
            private int out_index;
            private boolean rest_optional;
            private boolean rest_keywords_only;
            private PositionalArgStack v;
        }

        static boolean isDictOrNull(Object object) {
            return object == null || object instanceof PDict;
        }
    }

    @ValueType
    static final class PositionalArgStack {
        private final PTuple argv;
        private int argnum;
        private final PositionalArgStack prev;

        PositionalArgStack(PTuple argv, PositionalArgStack prev) {
            this.argv = argv;
            this.prev = prev;
        }
    }

    @GenerateUncached
    abstract static class GetArgNode extends Node {

        public abstract Object execute(PositionalArgStack p, Object kwds, Object[] kwdnames, boolean keywords_only);

        @Specialization
        Object doGeneric(PositionalArgStack p, Object kwds, Object[] kwdnames, boolean keywords_only,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode,
                        @Cached HashingStorageNodes.GetItemInteropNode getDictItemNode) {

            Object out = null;
            if (!keywords_only) {
                int l = lenNode.execute(p.argv);
                if (p.argnum < l) {
                    out = getItemNode.execute(getSequenceStorageNode.execute(p.argv), p.argnum);
                }
            }
            // only the bottom argstack can have keyword names
            if (kwds != null && out == null && p.prev == null && kwdnames != null) {
                Object kwdname = kwdnames[p.argnum];
                if (kwdname != null) {
                    // the cast to PDict is safe because either it is null or a PDict (ensured by
                    // the guards)
                    out = getDictItemNode.executeWithGlobalState(getDictStorageNode.execute((PDict) kwds), kwdname);
                }
            }
            p.argnum++;
            return out;
        }
    }

    @GenerateUncached
    abstract static class ExecuteConverterNode extends Node {

        public abstract void execute(int index, Object converter, Object inputArgument, Object outputArgument) throws ParseArgumentsException;

        @Specialization(guards = "cachedIndex == index", limit = "3")
        static void doExecuteConverterCached(@SuppressWarnings("unused") int index, Object converter, Object inputArgument, Object outputArgument,
                        @Cached(value = "index", allowUncached = true) int cachedIndex,
                        @CachedLibrary("converter") InteropLibrary converterLib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib,
                        @Exclusive @Cached ToSulongNode toSulongNode,
                        @Exclusive @Cached PRaiseNativeNode raiseNode,
                        @Exclusive @Cached ConverterCheckResultNode checkResultNode) throws ParseArgumentsException {
            try {
                Object result = converterLib.execute(converter, toSulongNode.execute(inputArgument), outputArgument);
                if (resultLib.fitsInInt(result)) {
                    checkResultNode.execute(resultLib.asInt(result));
                    return;
                }
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, "calling argument converter failed; unexpected return value %s", result);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, "calling argument converter failed; incompatible parameters '%s'", e.getSuppliedValues());
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, "calling argument converter failed; expected %d but got %d parameters.", e.getExpectedArity(),
                                e.getActualArity());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, "argument converted is not executable");
            }
            throw ParseArgumentsException.raise();
        }
    }

    @GenerateUncached
    abstract static class ConverterCheckResultNode extends Node {

        public abstract void execute(int statusCode) throws ParseArgumentsException;

        @Specialization(guards = "statusCode != 0")
        static void doSuccess(@SuppressWarnings("unused") int statusCode) {
            // all fine
        }

        @Specialization(guards = "statusCode == 0")
        static void doError(int statusCode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PRaiseNativeNode raiseNode) throws ParseArgumentsException {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (!errOccurred) {
                // converter should have set exception
                raiseNode.raiseInt(null, 0, PythonBuiltinClassType.TypeError, "converter function failed to set an error on failure");
            }
            throw ParseArgumentsException.raise();
        }
    }

    @GenerateUncached
    abstract static class WriteOutVarNode extends Node {
        static final int TYPE_I8 = 0;
        static final int TYPE_I16 = 1;
        static final int TYPE_I32 = 2;
        static final int TYPE_I64 = 3;
        static final int TYPE_U8 = 4;
        static final int TYPE_U16 = 5;
        static final int TYPE_U32 = 6;
        static final int TYPE_U64 = 7;
        static final int TYPE_DOUBLE = 8;
        static final int TYPE_FLOAT = 9;
        static final int TYPE_VOIDPTR = 10;

        public final void writeUInt8(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_U8, value);
        }

        public final void writeInt8(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_I8, value);
        }

        public final void writeUInt16(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_U16, value);
        }

        public final void writeInt16(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_I16, value);
        }

        public final void writeInt32(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_I32, value);
        }

        public final void writeUInt32(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_U32, value);
        }

        public final void writeInt64(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_I64, value);
        }

        public final void writeUInt64(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_U64, value);
        }

        public final void writeFloat(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_FLOAT, value);
        }

        public final void writeDouble(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_DOUBLE, value);
        }

        public final void writeObject(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_VOIDPTR, value);
        }

        public abstract void execute(Object varargs, int outIndex, int accessType, Object value) throws InteropException;

        @Specialization(guards = "accessType == TYPE_U8", limit = "1")
        void doUInt8(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "u8", value);
        }

        @Specialization(guards = "accessType == TYPE_I8", limit = "1")
        void doInt8(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "i8", (byte) value);
        }

        @Specialization(guards = "accessType == TYPE_I16", limit = "1")
        void doInt16(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException {
            try {
                long ival = asNativePrimitiveNode.toInt64(value);
                // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                if (ival < Short.MIN_VALUE) {
                    throw raiseNode.raise(PythonBuiltinClassType.OverflowError, "signed short integer is less than minimum");
                } else if (ival > Short.MAX_VALUE) {
                    throw raiseNode.raise(PythonBuiltinClassType.OverflowError, "signed short integer is greater than maximum");
                }
                doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "i16", value);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
                throw ParseArgumentsException.raise();
            }
        }

        @Specialization(guards = "accessType == TYPE_U16", limit = "1")
        void doInt16(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("transformExceptionToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException {
            try {
                long ival = asNativePrimitiveNode.toInt64(value);
                doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "u16", value);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, e);
                throw ParseArgumentsException.raise();
            }
        }

        @Specialization(guards = "accessType == TYPE_I32", limit = "1")
        void doInt(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "i32", asNativePrimitiveNode.toInt32(value));
        }

        @Specialization(guards = "accessType == TYPE_I64", limit = "1")
        void doLong(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "i64", asNativePrimitiveNode.toInt64(value));
        }

        @Specialization(guards = "accessType == TYPE_VOIDPTR", limit = "1")
        void doObject(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached ToSulongNode toSulongNode) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, "ptr", toSulongNode.execute(value));
        }

        private static void doAccess(InteropLibrary varargsLib, InteropLibrary outVarPtrLib, InteropLibrary outVarLib, Object varargs, int outIndex, String accessor, Object value)
                        throws InteropException {
            try {
                Object outVarPtr = varargsLib.readArrayElement(varargs, outIndex);
                Object outVar = outVarPtrLib.readMember(outVarPtr, "content");
                outVarLib.writeMember(outVar, accessor, value);
            } catch (InvalidArrayIndexException | UnsupportedMessageException | UnsupportedTypeException | UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreter();
                throw e;
            }
        }

        static boolean isI8(String accessor) {
            return "i8".equals(accessor);
        }
    }

    static final class ParseArgumentsException extends ControlFlowException {
        private static final long serialVersionUID = 1L;

        static ParseArgumentsException raise() {
            CompilerDirectives.transferToInterpreter();
            throw new ParseArgumentsException();
        }
    }
}
