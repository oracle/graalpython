/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pyexpat;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "pyexpat")
public final class PyExpatModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T_CODES = tsLiteral("codes");
    private static final TruffleString T_MESSAGES = tsLiteral("messages");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyExpatModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(SpecialAttributeNames.T___DOC__, """
                        Interface to pyexpat parser (Java backend).

                        NOTE: This backend currently provides best-effort compatibility with native Expat.
                        Known incompatibilities include Expat-specific incremental buffering/chunking behavior,
                        exact error code/message mapping, and some DTD/external entity callback semantics.
                        """);
        addBuiltinConstant("EXPAT_VERSION", toTruffleStringUncached("expat_2.6.0"));
        addBuiltinConstant("version_info", PFactory.createTuple(core.getLanguage(), new Object[]{2, 6, 0}));
        addBuiltinConstant("native_encoding", toTruffleStringUncached("UTF-8"));

        addBuiltinConstant("XML_PARAM_ENTITY_PARSING_NEVER", PXMLParser.XML_PARAM_ENTITY_PARSING_NEVER);
        addBuiltinConstant("XML_PARAM_ENTITY_PARSING_UNLESS_STANDALONE", PXMLParser.XML_PARAM_ENTITY_PARSING_UNLESS_STANDALONE);
        addBuiltinConstant("XML_PARAM_ENTITY_PARSING_ALWAYS", PXMLParser.XML_PARAM_ENTITY_PARSING_ALWAYS);

        addBuiltinConstant("error", core.lookupType(PythonBuiltinClassType.PyExpatError));
        addBuiltinConstant("ExpatError", core.lookupType(PythonBuiltinClassType.PyExpatError));

        PythonLanguage language = core.getLanguage();
        PythonModule errors = PFactory.createPythonModule(toTruffleStringUncached("pyexpat.errors"));
        PDict codes = PFactory.createDict(language);
        PDict messages = PFactory.createDict(language);
        addError(errors, codes, messages, "XML_ERROR_FINISHED", "parsing finished", PXMLParser.XML_ERROR_FINISHED);
        addError(errors, codes, messages, "XML_ERROR_SYNTAX", "syntax error", PXMLParser.XML_ERROR_SYNTAX);
        addError(errors, codes, messages, "XML_ERROR_UNCLOSED_TOKEN", "unclosed token", PXMLParser.XML_ERROR_UNCLOSED_TOKEN);
        errors.setAttribute(T_CODES, codes);
        errors.setAttribute(T_MESSAGES, messages);
        addBuiltinConstant("errors", errors);

        PythonModule model = PFactory.createPythonModule(toTruffleStringUncached("pyexpat.model"));
        addBuiltinConstant("model", model);

        addBuiltinConstant("features", PFactory.createList(language));
        super.initialize(core);
    }

    private static void addError(PythonModule errors, PDict codes, PDict messages, String constName, String msg, int code) {
        TruffleString msgTs = toTruffleStringUncached(msg);
        errors.setAttribute(toTruffleStringUncached(constName), msgTs);
        codes.setItem(msgTs, code);
        messages.setItem(code, msgTs);
    }

    @Builtin(name = "ErrorString", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ErrorStringNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString doIt(int code) {
            return switch (code) {
                case PXMLParser.XML_ERROR_FINISHED -> toTruffleStringUncached("parsing finished");
                case PXMLParser.XML_ERROR_UNCLOSED_TOKEN -> toTruffleStringUncached("unclosed token");
                default -> toTruffleStringUncached("syntax error");
            };
        }
    }

    @Builtin(name = "ParserCreate", minNumOfPositionalArgs = 0, parameterNames = {"encoding", "namespace_separator", "intern"})
    @GenerateNodeFactory
    abstract static class ParserCreateNode extends PythonBuiltinNode {
        @Specialization
        Object create(Object encoding, Object namespaceSeparator, @SuppressWarnings("unused") Object intern,
                        @Bind Node inliningTarget) {
            Object sep = namespaceSeparator == PNone.NO_VALUE ? PNone.NONE : namespaceSeparator;
            return XMLParserBuiltins.createParser(inliningTarget, this, sep);
        }
    }
}
