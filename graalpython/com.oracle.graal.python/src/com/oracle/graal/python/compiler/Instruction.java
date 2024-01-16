/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import java.util.List;

import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

final class Instruction {

    final OpCodes opcode;
    int arg;
    final byte[] followingArgs;
    final Block target;
    final SourceRange location;

    /**
     * Bytecode index of the start of the instruction in the instruction stream including possible
     * extended args
     */
    public int bci = -1;
    public byte quickenOutput;
    public List<Instruction> quickeningGeneralizeList;

    Instruction(OpCodes opcode, int arg, byte[] followingArgs, Block target, SourceRange location) {
        this.opcode = opcode;
        this.arg = arg;
        this.followingArgs = followingArgs;
        this.target = target;
        this.location = location;
        assert opcode.argLength < 2 || followingArgs.length == opcode.argLength - 1;
    }

    @Override
    public String toString() {
        if (target != null) {
            return String.format("%s %s", opcode, target);
        }
        if (opcode.hasArg()) {
            return String.format("%s %s", opcode, arg);
        }
        return opcode.toString();
    }

    public int bodyBci() {
        assert bci != -1;
        // 2 bytes for EXTENDED_ARG opcode and the arg itself
        return bci + 2 * extensions();
    }

    public int extensions() {
        if (arg <= 0xFF) {
            return 0;
        } else if (arg <= 0xFFFF) {
            return 1;
        } else if (arg <= 0xFFFFFF) {
            return 2;
        } else {
            return 3;
        }
    }

    public int extendedLength() {
        return opcode.length() + extensions() * 2;
    }
}
