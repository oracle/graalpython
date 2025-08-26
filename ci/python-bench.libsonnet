(import "python-gate.libsonnet") +
{
    local common                = import "graal/ci/common.jsonnet",
    local common_util           = import "graal/ci/ci_common/common-utils.libsonnet",
    local run_spec              = import "graal/ci/ci_common/run-spec.libsonnet",
    local tools                 = import "graal/ci/ci_common/run-spec-tools.libsonnet",
    local const                 = import "constants.libsonnet",
    local reports               = $.overlay_imports.reports,

    local exclude               = run_spec.exclude,
    local task_spec             = run_spec.task_spec,
    local add_multiply          = run_spec.add_multiply,
    local platform_spec         = run_spec.platform_spec,
    local evaluate_late         = run_spec.evaluate_late,
    local downloads             = self.downloads,
    local graalpy_gate          = self.graalpy_gate,
    local os_arch_jdk_mixin     = self.os_arch_jdk_mixin,
    local all_jobs              = self.all_jobs,
    local no_jobs               = self.no_jobs,

    local eclipse               = task_spec(common.deps.eclipse),
    local jdt                   = task_spec(common.deps.jdt),
    local pylint                = task_spec(common.deps.pylint),
    local svm                   = task_spec(common.deps.svm),
    local test_reports          = task_spec(reports),

    BENCHMARKS:: {
        micro: "micro-graalpython:*",
        micro_native: "micro-native-graalpython:*",
        meso: "meso-graalpython:*",
        macro: "macro-graalpython:*",
        interop: "interop-graalpython:*",
        warmup: "python-warmup-graalpython:*",
        micro_small: "micro-small-graalpython:*",
        meso_small: "meso-small-graalpython:*",
        java_embedding_meso: "java-embedding-meso:*",
        java_embedding_meso_small: "java-embedding-meso-small:*",
        jmh: "python-jmh:GRAALPYTHON_BENCH",
        heap: "heap-graalpython:*",
    },

    PY_BENCHMARKS:: {
        pyperformance: "pyperformance-suite",
        pypy: "pypy-suite",
        numpy: "numpy-suite",
        pandas: "pandas-suite",
    },

    local bench_feature_map = {
        vm_name: {
            [name]: no_jobs {"*"+: vm_name(name)}
            for name in std.objectFields(const.VM)
        },
        job_type: {
            checkup: no_jobs {"*" +: checkup},
        },
    },
    bench_variants(s):: run_spec.generate_variants(s, bench_feature_map),

    local checkup = task_spec({
        name_suffix +:: ["checkup"],
        setup+: [
            ['set-export', 'GRAALPYTHON_BENCHMARKS_CHECKUP', '1']
        ],
        teardown: [],
    }),

    forks(value):: task_spec({
        local fork_count_path(file) = if std.startsWith(file, "./") then value else "$FORK_COUNTS_DIRECTORY/python/" + file,
        local path = if std.length(value) == 0 then "" else fork_count_path(value),
        name_suffix +:: ["forks"],
        mx_bench_args +:: ["--fork-count-file=" + path],
        environment +: {
            FORK_COUNTS_DIRECTORY: "$BUILD_DIR/benchmarking-config/fork-counts"
        },
        setup +: [
            ["git", "clone", "--depth", "1", $.overlay_imports.BENCHMARK_CONFIG_GIT, "$BUILD_DIR/benchmarking-config"]
        ]
    }),

    verbose:: task_spec({
        mx_args+:: ["-v"]
    }),

    local vm_name(name) = task_spec({
        vm_name:: name,
    }),

    local environment(os, arch) = self.environment(os, arch) + {
        BENCH_RESULTS_FILE_PATH: "bench-results.json",
        PANDAS_REPO_URL: $.overlay_imports.PANDAS_REPO_GIT,
        PIP_EXTRA_INDEX_URL: $.overlay_imports.PIP_EXTRA_INDEX_URL,
    },

    local packages(os, arch) = self.packages(os, arch) + {
        make: ">=3.83",
        binutils: "==2.23.2",
    },

    local logs(os, arch) = self.logs(os, arch),

    local is_graalvm_vm(name) = std.startsWith(name, "graalvm_") || std.startsWith(name, "graalpython") || std.startsWith(name, "java_embedding") || std.startsWith(name, "java_jmh"),
    local is_java_embedding_vm(name) = std.startsWith(name, "java_embedding"),
    local is_baseline_vm(name) = std.startsWith(name, "cpython") || std.startsWith(name, "pypy"),
    local is_cpython_vm(name) = std.startsWith(name, "cpython"),

    local capabilities(os, arch) =
        if os == "linux" then
            ["no_frequency_scaling", "tmpfs25g", "x52"]
        else
            [],

    bench_mixin:: task_spec({
        capabilities+: capabilities(self.os, self.arch),
    }),

    local bench_cmd(bench, mx_args, mx_bench_args, vm, vm_config_name, vm_info, results_file=null, bench_args=[]) =
        assert std.type(bench_args) == "array" : "bench_args must be an array";
        ["mx"] + mx_args +
        vm_info.env + vm_info.dy +
        ["benchmark"] + mx_bench_args + [bench] +
        (if results_file != null then ["--results-file", results_file] else []) +
        vm + if std.objectHas(vm_info, 'python_vm_config') then [vm_config_name, vm_info.python_vm_config] else [] +
        ["--"] + bench_args,

    local bench_base_task(bench=null, benchmarks=$.BENCHMARKS) = {
        // private
        name_prefix :: ["pybench"],
        name_suffix :: [],
        vm_name:: null,
        benchmarks:: benchmarks,
        target:: null,
        mx_args:: [],
        mx_bench_args:: [],
        vm_config_name:: "--python-vm-config",
        variations:: [],
        os:: null,
        arch:: null,
        jdk:: null,
        bench_name:: if bench == null then self.task_name else bench,
        name_target:: null,
        _name_target:: if self.name_target == null then self.target else self.name_target,
        vm_info:: const.VM[self.vm_name],
        graalvm_edition:: self.vm_info.edition,
        assert self.bench_name in benchmarks : "specified bench name ('%s') not defined in $.BENCHMARKS" % self.bench_name,
        bench:: benchmarks[self.bench_name],
        vm:: [],

        // public
        capabilities+: capabilities(self.os, self.arch),
        downloads: downloads(self.os, self.arch),
        targets: [self.target] + if self.target == 'bench' then [] else ['bench'],
        python_version: "3.8",
        local full_task_name = std.prune(self.name_prefix + [self.bench_name] + self.name_suffix),
        name: std.join("-", std.prune(full_task_name + [self.vm_name] + [self._name_target] + self.variations + [self.os, self.arch, self.jdk])),
        packages +: packages(self.os, self.arch),
        environment +: environment(self.os, self.arch),
        logs +: logs(self.os, self.arch),
        setup: [],
        run: [
            bench_cmd(self.bench, self.mx_args, self.mx_bench_args, self.vm, self.vm_config_name, self.vm_info),
        ],
        teardown: [
            ["bench-uploader.py", "${BENCH_RESULTS_FILE_PATH}"],
        ]
    },

    local py_bench = {
        vm:: [
            "--",
            "--python-vm", self.vm_info.python_vm,
        ],
    },

    local graalpy_bench = {
        vm:: if (is_java_embedding_vm(self.vm_name)) then [
            "--",
            "--pythonjavadriver-vm", self.vm_info.python_vm,
            "--jvm", self.vm_info.jvm,
            "--jvm-config", self.vm_info.jvm_config
        ] else if std.objectHas(self.vm_info, "python_vm") then [
            assert !std.objectHas(self.vm_info, "jvm") && !std.objectHas(self.vm_info, "jvm_config"): "%s defines JVM fields (%s) for a standalone Python run" % [self.name, self.vm_info];
            "--",
            "--python-vm", self.vm_info.python_vm,
        ] else [
            "--",
            "--jvm", self.vm_info.jvm,
            "--jvm-config", self.vm_info.jvm_config,
        ],
        vm_config_name:: if (is_java_embedding_vm(self.vm_name)) then
                "--pythonjavadriver-vm-config"
            else
                super.vm_config_name,
        setup+: [
            // NOTE: logic shared with ci/python-gate.libsonnet, keep in sync
            // ensure we get graal-enterprise as a hostvm
            ["git", "clone", $.overlay_imports.GRAAL_ENTERPRISE_GIT, "${BUILD_DIR}/graal-enterprise"],
            // force imports the main repository to get the right graal commit
            ["mx", "sforceimports"],
            // checkout the matching revision of graal-enterprise repository based on the graal/compiler checkout
            ["mx", "--quiet", "--dy", "/graal-enterprise", "checkout-downstream", "compiler", "graal-enterprise", "--no-fetch"],
            // force imports with the env, which may clone other things (e.g. substratevm-enterprise-gcs)
            ["mx"] + self.vm_info.env + self.vm_info.dy + ["sforceimports"],
            // force imports the main repository to get the right graal commit
            ["mx", "sforceimports"],
            // logging
            ["mx"] + self.vm_info.env + self.vm_info.dy + ["sversions"],
            // build main repository
            ["mx", "build"],
            ["mx"] + self.vm_info.env + self.vm_info.dy + ["graalvm-show"],
            ["mx"] + self.vm_info.env + self.vm_info.dy + ["build", "--force-javac"],
        ] + (
            if self.bench == "compute" then [
                ["mx", "build", "--dep", "compute"]
            ] else []
        ),
    },

    //------------------------------------------------------------------------------------------------------------------
    // graalpy bench tasks
    //------------------------------------------------------------------------------------------------------------------
    bench_task(bench=null, benchmarks=$.BENCHMARKS):: os_arch_jdk_mixin + test_reports + task_spec(
        bench_base_task(bench=bench, benchmarks=benchmarks) +
        evaluate_late(
            {bench_task_1: function(builder)
                local vm_name = builder.vm_name;
                local vm_info = builder.vm_info;
                if is_baseline_vm(vm_name) then
                    py_bench
                else
                    assert is_graalvm_vm(vm_name): "vm_name '%s' is not a graalvm vm" % vm_name;
                    graalpy_bench
            }
        )
    ),

    bisect_bench_task:: os_arch_jdk_mixin + test_reports + task_spec({
        downloads: downloads(self.os, self.arch),
        name: "bisect-benchmark",
        targets: ['bench'],
        logs +: logs(self.os, self.arch),
        packages +: packages(self.os, self.arch) + {
            "apache/ant": ">=1.9.4",
            libyaml: "==0.2.5",
            "pip:ninja_syntax": "==1.7.2",
            "pip:pylint": "==2.4.4",
            "pip:lazy-object-proxy": "==1.6.0",
            ruby: "==3.1.2",
            papi: "==5.5.1",
        },
        environment +: environment(self.os, self.arch) + {
            BISECT_BENCHMARK_CONFIG: "bisect-benchmark.ini",
            BISECT_EMAIL_SMTP_SERVER: $.overlay_imports.BISECT_EMAIL_SMTP_SERVER,
            BISECT_EMAIL_TO_PATTERN: ".*@oracle.com",
            BISECT_EMAIL_FROM: $.overlay_imports.BISECT_EMAIL_FROM,
            ENABLE_POLYBENCH_HPC: "yes",
            POLYBENCH_HPC_EXTRA_HEADERS: "/cm/shared/apps/papi/papi-5.5.1/include",
            POLYBENCH_HPC_PAPI_LIB_DIR: "/cm/shared/apps/papi/papi-5.5.1/lib",
        },
        setup: [
            ["git", "clone", $.overlay_imports.GRAAL_ENTERPRISE_GIT, "${BUILD_DIR}/graal-enterprise"],
            ["git", "clone", $.overlay_imports.CI_OVERLAYS_GIT, "${BUILD_DIR}/ci-overlays"],
        ],
        run: [
            ["mx", "bisect-benchmark"],
        ],
    }),
}

// Local Variables:
// jsonnet-indent-level: 4
// End:
