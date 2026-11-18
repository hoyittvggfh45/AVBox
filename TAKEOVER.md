# 接手说明

上游：https://github.com/XiaochangXu/AVBox ，原作者 XiaochangXu。
上游仓库已归档（不再更新），本树从上游全量克隆后接着维护。
接手仓：https://github.com/hoyittvggfh45/AVBox（公开，2026-11-18 新建）。

许可证：GNU AGPLv3（见 LICENSE），原作者署名与许可证保留；
对外发包时源码同样以 AGPLv3 提供，不换许可证、不抹出处。

## 本树相对上游的改动

1. APK 插件清单：`app/src/main/assets/moyi-zone.json`（魔蚁 XPTV 宿主契约，
   installMode=apk，包名 com.github.avbox.osc，区=片库）。
   清单里的 version / versionCode 必须与 `app/build.gradle.kts` 双处同步。
2. 站点登记表：`app/src/main/assets/sites.json`，内容为 `{"sites": []}`。
   AVBox 默认不内置任何片源（`default_config.json` 只有广告规则），
   空表是如实状态，不是缺漏；源由用户在应用内自行配置。
3. 捞回的文档：`skill/history/` 四份决策历史文档（上游 2026-11-16 删掉，
   本树从 git 历史恢复，内容一字未改），因为现行规范多处引用它们。
4. 海阔运行时页：`app/src/main/assets/haikuo/runtime.html`（411107 字节），
   从用户自己的魔蚁树（`lib/miniapp/haikuo/haikuo_runtime_html.dart`）原样
   提取的 JS 运行时页，md5 e286b30938ab45c18373136b8147f75a；宿主侧
   （Kotlin 服务 + 桥 + 存储）另行实现，见工作笔记。
5. 海阔宿主（2026-11-18 落树，未编译验证，云构建+真机验等用户令）：
   `haikuo/` 引擎包 10 个文件（JSON/模型/helpers/编解码/存储/对话框/
   会话/服务/桥/云剪贴板）+ `ui/` 5 个 Activity（首页/运行页/播放页/
   小说页/图片页）+ `ui/page/` 7 个界面文件 + `ui/player/HaikuoPlayback.kt`
   （push_fallback 取流拼装）；运行页经设置页「小程序」入口进入；
   内置规则 `assets/haikuo_builtin_rules.json`（4.1MB，与魔蚁树同文件
   md5 一致）+ 预置模块 `assets/haikuo_modules/`（26 个文件）与
   `assets/haikuo_modules_manifest.json`（均与魔蚁树逐字节一致）。
   播放走 AVBox 现有 PlayContainer + 共享播放引擎，桥 play 消息经
   sourceKey=push_fallback 兜底直通，不经过蜘蛛。
6. 蚂蚁小应用宿主（2026-11-18 落树，未编译验证，云构建+真机验等用户令）：
   `miniapp/` 引擎包 10 个文件（清单/设置/广告过滤/SDK 脚本/存储/
   本地服务/运行时/桥/源提供者/内置播种）+ `ui/` 4 个 Activity（首页/
   宿主页/嗅探页/设置页）+ `ui/page/` 5 个界面文件，共 4627 行新
   Kotlin；设置页「小程序」入口改指小程序首页，海阔从其菜单进入。
   source.* 只认已配置的非隐藏蜘蛛站（type 3）；player.open 直链走
   页内嵌 PlayContainer、需解析走可见嗅探页。内置资产
   `assets/miniapps/builtin/` 带 market.json 与 3 个小 zip（校验值与
   清单一致），59MB 的 drpy_node.zip 未打包（Node 运行时宿主不提供）。
   顺手修了海阔运行页一处坏 import（HaikuoX5Intercept 无包名导入）。
7. 主题 14 套预设（2026-11-18 落树，未编译验证，云构建+真机验等用户令）：
   新文件 `ui/theme/MoyiThemeConfig.kt`（14 套精确配色 + 显示方案绑定 +
   固定映射 + LocalMoyiThemeConfig）+ 引擎接线（ThemeSource 加 MOYI 分支、
   ThemeConfig 加 preset 字段、AppThemeState 存取与 scheme 派生、
   AVBoxTheme 按预设亮暗与配色生效）；主题设置页新增预设组（双列实色
   卡片、内容档弹窗、恢复默认 XP），预设生效时亮暗与纯黑两行置灰；
   261 个色值与魔蚁原文机械核对按序全等。默认仍跟随系统；内容档只存
   取暂无消费方；3D 方块与 Win7/XP 桌面壳不在本批。
8. 调试构建工作流（2026-11-18 新增）：`.github/workflows/build-debug.yml`，
   手动触发，只编 debug 包（不走签名校验），产物以 Actions artifact 形式
   留存 14 天；正式版 release.yml 未动，仍等新签名方案。
   推到接手仓的 gradle.properties 里旧签名口令已清空（旧密钥已删，口令无用；
   本地工作树里的原文件未改）。

## 签名状态

上游原签名密钥已于 2026-11-16 从仓库删除，且密钥曾经公开过，绝不能再用。
发布构建（release.yml）目前会卡在签名校验上；新签名方案待定，
定下来之前只出调试（debug）构建。
