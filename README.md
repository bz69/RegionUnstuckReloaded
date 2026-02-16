# RegionUnstuckReloaded

Teleport stuck WorldGuard region players outside the region.

## Commands
* `/regionunstuckreloaded unstuck` — use unstuck
* `/stuck` — alias for unstuck
* `/regionunstuckreloaded reload` — reload config

## Permissions
* `regionunstuck.command` — use unstuck
* `regionunstuck.reload` — reload config
* `regionunstuck.nocooldown` — bypass cooldown set after teleport
* `regionunstuck.nodelay` — instant teleport without delay
* `regionunstuck.*` — full access

## Dependencies
* WorldGuard 7.x
* WorldEdit 7.x

## Installation
1. Install WorldGuard and WorldEdit.
2. Put the plugin jar into `plugins/`.
3. Restart the server and edit `config.yml`.
4. Use `/regionunstuckreloaded reload` after changes.

## Build
`./gradlew build`