package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.ASN1_EMAIL;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.ASN1_EMAILADDRESS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_CA_ISSUERS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_COMMON_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_COUNTRY_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_CRL_DISTRIBUTION_POINTS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_ISSUER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_LOCALITY_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_NOT_AFTER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_NOT_BEFORE;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_OCSP;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_ORGANIZATIONAL_UNIT_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_ORGANIZATION_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_SERIAL_NUMBER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_STATE_OR_PROVICE_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_SUBJECT;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_SUBJECT_ALT_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_VERSION;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_AUTHORITY_INFO_ACCESS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_CA_ISSUERS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.OID_CRL_DISTRIBUTION_POINTS;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.Node;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import javax.crypto.spec.DHParameterSpec;
import sun.security.provider.certpath.OCSP;
import sun.security.util.DerValue;
import sun.security.x509.AccessDescription;
import sun.security.x509.AuthorityInfoAccessExtension;
import sun.security.x509.CRLDistributionPointsExtension;
import sun.security.x509.DistributionPoint;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.GeneralNames;
import sun.security.x509.URIName;
import sun.security.x509.X500Name;

public final class CertUtils {

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String BEGIN_DH_PARAMETERS = "-----BEGIN DH PARAMETERS-----";
    private static final String END_DH_PARAMETERS = "-----END DH PARAMETERS-----";

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
    static boolean isSelfSigned(X509Certificate cert) {
        try {
            cert.verify(cert.getPublicKey());
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException | CertificateException e) {
            return false;
        }
        return true;
    }

    @TruffleBoundary
    static boolean isCrl(X509Certificate cert, boolean[] keyUsage) {
        return keyUsage != null && keyUsage.length > 6 && keyUsage[6];
    }

    /**
     * _ssl.c#_decode_certificate
     */
    public static PDict decodeCertificate(X509Certificate cert, HashingStorageLibrary hlib, PythonObjectFactory factory) throws CertificateParsingException, IOException {
        PDict dict = factory.createDict();
        HashingStorage storage = dict.getDictStorage();
        storage = setItem(hlib, storage, JAVA_X509_OCSP, parseOCSP(cert, factory));
        storage = setItem(hlib, storage, JAVA_X509_CA_ISSUERS, parseCAIssuers(cert, factory));
        storage = setItem(hlib, storage, JAVA_X509_ISSUER, parseX500Name(cert.getIssuerDN(), factory));
        storage = setItem(hlib, storage, JAVA_X509_NOT_AFTER, getNotAfter(cert));
        storage = setItem(hlib, storage, JAVA_X509_NOT_BEFORE, getNotBefore(cert));
        storage = setItem(hlib, storage, JAVA_X509_SERIAL_NUMBER, getSerialNumber(cert));
        storage = setItem(hlib, storage, JAVA_X509_CRL_DISTRIBUTION_POINTS, parseCRLPoints(cert, factory));
        storage = setItem(hlib, storage, JAVA_X509_SUBJECT, parseX500Name(cert.getSubjectDN(), factory));
        storage = setItem(hlib, storage, JAVA_X509_SUBJECT_ALT_NAME, parseSubjectAltName(cert, factory));
        storage = setItem(hlib, storage, JAVA_X509_VERSION, getVersion(cert));
        dict.setDictStorage(storage);
        return dict;
    }

    private static HashingStorage setItem(HashingStorageLibrary hlib, HashingStorage storage, String key, Object value) throws CertificateParsingException {
        if (value != null) {
            return hlib.setItem(storage, key, value);
        } else {
            return storage;
        }
    }

    @TruffleBoundary
    private static String getSerialNumber(X509Certificate x509Certificate) {
        String sn = x509Certificate.getSerialNumber().toString(16).toUpperCase();
        return sn.length() == 1 ? "0" + sn : sn;
    }

    @TruffleBoundary
    private static int getVersion(X509Certificate x509Certificate) {
        return x509Certificate.getVersion();
    }

    @TruffleBoundary
    private static String getNotAfter(X509Certificate x509Certificate) {
        return formatDate(x509Certificate.getNotAfter());
    }

    @TruffleBoundary
    private static String getNotBefore(X509Certificate x509Certificate) {
        return formatDate(x509Certificate.getNotBefore());
    }

    private static final SimpleDateFormat DF1 = new SimpleDateFormat("MMM  d HH:mm:ss yyyy z");
    private static final SimpleDateFormat DF2 = new SimpleDateFormat("MMM dd HH:mm:ss yyyy z");
    private static final Calendar CAL = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    static {
        DF1.setTimeZone(TimeZone.getTimeZone("GMT"));
        DF2.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @TruffleBoundary
    private static String formatDate(Date d) {
        CAL.setTime(d);
        int day = CAL.get(Calendar.DAY_OF_MONTH);
        if (day < 10) {
            return DF1.format(d);
        } else {
            return DF2.format(d);
        }
    }

    @TruffleBoundary
    private static PTuple parseX500Name(Principal p, PythonObjectFactory factory) throws IOException {
        if (p instanceof X500Name) {
            X500Name dn = (X500Name) p;
            List<PTuple> result = new ArrayList<>(6);
            addTuple(factory, result, JAVA_X509_COUNTRY_NAME, dn.getCountry());
            addTuple(factory, result, JAVA_X509_STATE_OR_PROVICE_NAME, dn.getState());
            addTuple(factory, result, JAVA_X509_LOCALITY_NAME, dn.getLocality());
            addTuple(factory, result, JAVA_X509_ORGANIZATION_NAME, dn.getOrganization());
            addTuple(factory, result, JAVA_X509_ORGANIZATIONAL_UNIT_NAME, dn.getOrganizationalUnit());
            addTuple(factory, result, JAVA_X509_COMMON_NAME, dn.getCommonName());
            parseAndAddName(dn.getName(), result, factory, ASN1_EMAIL, ASN1_EMAILADDRESS);
            return factory.createTuple(result.toArray(new PTuple[result.size()]));
        }
        return null;
    }

    @TruffleBoundary
    private static void parseAndAddName(String name, List<PTuple> result, PythonObjectFactory factory, String... fields) throws IOException {
        List<PTuple> tuples = new ArrayList<>(16);
        for (String component : name.split(",")) {
            String[] kv = component.split("=");
            if (kv.length == 2) {
                for (String f : fields) {
                    if (f.equals(kv[0].trim())) {
                        addTuple(factory, result, ASN1Helper.translateKeyToPython(kv[0].trim()), kv[1].trim());
                        break;
                    }
                }
            }
        }
        result.addAll(tuples);
    }

    private static void addTuple(PythonObjectFactory factory, List<PTuple> tuples, String... s) {
        assert s.length == 2;
        if (s[1] == null || s[1].isEmpty()) {
            return;
        }
        tuples.add(factory.createTuple(new Object[]{factory.createTuple(s)}));
    }

    @TruffleBoundary
    private static PTuple parseSubjectAltName(X509Certificate certificate, PythonObjectFactory factory) throws CertificateParsingException {
        List<Object> tuples = new ArrayList<>(16);
        Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
        if (altNames != null) {
            for (List<?> altName : altNames) {
                if (altName.size() == 2 && altName.get(0) instanceof Integer) {
                    int type = (Integer) altName.get(0);
                    Object value = altName.get(1);
                    switch (type) {
                        case 2:
                            tuples.add(factory.createTuple(new Object[]{"DNS", value}));
                            break;
                        default:
                            // TODO other types
                            continue;
                    }
                }
            }
            return factory.createTuple(tuples.toArray(new Object[0]));
        }
        return null;
    }

    @TruffleBoundary
    private static PTuple parseCRLPoints(X509Certificate cert, PythonObjectFactory factory) throws IOException {
        List<String> result = new ArrayList<>();
        byte[] bytes = cert.getExtensionValue(OID_CRL_DISTRIBUTION_POINTS);
        if (bytes != null) {
            DerValue val = new DerValue(bytes);
            bytes = val.getOctetString();
            CRLDistributionPointsExtension cdpe;
            try {
                cdpe = new CRLDistributionPointsExtension(false, bytes);
            } catch (IOException ex) {
                // just ignore
                // TODO log at least?
                return null;
            }
            List<DistributionPoint> points = cdpe.get("points");
            if (points != null) {
                for (DistributionPoint point : points) {
                    GeneralNames fullName = point.getFullName();
                    if (fullName != null) {
                        List<GeneralName> names = fullName.names();
                        if (names != null) {
                            for (GeneralName generalName : names) {
                                GeneralNameInterface n = generalName.getName();
                                if (n instanceof URIName) {
                                    result.add(((URIName) n).getURI().toString());
                                }
                            }
                        }
                    }
                }
            }
            return factory.createTuple(result.toArray(new String[result.size()]));
        }
        return null;
    }

    @TruffleBoundary
    private static PTuple parseCAIssuers(X509Certificate cert, PythonObjectFactory factory) throws IOException {
        List<String> result = new ArrayList<>();
        byte[] bytes = cert.getExtensionValue(OID_AUTHORITY_INFO_ACCESS);
        if (bytes != null) {
            DerValue val = new DerValue(bytes);
            bytes = val.getOctetString();
            AuthorityInfoAccessExtension aiae = new AuthorityInfoAccessExtension(false, bytes);
            for (AccessDescription ad : aiae.getAccessDescriptions()) {
                if (ad.getAccessMethod().toString().equals(OID_CA_ISSUERS)) {
                    GeneralName gn = ad.getAccessLocation();
                    if (gn != null) {
                        GeneralNameInterface n = gn.getName();
                        if (n instanceof URIName) {
                            result.add(((URIName) n).getURI().toString());
                        }
                    }
                }
            }
            return factory.createTuple(result.toArray(new String[result.size()]));
        }
        return null;
    }

    @TruffleBoundary
    private static PTuple parseOCSP(X509Certificate cert, PythonObjectFactory factory) throws IOException {
        URI ocsp = OCSP.getResponderURI(cert);
        if (ocsp != null) {
            return factory.createTuple(new String[]{ocsp.toString()});
        }
        return null;
    }

    public enum LoadCertError {
        NO_ERROR,
        NO_CERT_DATA,
        EMPTY_CERT,
        BEGIN_CERTIFICATE_WITHOUT_END,
        SOME_BAD_BASE64_DECODE,
        BAD_BASE64_DECODE;
    }

    @TruffleBoundary
    public static LoadCertError getCertificates(BufferedReader r, List<X509Certificate> result) throws IOException, CertificateException {
        Base64.Decoder decoder = Base64.getDecoder();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        boolean sawBegin = false;
        boolean someData = false;
        StringBuilder sb = new StringBuilder(2000);
        List<String> data = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (sawBegin) {
                if (line.contains(BEGIN_CERTIFICATE)) {
                    break;
                }
                if (line.contains(END_CERTIFICATE)) {
                    sawBegin = false;
                    if (!someData && sb.length() > 0) {
                        someData = true;
                    }
                    data.add(sb.toString());
                } else {
                    sb.append(line);
                }
            } else if (line.contains(BEGIN_CERTIFICATE)) {
                sawBegin = true;
                sb.setLength(0);
            }
        }
        if (sawBegin) {
            return LoadCertError.BEGIN_CERTIFICATE_WITHOUT_END;
        }
        for (String s : data) {
            if (!s.isEmpty()) {
                byte[] der;
                try {
                    der = decoder.decode(s);
                } catch (IllegalArgumentException e) {
                    if (result.isEmpty()) {
                        return LoadCertError.BAD_BASE64_DECODE;
                    } else {
                        return LoadCertError.SOME_BAD_BASE64_DECODE;
                    }
                }
                result.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
            } else if (someData) {
                return LoadCertError.EMPTY_CERT;
            }
        }
        if (result.isEmpty()) {
            return LoadCertError.NO_CERT_DATA;
        }
        return LoadCertError.NO_ERROR;
    }

    /**
     * Returns the first private key found
     */
    @TruffleBoundary
    static PrivateKey getPrivateKey(Node node, TruffleFile pkPem, String alg) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        boolean begin = false;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = pkPem.newBufferedReader()) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!begin && line.contains(BEGIN_PRIVATE_KEY)) {
                    begin = true;
                } else if (begin) {
                    if (line.contains(END_PRIVATE_KEY)) {
                        begin = false;
                        // get first private key found
                        break;
                    }
                    sb.append(line);
                }
            }
        }
        if (begin || sb.length() == 0) {
            // TODO: append any additional info? original msg is e.g. "[SSL] PEM lib (_ssl.c:3991)"
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(sb.toString());
        } catch (IllegalArgumentException e) {
            throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_SSL_PEM_LIB, ErrorMessages.SSL_PEM_LIB);
        }
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        KeyFactory factory = KeyFactory.getInstance(alg);
        return factory.generatePrivate(spec);
    }

    @TruffleBoundary
    static DHParameterSpec getDHParameters(Node node, File file) throws IOException, NoSuchAlgorithmException, InvalidParameterSpecException {
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            // TODO: test me!
            String line;
            boolean begin = false;
            StringBuilder sb = new StringBuilder();
            while ((line = r.readLine()) != null) {
                if (line.contains(BEGIN_DH_PARAMETERS)) {
                    begin = true;
                } else if (begin) {
                    if (line.contains(END_DH_PARAMETERS)) {
                        break;
                    }
                    sb.append(line.trim());
                }
            }
            if (!begin) {
                throw PRaiseSSLErrorNode.raiseUncached(node, SSLErrorCode.ERROR_NO_START_LINE, ErrorMessages.SSL_PEM_NO_START_LINE);
            }
            AlgorithmParameters ap = AlgorithmParameters.getInstance("DH");
            ap.init(Base64.getDecoder().decode(sb.toString()));
            return ap.getParameterSpec(DHParameterSpec.class);
        }
    }
}
