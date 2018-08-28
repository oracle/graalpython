{
  overlay: "934f7a99e60cbc8d0affd873805c057b576f3709",

  // ======================================================================================================
  // 
  // help:
  //  1) to get the json out of the jsonnet configuration make sure the `jsonnet` executable is in path
  //  2) execute the following command: jsonnet ci.jsonnet > ci.json
  //  3) a helper script which does just that is located in: ./scripts/jsonnet_json.sh
  //
  // ======================================================================================================

  // ======================================================================================================
  //
  // locals (will not appear in the generated json)
  //
  // ======================================================================================================

  // timelimit
  local TIME_LIMIT = {
    "30m": "00:30:00",
    "1h": "1:00:00",
    "2h": "2:00:00",
    "3h": "3:00:00",
    "4h": "4:00:00",
    "8h": "8:00:00",
    "10h": "10:00:00",
    "16h": "16:00:00",
    "20h": "20:00:00",
  },
  TIME_LIMIT: TIME_LIMIT,

  // targets
  local TARGET = {
    onDemand: ["bench"],
    postMerge: ["post-merge"],
    weekly: ["weekly"],
    gate: ["gate"],
  },
  TARGET: TARGET,

  // ------------------------------------------------------------------------------------------------------
  //
  // utility funcs 
  //
  // ------------------------------------------------------------------------------------------------------
  local utils = {
    download: function(name, version, platformSpecific = true)
      {name: name, version: version, platformspecific: platformSpecific},
    
    getValue: function(object, field)
      if (!std.objectHas(object, field)) then
        error "unknown field: "+field+" in "+object+", valid choices are: "+std.objectFields(object)
      else
        object[field],

    graalOption: function(name, value)
      ["--Ja", "@-Dgraal."+name+"="+value],
  },
  
  // ------------------------------------------------------------------------------------------------------
  //
  // platform mixins
  //
  // ------------------------------------------------------------------------------------------------------
  local linuxMixin = {
    capabilities +: ["linux", "amd64"],
    packages +: {
      "maven": "==3.3.9",
      "git": ">=1.8.3",
      "mercurial": ">=3.2.4",
      "gcc": "==4.9.1",
      "llvm": ">=4.0",
      "python": "==3.4.1",
      "libffi": ">=3.2.1",
      "bzip2": ">=1.0.6",
    },
    downloads +: {
      LIBGMP: utils.download("libgmp", "6.1.0"),
    },
  },
  linuxMixin: linuxMixin,

  local linuxBenchMixin = linuxMixin + {
    capabilities +: ["no_frequency_scaling", "tmpfs25g", "x52"],
  },
  linuxBenchMixin: linuxBenchMixin,

  local darwinMixin = {
    capabilities +: ["darwin_sierra", "amd64"],
    packages +: {
      "pip:astroid": "==1.1.0",
      "pip:pylint": "==1.1.0",
      "llvm": "==4.0.1",
    },
  },

  local getPlatform = function(platform)
    local PLATFORMS = {
      "linux": linuxMixin,
      "linuxBench": linuxBenchMixin,
      "darwin": darwinMixin,
    };
    utils.getValue(PLATFORMS, platform),

  // ------------------------------------------------------------------------------------------------------
  //
  // mixins
  //
  // ------------------------------------------------------------------------------------------------------
  local pypyMixin = {
    downloads +: {
      PYPY_HOME: utils.download("pypy3", "5.8.0-minimal"),
    },
  },
  pypyMixin: pypyMixin,

  local eclipseMixin = {
    downloads +: {
      ECLIPSE: utils.download("eclipse", "4.5.2.1"),
      JDT: utils.download("ecj", "4.6.1", false),
    },
    environment +: {
      ECLIPSE_EXE: "$ECLIPSE/eclipse",
    },
  },

  local labsjdk8Mixin = {
    downloads +: {
      JAVA_HOME: utils.download("labsjdk", "8u172-jvmci-0.46"),
      EXTRA_JAVA_HOMES : { pathlist: [utils.download("oraclejdk", "11+20")] },
    },
    environment +: {
      CI: "true",
      PATH: "$JAVA_HOME/bin:$PATH",
    },
  },
  labsjdk8Mixin: labsjdk8Mixin,

  local graalMixin = labsjdk8Mixin + {
    environment +: {
      HOST_VM: "server",
    },
  },

  local graalCoreMixin = graalMixin + {
    environment +: {
      HOST_VM_CONFIG: "graal-core",
    },
  },
  graalCoreMixin:  graalCoreMixin,

  local sulongMixin = labsjdk8Mixin + {
    environment +: {
      CPPFLAGS: "-I$LIBGMP/include",
      LD_LIBRARY_PATH: "$LIBGMP/lib:$LLVM/lib:$LD_LIBRARY_PATH",
    }
  },
  sulongMixin: sulongMixin,

  // ------------------------------------------------------------------------------------------------------
  //
  // the build templates
  //
  // ------------------------------------------------------------------------------------------------------
  local baseBuilder = {
    downloads: {},
    environment: {},
    setup: [],
    logs: ["dumps/*/*"],
    timelimit: TIME_LIMIT["30m"],
    packages: {},
    capabilities: [],
    name: null,
    targets: [],
    run: [],
  },

  local commonBuilder = baseBuilder + labsjdk8Mixin + {
    dynamicImports:: "sulong,/compiler",

    setup +: [
      ["mx", "sforceimport"],
      ["mx", "--dynamicimports", self.dynamicImports, "build"],
    ]
  },
  commonBuilder: commonBuilder,

  // ------------------------------------------------------------------------------------------------------
  //
  // the gate templates
  //
  // ------------------------------------------------------------------------------------------------------
  local baseGate = commonBuilder + {
    tags: "tags must be defined",

//    local truffleDebugFlags = utils.graalOption("TraceTruffleCompilation", "true"),
//    local truffleDebugFlags = utils.graalOption("TraceTruffleCompilationDetails", "true"),
    local truffleDebugFlags = [],
    targets: TARGET.gate,
    run +: [
      ["mx"] + truffleDebugFlags + ["--strict-compliance", "--dynamicimports", super.dynamicImports, "--primary", "gate", "--tags", self.tags, "-B=--force-deprecation-as-warning-for-dependencies"],
    ]
  },

  local baseGraalGate = baseGate + sulongMixin + graalCoreMixin,
  baseGraalGate: baseGraalGate,

  // specific gates
  local testGate = function(type, platform)
    baseGraalGate + {tags:: "python-"+type} + getPlatform(platform) + {name: "python-"+ type +"-"+platform},

  local styleGate = baseGraalGate + eclipseMixin + linuxMixin + {
    tags:: "style,fullbuild,python-license",
    name: "python-style",

    timelimit: TIME_LIMIT["1h"],
  },

  local graalVmGate = baseGraalGate + linuxMixin {
    tags:: "python-graalvm",
    name: "python-graalvm",

    timelimit: TIME_LIMIT["1h"],
  },

  // ------------------------------------------------------------------------------------------------------
  //
  // the deploy templates
  //
  // ------------------------------------------------------------------------------------------------------
  local deployGate = function(platform)
    baseBuilder + graalCoreMixin + sulongMixin + getPlatform(platform) + {
      targets: TARGET.postMerge,
      setup +: [
        ["mx", "sversions"],
        ["mx", "build", "--force-javac"],
      ],
      run +: [
        ["mx", "deploy-binary-if-master", "python-public-snapshots"],
      ],
      name: "deploy-binaries-"+platform,
    },

  // ------------------------------------------------------------------------------------------------------
  //
  // the gates
  //
  // ------------------------------------------------------------------------------------------------------
  local gates = [
    // unittests 
    testGate(type="unittest", platform="linux"),
    testGate(type="unittest", platform="darwin"),

    // junit 
    testGate(type="junit", platform="linux"),
    testGate(type="junit", platform="darwin"),

    // style 
    styleGate,

    // graalvm gates
    graalVmGate,

    // deploy binaries 
    deployGate(platform="linux"),
    deployGate(platform="darwin"),
  ],

  // ======================================================================================================
  //
  // the builds (the public section)
  //
  // ======================================================================================================
  builds: gates,
}
