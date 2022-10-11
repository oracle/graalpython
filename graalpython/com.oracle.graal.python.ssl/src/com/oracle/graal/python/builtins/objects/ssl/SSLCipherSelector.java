/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

public class SSLCipherSelector {
    private static final SSLCipher[] TLS3_CIPHER_SUITES = new SSLCipher[]{
                    SSLCipher.TLS_AES_256_GCM_SHA384, SSLCipher.TLS_CHACHA20_POLY1305_SHA256,
                    SSLCipher.TLS_AES_128_GCM_SHA256};

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
            throw SSLErrorBuiltins.raiseUncachedSSLError(ErrorMessages.NO_CIPHER_CAN_BE_SELECTED);
        }
        // The API that CPython uses is meant only for setting <= TLSv1.2 ciphersuites, but it
        // also unconditionally adds a hardcoded list of TLSv1.3 ciphersuites to the beginning
        // of the list (this is not influenced by the parameters). Note CPython doesn't expose
        // any API to manipulate TLSv1.3 ciphersuites, they are just always enabled in this
        // order
        SSLCipher[] result = new SSLCipher[TLS3_CIPHER_SUITES.length + selected.size()];
        System.arraycopy(TLS3_CIPHER_SUITES, 0, result, 0, TLS3_CIPHER_SUITES.length);
        for (int i = 0; i < selected.size(); i++) {
            result[TLS3_CIPHER_SUITES.length + i] = selected.get(i);
        }
        return result;
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
                throw PRaiseNode.raiseUncached(node, NotImplementedError, toTruffleStringUncached("@SECLEVEL not implemented"));
            } else {
                throw SSLErrorBuiltins.raiseUncachedSSLError(ErrorMessages.NO_CIPHER_CAN_BE_SELECTED);
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
            List<SSLCipher> ciphers = SSLCipherStringMapping.get(component);
            if (ciphers == null) {
                if (component.equals("PROFILE=SYSTEM")) {
                    throw PRaiseNode.raiseUncached(node, NotImplementedError, toTruffleStringUncached("PROFILE=SYSTEM not implemented"));
                }
                return Collections.emptyList();
            }
            if (result == null) {
                result = new ArrayList<>(ciphers);
            } else {
                result.retainAll(ciphers);
            }
        }
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }
}
