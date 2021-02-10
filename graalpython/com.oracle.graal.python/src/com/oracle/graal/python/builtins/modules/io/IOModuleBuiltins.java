/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PFileIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIOBase;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_io")
public class IOModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOModuleBuiltinsFactory.getFactories();
    }

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("DEFAULT_BUFFER_SIZE", DEFAULT_BUFFER_SIZE);
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        /*
         * This is temporary fix and will be removed once _io patches are removed.
         */
        PythonModule ioModule = core.lookupBuiltinModule("_io");
        PythonAbstractClass unspportedOp = (PythonAbstractClass) ioModule.getAttribute("UnsupportedOperation");
        core.lookupType(IOUnsupportedOperation).setSuperClass(unspportedOp);
    }

    @Builtin(name = "_IOBase", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PIOBase)
    @GenerateNodeFactory
    public abstract static class IOBaseNode extends PythonBuiltinNode {
        @Specialization
        public PythonObject create(Object cls) {
            return factory().createPythonObject(cls);
        }
    }

    @Builtin(name = "FileIO", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PFileIO)
    @GenerateNodeFactory
    public abstract static class FileIONode extends PythonBuiltinNode {
        @Specialization
        public PFileIO doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see FileIOBuiltins.InitNode
            return factory().createFileIO(cls);
        }
    }

    @Builtin(name = "BufferedReader", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PBufferedReader)
    @GenerateNodeFactory
    public abstract static class BufferedReaderNode extends PythonBuiltinNode {
        @Specialization
        public PBuffered doNew(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BufferedReaderBuiltins.InitNode
            return factory().createBufferedReader(cls);
        }
    }
}
