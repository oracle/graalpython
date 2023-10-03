/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRWPair;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedRandom;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedReader;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBufferedWriter;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBytesIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PFileIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIOBase;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PStringIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PTextIOWrapper;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = {PIOBase, PFileIO, PStringIO, PTextIOWrapper, PBytesIO,
                PBufferedReader, PBufferedWriter, PBufferedRandom, PBufferedRWPair})
public final class IOBaseDictBuiltins extends AbstractBufferedIOBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOBaseDictBuiltinsFactory.getFactories();
    }

    /*
     * builtin __dict__ is readonly
     */
    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object doit(PythonObject self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization
        static Object setDict(PythonObject self, @SuppressWarnings("unused") Object d,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.AssertionError, ErrorMessages.ATTR_DICT_IS_NOT_WRITABLE, self);
        }
    }
}
