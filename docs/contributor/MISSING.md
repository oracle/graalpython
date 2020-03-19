# (Largely) Missing Core Modules

This is just a snapshot as of:
Thu 2020-03-19 -  5:19 PM

### Builtin modules from sys.builtin_module_names that we don't have built-in or care shared object modules

#### These we might want to use from C
 * **_blake2**: hashing extension module, we could use it from C
 * **_sha3**:  We should just run the C module
 * **grp**:  UNIX group file access. We need to use this from the C module.
 * **spwd**:  UNIX shadow password file access. We need to use this from the C module.
 * **syslog**:  Access to syslog. We should probable just use this from the C module.
 * **audioop**:  Should be useable from C
 * **_curses, _curses_panel**:  Use from C
 * **_csv**:  We can use it from C
 * **_gdbm**:  Use from C
 * **_dbm**:  Use from C
 * **nis**:  We should just use the C module
 * **_sqlite3**:  Either use from C, or use a Java API.
 * **termios**:  Posix terminal module IO. Use from C
 * **_tkinter**: Should be used from C

#### These are not strictly needed for now
 * **_abc**:  Just a performance optimization, not necessary.
 * **_bisect**:  Just a performance optimization, not necessary.
 * **_datetime**: Just a performance optimization, not necessary.
 * **_elementtree**: Just a performance optimization, not necessary.
 * **_heapq**: Just a performance optimization, not necessary.
 * **_pickle**:  Just a performance optimization, not necessary.
 * **_stat**:  Just a performance optimization, not necessary.
 * **_warnings**:  Warnings filtering, not strictly needed.
 * **_asyncio**:  We should write this ourselves, but I think the pure Python should be enough for a while
 * **_decimal**:  Just performance, not necessary
 * **_hashlib**:  Just for performance
 * **_json**:  Just for performance
 * **_queue**:  Just for performance, not needed

#### These we actually have, just not in Java
 * **_struct**:  We already use this from the C module.
 * **_bz2**:  We're already using this from C
 * **_md5**:  We use the Python impl from PyPy
 * **_sha1**:  We use the Python impl from PyPy
 * **_sha256**:  We use the Python impl from PyPy
 * **_sha512**:  We use the Python impl from PyPy
 * **mmap**:  We already use this from the C module.

#### These we probably won't support
 * **_symtable**:  Interface for the compilers internal symboltable, we cannot easily support this
 * **_opcode**:  We won't have it
 * **ossaudiodev**:  Not needed, it's for Linux OSS audio

#### These we should re-implement
 * **_lsprof**:  We'll probably just want to replace this with the Truffle profiler
 * **_tracemalloc**:  Memory allocation tracing, we should substitute with the Truffle instrument.
 * **cmath**:  Complex math module. We should implement this in Java, I think.
 * **_codecs_cn, _codecs_hk, _codecs_iso2022, _codecs_jp, _codecs_kr, _codecs_tw, _multibytecodec**:  We can just use our own codecs
 * **_crypt**:  We can just implement this in Java, it's a single function
 * **_ctypes, _ctypes_test**:  We might be able to use these directly, but reimplement would be faster
 * **parser**:  We need to implement this for our parser
 * **_ssl**:  To use this from C, we have to use the socketmodule from C also
 * **_uuid**: Can be implemented ourselves, is just 1 function

### Very incomplete on our part:

 * **_ast**: Used in various places, including the help system. Would be nice to support, ours is an empty shell
 * **_socket**: We map to Java sockets, but not much else.
 * **resource**:  This is about resources, there should be Truffle APIs for this (there are issues open)
 * **_multiprocessing**:  We need to implement this with the Context API
