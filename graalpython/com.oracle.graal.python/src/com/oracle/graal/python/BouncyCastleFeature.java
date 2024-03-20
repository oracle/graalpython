/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python;

import java.security.Security;

import com.oracle.graal.python.runtime.PythonImageBuildOptions;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.python.builtins.objects.ssl.CertUtils;

public class BouncyCastleFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!PythonImageBuildOptions.WITHOUT_SSL) {
            RuntimeClassInitializationSupport support = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
            support.initializeAtBuildTime("org.bouncycastle", "security provider");
            support.rerunInitialization("org.bouncycastle.jcajce.provider.drbg.DRBG$Default", "RNG");
            support.rerunInitialization("org.bouncycastle.jcajce.provider.drbg.DRBG$NonceAndIV", "RNG");
            Security.addProvider(CertUtils.BOUNCYCASTLE_PROVIDER);

            // Register runtime reflection here, not in a config, so it can be easily disabled
            String[] reflectiveClasses = new String[]{
                            "org.bouncycastle.jcajce.provider.asymmetric.COMPOSITE$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.DH$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.DSA$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.DSTU4145$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.EC$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.ECGOST$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.EdEC$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.ElGamal$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.GM$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.GOST$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.IES$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.RSA$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.X509$Mappings",
                            "org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi$ECDSA",
                            "org.bouncycastle.jcajce.provider.asymmetric.ec.SignatureSpi$ecDSA",
                            "org.bouncycastle.jcajce.provider.asymmetric.rsa.KeyFactorySpi",
                            "org.bouncycastle.jcajce.provider.digest.Blake2b$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.Blake2s$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.DSTU7564$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.GOST3411$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.Haraka$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.Keccak$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.MD2$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.MD4$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.MD5$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.RIPEMD128$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.RIPEMD160$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.RIPEMD256$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.RIPEMD320$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SHA1$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SHA224$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SHA256$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SHA3$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SHA384$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SHA512$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.SM3$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.Skein$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.Tiger$Mappings",
                            "org.bouncycastle.jcajce.provider.digest.Whirlpool$Mappings",
                            "org.bouncycastle.jcajce.provider.drbg.DRBG$Mappings",
                            "org.bouncycastle.jcajce.provider.keystore.BC$Mappings",
                            "org.bouncycastle.jcajce.provider.keystore.BCFKS$Mappings",
                            "org.bouncycastle.jcajce.provider.keystore.PKCS12$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.AES$AlgParams",
                            "org.bouncycastle.jcajce.provider.symmetric.AES$CBC",
                            "org.bouncycastle.jcajce.provider.symmetric.AES$ECB",
                            "org.bouncycastle.jcajce.provider.symmetric.AES$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.ARC4$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.ARIA$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Blowfish$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.CAST5$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.CAST6$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Camellia$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.ChaCha$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.DES$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.DESede$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.DSTU7624$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.GOST28147$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.GOST3412_2015$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Grain128$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Grainv1$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.HC128$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.HC256$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.IDEA$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Noekeon$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.OpenSSLPBKDF$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.OpenSSLPBKDF$PBKDF",
                            "org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF1$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF2$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.PBEPBKDF2$PBKDF2withSHA256",
                            "org.bouncycastle.jcajce.provider.symmetric.PBEPKCS12$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Poly1305$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.RC2$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.RC5$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.RC6$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Rijndael$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.SCRYPT$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.SEED$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.SM4$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Salsa20$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Serpent$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Shacal2$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.SipHash$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.SipHash128$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Skipjack$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.TEA$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.TLSKDF$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Threefish$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Twofish$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.VMPC$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.VMPCKSA3$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.XSalsa20$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.XTEA$Mappings",
                            "org.bouncycastle.jcajce.provider.symmetric.Zuc$Mappings"
            };

            for (String name : reflectiveClasses) {
                try {
                    RuntimeReflection.register(Class.forName(name).getConstructor());
                } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                    throw new RuntimeException("Could not register " + name + " constructor for reflective access!", e);
                }
            }
        }
    }
}
