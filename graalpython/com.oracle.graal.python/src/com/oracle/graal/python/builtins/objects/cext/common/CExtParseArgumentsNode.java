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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_CONVERTBUFFER;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_BUFFER_R;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_BUFFER_RW;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltins.PyTuple_GetItem;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsNativeComplexNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.TransformExceptionToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.PCallCExtFunction;
import com.oracle.graal.python.builtins.objects.cext.common.CExtParseArgumentsNodeFactory.ConvertSingleArgNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
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
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public abstract class CExtParseArgumentsNode {
    static final int FORMAT_LOWER_S = 's';
    static final int FORMAT_UPPER_S = 'S';
    static final int FORMAT_LOWER_Z = 'z';
    static final int FORMAT_UPPER_Z = 'Z';
    static final int FORMAT_LOWER_Y = 'y';
    static final int FORMAT_UPPER_Y = 'Y';
    static final int FORMAT_LOWER_U = 'u';
    static final int FORMAT_UPPER_U = 'U';
    static final int FORMAT_LOWER_E = 'e';
    static final int FORMAT_LOWER_B = 'b';
    static final int FORMAT_UPPER_B = 'B';
    static final int FORMAT_LOWER_H = 'h';
    static final int FORMAT_UPPER_H = 'H';
    static final int FORMAT_LOWER_I = 'i';
    static final int FORMAT_UPPER_I = 'I';
    static final int FORMAT_LOWER_L = 'l';
    static final int FORMAT_UPPER_L = 'L';
    static final int FORMAT_LOWER_K = 'k';
    static final int FORMAT_UPPER_K = 'K';
    static final int FORMAT_LOWER_N = 'n';
    static final int FORMAT_LOWER_C = 'c';
    static final int FORMAT_UPPER_C = 'C';
    static final int FORMAT_LOWER_F = 'f';
    static final int FORMAT_LOWER_D = 'd';
    static final int FORMAT_UPPER_D = 'D';
    static final int FORMAT_UPPER_O = 'O';
    static final int FORMAT_LOWER_W = 'w';
    static final int FORMAT_LOWER_P = 'p';
    static final int FORMAT_PAR_OPEN = '(';
    static final int FORMAT_PAR_CLOSE = ')';

    @ImportStatic({PGuards.class, PythonUtils.class})
    public abstract static class ParseTupleAndKeywordsNode extends Node {

        public abstract int execute(TruffleString funName, Object argv, Object kwds, Object format, Object kwdnames, Object varargs);

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Specialization(guards = {"isDictOrNull(kwds)", "eqNode.execute(cachedFormat, format, TS_ENCODING)", "lengthNode.execute(cachedFormat, TS_ENCODING) <= 8"}, limit = "5")
        int doSpecial(TruffleString funName, PTuple argv, Object kwds, @SuppressWarnings("unused") TruffleString format, Object kwdnames, Object varargs,
                        @Cached(value = "format", allowUncached = true) @SuppressWarnings("unused") TruffleString cachedFormat,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @Cached("createConvertArgNodes(cachedFormat, lengthNode)") ConvertSingleArgNode[] convertArgNodes,
                        @Cached HashingStorageLen kwdsLenNode,
                        @Cached PRaiseNativeNode raiseNode,
                        @SuppressWarnings("unused") @Cached TruffleString.EqualNode eqNode) {
            try {
                PDict kwdsDict = null;
                if (kwds != null && kwdsLenNode.execute(((PDict) kwds).getDictStorage()) != 0) {
                    kwdsDict = (PDict) kwds;
                }
                int length = lengthNode.execute(format, TS_ENCODING);
                ParserState state = new ParserState(funName, new PositionalArgStack(argv, null));
                int i = 0;
                while (i < length) {
                    i = convertArgNodes[i].execute(state, kwdsDict, format, i, length, kwdnames, varargs, codepointAtIndexNode);
                }
                checkExcessArgs(argv, state, raiseNode);
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                return 0;
            }
        }

        @Specialization(guards = "isDictOrNull(kwds)", replaces = "doSpecial")
        @Megamorphic
        int doGeneric(TruffleString funName, PTuple argv, Object kwds, TruffleString format, Object kwdnames, Object varargs,
                        @Cached ConvertSingleArgNode convertArgNode,
                        @Cached HashingStorageLen kwdsLenNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @Cached PRaiseNativeNode raiseNode) {
            try {
                PDict kwdsDict = null;
                if (kwds != null && kwdsLenNode.execute(((PDict) kwds).getDictStorage()) != 0) {
                    kwdsDict = (PDict) kwds;
                }
                int length = lengthNode.execute(format, TS_ENCODING);
                ParserState state = new ParserState(funName, new PositionalArgStack(argv, null));
                int i = 0;
                while (i < length) {
                    i = convertArgNode.execute(state, kwdsDict, format, i, length, kwdnames, varargs, codepointAtIndexNode);
                }
                checkExcessArgs(argv, state, raiseNode);
                return 1;
            } catch (InteropException | ParseArgumentsException e) {
                return 0;
            }
        }

        private static void checkExcessArgs(PTuple argv, ParserState state, PRaiseNativeNode raiseNode) {
            int argvLen = argv.getSequenceStorage().length();
            if (argvLen > state.v.argnum) {
                raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.EXPECTED_AT_MOST_D_ARGS_GOT_D, state.v.argnum, argvLen);
                throw ParseArgumentsException.raise();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        int error(TruffleString funName, Object argv, Object kwds, Object format, Object kwdnames, Object varargs,
                        @Cached PRaiseNativeNode raiseNode) {
            return raiseNode.raiseIntWithoutFrame(0, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }

        static ConvertSingleArgNode[] createConvertArgNodes(TruffleString format, TruffleString.CodePointLengthNode lengthNode) {
            ConvertSingleArgNode[] convertArgNodes = new ConvertSingleArgNode[lengthNode.execute(format, TS_ENCODING)];
            for (int i = 0; i < convertArgNodes.length; i++) {
                convertArgNodes[i] = ConvertSingleArgNodeGen.create();
            }
            return convertArgNodes;
        }

        static boolean isDictOrNull(Object object) {
            return object == null || object instanceof PDict;
        }
    }

    abstract static class ConvertSingleArgNode extends Node {

        public abstract int execute(ParserState state, Object kwds, TruffleString format, int formatIdx, int formatLength, Object kwdnames, Object varargs,
                        TruffleString.CodePointAtIndexNode codepointAtIndexNode) throws InteropException;

        private static char charFromPString(PString arg, TruffleString.ReadCharUTF16Node readCharNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
            if (arg.isMaterialized()) {
                return readCharNode.execute(switchEncodingNode.execute(arg.getMaterialized(), TruffleString.Encoding.UTF_16), 0);
            }
            if (arg.isNativeCharSequence()) {
                return charFromNativePString(arg);
            }
            throw CompilerDirectives.shouldNotReachHere("PString is neither materialized nor native");
        }

        @TruffleBoundary
        private static char charFromNativePString(PString arg) {
            assert arg.isNativeCharSequence();
            return arg.getNativeCharSequence().charAt(0);
        }

        private static ParseArgumentsException raise(PRaiseNativeNode raiseNode, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            CompilerDirectives.transferToInterpreter();
            raiseNode.executeInt(null, 0, errType, format, arguments);
            throw ParseArgumentsException.raise();
        }

        @Specialization
        int doArg(ParserState state, Object kwds, TruffleString format, int formatIdx, int formatLength, Object kwdnames, Object varargs, TruffleString.CodePointAtIndexNode codepointAtIndexNode,
                        @Cached AsNativePrimitiveNode asNativePrimitiveNode,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Cached AsNativeDoubleNode asDoubleNode,
                        @Cached AsNativeComplexNode asComplexNode,
                        @Cached StringLenNode stringLenNode,
                        @Cached TruffleString.ReadCharUTF16Node readCharNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsBuiltinClassProfile isBytesProfile,
                        @Cached GetArgNode getArgNode,
                        @Cached GetNextVaArgNode getVaArgNode,
                        @Cached WriteNextVaArgNode writeOutVarNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode toNativeNode,
                        @Cached PRaiseNativeNode raiseNode,
                        @Cached ConvertParArgNode convertParArgNode,
                        @Cached ConvertExtendedArgNode convertExtendedArgNode,
                        @Cached ConvertArgNode convertArgNode) throws InteropException {
            try {
                int c = codePoint(format, formatIdx, codepointAtIndexNode);
                switch (c) {
                    case FORMAT_LOWER_B:
                    case FORMAT_UPPER_B:
                    case FORMAT_LOWER_C:
                    case FORMAT_UPPER_C:
                    case FORMAT_LOWER_D:
                    case FORMAT_UPPER_D:
                    case FORMAT_LOWER_F:
                    case FORMAT_LOWER_H:
                    case FORMAT_UPPER_H:
                    case FORMAT_LOWER_I:
                    case FORMAT_UPPER_I:
                    case FORMAT_LOWER_K:
                    case FORMAT_UPPER_K:
                    case FORMAT_LOWER_L:
                    case FORMAT_UPPER_L:
                    case FORMAT_LOWER_N:
                    case FORMAT_LOWER_P:
                    case FORMAT_UPPER_Y:
                    case FORMAT_UPPER_U:
                    case FORMAT_UPPER_S: {
                        // simple cases without variants
                        Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
                        if (skipOptionalArg(arg, state.restOptional)) {
                            // skip vararg element
                            getVaArgNode.getVoidPtr(varargs);
                        } else {
                            switch (c) {
                                case FORMAT_UPPER_S:
                                    if (isBytesProfile.profileClass(getClassNode.execute(arg), PythonBuiltinClassType.PBytes)) {
                                        writeOutVarNode.writePyObject(varargs, toNativeNode.execute(arg));
                                    } else {
                                        throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_NOT_P, "bytes", arg);
                                    }
                                    break;
                                case FORMAT_LOWER_B: {
                                    // C type: unsigned char
                                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                                    if (ival < 0) {
                                        throw raise(raiseNode, OverflowError, ErrorMessages.UNSIGNED_BYTE_INT_LESS_THAN_MIN);
                                    } else if (ival > Byte.MAX_VALUE * 2 + 1) {
                                        // TODO(fa) MAX_VALUE should be retrieved from Sulong
                                        throw raise(raiseNode, OverflowError, ErrorMessages.UNSIGNED_BYTE_INT_GREATER_THAN_MAX);
                                    }
                                    writeOutVarNode.writeUInt8(varargs, ival);
                                    break;
                                }

                                case FORMAT_UPPER_B:
                                    // C type: unsigned char
                                    writeOutVarNode.writeUInt8(varargs, asNativePrimitiveNode.toInt64(arg, false));
                                    break;

                                case FORMAT_LOWER_H: {
                                    // C type: signed short int
                                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                                    // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from
                                    // Sulong
                                    if (ival < Short.MIN_VALUE) {
                                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_SHORT_INT_LESS_THAN_MIN);
                                    } else if (ival > Short.MAX_VALUE) {
                                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_SHORT_INT_GREATER_THAN_MAX);
                                    }
                                    writeOutVarNode.writeInt16(varargs, ival);
                                    break;
                                }
                                case FORMAT_UPPER_H:
                                    // C type: short int sized bitfield
                                    writeOutVarNode.writeInt16(varargs, asNativePrimitiveNode.toInt64(arg, false));
                                    break;
                                case FORMAT_LOWER_I: {
                                    // C type: signed int
                                    long ival = asNativePrimitiveNode.toInt64(arg, true);
                                    // TODO(fa) MIN_VALUE and MAX_VALUE should be retrieved from
                                    // Sulong
                                    if (ival < Integer.MIN_VALUE) {
                                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_INT_LESS_THAN_MIN);
                                    } else if (ival > Integer.MAX_VALUE) {
                                        throw raise(raiseNode, OverflowError, ErrorMessages.SIGNED_INT_GREATER_THAN_MAX);
                                    }
                                    writeOutVarNode.writeInt32(varargs, ival);
                                    break;
                                }
                                case FORMAT_UPPER_I:
                                    // C type: int sized bitfield
                                    writeOutVarNode.writeUInt32(varargs, asNativePrimitiveNode.toInt64(arg, false));
                                    break;
                                case FORMAT_LOWER_L:
                                case FORMAT_UPPER_L:
                                    // C type: signed long and signed long long
                                    writeOutVarNode.writeInt64(varargs, asNativePrimitiveNode.toInt64(arg, true));
                                    break;
                                case FORMAT_LOWER_K:
                                case FORMAT_UPPER_K:
                                    // C type: unsigned long and unsigned long long
                                    writeOutVarNode.writeUInt64(varargs, asNativePrimitiveNode.toUInt64(arg, false));
                                    break;
                                case FORMAT_LOWER_N:
                                    // C type: PySSize_t
                                    if (arg instanceof PythonNativeVoidPtr) {
                                        writeOutVarNode.writePyObject(varargs, ((PythonNativeVoidPtr) arg).getPointerObject());
                                    } else {
                                        // TODO(fa): AsNativePrimitiveNode coerces using '__int__',
                                        // but here we must
                                        // use '__index__'
                                        writeOutVarNode.writeInt64(varargs, asNativePrimitiveNode.toInt64(arg, true));
                                    }
                                    break;
                                case FORMAT_LOWER_C: {
                                    SequenceStorage s = null;
                                    if (arg instanceof PBytes) {
                                        s = ((PBytes) arg).getSequenceStorage();
                                    } else if (arg instanceof PByteArray) {
                                        s = ((PByteArray) arg).getSequenceStorage();
                                    }

                                    if (s != null && s.length() == 1) {
                                        writeOutVarNode.writeInt8(varargs, getItemNode.execute(s, 0));
                                    } else {
                                        throw raise(raiseNode, TypeError, ErrorMessages.MUST_BE_BYTE_STRING_LEGTH1_NOT_P, arg);
                                    }
                                    break;
                                }
                                case FORMAT_UPPER_C: {
                                    // TODO(fa): There could be native subclasses (i.e. the Java
                                    // type would not be
                                    // 'String' or 'PString') but we do currently not support this.
                                    if (!(PGuards.isString(arg) && stringLenNode.execute(arg) == 1)) {
                                        throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_UNICODE_CHAR_NOT_P, arg);
                                    }
                                    // TODO(fa) use the sequence lib to get the character once
                                    // available
                                    char singleChar;
                                    if (isJavaString(arg)) {
                                        singleChar = ((String) arg).charAt(0);
                                    } else if (arg instanceof TruffleString) {
                                        singleChar = readCharNode.execute(switchEncodingNode.execute((TruffleString) arg, TruffleString.Encoding.UTF_16), 0);
                                    } else if (arg instanceof PString) {
                                        singleChar = charFromPString((PString) arg, readCharNode, switchEncodingNode);
                                    } else {
                                        throw raise(raiseNode, SystemError, ErrorMessages.UNSUPPORTED_STR_TYPE, arg.getClass());
                                    }
                                    writeOutVarNode.writeInt32(varargs, (int) singleChar);
                                    break;
                                }
                                case FORMAT_LOWER_F:
                                    writeOutVarNode.writeFloat(varargs, (float) asDoubleNode.executeDouble(arg));
                                    break;
                                case FORMAT_LOWER_D:
                                    writeOutVarNode.writeDouble(varargs, asDoubleNode.execute(arg));
                                    break;
                                case FORMAT_UPPER_D:
                                    writeOutVarNode.writeComplex(varargs, asComplexNode.execute(arg));
                                    break;
                                case FORMAT_LOWER_P:
                                    writeOutVarNode.writeInt32(varargs, isTrueNode.execute(null, arg) ? 1 : 0);
                                    break;
                                case FORMAT_UPPER_Y:
                                    if (isBytesProfile.profileClass(getClassNode.execute(arg), PythonBuiltinClassType.PByteArray)) {
                                        writeOutVarNode.writePyObject(varargs, toNativeNode.execute(arg));
                                    } else {
                                        throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_NOT_P, "bytearray", arg);
                                    }
                                    break;
                                case FORMAT_UPPER_U:
                                    if (isBytesProfile.profileClass(getClassNode.execute(arg), PythonBuiltinClassType.PString)) {
                                        writeOutVarNode.writePyObject(varargs, toNativeNode.execute(arg));
                                    } else {
                                        throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_NOT_P, "str", arg);
                                    }
                                    break;
                                default:
                                    throw CompilerDirectives.shouldNotReachHere();
                            }
                        }
                        return formatIdx + 1;
                    }
                    case FORMAT_LOWER_E: {
                        int la1 = formatIdx + 1 < formatLength ? codePoint(format, formatIdx + 1, codepointAtIndexNode) : ' ';
                        int la2 = formatIdx + 2 < formatLength ? codePoint(format, formatIdx + 2, codepointAtIndexNode) : ' ';
                        Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);

                        if (skipOptionalArg(arg, state.restOptional)) {
                            // skip vararg elements
                            getVaArgNode.getVoidPtr(varargs);
                            getVaArgNode.getVoidPtr(varargs);
                            if (la2 == '#') {
                                // skip third vararg element
                                getVaArgNode.getVoidPtr(varargs);
                            }
                        } else {
                            convertExtendedArgNode.execute(c, la1, la2, arg, varargs);
                        }
                        return la2 == '#' ? formatIdx + 3 : formatIdx + 2;
                    }
                    case FORMAT_LOWER_S:
                    case FORMAT_LOWER_Z:
                    case FORMAT_UPPER_Z:
                    case FORMAT_LOWER_Y:
                    case FORMAT_LOWER_U:
                    case FORMAT_UPPER_O:
                    case FORMAT_LOWER_W: {
                        int la = formatIdx + 1 < formatLength ? codePoint(format, formatIdx + 1, codepointAtIndexNode) : ' ';
                        Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);

                        if (skipOptionalArg(arg, state.restOptional)) {
                            // skip vararg element
                            getVaArgNode.getVoidPtr(varargs);
                            if (la == '#' || la == '!' || la == '&') {
                                // skip second vararg element
                                getVaArgNode.getVoidPtr(varargs);
                            }
                        } else {
                            convertArgNode.execute(c, la, arg, varargs);
                        }

                        if (la == '#' || la == '!' || la == '&' || la == '*') {
                            return formatIdx + 2;
                        }
                        return formatIdx + 1;
                    }
                    case FORMAT_PAR_OPEN:
                    case FORMAT_PAR_CLOSE:
                        convertParArgNode.execute(state, kwds, c, kwdnames);
                        return formatIdx + 1;
                    case '|':
                        if (state.restOptional) {
                            raiseNode.raiseIntWithoutFrame(0, SystemError, ErrorMessages.INVALID_FORMAT_STRING_PIPE_SPECIFIED_TWICE, c);
                            throw ParseArgumentsException.raise();
                        }
                        state.restOptional();
                        return formatIdx + 1;
                    case '$':
                        if (state.restKeywordsOnly) {
                            raiseNode.raiseIntWithoutFrame(0, SystemError, ErrorMessages.INVALID_FORMAT_STRING_PIPE_SPECIFIED_TWICE, c);
                            throw ParseArgumentsException.raise();
                        }
                        state.restKeywordsOnly();
                        return formatIdx + 1;
                    /*
                     * All other valid chars should be either removed before getting to
                     * ParseTupleAndKeywordsNode or handled as lookahead in other cases.
                     */
                    default:
                        raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.UNRECOGNIZED_FORMAT_CHAR, c);
                        throw ParseArgumentsException.raise();
                }
            } catch (

            PException e) {
                CompilerDirectives.transferToInterpreter();
                TransformExceptionToNativeNodeGen.getUncached().execute(null, e);
                throw ParseArgumentsException.raise();
            }
        }

        private static int codePoint(TruffleString format, int formatIdx, TruffleString.CodePointAtIndexNode codepointAtIndexNode) {
            return codepointAtIndexNode.execute(format, formatIdx, TS_ENCODING);
        }

        public CExtToNativeNode createTN() {
            return PythonContext.get(this).getCApiContext().getSupplier().createToNativeNode();
        }

        public CExtToNativeNode getUncachedTN() {
            return PythonContext.get(this).getCApiContext().getSupplier().getUncachedToNativeNode();
        }

        public CExtToJavaNode createTJ() {
            return PythonContext.get(this).getCApiContext().getSupplier().createToJavaNode();
        }

        public CExtToJavaNode getUncachedTJ() {
            return PythonContext.get(this).getCApiContext().getSupplier().getUncachedToJavaNode();
        }
    }

    private static boolean skipOptionalArg(Object arg, boolean optional) {
        return arg == null && optional;
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
        private final TruffleString funName;
        private boolean restOptional;
        private boolean restKeywordsOnly;
        private PositionalArgStack v;

        ParserState(TruffleString funName, PositionalArgStack v) {
            this.funName = funName;
            this.restOptional = false;
            this.restKeywordsOnly = false;
            this.v = v;
        }

        void restOptional() {
            restOptional = true;
        }

        void restKeywordsOnly() {
            restKeywordsOnly = true;
        }

        void open(PositionalArgStack nestedArgs) {
            restKeywordsOnly = false;
            v = nestedArgs;
        }

        void close() {
            restKeywordsOnly = false;
            v = v.prev;
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
    @ImportStatic(CExtParseArgumentsNode.class)
    abstract static class ConvertArgNode extends Node {
        public abstract void execute(int c, int la, Object arg, Object varargs) throws InteropException;

        @Specialization(guards = "c == FORMAT_LOWER_Y")
        void doBufferR(@SuppressWarnings("unused") int c, int la, Object arg, Object varargs,
                        @Cached GetNextVaArgNode getVaArgNode,
                        @Cached PCallCExtFunction callGetBufferRwNode,
                        @Shared("writeOutVarNode") @Cached WriteNextVaArgNode writeOutVarNode,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode argToSulongNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException {
            if (la == '*') {
                /* formatIdx++; */
                // 'y*'; output to 'Py_buffer*'
                Object pybufferPtr = getVaArgNode.getPyObjectPtr(varargs);
                getbuffer(callGetBufferRwNode, raiseNode, arg, argToSulongNode, pybufferPtr, true);
            } else {
                Object voidPtr = getVaArgNode.getVoidPtr(varargs);
                Object count = convertbuffer(callGetBufferRwNode, raiseNode, arg, argToSulongNode, voidPtr);
                if (la == '#') {
                    /* formatIdx++; */
                    // 'y#'
                    writeOutVarNode.writeInt64(varargs, count);
                }
            }
        }

        @Specialization(guards = "c == FORMAT_LOWER_S || c == FORMAT_LOWER_Z")
        static void doCString(@SuppressWarnings("unused") int c, int la, Object arg, Object varargs,
                        @Cached GetNextVaArgNode getVaArgNode,
                        @Cached AsCharPointerNode asCharPointerNode,
                        @Cached StringLenNode stringLenNode,
                        @Shared("writeOutVarNode") @Cached WriteNextVaArgNode writeOutVarNode,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            if (la == '*') {
                /* formatIdx++; */
                // 's*' or 'z*'
                getVaArgNode.getPyObjectPtr(varargs);
                // TODO(fa) create Py_buffer
            } else if (la == '#') {
                /* formatIdx++; */
                // 's#' or 'z#'
                if (c == FORMAT_LOWER_Z && PGuards.isPNone(arg)) {
                    writeOutVarNode.writePyObject(varargs, toNativeNode.execute(PythonContext.get(toNativeNode).getNativeNull()));
                    writeOutVarNode.writeInt32(varargs, 0);
                } else if (PGuards.isString(arg)) {
                    // TODO(fa) we could use CStringWrapper to do the copying lazily
                    writeOutVarNode.writePyObject(varargs, asCharPointerNode.execute(arg));
                    writeOutVarNode.writeInt64(varargs, (long) stringLenNode.execute(arg));
                } else {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_GOT_P, c == FORMAT_LOWER_Z ? "str or None" : "str", arg);
                }
            } else {
                // 's' or 'z'
                if (c == FORMAT_LOWER_Z && PGuards.isPNone(arg)) {
                    writeOutVarNode.writePyObject(varargs, toNativeNode.execute(PythonContext.get(toNativeNode).getNativeNull()));
                } else if (PGuards.isString(arg)) {
                    // TODO(fa) we could use CStringWrapper to do the copying lazily
                    writeOutVarNode.writePyObject(varargs, asCharPointerNode.execute(arg));
                } else {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_GOT_P, c == FORMAT_LOWER_Z ? "str or None" : "str", arg);
                }
            }
        }

        @Specialization(guards = "c == FORMAT_UPPER_O")
        static void doObject(@SuppressWarnings("unused") int c, int la, Object arg, Object varargs,
                        @Cached GetNextVaArgNode getVaArgNode,
                        @Cached ExecuteConverterNode executeConverterNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib,
                        @Cached(value = "createTJ()", uncached = "getUncachedTJ()") CExtToJavaNode typeToJavaNode,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode toNativeNode,
                        @Shared("writeOutVarNode") @Cached WriteNextVaArgNode writeOutVarNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            if (la == '!') {
                /* formatIdx++; */
                Object argValue = getVaArgNode.getPyObjectPtr(varargs);
                Object typeObject = typeToJavaNode.execute(argValue);
                assert PGuards.isClass(typeObject, lib);
                if (!isSubtypeNode.execute(getClassNode.execute(arg), typeObject)) {
                    raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.EXPECTED_OBJ_TYPE_P_GOT_P, typeObject, arg);
                    throw ParseArgumentsException.raise();
                }
                writeOutVarNode.writePyObject(varargs, toNativeNode.execute(arg));
            } else if (la == '&') {
                /* formatIdx++; */
                Object converter = getVaArgNode.getPyObjectPtr(varargs);
                Object output = getVaArgNode.getPyObjectPtr(varargs);
                executeConverterNode.execute(converter, arg, output);
            } else {
                writeOutVarNode.writePyObject(varargs, toNativeNode.execute(arg));
            }
        }

        @Specialization(guards = "c == FORMAT_LOWER_W")
        void doBufferRW(@SuppressWarnings("unused") int c, int la, Object arg, Object varargs,
                        @Cached GetNextVaArgNode getVaArgNode,
                        @Cached PCallCExtFunction callGetBufferRwNode,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode toNativeNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            if (la != '*') {
                throw raise(raiseNode, TypeError, ErrorMessages.INVALID_USE_OF_W_FORMAT_CHAR);

            }
            Object pybufferPtr = getVaArgNode.getPyObjectPtr(varargs);
            getbuffer(callGetBufferRwNode, raiseNode, arg, toNativeNode, pybufferPtr, false);
        }

        private void getbuffer(PCallCExtFunction callGetBufferRwNode, PRaiseNativeNode raiseNode, Object arg, CExtToNativeNode toSulongNode, Object pybufferPtr, boolean readOnly)
                        throws ParseArgumentsException {
            NativeCAPISymbol funSymbol = readOnly ? FUN_GET_BUFFER_R : FUN_GET_BUFFER_RW;
            Object rc = callGetBufferRwNode.call(PythonContext.get(this).getCApiContext(), funSymbol, toSulongNode.execute(arg), pybufferPtr);
            if (!(rc instanceof Number)) {
                throw raise(raiseNode, SystemError, ErrorMessages.RETURNED_UNEXPECTE_RET_CODE_EXPECTED_INT_BUT_WAS_S, funSymbol, rc.getClass());
            }
            int i = intValue((Number) rc);
            if (i == -1) {
                throw converterr(raiseNode, readOnly ? ErrorMessages.READ_ONLY_BYTELIKE_OBJ : ErrorMessages.READ_WRITE_BYTELIKE_OBJ, arg);
            } else if (i == -2) {
                throw converterr(raiseNode, ErrorMessages.CONTIGUOUS_BUFFER, arg);
            }
        }

        private static ParseArgumentsException converterr(PRaiseNativeNode raiseNode, TruffleString msg, Object arg) {
            if (arg == PNone.NONE) {
                throw raise(raiseNode, TypeError, ErrorMessages.MUST_BE_S_NOT_NONE, msg);
            }
            throw raise(raiseNode, TypeError, ErrorMessages.MUST_BE_S_NOT_P, msg, arg);
        }

        private int convertbuffer(PCallCExtFunction callConvertbuffer, PRaiseNativeNode raiseNode, Object arg, CExtToNativeNode toSulong, Object voidPtr) {
            Object rc = callConvertbuffer.call(PythonContext.get(this).getCApiContext(), FUN_CONVERTBUFFER, toSulong.execute(arg), voidPtr);
            if (!(rc instanceof Number)) {
                throw CompilerDirectives.shouldNotReachHere("wrong result of internal function");
            }
            int i = intValue((Number) rc);
            // first two results are the error results from getbuffer, the third is the one from
            // convertbuffer
            if (i == -1) {
                throw converterr(raiseNode, ErrorMessages.READ_WRITE_BYTELIKE_OBJ, arg);
            } else if (i == -2) {
                throw converterr(raiseNode, ErrorMessages.CONTIGUOUS_BUFFER, arg);
            } else if (i == -3) {
                throw converterr(raiseNode, ErrorMessages.READ_ONLY_BYTELIKE_OBJ, arg);
            }
            return i;
        }

        @TruffleBoundary
        private static int intValue(Number rc) {
            return rc.intValue();
        }

        private static ParseArgumentsException raise(PRaiseNativeNode raiseNode, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            CompilerDirectives.transferToInterpreter();
            raiseNode.executeInt(null, 0, errType, format, arguments);
            throw ParseArgumentsException.raise();
        }

        public CExtToNativeNode createTN() {
            return PythonContext.get(this).getCApiContext().getSupplier().createToNativeNode();
        }

        public CExtToNativeNode getUncachedTN() {
            return PythonContext.get(this).getCApiContext().getSupplier().getUncachedToNativeNode();
        }

        public CExtToJavaNode createTJ() {
            return PythonContext.get(this).getCApiContext().getSupplier().createToJavaNode();
        }

        public CExtToJavaNode getUncachedTJ() {
            return PythonContext.get(this).getCApiContext().getSupplier().getUncachedToJavaNode();
        }
    }

    /**
     * This node does the conversion of a single specifier and is comparable to CPython's
     * {@code convertsimple} function. Each specifier is implemented in a separate specialization
     * since the different specifiers need a very different set of helper nodes.
     */
    @ImportStatic(CExtParseArgumentsNode.class)
    abstract static class ConvertParArgNode extends Node {
        public abstract void execute(ParserState state, Object kwds, int c, Object kwdnames) throws InteropException;

        @Specialization(guards = "c == FORMAT_PAR_OPEN")
        static void doParOpen(ParserState state, Object kwds, @SuppressWarnings("unused") int c, Object kwdnames,
                        @Cached PySequenceCheckNode sequenceCheckNode,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                        @Cached PythonObjectFactory factory,
                        @Cached GetArgNode getArgNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {

            Object arg = getArgNode.execute(state, kwds, kwdnames, state.restKeywordsOnly);
            if (skipOptionalArg(arg, state.restOptional)) {
                state.open(new PositionalArgStack(factory.createEmptyTuple(), state.v));
                return;
            } else {
                if (!sequenceCheckNode.execute(arg)) {
                    throw raise(raiseNode, TypeError, ErrorMessages.EXPECTED_S_GOT_P, "tuple", arg);
                }
                try {
                    state.open(new PositionalArgStack(constructTupleNode.execute(null, arg), state.v));
                    return;
                } catch (PException e) {
                    throw raise(raiseNode, TypeError, ErrorMessages.FAILED_TO_CONVERT_SEQ);
                }
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "c == FORMAT_PAR_CLOSE")
        static void doParClose(ParserState state, Object kwds, int c, Object kwdnames,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws ParseArgumentsException {
            if (state.v.prev == null) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.LEFT_BRACKET_WO_RIGHT_BRACKET_IN_ARG);
                throw ParseArgumentsException.raise();
            }
            int len = state.v.argv.getSequenceStorage().length();
            // Only check for excess. Too few arguments are checked when obtaining them
            if (len > state.v.argnum) {
                throw raise(raiseNode, TypeError, ErrorMessages.MUST_BE_SEQ_OF_LENGTH_D_NOT_D, state.v.argnum, len);
            }
            state.close();
        }

        private static ParseArgumentsException raise(PRaiseNativeNode raiseNode, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            CompilerDirectives.transferToInterpreter();
            raiseNode.executeInt(null, 0, errType, format, arguments);
            throw ParseArgumentsException.raise();
        }
    }

    /**
     * This node does the conversion of a single specifier and is comparable to CPython's
     * {@code convertsimple} function. Each specifier is implemented in a separate specialization
     * since the different specifiers need a very different set of helper nodes.
     */
    @ImportStatic(CExtParseArgumentsNode.class)
    abstract static class ConvertExtendedArgNode extends Node {
        public abstract void execute(int c, int la1, int la2, Object arg, Object varargs) throws InteropException;

        @Specialization(guards = "c == FORMAT_LOWER_E")
        @SuppressWarnings("unused")
        void doEncodedString(@SuppressWarnings("unused") int c, int la1, int la2, Object arg, Object varargs,
                        @Cached AsCharPointerNode asCharPointerNode,
                        @Cached GetNextVaArgNode getVaArgNode,
                        @Cached(value = "createTJ()", uncached = "getUncachedTJ()") CExtToJavaNode argToJavaNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached WriteNextVaArgNode writeOutVarNode,
                        @Cached PRaiseNativeNode raiseNode) throws InteropException, ParseArgumentsException {
            Object encoding = getVaArgNode.getCharPtr(varargs);
            final boolean recodeStrings;
            if (la1 == 's') {
                recodeStrings = true;
            } else if (la1 == 't') {
                recodeStrings = false;
            } else {
                throw raise(raiseNode, TypeError, ErrorMessages.ESTAR_FORMAT_SPECIFIERS_NOT_ALLOWED, arg);
            }
            // XXX: TODO: actual support for the en-/re-coding of objects, proper error handling
            // TODO(tfel) we could use CStringWrapper to do the copying lazily
            writeOutVarNode.writePyObject(varargs, asCharPointerNode.execute(arg));
            if (la2 == '#') {
                final int size = sizeNode.execute(null, argToJavaNode.execute(arg));
                writeOutVarNode.writeInt64(varargs, size);
            }
        }

        private static ParseArgumentsException raise(PRaiseNativeNode raiseNode, PythonBuiltinClassType errType, TruffleString format, Object... arguments) {
            CompilerDirectives.transferToInterpreter();
            raiseNode.executeInt(null, 0, errType, format, arguments);
            throw ParseArgumentsException.raise();
        }

        public CExtToJavaNode createTJ() {
            return PythonContext.get(this).getCApiContext().getSupplier().createToJavaNode();
        }

        public CExtToJavaNode getUncachedTJ() {
            return PythonContext.get(this).getCApiContext().getSupplier().getUncachedToJavaNode();
        }
    }

    /**
     * Gets a single argument from the arguments tuple or from the keywords dictionary.
     */
    abstract static class GetArgNode extends Node {

        public abstract Object execute(ParserState state, Object kwds, Object kwdnames, boolean keywords_only) throws InteropException;

        @Specialization(guards = {"kwds == null", "!keywordsOnly"})
        @SuppressWarnings("unused")
        static Object doNoKeywords(ParserState state, Object kwds, Object kwdnames, boolean keywordsOnly,
                        @Shared("lenNode") @Cached SequenceNodes.LenNode lenNode,
                        @Shared("getItemNode") @Cached PyTuple_GetItem getItemNode,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) {

            Object out = null;
            assert !keywordsOnly;
            int l = lenNode.execute(state.v.argv);
            if (state.v.argnum < l) {
                out = getItemNode.execute(state.v.argv, state.v.argnum);
            }
            if (out == null && !state.restOptional) {
                raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.S_MISSING_REQUIRED_ARG_POS_D, state.funName, state.v.argnum);
                throw ParseArgumentsException.raise();
            }
            state.v.argnum++;
            return out;
        }

        @Specialization(replaces = "doNoKeywords")
        Object doGeneric(ParserState state, Object kwds, Object kwdnames, boolean keywordsOnly,
                        @Shared("lenNode") @Cached SequenceNodes.LenNode lenNode,
                        @Shared("getItemNode") @Cached PyTuple_GetItem getItemNode,
                        @CachedLibrary(limit = "1") InteropLibrary kwdnamesLib,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PCallCExtFunction callCStringToString,
                        @Shared("raiseNode") @Cached PRaiseNativeNode raiseNode) throws InteropException {

            Object out = null;
            if (!keywordsOnly) {
                int l = lenNode.execute(state.v.argv);
                if (state.v.argnum < l) {
                    out = getItemNode.execute(state.v.argv, state.v.argnum);
                }
            }
            // only the bottom argstack can have keyword names
            if (kwds != null && out == null && state.v.prev == null && kwdnames != null) {
                Object kwdnamePtr = kwdnamesLib.readArrayElement(kwdnames, state.v.argnum);
                // TODO(fa) check if this is the NULL pointer since the kwdnames are always
                // NULL-terminated
                Object kwdname = callCStringToString.call(PythonContext.get(this).getCApiContext(), NativeCAPISymbol.FUN_PY_TRUFFLE_CSTR_TO_STRING, kwdnamePtr);
                if (kwdname instanceof TruffleString) {
                    // the cast to PDict is safe because either it is null or a PDict (ensured by
                    // the guards)
                    out = getItem.execute(((PDict) kwds).getDictStorage(), (TruffleString) kwdname);
                }
            }
            if (out == null && !state.restOptional) {
                raiseNode.raiseIntWithoutFrame(0, TypeError, ErrorMessages.S_MISSING_REQUIRED_ARG_POS_D, state.funName, state.v.argnum);
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
    abstract static class ExecuteConverterNode extends Node {

        private static final Source NFI_SIGNATURE = Source.newBuilder("nfi", "(POINTER,POINTER):SINT32", "exec").build();

        public abstract void execute(Object converter, Object inputArgument, Object outputArgument);

        @Specialization(guards = "!converterLib.isExecutable(converter)")
        static void doExecuteConverterNative(Object converter, Object inputArgument, Object outputArgument,
                        @Cached(value = "parseSignature()", allowUncached = true) Object signature,
                        @CachedLibrary("signature") SignatureLibrary signatureLib,
                        @CachedLibrary(limit = "1") InteropLibrary converterLib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode toNativeNode,
                        @Exclusive @Cached PRaiseNativeNode raiseNode,
                        @Exclusive @Cached ConverterCheckResultNode checkResultNode) {
            Object boundConverter = signatureLib.bind(signature, converter);
            doExecuteConverterGeneric(boundConverter, inputArgument, outputArgument, converterLib, resultLib, toNativeNode, raiseNode, checkResultNode);
        }

        @Specialization(limit = "5", guards = "converterLib.isExecutable(converter)")
        static void doExecuteConverterGeneric(Object converter, Object inputArgument, Object outputArgument,
                        @CachedLibrary("converter") InteropLibrary converterLib,
                        @CachedLibrary(limit = "1") InteropLibrary resultLib,
                        @Cached(value = "createTN()", uncached = "getUncachedTN()") CExtToNativeNode toNativeNode,
                        @Exclusive @Cached PRaiseNativeNode raiseNode,
                        @Exclusive @Cached ConverterCheckResultNode checkResultNode) {
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
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.CALLING_ARG_CONVERTER_FAIL_EXPECTED_D_GOT_P, e.getExpectedMinArity(),
                                e.getActualArity());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                raiseNode.raiseIntWithoutFrame(0, PythonBuiltinClassType.SystemError, ErrorMessages.ARG_CONVERTED_NOT_EXECUTABLE);
            }
            throw ParseArgumentsException.raise();
        }

        static Object parseSignature() {
            return PythonContext.get(null).getEnv().parseInternal(NFI_SIGNATURE).call();
        }

        static CExtToNativeNode createTN() {
            return PythonContext.get(null).getCApiContext().getSupplier().createToNativeNode();
        }

        static CExtToNativeNode getUncachedTN() {
            return PythonContext.get(null).getCApiContext().getSupplier().getUncachedToNativeNode();
        }
    }

    /**
     * Executes a custom argument converter (i.e.
     * {@code int converter_fun(PyObject *arg, void *outVar)}.
     */
    abstract static class ConverterCheckResultNode extends Node {

        public abstract void execute(int statusCode) throws ParseArgumentsException;

        @Specialization(guards = "statusCode != 0")
        static void doSuccess(@SuppressWarnings("unused") int statusCode) {
            // all fine
        }

        @Specialization(guards = "statusCode == 0")
        static void doError(@SuppressWarnings("unused") int statusCode,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached PRaiseNativeNode raiseNode) throws ParseArgumentsException {
            PException currentException = getThreadStateNode.getCurrentException();
            boolean errOccurred = currentException != null;
            if (!errOccurred) {
                // converter should have set exception
                raiseNode.raiseInt(null, 0, TypeError, ErrorMessages.CONVERTER_FUNC_FAILED_TO_SET_ERROR);
            }
            throw ParseArgumentsException.raise();
        }
    }

    static final class ParseArgumentsException extends ControlFlowException {
        private static final long serialVersionUID = 1L;

        static ParseArgumentsException raise() {
            CompilerDirectives.transferToInterpreter();
            throw new ParseArgumentsException();
        }
    }

    public abstract static class SplitFormatStringNode extends Node {

        public abstract TruffleString[] execute(TruffleString format);

        @Specialization(guards = "cachedFormat.equals(format)", limit = "1")
        static TruffleString[] doCached(@SuppressWarnings("unused") TruffleString format,
                        @Cached("format") @SuppressWarnings("unused") TruffleString cachedFormat,
                        @Cached(value = "extractFormatOnly(format)", dimensions = 1) TruffleString[] cachedResult) {
            return cachedResult;
        }

        @Specialization(replaces = "doCached")
        static TruffleString[] doGeneric(TruffleString format,
                        @Cached ConditionProfile hasFunctionNameProfile,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.CodePointLengthNode lengthNode) {
            int len = lengthNode.execute(format, TS_ENCODING);
            int colonIdx = indexOfCodePointNode.execute(format, ':', 0, len, TS_ENCODING);
            if (hasFunctionNameProfile.profile(colonIdx >= 0)) {
                // trim off function name
                return new TruffleString[]{substringNode.execute(format, 0, colonIdx, TS_ENCODING, false), substringNode.execute(format, colonIdx + 1, len - colonIdx - 1, TS_ENCODING, false)};
            }
            return new TruffleString[]{format, T_EMPTY_STRING};
        }

        static TruffleString[] extractFormatOnly(TruffleString format) {
            return doGeneric(format, ConditionProfile.getUncached(), TruffleString.IndexOfCodePointNode.getUncached(), TruffleString.SubstringNode.getUncached(),
                            TruffleString.CodePointLengthNode.getUncached());
        }
    }
}
