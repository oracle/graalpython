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
package org.graalvm.python.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Nested;

public interface GraalPyExtension {
    /**
     * Tells whether to use the community version.
     *
     * @return {@code true} if the community artifacts should be used, default is false
     */
    Property<Boolean> getCommunity();

    /**
     * Deprecated: use {@link #getExternalDirectory()}.
     */
    DirectoryProperty getPythonResourcesDirectory();

    /**
     * Optional external directory supposed to be populated with python resources, namely the
     * virtual environment in the "venv" subdirectory.  Existing files and directories other
     * than the "venv" subdirectory will not be touched by the plugin and can be maintained manually.
     * Mutually exclusive with {@link #getResourceDirectory()}.
     */
    DirectoryProperty getExternalDirectory();

    /**
     * Directory within Java resources that should be used for the virtual filesystem.
     * The virtual environment will be deployed into "venv" subdirectory.
     * Mutually exclusive with {@link #getExternalDirectory()}.
     */
    Property<String> getResourceDirectory();

    /**
     * Experimental property. Allows overriding the default Polyglot and GraalPy version.
     * This is intended only for testing of new unreleased versions. It is recommended
     * to use corresponding versions of GraalPy Gradle plugin and the polyglot runtime.
     */
    @Incubating
    Property<String> getPolyglotVersion();

    /**
     * Determines third party python packages to be installed for graalpy usage.
     */
    SetProperty<String> getPackages();

    /**
     * Determines what parts of graalpy stdlib are supposed to be available for graalpy.
     */
    @Nested
    PythonHomeInfo getPythonHome();

    /**
     * Configures the PythonHome object using provided closure. This provides support for closure
     * based configuration, i.e.: {@code pythonHome { ... }}.
     */
    @SuppressWarnings("unused")
    default void pythonHome(Action<? super PythonHomeInfo> action) {
        action.execute(getPythonHome());
    }
}
