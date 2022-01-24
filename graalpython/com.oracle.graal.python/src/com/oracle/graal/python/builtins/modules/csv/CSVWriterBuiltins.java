/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.csv;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVWriter)
public final class CSVWriterBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVWriterBuiltinsFactory.getFactories();
    }

    @Builtin(name = "writerow", parameterNames = {"$self", "seq"}, minNumOfPositionalArgs = 2, doc = WRITEROW_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetClassNode getClass,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached CallUnaryMethodNode callNode) {
            Object iter;

            try {
                iter = getIter.execute(frame, seq);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.TypeError, errorProfile);
                throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.EXPECTED_ITERABLE_NOT_S, getClass.execute(seq));
            }

            // Join all fields of passed in sequence in internal buffer.
            PythonLanguage language = getLanguage();
            Object state = IndirectCallContext.enter(frame, language, getContext(), this);
            try {
                self.joinFields(this, iter);
            } finally {
                IndirectCallContext.exit(frame, language, getContext(), state);
            }

            return callNode.executeObject(frame, self.write, PythonUtils.sbToString(self.rec));
        }
    }

    @Builtin(name = "writerows", parameterNames = {"$self", "seqseq"}, minNumOfPositionalArgs = 2, doc = WRITEROWS_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowsNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNext,
                        @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Cached WriteRowNode writeRow) {
            Object iter, row;

            iter = getIter.execute(frame, seq);

            while (true) {
                try {
                    row = getNext.execute(frame, iter);
                    writeRow.execute(frame, self, row);
                } catch (PException e) {
                    e.expectStopIteration(isBuiltinClassProfile);
                    break;
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "dialect", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetDialectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static CSVDialect doIt(CSVWriter self) {
            return self.dialect;
        }
    }

    private static final String WRITEROW_DOC = "writerow(iterable)\n" +
                    "\n" +
                    "Construct and write a CSV record from an iterable of fields.  Non-string\n" +
                    "elements will be converted to string.";

    private static final String WRITEROWS_DOC = "writerows(iterable of iterables)\n" +
                    "\n" +
                    "Construct and write a series of iterables to a csv file.  Non-string\n" +
                    "elements will be converted to string.";

}
