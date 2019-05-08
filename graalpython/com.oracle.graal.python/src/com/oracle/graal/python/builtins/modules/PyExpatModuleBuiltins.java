/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "pyexpat")
public class PyExpatModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyExpatModuleBuiltinsFactory.getFactories();
    }

    private static enum ContentModelConstant {
        XML_CQUANT_NONE(0),
        XML_CQUANT_OPT(1),
        XML_CQUANT_PLUS(3),
        XML_CQUANT_REP(2),
        XML_CTYPE_ANY(2),
        XML_CTYPE_CHOICE(5),
        XML_CTYPE_EMPTY(1),
        XML_CTYPE_MIXED(3),
        XML_CTYPE_NAME(4),
        XML_CTYPE_SEQ(6);

        private final int number;

        private ContentModelConstant(int number) {
            this.number = number;
        }
    }

    private static enum ErrorConstant {
        XML_ERROR_NO_MEMORY("out of memory"),
        XML_ERROR_SYNTAX("syntax error"),
        XML_ERROR_NO_ELEMENTS("no element found"),
        XML_ERROR_INVALID_TOKEN("not well-formed (invalid token)"),
        XML_ERROR_UNCLOSED_TOKEN("unclosed token"),
        XML_ERROR_PARTIAL_CHAR("partial character"),
        XML_ERROR_TAG_MISMATCH("mismatched tag"),
        XML_ERROR_DUPLICATE_ATTRIBUTE("duplicate attribute"),
        XML_ERROR_JUNK_AFTER_DOC_ELEMENT("junk after document element"),
        XML_ERROR_PARAM_ENTITY_REF("illegal parameter entity reference"),
        XML_ERROR_UNDEFINED_ENTITY("undefined entity"),
        XML_ERROR_RECURSIVE_ENTITY_REF("recursive entity reference"),
        XML_ERROR_ASYNC_ENTITY("asynchronous entity"),
        XML_ERROR_BAD_CHAR_REF("reference to invalid character number"),
        XML_ERROR_BINARY_ENTITY_REF("reference to binary entity"),
        XML_ERROR_ATTRIBUTE_EXTERNAL_ENTITY_REF("reference to external entity in attribute"),
        XML_ERROR_MISPLACED_XML_PI("XML or text declaration not at start of entity"),
        XML_ERROR_UNKNOWN_ENCODING("unknown encoding"),
        XML_ERROR_INCORRECT_ENCODING("encoding specified in XML declaration is incorrect"),
        XML_ERROR_UNCLOSED_CDATA_SECTION("unclosed CDATA section"),
        XML_ERROR_EXTERNAL_ENTITY_HANDLING("error in processing external entity reference"),
        XML_ERROR_NOT_STANDALONE("document is not standalone"),
        XML_ERROR_UNEXPECTED_STATE("unexpected parser state - please send a bug report"),
        XML_ERROR_ENTITY_DECLARED_IN_PE("entity declared in parameter entity"),
        XML_ERROR_FEATURE_REQUIRES_XML_DTD("requested feature requires XML_DTD support in Expat"),
        XML_ERROR_CANT_CHANGE_FEATURE_ONCE_PARSING("cannot change setting once parsing has begun"),
        XML_ERROR_UNBOUND_PREFIX("unbound prefix"),
        XML_ERROR_UNDECLARING_PREFIX("must not undeclare prefix"),
        XML_ERROR_INCOMPLETE_PE("incomplete markup in parameter entity"),
        XML_ERROR_XML_DECL("XML declaration not well-formed"),
        XML_ERROR_TEXT_DECL("text declaration not well-formed"),
        XML_ERROR_PUBLICID("illegal character(s) in public id"),
        XML_ERROR_SUSPENDED("parser suspended"),
        XML_ERROR_NOT_SUSPENDED("parser not suspended"),
        XML_ERROR_ABORTED("parsing aborted"),
        XML_ERROR_FINISHED("parsing finished"),
        XML_ERROR_SUSPEND_PE("cannot suspend in external parameter entity");

        private final String message;

        private ErrorConstant(String message) {
            this.message = message;
        }
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        PythonModule model = core.factory().createPythonModule("pyexpat.model");
        for (ContentModelConstant v : ContentModelConstant.values()) {
            model.setAttribute(v.name(), v.number);
        }
        builtinConstants.put("model", model);

        PythonModule errors = core.factory().createPythonModule("pyexpat.errors");
        Map<String, Integer> codes = new HashMap<>();
        Map<Integer, String> messages = new HashMap<>();
        for (ErrorConstant c : ErrorConstant.values()) {
            errors.setAttribute(c.name(), c.message);
            codes.put(c.message, c.ordinal() + 1);
            messages.put(c.ordinal() + 1, c.message);
        }
        errors.setAttribute("messages", core.factory().createDict(messages));
        errors.setAttribute("codes", core.factory().createDict(codes));
        builtinConstants.put("errors", errors);
    }

    @Builtin(name = "ParserCreate", parameterNames = {"encoding", "namespace_separator", "intern"}, doc = "Return a new XML parser object.")
    @GenerateNodeFactory
    abstract static class ParserCreateNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object fail(Object encoding, Object namespace_separator, Object intern) {
            throw raise(PythonBuiltinClassType.NotImplementedError, "XML pyexpat parser is not implemented");
        }
    }
}
