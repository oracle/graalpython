/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util.elf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ElfReader {
    private static final int EI_NIDENT = 16;
    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;
    private static final int ELFDATA2MSB = 2;
    private static final int ELFCLASS64 = 2;

    private ElfReader() {
    }

    static ByteBuffer create(ByteBuffer byteSequence) {
        checkIdent(byteSequence);
        if (!is64Bit(byteSequence)) {
            throw new RuntimeException("Only 64bit ELF files are supported");
        }
        if (isBigEndian(byteSequence)) {
            byteSequence.order(ByteOrder.BIG_ENDIAN);
        } else {
            byteSequence.order(ByteOrder.LITTLE_ENDIAN);
        }
        return byteSequence;
    }

    private static boolean isBigEndian(ByteBuffer ident) {
        return ident.get(EI_DATA) == ELFDATA2MSB;
    }

    private static boolean is64Bit(ByteBuffer ident) {
        return ident.get(EI_CLASS) == ELFCLASS64;
    }

    private static void checkIdent(ByteBuffer ident) {
        checkIdentByte(ident, 0, 0x7f);
        checkIdentByte(ident, 1, 'E');
        checkIdentByte(ident, 2, 'L');
        checkIdentByte(ident, 3, 'F');
    }

    private static void checkIdentByte(ByteBuffer ident, int ind, int val) {
        if (ident.get(ind) != val) {
            throw new RuntimeException("Invalid ELF file!");
        }
    }
}
