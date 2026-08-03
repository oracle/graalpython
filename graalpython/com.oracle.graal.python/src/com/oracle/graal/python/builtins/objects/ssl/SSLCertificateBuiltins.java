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
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.FAILED_TO_ENCODE_CERTIFICATE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNSUPPORTED_CERTIFICATE_FORMAT;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLCertificate)
public final class SSLCertificateBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = SSLCertificateBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLCertificateBuiltinsFactory.getFactories();
    }

    @Builtin(name = "public_bytes", minNumOfPositionalArgs = 1, parameterNames = {"$self", "format"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class PublicBytesNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object publicBytes(PSSLCertificate self, int format,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            byte[] encoded;
            try {
                encoded = self.getCertificate().getEncoded();
            } catch (CertificateEncodingException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, FAILED_TO_ENCODE_CERTIFICATE, e.getMessage());
            }
            if (format == 2) {
                return PFactory.createBytes(language, encoded);
            } else if (format == 1) {
                String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
                return toTruffleStringUncached(
                                "-----BEGIN CERTIFICATE-----\n" + base64 + "\n-----END CERTIFICATE-----\n");
            }
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, UNSUPPORTED_CERTIFICATE_FORMAT);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLCertificateBuiltinsClinicProviders.PublicBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "get_info", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetInfoNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getInfo(PSSLCertificate self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PConstructAndRaiseNode.Lazy raiseNode) {
            try {
                return CertUtils.decodeCertificate(inliningTarget, raiseNode, self.getCertificate(), language);
            } catch (java.security.cert.CertificateParsingException e) {
                return PFactory.createDict(language);
            }
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object repr(PSSLCertificate self) {
            String subject = self.getCertificate().getSubjectX500Principal().getName(X500Principal.RFC2253);
            return toTruffleStringUncached("<_ssl.Certificate '" + subject + "'>");
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        @TruffleBoundary
        static long hash(PSSLCertificate self) {
            long hash = self.getHash();
            if (hash == -1) {
                try {
                    byte[] subject = self.getCertificate().getSubjectX500Principal().getEncoded();
                    byte[] digest = MessageDigest.getInstance("SHA-1").digest(subject);
                    int value = (digest[0] & 0xff) | (digest[1] & 0xff) << 8 |
                                    (digest[2] & 0xff) << 16 | (digest[3] & 0xff) << 24;
                    hash = Integer.toUnsignedLong(value);
                    self.setHash(hash);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
            return hash;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends RichCmpBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object compare(PSSLCertificate self, PSSLCertificate other, RichCmpOp op) {
            if (!op.isEqOrNe()) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return self.getCertificate().equals(other.getCertificate()) == op.isEq();
        }

        @Fallback
        static PNotImplemented compare(@SuppressWarnings("unused") Object self,
                        @SuppressWarnings("unused") Object other,
                        @SuppressWarnings("unused") RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
