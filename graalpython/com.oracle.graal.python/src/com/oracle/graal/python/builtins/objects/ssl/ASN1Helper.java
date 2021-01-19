package com.oracle.graal.python.builtins.objects.ssl;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class ASN1Helper {
    private static final Map<String, String> javaToName = new HashMap<>();

    static {
        javaToName.put("CN", "commonName");
        javaToName.put("C", "countryName");
        javaToName.put("L", "localityName");
        javaToName.put("S", "stateOrProvinceName");
        javaToName.put("ST", "stateOrProvinceName");
        javaToName.put("O", "organizationName");
        javaToName.put("OU", "organizationalUnitName");
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
        javaToName.put("EMAIL", "emailAddress");
        javaToName.put("EMAILADDRESS", "emailAddress");
        javaToName.put("UID", "userId");
        javaToName.put("SERIALNUMBER", "serialNumber");

        // X509 OIDs
        javaToName.put("OID.2.5.4.3", "commonName");
        javaToName.put("OID.2.5.4.4", "surname");
        javaToName.put("OID.2.5.4.5", "serialNumber");
        javaToName.put("OID.2.5.4.6", "countryName");
        javaToName.put("OID.2.5.4.7", "localityName");
        javaToName.put("OID.2.5.4.8", "stateOrProvinceName");
        javaToName.put("OID.2.5.4.9", "streetAddress");
        javaToName.put("OID.2.5.4.10", "organizationName");
        javaToName.put("OID.2.5.4.11", "organizationalUnitName");
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
