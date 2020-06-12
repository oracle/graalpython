/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = ZipImportModuleBuiltins.ZIPIMPORT_MODULE_NAME)
public class ZipImportModuleBuiltins extends PythonBuiltins {

    protected static final String ZIPIMPORT_MODULE_NAME = "zipimport";

    private static final String ZIP_DIRECTORY_CACHE_NAME = "_zip_directory_cache";

    private static final String ZIPIMPORTER_DOC = "zipimporter(archivepath) -> zipimporter object\n" +
                    "\n" +
                    "Create a new zipimporter instance. 'archivepath' must be a path to\n" +
                    "a zipfile. ZipImportError is raised if 'archivepath' doesn't point to\n" +
                    "a valid Zip archive.";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZipImportModuleBuiltinsFactory.getFactories();
    }

    public ZipImportModuleBuiltins() {
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(ZIP_DIRECTORY_CACHE_NAME, core.factory().createDict());

    }

    @Builtin(name = "zipimporter", constructsClass = PythonBuiltinClassType.PZipImporter, minNumOfPositionalArgs = 2, doc = ZIPIMPORTER_DOC)
    @GenerateNodeFactory
    public abstract static class ZipImporterNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PZipImporter createNew(Object cls, @SuppressWarnings("unused") Object path,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            PythonModule module = getCore().lookupBuiltinModule(ZIPIMPORT_MODULE_NAME);
            return factory().createZipImporter(cls, (PDict) readNode.execute(module, ZIP_DIRECTORY_CACHE_NAME), getContext().getEnv().getFileNameSeparator());
        }

    }

}
