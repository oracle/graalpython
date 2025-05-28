/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.python.builtins.objects.ssl.LazyBouncyCastleProvider;
import com.oracle.graal.python.runtime.PythonImageBuildOptions;

public class BouncyCastleFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!PythonImageBuildOptions.WITHOUT_SSL) {
            RuntimeClassInitializationSupport support = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);

            if (Runtime.version().feature() >= 25) {
                // In current native image, security providers need to get verified at build time,
                // but then are reinitialized at runtime
                support.initializeAtRunTime("org.bouncycastle", "security provider");
                Security.addProvider(new BouncyCastleProvider());
            } else {
                support.initializeAtBuildTime("org.bouncycastle", "security provider");
                support.initializeAtRunTime("org.bouncycastle.jcajce.provider.drbg.DRBG$Default", "RNG");
                support.initializeAtRunTime("org.bouncycastle.jcajce.provider.drbg.DRBG$NonceAndIV", "RNG");
                LazyBouncyCastleProvider.initProvider();
            }

            // SSLBasicKeyDerivation looks up the classes below reflectively since jdk-25+23
            // See https://github.com/openjdk/jdk/pull/24393
            String[] reflectiveClasses = new String[]{
                            "com.sun.crypto.provider.HKDFKeyDerivation$HKDFSHA256",
                            "com.sun.crypto.provider.HKDFKeyDerivation$HKDFSHA384",
                            "com.sun.crypto.provider.HKDFKeyDerivation$HKDFSHA512",
                            "sun.security.pkcs11.P11HKDF",
            };
            for (String name : reflectiveClasses) {
                try {
                    Class.forName(name);
                } catch (SecurityException | ClassNotFoundException e) {
                    return;
                }
            }
            // For backwards compatibility with older JDKs, we only do this if we found
            // all those classes
            Security.addProvider(Security.getProvider("SunJCE"));
            for (String name : reflectiveClasses) {
                try {
                    RuntimeReflection.register(Class.forName(name));
                    RuntimeReflection.register(Class.forName(name).getConstructors());
                } catch (SecurityException | ClassNotFoundException e) {
                    throw new RuntimeException("Could not register " + name + " for reflective access!", e);
                }
            }
        }
    }
}
