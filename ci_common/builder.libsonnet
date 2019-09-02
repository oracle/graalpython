local utils = import 'utils.libsonnet';
local const = import 'constants.libsonnet';
local mixins = import 'mixins.libsonnet';

{
    // ------------------------------------------------------------------------------------------------------
    //
    // the build templates
    //
    // ------------------------------------------------------------------------------------------------------
    local base = {
        downloads: {},
        environment: {},
        setup: [],
        logs: ["dumps/*/*"],
        timelimit: const.TIME_LIMIT["30m"],
        packages: {},
        capabilities: [],
        name: null,
        targets: [],
        run: [],
    },
    base:: base,

    local common = base + mixins.labsjdk8 + {
        dynamicImports:: "/compiler",

        environment +: {
            MX_PYTHON_VERSION: "3",
        },

        setup +: [
            ["mx", "sforceimports"],
            ["mx", "--dynamicimports", self.dynamicImports, "build"],
        ]
    },
    common:: common,

    // ------------------------------------------------------------------------------------------------------
    //
    // the gate templates
    //
    // ------------------------------------------------------------------------------------------------------
    local baseGate = common + {
        tags: "tags must be defined",

        // local truffleDebugFlags = utils.graalOption("TraceTruffleCompilation", "true"),
        // local truffleDebugFlags = utils.graalOption("TraceTruffleCompilationDetails", "true"),
        local truffleDebugFlags = [],
        targets: const.TARGET.gate,
        run +: [
            ["mx"] +
            truffleDebugFlags +
            ["--strict-compliance", "--dynamicimports", super.dynamicImports, "--primary", "gate", "--tags", self.tags, "-B=--force-deprecation-as-warning-for-dependencies"],
        ]
    },
    baseGate:: baseGate,

    local baseGraalGate = baseGate + mixins.sulong + mixins.graalCore,
    baseGraalGate:: baseGraalGate,

    // specific gates
    testGate(type, platform)::
        baseGraalGate + {tags:: "python-"+type} + mixins.getPlatform(platform) + {name: "python-"+ type +"-"+platform},

    testGateTime(type, platform, timelimit)::
        baseGraalGate + {tags:: "python-"+type} + mixins.getPlatform(platform) + {name: "python-"+ type +"-"+platform} + {timelimit: timelimit},

    local baseStyleGate = baseGraalGate + mixins.eclipse + mixins.linux + {
        tags:: "style",
        name: "python-style",

        timelimit: const.TIME_LIMIT["1h"],
    },
    baseStyleGate:: baseStyleGate,

    local styleGate = baseStyleGate + {
        tags:: super.tags + ",fullbuild,python-license",
    },
    styleGate:: styleGate,

    local graalVmGate = baseGraalGate + mixins.linux {
        tags:: "python-graalvm",
        name: "python-graalvm",

        timelimit: const.TIME_LIMIT["1h"],
    },
    graalVmGate:: graalVmGate,

    // ------------------------------------------------------------------------------------------------------
    //
    // the deploy templates
    //
    // ------------------------------------------------------------------------------------------------------
    deployGate(platform)::
        base + mixins.graalCore + mixins.sulong + mixins.getPlatform(platform) + {
            targets: const.TARGET.postMerge,
            setup +: [
                ["mx", "sversions"],
                ["mx", "build", "--force-javac"],
            ],
            run +: [
                ["mx", "deploy-binary-if-master", "python-public-snapshots"],
            ],
            name: "deploy-binaries-" + platform,
        },

    coverageGate::
        common + mixins.getPlatform(platform="linux") + {
            targets: const.TARGET.weekly,
            timelimit: const.TIME_LIMIT["4h"],
            run +: [
                // cannot run with excluded "GeneratedBy" since that would lead to "command line too long"
                // ['mx', '--jacoco-whitelist-package', 'com.oracle.graal.python', '--jacoco-exclude-annotation', '@GeneratedBy', '--strict-compliance', "--dynamicimports", super.dynamicImports, "--primary", 'gate', '-B=--force-deprecation-as-warning-for-dependencies', '--strict-mode', '--tags', "python-junit", '--jacocout', 'html'],
                // ['mx', '--jacoco-whitelist-package', 'com.oracle.graal.python', '--jacoco-exclude-annotation', '@GeneratedBy', 'sonarqube-upload', "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.graalvm.python", "-Dsonar.projectName=GraalVM - Python", '--exclude-generated'],
                ['mx', '--jacoco-whitelist-package', 'com.oracle.graal.python', '--strict-compliance', "--dynamicimports", super.dynamicImports, "--primary", 'gate', '-B=--force-deprecation-as-warning-for-dependencies', '--strict-mode', '--tags', "python-unittest,python-tagged-unittest,python-junit", '--jacocout', 'html'],
                ['mx', '--jacoco-whitelist-package', 'com.oracle.graal.python', 'sonarqube-upload', "-Dsonar.host.url=$SONAR_HOST_URL", "-Dsonar.projectKey=com.oracle.graalvm.python", "-Dsonar.projectName=GraalVM - Python", '--exclude-generated'],
            ],
            name: "python-coverage"
        },

}
