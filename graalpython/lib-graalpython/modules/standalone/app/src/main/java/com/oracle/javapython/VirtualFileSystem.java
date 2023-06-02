/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.javapython;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.graalvm.polyglot.io.FileSystem;

public final class VirtualFileSystem implements FileSystem {
        static final record Entry(Path path, boolean isFile, Object data) {};

        static final String VFS_PREFIX = "/vfs";
        private static final TreeMap<Path, Entry> VFS_ENTRIES = new TreeMap<>();

        private static final String FILES_LIST_PATH = "/vfs/fileslist.txt";
        private static Set<String> filesList;
        private static Set<String> dirsList;

        private final FileSystem delegate = FileSystem.newDefaultFileSystem();

        private static void putVFSEntry(Entry e) throws IOException {
            VFS_ENTRIES.put(toRealPathStatic(toAbsolutePathStatic(e.path)), e);
        }
        
        private static Set<String> getFilesList() throws IOException {
            if(filesList == null) {
                initFilesAndDirsList();
            }
            return filesList;
        }        
        
        private static Set<String> getDirsList() throws IOException {
            if(dirsList == null) {
                initFilesAndDirsList();
            }
            return dirsList;
        }        

        private static void initFilesAndDirsList() throws IOException {
            filesList = new HashSet<>();
            dirsList = new HashSet<>();
            try(InputStream stream = VirtualFileSystem.class.getResourceAsStream(FILES_LIST_PATH)) {
                if (stream == null) {
                    return;
                }                
                BufferedReader br = new BufferedReader(new InputStreamReader(stream));
                String line;
                while((line = br.readLine()) != null) {
                    if(line.endsWith("/")) {                        
                        line = line.substring(0, line.length() - 1);
                        dirsList.add(line);
                    } else {
                        filesList.add(line);                            
                    }                         
                }                    
            }    
        }
        
        private static Entry readDirEntry(String parentDir) throws IOException {
            List<String> l = new ArrayList<>();
            
            // find all files in parent dir
            for (String file : getFilesList()) {
                if(isParent(parentDir, file)) {
                    l.add(file);
                }
            }

            // find all dirs in parent dir
            for (String file : getDirsList()) {
                if(isParent(parentDir, file)) {
                    l.add(file);
                }
            }
            
            Path[] paths = new Path[l.size()];
            for (int i = 0; i < paths.length; i++) {
                paths[i] = Paths.get(l.get(i));
            }
            return new Entry(Paths.get(parentDir), false, paths);
        }

        private static boolean isParent(String parentDir, String file) {
            return file.length() > parentDir.length() && file.startsWith(parentDir) && 
                   file.indexOf("/", parentDir.length() + 1) < 0;
        }
        
        private static Entry readFileEntry(String file) throws IOException {
            return new Entry(Paths.get(file), true, readResource(file));
        }

        static byte[] readResource(String path) throws IOException {
            try (InputStream stream = VirtualFileSystem.class.getResourceAsStream(path)) {
                if (stream == null) {
                    return null;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int n;
                byte[] data = new byte[4096];

                while ((n = stream.readNBytes(data, 0, data.length)) != 0) {
                    buffer.write(data, 0, n);
                }
                buffer.flush();
                return buffer.toByteArray();
            }
        }

        private Entry file(Path path) throws IOException {
            Entry e = VFS_ENTRIES.get(toRealPath(toAbsolutePath(path)));
            if(e == null) {
                String pathString = path.toString();
                if(pathString.endsWith("/")) {
                    pathString = pathString.substring(0, pathString.length() - 1);
                }
                
                URL uri = VirtualFileSystem.class.getResource(pathString);
                if(uri != null) {
                    if(getDirsList().contains(pathString)) {
                        e = readDirEntry(pathString);
                    } else {
                        e = readFileEntry(pathString);
                        getFilesList().remove(pathString);
                    }
                    putVFSEntry(e);
                }
            }
            return e;
        }

        @Override
        public Path parsePath(URI uri) {
            if (uri.getScheme().equals("file")) {
                return Paths.get(uri);
            } else {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        @Override
        public Path parsePath(String path) {
            return Paths.get(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            if (path.normalize().startsWith(VFS_PREFIX)) {
                if (modes.contains(AccessMode.WRITE)) {
                    throw new IOException("read-only filesystem");
                }
                if (file(path) == null) {
                    throw new IOException("no such file or directory");
                }
            } else {
                delegate.checkAccess(path, modes, linkOptions);
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            if (dir.normalize().startsWith(VFS_PREFIX)) {
                throw new SecurityException("read-only filesystem");
            } else {
                delegate.createDirectory(dir, attrs);
            }
        }

        @Override
        public void delete(Path path) throws IOException {
            if (path.normalize().startsWith(VFS_PREFIX)) {
                throw new SecurityException("read-only filesystem");
            } else {
                delegate.delete(path);
            }
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            if (!path.normalize().startsWith(VFS_PREFIX)) {
                return delegate.newByteChannel(path, options, attrs);                
            } 
            
            if (options.isEmpty() || (options.size() == 1 && options.contains(StandardOpenOption.READ))) {
                final Entry e = file(path);
                if (e == null) {
                    throw new IOException("no such file");
                }
                if (!e.isFile) {
                    throw new IOException("is a directory");
                }
                return new SeekableByteChannel() {
                    int position = 0;

                    byte[] bytes = (byte[]) e.data;

                    @Override
                    public int read(ByteBuffer dst) throws IOException {
                        if (position > bytes.length) {
                            return -1;
                        } else if (position == bytes.length) {
                            return 0;
                        } else {
                            int length = Math.min(bytes.length - position, dst.remaining());
                            dst.put(bytes, position, length);
                            position += length;
                            if (dst.hasRemaining()) {
                                position++;
                            }
                            return length;
                        }
                    }

                    @Override
                    public int write(ByteBuffer src) throws IOException {
                        throw new IOException("read-only");
                    }

                    @Override
                    public long position() throws IOException {
                        return position;
                    }

                    @Override
                    public SeekableByteChannel position(long newPosition) throws IOException {
                        newPosition = Math.max(0, newPosition);
                        return this;
                    }

                    @Override
                    public long size() throws IOException {
                        return bytes.length;
                    }

                    @Override
                    public SeekableByteChannel truncate(long size) throws IOException {
                        throw new IOException("read-only");
                    }

                    @Override
                    public boolean isOpen() {
                        return true;
                    }

                    @Override
                    public void close() throws IOException {
                    }
                };
            } else {
                throw new SecurityException("read-only filesystem");
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            if (!dir.normalize().startsWith(VFS_PREFIX)) {
                return delegate.newDirectoryStream(dir, filter);
            } 
            Entry e = file(dir);
            if (e == null) {
                throw new IOException("no such file or directory");
            }
            if (e.isFile) {
                // a file, not a directory
                throw new NotDirectoryException(dir.toString());
            }
            return new DirectoryStream<>() {
                @Override
                public void close() throws IOException {
                    // nothing to do
                }

                @Override
                public Iterator<Path> iterator() {
                    return Arrays.asList((Path[])e.data).iterator();
                }
            };
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return toAbsolutePathStatic(path);
        }
        
        private static Path toAbsolutePathStatic(Path path) {
            if (path.startsWith("/")) {
                return path;
            } else {
                return Paths.get("/", path.toString());
            }
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return toRealPathStatic(path, linkOptions);
        }
        
        private static Path toRealPathStatic(Path path, LinkOption... linkOptions) throws IOException {
            return path.normalize();
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            if (!path.normalize().startsWith(VFS_PREFIX)) {
                return delegate.readAttributes(path, attributes, options);
            }
            Entry e = file(path);
            if (e == null) {
                throw new IOException("no such file " + path);
            }
            HashMap<String, Object> attrs = new HashMap<>();
            if (attributes.startsWith("unix:") || attributes.startsWith("posix:") ) {
                throw new UnsupportedOperationException();
            }

            attrs.put("creationTime", FileTime.fromMillis(0));
            attrs.put("lastModifiedTime", FileTime.fromMillis(0));
            attrs.put("lastAccessTime", FileTime.fromMillis(0));
            attrs.put("isRegularFile", e.isFile);
            attrs.put("isDirectory", !e.isFile);
            attrs.put("isSymbolicLink", false);
            attrs.put("isOther", false);
            attrs.put("size", (long) (e.isFile ? ((byte[])e.data).length : 0));
            attrs.put("mode", 555);
            attrs.put("dev", 0L);
            attrs.put("nlink", 1);
            attrs.put("uid", 0);
            attrs.put("gid", 0);
            attrs.put("ctime", FileTime.fromMillis(0));
            return attrs;
        }

//        private static void p(Object... ss) {
//            StringBuilder sb = new StringBuilder();            
//            for (Object s : ss) {
//                sb.append(s);
//            }
//            System.out.println(sb.toString());
//        }
    }
