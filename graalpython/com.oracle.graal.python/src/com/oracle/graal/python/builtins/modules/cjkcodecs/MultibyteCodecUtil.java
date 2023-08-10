/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.MAXSIZE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.ERROR_IGNORE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.ERROR_REPLACE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.ERROR_STRICT;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBENC_FLUSH;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBENC_MAX;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_INTERNAL;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_TOOFEW;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_TOOSMALL;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_END;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_REASON;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_START;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.UNICODE_ERROR_ATTR_FACTORY;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODING_ERROR_HANDLER_MUST_RETURN;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODING_ERROR_HANDLER_MUST_RETURN;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_MULTIBYTE_SEQUENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.INCOMPLETE_MULTIBYTE_SEQUENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.INTERNAL_CODEC_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.POSITION_D_FROM_ERROR_HANDLER_OUT_OF_BOUNDS;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_RUNTIME_ERROR;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.nio.CharBuffer;

import com.oracle.graal.python.builtins.modules.codecs.CodecsRegistry.PyCodecLookupErrorNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionAttrNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class MultibyteCodecUtil {

    protected static final char Py_UNICODE_REPLACEMENT_CHARACTER = 0xFFFD;

    protected static final int MULTIBYTECODECSTATE = 8; // CPython state size

    static final int MBENC_RESET = MBENC_MAX << 1; /* reset after an encoding session */

    @TruffleBoundary
    protected static MultibyteCodec findCodec(MultibyteCodec[] list, TruffleString enc,
                    TruffleString.EqualNode isEqual) {
        for (MultibyteCodec codec : list) {
            if (codec != null && isEqual.execute(codec.encoding, enc, TS_ENCODING)) {
                return codec;
            }
        }
        return null;
    }

    @TruffleBoundary
    protected static CharBuffer writerInit(int len) {
        return CharBuffer.allocate(len);
    }

    @TruffleBoundary
    static TruffleString internalErrorCallback(TruffleString errors,
                    TruffleString.EqualNode isEqual) {
        if (errors == null || isEqual.execute(errors, ERROR_STRICT, TS_ENCODING)) {
            return ERROR_STRICT;
        } else if (isEqual.execute(errors, ERROR_IGNORE, TS_ENCODING)) {
            return ERROR_IGNORE;
        } else if (isEqual.execute(errors, ERROR_REPLACE, TS_ENCODING)) {
            return ERROR_REPLACE;
        } else {
            return errors;
        }
    }

    @GenerateInline
    @GenerateCached(alwaysInlineCached = true)
    abstract static class CallErrorCallbackNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object errors, Object exc);

        // call_error_callback
        @Specialization
        static Object callErrorCallback(Node inliningTarget, Object errors, Object exc,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyCodecLookupErrorNode lookupErrorNode,
                        @Cached(inline = false) CallNode callNode) {
            assert (PyUnicodeCheckNode.executeUncached(errors));
            TruffleString str = castToStringNode.execute(inliningTarget, errors);
            Object cb = lookupErrorNode.execute(inliningTarget, str);
            return callNode.execute(cb, exc);
        }
    }

    abstract static class EncodeErrorNode extends PNodeWithRaise {

        private static final CharBuffer REPLACEMENT = CharBuffer.wrap("?");

        abstract int execute(VirtualFrame frame, MultibyteCodec codec,
                        MultibyteCodecState state,
                        MultibyteEncodeBuffer buf,
                        Object errors, int e);

        // multibytecodec_encerror
        @Specialization
        int encerror(VirtualFrame frame, MultibyteCodec codec,
                        MultibyteCodecState state,
                        MultibyteEncodeBuffer buf, Object errors, int e,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached CastToTruffleStringNode toTString,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyBytesCheckNode bytesCheckNode,
                        @Cached PyLongAsIntNode asSizeNode,
                        @Cached(inline = true) CallErrorCallbackNode callErrorCallbackNode,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached EncodeNode encodeNode) {

            TruffleString reason = ILLEGAL_MULTIBYTE_SEQUENCE;
            int esize = e;
            if (e < 0) {
                switch (e) {
                    case MBERR_TOOSMALL:
                        buf.expandOutputBuffer(-1, getRaiseNode());
                        return 0; /* retry it */
                    case MBERR_TOOFEW:
                        reason = INCOMPLETE_MULTIBYTE_SEQUENCE;
                        esize = buf.getInpos();
                        break;
                    case MBERR_INTERNAL:
                        throw raise(RuntimeError, INTERNAL_CODEC_ERROR);
                    default:
                        throw raise(RuntimeError, UNKNOWN_RUNTIME_ERROR);
                }
            }

            if (errors == ERROR_REPLACE) {
                CharBuffer origInbuf = buf.inputBuffer;
                buf.inputBuffer = REPLACEMENT;
                buf.rewindInbuf();
                int r;
                for (;;) {
                    r = codec.encode(state, buf, 0);
                    if (r == MBERR_TOOSMALL) {
                        buf.expandOutputBuffer(-1, getRaiseNode());
                    } else {
                        break;
                    }
                }

                buf.inputBuffer = origInbuf;

                if (r != 0) {
                    buf.expandOutputBuffer(1, getRaiseNode());
                    buf.append('?');
                }
            }
            if (errors == ERROR_IGNORE || errors == ERROR_REPLACE) {
                buf.incInpos(esize);
                return 0;
            }

            int start = buf.getInpos();
            int end = start + esize;

            /* use cached exception object if available */
            if (buf.excobj == null) {
                buf.excobj = factory.createBaseException(UnicodeEncodeError);
                TruffleString encoding = codec.encoding;
                Object[] args = new Object[]{encoding, buf.toTString(), start, end, reason};
                buf.excobj.setArgs(factory.createTuple(args));
                buf.excobj.setExceptionAttributes(args);
            } else {
                attrNode.execute(buf.excobj, start, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
                attrNode.execute(buf.excobj, end, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
                attrNode.execute(buf.excobj, reason, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY);
            }

            if (errors == ERROR_STRICT) {
                throw getRaiseNode().raiseExceptionObject(buf.excobj);
                // PyCodec_StrictErrors(buf.excobj);
            }

            Object retobj = callErrorCallbackNode.execute(frame, inliningTarget, errors, buf.excobj);

            boolean isError = !(retobj instanceof PTuple);
            Object tobj = null;
            Object newposobj = null;
            boolean isUnicode = false;
            if (!isError) {
                PTuple tuple = (PTuple) retobj;
                Object[] array = getArray.execute(inliningTarget, tuple.getSequenceStorage());
                isError = array.length != 2;
                if (!isError) {
                    tobj = array[0];
                    newposobj = array[1];
                    isUnicode = unicodeCheckNode.execute(inliningTarget, tobj);
                    isError = !isUnicode && !bytesCheckNode.execute(inliningTarget, tobj);
                    isError = isError || !longCheckNode.execute(inliningTarget, newposobj);
                }
            }

            if (isError) {
                throw raise(TypeError, ENCODING_ERROR_HANDLER_MUST_RETURN);
            }

            PBytes retstr;
            if (isUnicode) {
                TruffleString str = toTString.execute(inliningTarget, tobj);
                int datalen = codePointLengthNode.execute(str, TS_ENCODING);
                retstr = encodeEmptyInput(datalen, MBENC_FLUSH, factory);
                if (retstr == null) {
                    MultibyteEncodeBuffer tmpbuf = new MultibyteEncodeBuffer(str);
                    retstr = encodeNode.execute(frame, codec, state, tmpbuf, ERROR_STRICT, MBENC_FLUSH,
                                    factory);
                }
            } else {
                retstr = (PBytes) tobj;
            }

            byte[] retstrbytes = toBytesNode.execute(retstr);
            int retstrsize = retstrbytes.length;
            if (retstrsize > 0) {
                buf.expandOutputBuffer(retstrsize, getRaiseNode());
                buf.append(retstrbytes);
            }

            int newpos = 0;
            try {
                newpos = asSizeNode.execute(frame, inliningTarget, newposobj);
                if (newpos < 0) {
                    newpos += buf.getInlen();
                }
            } catch (PException exception) {
                throw raise(IndexError, POSITION_D_FROM_ERROR_HANDLER_OUT_OF_BOUNDS, newpos);
            }
            if (newpos < 0 || newpos > buf.getInlen()) {
                throw raise(IndexError, POSITION_D_FROM_ERROR_HANDLER_OUT_OF_BOUNDS, newpos);
            }

            buf.setInpos(newpos);

            return 0;
        }
    }

    abstract static class DecodeErrorNode extends PNodeWithRaise {

        abstract void execute(VirtualFrame frame, MultibyteCodec codec,
                        // MultibyteCodecState state,
                        MultibyteDecodeBuffer buf, TruffleString errors, int e);

        // multibytecodec_decerror
        @Specialization
        void decerror(VirtualFrame frame, MultibyteCodec codec,
                        // MultibyteCodecState state,
                        MultibyteDecodeBuffer buf, TruffleString errors, int e,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyLongAsIntNode asSizeNode,
                        @Cached CastToJavaStringNode toString,
                        @Cached(inline = true) CallErrorCallbackNode callErrorCallbackNode) {

            TruffleString reason = ILLEGAL_MULTIBYTE_SEQUENCE;
            int esize = e;

            if (e < 0) {
                switch (e) {
                    case MBERR_TOOSMALL:
                        // retry it
                        return;
                    case MBERR_TOOFEW:
                        reason = INCOMPLETE_MULTIBYTE_SEQUENCE;
                        esize = buf.remaining();
                        break;
                    case MBERR_INTERNAL:
                        throw raise(RuntimeError, INTERNAL_CODEC_ERROR);
                    default:
                        throw raise(RuntimeError, UNKNOWN_RUNTIME_ERROR);
                }
            }

            if (errors == ERROR_REPLACE) {
                buf.writeChar(Py_UNICODE_REPLACEMENT_CHARACTER);
            }
            if (errors == ERROR_IGNORE || errors == ERROR_REPLACE) {
                buf.incInpos(esize);
                return;
            }

            int start = buf.getInpos();
            int end = start + esize;

            /* use cached exception object if available */
            if (buf.excobj == null) {
                buf.excobj = factory.createBaseException(UnicodeDecodeError);
                PBytes inbuf = buf.createPBytes(factory);
                TruffleString encoding = codec.encoding;
                Object[] args = new Object[]{encoding, inbuf, buf.getInpos(), start, end, reason};
                buf.excobj.setArgs(factory.createTuple(args));
                buf.excobj.setExceptionAttributes(args);
            } else {
                attrNode.execute(buf.excobj, start, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
                attrNode.execute(buf.excobj, end, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
                attrNode.execute(buf.excobj, reason, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY);
            }

            if (errors == ERROR_STRICT) {
                throw getRaiseNode().raiseExceptionObject(buf.excobj);
                // PyCodec_StrictErrors(buf.excobj);
            }

            Object retobj = callErrorCallbackNode.execute(frame, inliningTarget, errors, buf.excobj);

            boolean isError = !(retobj instanceof PTuple);
            Object retuni = null;
            Object newposobj = null;
            if (!isError) {
                PTuple tuple = (PTuple) retobj;
                Object[] array = getArray.execute(inliningTarget, tuple.getSequenceStorage());
                isError = array.length != 2;
                if (!isError) {
                    retuni = array[0];
                    newposobj = array[1];
                    isError = !unicodeCheckNode.execute(inliningTarget, retuni);
                    isError = isError || !longCheckNode.execute(inliningTarget, newposobj);
                }
            }

            if (isError) {
                throw raise(TypeError, DECODING_ERROR_HANDLER_MUST_RETURN);
            }

            buf.writeStr(toString.execute(retuni));

            int newpos = -1;
            try {
                newpos = asSizeNode.execute(frame, inliningTarget, newposobj);
                if (newpos < 0) {
                    newpos += buf.getInpos();
                }
            } catch (PException ee) {
                throw raise(IndexError, POSITION_D_FROM_ERROR_HANDLER_OUT_OF_BOUNDS, newpos);
            }
            if (newpos > buf.getInSize()) {
                throw raise(IndexError, POSITION_D_FROM_ERROR_HANDLER_OUT_OF_BOUNDS, newpos);
            }

            buf.setInpos(newpos);
        }
    }

    protected static PBytes encodeEmptyInput(int len, int flags,
                    PythonObjectFactory factory) {
        if (len == 0 && (flags & MBENC_RESET) == 0) {
            return factory.createBytes(EMPTY_BYTE_ARRAY);
        }
        return null;
    }

    abstract static class EncodeNode extends PNodeWithRaise {

        abstract PBytes execute(VirtualFrame frame, MultibyteCodec codec, MultibyteCodecState state, MultibyteEncodeBuffer buf, Object errors, int flags,
                        PythonObjectFactory factory);

        // multibytecodec_encode
        @Specialization
        PBytes encode(VirtualFrame frame, MultibyteCodec codec, MultibyteCodecState state, MultibyteEncodeBuffer buf, Object errors, int flags,
                        PythonObjectFactory factory,
                        @Cached EncodeErrorNode encodeErrorNode) {

            // if (buf.inlen == 0 && (flags & MBENC_RESET) == 0) {
            // return factory.createBytes(EMPTY_BYTE_ARRAY);
            // }

            if (buf.getInlen() > (MAXSIZE - 16) / 2) {
                throw raise(MemoryError);
            }

            while (!buf.isFull()) {
                /*
                 * we don't reuse inleft and outleft here. error callbacks can relocate the cursor
                 * anywhere on buffer
                 */

                // data, buf.inpos, buf.inlen, buf.outbuf, (buf.outbuf.length - buf.outbufIdx)
                int r = codec.encode(state, buf, flags);
                if ((r == 0) || (r == MBERR_TOOFEW && (flags & MBENC_FLUSH) == 0)) {
                    break;
                } else {
                    encodeErrorNode.execute(frame, codec, state, buf, errors, r);
                    if (r == MBERR_TOOFEW) {
                        break;
                    }
                }
            }

            if (codec.canEncreset() && (flags & MBENC_RESET) != 0) {
                while (true) {
                    // buf.outbuf, (buf.outbuf.length - buf.outbufIdx)
                    int r = codec.encreset(state, buf);
                    if (r == 0) {
                        break;
                    } else {
                        encodeErrorNode.execute(frame, codec, state, buf, errors, r);
                    }
                }
            }

            return buf.createPBytes(factory);
        }

    }
}
