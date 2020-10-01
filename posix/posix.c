#include <fcntl.h>
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

int32_t call_open(const char *pathname, int32_t flags) {
    return open(pathname, flags);
}

int32_t call_close(int32_t fd) {
    return close(fd);
}

int64_t call_read(int32_t fd, void *buf, uint64_t count) {
    return read(fd, buf, count);
}

