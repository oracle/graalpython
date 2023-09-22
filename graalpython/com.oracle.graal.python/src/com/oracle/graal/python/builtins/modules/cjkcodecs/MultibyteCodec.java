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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.nio.charset.Charset;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class MultibyteCodec {

    enum CodecType {
        STATELESS_WINIT,
        // CODEC_INIT, ENCODER, DECODER
        // big5hkscs

        STATELESS,
        // ENCODER, DECODER
        // gb2312
        // gbk
        // gb18030
        // shift_jis
        // cp932
        // euc_jp
        // shift_jis_2004
        // euc_jis_2004
        // euc_jisx0213
        // shift_jisx0213
        // euc_kr
        // cp949
        // johab
        // big5
        // cp950

        STATEFUL,
        // ENCODER_INIT, ENCODER_RESET, DECODER_INIT, DECODER_RESET, ENCODER, DECODER
        // hz

        ISO2022,
        // CODEC_INIT, ENCODER_INIT, ENCODER_RESET, DECODER_INIT, DECODER_RESET, ENCODER, DECODER
        // iso2022_kr
        // iso2022_jp
        // iso2022_jp_1
        // iso2022_jp_2
        // iso2022_jp_2004
        // iso2022_jp_3
        // iso2022_jp_ext
    }

    protected final TruffleString encoding;
    protected final Charset charset;
    protected final CodecType type;

    protected MultibyteCodec(TruffleString encoding, Charset charset, CodecType type) {
        this.encoding = encoding;
        this.charset = charset;
        this.type = type;
    }

    protected boolean canEncreset() {
        return type == CodecType.ISO2022 || type == CodecType.STATEFUL;
    }

    public void codecinit() {
        // nothing to do..
    }

    public MultibyteCodecState encinit(TruffleString errors) {
        return MultibyteCodecState.encoder(charset, errors);
    }

    @TruffleBoundary
    public int encode(MultibyteCodecState state, MultibyteEncodeBuffer buf, int flags) {
        MultibyteCodecState.Encoder encoder = (MultibyteCodecState.Encoder) state;
        return encoder.encode(buf, flags);
    }

    public int encreset(MultibyteCodecState state, MultibyteEncodeBuffer buf) {
        MultibyteCodecState.Encoder encoder = (MultibyteCodecState.Encoder) state;
        return encoder.encreset(buf);
    }

    public MultibyteCodecState decinit(TruffleString errors) {
        return MultibyteCodecState.decoder(charset, errors);
    }

    @TruffleBoundary
    public int decode(MultibyteCodecState state, MultibyteDecodeBuffer buf, Node raisingNode) {
        MultibyteCodecState.Decoder decoder = (MultibyteCodecState.Decoder) state;
        return decoder.decode(buf, raisingNode);
    }

    public void decreset(MultibyteCodecState state) {
        MultibyteCodecState.Decoder decoder = (MultibyteCodecState.Decoder) state;
        decoder.decreset();
    }

    @TruffleBoundary
    public int getErrorLength(MultibyteCodecState state) {
        return state.coderResult.length();
    }

    private TruffleString getErrorReason(MultibyteCodecState state) {
        if (state.coderResult.isMalformed()) {
            return ErrorMessages.MALFORMED_INPUT;
        } else if (state.coderResult.isUnmappable()) {
            return ErrorMessages.UNMAPPABLE_CHARACTER;
        } else {
            throw new IllegalArgumentException("Unicode error constructed from non-error result");
        }
    }
}
