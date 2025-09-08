/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <libgen.h>
#include <string.h>
#include <limits.h>
#include <mach-o/dyld.h>

#define MAX_LINE 4096
#define MAX_ARGS 100

#define GRAAL_PYTHON_EXE_ARG "--python.Executable="
#define GRAAL_PYTHON_BASE_EXE_ARG "--python.VenvlauncherCommand="

/**
 * Reads the 'venvlauncher_command' from a pyvenv.cfg file.
 * Returns a newly allocated string. Caller must free() it.
 */
char *get_pyenvcfg_command(const char *pyenv_cfg_path) {
    FILE *fp = fopen(pyenv_cfg_path, "r");
    if (fp == NULL) {
        fprintf(stderr, "Failed to open pyenv.cfg file!\n");
        exit(1);
    }

    char *current_line = NULL;
    size_t current_line_size = 0;
    const char key[] = "venvlauncher_command";
    size_t key_len = sizeof(key) - 1;

    while (getline(&current_line, &current_line_size, fp) != -1) {
        char *p = current_line;

        while (isspace((unsigned char) *p)) p++;

        if (strncmp(p, key, key_len) == 0) {
            p += key_len;

            // Skip spaces and '='
            while (isspace((unsigned char) *p)) p++;
            if (*p == '=') {
                p++;
            } else {
                fprintf(stderr, "venv command is not in correct format. Expected '=' after the venvlauncher_command");
                free(current_line);
                fclose(fp);
                exit(1);
            }
            while (isspace((unsigned char) *p)) p++;
            if (*p == '\"') {
                char *end = p + strlen(p);
                while (end > p && (isspace((unsigned char) end[-1]) || end[-1] == '\n')) {
                    *--end = '\0';
                }
                if (end <= p + 1 || end[-1] != '\"') {
                    fprintf(stderr, "venv command is not in correct format");
                    free(current_line);
                    fclose(fp);
                    exit(1);
                }
                p++;
                end[-1] = '\0';
            }

            char *result = strdup(p);
            free(current_line);
            fclose(fp);
            return result;

        }
    }
    free(current_line);
    fprintf(stderr, "venvlauncher_command not found in pyenv.cfg file");
    exit(1);
}

char **split_venv_command_into_args(const char *venv_command, int *argc_out) {

    char *copy = strdup(venv_command);
    size_t capacity = 5;
    char **args = malloc(capacity * sizeof(char *));
    if (!args) {
        fprintf(stderr, "allocation failed failed\n");
        free(copy);
        exit(1);
    }

    size_t count = 0;
    char *current_token = strtok(copy, " ");
    while (current_token) {
        if (count >= capacity) {
            capacity *= 2;
            char **tmp = realloc(args, capacity * sizeof(char *));
            if (!tmp) {
                // allocation failed
                fprintf(stderr, "reallocation failed\n");
                for (size_t i = 0; i < count; i++) {
                    free(args[i]);
                }
                free(args);
                free(copy);
                exit(1);
            }
            args = tmp;
        }

        args[count++] = strdup(current_token);
        current_token = strtok(NULL, " ");
    }

    free(copy);
    *argc_out = (int) count;
    return args;
}

void find_pyvenv(char *pyvenv_cfg_path, size_t path_size) {
    char executable_path[PATH_MAX];
    uint32_t executable_path_size = sizeof(executable_path);

    // Get current executable path (macOS)
    if (_NSGetExecutablePath(executable_path, &executable_path_size) != 0) {
        fprintf(stderr, "Failed to get executable path\n");
        exit(1);
    }

    // First try search for the pyvenv on top level
    char *dir_path = dirname(executable_path);
    snprintf(pyvenv_cfg_path, path_size, "%s/pyvenv.cfg", dir_path);
    if (access(pyvenv_cfg_path, F_OK) != 0) {
        // Try searching one level up
        dir_path = dirname(dir_path);
        snprintf(pyvenv_cfg_path, path_size, "%s/pyvenv.cfg", dir_path);
        if (access(pyvenv_cfg_path, F_OK) != 0) {
            fprintf(stderr, "Error: pyvenv.cfg file not found at %s\n", pyvenv_cfg_path);
            exit(1);
        }
    }
}

int main(int argc, char *argv[]) {
    char pyvenv_cfg_path[PATH_MAX];

    find_pyvenv(pyvenv_cfg_path, sizeof(pyvenv_cfg_path));

    char *venv_command = get_pyenvcfg_command(pyvenv_cfg_path);

    int venv_argc = 0;
    char **venv_args = split_venv_command_into_args(venv_command, &venv_argc);

    // Adds "--python.VenvlauncherCommand="
    // + 2 for quotes
    // + 1 for '\0'
    size_t python_base_exec_size = strlen(venv_command) + strlen(GRAAL_PYTHON_BASE_EXE_ARG) + 2 + 1;
    char python_base_exec_command[python_base_exec_size];
    snprintf(python_base_exec_command, python_base_exec_size, "%s\"%s\"", GRAAL_PYTHON_BASE_EXE_ARG, venv_command);

    // Adds "--python.Executable="
    size_t python_exec_arg_size = strlen(GRAAL_PYTHON_EXE_ARG) + strlen(argv[0]) + 1;
    char python_exec_command[python_exec_arg_size];
    snprintf(python_exec_command,
             python_exec_arg_size,
             "%s%s",
             GRAAL_PYTHON_EXE_ARG,
             argv[0]);

    // venv_args + "--python.VenvlauncherCommand=" + Adds "--python.Executable=" + rest of argc (-1 because we are not interested in argv[0]) + NULL
    size_t args_size = venv_argc + 2 + (argc - 1) + 1;
    char *args[args_size];

    // copy venv_args
    int k = 0;
    for (k = 0; k < venv_argc; k++) {
        args[k] = venv_args[k];
    }

    args[k++] = python_base_exec_command;
    args[k++] = python_exec_command;

    // copy rest of the args
    for (int i = 1; i < argc; i++) {
        args[k++] = argv[i];
    }

    args[k] = NULL;

    execv(args[0], args);
    perror("execv failed"); // only runs if execv fails
    return 1;
}
