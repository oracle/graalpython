/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.runtime.PythonContext;

final class MachOFile extends SharedObject {
    private final ByteBuffer buffer;
    private final MachOHeader mh;
    private final List<MachOLoadCommand> loadCommands;
    private int emptySpace;

    MachOFile(byte[] f, PythonContext context) throws IOException {
        this.buffer = ByteBuffer.wrap(f);
        this.mh = MachOHeader.read(buffer);
        this.loadCommands = new ArrayList<>();
        for (int i = 0; i < mh.nCmds; i++) {
            loadCommands.add(MachOLoadCommand.get(buffer));
        }
        buffer.position(MachOHeader.SIZE64 + mh.sizeOfCmds);
        int zeroBytes = 0;
        while (buffer.get() == 0) {
            zeroBytes++;
        }
        this.emptySpace = zeroBytes;
    }

    public void removeCodeSignature() {
        for (int i = 0; i < loadCommands.size(); ++i) {
            var cmd = loadCommands.get(i);
            if (cmd.cmd == MachOLoadCommand.LC_CODE_SIGNATURE) {
                loadCommands.remove(i);
                emptySpace += cmd.cmdSize;
                break;
            }
        }
    }

    @Override
    public void setId(String newId) throws IOException {
        removeCodeSignature();
        MachOLoadCommand oldIdCommand = null;
        for (int i = 0; i < loadCommands.size(); ++i) {
            var cmd = loadCommands.get(i);
            if (cmd.cmd == MachODylibCommand.LC_ID_DYLIB) {
                oldIdCommand = cmd;
                break;
            }
        }

        MachODylibCommand newCmd;
        if (oldIdCommand != null) {
            newCmd = MachODylibCommand.get(oldIdCommand.content);
        } else {
            newCmd = new MachODylibCommand(MachODylibCommand.LC_ID_DYLIB, MachODylibCommand.SIZE, new byte[0], MachODylibCommand.SIZE, 0, 0, 0);
        }
        newCmd.setName(newId);

        if ((oldIdCommand != null && newCmd.cmdSize - oldIdCommand.cmdSize > emptySpace) || newCmd.cmdSize > emptySpace) {
            throw new IOException("Not enough empty space to change ID");
        }

        loadCommands.remove(oldIdCommand);

        var newBuffer = ByteBuffer.allocate(newCmd.cmdSize);
        newBuffer.order(buffer.order());
        newCmd.put(newBuffer);
        loadCommands.add(MachOLoadCommand.get(newBuffer));

        if (oldIdCommand != null) {
            mh.sizeOfCmds -= oldIdCommand.cmdSize;
            emptySpace += oldIdCommand.cmdSize;
        }
        emptySpace -= newCmd.cmdSize;
        mh.sizeOfCmds += newCmd.cmdSize;
    }

    @Override
    public void changeOrAddDependency(String oldName, String newName) throws IOException {
        removeCodeSignature();

        for (int i = 0; i < loadCommands.size(); ++i) {
            var cmd = loadCommands.get(i);
            if (cmd.cmd == MachODylibCommand.LC_LOAD_DYLIB) {
                var loadCmd = MachODylibCommand.get(cmd.content);
                if (loadCmd.getName().equals(oldName)) {
                    loadCommands.remove(i);
                    emptySpace += cmd.cmdSize;
                    break;
                }
            }
        }

        var newCmd = new MachODylibCommand(MachODylibCommand.LC_LOAD_DYLIB, MachODylibCommand.SIZE, new byte[0], MachODylibCommand.SIZE, 0, 0, 0);
        newCmd.setName(newName);

        if (newCmd.cmdSize > emptySpace) {
            throw new IOException("Not enough empty space to add dependency");
        }

        var newBuffer = ByteBuffer.allocate(newCmd.cmdSize);
        newBuffer.order(buffer.order());
        newCmd.put(newBuffer);
        loadCommands.add(MachOLoadCommand.get(newBuffer));

        mh.nCmds += 1;
        mh.sizeOfCmds += newCmd.cmdSize;
        emptySpace -= newCmd.cmdSize;
    }

    @Override
    public byte[] write() {
        buffer.position(0);
        mh.put(buffer);
        return buffer.array();
    }
}
