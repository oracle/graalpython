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
package com.oracle.graal.python.builtins.objects.ssl;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class ASN1Helper {

    static final String JAVA_X509_CA_ISSUERS = "caIssuers";
    static final String JAVA_X509_COMMON_NAME = "commonName";
    static final String JAVA_X509_COUNTRY_NAME = "countryName";
    static final String JAVA_X509_CRL_DISTRIBUTION_POINTS = "crlDistributionPoints";
    static final String JAVA_X509_ISSUER = "issuer";
    static final String JAVA_X509_LOCALITY_NAME = "localityName";
    static final String JAVA_X509_NOT_BEFORE = "notBefore";
    static final String JAVA_X509_NOT_AFTER = "notAfter";
    static final String JAVA_X509_OCSP = "OCSP";
    static final String JAVA_X509_ORGANIZATIONAL_UNIT_NAME = "organizationalUnitName";
    static final String JAVA_X509_ORGANIZATION_NAME = "organizationName";
    static final String JAVA_X509_SERIAL_NUMBER = "serialNumber";
    static final String JAVA_X509_STATE_OR_PROVICE_NAME = "stateOrProvinceName";
    static final String JAVA_X509_SUBJECT_ALT_NAME = "subjectAltName";
    static final String JAVA_X509_SUBJECT = "subject";
    static final String JAVA_X509_VERSION = "version";

    static final String ASN1_EMAILADDRESS = "EMAILADDRESS";
    static final String ASN1_EMAIL = "EMAIL";

    static final String OID_CRL_DISTRIBUTION_POINTS = "2.5.29.31";
    static final String OID_OCSP = "1.3.6.1.5.5.7.48.1";
    static final String OID_CA_ISSUERS = "1.3.6.1.5.5.7.48.2";
    static final String OID_AUTHORITY_INFO_ACCESS = "1.3.6.1.5.5.7.1.1";

    private static final Map<String, String> javaToName = new HashMap<>();

    static {
        javaToName.put("CN", JAVA_X509_COMMON_NAME);
        javaToName.put("C", JAVA_X509_COUNTRY_NAME);
        javaToName.put("L", JAVA_X509_LOCALITY_NAME);
        javaToName.put("S", JAVA_X509_STATE_OR_PROVICE_NAME);
        javaToName.put("ST", JAVA_X509_STATE_OR_PROVICE_NAME);
        javaToName.put("O", JAVA_X509_ORGANIZATION_NAME);
        javaToName.put("OU", JAVA_X509_ORGANIZATIONAL_UNIT_NAME);
        javaToName.put("T", "title");
        javaToName.put("IP", "ipAddress");
        javaToName.put("STREET", "streetAddress");
        javaToName.put("DC", "domainComponent");
        javaToName.put("DNQUALIFIER", "dnQualifier");
        javaToName.put("DNQ", "dnQualifier");
        javaToName.put("SURNAME", "surname");
        javaToName.put("GIVENNAME", "givenName");
        javaToName.put("INITIALS", "initials");
        javaToName.put("GENERATION", "generationQualifier");
        javaToName.put(ASN1_EMAIL, "emailAddress");
        javaToName.put(ASN1_EMAILADDRESS, "emailAddress");
        javaToName.put("UID", "userId");
        javaToName.put("SERIALNUMBER", JAVA_X509_SERIAL_NUMBER);

        // X509 OIDs
        javaToName.put("OID.2.5.4.3", JAVA_X509_COMMON_NAME);
        javaToName.put("OID.2.5.4.4", "surname");
        javaToName.put("OID.2.5.4.5", JAVA_X509_SERIAL_NUMBER);
        javaToName.put("OID.2.5.4.6", JAVA_X509_COUNTRY_NAME);
        javaToName.put("OID.2.5.4.7", JAVA_X509_LOCALITY_NAME);
        javaToName.put("OID.2.5.4.8", JAVA_X509_STATE_OR_PROVICE_NAME);
        javaToName.put("OID.2.5.4.9", "streetAddress");
        javaToName.put("OID.2.5.4.10", JAVA_X509_ORGANIZATION_NAME);
        javaToName.put("OID.2.5.4.11", JAVA_X509_ORGANIZATIONAL_UNIT_NAME);
        javaToName.put("OID.2.5.4.12", "title");
        javaToName.put("OID.2.5.4.13", "description");
        javaToName.put("OID.2.5.4.14", "searchGuide");
        javaToName.put("OID.2.5.4.15", "businessCategory");
        javaToName.put("OID.2.5.4.16", "postalAddress");
        javaToName.put("OID.2.5.4.17", "postalCode");
        javaToName.put("OID.2.5.4.18", "postOfficeBox");
        javaToName.put("OID.2.5.4.19", "physicalDeliveryOfficeName");
        javaToName.put("OID.2.5.4.20", "telephoneNumber");
        javaToName.put("OID.2.5.4.21", "telexNumber");
        javaToName.put("OID.2.5.4.22", "teletexTerminalIdentifier");
        javaToName.put("OID.2.5.4.23", "facsimileTelephoneNumber");
        javaToName.put("OID.2.5.4.24", "x121Address");
        javaToName.put("OID.2.5.4.25", "internationaliSDNNumber");
        javaToName.put("OID.2.5.4.26", "registeredAddress");
        javaToName.put("OID.2.5.4.27", "destinationIndicator");
        javaToName.put("OID.2.5.4.28", "preferredDeliveryMethod");
        javaToName.put("OID.2.5.4.29", "presentationAddress");
        javaToName.put("OID.2.5.4.30", "supportedApplicationContext");
        javaToName.put("OID.2.5.4.31", "member");
        javaToName.put("OID.2.5.4.32", "owner");
        javaToName.put("OID.2.5.4.33", "roleOccupant");
        javaToName.put("OID.2.5.4.34", "seeAlso");
        javaToName.put("OID.2.5.4.35", "userPassword");
        javaToName.put("OID.2.5.4.36", "userCertificate");
        javaToName.put("OID.2.5.4.37", "cACertificate");
        javaToName.put("OID.2.5.4.38", "authorityRevocationList");
        javaToName.put("OID.2.5.4.39", "certificateRevocationList");
        javaToName.put("OID.2.5.4.40", "crossCertificatePair");
        javaToName.put("OID.2.5.4.41", "name");
        javaToName.put("OID.2.5.4.42", "givenName");
        javaToName.put("OID.2.5.4.43", "initials");
        javaToName.put("OID.2.5.4.44", "generationQualifier");
        javaToName.put("OID.2.5.4.45", "x500UniqueIdentifier");
        javaToName.put("OID.2.5.4.46", "dnQualifier");
        javaToName.put("OID.2.5.4.47", "enhancedSearchGuide");
        javaToName.put("OID.2.5.4.48", "protocolInformation");
        javaToName.put("OID.2.5.4.49", "distinguishedName");
        javaToName.put("OID.2.5.4.50", "uniqueMember");
        javaToName.put("OID.2.5.4.51", "houseIdentifier");
        javaToName.put("OID.2.5.4.52", "supportedAlgorithms");
        javaToName.put("OID.2.5.4.53", "deltaRevocationList");
        javaToName.put("OID.2.5.4.54", "dmdName");
        javaToName.put("OID.2.5.4.65", "pseudonym");
        javaToName.put("OID.2.5.4.72", "role");
        javaToName.put("OID.2.5.4.97", "organizationIdentifier");
        javaToName.put("OID.2.5.4.98", "countryCode3c");
        javaToName.put("OID.2.5.4.99", "countryCode3n");
        javaToName.put("OID.2.5.4.100", "dnsName");

        // CA/Browser Forum OIDs
        javaToName.put("OID.1.3.6.1.4.1.311.60.2.1.1", "jurisdictionLocalityName");
        javaToName.put("OID.1.3.6.1.4.1.311.60.2.1.2", "jurisdictionStateOrProvinceName");
        javaToName.put("OID.1.3.6.1.4.1.311.60.2.1.3", "jurisdictionCountryName");

    }

    @TruffleBoundary
    public static String translateKeyToPython(String javaName) {
        return javaToName.getOrDefault(javaName, javaName);
    }
}
