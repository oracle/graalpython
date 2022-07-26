/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextContextVarBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextContextVarBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyContextVar_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyContextVarNewNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object createNone(TruffleString name, @SuppressWarnings("unused") PNone def,
                          @Cached GilNode gil) {
            return create(name, PContextVar.NO_DEFAULT, gil);
        }

        @Specialization(guards = "!isNone(def)")
        Object create(TruffleString name, Object def,
                      @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return factory().createContextVar(name, def);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @Builtin(name = "PyContextVar_Get", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyContextVarGetNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object get(PContextVar self, @SuppressWarnings("unused") PNone def,
                   @Cached GilNode gil) {
            return get(self, PContextVar.NO_DEFAULT, gil);
        }

        @Specialization(guards = "!isPNone(def)")
        Object get(PContextVar self, Object def,
                    @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                return self.get(def);
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @Builtin(name = "PyContextVar_Set", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyContextVarSetNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object set(PContextVar self, Object value,
                   @Cached GilNode gil) {
            boolean mustRelease = gil.acquire();
            try {
                self.setValue(value);
                return PNone.NONE;
            } finally {
                gil.release(mustRelease);
            }
        }
    }
}
