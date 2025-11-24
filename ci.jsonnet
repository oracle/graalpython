// python-gate.libsonnet and python-bench.libsonnet reference overlay_imports
// via the global reference `$', so to make this work with the CI overlay
// mechanism we import them like this into the main object instead of loading
// them into a local.
(import "ci/python-gate.libsonnet") +
(import "ci/python-bench.libsonnet") +
{
    overlay: "907f84a98fe5736824d2964e8815d75d0511d39d",
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

    local patch_check            = base_gate + task_spec({
        // Runs patch verifier as part of the copyright check
        run: [
            ["mx", "python-checkcopyrights"],
        ],
        setup: [],
    }),

    local gate_task_dict = {
        "patch-check": patch_check + platform_spec(no_jobs) + platform_spec({
            "linux:amd64:jdk-latest"     : tier1,
        }),
    },

    processed_gate_builds::run_spec.process(gate_task_dict),

    builds: utils.ensure_no_mx_wrong_build(
        utils.ensure_tier_time_and_machine_limits(
            utils.with_notify_groups([
                {'defined_in': std.thisFile} + b for b in self.processed_gate_builds.list
            ])
        )
    ),
}

// Local Variables:
// jsonnet-indent-level: 4
// smie-indent-basic: 4
// End:
