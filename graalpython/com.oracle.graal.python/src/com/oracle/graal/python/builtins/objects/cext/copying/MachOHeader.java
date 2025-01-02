/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.copying;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MachOHeader {

    static final int SIZE64 = 8 * Integer.BYTES;
    static final int MAGIC64 = 0xFEEDFACF;
    static final int CIGAM_64 = 0xCFFAEDFE;

    // see e.g. https://llvm.org/doxygen/structllvm_1_1MachO_1_1mach__header__64.html
    final int magic;
    final int cpuType;
    final int cpuSubType;
    final int fileType;
    int nCmds;
    int sizeOfCmds;
    final int flags;
    final int reserved;

    MachOHeader(int magic, int cpuType, int cpuSubType, int fileType, int nrOfCmds, int sizeOfCmds, int flags, int reserved) {
        this.magic = magic;
        this.cpuType = cpuType;
        this.cpuSubType = cpuSubType;
        this.fileType = fileType;
        this.nCmds = nrOfCmds;
        this.sizeOfCmds = sizeOfCmds;
        this.flags = flags;
        this.reserved = reserved;
    }

    static MachOHeader read(ByteBuffer f) throws IOException {
        int magic = f.getInt();
        switch (magic) {
            case MAGIC64:
                // Ok
                break;
            case CIGAM_64:
                f.order(f.order() == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
                magic = MAGIC64;
                break;
            default:
                throw new IOException("Unsupported magic");
        }
        int cpuType = f.getInt();
        int cpuSubType = f.getInt();
        int fileType = f.getInt();
        int nrOfCmds = f.getInt();
        int sizeOfCmds = f.getInt();
        int flags = f.getInt();
        int reserved = f.getInt();

        return new MachOHeader(magic, cpuType, cpuSubType, fileType, nrOfCmds, sizeOfCmds, flags, reserved);
    }

    void put(ByteBuffer f) {
        f.putInt(magic);
        f.putInt(cpuType);
        f.putInt(cpuSubType);
        f.putInt(fileType);
        f.putInt(nCmds);
        f.putInt(sizeOfCmds);
        f.putInt(flags);
        f.putInt(reserved);
    }
}
