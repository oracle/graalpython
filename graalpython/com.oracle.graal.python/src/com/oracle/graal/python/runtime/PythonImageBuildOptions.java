/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

/**
 * This class provides a set of options that can be used to disable Python features. These options
 * are safe to use from {@link org.graalvm.nativeimage.hosted.Feature}.
 */
public final class PythonImageBuildOptions {

    /**
     * Whether Java classes are included that implement the SSL module. These come from packages
     * including (but not limited to): javax.net.ssl, org.bouncycastle, java.security, javax.crypto,
     * sun.security
     */
    public static final boolean WITHOUT_SSL = Boolean.getBoolean("python.WithoutSSL");
    /**
     * Whether cryptographic hashing functions are implemented via java.security.MessageDigest,
     * javax.crypto.Mac and related functions.
     */
    public static final boolean WITHOUT_DIGEST = Boolean.getBoolean("python.WithoutDigest");
    /**
     * Whether Java classes are included that relate to Unix-specific access, modify process
     * properties such as the default timezone, access the platform's Runtime MXBean, or spawn
     * subprocesses are available.
     */
    public static final boolean WITHOUT_PLATFORM_ACCESS = Boolean.getBoolean("python.WithoutPlatformAccess");
    /**
     * This property can be used to exclude zip, zlib, lzma, and bzip2 support from the Python core.
     */
    public static final boolean WITHOUT_COMPRESSION_LIBRARIES = Boolean.getBoolean("python.WithoutCompressionLibraries");
    /**
     * This property can be used to exclude native posix support from the build. Only Java emulation
     * will be available.
     */
    public static final boolean WITHOUT_NATIVE_POSIX = Boolean.getBoolean("python.WithoutNativePosix");
    /**
     * This property can be used to exclude socket and inet support from the Java posix backend.
     */
    public static final boolean WITHOUT_JAVA_INET = Boolean.getBoolean("python.WithoutJavaInet");
    /**
     * This property can be used to disable any usage of JNI.
     */
    public static final boolean WITHOUT_JNI = Boolean.getBoolean("python.WithoutJNI");

    private PythonImageBuildOptions() {
    }
}
