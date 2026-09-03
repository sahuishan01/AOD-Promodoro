# AGENTS.md - Agent Instructions & Release Guidelines

## CI Build Duration Benchmark
- **Average CI Workflow Duration**: ~6 minutes 15 seconds (375s).
- **First Poll Interval**: Set initial wait timer to 360 seconds (6 minutes) after pushing release tag before first `gh run list` status check.

## Release Notifications (`agent-releases`)
- Always include the **direct release link** (e.g. `https://github.com/sahuishan01/AOD-Promodoro/releases/tag/vX.Y.Z`) in the notification body and/or title whenever publishing a release to `https://ntfy.algosculptor.com/agent-releases`.
