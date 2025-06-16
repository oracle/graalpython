/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.zlib;

import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.DEF_BUF_SIZE;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.MAX_WBITS;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.Z_FINISH;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.Z_SYNC_FLUSH;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

public final class JavaCompress extends JavaZlibCompObject {

    private static class GZIPCompressStream extends GZIPOutputStream {

        public GZIPCompressStream(OutputStream out) throws IOException {
            super(out);
        }

        public Deflater getDeflater() {
            return def;
        }
    }

    private record CompressStream(Deflater deflater, ByteArrayOutputStream out, GZIPCompressStream stream) {
    }

    @TruffleBoundary
    private static CompressStream createStream(int level, int wbits) {
        Deflater def;
        GZIPCompressStream stream = null;
        ByteArrayOutputStream out = null;
        if (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16)) {
            // wbits 25..31: gzip container with wrapping
            try {
                out = new ByteArrayOutputStream();
                stream = new GZIPCompressStream(out);
                def = stream.getDeflater();
                def.setLevel(level);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // wbits < 0: generate a RAW stream, i.e., no wrapping
            // Otherwise: wrap stream with zlib header and trailer
            def = new Deflater(level, wbits < 0);
        }
        return new CompressStream(def, out, stream);
    }

    final CompressStream stream;
    final int level;
    final int strategy;

    public JavaCompress(Object cls, Shape instanceShape, int level, int wbits, int strategy, byte[] zdict) {
        super(cls, instanceShape, wbits, zdict);
        this.stream = JavaCompress.createStream(level, wbits);
        this.level = level;
        this.strategy = strategy;
    }

    @TruffleBoundary
    protected void setDeflaterInput(byte[] data, int length) {
        canCopy = inputData == null;
        inputData = data;
        inputLen = length;
        stream.deflater.setInput(data, 0, length);
    }

    @TruffleBoundary
    protected ZLibCompObject copy() {
        assert canCopy();
        JavaCompress obj = PFactory.createJavaZLibCompObjectCompress(PythonLanguage.get(null), level, wbits, strategy, zdict);
        Deflater deflater = obj.stream.deflater();

        obj.setStrategy();
        obj.setDictionary();
        if (inputData != null) {
            // feed the new copy of deflater the same input data
            obj.setDeflaterInput(inputData, inputLen);
            deflater.deflate(new byte[inputLen]);
        }
        return obj;
    }

    @TruffleBoundary
    protected void setStrategy() {
        stream.deflater.setStrategy(strategy);
    }

    protected void setDictionary() {
        if (getZdict().length > 0) {
            stream.deflater.setDictionary(getZdict());
        }
    }

    private static void compress(CompressStream stream, ByteArrayOutputStream out, int mode) {
        byte[] result = new byte[DEF_BUF_SIZE];
        int bytesWritten = result.length;
        while (bytesWritten == result.length) {
            bytesWritten = stream.deflater.deflate(result, 0, result.length, mode);
            out.write(result, 0, bytesWritten);
        }
    }

    @TruffleBoundary
    protected byte[] compress(int mode) {
        if (mode == Z_FINISH) {
            return compressFinish();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        compress(stream, baos, mode);
        return baos.toByteArray();
    }

    @TruffleBoundary
    private static void compressFinish(CompressStream stream, ByteArrayOutputStream out) {
        stream.deflater.finish();
        compress(stream, out, Z_SYNC_FLUSH);
        stream.deflater.end();
    }

    @TruffleBoundary
    private byte[] compressFinish() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        compressFinish(stream, baos);
        setUninitialized();
        return baos.toByteArray();
    }

    @TruffleBoundary
    protected static byte[] compressFinish(byte[] bytes, int length, int level, int wbits, Node node) {
        CompressStream stream = createStream(level, wbits);
        stream.deflater.setInput(bytes, 0, length);
        if (stream.stream != null) {
            try {
                stream.stream.finish();
                return stream.out.toByteArray();
            } catch (ZipException ze) {
                throw PRaiseNode.raiseStatic(node, ZLibError, ze);
            } catch (IOException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        Deflater compresser = stream.deflater();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        compresser.setInput(bytes, 0, length);
        compresser.finish();
        byte[] resultArray = new byte[DEF_BUF_SIZE];
        while (!compresser.finished()) {
            int howmany = compresser.deflate(resultArray);
            baos.write(resultArray, 0, howmany);
        }
        compresser.end();
        return baos.toByteArray();

    }
}
