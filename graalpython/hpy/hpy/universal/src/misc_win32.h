/************************************************************/
/* Emulate dlopen()&co. from the Windows API */

#define RTLD_LAZY   0
#define RTLD_NOW    0
#define RTLD_GLOBAL 0
#define RTLD_LOCAL  0

static void *dlopen(const char *filename, int flag)
{
    return (void *)LoadLibraryA(filename);
}

static void *dlopenW(const wchar_t *filename)
{
    return (void *)LoadLibraryW(filename);
}

static void *dlsym(void *handle, const char *symbol)
{
    void *address = GetProcAddress((HMODULE)handle, symbol);
#ifndef MS_WIN64
    if (!address) {
        /* If 'symbol' is not found, then try '_symbol@N' for N in
           (0, 4, 8, 12, ..., 124).  Unlike ctypes, we try to do that
           for any symbol, although in theory it should only be done
           for __stdcall functions.
        */
        int i;
        char mangled_name[1 + strlen(symbol) + 1 + 3 + 1];
        for (i = 0; i < 32; i++) {
            sprintf(mangled_name, "_%s@%d", symbol, i * 4);
            address = GetProcAddress((HMODULE)handle, mangled_name);
            if (address)
                break;
        }
    }
#endif
    return address;
}

static int dlclose(void *handle)
{
    return FreeLibrary((HMODULE)handle) ? 0 : -1;
}

static const char *dlerror(void)
{
    static char buf[32];
    DWORD dw = GetLastError();
    if (dw == 0)
        return NULL;
    sprintf(buf, "error 0x%x", (unsigned int)dw);
    return buf;
}
