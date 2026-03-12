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
package com.oracle.graal.python.bouncycastle;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import com.oracle.graal.python.builtins.objects.ssl.CertUtils.NeedsPasswordException;
import com.oracle.graal.python.builtins.objects.ssl.SSLBouncyCastleSupport;

public final class BCSSLBouncyCastleSupport implements SSLBouncyCastleSupport {
    private static Provider getProvider() {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        return provider != null ? provider : new BouncyCastleProvider();
    }

    @Override
    public PrivateKey loadPrivateKey(char[] password, String pemText) throws IOException, NeedsPasswordException, GeneralSecurityException {
        try (PEMParser pemParser = new PEMParser(new StringReader(pemText))) {
            Provider provider = getProvider();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(provider);
            Object object;
            while ((object = pemParser.readObject()) != null) {
                PrivateKeyInfo pkInfo;
                if (object instanceof PEMKeyPair) {
                    pkInfo = ((PEMKeyPair) object).getPrivateKeyInfo();
                } else if (object instanceof PEMEncryptedKeyPair) {
                    if (password == null) {
                        throw new NeedsPasswordException();
                    }
                    JcePEMDecryptorProviderBuilder decryptor = new JcePEMDecryptorProviderBuilder().setProvider(provider);
                    PEMKeyPair keyPair = ((PEMEncryptedKeyPair) object).decryptKeyPair(decryptor.build(password));
                    pkInfo = keyPair.getPrivateKeyInfo();
                } else if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
                    if (password == null) {
                        throw new NeedsPasswordException();
                    }
                    JceOpenSSLPKCS8DecryptorProviderBuilder decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder().setProvider(provider);
                    pkInfo = ((PKCS8EncryptedPrivateKeyInfo) object).decryptPrivateKeyInfo(decryptor.build(password));
                } else if (object instanceof PrivateKeyInfo) {
                    pkInfo = (PrivateKeyInfo) object;
                } else {
                    continue;
                }
                return converter.getPrivateKey(pkInfo);
            }
            return null;
        } catch (OperatorCreationException | PKCSException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
