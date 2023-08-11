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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

public class ASN1Helper {

    static final TruffleString T_JAVA_X509_CA_ISSUERS = tsLiteral("caIssuers");
    static final TruffleString T_JAVA_X509_COMMON_NAME = tsLiteral("commonName");
    static final TruffleString T_JAVA_X509_COUNTRY_NAME = tsLiteral("countryName");
    static final TruffleString T_JAVA_X509_CRL_DISTRIBUTION_POINTS = tsLiteral("crlDistributionPoints");
    static final TruffleString T_JAVA_X509_ISSUER = tsLiteral("issuer");
    static final TruffleString T_JAVA_X509_LOCALITY_NAME = tsLiteral("localityName");
    static final TruffleString T_JAVA_X509_NOT_BEFORE = tsLiteral("notBefore");
    static final TruffleString T_JAVA_X509_NOT_AFTER = tsLiteral("notAfter");
    static final TruffleString T_JAVA_X509_OCSP = tsLiteral("OCSP");
    static final TruffleString T_JAVA_X509_ORGANIZATIONAL_UNIT_NAME = tsLiteral("organizationalUnitName");
    static final TruffleString T_JAVA_X509_ORGANIZATION_NAME = tsLiteral("organizationName");
    static final TruffleString T_JAVA_X509_SERIAL_NUMBER = tsLiteral("serialNumber");
    static final TruffleString T_JAVA_X509_STATE_OR_PROVICE_NAME = tsLiteral("stateOrProvinceName");
    static final TruffleString T_JAVA_X509_SUBJECT_ALT_NAME = tsLiteral("subjectAltName");
    static final TruffleString T_JAVA_X509_SUBJECT = tsLiteral("subject");
    static final TruffleString T_JAVA_X509_VERSION = tsLiteral("version");

    static final String ASN1_EMAILADDRESS = "EMAILADDRESS";
    static final String ASN1_EMAIL = "EMAIL";

    static final String OID_CRL_DISTRIBUTION_POINTS = "2.5.29.31";
    static final byte[] OID_OCSP = new byte[]{/* 1, 3, 6, */ 1, 5, 5, 7, 48, 1};
    static final byte[] OID_CA_ISSUERS = new byte[]{/* 1, 3, 6, */ 1, 5, 5, 7, 48, 2};
    static final String OID_AUTHORITY_INFO_ACCESS = "1.3.6.1.5.5.7.1.1";

    private static final Map<String, TruffleString> javaToName = new HashMap<>();

    static {
        javaToName.put("CN", T_JAVA_X509_COMMON_NAME);
        javaToName.put("C", T_JAVA_X509_COUNTRY_NAME);
        javaToName.put("L", T_JAVA_X509_LOCALITY_NAME);
        javaToName.put("S", T_JAVA_X509_STATE_OR_PROVICE_NAME);
        javaToName.put("ST", T_JAVA_X509_STATE_OR_PROVICE_NAME);
        javaToName.put("O", T_JAVA_X509_ORGANIZATION_NAME);
        javaToName.put("OU", T_JAVA_X509_ORGANIZATIONAL_UNIT_NAME);
        javaToName.put("T", tsLiteral("title"));
        javaToName.put("IP", tsLiteral("ipAddress"));
        javaToName.put("STREET", tsLiteral("streetAddress"));
        javaToName.put("DC", tsLiteral("domainComponent"));
        javaToName.put("DNQUALIFIER", tsLiteral("dnQualifier"));
        javaToName.put("DNQ", tsLiteral("dnQualifier"));
        javaToName.put("SURNAME", tsLiteral("surname"));
        javaToName.put("GIVENNAME", tsLiteral("givenName"));
        javaToName.put("INITIALS", tsLiteral("initials"));
        javaToName.put("GENERATION", tsLiteral("generationQualifier"));
        javaToName.put(ASN1_EMAIL, tsLiteral("emailAddress"));
        javaToName.put(ASN1_EMAILADDRESS, tsLiteral("emailAddress"));
        javaToName.put("UID", tsLiteral("userId"));
        javaToName.put("SERIALNUMBER", T_JAVA_X509_SERIAL_NUMBER);

        // X509 OIDs
        javaToName.put("OID.1.2.840.113549.1.9.1", tsLiteral("emailAddress"));
        javaToName.put("OID.2.5.4.3", T_JAVA_X509_COMMON_NAME);
        javaToName.put("OID.2.5.4.4", tsLiteral("surname"));
        javaToName.put("OID.2.5.4.5", T_JAVA_X509_SERIAL_NUMBER);
        javaToName.put("OID.2.5.4.6", T_JAVA_X509_COUNTRY_NAME);
        javaToName.put("OID.2.5.4.7", T_JAVA_X509_LOCALITY_NAME);
        javaToName.put("OID.2.5.4.8", T_JAVA_X509_STATE_OR_PROVICE_NAME);
        javaToName.put("OID.2.5.4.9", tsLiteral("streetAddress"));
        javaToName.put("OID.2.5.4.10", T_JAVA_X509_ORGANIZATION_NAME);
        javaToName.put("OID.2.5.4.11", T_JAVA_X509_ORGANIZATIONAL_UNIT_NAME);
        javaToName.put("OID.2.5.4.12", tsLiteral("title"));
        javaToName.put("OID.2.5.4.13", tsLiteral("description"));
        javaToName.put("OID.2.5.4.14", tsLiteral("searchGuide"));
        javaToName.put("OID.2.5.4.15", tsLiteral("businessCategory"));
        javaToName.put("OID.2.5.4.16", tsLiteral("postalAddress"));
        javaToName.put("OID.2.5.4.17", tsLiteral("postalCode"));
        javaToName.put("OID.2.5.4.18", tsLiteral("postOfficeBox"));
        javaToName.put("OID.2.5.4.19", tsLiteral("physicalDeliveryOfficeName"));
        javaToName.put("OID.2.5.4.20", tsLiteral("telephoneNumber"));
        javaToName.put("OID.2.5.4.21", tsLiteral("telexNumber"));
        javaToName.put("OID.2.5.4.22", tsLiteral("teletexTerminalIdentifier"));
        javaToName.put("OID.2.5.4.23", tsLiteral("facsimileTelephoneNumber"));
        javaToName.put("OID.2.5.4.24", tsLiteral("x121Address"));
        javaToName.put("OID.2.5.4.25", tsLiteral("internationaliSDNNumber"));
        javaToName.put("OID.2.5.4.26", tsLiteral("registeredAddress"));
        javaToName.put("OID.2.5.4.27", tsLiteral("destinationIndicator"));
        javaToName.put("OID.2.5.4.28", tsLiteral("preferredDeliveryMethod"));
        javaToName.put("OID.2.5.4.29", tsLiteral("presentationAddress"));
        javaToName.put("OID.2.5.4.30", tsLiteral("supportedApplicationContext"));
        javaToName.put("OID.2.5.4.31", tsLiteral("member"));
        javaToName.put("OID.2.5.4.32", tsLiteral("owner"));
        javaToName.put("OID.2.5.4.33", tsLiteral("roleOccupant"));
        javaToName.put("OID.2.5.4.34", tsLiteral("seeAlso"));
        javaToName.put("OID.2.5.4.35", tsLiteral("userPassword"));
        javaToName.put("OID.2.5.4.36", tsLiteral("userCertificate"));
        javaToName.put("OID.2.5.4.37", tsLiteral("cACertificate"));
        javaToName.put("OID.2.5.4.38", tsLiteral("authorityRevocationList"));
        javaToName.put("OID.2.5.4.39", tsLiteral("certificateRevocationList"));
        javaToName.put("OID.2.5.4.40", tsLiteral("crossCertificatePair"));
        javaToName.put("OID.2.5.4.41", tsLiteral("name"));
        javaToName.put("OID.2.5.4.42", tsLiteral("givenName"));
        javaToName.put("OID.2.5.4.43", tsLiteral("initials"));
        javaToName.put("OID.2.5.4.44", tsLiteral("generationQualifier"));
        javaToName.put("OID.2.5.4.45", tsLiteral("x500UniqueIdentifier"));
        javaToName.put("OID.2.5.4.46", tsLiteral("dnQualifier"));
        javaToName.put("OID.2.5.4.47", tsLiteral("enhancedSearchGuide"));
        javaToName.put("OID.2.5.4.48", tsLiteral("protocolInformation"));
        javaToName.put("OID.2.5.4.49", tsLiteral("distinguishedName"));
        javaToName.put("OID.2.5.4.50", tsLiteral("uniqueMember"));
        javaToName.put("OID.2.5.4.51", tsLiteral("houseIdentifier"));
        javaToName.put("OID.2.5.4.52", tsLiteral("supportedAlgorithms"));
        javaToName.put("OID.2.5.4.53", tsLiteral("deltaRevocationList"));
        javaToName.put("OID.2.5.4.54", tsLiteral("dmdName"));
        javaToName.put("OID.2.5.4.65", tsLiteral("pseudonym"));
        javaToName.put("OID.2.5.4.72", tsLiteral("role"));
        javaToName.put("OID.2.5.4.97", tsLiteral("organizationIdentifier"));
        javaToName.put("OID.2.5.4.98", tsLiteral("countryCode3c"));
        javaToName.put("OID.2.5.4.99", tsLiteral("countryCode3n"));
        javaToName.put("OID.2.5.4.100", tsLiteral("dnsName"));

        // CA/Browser Forum OIDs
        javaToName.put("OID.1.3.6.1.4.1.311.60.2.1.1", tsLiteral("jurisdictionLocalityName"));
        javaToName.put("OID.1.3.6.1.4.1.311.60.2.1.2", tsLiteral("jurisdictionStateOrProvinceName"));
        javaToName.put("OID.1.3.6.1.4.1.311.60.2.1.3", tsLiteral("jurisdictionCountryName"));

    }

    @TruffleBoundary
    public static TruffleString translateKeyToPython(String javaName) {
        TruffleString pythonName = javaToName.get(javaName);
        return pythonName == null ? toTruffleStringUncached(javaName) : pythonName;
    }
}
