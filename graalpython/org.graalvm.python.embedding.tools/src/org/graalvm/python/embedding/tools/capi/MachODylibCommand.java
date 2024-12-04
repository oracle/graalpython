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
package org.graalvm.python.embedding.tools.capi;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * A {@link MachOLoadCommand} reinterpreted as a dylib_command.
 *
 * <pre>
 * struct dylib_command {
 *  uint32_t cmd;
 *  uint32_t cmdsize;
 *  struct dylib dylib;
 * };
 *
 * struct dylib {
 *  uint32_t name;
 *  uint32_t timestamp;
 *  uint32_t current_version;
 *  uint32_t compatibility_version;
 * };
 * </pre>
*/
final class MachODylibCommand {

    static final int SIZE = 6 * Integer.BYTES;
    static final int LC_ID_DYLIB = 0x0000000D;
    static final int LC_LOAD_DYLIB = 0x0000000C;

    int cmd;
    int cmdSize;
    int offsetToName;
    int timestamp;
    int currentVersion;
    int compatibilityVersion;

    /**
     * Part of the buffer, calculated via offsetToName
     */
    private byte[] paddedName;

    MachODylibCommand(int cmd, int cmdSize, byte[] paddedName, int offsetToName, int timestamp, int currentVersion, int compatibilityVersion) {
        this.cmd = cmd;
        this.cmdSize = cmdSize;
        this.paddedName = paddedName;
        this.offsetToName = offsetToName;
        this.timestamp = timestamp;
        this.currentVersion = currentVersion;
        this.compatibilityVersion = compatibilityVersion;
    }

    static MachODylibCommand get(ByteBuffer buffer) {
        int pos = buffer.position();

        int cmd = buffer.getInt();
        if (cmd != LC_LOAD_DYLIB && cmd != LC_ID_DYLIB) {
            buffer.position(pos);
            return null;
        }
        int cmdSize = buffer.getInt();
        int offsetToName = buffer.getInt();
        int timestamp = buffer.getInt();
        int currentVersion = buffer.getInt();
        int compatibilityVersion = buffer.getInt();

        buffer.position(pos + offsetToName);
        var paddedName = new byte[cmdSize - offsetToName];
        buffer.get(paddedName);
        return new MachODylibCommand(cmd, cmdSize, paddedName, offsetToName, timestamp, currentVersion, compatibilityVersion);
    }

    public void setName(String name) {
        byte[] nameBytes = name.getBytes(Charset.forName("UTF-8"));
        int nameLen = nameBytes.length;
        int paddedLen = (nameLen & ~(Long.BYTES - 1)) + Long.BYTES;
        byte[] paddedNameBytes = new byte[paddedLen];
        System.arraycopy(nameBytes, 0, paddedNameBytes, 0, paddedLen);
        this.paddedName = paddedNameBytes;
        this.cmdSize = MachODylibCommand.SIZE + paddedLen;
    }

    void put(ByteBuffer f) {
        f.putInt(cmd);
        f.putInt(cmdSize);
        f.putInt(SIZE);
        f.putInt(timestamp);
        f.putInt(currentVersion);
        f.putInt(compatibilityVersion);
        f.put(paddedName);
    }

    public String getName() {
        return new String(paddedName, Charset.forName("UTF-8")).trim();
    }
}
