/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.python.embedding.utils;

import org.graalvm.polyglot.Context;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @deprecated use {@link org.graalvm.python.embedding.GraalPyResources} instead
 */
@SuppressWarnings("deprecation")
@Deprecated
public final class GraalPyResources {

    private GraalPyResources() {
    }

    /**
     * @deprecated use {@link org.graalvm.python.embedding.GraalPyResources#createContext()} instead
     */
    @Deprecated
    public static Context createContext() {
        return org.graalvm.python.embedding.GraalPyResources.createContext();
    }

    /**
     * @deprecated use {@link org.graalvm.python.embedding.GraalPyResources#contextBuilder()}
     *             instead
     */
    @Deprecated
    public static Context.Builder contextBuilder() {
        return org.graalvm.python.embedding.GraalPyResources.contextBuilder();
    }

    /**
     * @deprecated use
     *             {@link org.graalvm.python.embedding.GraalPyResources#contextBuilder(org.graalvm.python.embedding.VirtualFileSystem)}
     *             instead
     */
    @Deprecated
    public static Context.Builder contextBuilder(VirtualFileSystem vfs) {
        return org.graalvm.python.embedding.GraalPyResources.contextBuilder(vfs.delegate);
    }

    /**
     * @deprecated use {@link org.graalvm.python.embedding.GraalPyResources#contextBuilder(Path)}
     *             instead
     */
    @Deprecated
    public static Context.Builder contextBuilder(Path resourcesDirectory) {
        return org.graalvm.python.embedding.GraalPyResources.contextBuilder(resourcesDirectory);
    }

    /**
     * @deprecated use
     *             {@link org.graalvm.python.embedding.GraalPyResources#getNativeExecutablePath()}
     *             instead
     */
    @Deprecated
    public static Path getNativeExecutablePath() {
        return org.graalvm.python.embedding.GraalPyResources.getNativeExecutablePath();
    }

    /**
     * @deprecated use
     *             {@link org.graalvm.python.embedding.GraalPyResources#extractVirtualFileSystemResources(org.graalvm.python.embedding.VirtualFileSystem, Path)}
     *             instead
     */
    @Deprecated
    public static void extractVirtualFileSystemResources(VirtualFileSystem vfs, Path resourcesDirectory) throws IOException {
        org.graalvm.python.embedding.GraalPyResources.extractVirtualFileSystemResources(vfs.delegate, resourcesDirectory);
    }
}
