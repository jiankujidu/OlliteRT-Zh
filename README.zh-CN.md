# OlliteRT 中文版

> 在 Android 设备上**本地运行**大语言模型（LLM / 多模态）的推理服务器。
> 本项目为中文修改版：全中文化界面、启动弹窗内置项目入口、内置国内模型源，开箱即用，无需翻墙下载模型。

## 特性

- 📱 **手机本地推理**，数据不出设备
- 🔌 提供 **OpenAI 兼容 HTTP API**，可被 OpenWebUI、桌面/移动客户端、自建应用调用
- 🧠 支持文本、**视觉（VL）**、**音频**、**思维链（thinking）**、**工具调用（function calling）** 模型
- 🌏 内置 **210 个可运行模型**清单（`litert-community` 全量变体），国内 **ModelScope 直链**下载，速度正常
- 🈶 全中文界面，启动弹窗含「项目主页（GitHub）」一键跳转

## 快速开始

1. 将 APK 安装到 **arm64-v8a** 设备（Android 12+）。
2. 打开应用，每次启动会弹出社区/项目弹窗（点「项目主页（GitHub）」可回到本仓库）。
3. 进入「模型」页，选择模型下载（国内走 ModelScope 直链，无需代理）。
4. 下载完成后启动本地服务器，记下应用内显示的局域网地址与端口。
5. 在客户端（OpenWebUI 或任意 OpenAI 兼容工具）连接该地址即可对话。

## 客户端连接示例

- **OpenWebUI**：设置 → 连接 → OpenAI API，填 `http://手机IP:端口`，API Key 任意填写。
- **其他 OpenAI 兼容客户端**：Base URL 填上述地址，按 OpenAI `/v1/chat/completions` 规范调用。
- 具体端口与应用内显示为准。

## 自定义模型

应用内「添加模型」粘贴模型清单 JSON 即可，格式参考
[`model_allowlists/v1/model_allowlist_cn.json`](model_allowlists/v1/model_allowlist_cn.json)。

## 模型清单

- `model_allowlists/v1/model_allowlist_cn.json`：内置 **210 个**可运行模型，国内源。
- 能力覆盖：**图像 61 / 音频 26 / 思考 88 / 工具 169**。
- 包含同模型的不同量化档（int4 / int8 / fp16）与部分芯片 NPU 优化版（特定硬件可跑）。

## 项目主页

[GitHub 仓库](https://github.com/PLACEHOLDER_OWNER/OlliteRT-Zh)

## 源码与构建（开发者）

- 本仓库即完整 Android 源码。
- 环境：AGP 9.2 / Kotlin 2.3 / Gradle 9.4，仅编译 `arm64-v8a`。
- 调试构建：`gradlew :app:assembleStableDebug`

## 免责声明

本软件基于原项目 [NightMean/OlliteRT](https://github.com/NightMean/OlliteRT) 修改，按 **Apache-2.0** 许可分发，仅供学习与研究使用。
模型权重版权归各自所有者，请遵守相应许可。
