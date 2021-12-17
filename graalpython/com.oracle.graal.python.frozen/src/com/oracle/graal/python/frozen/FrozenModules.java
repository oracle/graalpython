/* Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.frozen;

import java.util.HashMap;
import java.util.Map;


public final class FrozenModules {
    public static Map<String, PythonFrozenModule> frozenModules = createFrozenModulesMap();

    private static Map<String, PythonFrozenModule> createFrozenModulesMap() {
        Map<String, PythonFrozenModule> frozenModules = new HashMap<String, PythonFrozenModule>();
		frozenModules.put("importlib._bootstrap", new PythonFrozenModule("ImportlibBootstrap", FrozenImportlibBootstrap.importlibBootstrapByteCode, FrozenImportlibBootstrap.importlibBootstrapByteCodeSize));
		frozenModules.put("importlib._bootstrap_external", new PythonFrozenModule("ImportlibBootstrapExternal", FrozenImportlibBootstrapExternal.importlibBootstrapExternalByteCode, FrozenImportlibBootstrapExternal.importlibBootstrapExternalByteCodeSize));
		frozenModules.put("zipimport", new PythonFrozenModule("Zipimport", FrozenZipimport.zipimportByteCode, FrozenZipimport.zipimportByteCodeSize));
		frozenModules.put("abc", new PythonFrozenModule("Abc", FrozenAbc.abcByteCode, FrozenAbc.abcByteCodeSize));
		frozenModules.put("codecs", new PythonFrozenModule("Codecs", FrozenCodecs.codecsByteCode, FrozenCodecs.codecsByteCodeSize));
		frozenModules.put("io", new PythonFrozenModule("Io", FrozenIo.ioByteCode, FrozenIo.ioByteCodeSize));
		frozenModules.put("_collections_abc", new PythonFrozenModule("CollectionsAbc", FrozenCollectionsAbc.collectionsAbcByteCode, FrozenCollectionsAbc.collectionsAbcByteCodeSize));
		frozenModules.put("_sitebuiltins", new PythonFrozenModule("Sitebuiltins", FrozenSitebuiltins.sitebuiltinsByteCode, FrozenSitebuiltins.sitebuiltinsByteCodeSize));
		frozenModules.put("genericpath", new PythonFrozenModule("Genericpath", FrozenGenericpath.genericpathByteCode, FrozenGenericpath.genericpathByteCodeSize));
		frozenModules.put("ntpath", new PythonFrozenModule("Ntpath", FrozenNtpath.ntpathByteCode, FrozenNtpath.ntpathByteCodeSize));
		frozenModules.put("posixpath", new PythonFrozenModule("Posixpath", FrozenPosixpath.posixpathByteCode, FrozenPosixpath.posixpathByteCodeSize));
		frozenModules.put("os", new PythonFrozenModule("Os", FrozenOs.osByteCode, FrozenOs.osByteCodeSize));
		frozenModules.put("site", new PythonFrozenModule("Site", FrozenSite.siteByteCode, FrozenSite.siteByteCodeSize));
		frozenModules.put("stat", new PythonFrozenModule("Stat", FrozenStat.statByteCode, FrozenStat.statByteCodeSize));
    return frozenModules;
    }
}