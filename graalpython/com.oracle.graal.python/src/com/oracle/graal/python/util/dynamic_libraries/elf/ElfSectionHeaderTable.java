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
import java.util.HashMap;
import java.util.Map;

public final class ElfSectionHeaderTable {
    private static final int ELF64_SHTENT_SIZE = 64;

    private static final int SHF_ALLOC = 0x2;

    public static final class Entry {
        private final int shName;
        private final int shType;
        private final long shFlags;
        private final long shAddr;
        private final long shOffset;
        private final long shSize;
        private final int shLink;
        private final int shInfo;
        private final long shAddralign;
        private final long shEntsize;

        private Entry(int shName, int shType, long shFlags, long shAddr, long shOffset, long shSize, int shLink, int shInfo, long shAddralign, long shEntsize) {
            assert shSize < Long.MAX_VALUE;
            this.shName = shName;
            this.shType = shType;
            this.shFlags = shFlags;
            this.shAddr = shAddr;
            this.shOffset = shOffset;
            this.shSize = shSize;
            this.shLink = shLink;
            this.shInfo = shInfo;
            this.shAddralign = shAddralign;
            this.shEntsize = shEntsize;
        }

        public int getType() {
            return shType;
        }

        protected long getFlags() {
            return shFlags;
        }

        public String getName(ElfSectionHeaderTable sht) {
            return sht.getString(shName);
        }

        public long getOffset() {
            return shOffset;
        }

        public long getSize() {
            return shSize;
        }

        public int getLink() {
            return shLink;
        }

        public long getEntrySize() {
            return shEntsize;
        }

        public boolean isAllocated() {
            return (getFlags() & SHF_ALLOC) != 0;
        }

        public long getShAddr() {
            assert isAllocated();
            return shAddr;
        }

        public long getShAddralign() {
            return shAddralign;
        }

        public long getShEntsize() {
            return shEntsize;
        }

        public long getShFlags() {
            return shFlags;
        }

        public int getShInfo() {
            return shInfo;
        }

        public int getShLink() {
            return shLink;
        }

        public int getShName() {
            return shName;
        }

        public long getShOffset() {
            return shOffset;
        }

        public long getShSize() {
            return shSize;
        }

        public int getShType() {
            return shType;
        }
    }

    private final Entry[] entries;
    private final Map<Integer, String> stringMap;
    private final ByteBuffer stringTable;

    private ElfSectionHeaderTable(Entry[] entries, ByteBuffer stringTable) {
        this.entries = entries;
        this.stringMap = new HashMap<>();
        this.stringTable = stringTable;
    }

    public static ElfSectionHeaderTable create(ElfHeader header, ByteBuffer elfFile) {
        Entry[] entries = new Entry[header.getShnum()];
        elfFile.position((int) header.getShoff());
        for (int cntr = 0; cntr < entries.length; cntr++) {
            entries[cntr] = readEntry(header, elfFile);
        }

        // read string table
        ByteBuffer stringTableData = null;
        if (header.getShstrndx() < entries.length) {
            Entry e = entries[header.getShstrndx()];
            if (e.getSize() > 0) {
                stringTableData = elfFile.slice((int) e.getOffset(), (int) e.getSize());
                stringTableData.order(elfFile.order());
            }
        }
        return new ElfSectionHeaderTable(entries, stringTableData);
    }

    public Entry[] getEntries() {
        return entries;
    }

    public Entry getEntry(String name) {
        for (Entry e : entries) {
            if (e.getName(this).equals(name)) {
                return e;
            }
        }
        return null;
    }

    private String getString(int ind) {
        if (stringTable == null || ind >= stringTable.capacity()) {
            return "";
        }
        String str = stringMap.get(ind);
        if (str == null) {
            final StringBuilder buf = new StringBuilder();
            int pos = ind;
            byte b = stringTable.get(pos++);
            while (b != 0) {
                buf.append((char) b);
                b = stringTable.get(pos++);
            }
            str = buf.toString();
            stringMap.put(ind, str);
        }
        return str;
    }

    private static Entry readEntry(ElfHeader header, ByteBuffer reader) {
        int shName = reader.getInt();
        int shType = reader.getInt();
        long shFlags = reader.getLong();
        long shAddr = reader.getLong();
        long shOffset = reader.getLong();
        long shSize = reader.getLong();
        int shLink = reader.getInt();
        int shInfo = reader.getInt();
        long shAddralign = reader.getLong();
        long shEntsize = reader.getLong();

        reader.position(reader.position() + header.getShentsize() - ELF64_SHTENT_SIZE);

        return new Entry(shName, shType, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAddralign, shEntsize);
    }
}
