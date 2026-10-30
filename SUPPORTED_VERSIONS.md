# Supported Minecraft versions

AuthSecured supports dedicated servers from Minecraft 1.21 through the current
stable release, 26.3.

## Paper-compatible servers

The single `authsecured-paper-1.3.0.jar` artifact is compiled against the
lowest supported Paper API (`1.21`) and uses only API calls retained by newer
Paper, Purpur, Spigot, and Leaf releases.

The same artifact runs on Folia. There it changes a player's state on the
thread of the region that owns the player and runs server-wide timers on the
global region, because Folia has no single server thread.

## Fabric servers

Fabric mods are remapped or compiled for an exact Minecraft release. Use the
JAR whose `+mc...` suffix matches the server exactly:

| Minecraft | Fabric API |
| --- | --- |
| 1.21 | 0.102.0+1.21 |
| 1.21.1 | 0.116.15+1.21.1 |
| 1.21.2 | 0.106.1+1.21.2 |
| 1.21.3 | 0.114.1+1.21.3 |
| 1.21.4 | 0.119.4+1.21.4 |
| 1.21.5 | 0.128.2+1.21.5 |
| 1.21.6 | 0.128.2+1.21.6 |
| 1.21.7 | 0.129.0+1.21.7 |
| 1.21.8 | 0.136.1+1.21.8 |
| 1.21.9 | 0.134.1+1.21.9 |
| 1.21.10 | 0.138.4+1.21.10 |
| 1.21.11 | 0.141.6+1.21.11 |
| 26.1 | 0.145.1+26.1 |
| 26.1.1 | 0.145.4+26.1.1 |
| 26.1.2 | 0.155.2+26.1.2 |
| 26.2 | 0.157.0+26.2 |
| 26.3 | 0.164.0+26.3 |

Minecraft 1.21.x artifacts target Java 21. Minecraft 26.x artifacts target
Java 25, matching the corresponding dedicated server requirement.

Run `./build_fabric_matrix.sh` to compile every supported Fabric artifact.
