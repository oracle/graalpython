package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

public class SSLCipherSelector {
    private static final SSLCipher[] TLS3_CIPHER_SUITES = new SSLCipher[]{
                    SSLCipher.TLS_AES_256_GCM_SHA384, SSLCipher.TLS_CHACHA20_POLY1305_SHA256,
                    SSLCipher.TLS_AES_128_GCM_SHA256, SSLCipher.TLS_AES_128_CCM_SHA256};

    @TruffleBoundary
    public static SSLCipher[] selectCiphers(Node node, String cipherList) {
        List<SSLCipher> selected = new LinkedList<>();
        Set<SSLCipher> deleted = new HashSet<>();
        // Handle ciphersuites for TLS version <= 1.2. TLSv1.3 ciphersuites are handled
        // separately.
        selectCiphersFromList(node, cipherList, selected, deleted);
        // The call fails when no <= TLSv1.2 ciphersuites get selected, regardless of TLSv1.3
        // ciphersuites
        if (selected.size() == 0) {
            throw PRaiseNode.raiseUncached(node, SSLError, ErrorMessages.NO_CIPHER_CAN_BE_SELECTED);
        }
        // The API that CPython uses is meant only for setting <= TLSv1.2 ciphersuites, but it
        // also unconditionally adds a hardcoded list of TLSv1.3 ciphersuites to the beginning
        // of the list (this is not influenced by the parameters). Note CPython doesn't expose
        // any API to manipulate TLSv1.3 ciphersuites, they are just always enabled in this
        // order
        for (int i = TLS3_CIPHER_SUITES.length - 1; i >= 0; i--) {
            selected.add(0, TLS3_CIPHER_SUITES[i]);
        }
        return selected.toArray(new SSLCipher[0]);
    }

    private static void selectCiphersFromList(Node node, String cipherList, List<SSLCipher> selected, Set<SSLCipher> deleted) {
        for (String cipherString : cipherList.split("[:, ]")) {
            selectSingle(node, cipherString, selected, deleted);
        }
    }

    private static void selectSingle(Node node, String cipherString, List<SSLCipher> selected, Set<SSLCipher> deleted) {
        if (cipherString.startsWith("!")) {
            // Remove the ciphers from the list and prevent them from reappearing
            List<SSLCipher> ciphers = getCiphersForCipherString(node, cipherString.substring(1));
            selected.removeAll(ciphers);
            deleted.addAll(ciphers);
        } else if (cipherString.startsWith("-")) {
            selected.removeAll(getCiphersForCipherString(node, cipherString.substring(1)));
        } else if (cipherString.startsWith("+")) {
            for (SSLCipher cipher : getCiphersForCipherString(node, cipherString.substring(1))) {
                if (selected.remove(cipher)) {
                    selected.add(cipher);
                }
            }
        } else if (cipherString.startsWith("@")) {
            if (cipherString.startsWith("@STRENGTH")) {
                selected.sort(Comparator.comparingInt(SSLCipher::getStrengthBits).reversed());
            } else if (cipherString.startsWith("@SECLEVEL=")) {
                throw PRaiseNode.raiseUncached(node, NotImplementedError, "@SECLEVEL not implemented");
            } else {
                throw PRaiseNode.raiseUncached(node, SSLError, ErrorMessages.NO_CIPHER_CAN_BE_SELECTED);
            }
        } else if (cipherString.equals("DEFAULT")) {
            selectCiphersFromList(node, "ALL:!COMPLEMENTOFDEFAULT:!eNULL", selected, deleted);
        } else {
            List<SSLCipher> ciphers = getCiphersForCipherString(node, cipherString);
            for (SSLCipher cipher : ciphers) {
                if (!deleted.contains(cipher) && !selected.contains(cipher)) {
                    selected.add(cipher);
                }
            }
        }
    }

    private static List<SSLCipher> getCiphersForCipherString(Node node, String cipherString) {
        List<SSLCipher> result = null;
        for (String component : cipherString.split("\\+")) {
            SSLCipher[] ciphers = SSLCipherStringMapping.get(component);
            if (ciphers == null) {
                if (component.equals("PROFILE=SYSTEM")) {
                    throw PRaiseNode.raiseUncached(node, NotImplementedError, "PROFILE=SYSTEM not implemented");
                }
                return Collections.emptyList();
            }
            if (result == null) {
                result = new ArrayList<>(Arrays.asList(ciphers));
            } else {
                result.retainAll(Arrays.asList(ciphers));
            }
        }
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }
}
