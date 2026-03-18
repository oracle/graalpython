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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
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
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "pyexpat")
public final class PyExpatModuleBuiltins extends PythonBuiltins {
    private static final TruffleString T_CODES = tsLiteral("codes");
    private static final TruffleString T_MESSAGES = tsLiteral("messages");
    private static final TruffleString T_PARSER_CREATE = tsLiteral("ParserCreate");
    private static final TruffleString T_NAMESPACE_SEPARATOR = tsLiteral("'namespace_separator'");
    private static final TruffleString T_STR_OR_NONE = tsLiteral("str or None");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyExpatModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        PythonLanguage language = core.getLanguage();
        addBuiltinConstant(T___DOC__, """
                        Interface to pyexpat parser (Java backend).

                        NOTE: This backend currently provides best-effort compatibility with native Expat.
                        Known incompatibilities include Expat-specific incremental buffering/chunking behavior,
                        exact error code/message mapping, and some DTD/external entity callback semantics.
                        """);
        addBuiltinConstant("EXPAT_VERSION", T_JAVA);
        addBuiltinConstant("version_info", PFactory.createTuple(language, new Object[]{2, 6, 0}));
        addBuiltinConstant("native_encoding", toTruffleStringUncached("UTF-8"));

        addBuiltinConstant("XML_PARAM_ENTITY_PARSING_NEVER", PXMLParser.XML_PARAM_ENTITY_PARSING_NEVER);
        addBuiltinConstant("XML_PARAM_ENTITY_PARSING_UNLESS_STANDALONE", PXMLParser.XML_PARAM_ENTITY_PARSING_UNLESS_STANDALONE);
        addBuiltinConstant("XML_PARAM_ENTITY_PARSING_ALWAYS", PXMLParser.XML_PARAM_ENTITY_PARSING_ALWAYS);

        PythonBuiltinClass expatErrorType = core.lookupType(PythonBuiltinClassType.PyExpatError);
        addBuiltinConstant("error", expatErrorType);
        addBuiltinConstant("ExpatError", expatErrorType);

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

        super.initialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        addBuiltinConstant("features", PFactory.createList(core.getLanguage()));
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

        private static final TruffleString T_PARSING_FINISHED = tsLiteral("parsing finished");
        private static final TruffleString T_UNCLOSED_TOKEN = tsLiteral("unclosed token");
        private static final TruffleString T_SYNTAX_ERROR = tsLiteral("syntax error");

        @Specialization
        static TruffleString doIt(int code) {
            return switch (code) {
                case PXMLParser.XML_ERROR_FINISHED -> T_PARSING_FINISHED;
                case PXMLParser.XML_ERROR_UNCLOSED_TOKEN -> T_UNCLOSED_TOKEN;
                default -> T_SYNTAX_ERROR;
            };
        }
    }

    @Builtin(name = "ParserCreate", minNumOfPositionalArgs = 0, parameterNames = {"encoding", "namespace_separator", "intern"})
    @GenerateNodeFactory
    abstract static class ParserCreateNode extends PythonBuiltinNode {
        @Specialization
        static Object create(Object encoding, Object namespaceSeparator, Object intern,
                        @Bind Node inliningTarget,
                        @Cached XMLParserBuiltins.CreateParserNode createParserNode,
                        @Cached PRaiseNode raiseNode) {
            Object sep = namespaceSeparator == PNone.NO_VALUE ? PNone.NONE : namespaceSeparator;
            if (sep != PNone.NONE && !(sep instanceof TruffleString)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_BRACKETS_ARG_S_MUST_BE_S_NOT_P, T_PARSER_CREATE, T_NAMESPACE_SEPARATOR, T_STR_OR_NONE, sep);
            }
            Object internDict;
            if (intern == PNone.NO_VALUE) {
                internDict = PFactory.createDict(PythonLanguage.get(inliningTarget));
            } else if (intern == PNone.NONE) {
                internDict = PNone.NONE;
            } else if (intern instanceof PDict) {
                internDict = intern;
            } else {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.INTERN_MUST_BE_A_DICTIONARY);
            }
            return createParserNode.execute(inliningTarget, encoding, sep, internDict);
        }
    }
}
