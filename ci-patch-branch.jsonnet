// This CI configuration is used on github/patches/* patch branches to only run
// the patch verifier and not other gates
local ci = import "ci.jsonnet";
local patch_builds = [
    build for build in ci.builds if std.objectHas(build, "run_on_patch_branch") && build.run_on_patch_branch
];

ci + {
    // Run the verifier directly instead of creating tier coordinator jobs.
    tierConfig: {},
    builds: [build + { targets: ["gate"] } for build in patch_builds],
}
