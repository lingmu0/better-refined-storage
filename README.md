# Better Sophisticated Storage

一个基于“更好的超越维度”界面思路的独立附属模组，为精致存储提供便携终端侧边栏。

[公开源码与问题反馈](https://github.com/lingmu0/better-sophisticated-storage) ·
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/better-sophisticated-storage)

源码同时保留 `forge/` 和 `neoforge/` 目录；`mc-1.20.1`、`mc-1.21.1` 分支为对应版本发布时的源码快照。

## 功能

- 只有玩家携带已绑定且可访问网络的 RS 无线终端（含创造无线终端），或已连接的
  `integratedterminals:terminal_storage_portable` 时，才显示并操作网络。
- 便携终端既可以放在普通玩家物品栏，也可以放在 Curios 饰品栏。
- 在容器界面显示可搜索的精致存储网络物品列表，支持点击取出、放入、丢弃和快捷栏交互。
- 一键存容器、 一键存背包、容器 Shift 转移都会跳过便携终端，避免把终端本身存进网络。
- 配置文件中的 `disableConflictingKeys` 设为 `true` 时，存入快捷键会取消输入事件，避免触发其他绑定了相同按键的功能；默认值为 `false`。
- 服务端验证物品和数量，客户端搜索只负责筛选显示结果。

## 依赖

使用精致存储网络时需要 Refined Storage：Forge 1.20.1 支持 `1.12.x`，NeoForge 1.21.1 支持 `2.x`。

使用 Integrated Terminals 兼容路径时需要：

- [Integrated Terminals](https://github.com/CyclopsMC/IntegratedTerminals)
- [Sophisticated Storage](https://github.com/P3pp3rF1y/SophisticatedStorage)
- Integrated Terminals 所需的 Integrated Dynamics / Common Capabilities
- 若要把终端放进饰品栏，需要 Curios API 及对应饰品栏模组

## 构建

- Forge 1.20.1：在 `forge` 目录运行 `gradlew.bat build`
- NeoForge 1.21.1：在 `neoforge` 目录运行 `gradlew.bat build`

构建产物位于各自版本目录的 `build/libs`，两个版本当前均为 `0.4.10`。

侧边栏采用与“更好的超越维度”一致的原版面板和凹槽格子样式。
侧边栏数量使用原版物品装饰层绘制，避免被物品模型遮挡，保留 K/M/B 数量缩写。
数量为 1 时不显示数量角标，仍保留原版耐久条等物品装饰。
搜索框默认提示按当前字体和框内宽度截断，超出的部分显示为 `...`；输入内容不受截断影响。

Forge 1.20.1 另外支持 Refined Storage 1.12.x 的已绑定无线终端和创造无线终端，
包括普通背包和 Curios 饰品栏；保留 RS 原生网络运行、无线范围、权限及能量检查。

NeoForge 物品匹配回归检查（独立 JVM，不启动游戏）：在 `neoforge` 目录运行
`gradlew.bat verifyStorageMatching -I verification/stack-regression.init.gradle --no-configuration-cache`。
检查复制物品键、数量和数据组件匹配、服务端侧栏槽位可用性及同类物品数量合并。

Forge 旧版 RS 回归检查（独立 JVM，不启动游戏）：在 `forge` 目录运行
`gradlew.bat verifyLegacyRefinedStorage -I verification/legacy-rs-regression.init.gradle "-PlegacyRsJar=<refinedstorage-1.12.4.jar 的绝对路径>"`。
检查实际旧版 API 签名、绑定解析、缓存列表、权限、模拟/实际存取、NBT 和无线距离边界。
