/* Copyright (c) 2023, 2026, Oracle and/or its affiliates.
 * Copyright (C) 1996-2023 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#define __STDC_WANT_LIB_EXT1__ 1

#include <windows.h>
#include <pathcch.h>
#include <shellapi.h>
#include <stringapiset.h>
#include <stdio.h>
#include <stdbool.h>
#include <stdint.h>
#include <share.h>
#include <string.h>

#pragma comment(lib, "Pathcch.lib")
#pragma comment(lib, "Shell32.lib")

#define MAXLEN PATHCCH_MAX_CCH
#define MSGSIZE 1024

#define RC_NO_STD_HANDLES   100
#define RC_CREATE_PROCESS   101

static FILE * log_fp = NULL;

void
debug(wchar_t * format, ...)
{
    va_list va;

    if (log_fp != NULL) {
        wchar_t buffer[MAXLEN];
        int r = 0;
        va_start(va, format);
        r = vswprintf_s(buffer, MAXLEN, format, va);
        va_end(va);

        if (r <= 0) {
            return;
        }
        fputws(buffer, log_fp);
        while (r && isspace(buffer[r])) {
            buffer[r--] = L'\0';
        }
        if (buffer[0]) {
            OutputDebugStringW(buffer);
        }
    }
}

void
formatWinerror(int rc, wchar_t * message, int size)
{
    FormatMessageW(
        FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
        NULL, rc, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        message, size, NULL);
}

void
winerror(int err, wchar_t * format, ... )
{
    va_list va;
    wchar_t message[MSGSIZE];
    wchar_t win_message[MSGSIZE];
    int len;

    if (err == 0) {
        err = GetLastError();
    }

    va_start(va, format);
    len = _vsnwprintf_s(message, MSGSIZE, _TRUNCATE, format, va);
    va_end(va);

    formatWinerror(err, win_message, MSGSIZE);
    if (len >= 0) {
        _snwprintf_s(&message[len], MSGSIZE - len, _TRUNCATE, L": %s",
                     win_message);
    }

    fwprintf(stderr, L"%s\n", message);
}

void
error(wchar_t * format, ... )
{
    va_list va;
    wchar_t message[MSGSIZE];

    va_start(va, format);
    _vsnwprintf_s(message, MSGSIZE, _TRUNCATE, format, va);
    va_end(va);

    fwprintf(stderr, L"%s\n", message);
}

bool
isEnvVarSet(const wchar_t *name)
{
    /* only looking for non-empty, which means at least one character
       and the null terminator */
    return GetEnvironmentVariableW(name, NULL, 0) >= 2;
}

/******************************************************************************\
 ***                          COMMAND-LINE PARSING                          ***
\******************************************************************************/

BOOL
_safeDuplicateHandle(HANDLE in, HANDLE * pout, const wchar_t *nameForError)
{
    BOOL ok;
    HANDLE process = GetCurrentProcess();
    DWORD rc;

    *pout = NULL;
    ok = DuplicateHandle(process, in, process, pout, 0, TRUE,
                         DUPLICATE_SAME_ACCESS);
    if (!ok) {
        rc = GetLastError();
        if (rc == ERROR_INVALID_HANDLE) {
            debug(L"DuplicateHandle returned ERROR_INVALID_HANDLE\n");
            ok = TRUE;
        }
        else {
            winerror(0, L"Failed to duplicate %s handle", nameForError);
        }
    }
    return ok;
}

BOOL WINAPI
ctrl_c_handler(DWORD code)
{
    return TRUE;    /* We just ignore all control events. */
}


int
launchEnvironment(wchar_t *env, wchar_t *exe)
{
    HANDLE job;
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION info;
    DWORD rc;
    BOOL ok;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;

    debug(L"\n# about to run: %s\n", exe);
    job = CreateJobObject(NULL, NULL);
    ok = QueryInformationJobObject(job, JobObjectExtendedLimitInformation, &info, sizeof(info), &rc);
    if (!ok || (rc != sizeof(info)) || !job) {
        winerror(0, L"Failed to query job information");
        return RC_CREATE_PROCESS;
    }
    info.BasicLimitInformation.LimitFlags |= JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE |
                                             JOB_OBJECT_LIMIT_SILENT_BREAKAWAY_OK;
    ok = SetInformationJobObject(job, JobObjectExtendedLimitInformation, &info, sizeof(info));
    if (!ok) {
        winerror(0, L"Failed to update job information");
        return RC_CREATE_PROCESS;
    }
    memset(&si, 0, sizeof(si));
    GetStartupInfoW(&si);
    if (!_safeDuplicateHandle(GetStdHandle(STD_INPUT_HANDLE), &si.hStdInput, L"stdin") ||
        !_safeDuplicateHandle(GetStdHandle(STD_OUTPUT_HANDLE), &si.hStdOutput, L"stdout") ||
        !_safeDuplicateHandle(GetStdHandle(STD_ERROR_HANDLE), &si.hStdError, L"stderr")) {
        return RC_NO_STD_HANDLES;
    }

    ok = SetConsoleCtrlHandler(ctrl_c_handler, TRUE);
    if (!ok) {
        winerror(0, L"Failed to update Control-C handler");
        return RC_NO_STD_HANDLES;
    }

    si.dwFlags = STARTF_USESTDHANDLES;
    ok = CreateProcessW(NULL, exe, NULL, NULL, TRUE, CREATE_UNICODE_ENVIRONMENT, env, NULL, &si, &pi);
    if (!ok) {
        winerror(0, L"Unable to create process using '%s'", exe);
        return RC_CREATE_PROCESS;
    }
    AssignProcessToJobObject(job, pi.hProcess);
    CloseHandle(pi.hThread);
    WaitForSingleObjectEx(pi.hProcess, INFINITE, FALSE);
    ok = GetExitCodeProcess(pi.hProcess, &rc);
    if (!ok) {
        winerror(0, L"Failed to get exit code of process");
        return RC_CREATE_PROCESS;
    }
    debug(L"child process exit code: %d\n", rc);
    return rc;
}


/******************************************************************************\
 ***                           PROCESS CONTROLLER                           ***
\******************************************************************************/

#define GRAAL_PYTHON_ARGS L"GRAAL_PYTHON_ARGS="
#define GRAAL_PYTHON_EXE_ARG L"--python.Executable="
#define GRAAL_PYTHON_BASE_EXECUTABLE_ARG L"--python.BaseExecutable="
#define GRAAL_PYTHON_VENVLAUNCHER_COMMAND_ARG L"--python.VenvlauncherCommand="
#define GRAAL_PYTHON_COMMAND_CFG "venvlauncher_command = "
#define GRAAL_PYTHON_BASE_EXECUTABLE_CFG "base-executable = "
#define PYVENV_CFG L"pyvenv.cfg"

static int
copyUtf8ToWideChar(const char *in, int inLen, wchar_t *out, size_t outLen)
{
    if (outLen == 0) {
        return 0;
    }
    int written = MultiByteToWideChar(CP_UTF8, 0, in, inLen, out, (int)outLen - 1);
    if (written <= 0) {
        return 0;
    }
    out[written] = L'\0';
    return written;
}

static int
readPyVenvCfgValue(FILE *pyvenvCfgFile, const char *key, wchar_t *out, size_t outLen)
{
    char line[MAXLEN];
    size_t keyLen = strlen(key);

    rewind(pyvenvCfgFile);
    while (fgets(line, sizeof(line), pyvenvCfgFile) != NULL) {
        if (strncmp(line, key, keyLen) == 0) {
            size_t valueLen = strcspn(line + keyLen, "\r\n");
            return copyUtf8ToWideChar(line + keyLen, (int)valueLen, out, outLen);
        }
    }
    return 0;
}

static int
extractBaseExecutable(const wchar_t *command, wchar_t *out, size_t outLen)
{
    int cmdArgc = 0;
    wchar_t **cmdArgv = CommandLineToArgvW(command, &cmdArgc);
    if (cmdArgv == NULL || cmdArgc < 1) {
        if (cmdArgv != NULL) {
            LocalFree(cmdArgv);
        }
        return -1;
    }
    int rc = wcscpy_s(out, outLen, cmdArgv[0]);
    LocalFree(cmdArgv);
    return rc;
}

int
wmain(int argc, wchar_t ** argv)
{
    FILE * currentExeFile = NULL;
    FILE * pyvenvCfgFile = NULL;
    uint32_t newExecutableSize = 0;
    wchar_t * env = NULL;
    wchar_t * envCur = NULL;
    wchar_t * newEnv = NULL;
    wchar_t * newEnvCur = NULL;
    int envSize = 0;
    int exitCode = 0;

    wchar_t newExecutable[MAXLEN];
    wchar_t * newExeStart = NULL;
    memset(newExecutable, 0, sizeof(newExecutable));

    wchar_t baseExecutable[MAXLEN];
    memset(baseExecutable, 0, sizeof(baseExecutable));

    wchar_t currentExecutable[MAXLEN];
    int currentExecutableSize = sizeof(currentExecutable) / sizeof(currentExecutable[0]);
    memset(currentExecutable, 0, sizeof(currentExecutable));

    wchar_t pyvenvCfg[MAXLEN];
    memset(pyvenvCfg, 0, sizeof(pyvenvCfg));

    if (isEnvVarSet(L"PYLAUNCHER_DEBUG")) {
        setvbuf(stderr, (char *)NULL, _IONBF, 0);
        log_fp = stderr;
    }
    for (int i = 0; i < argc; ++i) {
        debug(L"argv%d: %s\n", i, argv[i]);
    }

    exitCode = GetModuleFileNameW(NULL, currentExecutable, currentExecutableSize);
    if (exitCode == 0 || exitCode == currentExecutableSize) {
        winerror(0, L"Failed to get current executable name");
        goto abort;
    }
    debug(L"exe: %s\n", currentExecutable);

    // check if there's a pyvenv.cfg with a command
    memcpy(pyvenvCfg, currentExecutable, MAXLEN);
    PathCchRemoveFileSpec(pyvenvCfg, MAXLEN);
    PathCchAppend(pyvenvCfg, MAXLEN, PYVENV_CFG);
    pyvenvCfgFile = _wfsopen(pyvenvCfg, L"r", _SH_DENYNO);
    if (!pyvenvCfgFile) {
        debug(L"no pyvenv.cfg at %s\n", pyvenvCfg);
        PathCchRemoveFileSpec(pyvenvCfg, MAXLEN);
        PathCchRemoveFileSpec(pyvenvCfg, MAXLEN);
        PathCchAppend(pyvenvCfg, MAXLEN, PYVENV_CFG);
        pyvenvCfgFile = _wfsopen(pyvenvCfg, L"r", _SH_DENYNO);
    }
    if (pyvenvCfgFile) {
        debug(L"pyvenv.cfg at %s\n", pyvenvCfg);
        newExecutableSize = readPyVenvCfgValue(pyvenvCfgFile, GRAAL_PYTHON_COMMAND_CFG, newExecutable + 1, MAXLEN - 1) * sizeof(newExecutable[0]);
        if (newExecutableSize) {
            debug(L"new executable from pyvenv.cfg: %s\n", newExecutable + 1);
        }
        if (readPyVenvCfgValue(pyvenvCfgFile, GRAAL_PYTHON_BASE_EXECUTABLE_CFG, baseExecutable, MAXLEN)) {
            debug(L"base executable from pyvenv.cfg: %s\n", baseExecutable);
        }
    } else {
        debug(L"no pyvenv.cfg at %s\n", pyvenvCfg);
    }

    if (!newExecutableSize) {
        // read path to executable that created the venv from the end of the launcher
        currentExeFile = _wfsopen(currentExecutable, L"rb", _SH_DENYNO);
        if (!currentExeFile) {
            winerror(0, L"Failed to open current executable for reading");
            goto abort;
        }
        exitCode = fseek(currentExeFile, -sizeof(newExecutableSize), SEEK_END);
        if (exitCode) {
            error(L"Failed to seek to end of current executable");
            goto abort;
        }
        exitCode = fread_s(&newExecutableSize, sizeof(newExecutableSize), sizeof(newExecutableSize), 1, currentExeFile);
        if (exitCode != 1) {
            error(L"Failed to read size of original executable from end of current executable, tried to read %d, but only got %d", sizeof(newExecutableSize), exitCode);
            goto abort;
        }
        exitCode = fseek(currentExeFile, -newExecutableSize - sizeof(newExecutableSize), SEEK_END);
        if (exitCode) {
            error(L"Failed to seek to beginning of original executable string from end of current executable");
            goto abort;
        }
        exitCode = fread_s(newExecutable + 1, sizeof(newExecutable) - 2, 1, newExecutableSize, currentExeFile);
        if (exitCode != newExecutableSize) {
            error(L"Failed to read original executable of length %d from current executable, got %d", newExecutableSize, exitCode);
            goto abort;
        }
    }

    if (wcschr(newExecutable + 1, L'"')) {
        // quotes are not allowed in paths, so this is a complete commandline
        debug(L"new exe has quotes, treating it as commandline\n");
        newExeStart = newExecutable + 1;
    } else {
        newExecutable[0] = L'"';
        newExecutable[newExecutableSize / sizeof(newExecutable[0]) + 1] = L'"';
        newExeStart = newExecutable;
    }
    debug(L"new exe: %s\n", newExeStart);

    if (!baseExecutable[0]) {
        exitCode = extractBaseExecutable(newExeStart, baseExecutable, MAXLEN);
        if (exitCode) {
            debug(L"Failed to extract base executable from launcher command, using current executable instead\n");
            exitCode = wcscpy_s(baseExecutable, MAXLEN, currentExecutable);
            if (exitCode) {
                winerror(exitCode, L"Failed to copy current executable into base executable");
                goto abort;
            }
        }
    }
    debug(L"base executable: %s\n", baseExecutable);

    // calculate the size of the new environment, that is, the size of the previous environment
    // plus the size of the GRAAL_PYTHON_ARGS variable with the arguments to pass on
    env = GetEnvironmentStringsW();
    if (env == NULL) {
        winerror(0, L"Failed to get current environment");
        goto abort;
    }
    envCur = env;
    for (int i = 0; i = wcslen(envCur); i) {
        // env needs room for key=value and \0
        envSize = envSize + i + 1;
        envCur = envCur + i + 1;
    }
    // env needs room for key=, no \0
    envSize += wcslen(GRAAL_PYTHON_ARGS);
    // need room to specify original launcher path
    envSize += 1 + wcslen(GRAAL_PYTHON_EXE_ARG) + wcslen(currentExecutable);
    // need room to specify base executable path
    envSize += 1 + wcslen(GRAAL_PYTHON_BASE_EXECUTABLE_ARG) + wcslen(baseExecutable);
    // need room to specify original launcher command
    envSize += 1 + wcslen(GRAAL_PYTHON_VENVLAUNCHER_COMMAND_ARG) + wcslen(newExeStart);
    for (int i = 1; i < argc; ++i) {
        // env needs room for \v and arg, no \0
        envSize = envSize + 1 + wcslen(argv[i]);
    }
    // final \v at the end of GRAAL_PYTHON_ARGS, so it gets purged
    ++envSize;
    // \0 at the end of the GRAAL_PYTHON_ARGS variable
    ++envSize;
    // env needs room for \0 at the end
    ++envSize;

    // populate the new environment
    newEnv = calloc(envSize, sizeof(env[0]));
    if (newEnv == NULL) {
        winerror(0, L"Failed to allocate new environment");
        goto abort;
    }
    envCur = env;
    newEnvCur = newEnv;
    for (int i = 0; i = wcslen(envCur); i) {
        exitCode = wcscpy_s(newEnvCur, envSize, envCur);
        if (exitCode) {
            winerror(exitCode, L"Failed to copy envvar");
            goto abort;
        }
        debug(L"\t%s\n", newEnvCur);
        // move past key=value and \0
        newEnvCur = newEnvCur + i + 1;
        envCur = envCur + i + 1;
        envSize = envSize - i - 1;
    }
    exitCode = wcscpy_s(newEnvCur, envSize, GRAAL_PYTHON_ARGS);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy GRAAL_PYTHON_ARGS=");
        goto abort;
    }
    debug(L"\t%s", newEnvCur);
    // move past key=
    envSize = envSize - wcslen(GRAAL_PYTHON_ARGS);
    newEnvCur = newEnvCur + wcslen(GRAAL_PYTHON_ARGS);
    // specify launcher executable
    newEnvCur[0] = L'\v';
    --envSize;
    ++newEnvCur;
    exitCode = wcscpy_s(newEnvCur, envSize, GRAAL_PYTHON_EXE_ARG);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy %s", GRAAL_PYTHON_EXE_ARG);
        goto abort;
    }
    debug(L"%s", newEnvCur);
    envSize = envSize - wcslen(GRAAL_PYTHON_EXE_ARG);
    newEnvCur = newEnvCur + wcslen(GRAAL_PYTHON_EXE_ARG);
    exitCode = wcscpy_s(newEnvCur, envSize, currentExecutable);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy %s into env", currentExecutable);
        goto abort;
    }
    debug(L"%s", newEnvCur);
    envSize = envSize - wcslen(currentExecutable);
    newEnvCur = newEnvCur + wcslen(currentExecutable);
    // specify base executable
    newEnvCur[0] = L'\v';
    --envSize;
    ++newEnvCur;
    exitCode = wcscpy_s(newEnvCur, envSize, GRAAL_PYTHON_BASE_EXECUTABLE_ARG);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy %s", GRAAL_PYTHON_BASE_EXECUTABLE_ARG);
        goto abort;
    }
    debug(L"%s", newEnvCur);
    envSize = envSize - wcslen(GRAAL_PYTHON_BASE_EXECUTABLE_ARG);
    newEnvCur = newEnvCur + wcslen(GRAAL_PYTHON_BASE_EXECUTABLE_ARG);
    exitCode = wcscpy_s(newEnvCur, envSize, baseExecutable);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy %s into env", baseExecutable);
        goto abort;
    }
    debug(L"%s", newEnvCur);
    envSize = envSize - wcslen(baseExecutable);
    newEnvCur = newEnvCur + wcslen(baseExecutable);
    // specify original launcher command
    newEnvCur[0] = L'\v';
    --envSize;
    ++newEnvCur;
    exitCode = wcscpy_s(newEnvCur, envSize, GRAAL_PYTHON_VENVLAUNCHER_COMMAND_ARG);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy %s", GRAAL_PYTHON_VENVLAUNCHER_COMMAND_ARG);
        goto abort;
    }
    debug(L"%s", newEnvCur);
    envSize = envSize - wcslen(GRAAL_PYTHON_VENVLAUNCHER_COMMAND_ARG);
    newEnvCur = newEnvCur + wcslen(GRAAL_PYTHON_VENVLAUNCHER_COMMAND_ARG);
    exitCode = wcscpy_s(newEnvCur, envSize, newExeStart);
    if (exitCode) {
        winerror(exitCode, L"Failed to copy %s into env", newExeStart);
        goto abort;
    }
    debug(L"%s", newEnvCur);
    envSize = envSize - wcslen(newExeStart);
    newEnvCur = newEnvCur + wcslen(newExeStart);
    // insert all commandline args
    for (int i = 1; i < argc; ++i) {
        // insert and move past \v
        newEnvCur[0] = L'\v';
        --envSize;
        ++newEnvCur;
        exitCode = wcscpy_s(newEnvCur, envSize, argv[i]);
        if (exitCode) {
            winerror(exitCode, L"Failed to copy argument %d", i);
            goto abort;
        }
        debug(L"%s", newEnvCur);
        // insert and move past argument
        envSize = envSize - wcslen(argv[i]);
        newEnvCur = newEnvCur + wcslen(argv[i]);
    }
    // insert terminating \v for GRAAL_PYTHON_ARGS
    newEnvCur[0] = L'\v';
    --envSize;
    ++newEnvCur;
    // insert terminating \0 for GRAAL_PYTHON_ARGS
    newEnvCur[0] = L'\0';
    --envSize;
    ++newEnvCur;
    // inserting terminating \0 for env block
    newEnvCur[0] = L'\0';
    --envSize;
    if (envSize) {
        error(L"Environment size is wrong, %d", envSize);
        goto abort;
    }

    // Launch selected runtime
    exitCode = launchEnvironment(newEnv, newExeStart);

abort:
    if (pyvenvCfgFile) fclose(pyvenvCfgFile);
    if (currentExeFile) fclose(currentExeFile);
    if (env) FreeEnvironmentStringsW(env);
    if (newEnv) free(newEnv);
    return exitCode;
}
