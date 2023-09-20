# (Largely) Missing Core Modules

This is just a snapshot as of 2023-09-14.

### Builtin modules from sys.builtin_module_names that we don't have built-in or care shared object modules

#### These we might want to use from C
 * **_curses, _curses_panel**:  Use from C
 * **_dbm**:  Use from C
 * **_gdbm**:  Use from C
 * **_tkinter**: Should be used from C
 * **syslog**:  Access to syslog. We should probably just use this from the C module.

#### These are not strictly needed for now
 * **_bisect**:  Just a performance optimization, not necessary.
 * **_datetime**: Just a performance optimization, not necessary.
 * **_decimal**:  Just performance, not necessary
 * **_elementtree**: Just a performance optimization, not necessary.
 * **_heapq**: Just a performance optimization, not necessary.
 * **_stat**:  Just a performance optimization, not necessary.

#### These we probably won't support
 * **_opcode**:  We won't have it, our opcodes are different from CPython.
 * **_symtable**:  Interface for the compilers internal symboltable.

#### These we should re-implement
 * **_uuid**: Can be implemented ourselves, is just 1 function
 * **grp**:  UNIX group file access. Should be added to our POSIX APIs
 * **spwd**:  UNIX shadow password file access. Should be added to our POSIX APIs
 * **parser**:  We need to implement this for our parser

#### Deprecated in CPython, won't implement
 * **audioop**:  Scheduled for removal in 3.13
 * **ossaudiodev**:  Scheduled for removal in 3.13
 * **nis**:  Scheduled for removal in 3.13
