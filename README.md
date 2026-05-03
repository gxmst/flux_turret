# Flux Turret - 高能光棱防御系统

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)
![Forge](https://img.shields.io/badge/Forge-47.4.10-orange.svg)

Flux Turret (能量炮塔) 是一款 Minecraft 1.20.1 模组，提供基于 Forge Energy (FE) 驱动的光棱连锁防御炮塔，灵感来自红警2的光棱塔/光灵塔。

## 功能概述

- **FE 驱动**：使用 Forge Energy 作为唯一能源，兼容任意 FE 发电/输电模组。
- **光棱连锁**：多个炮塔自动组网，能量汇聚到离目标最近的主塔，形成树状攻击网络。
- **自动索敌**：在 16.5 格范围内自动锁定最近的敌对生物（需要视线）。
- **GeckoLib 渲染**：3D 模型 + 动态激光光束渲染。

## FE 输入

- 容量：100,000 FE
- 最大输入：1,000 FE/tick
- 主塔开火消耗：1,000 FE/次
- 从塔中继消耗：500 FE/次
- 外部无法从炮塔抽电（只进不出）

## 合成方式

```
    D
   III
  IIIII
```

- D = 钻石（顶层中央）
- I = 铁块（底层 3x3 + 中层 3 个）

## 基础使用

1. 放置炮塔方块。
2. 使用 FE 线缆/管道连接为其充能。
3. 炮塔会自动扫描并攻击范围内的敌对生物。
4. 多个炮塔在 12 格内会自动形成连锁网络。

## 连锁机制

- 每个炮塔在 12 格范围内寻找深度更低的邻居作为父节点。
- 离目标最近的有电炮塔成为主塔 (depth=0)，负责造成伤害。
- 从塔必须有电且属于同一棵树才会被计入支持。
- 主塔 BFS 按父子距离逐级扩展，最大深度 6 层。
- 主塔每 20 tick（1 秒）可开火一次，从塔每 2 tick 中继一次。
- 伤害 = 10 * (1 + 支持数 * 0.5)。

## 开发环境

- Minecraft: 1.20.1
- Forge: 47.4.10
- Java: 17
- GeckoLib: 4.4.4
- 构建：Gradle + ForgeGradle 6.x

```bash
chmod +x gradlew
./gradlew build
```

## 许可证

MIT
