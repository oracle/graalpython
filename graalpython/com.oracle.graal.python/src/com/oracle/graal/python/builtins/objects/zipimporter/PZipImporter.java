/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.zipimporter;

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PZipImporter extends PythonBuiltinObject {

    public static String SEPARATOR = File.separator;

    /**
     * pathname of the Zip archive
     */
    private String archive;

    /**
     * file prefix: "a/sub/directory/"
     */
    private String prefix;

    /**
     * dict with file info {path: toc_entry}
     */
    private PDict files;

    /**
     * Is the entry source or package
     */
    private static enum EntryType {
        IS_SOURCE,
        IS_PACKAGE
    };

    private static class SearchOrderEntry {

        public String suffix;
        public EnumSet<EntryType> type;

        public SearchOrderEntry(String suffix, EnumSet<EntryType> type) {
            this.suffix = suffix;
            this.type = type;
        }
    }

    protected static class ModuleCodeData {

        public String code;
        public boolean isPackage;
        public String path;

        public ModuleCodeData(String code, boolean isPackage, String path) {
            this.code = code;
            this.isPackage = isPackage;
            this.path = path;
        }
    }

    /**
     * Defines how the source and module will be searched in archive.
     */
    private static SearchOrderEntry[] searchOrder;

    /**
     * Module information
     */
    public static enum ModuleInfo {
        ERROR,
        NOT_FOUND,
        MODULE,
        PACKAGE
    };

    public PZipImporter(LazyPythonClass cls) {
        super(cls);
        this.archive = null;
        if (searchOrder == null) { // define the order once
            searchOrder = defineSearchOrder();
        }
    }

    private static SearchOrderEntry[] defineSearchOrder() {
        return new SearchOrderEntry[]{
                        new SearchOrderEntry(SEPARATOR + "__init__.py",
                                        EnumSet.of(EntryType.IS_PACKAGE, EntryType.IS_SOURCE)),
                        new SearchOrderEntry(".py", EnumSet.of(EntryType.IS_SOURCE))
        };
    }

    /**
     *
     * @return pathname of the Zip archive
     */
    public String getArchive() {
        return archive;
    }

    /**
     *
     * @return file prefix: "a/sub/directory/"
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     *
     * @return dict with file info {path: toc_entry}
     */
    public PDict getFiles() {
        return files;
    }

    public void setArchive(String archive) {
        this.archive = archive;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setFiles(PDict files) {
        this.files = files;
    }

    protected String getSubname(String fullname) {
        int i = fullname.lastIndexOf(".");
        if (i >= 0) {
            return fullname.substring(i + 1);
        }
        return fullname;
    }

    protected String makeFilename(String fullname) {
        return prefix + getSubname(fullname).replace('.', File.separatorChar);
    }

    protected PTuple getEntry(String filenameAndSuffix) {
        return (PTuple) files.getItem(filenameAndSuffix);
    }

    protected String makePackagePath(String fullname) {
        return archive + SEPARATOR + prefix + getSubname(fullname);
    }

    /**
     * 
     * @param filenameAndSuffix
     * @return code
     */
    @CompilerDirectives.TruffleBoundary
    private String getCode(String filenameAndSuffix) {
        try {
            ZipFile zip = new ZipFile(archive);
            ZipEntry entry = zip.getEntry(filenameAndSuffix);
            InputStream in = zip.getInputStream(entry);

            // reading the file should be done better?
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder code = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                code.append(line);
                code.append(System.lineSeparator());
            }
            reader.close();
            return code.toString();
        } catch (IOException e) {
            throw new RuntimeException("Can not read code from " + makePackagePath(filenameAndSuffix), e);
        }
    }

    /**
     * Return module information for the module with the fully qualified name.
     *
     * @param fullname the fully qualified name of the module
     * @return the module's information
     */
    protected final ModuleInfo getModuleInfo(String fullname) {
        String path = makeFilename(fullname);

        for (SearchOrderEntry entry : searchOrder) {
            PTuple importEntry = getEntry(path + entry.suffix);
            if (importEntry == null) {
                continue;
            }

            if (entry.type.contains(EntryType.IS_PACKAGE)) {
                return ModuleInfo.PACKAGE;
            }
            return ModuleInfo.MODULE;
        }
        return ModuleInfo.NOT_FOUND;
    }

    /**
     *
     * @param fullname
     * @return itself if the module is in this importer, otherwise null
     */
    protected final PZipImporter findModule(String fullname) {
        ModuleInfo moduleInfo = getModuleInfo(fullname);
        if (moduleInfo == ModuleInfo.ERROR || moduleInfo == ModuleInfo.NOT_FOUND) {
            return null;
        }
        return this;
    }

    protected final ModuleCodeData getModuleCode(String fullname) {
        String path = makeFilename(fullname);
        String fullPath = makePackagePath(fullname);

        if (path.length() < 0) {
            return null;
        }

        for (SearchOrderEntry entry : searchOrder) {
            String suffix = entry.suffix;
            String searchPath = path + suffix;
            String fullSearchPath = fullPath + suffix;

            PTuple tocEntry = getEntry(searchPath);
            if (tocEntry == null) {
                continue;
            }

            boolean isPackage = entry.type.contains(EntryType.IS_PACKAGE);

            String code = "";
            code = getCode(searchPath);
            return new ModuleCodeData(code, isPackage, fullSearchPath);
        }
        return null;
    }

}
