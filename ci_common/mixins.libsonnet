local utils = import 'utils.libsonnet';
local const = import 'constants.libsonnet';

{
    // ------------------------------------------------------------------------------------------------------
    //
    // platform mixins
    //
    // ------------------------------------------------------------------------------------------------------
    local linux = {
        capabilities +: ["linux", "amd64"],
        packages +: {
            "maven": "==3.3.9",
            "git": ">=1.8.3",
            "mercurial": ">=3.2.4",
            "gcc": "==4.9.1",
            "llvm": "==4.0.1",
            "python": "==3.6.5",
            "libffi": ">=3.2.1",
            "bzip2": ">=1.0.6",
        },
        downloads +: {
            LIBGMP: utils.download("libgmp", "6.1.0"),
        },
    },
    linux:: linux,

    local linuxBench = linux + {
        capabilities +: ["no_frequency_scaling", "tmpfs25g", "x52"],
    },
    linuxBench:: linuxBench,

    local darwin = {
        capabilities +: ["darwin_sierra", "amd64"],
        timelimit: const.TIME_LIMIT["1h"],
        packages +: {
            "pip:astroid": "==1.1.0",
            "pip:pylint": "==1.1.0",
            "llvm": "==4.0.1",
        },
    },
    darwin:: darwin,

    getPlatform(platform)::
        local PLATFORMS = {
          "linux": linux,
          "linuxBench": linuxBench,
          "darwin": darwin,
        };
        utils.getValue(PLATFORMS, platform),

    // ------------------------------------------------------------------------------------------------------
    //
    // general mixins
    //
    // ------------------------------------------------------------------------------------------------------
    local pypy = {
        downloads +: {
            PYPY_HOME: utils.download("pypy3", "7.1.0.beta"),
        },
    },
    pypy:: pypy,

    local eclipse = {
        downloads +: {
            ECLIPSE: utils.download("eclipse", "4.5.2.1"),
            JDT: utils.download("ecj", "4.6.1", false),
        },
        environment +: {
            ECLIPSE_EXE: "$ECLIPSE/eclipse",
        },
    },
    eclipse:: eclipse,

    local labsjdk8 = {
        downloads +: {
            JAVA_HOME: utils.download("oraclejdk", "8u212-jvmci-19.2-b01"),
        },
        environment +: {
            CI: "true",
            GRAALVM_CHECK_EXPERIMENTAL_OPTIONS: "true",
            PATH: "$JAVA_HOME/bin:$PATH",
        },
    },
    labsjdk8:: labsjdk8,

    local graal = labsjdk8 + {
        environment +: {
            HOST_VM: const.JVM.server,
        },
    },
    graal:: graal,

    local graalCore = graal + {
        environment +: {
            HOST_VM_CONFIG: const.JVM_CONFIG.core,
        },
    },
    graalCore:: graalCore,

    local sulong = labsjdk8 + {
        environment +: {
            CPPFLAGS: "-I$LIBGMP/include",
            LD_LIBRARY_PATH: "$LIBGMP/lib:$LLVM/lib:$LD_LIBRARY_PATH",
        }
    },
    sulong:: sulong,

    local bench = {
        packages +: {
            "make": ">=3.83",
            "binutils": "==2.23.2",
        },
        environment +: {
            BENCH_RESULTS_FILE_PATH: "bench-results.json",
        },
        logs +: [
            "bench-results.json",
        ],
    },
    bench:: bench,
}
