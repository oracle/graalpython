# (Largely) Missing Core Modules

This is just a snapshot as of 2021-07-29.

### Builtin modules from sys.builtin_module_names that we don't have built-in or care shared object modules

#### These we might want to use from C
 * **_blake2**: hashing extension module, we could use it from C
 * **_csv**:  We can use it from C
 * **_curses, _curses_panel**:  Use from C
 * **_dbm**:  Use from C
 * **_gdbm**:  Use from C
 * **_sha3**:  We should just run the C module
 * **_sqlite3**:  Either use from C, or use a Java API.
 * **_tkinter**: Should be used from C
 * **audioop**:  Should be useable from C
 * **nis**:  We should just use the C module
 * **syslog**:  Access to syslog. We should probably just use this from the C module.
 * **termios**:  Posix terminal module IO. Use from C

#### These are not strictly needed for now
 * **_abc**:  Just a performance optimization, not necessary.
 * **_asyncio**:  We should write this ourselves, but I think the pure Python should be enough for a while
 * **_bisect**:  Just a performance optimization, not necessary.
 * **_datetime**: Just a performance optimization, not necessary.
 * **_decimal**:  Just performance, not necessary
 * **_elementtree**: Just a performance optimization, not necessary.
 * **_hashlib**:  Just for performance
 * **_heapq**: Just a performance optimization, not necessary.
 * **_stat**:  Just a performance optimization, not necessary.

#### These we probably won't support
 * **_opcode**:  We won't have it
 * **_symtable**:  Interface for the compilers internal symboltable, we cannot easily support this
 * **ossaudiodev**:  Not needed, it's for Linux OSS audio

#### These we should re-implement
 * **_codecs_cn, _codecs_hk, _codecs_iso2022, _codecs_jp, _codecs_kr, _codecs_tw, _multibytecodec**:  We can just use our own codecs
 * **_string**: Empty right now, but its only two methods that we can re-implement
 * **_tracemalloc**:  Memory allocation tracing, we should substitute with the Truffle instrument.
 * **_uuid**: Can be implemented ourselves, is just 1 function
 * **faulthandler**: Needs to deal with Java stacks
 * **fcntl**: Should be added to our POSIX APIs
 * **grp**:  UNIX group file access. Should be added to our POSIX APIs
 * **spwd**:  UNIX shadow password file access. Should be added to our POSIX APIs
 * **parser**:  We need to implement this for our parser

### Incompleteness on our part:
 * **_contextvars**: Work in progress
 * **_signal**: Work in progress
 * **mmap**:  We use this as a mixture from the C module, Python, and Java code. Needs major optimizations.
 * **resource**:  This is about resources, there should be Truffle APIs for this (there are issues open)
 * **unicodedata**: A bit incomplete, but not difficult. Maybe should use a Java ICU library

### Basically complete or easy to make so
 * **_md5**:  We use the Python impl from PyPy, but should intrinsify as Java code for performance
 * **_random**
 * **_sha1**:  We use the Python impl from PyPy, but should intrinsify as Java code for performance
 * **_sha256**:  We use the Python impl from PyPy, but should intrinsify as Java code for performance
 * **_sha512**:  We use the Python impl from PyPy, but should intrinsify as Java code for performance
 * **binascii**: Just missing a few methods
 * **functools**: Missing a few functions, we mostly implemented it in Python, but should intrinsify the module in Java for better performance
 * **itertools**: We mostly just implement all this in Python (a lot is taken from PyPy), but should intrinsify the module in Java for better performance
 * **locale**: Partially Truffle APIs, should probably use more to play nice for embedders
 * **readline**: We re-implemented this in terms of JLine used in our launcher
