package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.ASN1_EMAIL;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.ASN1_EMAILADDRESS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_COMMON_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_COUNTRY_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_CRL_DISTRIBUTION_POINTS;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_ISSUER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_LOCALITY_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_NOT_AFTER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_NOT_BEFORE;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_ORGANIZATIONAL_UNIT_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_ORGANIZATION_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_SERIAL_NUMBER;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_SUBJECT;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_SUBJECT_ALT_NAME;
import static com.oracle.graal.python.builtins.objects.ssl.ASN1Helper.JAVA_X509_VERSION;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import sun.security.util.DerValue;
import sun.security.x509.CRLDistributionPointsExtension;
import sun.security.x509.DistributionPoint;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNameInterface;
import sun.security.x509.GeneralNames;
import sun.security.x509.URIName;
import sun.security.x509.X500Name;

final class CertUtils {

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
    static PDict decodeCertificate(X509Certificate cert, HashingStorageLibrary hlib, PythonObjectFactory factory) throws CertificateParsingException, IOException {
        PDict dict = factory.createDict();
        HashingStorage storage = dict.getDictStorage();
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
        BigInteger sn = x509Certificate.getSerialNumber();
        int signum = sn.signum();
        if (signum == 0) {
            return "00";
        }
        return sn.toString(16);
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
            addTuple(factory, result, JAVA_X509_ORGANIZATION_NAME, dn.getOrganization());
            addTuple(factory, result, JAVA_X509_ORGANIZATIONAL_UNIT_NAME, dn.getOrganizationalUnit());
            addTuple(factory, result, JAVA_X509_COMMON_NAME, dn.getCommonName());
            addTuple(factory, result, JAVA_X509_LOCALITY_NAME, dn.getLocality());
            addTuple(factory, result, JAVA_X509_COUNTRY_NAME, dn.getCountry());
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
        byte[] bytes = cert.getExtensionValue(ASN1Helper.OID_CRL_DISTRIBUTION_POINTS);
        if (bytes != null) {
            DerValue val = new DerValue(bytes);
            bytes = val.getOctetString();
            CRLDistributionPointsExtension CDPExt = new CRLDistributionPointsExtension(false, bytes);
            List<DistributionPoint> points = CDPExt.get("points");
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
}
