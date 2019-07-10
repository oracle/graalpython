/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class PZipImporter extends PythonBuiltinObject {
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
     * Cache of the files in the zipfile. Exported in ZipimportModuleBuiltins
     */
    private final PDict moduleZipDirectoryCache;

    /**
     * Is the entry source or package
     */
    private static enum EntryType {
        IS_SOURCE,
        IS_PACKAGE
    }

    /**
     * The separatorChar used in the context for this importer
     */
    private final String separator;

    private static class SearchOrderEntry {

        String suffix;
        EnumSet<EntryType> type;

        SearchOrderEntry(String suffix, EnumSet<EntryType> type) {
            this.suffix = suffix;
            this.type = type;
        }
    }

    protected static class ModuleCodeData {

        String code;
        boolean isPackage;
        String path;

        ModuleCodeData(String code, boolean isPackage, String path) {
            this.code = code;
            this.isPackage = isPackage;
            this.path = path;
        }
    }

    /**
     * Defines how the source and module will be searched in archive.
     */
    private final SearchOrderEntry[] searchOrder;

    /**
     * Module information
     */
    public static enum ModuleInfo {
        ERROR,
        NOT_FOUND,
        MODULE,
        PACKAGE
    }

    public PZipImporter(LazyPythonClass cls, PDict zipDirectoryCache, String separator) {
        super(cls);
        this.archive = null;
        this.prefix = null;
        this.separator = separator;
        this.moduleZipDirectoryCache = zipDirectoryCache;
        this.searchOrder = defineSearchOrder();
    }

    private SearchOrderEntry[] defineSearchOrder() {
        return new SearchOrderEntry[]{
                        new SearchOrderEntry(joinStrings(separator, "__init__.py"),
                                        enumSetOf(EntryType.IS_PACKAGE, EntryType.IS_SOURCE)),
                        new SearchOrderEntry(".py", enumSetOf(EntryType.IS_SOURCE))
        };
    }

    @TruffleBoundary
    private static String joinStrings(String a, String b) {
        return a + b;
    }

    @TruffleBoundary
    private static <E extends Enum<E>> EnumSet<E> enumSetOf(E e1) {
        return EnumSet.of(e1);
    }

    @TruffleBoundary
    private static <E extends Enum<E>> EnumSet<E> enumSetOf(E e1, E e2) {
        return EnumSet.of(e1, e2);
    }

    public PDict getZipDirectoryCache() {
        return moduleZipDirectoryCache;
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

    @TruffleBoundary
    protected String makeFilename(String fullname) {
        return prefix + getSubname(fullname).replace(".", separator);
    }

    protected PTuple getEntry(String filenameAndSuffix) {
        return (PTuple) files.getItem(filenameAndSuffix);
    }

    @TruffleBoundary
    protected String makePackagePath(String fullname) {
        return archive + separator + prefix + getSubname(fullname);
    }

    /**
     *
     * @param filenameAndSuffix
     * @return code
     * @throws IOException
     */
    @CompilerDirectives.TruffleBoundary
    private String getCode(String filenameAndSuffix) throws IOException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(archive);
            ZipEntry entry = zip.getEntry(filenameAndSuffix);
            InputStream in = zip.getInputStream(entry);

            // reading the file should be done better?
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            int size = (int) entry.getSize();
            if (size < 0) {
                size = (int) entry.getCompressedSize();
            }
            StringBuilder code = new StringBuilder(size < 16 ? 16 : size);
            String line;
            while ((line = reader.readLine()) != null) {
                code.append(line);
                code.append(System.lineSeparator());
            }
            reader.close();
            return code.toString();
        } catch (IOException e) {
            throw new IOException("Can not read code from " + makePackagePath(filenameAndSuffix), e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e) {
                    // just ignore it.
                }
            }
        }
    }

    /**
     * Return module information for the module with the fully qualified name.
     *
     * @param fullname the fully qualified name of the module
     * @return the module's information
     */
    @TruffleBoundary
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

    @TruffleBoundary
    protected final ModuleCodeData getModuleCode(String fullname) throws IOException {
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
