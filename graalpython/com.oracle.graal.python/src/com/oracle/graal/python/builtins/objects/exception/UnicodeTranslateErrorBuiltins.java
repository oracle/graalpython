/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_END;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_OBJECT;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_REASON;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_START;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.UNICODE_ERROR_ATTR_FACTORY;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsInt;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsString;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnicodeTranslateError)
public final class UnicodeTranslateErrorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnicodeTranslateErrorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class UnicodeTranslateErrorInitNode extends PythonBuiltinNode {
        public abstract Object execute(PBaseException self, Object[] args);

        @Specialization
        Object initNoArgs(PBaseException self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode toStringNode,
                        @Cached CastToJavaIntExactNode toJavaIntExactNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode) {
            baseInitNode.execute(self, args);
            // PyArg_ParseTuple(args, "UnnU"), TODO: add proper error messages
            self.setExceptionAttributes(new Object[]{
                            null, // placeholder for object so we do not redefine the access indexes
                                  // for the other attributes, although this exception does not have
                                  // an encoding set
                            getArgAsString(inliningTarget, args, 0, this, toStringNode),
                            getArgAsInt(inliningTarget, args, 1, this, toJavaIntExactNode),
                            getArgAsInt(inliningTarget, args, 2, this, toJavaIntExactNode),
                            getArgAsString(inliningTarget, args, 3, this, toStringNode)
            });
            return PNone.NONE;
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class UnicodeTranslateErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString str(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached CastToTruffleStringNode toStringNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached PyObjectStrAsTruffleStringNode strNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            if (self.getExceptionAttributes() == null) {
                // Not properly initialized.
                return T_EMPTY_STRING;
            }

            // Get reason and encoding as strings, which they might not be if they've been
            // modified after we were constructed.
            final TruffleString object = toStringNode.execute(inliningTarget, attrNode.get(self, IDX_OBJECT, UNICODE_ERROR_ATTR_FACTORY));
            final int start = attrNode.getInt(self, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
            final int end = attrNode.getInt(self, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
            final TruffleString reason = strNode.execute(frame, inliningTarget, attrNode.get(self, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY));
            if (start < codePointLengthNode.execute(object, TS_ENCODING) && end == start + 1) {
                final int badChar = codePointAtIndexNode.execute(object, start, TS_ENCODING);
                String badCharStr;
                if (badChar <= 0xFF) {
                    badCharStr = PythonUtils.formatJString("\\x%02x", badChar);
                } else if (badChar <= 0xFFFF) {
                    badCharStr = PythonUtils.formatJString("\\u%04x", badChar);
                } else {
                    badCharStr = PythonUtils.formatJString("\\U%08x", badChar);
                }
                return simpleTruffleStringFormatNode.format("can't translate character '%s' in position %d: %s", badCharStr, start, reason);
            } else {
                return simpleTruffleStringFormatNode.format("can't translate characters in position %d-%d: %s", start, end - 1, reason);
            }
        }
    }
}
