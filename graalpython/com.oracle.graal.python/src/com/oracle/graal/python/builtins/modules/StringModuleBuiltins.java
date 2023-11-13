/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.J_FORMATTER_FIELD_NAME_SPLIT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FORMATTER_PARSER;
import static com.oracle.graal.python.nodes.BuiltinNames.J__STRING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.str.TemplateFormatter;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__STRING)
public final class StringModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StringModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_FORMATTER_PARSER, minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @ArgumentClinic(name = "self", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormaterParserNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringModuleBuiltinsClinicProviders.FormaterParserNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PSequenceIterator formatterParser(VirtualFrame frame, TruffleString self,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PythonObjectFactory factory) {
            TemplateFormatter formatter = new TemplateFormatter(self);
            List<Object[]> parserList;
            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            Object state = ExecutionContext.IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                parserList = formatter.formatterParser(this);
            } finally {
                ExecutionContext.IndirectCallContext.exit(frame, language, context, state);
            }
            return parserListToIterator(parserList, factory);
        }
    }

    private static PSequenceIterator parserListToIterator(List<Object[]> parserList, PythonObjectFactory factory) {
        Object[] tuples = new Object[parserList.size()];
        for (int i = 0; i < tuples.length; i++) {
            tuples[i] = factory.createTuple(parserList.get(i));
        }
        return factory.createSequenceIterator(factory.createList(tuples));
    }

    @Builtin(name = J_FORMATTER_FIELD_NAME_SPLIT, minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @ArgumentClinic(name = "self", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatterFieldNameSplitNode extends PythonUnaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringModuleBuiltinsClinicProviders.FormatterFieldNameSplitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object formatterParser(VirtualFrame frame, TruffleString self,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PythonObjectFactory factory) {
            TemplateFormatter formatter = new TemplateFormatter(self);
            TemplateFormatter.FieldNameSplitResult result;
            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            Object state = ExecutionContext.IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                result = formatter.formatterFieldNameSplit(this);
            } finally {
                ExecutionContext.IndirectCallContext.exit(frame, language, context, state);
            }
            return factory.createTuple(new Object[]{result.first, parserListToIterator(result.parserList, factory)});
        }
    }
}
