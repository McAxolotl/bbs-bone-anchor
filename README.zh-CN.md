# BBS 骨骼 Anchor

[English](README.md) | **简体中文** · v1.0.0

BBS FS 的客户端 Fabric 附属模组，为**模型形态（Model Form）**加入逐骨骼、跨模型 **Anchor** 绑定。模型中的单根骨骼可以跟随另一个回放演员的形态、骨骼或身体部件附件。

## 功能

- 逐骨骼 Anchor 轨道，折叠在整体 Anchor 下方。
- 跨模型目标，复用原生 Anchor 的目标拾取、继承和保持变换编辑。
- 支持 IK 目标、骨骼约束、物理、镜像缩放和焊接渲染。
- 多个演员复用同一模型资源时使用独立骨架。
- 时间轴和形态的**筛选轨道**窗口保持相同的骨骼层级顺序。
- Anchor 轨道右键菜单提供仅当前会话有效的**只显示已绑定骨骼**过滤。
- 支持 BBS FS 全部 19 种语言。

## 环境要求

- Minecraft 1.20.1
- Fabric Loader 0.16.14+
- Fabric API
- BBS FS 2.7
- Java 17+

## 使用方法

1. 在 BBS 回放编辑器中打开影片。
2. 展开根模型形态的 **Anchor** 轨道。
3. 打开逐骨骼轨道，通过原生 Anchor 编辑器选择目标。
4. 右键整体 Anchor 或骨骼轨道，可切换**只显示已绑定骨骼**。

逐骨骼数据内部使用 `anchor:<骨骼名>`，界面只显示原始骨骼名。

## 限制

- 逐骨骼 Anchor 仅适用于根模型形态。
- 身体部件附件保持 BBS 原生行为。需要跨模型驱动的附件请使用独立回放演员。
- 受活动 IK、物理或约束直接控制的骨骼不能作为 Anchor 源。
- CEM 驱动的骨骼不能作为 Anchor 源。
- 绝对坐标与相对坐标演员之间不能绑定。

## 构建

```bash
./gradlew clean check build
./gradlew smokeTest
./gradlew smokeJar remapSmokeJar
```

`check` 会编译模组、运行无需启动游戏的用例，并审计项目与打包产物：mixin 注册、全部 19 种本地化、Fabric 元数据、源码约定与冒烟接线。`smokeTest` 会启动开发客户端跑游戏内用例，报告与期望计数不符即失败；`smokeJar`/`remapSmokeJar` 生成用于生产实例验证的冒烟 jar。构建时会从 Modrinth 获取最新的兼容 BBS FS 版本。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
