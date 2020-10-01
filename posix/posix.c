#include <sys/stat.h>
#include <stdint.h>
#include <unistd.h>

int64_t call_getpid() {
  return getpid();
}

int64_t call_umask(int64_t mask) {
  // TODO umask uses mode_t as argument/retval -> what Java type should we map it into? Using long for now.
  return umask(mask);
}

