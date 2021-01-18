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
        // TODO add entries for numeric OIDs
    }

    @TruffleBoundary
    public static String translateKeyToPython(String javaName) {
        return javaToName.getOrDefault(javaName, javaName);
    }
}
