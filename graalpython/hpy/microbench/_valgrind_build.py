from cffi import FFI
ffibuilder = FFI()

ffibuilder.cdef("""
    void callgrind_start(void);
    void callgrind_stop(void);
    int is_running_on_valgrind(void);
""")


ffibuilder.set_source("_valgrind", """
#include <valgrind/callgrind.h>
void callgrind_start(void) {
  CALLGRIND_START_INSTRUMENTATION;
}

void callgrind_stop(void) {
  CALLGRIND_STOP_INSTRUMENTATION;
  CALLGRIND_DUMP_STATS;
}

int is_running_on_valgrind(void) {
  return RUNNING_ON_VALGRIND;
}
""",
    libraries=[])

if __name__ == "__main__":
    ffibuilder.compile(verbose=True)
