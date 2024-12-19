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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;

final class MachOFile extends SharedObject {
    private final PythonContext context;
    private final ByteBuffer buffer;
    private final MachOHeader mh;
    private final List<MachOLoadCommand> loadCommands;
    private int emptySpace;

    MachOFile(byte[] f, PythonContext context) throws IOException {
        this.context = context;
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

    private void removeCommand(MachOLoadCommand cmd) {
        loadCommands.remove(cmd);
        mh.nCmds -= 1;
        mh.sizeOfCmds -= cmd.cmdSize;
        emptySpace += cmd.cmdSize;
    }

    private void addCommand(MachODylibCommand cmd) throws IOException {
        if (cmd.cmdSize > emptySpace) {
            throw new IOException(String.format("Not enough empty space add new cmd with string %s.", cmd.getName()));
        }
        var newBuffer = ByteBuffer.allocate(cmd.cmdSize);
        newBuffer.order(buffer.order());
        cmd.put(newBuffer);
        assert newBuffer.position() == cmd.cmdSize;
        newBuffer.position(0);
        var newCmd = MachOLoadCommand.get(newBuffer);
        loadCommands.add(newCmd);
        assert newCmd.cmdSize == cmd.cmdSize;
        mh.nCmds += 1;
        mh.sizeOfCmds += cmd.cmdSize;
        emptySpace -= cmd.cmdSize;
    }

    private void removeId() {
        for (int i = 0; i < loadCommands.size(); ++i) {
            var cmd = loadCommands.get(i);
            if (cmd.cmd == MachODylibCommand.LC_ID_DYLIB) {
                removeCommand(cmd);
            }
        }
    }

    private void removeLoad(String oldName) {
        for (int i = 0; i < loadCommands.size(); ++i) {
            var cmd = loadCommands.get(i);
            if (cmd.cmd == MachODylibCommand.LC_LOAD_DYLIB) {
                var loadCmd = MachODylibCommand.get(cmd.content);
                if (loadCmd.getName().equals(oldName)) {
                    removeCommand(cmd);
                    LOGGER.fine(() -> String.format("Removing LC_LOAD_DYLIB %s. New empty space is %d.", oldName, emptySpace));
                }
            }
        }
    }

    @Override
    public void setId(String newId) throws IOException {
        removeId();

        var newCmd = new MachODylibCommand(MachODylibCommand.LC_ID_DYLIB, MachODylibCommand.SIZE, new byte[0], MachODylibCommand.SIZE, 0, 0, 0);
        newCmd.setName(newId);
        addCommand(newCmd);

        LOGGER.fine(() -> String.format("Added LC_ID_DYLIB %s. New empty space is %d.", newId, emptySpace));
    }

    @Override
    public void changeOrAddDependency(String oldName, String newName) throws IOException {
        removeLoad(oldName);

        var newCmd = new MachODylibCommand(MachODylibCommand.LC_LOAD_DYLIB, MachODylibCommand.SIZE, new byte[0], MachODylibCommand.SIZE, 0, 0, 0);
        newCmd.setName(newName);
        addCommand(newCmd);

        LOGGER.fine(() -> String.format("Added LC_LOAD_DYLIB %s. New empty space is %d.", newName, emptySpace));
    }

    @Override
    public void write(TruffleFile copy) throws IOException, InterruptedException {
        buffer.position(0);
        mh.put(buffer);
        assert buffer.position() == MachOHeader.SIZE64;
        for (var cmd : loadCommands) {
            cmd.put(buffer);
        }
        assert buffer.position() == MachOHeader.SIZE64 + mh.sizeOfCmds;
        for (int i = 0; i < emptySpace; i++) {
            buffer.put((byte) 0);
        }

        try (var os = copy.newOutputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            os.write(buffer.array());
        }

        var pb = newProcessBuilder(context);
        pb.command(getCodesign(), "--force", "--sign", "-", copy.getAbsoluteFile().getPath());
        var proc = pb.start();
        if (proc.waitFor() != 0) {
            throw new IOException("Failed to run `codesign` command. Make sure you have it on your PATH.");
        }
    }

    private String getCodesign() {
        Env env = context.getEnv();
        var path = env.getEnvironment().getOrDefault("PATH", "").split(env.getPathSeparator());
        var i = 0;
        TruffleFile codesign;
        do {
            codesign = env.getPublicTruffleFile(path[i++]).resolve("codesign");
        } while (!codesign.isExecutable() && i < path.length);
        return codesign.toString();
    }

    @Override
    public void close() {
        // Nothing to do
    }
}
