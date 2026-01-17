# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DOV (Data Over Video) 是一个通过 HDMI 信号传输文件的工具。工作原理：
- **Hermes (sender)**: 将文件编码为视频信号，通过 HDMI 输出到屏幕
- **Argus (receiver)**: 通过采集卡捕获 HDMI 信号，将视频解码恢复为文件

目标硬件规格：1080P-60Hz 视频采集卡

## Build Commands

```bash
# 编译整个项目
mvn clean compile

# 打包
mvn clean package

# 安装到本地仓库
mvn clean install

# 只编译特定模块
mvn clean compile -pl hermes
mvn clean compile -pl argus

# 运行测试
mvn test

# 运行特定模块测试
mvn test -pl hermes
mvn test -pl argus
```

## Architecture

Maven 多模块项目结构：
- **dov** (parent pom) - 父项目，定义 Java 21 编译器配置
- **hermes (Hermes)** - 发送端，依赖 protocol 模块
- **argus (Argus)** - 接收端，依赖 protocol 模块
- **protocol** - 共享协议模块，被 Hermes 与 Argus 依赖

## Documentation

- **DESIGN.md** - 完整技术设计文档，包含：
  - 编码方案（8x8 像素块二值编码）
  - 帧格式设计（定位角标、帧头、数据区、校验区）
  - 传输协议（IDLE/START/DATA/EOF 帧类型）
  - 状态机设计
  - 纠错机制
  - UI 设计
  - 模块结构和依赖
