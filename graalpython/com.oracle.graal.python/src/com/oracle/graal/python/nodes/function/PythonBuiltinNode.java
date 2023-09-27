/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.function;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@NodeChild(value = "arguments", type = ReadArgumentNode[].class)
public abstract class PythonBuiltinNode extends PythonBuiltinBaseNode {
    public abstract Object execute(VirtualFrame frame);

    @Child private PRaiseNode raiseNode;

    protected final PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isAdoptable()) {
                raiseNode = insert(PRaiseNode.create());
            } else {
                raiseNode = PRaiseNode.getUncached();
            }
        }
        return raiseNode;
    }

    public PException raise(PythonBuiltinClassType type, TruffleString string) {
        return getRaiseNode().raise(type, string);
    }

    public PException raise(PythonBuiltinClassType exceptionType) {
        return getRaiseNode().raise(exceptionType);
    }

    public final PException raise(PythonBuiltinClassType type, TruffleString format, Object... arguments) {
        return getRaiseNode().raise(type, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        return getRaiseNode().raise(type, e);
    }
}
