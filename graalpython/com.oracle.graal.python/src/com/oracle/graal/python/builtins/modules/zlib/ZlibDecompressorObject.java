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

import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.truffle.api.object.Shape;

public class ZlibDecompressorObject extends PythonBuiltinObject {

    private final ZLibCompObject compObject; // mq: we are reusing ZLibCompObject to reduce code
                                             // footprint.

    /*
     * zst>avail_in is only 32 bit, so we store the true length separately. Conversion and looping
     * is encapsulated in decompress_buf()
     */
    private long availInReal;
    private boolean needsInput;

    // native
    private ZlibDecompressorObject(Object cls, Shape instanceShape, Object zst, NFIZlibSupport zlibSupport) {
        super(cls, instanceShape);
        this.compObject = new NativeZlibCompObject(cls, instanceShape, zst, zlibSupport);
        this.availInReal = 0;
        this.needsInput = true;
    }

    // Java
    private ZlibDecompressorObject(Object cls, Shape instanceShape, int wbits, byte[] zdict) {
        super(cls, instanceShape);
        this.compObject = new JavaDecompress(cls, instanceShape, wbits, zdict);
        this.availInReal = 0;
        this.needsInput = true;
    }

    public boolean isInitialized() {
        return compObject.isInitialized();
    }

    public boolean isEof() {
        return compObject.isEof();
    }

    public void setEof(boolean eof) {
        compObject.setEof(eof);
    }

    public Object getZst() {
        assert isNative();
        return ((NativeZlibCompObject) compObject).getZst();
    }

    public JavaDecompress getStream() {
        assert !isNative();
        return ((JavaDecompress) compObject);
    }

    public long getAvailInReal() {
        return availInReal;
    }

    public void setAvailInReal(long avail_in_real) {
        this.availInReal = avail_in_real;
    }

    public boolean isNeedsInput() {
        return needsInput;
    }

    public void setNeedsInput(boolean needs_input) {
        this.needsInput = needs_input;
    }

    public PBytes getUnusedData() {
        return compObject.getUnusedData();
    }

    public void setUnusedData(PBytes unusedData) {
        compObject.setUnusedData(unusedData);
    }

    public PBytes getUnconsumedTail() {
        return compObject.getUnconsumedTail();
    }

    public void setUnconsumedTail(PBytes unconsumedTail) {
        compObject.setUnconsumedTail(unconsumedTail);
    }

    public boolean isNative() {
        return compObject instanceof NativeZlibCompObject;
    }

    public static ZlibDecompressorObject createNative(Object cls, Shape instanceShape, Object zst, NFIZlibSupport zlibSupport) {
        return new ZlibDecompressorObject(cls, instanceShape, zst, zlibSupport);
    }

    public static ZlibDecompressorObject createJava(Object cls, Shape instanceShape, int wbits, byte[] zdict) {
        return new ZlibDecompressorObject(cls, instanceShape, wbits, zdict);
    }
}
