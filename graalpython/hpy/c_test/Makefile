CC ?= gcc
INCLUDE=-I.. -I../hpy/devel/include -I../hpy/debug/src/include
CFLAGS = -O0 -UNDEBUG -g -Wall -Werror -Wfatal-errors $(INCLUDE) -DHPY_ABI_UNIVERSAL

test: test_debug_handles test_stacktrace
	./test_debug_handles
	./test_stacktrace

test_debug_handles: test_debug_handles.o ../hpy/debug/src/dhqueue.o
	$(CC) -o $@ $^

test_stacktrace: test_stacktrace.o ../hpy/debug/src/stacktrace.o
	$(CC) -o $@ $^

%.o : %.c
	$(CC) -c $(CFLAGS) $< -o $@
