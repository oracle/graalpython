// python-gate.libsonnet and python-bench.libsonnet reference overlay_imports
// via the global reference `$', so to make this work with the CI overlay
// mechanism we import them like this into the main object instead of loading
// them into a local.
(import "ci/python-gate.libsonnet") +
(import "ci/python-bench.libsonnet") +
{
    overlay: "a8df46e16d6fcae69e9a7c42c632131fdf6e043e",
    specVersion: "6",
    // Until buildbot issues around CI tiers are resolved, we cannot use them
    // tierConfig: self.tierConfig,

    // These are filled in by the CI overlay
    overlay_imports: {
        reports: {},
        RELEASES_BASE_URL: "",
        RUBYGEMS_MIRROR: "",
        JEKYLL_THEME_GIT: "",
        WEBSITE_GIT: "",
        STAGING_DEPLOY_CMD: [],
        GRAAL_ENTERPRISE_GIT: "",
        CI_OVERLAYS_GIT: "",
        BENCHMARK_CONFIG_GIT: "",
        PANDAS_REPO_GIT: "",
        PIP_EXTRA_INDEX_URL: "",
        WATCHDOG_GIT: "",
        COMPLIANCE_GIT: "",
        BISECT_EMAIL_SMTP_SERVER: "",
        BISECT_EMAIL_FROM: "",
        npm_config_registry: "",
        RODINIA_DATASET_ZIP: "",
        BUILDBOT_COMMIT_SERVICE: "",
    },

    local run_spec              = import "ci/graal/ci/ci_common/run-spec.libsonnet",
    local utils                 = import "ci/utils.libsonnet",

    local task_spec             = run_spec.task_spec,
    local platform_spec         = run_spec.platform_spec,
    local t                     = self.timelimit,

    local tier1                 = self.tier1,
    local tier2                 = self.tier2,
    local tier3                 = self.tier3,
    local daily                 = self.daily,
    local weekly                = self.weekly,
    local monthly               = self.monthly,
    local bench                 = self.bench,
    local post_merge            = self.post_merge,
    local on_demand             = self.on_demand,

    local provide               = self.provide,
    local require               = self.require,
    local batches               = self.batches,

    local no_jobs               = self.no_jobs,

    local forks                 = self.forks,
    local bench_variants        = self.bench_variants,
    local BENCHMARKS            = self.BENCHMARKS,
    local PY_BENCHMARKS         = self.PY_BENCHMARKS,

    // builders build config
    local base_gate             = self.graalpy_gate,
    local gpgate                = self.graalpy_gate,
    local gpgate_ee             = self.graalpy_ee_gate,
    local gpgate_maven          = self.graalpy_maven_gate,
    local style_gate            = self.style_gate,
    local cpygate               = self.cpython_gate,
    local ut_retagger           = self.unittest_retagger_gate,
    local cov_jacoco_tagged     = self.cov_jacoco_gate_tagged,
    local cov_jacoco_base       = self.cov_jacoco_gate_base,
    local cov_truffle           = self.cov_truffle_gate,
    local watchdog              = self.watchdog,
    local bench_task(bench=null, benchmarks=BENCHMARKS) = super.bench_task(bench=bench, benchmarks=benchmarks),
    local bisect_bench_task     = self.bisect_bench_task,

    local bytecode_dsl_env = task_spec({
        environment +: {
            BYTECODE_DSL_INTERPRETER: "true"
        },
    }),
    local bytecode_dsl_gate(name) = bytecode_dsl_env + task_spec({
        tags :: name,
    }),
    local bytecode_dsl_bench = bytecode_dsl_env + task_spec({
        name_suffix +:: ["bytecode-dsl"],
    }),

    // -----------------------------------------------------------------------------------------------------------------
    //
    // main build definition (matrix)
    //
    // -----------------------------------------------------------------------------------------------------------------
    local GPY_JVM21_STANDALONE      = "graalpy-jvm21-standalone",
    local GPY_JVM_STANDALONE        = "graalpy-jvm-standalone",
    local GPY_NATIVE_STANDALONE     = "graalpy-native-standalone",
    local GPY_NATIVE_BYTECODE_DSL_STANDALONE = "graalpy-native-bc-dsl-standalone",
    local GPYEE_JVM_STANDALONE      = "graalpy-ee-jvm-standalone",
    local GPYEE_NATIVE_STANDALONE   = "graalpy-ee-native-standalone",
    local GRAAL_JDK_LATEST          = "graal-jdk-latest",
    local TAGGED_UNITTESTS_SPLIT    = 8,
    local COVERAGE_SPLIT            = 3,

    // -----------------------------------------------------------------------------------------------------------------
    // gates
    // -----------------------------------------------------------------------------------------------------------------
    local gate_task_dict = {
        "python-unittest": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : daily     + t("01:00:00") + provide(GPY_JVM21_STANDALONE),
            "linux:aarch64:jdk21"        : daily     + t("02:00:00") + provide(GPY_JVM21_STANDALONE),
            "darwin:aarch64:jdk21"       : daily     + t("01:00:00") + provide(GPY_JVM21_STANDALONE),
            "windows:amd64:jdk21"        : daily     + t("01:30:00") + provide(GPY_JVM21_STANDALONE),
            "linux:amd64:jdk-latest"     : tier2                     + require(GPY_JVM_STANDALONE),
            "linux:aarch64:jdk-latest"   : tier3                     + provide(GPY_JVM_STANDALONE),
            "darwin:aarch64:jdk-latest"  : tier3                     + provide(GPY_JVM_STANDALONE),
            "windows:amd64:jdk-latest"   : tier3                     + provide(GPY_JVM_STANDALONE),
        }),
        "python-unittest-bytecode-dsl": gpgate + platform_spec(no_jobs) + bytecode_dsl_gate("python-unittest") + platform_spec({
            "linux:amd64:jdk-latest"     : daily     + t("01:00:00"),
            "linux:aarch64:jdk-latest"   : daily     + t("01:00:00"),
            "darwin:aarch64:jdk-latest"  : daily     + t("01:00:00"),
        }),
        "python-unittest-multi-context": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : daily     + t("01:00:00") + require(GPY_JVM21_STANDALONE),
            "linux:aarch64:jdk21"        : daily     + t("01:30:00") + require(GPY_JVM21_STANDALONE),
            "darwin:aarch64:jdk21"       : daily     + t("01:00:00") + require(GPY_JVM21_STANDALONE),
            "windows:amd64:jdk21"        : daily     + t("02:00:00"),
            "linux:amd64:jdk-latest"     : tier2                     + require(GPY_JVM_STANDALONE),
            "linux:aarch64:jdk-latest"   : daily     + t("01:30:00") + require(GPY_JVM_STANDALONE),
            "darwin:aarch64:jdk-latest"  : daily     + t("01:00:00") + require(GPY_JVM_STANDALONE),
            "windows:amd64:jdk-latest"   : daily     + t("01:30:00"),
        }),
        "python-unittest-jython": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : daily     + t("00:30:00") + require(GPY_JVM21_STANDALONE),
            "linux:aarch64:jdk21"        : daily     + t("00:30:00") + require(GPY_JVM21_STANDALONE),
            "darwin:aarch64:jdk21"       : daily     + t("00:30:00") + require(GPY_JVM21_STANDALONE),
            "linux:amd64:jdk-latest"     : tier2                     + require(GPY_JVM_STANDALONE),
            "linux:aarch64:jdk-latest"   : daily     + t("00:30:00") + require(GPY_JVM_STANDALONE),
            "darwin:aarch64:jdk-latest"  : daily     + t("00:30:00") + require(GPY_JVM_STANDALONE),
        }),
        "python-unittest-hpy": gpgate + require(GPY_NATIVE_STANDALONE) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3,
        }),
        "python-unittest-arrow-storage": gpgate + require(GPY_JVM_STANDALONE) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier2,
        }),
        "python-unittest-posix": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier2                     + require(GPY_JVM_STANDALONE),
            "linux:aarch64:jdk-latest"   : tier3                     + require(GPY_JVM_STANDALONE),
            "darwin:aarch64:jdk-latest"  : tier3                     + require(GPY_JVM_STANDALONE),
        }),
        "python-unittest-standalone": gpgate_maven + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : daily     + t("02:00:00") + require(GPY_JVM21_STANDALONE),
            "linux:aarch64:jdk21"        : daily     + t("02:00:00") + require(GPY_JVM21_STANDALONE),
            "darwin:aarch64:jdk21"       : daily     + t("02:00:00") + require(GPY_JVM21_STANDALONE),
            "windows:amd64:jdk21"        : daily     + t("02:00:00") + require(GPY_JVM21_STANDALONE) + batches(2),
            "linux:amd64:jdk-latest"     : tier3                     + require(GPY_JVM_STANDALONE) + require(GRAAL_JDK_LATEST),
            "linux:aarch64:jdk-latest"   : tier3                     + require(GPY_JVM_STANDALONE) + require(GRAAL_JDK_LATEST),
            "darwin:aarch64:jdk-latest"  : tier3                     + require(GPY_JVM_STANDALONE) + require(GRAAL_JDK_LATEST),
            "windows:amd64:jdk-latest"   : tier3                     + require(GPY_JVM_STANDALONE) + require(GRAAL_JDK_LATEST) + batches(2),
        }),
        "python-junit": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : daily     + t("01:00:00"),
            "linux:aarch64:jdk21"        : daily     + t("01:30:00"),
            "darwin:aarch64:jdk21"       : daily     + t("01:30:00"),
            "windows:amd64:jdk21"        : daily     + t("01:00:00"),
            "linux:amd64:jdk-latest"     : tier3                      + require(GRAAL_JDK_LATEST),
            "linux:aarch64:jdk-latest"   : tier3                      + require(GRAAL_JDK_LATEST),
            "darwin:aarch64:jdk-latest"  : tier3                      + require(GRAAL_JDK_LATEST),
            "windows:amd64:jdk-latest"   : tier3                      + require(GRAAL_JDK_LATEST),
        }),
        "python-junit-bytecode-dsl": gpgate + platform_spec(no_jobs) + bytecode_dsl_gate("python-junit") + platform_spec({
            "linux:amd64:jdk-latest"     : tier3                      + require(GRAAL_JDK_LATEST),
        }),
        "python-junit-maven": gpgate_maven + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : daily     + t("00:30:00"),
            "linux:aarch64:jdk21"        : daily     + t("01:00:00"),
            "darwin:aarch64:jdk21"       : daily     + t("01:30:00"),
            "windows:amd64:jdk21"        : daily     + t("01:30:00"),
            "linux:amd64:jdk-latest"     : tier3                     + provide(GRAAL_JDK_LATEST),
            "linux:aarch64:jdk-latest"   : tier3                     + provide(GRAAL_JDK_LATEST),
            "darwin:aarch64:jdk-latest"  : tier3                     + provide(GRAAL_JDK_LATEST),
            "windows:amd64:jdk-latest"   : tier3                     + provide(GRAAL_JDK_LATEST),
        }),
        "python-junit-polyglot-isolates": gpgate_ee + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3,
            "linux:aarch64:jdk-latest"   : tier3,
            "darwin:aarch64:jdk-latest"  : tier3,
            "windows:amd64:jdk-latest"   : tier3,
        }),
        "python-svm-build": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier1                     + provide(GPY_NATIVE_STANDALONE),
            "linux:aarch64:jdk-latest"   : tier3                     + provide(GPY_NATIVE_STANDALONE),
            "darwin:aarch64:jdk-latest"  : tier3                     + provide(GPY_NATIVE_STANDALONE),
            "windows:amd64:jdk-latest"   : tier3                     + provide(GPY_NATIVE_STANDALONE),
        }),
        "python-pgo-profile": gpgate_ee + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : post_merge + t("01:30:00") + task_spec({
                run: [["mx", "python-native-pgo"]],
                logs+: [
                    "default.iprof.gz",
                    "default.lcov",
                ],
            }),
        }),
        "python-pgo-profile-bytecode-dsl": gpgate_ee + bytecode_dsl_env + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : post_merge + t("01:30:00") + task_spec({
                run: [["mx", "python-native-pgo"]],
                logs+: [
                    "default-bytecode-dsl.iprof.gz",
                    "default-bytecode-dsl.lcov",
                ],
            }),
        }),
        "python-svm-unittest": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier2                     + require(GPY_NATIVE_STANDALONE),
            "linux:aarch64:jdk-latest"   : tier3                     + require(GPY_NATIVE_STANDALONE),
            "darwin:aarch64:jdk-latest"  : tier3                     + require(GPY_NATIVE_STANDALONE),
            "windows:amd64:jdk-latest"   : tier3                     + require(GPY_NATIVE_STANDALONE) + batches(2),
        }),
        "python-svm-unittest-bytecode-dsl": gpgate + platform_spec(no_jobs) + bytecode_dsl_gate("python-svm-unittest") + platform_spec({
            "linux:amd64:jdk-latest"     : tier2                     + provide(GPY_NATIVE_BYTECODE_DSL_STANDALONE),
        }),
        "python-tagged-unittest": gpgate + require(GPY_NATIVE_STANDALONE) + batches(TAGGED_UNITTESTS_SPLIT) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier2,
            "linux:aarch64:jdk-latest"   : tier3,
            "darwin:aarch64:jdk-latest"  : tier3,
            "windows:amd64:jdk-latest"   : daily     + t("02:00:00"),
        }),
        "python-tagged-unittest-bytecode-dsl": gpgate + require(GPY_NATIVE_BYTECODE_DSL_STANDALONE) + batches(TAGGED_UNITTESTS_SPLIT) + bytecode_dsl_gate("python-tagged-unittest") + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3,
        }),
        "python-graalvm": gpgate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3                      + require(GRAAL_JDK_LATEST),
            "linux:aarch64:jdk-latest"   : tier3                      + require(GRAAL_JDK_LATEST),
            "darwin:aarch64:jdk-latest"  : tier3                      + require(GRAAL_JDK_LATEST),
            "windows:amd64:jdk-latest"   : tier3                      + require(GRAAL_JDK_LATEST),
        }),
        "python-unittest-cpython": cpygate + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier1,
        }),
        "python-unittest-retagger": ut_retagger + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3,
            "linux:aarch64:jdk-latest"   : weekly    + t("20:00:00"),
            "darwin:aarch64:jdk-latest"  : weekly    + t("20:00:00"),
            "windows:amd64:jdk-latest"   : weekly    + t("20:00:00"),
        }),
        "python-coverage-jacoco-tagged": cov_jacoco_tagged + batches(COVERAGE_SPLIT) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : weekly    + t("20:00:00"),
            "darwin:aarch64:jdk21"       : weekly    + t("20:00:00"),
            "windows:amd64:jdk21"        : weekly    + t("20:00:00"),
        }),
        "python-coverage-jacoco-base": cov_jacoco_base + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : weekly    + t("20:00:00"),
            "darwin:aarch64:jdk21"       : weekly    + t("20:00:00"),
            "windows:amd64:jdk21"        : weekly    + t("20:00:00"),
        }),
        "python-coverage-truffle": cov_truffle + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk21"          : weekly    + t("20:00:00"),
        }),
        "corp-compliance-watchdog": watchdog + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier1,
        }),
        "bisect-benchmark": bisect_bench_task + platform_spec(no_jobs) + platform_spec({
            # Compiler and SVM no longer support building with anything but the
            # latest JDK. This makes the bisect job prone to failure when
            # bisecting in the compiler suite because we keep the same latest
            # JDK, but there is nothing we can do about that.
            "linux:amd64:jdk-latest"     : on_demand + t("20:00:00"),
        }),
        "style": style_gate + task_spec({ tags:: "style,build,python-license" }) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier1 + provide(GPY_JVM_STANDALONE),
        }),
        "style-ecj": style_gate + task_spec({ tags:: "style,ecjbuild" }) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier1,
        }),
        // tests with sandboxed backends for various modules (posix, sha3, ctypes, ...)
        "python-unittest-sandboxed": gpgate_ee + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3,
        }),
        "python-svm-unittest-sandboxed": gpgate_ee + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3 + provide(GPYEE_NATIVE_STANDALONE),
        }),
        "tox-example": gpgate_ee + require(GPYEE_NATIVE_STANDALONE) + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier3,
        }),
    },

    local need_pgo = task_spec({runAfter: ["python-pgo-profile-post_merge-linux-amd64-jdk-latest"]}),
    local need_bc_pgo = task_spec({runAfter: ["python-pgo-profile-bytecode-dsl-post_merge-linux-amd64-jdk-latest"]}),
    local forks_warmup = forks("./mx.graalpython/warmup-fork-counts.json"),
    local forks_meso = forks("meso.json"),
    local raw_results = task_spec({
        logs +: [
            "raw_results.json",
        ],
    }),

    // -----------------------------------------------------------------------------------------------------------------
    // benchmarks
    // -----------------------------------------------------------------------------------------------------------------
    // [info]: when adding a benchmark, the key in the `bench_task_dict` is taken as the name of the benchmark if it is
    // not specified as the first arg to `bench_task`.
    local bench_task_dict = {
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ce_default"                                : {"linux:amd64:jdk-latest" : on_demand + t("08:00:00")},
            "vm_name:graalvm_ee_default"                                : {"linux:amd64:jdk-latest" : post_merge + t("08:00:00") + need_pgo},
            "vm_name:graalpython_core"                                  : {"linux:amd64:jdk-latest" : on_demand      + t("08:00:00")},
            "vm_name:graalpython_enterprise"                            : {"linux:amd64:jdk-latest" : daily      + t("08:00:00"),
                "job_type:checkup"                                      : {"linux:amd64:jdk-latest" : on_demand  + t("08:00:00")}
            },
            "vm_name:graalpython_enterprise_multi"                      : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
            "vm_name:cpython"                                           : {"linux:amd64:jdk-latest" : monthly    + t("04:00:00")},
            "vm_name:pypy"                                              : {"linux:amd64:jdk-latest" : on_demand    + t("04:00:00")},
        }),
        for bench in ["micro", "meso", "macro"]
    } + {
        [bench + "-bytecode-dsl"]: bench_task(bench) + bytecode_dsl_bench + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ee_default_bc_dsl"                                : {"linux:amd64:jdk-latest" : daily      + t("08:00:00")},
            "vm_name:graalpython_enterprise_bc_dsl"                            : {"linux:amd64:jdk-latest" : daily      + t("08:00:00")},
        }),
        for bench in ["micro", "meso", "macro"]
    } + {
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ce_default"                                : {"linux:amd64:jdk-latest" : on_demand + t("08:00:00")},
            "vm_name:graalvm_ee_default"                                : {"linux:amd64:jdk-latest" : post_merge + t("08:00:00") + need_pgo},
            "vm_name:graalpython_core"                                  : {"linux:amd64:jdk-latest" : on_demand      + t("08:00:00")},
            "vm_name:graalpython_core_panama"                           : {"linux:amd64:jdk-latest" : on_demand  + t("08:00:00")},
            "vm_name:graalpython_enterprise"                            : {"linux:amd64:jdk-latest" : daily      + t("08:00:00"),
                "job_type:checkup"                                      : {"linux:amd64:jdk-latest" : on_demand  + t("08:00:00")}
            },
            "vm_name:graalpython_enterprise_multi"                      : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
            "vm_name:graalpython_enterprise_panama"                     : {"linux:amd64:jdk-latest" : on_demand  + t("08:00:00")},
            "vm_name:cpython"                                           : {"linux:amd64:jdk-latest" : monthly    + t("04:00:00")},
            "vm_name:pypy"                                              : {"linux:amd64:jdk-latest" : on_demand    + t("04:00:00")},
        }),
        for bench in ["micro_native"]
    } + {
        // "small" benchmarks have their argument set such that they run in a resonable
        // time in the interpreter and they are used for interpreter benchmarking
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ce_default_interpreter"                    : {"linux:amd64:jdk-latest" : on_demand  + t("02:00:00")},
            "vm_name:graalvm_ee_default_interpreter"                    : {"linux:amd64:jdk-latest" : daily      + t("02:00:00")},
            "vm_name:graalpython_core_interpreter"                      : {"linux:amd64:jdk-latest" : on_demand  + t("02:00:00")},
            "vm_name:graalpython_core_native_interpreter"               : {"linux:amd64:jdk-latest" : on_demand  + t("02:00:00")},
            "vm_name:graalpython_enterprise_interpreter"                : {"linux:amd64:jdk-latest" : weekly     + t("02:00:00")},
            "vm_name:graalpython_core_interpreter_multi"                : {"linux:amd64:jdk-latest" : on_demand  + t("02:00:00")},
            "vm_name:graalpython_core_native_interpreter_multi"         : {"linux:amd64:jdk-latest" : on_demand  + t("02:00:00")},
            "vm_name:cpython"                                           : {"linux:amd64:jdk-latest" : weekly     + t("02:00:00")},
        }),
        for bench in ["micro_small", "meso_small"]
    } + {
        [bench + "-bytecode-dsl"]: bench_task(bench) + bytecode_dsl_bench + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ee_default_interpreter_bc_dsl"                    : {"linux:amd64:jdk-latest" : daily     + t("04:00:00")},
            "vm_name:graalpython_enterprise_interpreter_bc_dsl"                : {"linux:amd64:jdk-latest" : weekly    + t("04:00:00")},
        }),
        for bench in ["micro_small", "meso_small"]
    } + {
        // benchmarks executed via Java embedding driver
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:java_embedding_core_interpreter_multi_shared"      : {"linux:amd64:jdk-latest" : weekly     + t("02:00:00")},
        }),
        for bench in ["java_embedding_meso"]
    } + {
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalpython_core"                                  : {"linux:amd64:jdk-latest" : on_demand      + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_enterprise"                            : {"linux:amd64:jdk-latest" : daily      + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ce_default"                                : {"linux:amd64:jdk-latest" : on_demand      + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ee_default"                                : {"linux:amd64:jdk-latest" : daily      + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_core_multi_tier"                       : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_enterprise_multi_tier"                 : {"linux:amd64:jdk-latest" : weekly     + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ce_default_multi_tier"                     : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ee_default_multi_tier"                     : {"linux:amd64:jdk-latest" : weekly     + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_core_3threads"                         : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_enterprise_3threads"                   : {"linux:amd64:jdk-latest" : weekly     + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ce_default_3threads"                       : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ee_default_3threads"                       : {"linux:amd64:jdk-latest" : weekly     + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_core_multi_tier_3threads"              : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_enterprise_multi_tier_3threads"        : {"linux:amd64:jdk-latest" : weekly     + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ce_default_multi_tier_3threads"            : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalvm_ee_default_multi_tier_3threads"            : {"linux:amd64:jdk-latest" : weekly     + t("05:00:00") + forks_warmup},
            "vm_name:pypy"                                              : {"linux:amd64:jdk-latest" : on_demand    + t("01:00:00")},
        }),
        for bench in ["warmup"]
    } + {
        [bench + "-bytecode-dsl"]: bench_task(bench) + bytecode_dsl_bench + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ee_default_bc_dsl"                                : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
            "vm_name:graalpython_enterprise_bc_dsl"                            : {"linux:amd64:jdk-latest" : on_demand     + t("05:00:00") + forks_warmup},
        }),
        for bench in ["warmup"]
    } + {
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ee_default_interpreter"                    : {"linux:amd64:jdk-latest" : post_merge     + t("02:00:00") + need_pgo},
            "vm_name:graalpython_enterprise_interpreter"                : {"linux:amd64:jdk-latest" : weekly         + t("02:00:00")},
            "vm_name:cpython"                                           : {"linux:amd64:jdk-latest" : weekly         + t("01:00:00")},
        }),
        for bench in ["heap", "micro_small_heap"]
    } + {
        [bench + "-bytecode-dsl"]: bench_task(bench) + bytecode_dsl_bench + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ee_default_interpreter_bc_dsl"             : {"linux:amd64:jdk-latest" : post_merge     + t("02:00:00") + need_bc_pgo},
            "vm_name:graalpython_enterprise_interpreter_bc_dsl"         : {"linux:amd64:jdk-latest" : weekly         + t("02:00:00")},
        }),
        for bench in ["heap", "micro_small_heap"]
    } + {
        // interop benchmarks only for graalpython, weekly is enough
        [bench]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:java_jmh_core"                                  : {"linux:amd64:jdk-latest" : daily     + t("04:00:00")},
            "vm_name:java_jmh_enterprise"                            : {"linux:amd64:jdk-latest" : daily     + t("04:00:00")},
        }),
        for bench in ["jmh"]
    } + {
        // benchmarks with many forks for weekly performance reports
        [bench + "-forks"]: bench_task(bench) + platform_spec(no_jobs) + bench_variants({
            "vm_name:graalvm_ce_default"                                : {"linux:amd64:jdk-latest" : on_demand     + t("10:00:00") + forks_meso},
            "vm_name:graalvm_ee_default"                                : {"linux:amd64:jdk-latest" : weekly     + t("10:00:00") + forks_meso},
        }),
        for bench in ["meso"]
    } + {
        // benchmarks with community benchmark suites for external numbers
        [bench]: bench_task(bench, PY_BENCHMARKS) + platform_spec(no_jobs) + raw_results + bench_variants({
            "vm_name:graalpython_core"                                  : {"linux:amd64:jdk-latest" : on_demand     + t("08:00:00")},
            "vm_name:graalpython_enterprise"                            : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
            "vm_name:graalvm_ce_default"                                : {"linux:amd64:jdk-latest" : on_demand     + t("08:00:00")},
            "vm_name:graalvm_ee_default"                                : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
            "vm_name:cpython_launcher"                                  : {"linux:amd64:jdk-latest" : monthly     + t("08:00:00")},
            "vm_name:pypy_launcher"                                     : {"linux:amd64:jdk-latest" : on_demand     + t("08:00:00")},
        }),
        for bench in ["pyperformance"]
    } + {
        // Bytecode DSL benchmarks with community benchmark suites for external numbers
        [bench + "-bytecode-dsl"]: bench_task(bench, PY_BENCHMARKS) + bytecode_dsl_bench + platform_spec(no_jobs) + raw_results + bench_variants({
            "vm_name:graalvm_ee_default_bc_dsl"                                : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
        }),
        for bench in ["pyperformance"]
    } + {
        // benchmarks with community benchmark suites for external numbers
        [bench]: bench_task(bench, PY_BENCHMARKS) + platform_spec(no_jobs) + raw_results + bench_variants({
            "vm_name:graalpython_core"                                  : {"linux:amd64:jdk-latest" : on_demand     + t("08:00:00")},
            "vm_name:graalpython_core_panama"                           : {"linux:amd64:jdk-latest" : on_demand  + t("08:00:00")},
            "vm_name:graalpython_enterprise"                            : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
            "vm_name:graalpython_enterprise_panama"                     : {"linux:amd64:jdk-latest" : on_demand  + t("08:00:00")},
            "vm_name:graalvm_ce_default"                                : {"linux:amd64:jdk-latest" : on_demand     + t("08:00:00")},
            "vm_name:graalvm_ee_default"                                : {"linux:amd64:jdk-latest" : weekly     + t("08:00:00")},
            "vm_name:cpython_launcher"                                  : {"linux:amd64:jdk-latest" : monthly     + t("08:00:00")},
            "vm_name:pypy_launcher"                                     : {"linux:amd64:jdk-latest" : on_demand     + t("08:00:00")},
        }),
        for bench in ["numpy", "pandas"]
    },

    // -----------------------------------------------------------------------------------------------------------------
    //
    // export builds
    //
    // -----------------------------------------------------------------------------------------------------------------
    processed_gate_builds::run_spec.process(gate_task_dict),
    processed_bench_builds::run_spec.process(bench_task_dict),

    builds: utils.ensure_no_mx_wrong_build(
        utils.ensure_tier_time_and_machine_limits(
            utils.with_notify_groups([
                {'defined_in': std.thisFile} + b for b in self.processed_gate_builds.list + self.processed_bench_builds.list
            ])
        )
    ) + [
        {
            name: "graalpy-website-build",
            targets: ["tier1"],
            guard: {
                includes: ["docs/user/**", "docs/site/**"],
            },
            timelimit: "20:00",
            capabilities: ["linux", "amd64"],
            docker: {
                image: "buildslave_ol7",
                mount_modules: true,
            },
            packages: {
                ruby: "==3.2.2",
                libyaml: "==0.2.5",
                mx: "7.34.1",
                python3: "==3.8.10",
            },
            environment: {
                JEKYLL_ENV: "production",
                BUNDLE_PATH: "$PWD/../bundle-path",
                GEM_HOME: "$PWD/../gem-home",
                CI: "true",
            },
            run: [
                ["mkdir", "-p", "$GEM_HOME"],
                ["export", "PATH=$GEM_HOME/bin:$PATH"],
                ["gem", "install", "--no-document", "--source", $.overlay_imports.RUBYGEMS_MIRROR, "bundler", "-v", "2.5.9"],
                ["git", "clone", "-b", "main", $.overlay_imports.JEKYLL_THEME_GIT],
                ["cd", "graal-languages-jekyll-theme"],
                ["gem", "build", "graal-languages-jekyll-theme.gemspec"],
                ["mkdir", "-p", "../docs/site/vendor/cache"],
                ["cp", "graal-languages-jekyll-theme-*.gem", "../docs/site/vendor/cache"],
                ["cd", "../docs/site"],
                ["bundle", "install"],
                ["bundle", "exec", "jekyll", "build"],
            ],
            publishArtifacts: [
                {
                    name: "graalpy-website-build-artifact",
                    dir: "docs/site/_site",
                    patterns: ["*"],
                }
            ],
        },
        {
            name: "graalpy-website-deploy-staging",
            targets: ["deploy"],
            capabilities: ["linux", "amd64"],
            requireArtifacts: [
                {
                    name: "graalpy-website-build-artifact",
                    dir: "_site",
                }
            ],
            run: [
                $.overlay_imports.STAGING_DEPLOY_CMD
            ],
        },
        {
            name: "graalpy-website-deploy-production",
            targets: ["deploy"],
            capabilities: ["linux", "amd64"],
            packages: {
                mx: "7.34.1",
                python3: "==3.8.10",
            },
            requireArtifacts: [
                {
                    name: "graalpy-website-build-artifact",
                    dir: "_site",
                }
            ],
            run: [
                ["git", "clone", $.overlay_imports.WEBSITE_GIT],
                ["rsync", "-a", "--delete", "_site/", "graalvm-website/python"],
                ["git", "-C", "graalvm-website", "add", "."],
                ["git", "-C", "graalvm-website", "status"],
                ["git", "-C", "graalvm-website", "-c", "user.name=Web Publisher", "-c", "user.email=graalvm-dev@oss.oracle.com", "commit", "-m", "Update GraalPy website"],
                ["git", "-C", "graalvm-website", "push", "origin", "HEAD"],
                ["git", "branch", "--force", "--no-track", "published"],
                ["git", "push", "--force", "origin", "published"],
            ]
        },
    ],
}

// Local Variables:
// jsonnet-indent-level: 4
// smie-indent-basic: 4
// End:
