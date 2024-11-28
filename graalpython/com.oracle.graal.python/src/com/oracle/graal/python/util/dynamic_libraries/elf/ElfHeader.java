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
package com.oracle.graal.python.util.elf;

import java.nio.ByteBuffer;

public final class ElfHeader {

    private final short type;
    private final short machine;
    private final int version;
    private final long entry;
    private final long phoff;
    private final long shoff;
    private final int flags;
    private final short ehsize;
    private final short phentsize;
    private final short phnum;
    private final short shentsize;
    private final short shnum;
    private final short shstrndx;

    private ElfHeader(short type, short machine, int version, long entry, long phoff, long shoff, int flags, short ehsize, short phentsize, short phnum, short shentsize, short shnum,
                    short shstrndx) {
        this.type = type;
        this.machine = machine;
        this.version = version;
        this.entry = entry;
        this.phoff = phoff;
        this.shoff = shoff;
        this.flags = flags;
        this.ehsize = ehsize;
        this.phentsize = phentsize;
        this.phnum = phnum;
        this.shentsize = shentsize;
        this.shnum = shnum;
        this.shstrndx = shstrndx;
    }

    public short getType() {
        return type;
    }

    public short getMachine() {
        return machine;
    }

    public long getEntry() {
        return entry;
    }

    public long getPhoff() {
        return phoff;
    }

    public long getShoff() {
        return shoff;
    }

    public int getFlags() {
        return flags;
    }

    public short getEhsize() {
        return ehsize;
    }

    public short getPhentsize() {
        return phentsize;
    }

    public short getPhnum() {
        return phnum;
    }

    public short getShentsize() {
        return shentsize;
    }

    public short getShnum() {
        return shnum;
    }

    public short getShstrndx() {
        return shstrndx;
    }

    public int getVersion() {
        return version;
    }

    public static ElfHeader create(ByteBuffer elfFile) {
        // 64 bit
        short type = elfFile.getShort();
        short machine = elfFile.getShort();
        int version = elfFile.getInt();
        long entry = elfFile.getLong();
        long phoff = elfFile.getLong();
        long shoff = elfFile.getLong();
        int flags = elfFile.getInt();
        short ehsize = elfFile.getShort();
        short phentsize = elfFile.getShort();
        short phnum = elfFile.getShort();
        short shentsize = elfFile.getShort();
        short shnum = elfFile.getShort();
        short shstrndx = elfFile.getShort();
        return new ElfHeader(type, machine, version, entry, phoff, shoff, flags, ehsize, phentsize, phnum, shentsize, shnum, shstrndx);
    }
}
