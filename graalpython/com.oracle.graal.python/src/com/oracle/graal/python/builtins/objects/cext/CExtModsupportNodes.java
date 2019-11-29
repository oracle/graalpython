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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_BUFFER_R;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_BUFFER_RW;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.CExtModsupportNodesFactory.ConvertArgNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativeComplexNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode.IsSubtypeWithoutFrameNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.string.StringLenNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;

public abstract class CExtModsupportNodes {
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

        public abstract int execute(Object argv, Object kwds, Object format, Object kwdnames, Object varargs);

        @Specialization(guards = {"isDictOrNull(kwds)", "cachedFormat.equals(format)", "cachedFormat.length() <= 8"}, limit = "5")
        int doSpecial(PTuple argv, Object kwds, @SuppressWarnings("unused") String format, Object kwdnames, Object varargs,
                        @Cached(value = "format", allowUncached = true) String cachedFormat,
                        @Cached("createConvertArgNodes(cachedFormat)") ConvertArgNode[] convertArgNodes,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                doParsingExploded(argv, kwds, cachedFormat, kwdnames, varargs, convertArgNodes, raiseNode);
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                CompilerAsserts.neverPartOfCompilation();
                return 0;
            }
        }

        @Specialization(guards = "isDictOrNull(kwds)", replaces = "doSpecial")
        int doGeneric(PTuple argv, Object kwds, String format, Object kwdnames, Object varargs,
                        @Cached ConvertArgNode convertArgNode,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                ParserState state = new ParserState(new PositionalArgStack(argv, null));
                for (int i = 0; i < format.length(); i++) {
                    state = convertArg(state, kwds, format, i, kwdnames, varargs, convertArgNode, raiseNode);
                }
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                CompilerAsserts.neverPartOfCompilation();
                return 0;
            }
        }

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        private static void doParsingExploded(PTuple argv, Object kwds, String format, Object kwdnames, Object varargs, ConvertArgNode[] convertArgNodes, PRaiseNativeNode raiseNode)
                        throws InteropException, ParseArgumentsException {
            CompilerAsserts.partialEvaluationConstant(format.length());
            ParserState state = new ParserState(new PositionalArgStack(argv, null));
            for (int i = 0; i < format.length(); i++) {
                state = convertArg(state, kwds, format, i, kwdnames, varargs, convertArgNodes[i], raiseNode);
            }
        }

        private static ParserState convertArg(ParserState state, Object kwds, String format, int format_idx, Object kwdnames, Object varargs, ConvertArgNode convertArgNode,
                        PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            char c = format.charAt(format_idx);
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
                        raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, "')' without '(' in argument parsing");
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
                    // We extract and remove the function name already in the calling builtin. So this char may not occur here.
                    assert false : "got ':' but this should be trimmed from the format string";
                case ';':
                    assert false : "got ';' but this should be trimmed from the format string";
                    return state;
                default:
                    raiseNode.raiseIntWithoutFrame(0, TypeError, "unrecognized format char in arguments parsing: %c", c);
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
        private final int outIndex;
        private final boolean restOptional;
        private final boolean restKeywordsOnly;
        private final PositionalArgStack v;

        ParserState(PositionalArgStack v) {
            this(0, false, false, v);
        }

        private ParserState(int outIndex, boolean restOptional, boolean restKeywordsOnly, PositionalArgStack v) {
            this.outIndex = outIndex;
            this.restOptional = restOptional;
            this.restKeywordsOnly = restKeywordsOnly;
            this.v = v;
        }

        ParserState incrementOutIndex() {
            return new ParserState(outIndex + 1, restOptional, restKeywordsOnly, v);
        }

        ParserState restOptional() {
            return new ParserState(outIndex, true, restKeywordsOnly, v);
        }

        ParserState restKeywordsOnly() {
            return new ParserState(outIndex, restOptional, true, v);
        }

        ParserState open(PositionalArgStack nestedArgs) {
            return new ParserState(outIndex, restOptional, true, nestedArgs);
        }

        ParserState close() {
            return new ParserState(outIndex, restOptional, true, v.prev);
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
    @ImportStatic(CExtModsupportNodes.class)
    abstract static class ConvertArgNode extends Node {
        public abstract ParserState execute(ParserState state, Object kwds, char c, String format, int format_idx, Object kwdnames, Object varargs) throws InteropException, ParseArgumentsException;

        static boolean isCStringSpecifier(char c) {
            return c == FORMAT_LOWER_S || c == FORMAT_LOWER_Z;
        }

        @Specialization(guards = "c == FORMAT_LOWER_Y")
        static ParserState doBufferR(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached PCallCapiFunction callGetBufferRwNode,
                        @Cached ToSulongNode argToSulongNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (isLookahead(format, format_idx, '*')) {
                /* format_idx++; */
                // 'y*'; output to 'Py_buffer*'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object pybufferPtr = getVaArgNode.execute(varargs, state.outIndex);
                    getbuffer(callGetBufferRwNode, argToSulongNode, raiseNode, arg, pybufferPtr, true);
                }
            } else {
                // TODO(fa) convertbuffer: create a temporary 'Py_buffer' struct, call
                // 'get_buffer_r' and output the buffer's data pointer
                Object destDataPtr = getVaArgNode.execute(varargs, state.outIndex);
                if (isLookahead(format, format_idx, '#')) {
                    /* format_idx++; */
                    // 'y#'
                    // TODO(fa) additionally store size
                    Object pybufferPtr = getVaArgNode.execute(varargs, state.outIndex);
                    state = state.incrementOutIndex();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "isCStringSpecifier(c)")
        static ParserState doCString(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached AsCharPointerNode asCharPointerNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached StringLenNode stringLenNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            boolean z = c == FORMAT_LOWER_Z;
            if (isLookahead(format, format_idx, '*')) {
                /* format_idx++; */
                // 's*' or 'z*'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object pybuffer = getVaArgNode.execute(varargs, state.outIndex);
                    // TODO(fa) create Py_buffer
                }
            } else if (isLookahead(format, format_idx, '#')) {
                /* format_idx++; */
                // 's#' or 'z#'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    if (z && PGuards.isPNone(arg)) {
                        writeOutVarNode.writeObject(varargs, state.outIndex, getNativeNullNode.execute());
                        state = state.incrementOutIndex();
                        writeOutVarNode.writeInt32(varargs, state.outIndex, 0);
                    } else if (PGuards.isString(arg)) {
                        // TODO(fa) we could use CStringWrapper to do the copying lazily
                        writeOutVarNode.writePointer(varargs, state.outIndex, asCharPointerNode.execute(arg));
                        state = state.incrementOutIndex();
                        writeOutVarNode.writeInt32(varargs, state.outIndex, stringLenNode.execute(arg));
                    } else {
                        throw raise(raiseNode, TypeError, "expected %s, got %p", z ? "str or None" : "str", arg);
                    }
                }
            } else {
                // 's' or 'z'
                if (!skipOptionalArg(arg, state.restOptional)) {
                    if (z && PGuards.isPNone(arg)) {
                        writeOutVarNode.writeObject(varargs, state.outIndex, getNativeNullNode.execute());
                    } else if (PGuards.isString(arg)) {
                        // TODO(fa) we could use CStringWrapper to do the copying lazily
                        writeOutVarNode.writePointer(varargs, state.outIndex, asCharPointerNode.execute(arg));
                    } else {
                        throw raise(raiseNode, TypeError, "expected %s, got %p", z ? "str or None" : "str", arg);
                    }
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_S")
        static ParserState doBytes(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Exclusive @Cached GetLazyClassNode getClassNode,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                if (isBytesProfile.profileClass(getClassNode.execute(arg), PythonBuiltinClassType.PBytes)) {
                    writeOutVarNode.writeObject(varargs, state.outIndex, arg);
                }
                throw raise(raiseNode, TypeError, "expected bytes, not %p", arg);
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_Y")
        static ParserState doByteArray(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Exclusive @Cached GetLazyClassNode getClassNode,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                if (isBytesProfile.profileClass(getClassNode.execute(arg), PythonBuiltinClassType.PByteArray)) {
                    writeOutVarNode.writeObject(varargs, state.outIndex, arg);
                }
                throw raise(raiseNode, TypeError, "expected bytearray, not %p", arg);
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_U")
        static ParserState doUnicode(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Exclusive @Cached GetLazyClassNode getClassNode,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                if (isBytesProfile.profileClass(getClassNode.execute(arg), PythonBuiltinClassType.PString)) {
                    writeOutVarNode.writeObject(varargs, state.outIndex, arg);
                }
                throw raise(raiseNode, TypeError, "expected str, not %p", arg);
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_E")
        static ParserState doEncodedString(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, @SuppressWarnings("unused") Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                throw raise(raiseNode, TypeError, "'e*' format specifiers are not supported", arg);
            }
            return state;
        }

        @Specialization(guards = "c == FORMAT_LOWER_B")
        static ParserState doUnsignedByte(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            // C type: unsigned char
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                    if (ival < 0) {
                        throw raise(raiseNode, OverflowError, "unsigned byte integer is less than minimum");
                    } else if (ival > Byte.MAX_VALUE * 2 + 1) {
                        // TODO(fa) MAX_VALUE should be retrieved from Sulong
                        throw raise(raiseNode, OverflowError, "unsigned byte integer is greater than maximum");
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
        static ParserState doUnsignedByteBitfield(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format,
                        @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: unsigned char
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
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
        static ParserState doShortInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            // C type: signed short int
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                    // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                    if (ival < Short.MIN_VALUE) {
                        throw raise(raiseNode, OverflowError, "signed short integer is less than minimum");
                    } else if (ival > Short.MAX_VALUE) {
                        throw raise(raiseNode, OverflowError, "signed short integer is greater than maximum");
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
        static ParserState doUnsignedShortInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: short int sized bitfield
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
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
        static ParserState doSignedInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            // C type: signed int
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                    // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from Sulong
                    if (ival < Integer.MIN_VALUE) {
                        throw raise(raiseNode, OverflowError, "signed integer is less than minimum");
                    } else if (ival > Integer.MAX_VALUE) {
                        throw raise(raiseNode, OverflowError, "signed integer is greater than maximum");
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
        static ParserState doUnsignedInt(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: int sized bitfield
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
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
        static ParserState doLong(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                                  Object kwdnames, Object varargs,
                                  @Shared("getArgNode") @Cached GetArgNode getArgNode,
                                  @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                                  @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                                  @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: signed long and signed long long
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
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
        static ParserState doUnsignedLong(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: unsigned long and unsigned long long
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
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
        static ParserState doPySsizeT(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("excToNativeNode") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) throws InteropException, ParseArgumentsException {

            // C type: signed short int
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                try {
                    // TODO(fa): AsNativePrimitiveNode coerces using '__int__', but here we must use '__index__'
                    writeOutVarNode.writeInt64(varargs, state.outIndex, asNativePrimitiveNode.toInt64(arg, true));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    throw ParseArgumentsException.raise();
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_C")
        static ParserState doByteFromBytesOrBytearray(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format,
                        @SuppressWarnings("unused") int format_idx, Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
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
                    throw raise(raiseNode, TypeError, "must be a byte string of length 1, not %p", arg);
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_C")
        static ParserState doIntFromString(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached StringLenNode stringLenNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                // TODO(fa): There could be native subclasses (i.e. the Java type would not be
                // 'String' or 'PString') but we do currently not support this.
                if (!(PGuards.isString(arg) && stringLenNode.execute(arg) == 1)) {
                    throw raise(raiseNode, TypeError, "expected a unicode character, not %p", arg);
                }
                // TODO(fa) use the sequence lib to get the character once available
                char singleChar;
                if (arg instanceof String) {
                    singleChar = ((String) arg).charAt(0);
                } else if (arg instanceof PString) {
                    singleChar = ((PString) arg).getCharSequence().charAt(0);
                } else {
                    throw raise(raiseNode, SystemError, "unsupported string type: %s", arg.getClass());
                }
                writeOutVarNode.writeInt32(varargs, state.outIndex, (int) singleChar);
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_F")
        static ParserState doFloat(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                writeOutVarNode.writeFloat(varargs, state.outIndex, (float) asDoubleNode.execute(arg));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_D")
        static ParserState doDouble(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                writeOutVarNode.writeDouble(varargs, state.outIndex, asDoubleNode.execute(arg));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_D")
        static ParserState doComplex(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached AsNativeComplexNode asComplexNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                writeOutVarNode.writeComplex(varargs, state.outIndex, asComplexNode.execute(arg));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_UPPER_O")
        static ParserState doObject(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached ExecuteConverterNode executeConverterNode,
                        @Cached GetLazyClassNode getClassNode,
                        @Cached IsSubtypeWithoutFrameNode isSubtypeNode,
                        @Cached ToJavaNode typeToJavaNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (isLookahead(format, format_idx, '!')) {
                /* format_idx++; */
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object typeObject = typeToJavaNode.execute(getVaArgNode.execute(varargs, state.outIndex));
                    state = state.incrementOutIndex();
                    assert PGuards.isClass(typeObject);
                    if (!isSubtypeNode.executeWithGlobalState(getClassNode.execute(arg), typeObject)) {
                        raiseNode.raiseIntWithoutFrame(0, TypeError, "expected object of type %s, got %p", typeObject, arg);
                        throw ParseArgumentsException.raise();
                    }
                    writeOutVarNode.writeObject(varargs, state.outIndex, arg);
                }
            } else if (isLookahead(format, format_idx, '&')) {
                /* format_idx++; */
                Object converter = getVaArgNode.execute(varargs, state.outIndex);
                state = state.incrementOutIndex();
                if (!skipOptionalArg(arg, state.restOptional)) {
                    Object output = getVaArgNode.execute(varargs, state.outIndex);
                    executeConverterNode.execute(state.outIndex, converter, arg, output);
                }
            } else {
                if (!skipOptionalArg(arg, state.restOptional)) {
                    writeOutVarNode.writeObject(varargs, state.outIndex, arg);
                }
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_LOWER_W")
        static ParserState doBufferRW(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Cached GetVaArgsNode getVaArgNode,
                        @Cached PCallCapiFunction callGetBufferRwNode,
                        @Cached ToSulongNode argToSulongNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!isLookahead(format, format_idx, '*')) {
                throw raise(raiseNode, TypeError, "invalid use of 'w' format character");

            }
            if (!skipOptionalArg(arg, state.restOptional)) {
                Object pybufferPtr = getVaArgNode.execute(varargs, state.outIndex);
                getbuffer(callGetBufferRwNode, argToSulongNode, raiseNode, arg, pybufferPtr, false);
            }
            return state.incrementOutIndex();
        }

        private static void getbuffer(PCallCapiFunction callGetBufferRwNode, ToSulongNode argToSulongNode, PRaiseNativeNode raiseNode, Object arg, Object pybufferPtr, boolean readOnly)
                        throws ParseArgumentsException {
            String funName = readOnly ? FUN_GET_BUFFER_R : FUN_GET_BUFFER_RW;
            Object rc = callGetBufferRwNode.call(funName, argToSulongNode.execute(arg), pybufferPtr);
            if (!(rc instanceof Number)) {
                throw raise(raiseNode, SystemError, "%s returned an unexpected return code; expected 'int' but was %s", funName, rc.getClass());
            }
            int i = ((Number) rc).intValue();
            if (i == -1) {
                throw raise(raiseNode, TypeError, "read-write bytes-like object");
            } else if (i == -2) {
                throw raise(raiseNode, TypeError, "contiguous buffer");
            }
        }

        @Specialization(guards = "c == FORMAT_LOWER_P")
        static ParserState doPredicate(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Shared("writeOutVarNode") @Cached WriteOutVarNode writeOutVarNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (!skipOptionalArg(arg, state.restOptional)) {
                // TODO(fa) refactor 'CastToBooleanNode' to provide uncached version and use it
                writeOutVarNode.writeInt32(varargs, state.outIndex, LookupAndCallUnaryDynamicNode.getUncached().executeObject(arg, SpecialMethodNames.__BOOL__));
            }
            return state.incrementOutIndex();
        }

        @Specialization(guards = "c == FORMAT_PAR_OPEN")
        static ParserState doPredicate(ParserState state, Object kwds, @SuppressWarnings("unused") char c, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") int format_idx,
                        Object kwdnames, @SuppressWarnings("unused") Object varargs,
                        @Shared("getArgNode") @Cached GetArgNode getArgNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state.v, kwds, kwdnames, state.restKeywordsOnly);
            if (skipOptionalArg(arg, state.restOptional)) {
                return state.incrementOutIndex();
            } else {
                // n.b.: there is a small gap in this check: In theory, there could be
                // native subclass of tuple. But since we do not support this anyway, the
                // instanceof test is just the most efficient way to do it.
                if (!(arg instanceof PTuple)) {
                    throw raise(raiseNode, TypeError, "expected tuple, got %p", arg);
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

        private static boolean isLookahead(String format, int format_idx, char expected) {
            return format_idx + 1 < format.length() && format.charAt(format_idx + 1) == expected;
        }
    }

    /**
     * Gets a single argument from the arguments tuple or from the keywords dictionary.
     */
    @GenerateUncached
    abstract static class GetArgNode extends Node {

        public abstract Object execute(PositionalArgStack p, Object kwds, Object kwdnames, boolean keywords_only) throws InteropException;

        @Specialization(guards = {"kwds == null", "!keywordsOnly"})
        Object doNoKeywords(PositionalArgStack p, @SuppressWarnings("unused") Object kwds, @SuppressWarnings("unused") Object kwdnames, boolean keywordsOnly,
                        @Shared("lenNode") @Cached SequenceNodes.LenNode lenNode,
                        @Shared("getSequenceStorageNode") @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Shared("getItemNode") @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode) {

            Object out = null;
            assert !keywordsOnly;
            int l = lenNode.execute(p.argv);
            if (p.argnum < l) {
                out = getItemNode.execute(getSequenceStorageNode.execute(p.argv), p.argnum);
            }
            p.argnum++;
            return out;
        }

        @Specialization(replaces = "doNoKeywords", limit = "1")
        Object doGeneric(PositionalArgStack p, Object kwds, Object kwdnames, boolean keywordsOnly,
                        @Shared("lenNode") @Cached SequenceNodes.LenNode lenNode,
                        @Shared("getSequenceStorageNode") @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Shared("getItemNode") @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode,
                        @Cached HashingStorageNodes.GetItemInteropNode getDictItemNode,
                        @CachedLibrary("kwdnames") InteropLibrary kwdnamesLib,
                         @Cached PCallCapiFunction callCStringToString) throws InteropException {

            Object out = null;
            if (!keywordsOnly) {
                int l = lenNode.execute(p.argv);
                if (p.argnum < l) {
                    out = getItemNode.execute(getSequenceStorageNode.execute(p.argv), p.argnum);
                }
            }
            // only the bottom argstack can have keyword names
            if (kwds != null && out == null && p.prev == null && kwdnames != null) {
                Object kwdnamePtr = kwdnamesLib.readArrayElement(kwdnames, p.argnum);
                // TODO(fa) check if this is the NULL pointer since the kwdnames are always NULL-terminated
                Object kwdname = callCStringToString.call(NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING, kwdnamePtr);
                if (kwdname instanceof String) {
                    // the cast to PDict is safe because either it is null or a PDict (ensured by
                    // the guards)
                    out = getDictItemNode.executeWithGlobalState(getDictStorageNode.execute((PDict) kwds), kwdname);
                }
            }
            p.argnum++;
            return out;
        }
    }

    /**
     * Executes a custom argument converter (i.e.
     * {@code int converter_fun(PyObject *arg, void *outVar)}.
     */
    @GenerateUncached
    abstract static class ExecuteConverterNode extends Node {

        public abstract void execute(int index, Object converter, Object inputArgument, Object outputArgument) throws ParseArgumentsException;

        @Specialization(guards = "cachedIndex == index", limit = "3")
        static void doExecuteConverterCached(@SuppressWarnings("unused") int index, Object converter, Object inputArgument, Object outputArgument,
                        @Cached(value = "index", allowUncached = true) @SuppressWarnings("unused") int cachedIndex,
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
        static void doError(int statusCode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PRaiseNativeNode raiseNode) throws ParseArgumentsException {
            PException currentException = context.getCurrentException();
            boolean errOccurred = currentException != null;
            if (!errOccurred) {
                // converter should have set exception
                raiseNode.raiseInt(null, 0, TypeError, "converter function failed to set an error on failure");
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
        static final int TYPE_OBJECT = 11;
        static final int TYPE_COMPLEX = 12;

        private static final String[] MAP = new String[]{"i8", "i16", "i32", "i64", "u8", "i16", "u32", "u64", "d", "f", "ptr", "ptr", "c"};

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

        /**
         * Use this method to write an object (e.g. some Python object) which needs to be wrapped
         * into a native wrapper.
         */
        public final void writeObject(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_OBJECT, value);
        }

        /**
         * Use this method if the object (pointer) to write is already a Sulong object (e.g. an LLVM
         * pointer or a native wrapper) and does not need to be wrapped.
         */
        public final void writePointer(Object varargs, int outIndex, Object value) throws InteropException {
            execute(varargs, outIndex, TYPE_VOIDPTR, value);
        }

        public final void writeComplex(Object varargs, int outIndex, PComplex value) throws InteropException {
            execute(varargs, outIndex, TYPE_COMPLEX, value);
        }

        public abstract void execute(Object varargs, int outIndex, int accessType, Object value) throws InteropException;

        @Specialization(guards = "isPrimitive(accessType)", limit = "1")
        static void doPrimitive(Object varargs, int outIndex, int accessType, Number value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib) throws InteropException {
            // The access type should be PE constant if the appropriate 'write*' method is used
            CompilerAsserts.partialEvaluationConstant(accessType);
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, MAP[accessType], value);
        }

        @Specialization(guards = "accessType == TYPE_VOIDPTR", limit = "1")
        static void doPointer(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, MAP[accessType], value);
        }

        @Specialization(guards = "accessType == TYPE_OBJECT", limit = "1")
        static void doObject(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, Object value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib,
                        @Cached ToSulongNode toSulongNode) throws InteropException {
            doAccess(varargsLib, outVarPtrLib, outVarLib, varargs, outIndex, MAP[accessType], toSulongNode.execute(value));
        }

        @Specialization(guards = "accessType == TYPE_COMPLEX", limit = "1")
        static void doComplex(Object varargs, int outIndex, @SuppressWarnings("unused") int accessType, PComplex value,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @Shared("outVarPtrLib") @CachedLibrary(limit = "2") InteropLibrary outVarPtrLib,
                        @Exclusive @CachedLibrary(limit = "2") InteropLibrary outVarLib) throws InteropException {
            try {
                Object outVarPtr = varargsLib.readArrayElement(varargs, outIndex);
                Object outVar = outVarPtrLib.readMember(outVarPtr, "content");
                Object outVarComplex = outVarPtrLib.readMember(outVar, "c");
                outVarLib.writeMember(outVarComplex, "real", value.getReal());
                outVarLib.writeMember(outVarComplex, "img", value.getImag());
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e;
            }
        }

        private static void doAccess(InteropLibrary varargsLib, InteropLibrary outVarPtrLib, InteropLibrary outVarLib, Object varargs, int outIndex, String accessor, Object value)
                        throws InteropException {
            try {
                Object outVarPtr = varargsLib.readArrayElement(varargs, outIndex);
                Object outVar = outVarPtrLib.readMember(outVarPtr, "content");
                outVarLib.writeMember(outVar, accessor, value);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e;
            }
        }

        static boolean isPrimitive(int accessType) {
            return accessType < TYPE_VOIDPTR;
        }
    }

    /**
     * Gets the pointer to the outVar at the given index. This is basically an access to the varargs
     * like {@code va_arg(*valist, void *)}
     */
    @GenerateUncached
    abstract static class GetVaArgsNode extends Node {

        public abstract Object execute(Object varargs, int index) throws InteropException;

        @Specialization(limit = "1")
        static Object doGetVaArg(Object varargs, int out_index,
                        @CachedLibrary("varargs") InteropLibrary varargsLib,
                        @CachedLibrary(limit = "1") InteropLibrary outVarPtrLib) throws InteropException {
            try {
                Object outVarPtr = varargsLib.readArrayElement(varargs, out_index);
                return outVarPtrLib.readMember(outVarPtr, "content");
            } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw e;
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
}
