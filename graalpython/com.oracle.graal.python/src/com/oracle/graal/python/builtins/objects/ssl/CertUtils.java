/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_AUTHORITY_INFO_ACCESS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_CA_ISSUERS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_CRL_DISTRIBUTION_POINTS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_OCSP;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_CA_ISSUERS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_CRL_DISTRIBUTION_POINTS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_ISSUER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_NOT_AFTER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_NOT_BEFORE;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_OCSP;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_SERIAL_NUMBER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_SUBJECT;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_SUBJECT_ALT_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.T_JAVA_X509_VERSION;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.util.encoders.DecoderException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.strings.TruffleString;

public final class CertUtils {
    private static final TruffleString T_UNSUPPORTED = tsLiteral("<unsupported>");
    private static final TruffleString T_OTHERNAME = tsLiteral("othername");
    private static final TruffleString T_EMAIL = tsLiteral("email");
    private static final TruffleString T_DNS = tsLiteral("DNS");
    private static final TruffleString T_X_400_NAME = tsLiteral("X400Name");
    private static final TruffleString T_DIR_NAME = tsLiteral("DirName");
    private static final TruffleString T_EDI_PARTY_NAME = tsLiteral("EdiPartyName");
    private static final TruffleString T_URI = tsLiteral("URI");
    private static final TruffleString T_IP_ADDRESS = tsLiteral("IP Address");
    private static final TruffleString T_REGISTERED_ID = tsLiteral("Registered ID");

    public static class NoCertificateFoundException extends Exception {
        private static final long serialVersionUID = 5489472143646552420L;

        public NoCertificateFoundException() {
            super("No certificate found");
        }
    }

    public static class NeedsPasswordException extends Exception {
        private static final long serialVersionUID = -5153912585672596522L;

        public NeedsPasswordException() {
            super("Needs password to decrypt private key");
        }
    }

    /**
     * openssl v3_purp.c#check_ca
     */
    @TruffleBoundary
    static boolean isCA(X509Certificate cert, boolean[] keyUsage) {
        return keyUsage != null && keyUsage.length > 5 && keyUsage[5] ||
                        cert.getBasicConstraints() != -1 ||
                        cert.getVersion() == 1 && isSelfSigned(cert);
    }

    @TruffleBoundary
    public static boolean[] getKeyUsage(X509Certificate cert) {
        return cert.getKeyUsage();
    }

    @TruffleBoundary
    public static byte[] getEncoded(X509Certificate cert) throws CertificateEncodingException {
        return cert.getEncoded();
    }

    @TruffleBoundary
    static boolean isSelfSigned(X509Certificate cert) {
        try {
            cert.verify(cert.getPublicKey());
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException | CertificateException e) {
            return false;
        }
        return true;
    }

    @TruffleBoundary
    static boolean isCrl(boolean[] keyUsage) {
        return keyUsage != null && keyUsage.length > 6 && keyUsage[6];
    }

    /**
     * _ssl.c#_decode_certificate
     */
    @TruffleBoundary
    public static PDict decodeCertificate(X509Certificate cert, PythonLanguage language) throws CertificateParsingException {
        PDict dict = PFactory.createDict(language);
        HashingStorage storage = dict.getDictStorage();
        try {
            storage = setItem(storage, T_JAVA_X509_OCSP, parseOCSP(cert, language));
            storage = setItem(storage, T_JAVA_X509_CA_ISSUERS, parseCAIssuers(cert, language));
            storage = setItem(storage, T_JAVA_X509_ISSUER, createTupleForX509Name(cert.getIssuerX500Principal().getName("RFC1779"), language));
            storage = setItem(storage, T_JAVA_X509_NOT_AFTER, getNotAfter(cert));
            storage = setItem(storage, T_JAVA_X509_NOT_BEFORE, getNotBefore(cert));
            storage = setItem(storage, T_JAVA_X509_SERIAL_NUMBER, getSerialNumber(cert));
            storage = setItem(storage, T_JAVA_X509_CRL_DISTRIBUTION_POINTS, parseCRLPoints(cert, language));
            storage = setItem(storage, T_JAVA_X509_SUBJECT, createTupleForX509Name(cert.getSubjectX500Principal().getName("RFC1779"), language));
            storage = setItem(storage, T_JAVA_X509_SUBJECT_ALT_NAME, parseSubjectAltName(cert, language));
            storage = setItem(storage, T_JAVA_X509_VERSION, getVersion(cert));
        } catch (RuntimeException re) {
            throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL, re);
        }
        dict.setDictStorage(storage);
        return dict;
    }

    private static HashingStorage setItem(HashingStorage storage, TruffleString key, Object value) {
        if (value != null) {
            return HashingStorageSetItem.executeUncached(storage, key, value);
        } else {
            return storage;
        }
    }

    @TruffleBoundary
    private static TruffleString getSerialNumber(X509Certificate x509Certificate) {
        String sn = x509Certificate.getSerialNumber().toString(16).toUpperCase();
        // i2a_ASN1_INTEGER pads the number to have even number of digits
        return toTruffleStringUncached(sn.length() % 2 == 0 ? sn : '0' + sn);
    }

    @TruffleBoundary
    private static int getVersion(X509Certificate x509Certificate) {
        return x509Certificate.getVersion();
    }

    @TruffleBoundary
    private static TruffleString getNotAfter(X509Certificate x509Certificate) {
        return formatDate(x509Certificate.getNotAfter());
    }

    @TruffleBoundary
    private static TruffleString getNotBefore(X509Certificate x509Certificate) {
        return formatDate(x509Certificate.getNotBefore());
    }

    private static final ZoneId zoneId = ZoneId.of("GMT");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("MMM ppd HH:mm:ss yyyy z");

    @TruffleBoundary
    private static TruffleString formatDate(Date d) {
        return toTruffleStringUncached(ZonedDateTime.ofInstant(d.toInstant(), zoneId).format(DF));
    }

    @TruffleBoundary
    private static PTuple createTupleForX509Name(String name, PythonLanguage language) {
        List<PTuple> result = new ArrayList<>();
        for (String component : name.split(",")) {
            String[] kv = component.split("=");
            if (kv.length == 2) {
                PTuple innerTuple = PFactory.createTuple(language, new Object[]{ASN1Helper.translateKeyToPython(kv[0].trim()), toTruffleStringUncached(kv[1].trim())});
                result.add(PFactory.createTuple(language, new Object[]{innerTuple}));
            }
        }
        // The String form is in the LDAP format, where the elements are in reverse order from what
        // was in the certificate
        Collections.reverse(result);
        return PFactory.createTuple(language, result.toArray(new Object[0]));
    }

    @TruffleBoundary
    private static PTuple parseSubjectAltName(X509Certificate certificate, PythonLanguage language) throws CertificateParsingException {
        List<Object> tuples = new ArrayList<>(16);
        Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
        if (altNames != null) {
            for (List<?> altName : altNames) {
                if (altName.size() == 2 && altName.get(0) instanceof Integer) {
                    int type = (Integer) altName.get(0);
                    Object value = altName.get(1);
                    TruffleString stringValue;
                    if (value instanceof String) {
                        stringValue = toTruffleStringUncached((String) value);
                    } else {
                        stringValue = T_UNSUPPORTED;
                    }
                    switch (type) {
                        // see openssl v3_alt.c#i2v_GENERAL_NAME()
                        case 0:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_OTHERNAME, stringValue}));
                            break;
                        case 1:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_EMAIL, stringValue}));
                            break;
                        case 2:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_DNS, stringValue}));
                            break;
                        case 3:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_X_400_NAME, stringValue}));
                            break;
                        case 4:
                            tuples.add(PFactory.createTuple(language,
                                            new Object[]{T_DIR_NAME, value instanceof String ? createTupleForX509Name((String) value, language) : PFactory.createEmptyTuple(language)}));
                            break;
                        case 5:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_EDI_PARTY_NAME, stringValue}));
                            break;
                        case 6:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_URI, stringValue}));
                            break;
                        case 7:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_IP_ADDRESS, stringValue}));
                            break;
                        case 8:
                            tuples.add(PFactory.createTuple(language, new Object[]{T_REGISTERED_ID, stringValue}));
                            break;
                        default:
                            continue;
                    }
                }
            }
            return PFactory.createTuple(language, tuples.toArray(new Object[tuples.size()]));
        }
        return null;
    }

    // private static

    private static final class DerValue {
        private static final byte OCTET_STRING = 0x04;
        private static final byte OBJECT_IDENTIFIER = 0x06;
        private static final byte SEQUENCE = 0x10;

        private static final String ERROR_MESSAGE = "Invalid DER encoded data";

        final byte[] data;
        final boolean isContextTag;
        final int contentLen;
        final int contentStart;
        int contentTag;

        DerValue(byte[] data) throws CertificateParsingException {
            this(data, 0, data.length);
        }

        DerValue(byte[] data, int offset, int end) throws CertificateParsingException {
            if (offset == data.length) {
                // this is a 0-length value at the end of the data
                this.data = data;
                this.contentTag = 0;
                this.isContextTag = false;
                this.contentStart = offset;
                this.contentLen = 0;
            } else if (offset < data.length) {
                this.data = data;

                this.contentTag = data[offset] & 0b11111;
                this.isContextTag = (data[offset] & 0b11000000) == 0b10000000;
                int[] lenAndOffset = readLength(data, offset);
                this.contentStart = lenAndOffset[0];
                this.contentLen = lenAndOffset[1];

                assert this.contentTag != 0b11111 : "extended tag range not supported";
                assert contentStart + contentLen <= end;
            } else {
                throw new CertificateParsingException(ERROR_MESSAGE);
            }
        }

        private static int[] readLength(byte[] data, int offset) throws CertificateParsingException {
            try {
                int lenBase = data[offset + 1] & 0xff;
                if (lenBase < 128) {
                    return new int[]{offset + 2, lenBase};
                } else {
                    int lengthOfLength = lenBase - 128;
                    if (lengthOfLength > 4) {
                        throw new IllegalArgumentException("longer than int-range DER values not supported");
                    }
                    int fullLength = 0;
                    for (int i = 0; i < lengthOfLength; i++) {
                        fullLength = (fullLength << 8) | (data[offset + 2 + i] & 0xff);
                    }
                    return new int[]{offset + 2 + lengthOfLength, fullLength};
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new CertificateParsingException(ERROR_MESSAGE);
            }
        }

        byte[] getRawData() {
            return Arrays.copyOfRange(data, contentStart, contentStart + contentLen);
        }

        DerValue getObjectIdentifier() throws CertificateParsingException {
            if (contentTag != OBJECT_IDENTIFIER) {
                return null;
            } else {
                return new DerValue(data, contentStart, contentStart + contentLen);
            }
        }

        DerValue getContextTag(int tag) throws CertificateParsingException {
            if (contentTag != tag || !isContextTag) {
                return null;
            } else {
                return new DerValue(data, contentStart, contentStart + contentLen);
            }
        }

        DerValue getOctetString() throws CertificateParsingException {
            if (contentTag != OCTET_STRING) {
                return null;
            } else {
                return new DerValue(data, contentStart, contentStart + contentLen);
            }
        }

        DerValue getSequence() throws CertificateParsingException {
            if (contentTag != SEQUENCE) {
                return null;
            } else {
                return new DerValue(data, contentStart, contentStart + contentLen);
            }
        }

        String getGeneralNameURI() {
            // GeneralName ::= CHOICE {
            // otherName [0] AnotherName,
            // rfc822Name [1] IA5String,
            // dNSName [2] IA5String,
            // x400Address [3] ORAddress,
            // directoryName [4] Name,
            // ediPartyName [5] EDIPartyName,
            // uniformResourceIdentifier [6] IA5String,
            // iPAddress [7] OCTET STRING,
            // registeredID [8] OBJECT IDENTIFIER }
            if (contentTag == 6) {
                // we're only interested in URIs, which are encoded as 7-bit ASCII
                return new String(getRawData());
            } else {
                return null;
            }
        }

        List<DerValue> getSequenceElements() throws CertificateParsingException {
            List<DerValue> result = new ArrayList<>();
            iterateSequence((e, r) -> {
                result.add(e);
            }, result);
            return result;
        }

        @FunctionalInterface
        private interface DerSequenceConsumer<A, B> {
            abstract void accept(A a, B b) throws CertificateParsingException;
        }

        <T> void iterateSequence(DerSequenceConsumer<DerValue, T> consumer, T value) throws CertificateParsingException {
            int sequenceStart = contentStart;
            int sequenceEnd = contentStart + contentLen;
            DerValue sequenceData = getSequence();
            if (sequenceData == null) {
                return;
            }
            int i = sequenceStart;
            while (i < sequenceEnd) {
                DerValue element = new DerValue(data, i, sequenceEnd);
                i = element.contentStart + element.contentLen;
                consumer.accept(element, value);
            }
        }
    }

    @TruffleBoundary
    private static PTuple parseCRLPoints(X509Certificate cert, PythonLanguage language) throws CertificateParsingException {
        List<TruffleString> result = new ArrayList<>();
        byte[] bytes = cert.getExtensionValue(OID_CRL_DISTRIBUTION_POINTS);
        if (bytes == null) {
            return null;
        }
        DerValue data = new DerValue(bytes).getOctetString();
        if (data == null) {
            return null;
        }
        // CRLDistributionPoints ::= SEQUENCE SIZE (1..MAX) OF DistributionPoint
        data.iterateSequence((element, r) -> {
            // DistributionPoint ::= SEQUENCE {
            // distributionPoint [0] DistributionPointName OPTIONAL,
            // reasons [1] ReasonFlags OPTIONAL,
            // cRLIssuer [2] GeneralNames OPTIONAL }
            DerValue dp = element.getSequence();
            if (dp != null) {
                DerValue dpn = dp.getContextTag(0);
                if (dpn != null) {
                    // DistributionPointName ::= CHOICE {
                    // fullName [0] GeneralNames,
                    // nameRelativeToCRLIssuer [1] RelativeDistinguishedName }
                    DerValue fullName = dp.getContextTag(0);
                    if (fullName != null) {
                        fullName.contentTag = DerValue.SEQUENCE; // implicitly a SEQUENCE
                        // GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
                        fullName.iterateSequence((name, r2) -> {
                            String nextUri = name.getGeneralNameURI();
                            if (nextUri != null) {
                                r2.add(toTruffleStringUncached(nextUri));
                            }
                        }, r);
                    }
                }
            }
        }, result);
        if (result.size() > 0) {
            return PFactory.createTuple(language, result.toArray(new Object[result.size()]));
        } else {
            return null;
        }
    }

    @TruffleBoundary
    private static PTuple parseCAIssuers(X509Certificate cert, PythonLanguage language) throws CertificateParsingException {
        List<TruffleString> result = new ArrayList<>();
        byte[] bytes = cert.getExtensionValue(OID_AUTHORITY_INFO_ACCESS);
        if (bytes == null) {
            return null;
        }
        DerValue data = new DerValue(bytes).getOctetString();
        if (data == null) {
            return null;
        }
        // AuthorityInfoAccessSyntax ::= SEQUENCE SIZE (1..MAX) OF AccessDescription
        data.iterateSequence((element, r) -> {
            // AccessDescription ::= SEQUENCE {
            // accessMethod OBJECT IDENTIFIER,
            // accessLocation GeneralName }
            List<DerValue> elements = element.getSequenceElements();
            if (elements.size() == 2) {
                DerValue accessMethod = elements.get(0).getObjectIdentifier();
                if (accessMethod != null) {
                    if (Arrays.equals(accessMethod.getRawData(), OID_CA_ISSUERS)) {
                        String uri = elements.get(1).getGeneralNameURI();
                        if (uri != null) {
                            r.add(toTruffleStringUncached(uri));
                        }
                    }
                }
            }
        }, result);
        if (result.size() > 0) {
            return PFactory.createTuple(language, result.toArray(new Object[result.size()]));
        } else {
            return null;
        }
    }

    @TruffleBoundary
    private static PTuple parseOCSP(X509Certificate cert, PythonLanguage language) throws CertificateParsingException {
        List<TruffleString> result = new ArrayList<>();
        byte[] bytes = cert.getExtensionValue(OID_AUTHORITY_INFO_ACCESS);
        if (bytes == null) {
            return null;
        }
        DerValue data = new DerValue(bytes).getOctetString();
        if (data == null) {
            return null;
        }
        // AuthorityInfoAccessSyntax ::= SEQUENCE SIZE (1..MAX) OF AccessDescription
        data.iterateSequence((element, r) -> {
            // AccessDescription ::= SEQUENCE {
            // accessMethod OBJECT IDENTIFIER,
            // accessLocation GeneralName }
            List<DerValue> elements = element.getSequenceElements();
            if (elements.size() == 2) {
                DerValue accessMethod = elements.get(0).getObjectIdentifier();
                if (accessMethod != null) {
                    if (Arrays.equals(accessMethod.getRawData(), OID_OCSP)) {
                        String uri = elements.get(1).getGeneralNameURI();
                        if (uri != null) {
                            r.add(toTruffleStringUncached(uri));
                        }
                    }
                }
            }
        }, result);
        if (result.size() > 0) {
            return PFactory.createTuple(language, result.toArray(new Object[result.size()]));
        } else {
            return null;
        }
    }

    @TruffleBoundary
    public static List<Object> loadVerifyLocations(TruffleFile file, TruffleFile path) throws IOException, CertificateException, CRLException, NoCertificateFoundException {
        Collection<TruffleFile> files = new ArrayList<>();
        if (file != null) {
            files.add(file);
        }
        if (path != null && path.isDirectory()) {
            // TODO: see SSL_CTX_load_verify_locations
            // if capath is a directory, cpython loads certificates on demand
            files.addAll(path.list());
        }
        List<Object> result = new ArrayList<>();
        for (TruffleFile f : files) {
            try (BufferedReader r = f.newBufferedReader()) {
                List<Object> certificates = getCertificates(r);
                if (certificates.isEmpty()) {
                    throw new NoCertificateFoundException();
                }
                result.addAll(certificates);
            }
        }
        return result;
    }

    @TruffleBoundary
    public static List<Object> getCertificates(BufferedReader r) throws IOException, CertificateException, CRLException {
        return getCertificates(r, false);
    }

    @TruffleBoundary
    public static List<Object> getCertificates(BufferedReader r, boolean onlyCertificates) throws IOException, CertificateException, CRLException {
        List<Object> l = new ArrayList<>();
        PEMParser pemParser = new PEMParser(r);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Object object;
        while ((object = pemParser.readObject()) != null) {
            if (object instanceof X509CertificateHolder) {
                // TODO use the X509CertificateHolder directly without conversion
                l.add(factory.generateCertificate(new ByteArrayInputStream(((X509CertificateHolder) object).getEncoded())));
            }
            if (!onlyCertificates && object instanceof X509CRLHolder) {
                // TODO use the X509CRLHolder directly without conversion
                l.add(factory.generateCRL(new ByteArrayInputStream(((X509CRLHolder) object).getEncoded())));
            }
        }
        return l;
    }

    @TruffleBoundary
    static PrivateKey getPrivateKey(PythonContext context, BufferedReader reader, char[] password, X509Certificate cert)
                    throws NeedsPasswordException {
        PEMParser pemParser = new PEMParser(reader);
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        Provider provider = LazyBouncyCastleProvider.initProvider();
        converter.setProvider(provider);
        PrivateKey privateKey = null;
        try {
            Object object;
            while ((object = pemParser.readObject()) != null) {
                PrivateKeyInfo pkInfo;
                if (object instanceof PEMKeyPair) {
                    pkInfo = ((PEMKeyPair) object).getPrivateKeyInfo();
                } else if (object instanceof PEMEncryptedKeyPair) {
                    if (password == null) {
                        throw new NeedsPasswordException();
                    }
                    JcePEMDecryptorProviderBuilder decryptor = new JcePEMDecryptorProviderBuilder();
                    decryptor.setProvider(provider);
                    PEMKeyPair keyPair = ((PEMEncryptedKeyPair) object).decryptKeyPair(decryptor.build(password));
                    pkInfo = keyPair.getPrivateKeyInfo();
                } else if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
                    if (password == null) {
                        throw new NeedsPasswordException();
                    }
                    JceOpenSSLPKCS8DecryptorProviderBuilder decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder();
                    decryptor.setProvider(provider);
                    pkInfo = ((PKCS8EncryptedPrivateKeyInfo) object).decryptPrivateKeyInfo(decryptor.build(password));
                } else if (object instanceof PrivateKeyInfo) {
                    pkInfo = (PrivateKeyInfo) object;
                } else {
                    continue;
                }
                privateKey = converter.getPrivateKey(pkInfo);
                break;
            }
        } catch (IOException | DecoderException | OperatorCreationException | PKCSException e) {
            throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
        }
        if (privateKey == null) {
            throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
        }
        PublicKey publicKey = cert.getPublicKey();
        checkPrivateKey(context, privateKey, publicKey);
        return privateKey;
    }

    private static void checkPrivateKey(PythonContext context, PrivateKey privateKey, PublicKey publicKey) {
        /*
         * Check that the private key matches the public key by signing and verifying a short piece
         * of data.
         */
        try {
            Signature sign;
            try {
                sign = Signature.getInstance(String.format("SHA256with%s", privateKey.getAlgorithm()));
            } catch (NoSuchAlgorithmException e) {
                sign = Signature.getInstance(String.format("SHA1with%s", privateKey.getAlgorithm()));
            }
            sign.initSign(privateKey);
            byte[] data = new byte[128];
            context.getSecureRandom().nextBytes(data);
            sign.update(data);
            byte[] signature = sign.sign();
            sign.initVerify(publicKey);
            sign.update(data);
            if (sign.verify(signature)) {
                return;
            }
        } catch (NoSuchAlgorithmException e) {
            throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_SSL, e);
        } catch (SignatureException | InvalidKeyException e) {
            // fallthrough
        }
        throw PConstructAndRaiseNode.raiseUncachedSSLError(SSLErrorCode.ERROR_KEY_VALUES_MISMATCH, ErrorMessages.KEY_VALUES_MISMATCH);
    }

    @TruffleBoundary
    static Collection<?> generateCertificates(byte[] bytes) throws CertificateException {
        // test_load_verify_cadata appends an extra byte to a valid certificate and expects
        // a failure.
        // For some reason, CertificateFactory#generateCertificates() consumes the whole input
        // ignoring any extra bytes. Parsing certificate one by one seems to detect them.
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        ArrayList<Object> list = new ArrayList<>();
        ByteArrayInputStream is = new ByteArrayInputStream(bytes);
        while (is.available() > 0) {
            list.add(factory.generateCertificate(is));
        }
        return list;
    }

    @TruffleBoundary
    static String getAlias(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
        // TODO what to use for alias
        return md5(cert.getEncoded());
    }

    @TruffleBoundary
    static String getAlias(PrivateKey pk) throws NoSuchAlgorithmException {
        // TODO what to use for alias
        return md5(pk.getEncoded());
    }

    @TruffleBoundary
    private static String md5(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("md5").digest(bytes);
        return new BigInteger(1, digest).toString(16);
    }
}
