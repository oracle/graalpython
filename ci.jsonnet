local utils = import 'ci_common/utils.libsonnet';
local const = import 'ci_common/constants.libsonnet';
local builder = import 'ci_common/builder.libsonnet';

{
    overlay: "7ad24fb14b37e95aeac98c4dacf2e1350b928ad2",

    // ======================================================================================================
    //
    // help:
    //  1) to get the json out of the jsonnet configuration make sure the `jsonnet` executable is in path
    //  2) execute the following command: jsonnet ci.jsonnet > ci.json
    //  3) a helper script which does just that is located in: ./scripts/jsonnet_json.sh
    //
    // ======================================================================================================

    // ------------------------------------------------------------------------------------------------------
    //
    // the gates
    //
    // ------------------------------------------------------------------------------------------------------
    local gates = [
        // unittests on JDK8
        builder.testGate(type="unittest", platform="linux"),
        builder.testGate(type="unittest", platform="darwin"),
        builder.testGateTime(type="tagged-unittest", platform="linux", timelimit=const.TIME_LIMIT["2h"]),
        builder.testGateTime(type="tagged-unittest", platform="darwin", timelimit=const.TIME_LIMIT["2h"]),
        builder.testGate(type="svm-unittest", platform="linux"),
        builder.testGate(type="svm-unittest", platform="darwin"),
        builder.testGate(type="unittest-jython", platform="linux"),

        // JDK11
        // builder.testGate11(type="unittest", platform="linux"),
        // builder.testGate11(type="svm-unittest", platform="linux"),

        // junit
        builder.testGate(type="junit", platform="linux"),
        builder.testGate(type="junit", platform="darwin"),

        // style
        builder.styleGate,

        // coverage
        builder.coverageGate,

        // graalvm gates
        builder.graalVmGate,

        // deploy binaries
        builder.deployGate(platform="linux"),
        builder.deployGate(platform="darwin"),
    ],

    // ======================================================================================================
    //
    // the builds (the public section)
    //
    // ======================================================================================================
    builds: gates,
}
