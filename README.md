# 一起瞎折腾（OlliteRT 中文版）

<p>
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="110" alt="App 图标">
</p>

> 在 **Android 手机上本地运行**大语言模型（LLM / 多模态）的推理服务器。
> 模型跑在你自己的设备里，**数据不出手机**，不需要联网调用云端 API。

本仓库是 [OlliteRT](https://github.com/NightMean/OlliteRT) 的中文修改版：全中文界面、启动弹窗内置项目入口、内置国内模型源，**开箱即用，下载模型无需翻墙**。

---

## 下载安装

| 版本 | 文件 | 大小 | 说明 |
|---|---|---|---|
| v941 | [**OlliteRT-Zh-v941.apk**](https://github.com/jiankujidu/OlliteRT-Zh/releases/download/v0.9.10-v941/OlliteRT-Zh-v941.apk) | 111 MB | 最新稳定版（v0.9.10） |

**更新记录**

- **v941**（v0.9.10）：**图标整体换新（采用新版 LOGO 贴纸）** —— 桌面图标、顶栏品牌标、开屏图标、`beta` / `dev` 渠道包、Play 商店图标、favicon 全部同步替换；黑底转为 RGBA 透明底，贴纸完整不裁切，四周保留安全出血边距（逐尺寸校验通过）；版号升至 **0.9.10**。
- v940（v0.9.9）：**修复图标裁切** —— 贴纸完整保留不再被切掉一截，四周强制留透明边距（逐尺寸校验四边留白 > 0）；顶栏品牌标恢复完整贴纸；版号升至 **0.9.9**。
- v939（v0.9.8）：图标透明化，去掉黑底方块（RGBA 透明底）。
- v938（v0.9.7）：**Logo 全量换新** —— 桌面图标、顶栏品牌标、开屏图标、`beta` / `dev` 渠道包、Play 商店图标、favicon 全部换成最新贴纸版；彻底删除所有旧的自适应图标定义（`anydpi` / `background` / `foreground` / `monochrome`）。
- v937：App 内的 GitHub 链接全部指向本仓库（项目主页、Release 更新检查、模型清单远程刷新、提交反馈、隐私政策），仓库补齐中文版隐私政策与问题反馈模板。
- v936：桌面图标换成最终贴纸版 LOGO。

**安装要求**

- Android **12（API 31）** 及以上
- CPU 架构 **arm64-v8a**
- 首次安装请先**卸载旧版本**，避免图标缓存导致显示异常

> APK 文件超过 GitHub 单文件 100MB 限制，因此放在 [Releases](https://github.com/jiankujidu/OlliteRT-Zh/releases) 页面，没有放进仓库本体。

---

## 主要功能

- 📱 **手机本地推理** —— 模型和数据全部留在设备上
- 🔌 **OpenAI 兼容 HTTP 接口** —— 可被 OpenWebUI、各类桌面/移动客户端、自建应用直接调用
- 🧠 **多模态能力** —— 支持文本、**视觉（VL）**、**音频**、**思维链（thinking）**、**工具调用（function calling）** 模型
- 🌏 **内置 210 个可运行模型**清单 —— 覆盖 `litert-community` 全量变体，国内走 **ModelScope 直链**下载，速度正常
- 🈶 **全中文界面** —— 启动弹窗含「项目主页」一键跳转
- 🔄 **模型清单自动更新** —— 内置每 24 小时后台刷新，支持下拉手动刷新，也能添加自己的模型源

---

## 快速开始

1. 安装 APK 到 **arm64-v8a** 设备（Android 12+）。
2. 打开应用，进入「**模型**」页挑选模型下载（国内直连，无需代理）。
3. 下载完成后**启动本地服务器**，记下应用内显示的局域网地址和端口。
4. 在客户端里连接该地址即可开始对话。

**客户端配置示例**

- **OpenWebUI**：`设置 → 连接 → OpenAI API`，填 `http://手机IP:端口`，API Key 任意填。
- **其他 OpenAI 兼容客户端**：Base URL 填上述地址，按 `/v1/chat/completions` 规范调用。

具体地址与端口以应用内显示为准。

---

## 自定义模型源

在应用内「添加模型」粘贴模型清单 JSON 即可添加自己的源，格式参考
[`model_allowlists/v1/model_allowlist_cn.json`](model_allowlists/v1/model_allowlist_cn.json)。

添加后会与内置清单合并显示，刷新即可更新，**不需要重新发版**。

**内置清单**：`model_allowlists/v1/model_allowlist_cn.json`（210 个模型）
能力覆盖：图像 61 / 音频 26 / 思考 88 / 工具 169，含多种量化档（int4 / int8 / fp16）及部分芯片 NPU 优化版。

---

## 开发者：源码与构建

本仓库即完整 Android 源码。

```
环境：AGP 9.2 / Kotlin 2.3 / Gradle 9.4，仅编译 arm64-v8a
调试构建：gradlew :app:assembleStableDebug
技术栈：Jetpack Compose + Material 3 + Hilt + KSP
```

详细中文说明见 [README.zh-CN.md](README.zh-CN.md)。

---

## 致谢与来源

- 上游项目：[NightMean/OlliteRT](https://github.com/NightMean/OlliteRT)
- 模型来源：[ModelScope](https://www.modelscope.cn) / `litert-community`
- 本项目仅做中文化、本地化适配与体验优化

## 免责声明

请遵守当地法律法规及模型各自的许可协议使用。因使用本软件产生的任何问题由使用者自行承担。
