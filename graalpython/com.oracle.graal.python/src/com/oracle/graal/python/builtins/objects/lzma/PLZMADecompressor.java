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
package com.oracle.graal.python.builtins.objects.lzma;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.oracle.graal.python.builtins.modules.LZMAModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;

import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.XZFormatException;
import org.tukaani.xz.XZInputStream;

public class PLZMADecompressor extends PythonObject {

    private final int memlimit;
    private int format;
    private boolean eof;
    private boolean needsInput;

    public PLZMADecompressor(LazyPythonClass clazz, DynamicObject storage, int format, int memlimit) {
        super(clazz, storage);
        this.format = format;
        this.memlimit = memlimit;
    }

    public int getMemlimit() {
        return memlimit;
    }

    @TruffleBoundary
    public byte[] decompress(byte[] data) throws IOException {
        if (data.length == 0) {
            eof = true;
            needsInput = false;
            return data;
        }

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            InputStream xzStream = create(bis);
            return doDecompression(xzStream, bis);
        } catch (XZFormatException xze) {
            // only retry if format was AUTO because we first tried XZ and now we try LZMA
            if (format == LZMAModuleBuiltins.FORMAT_AUTO) {
                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                return doDecompression(createLZMA(bis), bis);
            }
            throw xze;
        }
    }

    private byte[] doDecompression(InputStream is, ByteArrayInputStream bis) throws IOException {
        int n = is.available();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(n);
        byte[] result = new byte[4096];
        int read = -1;

        while ((read = is.read(result)) > 0) {
            bos.write(result, 0, read);
        }
        eof = read == -1;
        needsInput = is.available() == 0 && bis.available() > 0;

        return bos.toByteArray();
    }

    private InputStream create(ByteArrayInputStream bis) throws IOException {
        switch (format) {
            case LZMAModuleBuiltins.FORMAT_AUTO:
            case LZMAModuleBuiltins.FORMAT_XZ:
                return createXZ(bis);

            case LZMAModuleBuiltins.FORMAT_ALONE:
                return createLZMA(bis);

            case LZMAModuleBuiltins.FORMAT_RAW:
            default:
                throw new IllegalStateException();
        }
    }

    private XZInputStream createXZ(ByteArrayInputStream bis) throws IOException {
        return new XZInputStream(bis, memlimit);
    }

    private LZMAInputStream createLZMA(ByteArrayInputStream bis) throws IOException {
        return new LZMAInputStream(bis, memlimit);
    }

    public boolean getEof() {
        return eof;
    }

    public boolean isNeedsInput() {
        return needsInput;
    }
}
