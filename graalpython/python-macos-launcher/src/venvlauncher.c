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
    const FILE *fp = fopen(pyenv_cfg_path, "r");
    if (fp == NULL) {
        fprintf(stderr, "Failed to open pyenv.cfg file!\n");
        exit(1);
    }

    char current_line[MAX_LINE];
    const char *key = "venvlauncher_command";
    size_t key_len = strlen(key);

    while (fgets(current_line, sizeof(current_line), fp)) {
        char *p = current_line;

        while (isspace((unsigned char) *p)) p++;

        if (strncmp(p, key, key_len) == 0) {
            p += key_len;

            // Skip spaces and '='
            while (isspace((unsigned char) *p)) p++;
            if (*p == '=') p++;
            while (isspace((unsigned char) *p)) p++;
            if (*p == '\"') {
                char *end = p + strlen(p);
                while (isspace((unsigned char) end[-1]) || end[-1] == '\n') {
                    *--end = '\0';
                }
                if (end[-1] != '\"') {
                    fprintf(stderr, "venv command is not in correct format!");
                    exit(1);
                }
                p++;
                end[-1] = '\0';
            }

            fclose(fp);
            return strdup(p);
        }
    }
}

void split_venv_command_into_args(char *venv_command, char *result[]) {
    char *current_token = strtok(venv_command, " ");
    for (int i = 0; i < 5; i++) {
        result[i] = current_token;
        current_token = strtok(NULL, " ");
    }
    if (current_token != NULL) {
        fprintf(stderr, "Unexpected venv_command size!\n");
        exit(1);
    }
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
    char *venv_command_dup = strdup(venv_command);

    char *venv_args[5];
    split_venv_command_into_args(venv_command_dup, venv_args);

    // Adds "--python.VenvlauncherCommand="
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

    // venv_args (5) + "--python.VenvlauncherCommand=" + Adds "--python.Executable=" + rest of argc + NULL
    size_t args_size = 5 + 2 + (argc - 1) + 1;
    char *args[args_size];

    // copy venv_args
    int k = 0;
    for (k = 0; k < 5; k++) {
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
