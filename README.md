<div align="center">

# 🛡️ Intave

**Anticheat for Minecraft servers, built on movement simulation and heuristics.**

![Minecraft 1.8–26.3](https://img.shields.io/badge/Minecraft-1.8%E2%80%9326.3-62b47a)
![Spigot · Paper · Folia](https://img.shields.io/badge/server-Spigot%20%C2%B7%20Paper%20%C2%B7%20Folia-0e7490)
![Requires ProtocolLib](https://img.shields.io/badge/requires-ProtocolLib-dc2626)
![Java 8+](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk&logoColor=white)
[![License: PolyForm Perimeter 1.0.0](https://img.shields.io/badge/license-PolyForm%20Perimeter%201.0.0%20(source--available)-blue)](LICENSE.md)

> BlockStorm's fork of [intave/intave](https://github.com/intave/intave).

</div>

---

Intave is a server-side anticheat for Spigot, Paper and Folia servers, made by the Intave team. BlockStorm maintains
this fork for its network: upstream `master` plus a set of patches, mostly false-positive fixes. The
patches are listed below and in the wiki. There are no prebuilt releases of this fork, so you build it from source.

## ✨ At a glance

- 🏃 Movement simulation for every client version from 1.8 to 26.3
- 🧠 Heuristics for aim, block placement, clicking and inventory actions
- ☁️ Optional cloud-based detection (a paid service from the Intave team, not needed for the other checks)
- ⚙️ Kicks, bans and staff notifications triggered by configurable violation thresholds
- 🔐 Trust factors, assigned through permissions, that tune how strict the checks are for each player
- 🧵 Folia support

## 🔧 What this fork changes

- Includes four of RobinBr26's pull requests to upstream before upstream accepts them: riding (vehicle collisions,
  setbacks, block breaking) and item use (use effects, delayed item use flags)
- Fixes false positives in block placement and break speed after lag, in flying through water on old clients, on
  carpets, with the sprint food rule, and with 1.21.4–1.21.10 and 26.1.x clients
- Fixes on 1.8 servers with ProtocolLib 5: golden apples can be eaten again, and the placement checks run again
- Behind a proxy without IP forwarding, one IP kick no longer kicks every player on the server
- On Folia, threshold commands (notify, kick, ban) now run
- Adds three combat heuristics (`multi-target`, `attack-while-blocking`, `rotation-duplicate`) and turns on reporting
  for the existing sprint/sneak toggle heuristic

The full list and the reason behind each change are on [Fork Changes](https://github.com/BlockStormHU/mc-intave-fork/wiki/Fork-Changes).

## 📖 Documentation

The documentation is in the [wiki](https://github.com/BlockStormHU/mc-intave-fork/wiki):

- [Installation](https://github.com/BlockStormHU/mc-intave-fork/wiki/Installation): requirements, and which jar to use
- [Building from Source](https://github.com/BlockStormHU/mc-intave-fork/wiki/Building-from-Source): how to build the jar
- [Configuration](https://github.com/BlockStormHU/mc-intave-fork/wiki/Configuration): `config.yml`, `advanced.yml` and placeholders
- [Commands and Permissions](https://github.com/BlockStormHU/mc-intave-fork/wiki/Commands-and-Permissions)
- [Fork Changes](https://github.com/BlockStormHU/mc-intave-fork/wiki/Fork-Changes)
- [Original README](https://github.com/BlockStormHU/mc-intave-fork/wiki/Original-README): upstream's README, unchanged

Upstream's own documentation is at [docs.intave.ac](https://docs.intave.ac/). It describes upstream's builds, and
almost all of it also applies to this fork.

## 📄 License

Intave is **source-available, not open source**. It is licensed under the
[PolyForm Perimeter License 1.0.0](LICENSE.md). You may use and modify it, also on commercial servers, but you may not
use it to provide a product that competes with Intave. This fork is distributed under the same terms.

Required Notice: Copyright Intave (http://intave.ac)

---

<div align="center">
<sub>Created by <b>Jpx3</b> and the <a href="https://github.com/intave/intave/graphs/contributors">Intave contributors</a> · Fork maintained by <b>BlockStorm</b> · Source-available under the <a href="LICENSE.md">PolyForm Perimeter License 1.0.0</a></sub>
</div>
