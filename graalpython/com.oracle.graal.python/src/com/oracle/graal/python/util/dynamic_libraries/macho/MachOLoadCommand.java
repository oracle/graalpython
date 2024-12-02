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
package com.oracle.graal.python.util.dynamic_libraries.macho;

import java.nio.ByteBuffer;

/**
 * A load command, without much interpretation. Stores the entire command as a byte array, and the
 * cmd id and size as fields.
 *
 * <pre>
 * struct load_command {
 *  uint32_t cmd;
 *  uint32_t cmdsize;
 *  ...
 * };
 * </pre>
*/
final class MachOLoadCommand {
    static final int LC_CODE_SIGNATURE = 0x0000001D;

    final int cmd;
    final int cmdSize;
    final ByteBuffer content;

    MachOLoadCommand(int cmd, int cmdSize, ByteBuffer content) {
        this.cmd = cmd;
        this.cmdSize = cmdSize;
        this.content = content;
    }

    static MachOLoadCommand get(ByteBuffer buffer) {
        int pos = buffer.position();
        int cmd = buffer.getInt();
        int cmdSize = buffer.getInt();
        var content = buffer.slice(pos, cmdSize);
        content.order(buffer.order());
        buffer.position(pos + cmdSize);
        return new MachOLoadCommand(cmd, cmdSize, content);
    }

    void put(ByteBuffer f) {
        f.put(content);
    }
}
