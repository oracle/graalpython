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
package com.oracle.graal.python.builtins.objects.exception;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnicodeError)
public final class UnicodeErrorBuiltins extends PythonBuiltins {
    static final int IDX_ENCODING = 0;
    static final int IDX_OBJECT = 1;
    static final int IDX_START = 2;
    static final int IDX_END = 3;
    static final int IDX_REASON = 4;
    public static final int UNICODE_ERR_NUM_ATTRS = IDX_REASON + 1;

    public static final BaseExceptionAttrNode.StorageFactory UNICODE_ERROR_ATTR_FACTORY = (args, factory) -> {
        return new Object[UNICODE_ERR_NUM_ATTRS];
    };

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return null;
    }

    public static String getArgAsString(Object[] args, int index, PNodeWithRaise raiseNode, CastToJavaStringNode castNode) {
        if (args.length < index + 1 || !PGuards.isString(args[index])) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError);
        } else {
            return castNode.execute(args[index]);
        }
    }

    public static int getArgAsInt(Object[] args, int index, PNodeWithRaise raiseNode, CastToJavaIntExactNode castNode) {
        if (args.length < index + 1 || !(PGuards.isInteger(args[index]) || PGuards.isPInt(args[index]))) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError);
        } else {
            return castNode.execute(args[index]);
        }
    }

    public static Object getArgAsBytes(VirtualFrame frame, Object[] args, int index, PythonObjectFactory factory, PNodeWithRaise raiseNode, BytesNodes.BytesInitNode bytesInitNode) {
        if (args.length < index + 1) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError);
        } else {
            if (!PGuards.isBytes(args[index])) {
                return factory.createBytes(bytesInitNode.execute(frame, args[index]));
            }
            return args[index];
        }
    }

    @Builtin(name = "encoding", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception encoding")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorEncodingNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_ENCODING, UNICODE_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "object", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception object")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_OBJECT, UNICODE_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, parameterNames = {"$self", "value"}, doc = "exception start")
    @GenerateNodeFactory
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index)
    public abstract static class UnicodeErrorStartNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return null;
        }

        @Specialization
        Object generic(PBaseException self, int value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "end", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, parameterNames = {"$self", "value"}, doc = "exception end")
    @GenerateNodeFactory
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index)
    public abstract static class UnicodeErrorEndNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return null;
        }

        @Specialization
        Object generic(PBaseException self, int value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "reason", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception reason")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorReasonNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY);
        }
    }
}
