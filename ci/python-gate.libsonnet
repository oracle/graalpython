{
    local common                = import "graal/ci/common.jsonnet",
    local common_util           = import "graal/ci/ci_common/common-utils.libsonnet",
    local run_spec              = import "graal/ci/ci_common/run-spec.libsonnet",
    local tools                 = import "graal/ci/ci_common/run-spec-tools.libsonnet",
    local pygate                = import "python-gate.libsonnet",
    local utils                 = import "utils.libsonnet",
    local const                 = import "constants.libsonnet",
    local reports               = $.overlay_imports.reports,

    local exclude               = run_spec.exclude,
    local task_spec             = run_spec.task_spec,
    local add_multiply          = run_spec.add_multiply,
    local platform_spec         = run_spec.platform_spec,
    local evaluate_late         = run_spec.evaluate_late,

    local jdt                   = task_spec(common.deps.jdt),
    local pylint                = task_spec(common.deps.pylint),
    local svm                   = task_spec(common.deps.svm),
    local test_reports          = task_spec(reports),


    // -----------------------------------------------------------------------------------------------------------------
    //
    // platform / jdk setup
    //
    // -----------------------------------------------------------------------------------------------------------------
    local jdk_name_to_dict = {[edition]: {
        "jdk21"+: common.jdks["labsjdk-" + edition + "-21"],
        "jdk-latest"+: common.jdks["labsjdk-" + edition + "-latest"],
    } for edition in ['ce', 'ee']},

    local jdk_name_to_devkit_suffix = function(name)
        if name == "jdk-latest" then "jdkLatest" else name,

    local default_os_arch = function(jdk_name, edition) {
        linux+: {
            amd64 +: common.linux_amd64,
            aarch64 +: common.linux_aarch64,
        },
        darwin +: {
            amd64 +: common.darwin_amd64 + {
                capabilities +: ["ram32gb"],
            },
            aarch64 +: common.darwin_aarch64 + {
                capabilities +: ["darwin_bigsur"],
            },
        },
        windows +: {
            amd64 +: common.windows_amd64 + {
                capabilities +: ["windows_server_2016"],
                packages+: common.devkits["windows-" + jdk_name_to_devkit_suffix(jdk_name)].packages
            }
        },
    },
    os_arch_jdk_mixin:: task_spec(run_spec.evaluate_late({
        // this starts with _ on purpose so that it will be evaluated first
        "_1_os_arch_jdk": function(b)
            local edition = if (std.objectHasAll(b, 'graalvm_edition')) then b.graalvm_edition else 'ce';
            tools.check_no_timelimit(jdk_name_to_dict[edition][b.jdk] + default_os_arch(b.jdk, edition)[b.os][b.arch])
    })),

    //------------------------------------------------------------------------------------------------------------------
    // control run-spec composition
    //------------------------------------------------------------------------------------------------------------------
    all_jobs:: {
        "windows:aarch64"+: exclude,
        "*:*:jdk19"+: exclude,
        "*:*:jdk21"+: exclude,
    },

    no_jobs:: $.all_jobs {
        "<all-os>"+: exclude,
    },

    //------------------------------------------------------------------------------------------------------------------
    // downloads
    //------------------------------------------------------------------------------------------------------------------
    local DOWNLOADS = {
        common: {
            GRADLE_JAVA_HOME: common.jdks_data["oraclejdk21"],
        },
        linux: {
            common: {
                LIBGMP: {name:"libgmp", version:"6.1.0", platformspecific:true},
                NUMPY_BENCHMARKS_DIR: {name: "numpy", version: "1.26.4", platformspecific: false},
                PYPY_HOME: {name: "pypy3", version: "3.10-v7.3.12", platformspecific: true},
                PYPY_BENCHMARKS_DIR: {name: "pypybenchmarks", version: "84f401a8f55a", platformspecific: false},
            },
            amd64: {
                MUSL_TOOLCHAIN: {name: "musl-toolchain", version: "1.0", platformspecific: true},
                PYTHON3_HOME: {name: "python3", version: "3.12.8", platformspecific: true},
            },
            aarch64: {},
        },
        darwin: {
            common: {},
            amd64: {},
            aarch64: {},
        },
        windows: {
            common: {
                MAVEN_HOME: {name: 'maven', version: '3.9.10', platformspecific: false},
            },
            amd64: {},
            aarch64: {},
        },
    },

    // This is the diff to 'DOWNLOADS' and meant to be used on OL8 images.
    // Use it as diff argument to function 'downloads'.
    local DOWNLOADS_DIFF_OL8 = {
        linux: {
            amd64: {
                // Remove Python3 download because this is built for OL7 and
                // does not work on OL8.
                PYTHON3_HOME: null,
            },
        },
    },

    local DOWNLOADS_DIFF = {
        buildslave_ol8: DOWNLOADS_DIFF_OL8,
    },

    //------------------------------------------------------------------------------------------------------------------
    // environment
    //------------------------------------------------------------------------------------------------------------------
    local ENVIRONMENT = {
        common: {
            PATH: "$PATH",
            HOST_VM: "server",
            HOST_VM_CONFIG: "graal-core",
            BENCH_RESULTS_FILE_PATH: "bench-results.json",
            GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
            MX_PYTHON_VERSION: "3.8",
            MX_OUTPUT_ROOT_INCLUDES_CONFIG: "false", // this is important so we can build things on JDK-latest and run them on older JDKs
            CI: "true",
            BISECT_BENCHMARK_CONFIG: "bisect-benchmark.ini",
            BISECT_EMAIL_FROM: $.overlay_imports.BISECT_EMAIL_FROM,
            BISECT_EMAIL_SMTP_SERVER: $.overlay_imports.BISECT_EMAIL_SMTP_SERVER,
            BISECT_EMAIL_TO_PATTERN: ".*@oracle.com",
            TRUFFLE_STRICT_OPTION_DEPRECATION: "true",
            npm_config_registry: $.overlay_imports.npm_config_registry,
        },
        linux: {
            common: ENV_POSIX + {},
            amd64: {},
            aarch64: {},
        },
        darwin: {
            common: ENV_POSIX + {
                LC_CTYPE: "en_US.UTF-8",
                PATH: utils.path_combine(ENVIRONMENT.common.PATH, "$PYTHON3_HOME:$MUSL_TOOLCHAIN/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/opt/homebrew/bin:/opt/homebrew/sbin"),
            },
            amd64: {},
            aarch64: {},
        },
        windows: {
            common: {
                PATH: "$MAVEN_HOME\\bin;$PATH",
            },
            amd64: {},
            aarch64: {},
        },
    },

    local ENV_POSIX = {
        CPPFLAGS: "-I$LIBGMP/include",
        LD_LIBRARY_PATH: "$LIBGMP/lib:$LLVM/lib:$LD_LIBRARY_PATH",
        FORK_COUNTS_DIRECTORY: "$BUILD_DIR/benchmarking-config/fork-counts",
        RODINIA_DATASET_ZIP: $.overlay_imports.RODINIA_DATASET_ZIP,
        PATH: utils.path_combine(ENVIRONMENT.common.PATH, "$PYTHON3_HOME:$MUSL_TOOLCHAIN/bin:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin"),
    },

    // This is the diff to 'ENVIRONMENT' and meant to be used on OL8 images.
    // Use it as diff argument to function 'downloads'.
    local ENVIRONMENT_DIFF_OL8 = {
        linux: {
            common: {
                // On OL8, we don't use our own build of Python 3.10.8 because that is built on OL7
                PATH: utils.path_combine(ENVIRONMENT.common.PATH, "$PATH:$MUSL_TOOLCHAIN/bin"),
            },
        },
        darwin: {
            common: {
                PATH: utils.path_combine(ENVIRONMENT.common.PATH, "$PATH:$MUSL_TOOLCHAIN/bin"),
            },
        },
    },

    local ENVIRONMENT_DIFF = {
        buildslave_ol8: ENVIRONMENT_DIFF_OL8,
    },

    //------------------------------------------------------------------------------------------------------------------
    // packages
    //------------------------------------------------------------------------------------------------------------------
    local PACKAGES = {
        common: {},
        linux: {
            common: {
                "00:devtoolset": "==7",
                "01:binutils": ">=2.34",
                bzip2: ">=1.0.6",
                cmake: ">=3.22.2",
                zlib: ">=1.2.11",
                lcov: ">=1.11",
                libffi: ">=3.2.1",
                llvm: "==8.0.0",
                maven: ">=3.3.9",
                curl: '==7.50.1',
            },
            amd64: {},
            aarch64: {},
        },
        darwin: {
            common: {
                coreutils: "",
                maven: ">=3.3.9",
            },
            amd64: {},
            aarch64: {},
        },
        windows: {
            common: {
                maven: ">=3.3.9",
            },
            amd64: {},
            aarch64: {},
        },
    },

    //------------------------------------------------------------------------------------------------------------------
    // logs
    //------------------------------------------------------------------------------------------------------------------
    local LOGS = [
        "dumps/*/*",
        "graal_dumps/*/*",
        "bench-results.json",
        "raw-results.json",
    ],

    //------------------------------------------------------------------------------------------------------------------
    // lib exports
    //------------------------------------------------------------------------------------------------------------------
    target(t):: task_spec({
        target:: t,
    }),

    timelimit(limit):: task_spec({timelimit: limit}),

    local tierConfig = {
      "tier1": "gate",
      "tier2": "gate",
      "tier3": "gate",
    },
    tierConfig: tierConfig,
    tier1:: $.target("tier1"),
    tier2:: $.target("tier2"),
    tier3:: $.target("tier3"),
    post_merge:: $.target("post-merge") + task_spec({name_target:: "post_merge"}),

    bench:: $.target("bench"),
    on_demand:: $.target("ondemand") + task_spec({name_target:: "on_demand"}),
    daily:: $.target("daily"),
    weekly:: $.target("weekly"),
    monthly:: $.target("monthly"),

    provide_graalpy_standalone_artifact(name):: task_spec(evaluate_late(
        // use 2 after _ to make sure we evaluate this right after _1 late eval keys like _1_os_arch_jdk
        "_2_provide_artifact", {
            local os = self.os,
            local arch = if self.arch == "amd64" then "" else "_" + self.arch,
            local jdk_version = self.jdk_version,
            local artifact_name = name + os + arch,
            local capabilities = if (self.os == "darwin" && self.arch != "aarch64") then
                    // for darwin, amd64: set minimum requirement to bigsur
                    [c for c in super.capabilities if !std.startsWith(c, "darwin")] + ["darwin_bigsur"]
                else
                    super.capabilities,
            capabilities: capabilities,
            local pattern = if (std.startsWith(artifact_name, "graalpy")) then
                    // We are matching something like: mxbuild/linux-amd64/GRAALPY_NATIVE_STANDALONE
                    "main/mxbuild/*/GRAALPY_*_STANDALONE"
                else
                    // We are matching something like: mxbuild/linux-amd64/GRAALVM_COMMUNITY_JAVA24/graalvm-community-openjdk-24.0.0-dev-linux-amd64
                    "graal/sdk/mxbuild/*/GRAALVM_COMMUNITY_JAVA" + jdk_version + "/graalvm-community-*",
            publishArtifacts+: [{
                "dir": "../",
                "name": artifact_name,
                "patterns": [pattern]
            }]
        })
    ),
    provide:: $.provide_graalpy_standalone_artifact,

    require_graalpy_standalone_artifact(name):: task_spec({
        local os = self.os,
        local arch = if self.arch == "amd64" then "" else "_" + self.arch,
        local jdk_version = self.jdk_version,
        local artifact_name = name + os + arch,
        setup+: [
            [".github/scripts/unpack-artifact", artifact_name],
            if (std.startsWith(artifact_name, "graalpy")) then
                [".github/scripts/set-export", "GRAALPY_HOME", "mxbuild/*/GRAALPY_*_STANDALONE"]
            else 
                [".github/scripts/set-export", "GRAAL_JDK_HOME", "../graal/sdk/mxbuild/*/GRAALVM_COMMUNITY_JAVA" + jdk_version + "/graalvm-community-*"]
        ],
        environment+: {
            MX_BUILD_SHALLOW_DEPENDENCY_CHECKS: "true",
        },
        requireArtifacts+: [{
            name: artifact_name,
            autoExtract: false,
            dir: "../",
        }],
    }),        
    require:: $.require_graalpy_standalone_artifact,

    docker(name="buildslave_ol7"):: task_spec(evaluate_late(
        // use 2 after _ to make sure we evaluate this right after _1 late eval keys like _1_os_arch_jdk
        "_2_docker", {
            docker: {
                image: name,
                mount_modules: true,
            },
        })
    ),
    ol7:: $.docker(name="buildslave_ol7"),
    ol8:: $.docker(name="buildslave_ol8") +
     task_spec({
         downloads: $.downloads(self.os, self.arch, self.docker),
         environment: $.environment(self.os, self.arch, self.docker),
     }),

    batches(num):: add_multiply([
        task_spec({
            environment+: {
                TAGGED_UNITTEST_PARTIAL: "%d/%d" % [i, num],
            },
            variations+::["batch" + i]
        }),
        for i in std.range(1, num)
    ]),

    local get_or_default(o, f, default={}) =
        if f != null && std.objectHas(o, f) then o[f] else default,

    // Combines a diff and calls std.prune to remove empty values
    local apply_diff(o, diff) =
        std.prune(o + diff),

    local get(o, os, arch, diff={}) =
        local common_all = apply_diff(get_or_default(o, "common"), get_or_default(diff, "common"));
        if std.objectHas(o, os) then
            local common_os = apply_diff(get_or_default(o[os], "common"), get_or_default(get_or_default(diff, os), "common"));
            local common_arch = apply_diff(get_or_default(o[os], arch), get_or_default(get_or_default(diff, os), arch));
            common_all + common_os + common_arch
        else
            common_all,

    downloads(os, arch, docker={})::
        get(DOWNLOADS, os, arch, get_or_default(DOWNLOADS_DIFF, get_or_default(docker, "image", ""))),

    environment(os, arch, docker={})::
        get(ENVIRONMENT, os, arch, get_or_default(ENVIRONMENT_DIFF, get_or_default(docker, "image", ""))),

    packages(os, arch)::
        get(PACKAGES, os, arch),

    local eclipse = task_spec(evaluate_late({
        // late evaluation of the eclipse mixin, conditional import based on platform
        // eclipse downloads are not provided for aarch64
        "eclipse": function(builder)
            local arch = builder.arch;
            if arch == "aarch64" then
                {}
            else
                common.deps.eclipse
    })),

    logs(os, arch):: LOGS,

    //------------------------------------------------------------------------------------------------------------------
    // graalpy gates
    //------------------------------------------------------------------------------------------------------------------
    local base_gate = $.os_arch_jdk_mixin + task_spec({
        // private
        tags:: self.task_name,
        task_name:: null,
        os:: null,
        arch:: null,
        primary_suite_path:: null,
        jdk:: null,
        graalvm_edition:: 'ce',
        target:: null,
        variations::[],
        dynamic_imports:: [],
        mx_parameters:: [],
        gate_parameters:: [],
        assert std.type(self.dynamic_imports) == "array" : "dynamic_imports must be an array",
        dy:: if std.length(self.dynamic_imports) > 0 then ["--dynamicimports", std.join(",", self.dynamic_imports)] else [],
        primary_suite:: if self.primary_suite_path != null then ["-p", self.primary_suite_path] else [],
        all_suites:: if self.primary_suite_path != null then ["--all-suites"] else [],
        name_target:: null,
        name_suffix +:: [],
        name_prefix +:: [],
        _name_target:: if self.name_target == null then self.target else self.name_target,
        // public
        capabilities: [],
        local full_task_name = std.prune(self.name_prefix + [self.task_name] + self.name_suffix),
        name: std.join("-", std.prune(full_task_name + [if std.objectHas(tierConfig, self._name_target) then tierConfig[self._name_target] else self._name_target] + self.variations + [self.os, self.arch, self.jdk])),
        python_version: "3.8",
        targets: [self.target],
        logs+: $.logs(self.os, self.arch),
        // all gates share the same base set of downloads
        downloads+: $.downloads(self.os, self.arch),
        // all gates share the same base environment
        environment+: $.environment(self.os, self.arch),
        packages+: $.packages(self.os, self.arch),
        run+: [
            ["mx"] + self.mx_parameters + self.dy + self.primary_suite + [
                "--strict-compliance", "--primary", "gate", "--tags", self.tags, "-B=--force-deprecation-as-warning",
            ] + self.all_suites + self.gate_parameters,
        ],
        deploysArtifacts: true,
    }),

    cpython_gate:: base_gate + test_reports + task_spec({
        tags:: "python-unittest-cpython",
        guard: {
            "includes": [
                "graalpython/com.oracle.graal.python.test/src/tests/**",
                "graalpython/com.oracle.graal.python.test/src/graalpytest.py",
                "mx.graalpython/mx_graalpython.py",
                "ci.jsonnet",
            ],
        },
    }),

    local jdk_spec = task_spec(evaluate_late({
        "select_graalvm_if_older_jdk": function (b)
            if b.jdk_version < common.jdks["labsjdk-ee-latest"].jdk_version then
                {
                    downloads+: {
                        LATEST_JAVA_HOME: common.jdks_data["labsjdk-ce-latest"],
                    }
                } +
                common.jdks["graalvm-ee-" + b.jdk_version] +
                {
                    dynamic_imports+:: [],
                    environment+: {
                        GRAAL_JDK_HOME: "$JAVA_HOME"
                    },
                }
            else
                {}
    })),

    graalpy_gate:: base_gate + test_reports + pylint + svm + jdk_spec + task_spec({
        guard+: {
            excludes+: ["**.md", "docs/**", "3rd_party_licenses.txt", "scripts/**"],
        },
        setup+: [
            // force imports the main repository to get the right graal commit
            ["mx"] + self.mx_parameters + ["sforceimports"],
            // logging
            ["mx"] + self.mx_parameters + self.dy + ["sversions"],
        ],
        on_success+: [
            ["rm", "-rf", "graal_dumps"],
        ],
    }),

    graalpy_ee_gate:: $.graalpy_gate + task_spec({
        setup+: [
            // NOTE: logic shared with ci/python-bench.libsonnet, keep in sync
            ["git", "clone", $.overlay_imports.GRAAL_ENTERPRISE_GIT, "../graal-enterprise"],
            // checkout the matching revision of graal-enterprise repository based on the graal/compiler checkout
            ["mx", "--quiet", "--dy", "/graal-enterprise", "checkout-downstream", "compiler", "graal-enterprise", "--no-fetch"],
            // force imports with the env, which may clone other things (e.g. substratevm-enterprise-gcs)
            ["mx", "--env", "native-ee", "sforceimports"],
            // force imports the main repository to get the right graal commit
            ["mx", "sforceimports"],
            // logging
            ["mx", "--env", "native-ee", "sversions"],
        ],
    }),

    graalpy_maven_gate:: $.graalpy_gate + task_spec({
        primary_suite_path: '../graal/vm',
        dynamic_imports+:: ["/graalpython"],
    }),

    graalpy_eclipse_gate:: $.graalpy_gate + eclipse + jdt,

    unittest_retagger_gate:: $.graalpy_gate + task_spec({
        environment+: {
            GRAALPYTEST_FAIL_FAST: "false",
        },
        run: [
            [
                "mx", "graalpytest", "--svm",
                "--tagged", "--all", "--continue-on-collection-errors", ".",
                # More workers doesn't help, the job is bottlenecked on all the timeouts in test_asyncio
                "-n", "6",
                # The default timeout is very generous to allow for infrastructure flakiness,
                # but we don't want to auto tag tests that take a long time
                "--timeout-factor", "0.3",
                "--mx-report", "report.json",
                "--exit-success-on-failures",
            ],
        ],
        logs+: ["report.json"],
    }),

    coverage_gate:: $.graalpy_gate + task_spec({
        coverage_args:: null,
        assert std.type(self.coverage_args) == "array" : "coverage_args must be an array",
        setup+: [
            ['set-export', 'MAIN_COMMIT_TS', ['git', 'show', '-s', "--format=%ct", "${MAIN_REVISION}"]],
            ['set-export', 'GRAAL_COMMIT', ['python', '-c', 'import sys;from urllib.request import urlopen; print(urlopen(sys.argv[1]).read().decode("ascii"))',
                                            $.overlay_imports.BUILDBOT_COMMIT_SERVICE + '?repoName=graal&target=weekly&before-ts=${MAIN_COMMIT_TS}']],
            ["git", "clone", $.overlay_imports.GRAAL_ENTERPRISE_GIT, "../graal-enterprise"],
            ['git', '-C', '../graal', 'checkout', '${GRAAL_COMMIT}'],
            ['mx', '-p', '../graal/vm', '--dynamicimports', 'graalpython', 'sforceimports'],
            // NOTE: jvm-only, so not need to handle substratevm-enterprise-gcs
            ['mx', '-p', '../graal-enterprise/vm-enterprise', 'checkout-downstream', 'vm', 'vm-enterprise'],
            ['git', 'checkout', '${MAIN_REVISION}'],
        ],
        run: [
            ['mx', 'python-coverage'] + self.coverage_args,
        ],
        teardown+: [
            ['mx', 'sversions', '--print-repositories', '--json', '|', 'coverage-uploader.py', '--associated-repos', '-'],
        ],
    }),
    coverage_gate_jacoco:: $.coverage_gate + task_spec({
        tags:: null,
        coverage_args:: ["jacoco", "--tags", std.join(',', self.tags)],
    }),
    cov_jacoco_gate_tagged:: $.coverage_gate_jacoco + task_spec({
        tags:: ["python-tagged-unittest"],
    }),
    cov_jacoco_gate_base:: $.coverage_gate_jacoco + task_spec({
        tags:: [
            "python-junit",
            "python-unittest",
            "python-unittest-multi-context",
            "python-unittest-jython",
        ] + if self.os != 'windows' then ["python-unittest-hpy"] else [],
    }),
    cov_truffle_gate:: $.coverage_gate + task_spec({
        coverage_args:: ["truffle"],
    }),

    watchdog:: $.graalpy_gate + task_spec({
        name: "corp-compliance-watchdog",
        packages +: {
            "nodejs": "==8.9.4"
        },
        environment +: {
            npm_config_registry: $.overlay_imports.npm_config_registry,
        },
        setup +: [
            ["git", "clone", "--depth", "1", $.overlay_imports.WATCHDOG_GIT, "../watchdog"],
            ["git", "clone", "--depth", "1", $.overlay_imports.COMPLIANCE_GIT, "../corporate-compliance"],
            ["mx"] + self.mx_parameters + self.dy + self.primary_suite + ["build"],
        ],
        run: [
            ["cd", "../watchdog"],
            ["npm", "install"],
            ["node", "watchdog.js", "--probe", "${PWD}", "--config", "../corporate-compliance/graalpython.xml"],
        ]
    }),

    local base_style_gate = $.graalpy_eclipse_gate + task_spec({
        dynamic_imports:: ["/truffle"],
        downloads +: {
            EXTRA_JAVA_HOMES: common.jdks_data["labsjdk-ce-21"],
        },
        packages +: {
          "pip:pylint": "==2.4.4",
        }
    }),
    style_gate:: base_style_gate,
}

// Local Variables:
// jsonnet-indent-level: 4
// smie-indent-basic: 4
// End:
