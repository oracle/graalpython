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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_BUFFER_R;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_BUFFER_RW;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativeComplexNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.PCallCExtFunction;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNodeFactory.ConvertArgNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNodeFactory.ParseTupleAndKeywordsNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class CExtParseArgumentsNode {
    static final char FORMAT_LOWER_S = 's';
    static final char FORMAT_UPPER_S = 'S';
    static final char FORMAT_LOWER_Z = 'z';
    static final char FORMAT_UPPER_Z = 'Z';
    static final char FORMAT_LOWER_Y = 'y';
    static final char FORMAT_UPPER_Y = 'Y';
    static final char FORMAT_LOWER_U = 'u';
    static final char FORMAT_UPPER_U = 'U';
    static final char FORMAT_LOWER_E = 'e';
    static final char FORMAT_LOWER_B = 'b';
    static final char FORMAT_UPPER_B = 'B';
    static final char FORMAT_LOWER_H = 'h';
    static final char FORMAT_UPPER_H = 'H';
    static final char FORMAT_LOWER_I = 'i';
    static final char FORMAT_UPPER_I = 'I';
    static final char FORMAT_LOWER_L = 'l';
    static final char FORMAT_UPPER_L = 'L';
    static final char FORMAT_LOWER_K = 'k';
    static final char FORMAT_UPPER_K = 'K';
    static final char FORMAT_LOWER_N = 'n';
    static final char FORMAT_LOWER_C = 'c';
    static final char FORMAT_UPPER_C = 'C';
    static final char FORMAT_LOWER_F = 'f';
    static final char FORMAT_LOWER_D = 'd';
    static final char FORMAT_UPPER_D = 'D';
    static final char FORMAT_UPPER_O = 'O';
    static final char FORMAT_LOWER_W = 'w';
    static final char FORMAT_LOWER_P = 'p';
    static final char FORMAT_PAR_OPEN = '(';

    @GenerateUncached
    @ReportPolymorphism
    @ImportStatic(PGuards.class)
    public abstract static class ParseTupleAndKeywordsNode extends Node {

        public abstract int execute(String funName, Object argv, Object kwds, Object format, Object kwdnames, Object varargs, CExtContext nativeContext);

        @Specialization(guards = {"isDictOrNull(kwds)", "cachedFormat.equals(format)", "cachedFormat.length() <= 8"}, limit = "5")
        int doSpecial(String funName, PTuple argv, Object kwds, @SuppressWarnings("unused") String format, Object kwdnames, Object varargs, CExtContext nativeConext,
                        @Cached(value = "format", allowUncached = true) @SuppressWarnings("unused") String cachedFormat,
                        @Cached(value = "getChars(format)", allowUncached = true, dimensions = 1) char[] chars,
                        @Cached("createConvertArgNodes(cachedFormat)") ConvertArgNode[] convertArgNodes,
                        @Cached HashingCollectionNodes.LenNode kwdsLenNode,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                PDict kwdsDict = null;
                if (kwds != null && kwdsLenNode.execute((PDict) kwds) != 0) {
                    kwdsDict = (PDict) kwds;
                }
                doParsingExploded(funName, argv, kwdsDict, chars, kwdnames, varargs, nativeConext, convertArgNodes, raiseNode);
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                return 0;
            }
        }

        @Specialization(guards = "isDictOrNull(kwds)", replaces = "doSpecial")
        int doGeneric(String funName, PTuple argv, Object kwds, String format, Object kwdnames, Object varargs, CExtContext nativeContext,
                        @Cached ConvertArgNode convertArgNode,
                        @Cached HashingCollectionNodes.LenNode kwdsLenNode,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                char[] chars = getChars(format);
                PDict kwdsDict = null;
                if (kwds != null && kwdsLenNode.execute((PDict) kwds) != 0) {
                    kwdsDict = (PDict) kwds;
                }
                ParserState state = new ParserState(funName, new PositionalArgStack(argv, null), nativeContext);
                for (int i = 0; i < format.length(); i++) {
                    state = convertArg(state, kwdsDict, chars, i, kwdnames, varargs, convertArgNode, raiseNode);
                }
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                return 0;
            }
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        private static void doParsingExploded(String funName, PTuple argv, Object kwds, char[] chars, Object kwdnames, Object varargs, CExtContext nativeContext,
                        ConvertArgNode[] convertArgNodes, PRaiseNativeNode raiseNode)
                        throws InteropException, ParseArgumentsException {
            CompilerAsserts.partialEvaluationConstant(chars.length);
            ParserState state = new ParserState(funName, new PositionalArgStack(argv, null), nativeContext);
            for (int i = 0; i < chars.length; i++) {
                state = convertArg(state, kwds, chars, i, kwdnames, varargs, convertArgNodes[i], raiseNode);
            }
        }

        private static ParserState convertArg(ParserState state, Object kwds, char[] format, int format_idx, Object kwdnames, Object varargs, ConvertArgNode convertArgNode,
                        PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            char c = format[format_idx];
            switch (c) {
                case FORMAT_LOWER_S:
                case FORMAT_LOWER_Z:
                case FORMAT_LOWER_Y:
                case FORMAT_UPPER_S:
                case FORMAT_UPPER_Y:
                case FORMAT_LOWER_U:
                case FORMAT_UPPER_Z:
                case FORMAT_UPPER_U:
                case FORMAT_LOWER_E:
                case FORMAT_LOWER_B:
                case FORMAT_UPPER_B:
                case FORMAT_LOWER_H:
                case FORMAT_UPPER_H:
                case FORMAT_LOWER_I:
                case FORMAT_UPPER_I:
                case FORMAT_LOWER_L:
                case FORMAT_LOWER_K:
                case FORMAT_UPPER_L:
                case FORMAT_UPPER_K:
                case FORMAT_LOWER_N:
                case FORMAT_LOWER_C:
                case FORMAT_UPPER_C:
                case FORMAT_LOWER_F:
                case FORMAT_LOWER_D:
                case FORMAT_UPPER_D:
                case FORMAT_UPPER_O:
                case FORMAT_LOWER_W:
                case FORMAT_LOWER_P:
                case FORMAT_PAR_OPEN:
                    return convertArgNode.execute(state, kwds, c, format, format_idx, kwdnames, varargs);
                case ')':
                    if (state.v.prev == null) {
                        CompilerDirectives.transferToInterpreter();
                        raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.LEFT_BRACKET_WO_RIGHT_BRACKET_IN_ARG);
                        throw ParseArgumentsException.raise();
                    } else {
                        return state.close();
                    }
                case '|':
                    return state.restOptional();
                case '$':
                    return state.restKeywordsOnly();
                case '!':
                case '&':
                case '*':
                case '#':
                    // always skip '!', '&', '*', and '#' because these will be handled in the
                    // look-ahead of the major specifiers like 'O' or 's'
                    return state;
                case ':':
                    // We extract and remove the function name already in the calling builtin. So
                    // this char may not occur here.
                    assert false : "got ':' but this should be trimmed from the format string";
                    return state;
                case ';':
                    // We extract and remove the function name already in the calling builtin. So
                    // this char may not occur here.
                    assert false : "got ';' but this should be trimmed from the format string";
                    return state;
                default:
                    raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.UNRECOGNIZED_FORMAT_CHAR, c);
                    throw ParseArgumentsException.raise();
            }
        }

        static ConvertArgNode[] createConvertArgNodes(String format) {
            ConvertArgNode[] convertArgNodes = new ConvertArgNode[format.length()];
            for (int i = 0; i < convertArgNodes.length; i++) {
                convertArgNodes[i] = ConvertArgNodeGen.create();
            }
            return convertArgNodes;
        }

        static boolean isDictOrNull(Object object) {
            return object == null || object instanceof PDict;
        }

        static char[] getChars(String format) {
            return format.toCharArray();
        }

        @Override
        public Node copy() {
            // create a new uninitialized node
            return ParseTupleAndKeywordsNodeGen.create();
        }
    }

    /**
     * The parser state that captures the current output variable index, if arguments are optional,
     * if arguments will be taken from the keywords dictionary only, and the current arguments
     * tuple.<br/>
     * The state is implemented in an immutable way since every specifier should get his unique
     * state.
     */
    @ValueType
    static final class ParserState {
        private final String funName;
        private final int outIndex;
        private final boolean restOptional;
        private final boolean restKeywordsOnly;
        private final PositionalArgStack v;
        private final CExtContext nativeContext;

        ParserState(String funName, PositionalArgStack v, CExtContext nativeContext) {
            this(funName, 0, false, false, v, nativeContext);
        }

        private ParserState(String funName, int outIndex, boolean restOptional, boolean restKeywordsOnly, PositionalArgStack v, CExtContext nativeContext) {
            this.funName = funName;
            this.outIndex = outIndex;
            this.restOptional = restOptional;
            this.restKeywordsOnly = restKeywordsOnly;
            this.v = v;
            this.nativeContext = nativeContext;
        }

        ParserState incrementOutIndex() {
            return new ParserState(funName, outIndex + 1, restOptional, restKeywordsOnly, v, nativeContext);
        }

        ParserState restOptional() {
            return new ParserState(funName, outIndex, true, restKeywordsOnly, v, nativeContext);
        }

        ParserState restKeywordsOnly() {
            return new ParserState(funName, outIndex, restOptional, true, v, nativeContext);
        }

        ParserState open(PositionalArgStack nestedArgs) {
            return new ParserState(funName, outIndex, restOptional, true, nestedArgs, nativeContext);
        }

        ParserState close() {
            return new ParserState(funName, outIndex, restOptional, true, v.prev, nativeContext);
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

    /**
     * This node does the conversion of a single specifier and is comparable to CPython's
     * {@code convertsimple} function. Each specifier is implemented in a separate specialization
     * since the different specifiers need a very different set of helper nodes.
     */
    @GenerateUncached
    @ImportStatic(CExtParseArgumentsNode.class)
    abstract static class ConvertArgNode extends Node {
        public abstract ParserState execute(ParserState state, Object kwds, char c, char[] format, int format_idx, Object kwdnames, Object varargs) throws InteropException, ParseArgumentsException;

        static boolean isCStringSpecifier(char c) {
            return c == FORMAT_LOWER_S || c == FORMAT_LOWER_Z;
        }

        @Specialization(guards = "c == FORMAT_LOWER_Y")
        static ParserState doBufferR(ParserState stateIn, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached PCallCExtFunction callGetBufferRwNode,
                        @Cached(value = "createTN(stateIn)", uncached = "getUncachedTN(stateIn)") CExtToNativeNode argToSulongNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            ParserState state = stateIn;
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (isLookahead(format, format_idx, '*')) {
                /* format_idx++; */
                // 'y*'; output to 'Py_buffer*'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object pybufferPtr = getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                    getbuffer(state.nativeContext, callGetBufferRwNode, raiseNode, argToSulongNode.execute(arg), pybufferPtr, true);
                }
            } else {
                // TODO(fa) convertbuffer: create a temporary 'Py_buffer' struct, call
                // 'get_buffer_r' and output the buffer's data pointer
                getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                if (isLookahead(format, format_idx, '#')) {
                    /* format_idx++; */
                    // 'y#'
                    // TODO(fa) additionally store size
                    getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                    state = state.incrementOutIndex();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "isCStringSpecifier(c)")
        static ParserState doCString(ParserState stateIn, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached AsCharPointerNode asCharPointerNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached StringLenNode stringLenNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Cached(value = "createTN(stateIn)", uncached = "getUncachedTN(stateIn)") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            ParserState state = stateIn;
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            boolean z = c == FORMAT_LOWER_Z;
            if (isLookahead(format, format_idx, '*')) {
                /* format_idx++; */
                // 's*' or 'z*'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                    // TODO(fa) create Py_buffer
                }
            } else if (isLookahead(format, format_idx, '#')) {
                /* format_idx++; */
                // 's#' or 'z#'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    if (z && PGuards.isPNone(arg)) {
                        writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(getNativeNullNode.execute()));
                        state = state.incrementOutIndex();
                        writeOutVarNode.writeInt32(varargs, state.outIndex, 0);
                    } else if (PGuards.isString(arg)) {
                        // TODO(fa) we could use CStringWrapper to do the copying lazily
                        writeOutVarNode.writePyObject(varargs, state.outIndex, asCharPointerNode.execute(arg));
                        state = state.incrementOutIndex();
                        writeOutVarNode.writeInt64(varargs, state.outIndex, (long) stringLenNode.execute(arg));
                    } else {
                        throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_GOT_P, z ? "str or None" : "str", arg);
                    }
                }
            } else {
                // 's' or 'z'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    if (z && PGuards.isPNone(arg)) {
                        writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(getNativeNullNode.execute()));
                    } else if (PGuards.isString(arg)) {
                        // TODO(fa) we could use CStringWrapper to do the copying lazily
                        writeOutVarNode.writePyObject(varargs, state.outIndex, asCharPointerNode.execute(arg));
                    } else {
                        throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_GOT_P, z ? "str or None" : "str", arg);
                    }
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_S")
        static ParserState doBytes(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Cached(value = "createTN(state)", uncached = "getUncachedTN(state)") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                if (isBytesProfile.profileClass(plib.getLazyPythonClass(arg), PythonBuiltinClassType.PBytes)) {
                    writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(arg));
                } else {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_NOT_P, arg);
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_Y")
        static ParserState doByteArray(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Cached(value = "createTN(state)", uncached = "getUncachedTN(state)") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                if (isBytesProfile.profileClass(plib.getLazyPythonClass(arg), PythonBuiltinClassType.PByteArray)) {
                    writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(arg));
                } else {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_NOT_P, "bytearray", arg);
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_U")
        static ParserState doUnicode(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Cached(value = "createTN(state)", uncached = "getUncachedTN(state)") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                if (isBytesProfile.profileClass(plib.getLazyPythonClass(arg), PythonBuiltinClassType.PString)) {
                    writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(arg));
                } else {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_NOT_P, "str", arg);
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_E")
        static ParserState doEncodedString(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, @SuppressWarnings("unused") Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                throw raise(raiseNode, TypeError, ErrorMessages.ESTAR_FORMAT_SPECIFIERS_NOT_ALLOWED, arg);
            }
            return state;
        }

        @Specialization(guards = "c == FORMAT_LOWER_B")
        static ParserState doUnsignedByte(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            // C type: unsigned char
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                    if (ival < 0) {
                        throw raise(raiseNode, OverflowError, ErrorMessages.UNSIGNED_BYTE_INT_LESS_THAN_MIN);
                    } else if (ival > Byte.MAX_VALUE * 2 + 1) {
                        // TODO(fa) MAX_VALUE should be retrieved from Sulong
                        throw raise(raiseNode, OverflowError, ErrorMessages.UNSIGNED_BYTE_INT_GREATER_THAN_MAX);
                    }
                    writeOutVarNode.writeUInt8(varargs, state.outIndex, ival);
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_B")
        static ParserState doUnsignedByteBitfield(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format,
                        @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: unsigned char
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    writeOutVarNode.writeUInt8(varargs, state.outIndex, asNativePrimitiveNode.toInt64(arg, false));
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_H")
        static ParserState doShortInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            // C type: signed short int
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                    // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                    if (ival < Short.MIN_VALUE) {
                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_SHORT_INT_LESS_THAN_MIN);
                    } else if (ival > Short.MAX_VALUE) {
                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_SHORT_INT_GREATER_THAN_MAX);
                    }
                    writeOutVarNode.writeInt16(varargs, state.outIndex, ival);
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_H")
        static ParserState doUnsignedShortInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: short int sized bitfield
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    writeOutVarNode.writeInt16(varargs, state.outIndex, asNativePrimitiveNode.toInt64(arg, false));
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_I")
        static ParserState doSignedInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            // C type: signed int
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                    // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                    if (ival < Integer.MIN_VALUE) {
                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_INT_LESS_THAN_MIN);
                    } else if (ival > Integer.MAX_VALUE) {
                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_INT_GREATER_THAN_MAX);
                    }
                    writeOutVarNode.writeInt32(varargs, state.outIndex, ival);
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_I")
        static ParserState doUnsignedInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: int sized bitfield
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    writeOutVarNode.writeUInt32(varargs, state.outIndex, asNativePrimitiveNode.toInt64(arg, false));
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        static boolean isLongSpecifier(char c) {
            return c == FORMAT_LOWER_L || c == FORMAT_UPPER_L;
        }

        @Specialization(guards = "isLongSpecifier(c)")
        static ParserState doLong(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: signed long and signed long long
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    writeOutVarNode.writeInt64(varargs, state.outIndex, asNativePrimitiveNode.toInt64(arg, true));
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        static boolean isLongBitfieldSpecifier(char c) {
            return c == FORMAT_LOWER_K || c == FORMAT_UPPER_K;
        }

        @Specialization(guards = "isLongBitfieldSpecifier(c)")
        static ParserState doUnsignedLong(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: unsigned long and unsigned long long
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    writeOutVarNode.writeUInt64(varargs, state.outIndex, asNativePrimitiveNode.toUInt64(arg, false));
                } catch (PException e) {
                    CompilerDirectives.transferToInterpreter();
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_N")
        static ParserState doPySsizeT(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: signed short int
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    // TODO(fa): AsNativePrimitiveNode coerces using '__int__', but here we must use
                    // '__index__'
                    writeOutVarNode.writeInt64(varargs, state.outIndex, asNativePrimitiveNode.toInt64(arg, true));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_C")
        static ParserState doByteFromBytesOrBytearray(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format,
                        @SuppressWarnings("unused") int format_idx, Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                SequenceStorage s = null;
                if (arg instanceof PBytes) {
                    s = ((PBytes) arg).getSequenceStorage();
                } else if (arg instanceof PByteArray) {
                    s = ((PByteArray) arg).getSequenceStorage();
                }

                if (s != null && lenNode.execute(s) == 1) {
                    writeOutVarNode.writeInt8(varargs, state.outIndex, getItemNode.execute(s, 0));
                } else {
                    throw raise(raiseNode, TypeError, ErrorMessages.MUST_BE_BYTE_STRING_LEGTH1_NOT_P, arg);
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_C")
        static ParserState doIntFromString(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached StringLenNode stringLenNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                // TODO(fa): There could be native subclasses (i.e. the Java type would not be
                // 'String' or 'PString') but we do currently not support this.
                if (!(PGuards.isString(arg) && stringLenNode.execute(arg) == 1)) {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_UNICODE_CHAR_NOT_P, arg);
                }
                // TODO(fa) use the sequence lib to get the character once available
                char singleChar;
                if (arg instanceof String) {
                    singleChar = ((String) arg).charAt(0);
                } else if (arg instanceof PString) {
                    singleChar = charFromPString(arg);
                } else {
                    throw raise(raiseNode, SystemError, ErrorMessages.UNSUPPORTED_STR_TYPE, arg.getClass());
                }
                writeOutVarNode.writeInt32(varargs, state.outIndex, (int) singleChar);
            }
            return state.incrementOutIndex();
        }

        @TruffleBoundary
        private static char charFromPString(Object arg) {
            return ((PString) arg).getCharSequence().charAt(0);
        }

        @Specialization(guards = "c == FORMAT_LOWER_F")
        static ParserState doFloat(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                writeOutVarNode.writeFloat(varargs, state.outIndex, (float) asDoubleNode.execute(arg));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_D")
        static ParserState doDouble(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                writeOutVarNode.writeDouble(varargs, state.outIndex, asDoubleNode.execute(arg));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_D")
        static ParserState doComplex(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativeComplexNode asComplexNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                writeOutVarNode.writeComplex(varargs, state.outIndex, asComplexNode.execute(arg));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_O")
        static ParserState doObject(ParserState stateIn, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached ExecuteConverterNode executeConverterNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached(value = "createTJ(stateIn)", uncached = "getUncachedTJ(stateIn)") CExtToJavaNode typeToJavaNode,
                        @Cached(value = "createTN(stateIn)", uncached = "getUncachedTN(stateIn)") CExtToNativeNode toNativeNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            ParserState state = stateIn;
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (isLookahead(format, format_idx, '!')) {
                /* format_idx++; */
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object typeObject = typeToJavaNode.execute(getVaArgNode.getPyObjectPtr(varargs, state.outIndex));
                    state = state.incrementOutIndex();
                    assert PGuards.isClass(typeObject, lib);
                    if (!isSubtypeNode.execute(plib.getLazyPythonClass(arg), typeObject)) {
                        raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.EXPECTED_OBJ_TYPE_S_GOT_P, typeObject, arg);
                        throw ParseArgumentsException.raise();
                    }
                    writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(arg));
                }
            } else if (isLookahead(format, format_idx, '&')) {
                /* format_idx++; */
                Object converter = getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                state = state.incrementOutIndex();
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object output = getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                    executeConverterNode.execute(state.nativeContext.getSupplier(), state.outIndex, converter, arg, output);
                }
            } else {
                if (!skipOptionalArg(arg, state.restOptional)) {
                    writeOutVarNode.writePyObject(varargs, state.outIndex, toNativeNode.execute(arg));
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_W")
        static ParserState doBufferRW(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached PCallCExtFunction callGetBufferRwNode,
                        @Cached(value = "createTN(state)", uncached = "getUncachedTN(state)") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!isLookahead(format, format_idx, '*')) {
                throw raise(raiseNode, TypeError, ErrorMessages.INVALID_USE_OF_W_FORMAT_CHAR);

            }
            if (!skipOptionalArg(arg, state.restOptional)) {
                Object pybufferPtr = getVaArgNode.getPyObjectPtr(varargs, state.outIndex);
                getbuffer(state.nativeContext, callGetBufferRwNode, raiseNode, toNativeNode.execute(arg), pybufferPtr, false);
            }
            return state.incrementOutIndex();
        }

        private static void getbuffer(CExtContext nativeContext, PCallCExtFunction callGetBufferRwNode, PRaiseNativeNode raiseNode, Object sulongArg, Object pybufferPtr, boolean readOnly)
                        throws ParseArgumentsException {
            String funName = readOnly ? FUN_GET_BUFFER_R : FUN_GET_BUFFER_RW;
            Object rc = callGetBufferRwNode.call(nativeContext, funName, sulongArg, pybufferPtr);
            if (!(rc instanceof Number)) {
                throw raise(raiseNode, SystemError, ErrorMessages.RETURNED_UNEXPECTE_RET_CODE_EXPECTED_INT_BUT_WAS_S, funName, rc.getClass());
            }
            int i = intValue((Number) rc);
            if (i == -1) {
                throw raise(raiseNode, TypeError, ErrorMessages.READ_WRITE_BYTELIKE_OBJ);
            } else if (i == -2) {
                throw raise(raiseNode, TypeError, ErrorMessages.CONTIGUOUS_BUFFER);
            }
        }

        @TruffleBoundary
        private static int intValue(Number rc) {
            return rc.intValue();
        }

        @Specialization(guards = "c == FORMAT_LOWER_P")
        static ParserState doPredicate(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                // TODO(fa) refactor 'CastToBooleanNode' to provide uncached version and use it
                writeOutVarNode.writeInt32(varargs, state.outIndex, LookupAndCallUnaryDynamicNode.getUncached().executeObject(arg, SpecialMethodNames.__BOOL__));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_PAR_OPEN")
        static ParserState doPredicate(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") char[] format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, @SuppressWarnings("unused") Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (skipOptionalArg(arg, state.restOptional)) {
                return state.incrementOutIndex();
            } else {
                // n.b.: there is a small gap in this check: In theory, there could be
                // native subclass of tuple. But since we do not support this anyway, the
                // instanceof test is just the most efficient way to do it.
                if (!(arg instanceof PTuple)) {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_GOT_P, "tuple", arg);
                }
                return state.open(new PositionalArgStack((PTuple) arg, state.v));
            }
        }

        private static ParseArgumentsException raise(PRaiseNativeNode raiseNode, PythonBuiltinClassType errType, String format, Object... arguments) {
            CompilerDirectives.transferToInterpreter();
            raiseNode.executeInt(null, 0, errType, format, arguments);
            throw ParseArgumentsException.raise();
        }

        private static boolean skipOptionalArg(Object arg, boolean optional) {
            return arg == null && optional;
        }

        private static boolean isLookahead(char[] format, int format_idx, char expected) {
            return format_idx + 1 < format.length && format[format_idx + 1] == expected;
        }

        public static CExtToNativeNode createTN(ParserState state) {
            return state.nativeContext.getSupplier().createToNativeNode();
        }

        public static CExtToNativeNode getUncachedTN(ParserState state) {
            return state.nativeContext.getSupplier().getUncachedToNativeNode();
        }

        public static CExtToJavaNode createTJ(ParserState state) {
            return state.nativeContext.getSupplier().createToJavaNode();
        }

        public static CExtToJavaNode getUncachedTJ(ParserState state) {
            return state.nativeContext.getSupplier().getUncachedToJavaNode();
        }
    }

    /**
     * Gets a single argument from the arguments tuple or from the keywords dictionary.
     */
    @GenerateUncached
    abstract static class GetArgNode extends Node {

        public abstract Object execute(ParserState state, Object kwds, Object kwdnames, boolean keywords_only) throws InteropException;

        @Specialization(guards = {"kwds == null", "!keywordsOnly"})
        @SuppressWarnings("unused")
        static Object doNoKeywords(ParserState state, Object kwds, Object kwdnames, boolean keywordsOnly,
                        @Shared("lenNode") @Cached SequenceNodes.LenNode lenNode,
                        @Shared("getSequenceStorageNode") @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Shared("getItemNode") @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) {

            Object out = null;
            assert !keywordsOnly;
            int l = lenNode.execute(state.v.argv);
            if (state.v.argnum < l) {
                out = getItemNode.execute(getSequenceStorageNode.execute(state.v.argv), state.v.argnum);
            }
            if (out == null && !state.restOptional) {
                raiseNode.raiseIntWithoutFrame(0, TypeError, "%s missing required argument (pos %d)", state.funName, state.v.argnum);
                throw ParseArgumentsException.raise();
            }
            state.v.argnum++;
            return out;
        }

        @Specialization(replaces = "doNoKeywords")
        static Object doGeneric(ParserState state, Object kwds, Object kwdnames, boolean keywordsOnly,
                        @Shared("lenNode") @Cached SequenceNodes.LenNode lenNode,
                        @Shared("getSequenceStorageNode") @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Shared("getItemNode") @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode,
                        @CachedLibrary(limit = "1") InteropLibrary kwdnamesLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached PCallCExtFunction callCStringToString,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException {

            Object out = null;
            if (!keywordsOnly) {
                int l = lenNode.execute(state.v.argv);
                if (state.v.argnum < l) {
                    out = getItemNode.execute(getSequenceStorageNode.execute(state.v.argv), state.v.argnum);
                }
            }
            // only the bottom argstack can have keyword names
            if (kwds != null && out == null && state.v.prev == null && kwdnames != null) {
                Object kwdnamePtr = kwdnamesLib.readArrayElement(kwdnames, state.v.argnum);
                // TODO(fa) check if this is the NULL pointer since the kwdnames are always
                // NULL-terminated
                Object kwdname = callCStringToString.call(state.nativeContext, NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING, kwdnamePtr);
                if (kwdname instanceof String) {
                    // the cast to PDict is safe because either it is null or a PDict (ensured by
                    // the guards)
                    out = lib.getItem(getDictStorageNode.execute((PDict) kwds), kwdname);
                }
            }
            if (out == null && !state.restOptional) {
                raiseNode.raiseIntWithoutFrame(0, TypeError, "%s missing required argument (pos %d)", state.funName, state.v.argnum);
                throw ParseArgumentsException.raise();
            }
            state.v.argnum++;
            return out;
        }
    }

    /**
     * Executes a custom argument converter (i.e.
     * {@code int converter_fun(PyObject *arg, void *outVar)}.
     */
    @GenerateUncached
    abstract static class ExecuteConverterNode extends Node {

        public abstract void execute(ConversionNodeSupplier supplier, int index, Object converter, Object inputArgument, Object outputArgument) throws ParseArgumentsException;

        @Specialization(guards = "cachedIndex == index", limit = "5")
        @SuppressWarnings("unused")
        static void doExecuteConverterCached(ConversionNodeSupplier supplier, int index, Object converter, Object inputArgument,
                        Object outputArgument,
                        @Cached(value = "index", allowUncached = true) @SuppressWarnings("unused") int cachedIndex,
                        @CachedLibrary("converter") InteropLibrary converterLib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib,
                        @Cached(value = "createTN(supplier)", uncached = "getUncachedTN(supplier)") CExtToNativeNode toNativeNode,
                        @Exclusive @Cached PRaiseNativeNode raiseNode,
                        @Exclusive @Cached ConverterCheckResultNode checkResultNode) throws ParseArgumentsException {
            doExecuteConverterGeneric(supplier, index, converter, inputArgument, outputArgument, converterLib, resultLib, toNativeNode, raiseNode, checkResultNode);
        }

        @Specialization(replaces = "doExecuteConverterCached", limit = "1")
        @SuppressWarnings("unused")
        static void doExecuteConverterGeneric(ConversionNodeSupplier supplier, int index, Object converter, Object inputArgument, Object outputArgument,
                        @CachedLibrary("converter") InteropLibrary converterLib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib,
                        @Cached(value = "createTN(supplier)", uncached = "getUncachedTN(supplier)") CExtToNativeNode toNativeNode,
                        @Exclusive @Cached PRaiseNativeNode raiseNode,
                        @Exclusive @Cached ConverterCheckResultNode checkResultNode) throws ParseArgumentsException {
            try {
                Object result = converterLib.execute(converter, toNativeNode.execute(inputArgument), outputArgument);
                if (resultLib.fitsInInt(result)) {
                    checkResultNode.execute(resultLib.asInt(result));
                    return;
                }
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.CALLING_ARG_CONVERTER_FAIL_UNEXPECTED_RETURN, result);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.CALLING_ARG_CONVERTER_FAIL_INCOMPATIBLE_PARAMS, e.getSuppliedValues());
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.CALLING_ARG_CONVERTER_FAIL_EXPECTED_D_GOT_P, e.getExpectedArity(),
                                e.getActualArity());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.ARG_CONVERTED_NOT_EXECUTABLE);
            }
            throw ParseArgumentsException.raise();
        }

        static CExtToNativeNode createTN(ConversionNodeSupplier supplier) {
            return supplier.createToNativeNode();
        }

        static CExtToNativeNode getUncachedTN(ConversionNodeSupplier supplier) {
            return supplier.getUncachedToNativeNode();
        }
    }

    /**
     * Executes a custom argument converter (i.e.
     * {@code int converter_fun(PyObject *arg, void *outVar)}.
     */
    @GenerateUncached
    abstract static class ConverterCheckResultNode extends Node {

        public abstract void execute(int statusCode) throws ParseArgumentsException;

        @Specialization(guards = "statusCode != 0")
        static void doSuccess(@SuppressWarnings("unused") int statusCode) {
            // all fine
        }

        @Specialization(guards = "statusCode == 0")
        static void doError(@SuppressWarnings("unused") int statusCode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PRaiseNativeNode raiseNode) throws ParseArgumentsException {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (!errOccurred) {
                // converter should have set exception
                raiseNode.raiseInt(null, 0, TypeError, ErrorMessages.CONVERTER_FUNC_FAILED_TO_SET_ERROR);
            }
            throw ParseArgumentsException.raise();
        }
    }

    /**
     * Writes to an output variable in the varargs by doing the necessary native typing and
     * dereferencing. This is mostly like
     *
     * <pre>
     *     SomeType *outVar = va_arg(valist, SomeType *);
     *     *outVar = value;
     * </pre>
     *
     * It is important to use the appropriate {@code write*} functions!
     */
    @GenerateUncached
    @ImportStatic(LLVMType.class)
    abstract static class WriteOutVarNode extends Node {

        public final void writeUInt8(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.uint8_ptr_t, value);
        }

        public final void writeInt8(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.int8_ptr_t, value);
        }

        public final void writeUInt16(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.uint16_ptr_t, value);
        }

        public final void writeInt16(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.int16_ptr_t, value);
        }

        public final void writeInt32(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.int32_ptr_t, value);
        }

        public final void writeUInt32(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.uint32_ptr_t, value);
        }

        public final void writeInt64(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.int64_ptr_t, value);
        }

        public final void writeUInt64(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.uint64_ptr_t, value);
        }

        public final void writeFloat(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.float_ptr_t, value);
        }

        public final void writeDouble(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.double_ptr_t, value);
        }

        /**
         * Use this method if the object (pointer) to write is already a Sulong object (e.g. an LLVM
         * pointer or a native wrapper) and does not need to be wrapped.
         */
        public final void writePyObject(Object valist, int index, Object value) throws InteropException {
            execute(valist, index, LLVMType.PyObject_ptr_ptr_t, value);
        }

        public final void writeComplex(Object valist, int index, PComplex value) throws InteropException {
            execute(valist, index, LLVMType.Py_complex_ptr_t, value);
        }

        public abstract void execute(Object valist, int index, LLVMType accessType, Object value) throws InteropException;

        @Specialization(guards = "isPointerToPrimitive(accessType)", limit = "1")
        static void doPrimitive(Object valist, int index, LLVMType accessType, Number value,
                        @CachedLibrary("valist") InteropLibrary vaListLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "1") InteropLibrary outVarPtrLib,
                        @Shared("getLLVMType") @Cached GetLLVMType getLLVMTypeNode) {
            // The access type should be PE constant if the appropriate 'write*' method is used
            doAccess(vaListLib, outVarPtrLib, valist, index, getLLVMTypeNode.execute(accessType), value);
        }

        @Specialization(guards = "accessType == PyObject_ptr_ptr_t", limit = "1")
        static void doPointer(Object valist, int index, @SuppressWarnings("unused") LLVMType accessType, Object value,
                        @CachedLibrary("valist") InteropLibrary vaListLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "1") InteropLibrary outVarPtrLib,
                        @Shared("getLLVMType") @Cached GetLLVMType getLLVMTypeNode) {
            doAccess(vaListLib, outVarPtrLib, valist, index, getLLVMTypeNode.execute(accessType), value);
        }

        @Specialization(guards = "accessType == Py_complex_ptr_t", limit = "1")
        static void doComplex(Object valist, int index, @SuppressWarnings("unused") LLVMType accessType, PComplex value,
                        @CachedLibrary("valist") InteropLibrary vaListLib,
                        @CachedLibrary(limit = "1") InteropLibrary outVarPtrLib,
                        @Shared("getLLVMType") @Cached GetLLVMType getLLVMTypeNode) {
            try {
                // like 'some_type* out_var = va_arg(vl, some_type*)'
                Object outVarPtr = vaListLib.invokeMember(valist, "get", index, getLLVMTypeNode.execute(accessType));
                outVarPtrLib.writeMember(outVarPtr, "real", value.getReal());
                outVarPtrLib.writeMember(outVarPtr, "img", value.getImag());
            } catch (InteropException e) {
                CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private static void doAccess(InteropLibrary valistLib, InteropLibrary outVarPtrLib, Object valist, int index, Object llvmTypeID, Object value) {
            try {
                // like 'some_type* out_var = va_arg(vl, some_type*)'
                Object outVarPtr = valistLib.invokeMember(valist, "get", index, llvmTypeID);
                // like 'out_var[0] = value'
                outVarPtrLib.writeArrayElement(outVarPtr, 0, value);
            } catch (InteropException e) {
                CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    /**
     * Gets the pointer to the outVar at the given index. This is basically an access to the varargs
     * like {@code va_arg(*valist, void *)}
     */
    @GenerateUncached
    @ImportStatic(LLVMType.class)
    abstract static class GetVaArgsNode extends Node {

        public final Object getInt8Ptr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.int8_ptr_t);
        }

        public final Object getInt16Ptr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.int16_ptr_t);
        }

        public final Object getInt32Ptr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.int32_ptr_t);
        }

        public final Object getInt63Ptr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.int64_ptr_t);
        }

        public final Object getPyObjectPtr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.PyObject_ptr_t);
        }

        public final Object getCharPtr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.char_ptr_t);
        }

        public final Object getPyComplexPtr(Object valist, int index) throws InteropException {
            return execute(valist, index, LLVMType.Py_complex_ptr_t);
        }

        public abstract Object execute(Object valist, int index, LLVMType llvmType) throws InteropException;

        @Specialization(limit = "1")
        static Object doGeneric(Object valist, int index, LLVMType llvmType,
                        @CachedLibrary("valist") InteropLibrary valistLib,
                        @Cached GetLLVMType getLLVMType) throws InteropException {
            try {
                return valistLib.invokeMember(valist, "get", index, getLLVMType.execute(llvmType));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    static final class ParseArgumentsException extends ControlFlowException {
        private static final long serialVersionUID = 1L;

        static ParseArgumentsException raise() {
            CompilerDirectives.transferToInterpreter();
            throw new ParseArgumentsException();
        }
    }

    @GenerateUncached
    public abstract static class SplitFormatStringNode extends Node {

        public abstract String[] execute(String format);

        @Specialization(guards = "cachedFormat.equals(format)", limit = "1")
        static String[] doCached(@SuppressWarnings("unused") String format,
                        @Cached("format") @SuppressWarnings("unused") String cachedFormat,
                        @Cached(value = "extractFormatOnly(format)", dimensions = 1) String[] cachedResult) {
            return cachedResult;
        }

        @Specialization(replaces = "doCached")
        static String[] doGeneric(String format,
                        @Cached("createBinaryProfile()") ConditionProfile hasFunctionNameProfile) {
            int colonIdx = format.indexOf(":");
            if (hasFunctionNameProfile.profile(colonIdx != -1)) {
                // trim off function name
                return new String[]{format.substring(0, colonIdx), format.substring(colonIdx + 1)};
            }
            return new String[]{format, ""};
        }

        static String[] extractFormatOnly(String format) {
            return doGeneric(format, ConditionProfile.getUncached());
        }
    }
}
