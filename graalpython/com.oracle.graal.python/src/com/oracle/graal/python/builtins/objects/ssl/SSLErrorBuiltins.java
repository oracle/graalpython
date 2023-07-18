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
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins.IDX_STRERROR;
import static com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins.IDX_WRITTEN;
import static com.oracle.graal.python.builtins.objects.ssl.SSLErrorCode.ERROR_CERT_VERIFICATION;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionAttrNode;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.SSLError)
public final class SSLErrorBuiltins extends PythonBuiltins {

    static final int IDX_REASON = IDX_WRITTEN + 1;
    static final int IDX_LIB = IDX_WRITTEN + 2;
    static final int IDX_VERIFY_CODE = IDX_WRITTEN + 3;
    static final int IDX_VERIFY_MESSAGE = IDX_WRITTEN + 4;
    static final int SSL_ERR_NUM_ATTRS = IDX_VERIFY_MESSAGE + 1;

    public static final BaseExceptionAttrNode.StorageFactory SSL_ERROR_ATTR_FACTORY = (args, factory) -> new Object[SSL_ERR_NUM_ATTRS];
    public static final TruffleString T_SSL_IN_BRACKETS = tsLiteral("[SSL]");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLErrorBuiltinsFactory.getFactories();
    }

    public static void setSSLErrorAttributes(PException exception, SSLErrorCode errorCode, TruffleString message) {
        setSSLErrorAttributes((PBaseException) exception.getUnreifiedException(), errorCode, message);
    }

    public static void setSSLErrorAttributes(PBaseException self, SSLErrorCode errorCode, TruffleString message) {
        Object[] data = new Object[SSL_ERR_NUM_ATTRS];
        final Object[] attrs = self.getExceptionAttributes();
        if (attrs != null) {
            PythonUtils.arraycopy(attrs, 0, data, 0, attrs.length);
        }
        TruffleString mnemonic = errorCode.getMnemonic();
        data[IDX_REASON] = mnemonic != null ? mnemonic : message;
        data[IDX_LIB] = T_SSL_IN_BRACKETS;
        if (errorCode == ERROR_CERT_VERIFICATION) {
            // not trying to be 100% correct,
            // use code = 1 (X509_V_ERR_UNSPECIFIED) and msg from jdk exception instead
            // see openssl x509_txt.c#X509_verify_cert_error_string
            data[IDX_VERIFY_CODE] = 1;
            data[IDX_VERIFY_MESSAGE] = message;
        }
        self.setExceptionAttributes(data);
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class SSLErrorInitNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds);

        @Specialization
        Object init(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds,
                        @Cached OsErrorBuiltins.OSErrorInitNode initNode) {
            initNode.execute(frame, self, args, kwds);
            Object[] sslAttrs = SSL_ERROR_ATTR_FACTORY.create(args, factory());
            PythonUtils.arraycopy(self.getExceptionAttributes(), 0, sslAttrs, 0, self.getExceptionAttributes().length);
            self.setExceptionAttributes(sslAttrs);
            return PNone.NONE;
        }
    }

    @Builtin(name = "reason", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorReasonNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_REASON, SSL_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "library", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorLibNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_LIB, SSL_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "verify_code", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorVerifyCodeNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_VERIFY_CODE, SSL_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "verify_message", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorVerifyMsgNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_VERIFY_MESSAGE, SSL_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strNode,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode) {
            Object strerror = self.getExceptionAttribute(IDX_STRERROR);
            if (PGuards.isString(strerror)) {
                return strerror;
            }
            return strNode.execute(frame, getArgsNode.execute(inliningTarget, self));
        }
    }

}
