/* MIT License
 *
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "debug_internal.h"

// Implements OS dependent abstraction of memory protection

#ifdef _HPY_DEBUG_MEM_PROTECT_USEMMAP

#ifndef _WIN32

// On UNIX systems we use mmap and mprotect

#include <sys/mman.h>
#include <string.h>

void *raw_data_copy(const void* data, HPy_ssize_t size, bool write_protect) {
    void* new_ptr;
    new_ptr = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (new_ptr == NULL)
        return NULL;
    memcpy(new_ptr, data, size);
    if (write_protect) {
        mprotect(new_ptr, size, PROT_READ);
    }
    return new_ptr;
}

void raw_data_protect(void* data, HPy_ssize_t size) {
    mprotect(data, size, PROT_NONE);
}

int raw_data_free(void *data, HPy_ssize_t size) {
    return munmap(data, size);
}

#else

// On Windows systems we use VirtualAlloc and VirtualProtect

#include <windows.h>
#include <memoryapi.h>

void *raw_data_copy(const void* data, HPy_ssize_t size, bool write_protect) {
    void* new_ptr;
    DWORD old;
    new_ptr = VirtualAlloc(NULL, size, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (new_ptr == NULL)
        return NULL;
    memcpy(new_ptr, data, size);
    if (write_protect) {
        VirtualProtect(new_ptr, size, PAGE_READONLY, &old);
    }
    return new_ptr;
}

void raw_data_protect(void* data, HPy_ssize_t size) {
    DWORD old;
    VirtualProtect(data, size, PAGE_NOACCESS, &old);
}

int raw_data_free(void *data, HPy_ssize_t size) {
    return !VirtualFree(data, 0, MEM_RELEASE);
}

#endif /* _WIN32 */

#else

// Generic fallback that should work for any OS with decent C compiler: copy
// the memory and then override it with garbage to "protect" it from reading.

#include <stdlib.h>
#include <string.h>

void *raw_data_copy(const void* data, HPy_ssize_t size, bool write_protect) {
    void *new_data = malloc(size);
    memcpy(new_data, data, size);
    return new_data;
}

void raw_data_protect(void* data, HPy_ssize_t size) {
    // Override the data with some garbage in hope that the program will
    // eventually crash or give incorrect result if it reads the garbage
    char poison[] = {0xBA, 0xD0, 0xDA, 0x7A};
    for (HPy_ssize_t dataIdx = 0, poisonIdx = 0; dataIdx < size; ++dataIdx) {
        ((char*)data)[dataIdx] = poison[poisonIdx];
        poisonIdx = (poisonIdx + 1) % sizeof(poison);
    }
}

int raw_data_free(void *data, HPy_ssize_t size) {
    free(data);
    return 0;
}

#endif
