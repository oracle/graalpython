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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ElfDynamicSection {

    private static final int DT_NEEDED = 1;
    private static final int DT_STRTAB = 5;
    private static final int DT_STRSZ = 10;
    private static final int DT_SONAME = 14;
    private static final int DT_RPATH = 15;
    private static final int DT_RUNPATH = 29;

    private static final class Entry {
        private final ByteBuffer buffer;

        private Entry(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public int getTag() {
            return (int) buffer.getLong(0);
        }

        public long getValue() {
            return buffer.getLong(8);
        }

        public void setValue(long value) {
            buffer.putLong(8, value);
        }
    }

    private final ByteBuffer elfFile;
    private final Entry[] entries;
    private final ByteBuffer dynamicSection;
    private final ByteBuffer dynamicStringTable;

    private ElfDynamicSection(ElfSectionHeaderTable sht, ByteBuffer dynamicSection, ByteBuffer elfFile) {
        this.elfFile = elfFile;
        this.dynamicSection = dynamicSection;
        this.entries = readEntries(dynamicSection);
        long strTabAddress = Arrays.stream(entries).filter(e -> e.getTag() == DT_STRTAB).map(e -> addressToOffset(sht, e.getValue())).findAny().orElse(0L);
        long strTabSize = Arrays.stream(entries).filter(e -> e.getTag() == DT_STRSZ).map(e -> e.getValue()).findAny().orElse(0L);
        this.dynamicStringTable = elfFile.slice((int) strTabAddress, (int) strTabSize);
        this.dynamicStringTable.order(elfFile.order());
    }

    public static ElfDynamicSection create(ElfSectionHeaderTable sht, ByteBuffer elfFile) {
        ElfSectionHeaderTable.Entry dynamiSHEntry = getDynamiSHEntry(sht);
        if (dynamiSHEntry != null) {
            long offset = dynamiSHEntry.getOffset();
            long size = dynamiSHEntry.getSize();
            var dynSec = elfFile.slice((int) offset, (int) size);
            dynSec.order(elfFile.order());
            return new ElfDynamicSection(sht, dynSec, elfFile);
        } else {
            return null;
        }
    }

    private static long addressToOffset(ElfSectionHeaderTable sht, long offset) {
        for (ElfSectionHeaderTable.Entry e : sht.getEntries()) {
            if (!e.isAllocated()) {
                continue;
            }

            long lower = e.getShAddr();
            long upper = e.getShSize() + e.getShAddr();
            if (offset >= lower && offset < upper) {
                return offset - lower + e.getOffset();
            }
        }
        return offset;
    }

    private static Entry[] readEntries(final ByteBuffer reader) {
        List<Entry> entries = new ArrayList<>();
        for (int cntr = 0; cntr < reader.capacity(); cntr += 16) {
            var slice = reader.slice(cntr, 16);
            slice.order(reader.order());
            entries.add(new Entry(slice));
        }
        return entries.toArray(new Entry[entries.size()]);
    }

    public List<String> getDTNeeded() {
        return getEntry(DT_NEEDED);
    }

    private static Stream<String> splitPaths(String path) {
        return Arrays.asList(path.split(":")).stream();
    }

    public Stream<String> getDTRunPathStream() {
        return getEntryStream(DT_RUNPATH).flatMap(ElfDynamicSection::splitPaths);
    }

    public String getDTSOName() {
        return getSingleEntry(DT_SONAME);
    }

    /**
     * Modifies the SONAME of the Elf file.
     * @return a new ByteBuffer that needs to be appended to the original ELF file
     */
    public ByteBuffer setDTSOName(String newName) {
        /*
          To set the soname we:
          1. Copy the entire string table to the end of the file
          2. Modify the current soname bytes to the new ones in that copy
          3. Modify the DT_STRTAB address to point to this new section
          4. Modify the DT_STRSZ to reflect the new size of this section
        */
        byte foundCnt = 0;
        var newNameBytes = newName.getBytes(Charset.forName("UTF-8"));
        long newDtStrTableOffset = elfFile.capacity();
        long newDtStrTableSize = dynamicStringTable.capacity() + newNameBytes.length);
        for (int i = 0; i < entries.length && foundCnt < 2; i++) {
            var entry = entries[i];
            if (entry.getTag() == DT_STRTAB) {
                foundCnt++;
                entry.setValue(newDtStrTableOffset);
            } else if (entry.getTag() == DT_STRSZ) {
                foundCnt++;
                entry.setValue(newDtStrTableSize);
            }
        }
    }

    public List<String> getDTRPath() {
        return getEntryStream(DT_RPATH).flatMap(ElfDynamicSection::splitPaths).collect(Collectors.toList());
    }

    private static ElfSectionHeaderTable.Entry getDynamiSHEntry(ElfSectionHeaderTable sht) {
        for (ElfSectionHeaderTable.Entry e : sht.getEntries()) {
            if (".dynamic".equals(e.getName(sht))) {
                return e;
            }
        }
        return null;
    }

    private String getSingleEntry(int tag) {
        for (Entry entry : entries) {
            if (entry.getTag() == tag) {
                return getString(entry.getValue());
            }
        }
        return null;
    }

    private List<String> getEntry(int tag) {
        return getEntryStream(tag).collect(Collectors.toList());
    }

    private Stream<String> getEntryStream(int tag) {
        return Arrays.stream(entries).filter(e -> e.getTag() == tag).map(e -> getString(e.getValue()));
    }

    private String getString(long offset) {
        if (offset >= dynamicStringTable.capacity()) {
            return "";
        }

        int pos = (int) offset;
        StringBuilder sb = new StringBuilder();

        byte b = dynamicStringTable.get(pos++);
        while (b != 0) {
            sb.append((char) b);
            b = dynamicStringTable.get(pos++);
        }

        return sb.toString();
    }
}
