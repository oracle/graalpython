local const = import 'constants.libsonnet';
local graal_common = import "graal/ci/common.jsonnet";

{
    // add a graal option
    graal_option(name, value)::
        ["--Ja", "@-Dpolyglot.engine."+name+"="+value],

    // utility for array contains
    contains(array, value)::
        std.count(array, value) > 0,

    with_notify_groups(builders)::
        [b + (if !std.objectHas(b, "notify_emails") && std.objectHas(b, "targets") && b.targets == const.TARGET.gate then
                {}
            else
                {notify_emails: const.NOTIFY_GROUPS}) for b in builders],

    path_combine(oldPath, newPath)::
        if oldPath != null && std.length(std.findSubstr("%PATH%", oldPath)) > 0 then
            std.join(";", std.map(function(it) if it == "%PATH%" then newPath else it, std.split(oldPath, ";")))
        else if oldPath != null then
            std.join(":", std.map(function(it) if it == "$PATH" then newPath else it, std.split(oldPath, ":")))
        else
            newPath,

    ensure_notify(builds):: [
        b + (
            if !std.objectHas(b, "notify_groups") && std.objectHas(b, "targets") && (b.targets == ["gate"] || b.targets == ["post-merge"]) then
                {}
            else
                {notify_groups: ["tim.felgentreff@oracle.com"]}
        )
        for b in builds
    ],

    ensure_no_mx_wrong_build(builds):: [
        b  + (
            if std.objectHas(b, "setup") && b.jdk_version != graal_common.jdks["labsjdk-ee-latest"].jdk_version then
                assert (std.length([step for step in b.setup if std.length(std.find("build", step)) != 0 && std.length(std.find("mx", step)) != 0]) == 0) : b.name + " should not call build in " + b.setup;
                {}
            else
                {}
        )
        for b in builds
    ],

    ensure_tier_time_and_machine_limits(builds):: [
        b  + (
            if std.count(b.targets, "tier1") > 0 then
                (
                    assert !std.objectHas(b, "timelimit") : b.name + " should not have a custom timelimit";
                    assert b.os == "linux" : "Only linux is allowed in tier1, move " + b.name + " to another tier";
                    assert b.arch == "amd64" : "Only amd64 is allowed in tier1, move " + b.name + " to another tier";
                    {timelimit: "00:10:00"}
                )
            else if std.count(b.targets, "tier2") > 0 then
                (
                    assert !std.objectHas(b, "timelimit") : b.name + " should not have a custom timelimit";
                    assert b.os == "linux" : "Only linux is allowed in tier2, move " + b.name + " to another tier";
                    assert b.arch == "amd64" : "Only amd64 is allowed in tier2, move " + b.name + " to another tier";
                    {timelimit: "00:20:00"}
                )
            else if std.count(b.targets, "tier3") > 0 then
                (
                    assert !std.objectHas(b, "timelimit") : b.name + " should not have a custom timelimit";
                    if std.count(["linux", "windows"], b.os) > 0 then
                        {timelimit: "01:00:00"}
                    else
                        {timelimit: "00:40:00"}
                )
            else
                {}
        )
        for b in builds
    ],
}

// Local Variables:
// smie-indent-basic: 4
// jsonnet-indent-level: 4
// End:
