# (Largely) Missing Core Modules

This is just a snapshot as of:
Thu 2020-03-19 -  5:19 PM

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
 * **grp**:  UNIX group file access. We need to use this from the C module.
 * **nis**:  We should just use the C module
 * **spwd**:  UNIX shadow password file access. We need to use this from the C module.
 * **syslog**:  Access to syslog. We should probable just use this from the C module.
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
 * **_json**:  Just for performance
 * **_pickle**:  Just a performance optimization, not necessary.
 * **_queue**:  Just for performance, not needed
 * **_queue**: Just an optimization, we stubbed it out
 * **_stat**:  Just a performance optimization, not necessary.
 * **_warnings**:  Warnings filtering, not strictly needed.

#### These we probably won't support
 * **_opcode**:  We won't have it
 * **_symtable**:  Interface for the compilers internal symboltable, we cannot easily support this
 * **ossaudiodev**:  Not needed, it's for Linux OSS audio

#### These we should re-implement
 * **_codecs_cn, _codecs_hk, _codecs_iso2022, _codecs_jp, _codecs_kr, _codecs_tw, _multibytecodec**:  We can just use our own codecs
 * **_crypt**:  We can just implement this in Java, it's a single function
 * **_ctypes, _ctypes_test**:  We might be able to use these directly, but reimplement would be faster
 * **_lsprof**:  We'll probably just want to replace this with the Truffle profiler
 * **_ssl**:  To use this from C, we have to use the socketmodule from C also
 * **_string**: Empty right now, but its only two methods that we can re-implement
 * **_tracemalloc**:  Memory allocation tracing, we should substitute with the Truffle instrument.
 * **_uuid**: Can be implemented ourselves, is just 1 function
 * **cmath**:  Complex math module. We should implement this in Java, I think.
 * **faulthandler**: Needs to deal with Java stacks
 * **fcntl**: Should use the TruffleFile APIs
 * **parser**:  We need to implement this for our parser
 * **select**: Needs to work with TruffleFile and future Truffle socket abstractions

### Incompleteness on our part:
 * **_ast**: Used in various places, including the help system. Would be nice to support, ours is an empty shell
 * **_contextvars**: Very incomplete
 * **_multiprocessing**:  We need to implement this with the Context API
 * **_signal**: Needs a Truffle API for Signal handling, until then this is the bare minimum
 * **_socket**: We map to Java sockets, but not much else.
 * **array**: This just exposes the array type. Missing some methods and major optimizations.
 * **mmap**:  We use this as a mixture from the C module, Python, and Java code. Needs major optimizations.
 * **posix**: Missing quite a bit of functionality that isn't easy to expose with Truffle API
 * **resource**:  This is about resources, there should be Truffle APIs for this (there are issues open)
 * **thread**: The module is incomplete, and we don't have proper multi-threading in our impl, yet
 * **unicodedata**: A bit incomplete, but not difficult. Maybe should use a Java ICU library

### Basically complete or easy to make so
 * **_bz2**:  We're already using this from C
 * **_collections**
 * **_imp**
 * **_io**: We have built the bare minimum and are using _pyio mostly, which has everything we need
 * **_md5**:  We use the Python impl from PyPy
 * **_posixsubprocess**
 * **_random**
 * **_sha1**:  We use the Python impl from PyPy
 * **_sha256**:  We use the Python impl from PyPy
 * **_sha512**:  We use the Python impl from PyPy
 * **_sre**: We use TRegex with fallback to the CPython module for special features
 * **_struct**:  We already use this from the C module.
 * **_weakref**
 * **atexit**
 * **binascii**: Just missing a methods
 * **builtins**: Missing very few functions
 * **codecs**
 * **errno**
 * **functools**: Missing a few functions, we mostly implemented it in Python
 * **gc**
 * **itertools**: We mostly just implement all this in Python (a lot is taken from PyPy)
 * **locale**: Partially Truffle APIs, should probably use more to play nice for embedders
 * **marshal**
 * **math**
 * **operator**
 * **pwd**
 * **pyexpat**: We've re-implemented this in Java. If too incompatible, we should just switch to the C code.
 * **readline**: We re-implemented this in terms of JLine used in our launcher
 * **sys**
 * **time**: Missing a few methods, but nothing hard
 * **zipimport**: We have reimplemented this, but Python 3.8 is moving to a pure-Python impl that we can use
 * **zlib**
