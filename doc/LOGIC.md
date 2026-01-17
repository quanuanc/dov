# DOV 程序运行逻辑文档

本文档描述 Hermes 和 Argus 程序从启动到完成传输的完整运行逻辑。

---

## 1. 整体架构

### 1.1 组件交互图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Hermes                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────────┐   │
│  │  SenderApp   │───▶│ SenderController │───▶│   FrameRenderer      │   │
│  │  (JavaFX)    │    │   (状态机控制)    │    │   (帧图像生成)        │   │
│  └──────────────┘    └──────────────────┘    └──────────────────────┘   │
│         │                    │                          │                │
│         │                    ▼                          │                │
│         │           ┌──────────────────┐                │                │
│         │           │   FileChunker    │                │                │
│         │           │   (文件分块)      │                │                │
│         │           └──────────────────┘                │                │
│         │                    │                          │                │
│         │                    ▼                          ▼                │
│         │           ┌──────────────────────────────────────┐            │
│         │           │           FrameCodec                  │            │
│         │           │    (数据 → 帧图像编码)                 │            │
│         └──────────▶│           BlockCodec                  │            │
│                     │    (字节 → 8x8像素块)                  │            │
│                     └──────────────────────────────────────┘            │
│                                      │                                   │
│                                      ▼                                   │
│                              ┌──────────────┐                           │
│                              │  屏幕/HDMI   │                           │
│                              └──────────────┘                           │
└─────────────────────────────────────────────────────────────────────────┘
                                       │
                                       │ HDMI 信号
                                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              Argus                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                              ┌──────────────┐                           │
│                              │   采集卡     │                           │
│                              └──────────────┘                           │
│                                      │                                   │
│                                      ▼                                   │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────────┐   │
│  │ ReceiverApp  │◀───│ReceiverController│◀───│   CaptureDevice      │   │
│  │   (UI)       │    │   (状态机控制)    │    │   (采集封装)          │   │
│  └──────────────┘    └──────────────────┘    └──────────────────────┘   │
│         │                    │                          │                │
│         │                    ▼                          ▼                │
│         │           ┌──────────────────────────────────────┐            │
│         │           │          FrameAnalyzer               │            │
│         │           │    (帧检测 + 解码)                    │            │
│         │           │          FrameDetector               │            │
│         │           │    (角标识别 + 边界检测)               │            │
│         │           │          FrameCodec                  │            │
│         │           │    (帧图像 → 数据解码)                 │            │
│         │           └──────────────────────────────────────┘            │
│         │                    │                                           │
│         │                    ▼                                           │
│         │           ┌──────────────────┐                                │
│         │           │  FileAssembler   │                                │
│         │           │   (文件重组)      │                                │
│         │           └──────────────────┘                                │
│         │                    │                                           │
│         │                    ▼                                           │
│         └───────────▶ 输出文件                                           │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 线程模型

#### Hermes 线程

```
┌─────────────────────────────────────────────────────────┐
│ JavaFX Application Thread (主线程)                       │
│ - UI 渲染                                                │
│ - 用户事件处理                                            │
│ - 帧图像显示                                              │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Frame Scheduler Thread (帧调度线程)                       │
│ - 定时器驱动                                              │
│ - 按固定间隔切换帧                                         │
│ - 通知主线程更新显示                                       │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Preparation Thread (准备线程，仅在 PREPARING 阶段)         │
│ - 计算文件 SHA-256                                        │
│ - 分块并编码所有帧                                         │
│ - 完成后通知主线程                                         │
└─────────────────────────────────────────────────────────┘
```

#### Argus 线程

```
┌─────────────────────────────────────────────────────────┐
│ Main Thread (主线程)                                     │
│ - UI 渲染和更新                                          │
│ - 用户事件处理                                            │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Capture Thread (采集线程)                                │
│ - 持续从采集卡读取帧                                       │
│ - 将帧放入处理队列                                         │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Analyzer Thread (分析线程)                               │
│ - 从队列取帧                                              │
│ - 执行帧检测和解码                                         │
│ - 更新接收状态                                            │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ Assembler Thread (重组线程，仅在 ASSEMBLING 阶段)          │
│ - 重组文件                                                │
│ - 校验 SHA-256                                           │
│ - 写入磁盘                                                │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Hermes 运行逻辑

### 2.1 启动流程

```
SenderApp.main()
    │
    ▼
Application.launch()
    │
    ▼
SenderApp.start(Stage primaryStage)
    │
    ├──▶ 创建全屏场景 (1920x1080)
    │
    ├──▶ 初始化 SenderController
    │       │
    │       ├──▶ 创建 FrameRenderer
    │       │
    │       ├──▶ 创建 FrameCodec 实例
    │       │
    │       └──▶ 设置状态为 IDLE
    │
    ├──▶ 初始化 ControlPanel (隐藏状态)
    │
    ├──▶ 注册键盘事件
    │       │
    │       ├──▶ ESC: 切换控制面板显示
    │       ├──▶ Space: 开始/暂停
    │       └──▶ Q: 退出
    │
    ├──▶ 启动帧调度定时器
    │       │
    │       └──▶ 每 frameIntervalMs 触发一次 onFrameTick()
    │
    └──▶ 进入 IDLE 状态，显示 IDLE 帧
```

### 2.2 IDLE 状态运行逻辑

```
状态: IDLE
────────────────────────────────────

主循环 (由定时器驱动):

onFrameTick():
    │
    ├──▶ if (state == IDLE)
    │       │
    │       └──▶ 显示预生成的 IDLE 帧图像
    │
    └──▶ 等待用户操作

用户操作: 选择文件
    │
    ▼
onFileSelected(File file):
    │
    ├──▶ 保存文件引用
    │
    ├──▶ 设置状态为 PREPARING
    │
    └──▶ 启动准备线程
```

### 2.3 PREPARING 状态运行逻辑

```
状态: PREPARING
────────────────────────────────────

准备线程:

PrepareTask.run():
    │
    ├──▶ 读取文件
    │
    ├──▶ 计算 SHA-256
    │       │
    │       └──▶ 更新 UI 进度 "计算校验和..."
    │
    ├──▶ 计算分块数量
    │       │
    │       totalFrames = ceil(fileSize / PAYLOAD_SIZE)
    │
    ├──▶ 生成 START 帧图像
    │       │
    │       ├──▶ 构建 START 帧数据
    │       │       - 文件名
    │       │       - 文件大小
    │       │       - 总帧数
    │       │       - SHA-256
    │       │
    │       └──▶ FrameCodec.encodeStartFrame(data) → BufferedImage
    │
    ├──▶ 生成所有 DATA 帧图像
    │       │
    │       for i in 0..totalFrames:
    │           │
    │           ├──▶ 读取文件块 (offset = i * PAYLOAD_SIZE)
    │           │
    │           ├──▶ 添加 RS 纠错码
    │           │
    │           ├──▶ FrameCodec.encodeDataFrame(i, data) → BufferedImage
    │           │
    │           ├──▶ 存入帧列表 frames[i]
    │           │
    │           └──▶ 更新 UI 进度 "编码帧 i/totalFrames"
    │
    ├──▶ 生成 EOF 帧图像
    │       │
    │       └──▶ FrameCodec.encodeEofFrame(totalFrames, sha256) → BufferedImage
    │
    ├──▶ 通知主线程准备完成
    │
    └──▶ 设置状态为 SENDING_START
```

### 2.4 SENDING_START 状态运行逻辑

```
状态: SENDING_START
────────────────────────────────────

变量:
    startRepeatCount = 0
    START_REPEAT_TIMES = 5

主循环:

onFrameTick():
    │
    ├──▶ 显示 START 帧图像
    │
    ├──▶ startRepeatCount++
    │
    └──▶ if (startRepeatCount >= START_REPEAT_TIMES)
            │
            ├──▶ startRepeatCount = 0
            │
            ├──▶ currentFrameIndex = 0
            │
            ├──▶ dataRepeatCount = 0
            │
            └──▶ 设置状态为 SENDING_DATA
```

### 2.5 SENDING_DATA 状态运行逻辑

```
状态: SENDING_DATA
────────────────────────────────────

变量:
    currentFrameIndex = 0      // 当前帧序号
    dataRepeatCount = 0        // 当前帧已重复次数
    DATA_REPEAT_TIMES = 3      // 每帧重复次数

主循环:

onFrameTick():
    │
    ├──▶ 显示 frames[currentFrameIndex]
    │
    ├──▶ dataRepeatCount++
    │
    ├──▶ 更新 UI 进度
    │
    └──▶ if (dataRepeatCount >= DATA_REPEAT_TIMES)
            │
            ├──▶ dataRepeatCount = 0
            │
            ├──▶ currentFrameIndex++
            │
            └──▶ if (currentFrameIndex >= totalFrames)
                    │
                    ├──▶ eofRepeatCount = 0
                    │
                    └──▶ 设置状态为 SENDING_EOF

用户操作: 取消
    │
    └──▶ 设置状态为 IDLE
```

### 2.6 SENDING_EOF 状态运行逻辑

```
状态: SENDING_EOF
────────────────────────────────────

变量:
    eofRepeatCount = 0
    EOF_REPEAT_TIMES = 5

主循环:

onFrameTick():
    │
    ├──▶ 显示 EOF 帧图像
    │
    ├──▶ eofRepeatCount++
    │
    └──▶ if (eofRepeatCount >= EOF_REPEAT_TIMES)
            │
            ├──▶ 显示 "发送完成" 消息
            │
            ├──▶ 清理帧缓存
            │
            └──▶ 设置状态为 IDLE
```

---

## 3. Argus 运行逻辑

### 3.1 启动流程

```
ReceiverApp.main()
    │
    ▼
初始化主窗口
    │
    ├──▶ 创建 UI 组件
    │       - 设备选择下拉框
    │       - 预览面板
    │       - 状态显示
    │       - 进度条
    │
    ├──▶ 枚举采集设备
    │       │
    │       └──▶ CaptureDevice.listDevices() → List<DeviceInfo>
    │
    ├──▶ 初始化 ReceiverController
    │       │
    │       ├──▶ 创建 FrameDetector
    │       │
    │       ├──▶ 创建 FrameCodec
    │       │
    │       ├──▶ 创建 FileAssembler
    │       │
    │       └──▶ 设置状态为 SCANNING
    │
    └──▶ 等待用户选择设备
```

### 3.2 设备选择与启动

```
用户操作: 选择采集设备并点击"开始接收"
    │
    ▼
onStartCapture(deviceId):
    │
    ├──▶ CaptureDevice.open(deviceId)
    │       │
    │       ├──▶ 设置分辨率 1920x1080
    │       │
    │       └──▶ 设置帧率 30fps (或设备支持的最大值)
    │
    ├──▶ 启动采集线程
    │       │
    │       └──▶ CaptureThread.start()
    │
    ├──▶ 启动分析线程
    │       │
    │       └──▶ AnalyzerThread.start()
    │
    └──▶ 设置状态为 SCANNING
```

### 3.3 采集线程逻辑

```
CaptureThread.run():
────────────────────────────────────

while (!stopped):
    │
    ├──▶ Mat frame = captureDevice.read()
    │
    ├──▶ if (frame == null || frame.empty())
    │       │
    │       └──▶ continue (跳过空帧)
    │
    ├──▶ 转换为 BufferedImage
    │
    ├──▶ 放入帧队列 (frameQueue.offer(image))
    │       │
    │       └──▶ 如果队列满，丢弃旧帧
    │
    └──▶ 更新预览面板 (降采样显示)
```

### 3.4 SCANNING 状态运行逻辑

```
状态: SCANNING
────────────────────────────────────

分析线程:

AnalyzerThread.run():
    │
    while (!stopped):
        │
        ├──▶ BufferedImage frame = frameQueue.poll(timeout)
        │
        ├──▶ if (frame == null)
        │       │
        │       └──▶ continue
        │
        ├──▶ DetectionResult result = frameDetector.detect(frame)
        │       │
        │       ├──▶ 检测四角定位标
        │       │       │
        │       │       isTopLeftBlack = checkCorner(frame, TOP_LEFT, BLACK)
        │       │       isTopRightWhite = checkCorner(frame, TOP_RIGHT, WHITE)
        │       │       isBotLeftWhite = checkCorner(frame, BOT_LEFT, WHITE)
        │       │       isBotRightBlack = checkCorner(frame, BOT_RIGHT, BLACK)
        │       │       │
        │       │       └──▶ 全部匹配 → 有效帧
        │       │
        │       ├──▶ 如果无效帧
        │       │       │
        │       │       └──▶ continue
        │       │
        │       └──▶ 计算边界偏移量
        │
        ├──▶ 解码帧头
        │       │
        │       FrameHeader header = frameCodec.decodeHeader(frame, result.bounds)
        │       │
        │       ├──▶ 验证魔数 (0x44, 0x56)
        │       │       │
        │       │       └──▶ 不匹配 → continue
        │       │
        │       └──▶ 提取帧类型
        │
        └──▶ 根据帧类型处理
                │
                ├──▶ IDLE:
                │       │
                │       ├──▶ 更新 UI "已连接 - 等待传输"
                │       │
                │       └──▶ 设置状态为 CONNECTED
                │
                ├──▶ START:
                │       │
                │       └──▶ 处理 START 帧 (见 3.5)
                │
                ├──▶ DATA:
                │       │
                │       └──▶ 忽略 (没有文件信息)
                │
                └──▶ EOF:
                        │
                        └──▶ 忽略 (没有正在接收的文件)
```

### 3.5 CONNECTED 状态运行逻辑

```
状态: CONNECTED
────────────────────────────────────

分析线程继续运行:

收到帧类型判断:
    │
    ├──▶ IDLE:
    │       │
    │       └──▶ 保持 CONNECTED 状态
    │
    ├──▶ START:
    │       │
    │       ├──▶ 解码 START 帧数据
    │       │       │
    │       │       ├──▶ 提取文件名
    │       │       ├──▶ 提取文件大小
    │       │       ├──▶ 提取总帧数
    │       │       └──▶ 提取 SHA-256
    │       │
    │       ├──▶ 初始化 FileAssembler
    │       │       │
    │       │       fileAssembler.init(fileName, fileSize, totalFrames, sha256)
    │       │       │
    │       │       └──▶ 创建帧接收位图 receivedFrames[totalFrames]
    │       │
    │       ├──▶ 更新 UI
    │       │       │
    │       │       └──▶ "开始接收: filename (size)"
    │       │
    │       └──▶ 设置状态为 RECEIVING
    │
    ├──▶ DATA:
    │       │
    │       └──▶ 忽略 (等待 START)
    │
    └──▶ EOF:
            │
            └──▶ 忽略
```

### 3.6 RECEIVING 状态运行逻辑

```
状态: RECEIVING
────────────────────────────────────

变量:
    receivedFrames = boolean[totalFrames]  // 帧接收标记
    frameDataMap = Map<Integer, byte[]>    // 帧数据缓存
    receivedCount = 0                      // 已接收帧数
    lastFrameTime = currentTime            // 上次收帧时间

分析线程:

收到帧类型判断:
    │
    ├──▶ IDLE:
    │       │
    │       └──▶ 忽略 (继续等待数据)
    │
    ├──▶ START:
    │       │
    │       ├──▶ 比较文件信息
    │       │       │
    │       │       ├──▶ 相同文件 → 忽略 (重复的 START)
    │       │       │
    │       │       └──▶ 不同文件 →
    │       │               │
    │       │               ├──▶ 丢弃当前接收数据
    │       │               │
    │       │               └──▶ 重新初始化，开始新文件
    │       │
    │       └──▶ 更新 lastFrameTime
    │
    ├──▶ DATA:
    │       │
    │       ├──▶ 解码帧数据
    │       │       │
    │       │       ├──▶ 提取帧序号
    │       │       │
    │       │       ├──▶ 提取数据内容
    │       │       │
    │       │       ├──▶ 验证 CRC32
    │       │       │       │
    │       │       │       └──▶ 失败 → 丢弃，等待重复帧
    │       │       │
    │       │       └──▶ RS 纠错解码
    │       │
    │       ├──▶ 检查是否重复帧
    │       │       │
    │       │       if (receivedFrames[frameIndex])
    │       │           │
    │       │           └──▶ 忽略 (去重)
    │       │
    │       ├──▶ 存储帧数据
    │       │       │
    │       │       frameDataMap.put(frameIndex, data)
    │       │       receivedFrames[frameIndex] = true
    │       │       receivedCount++
    │       │
    │       ├──▶ 更新 UI 进度
    │       │       │
    │       │       progress = receivedCount / totalFrames
    │       │
    │       └──▶ 更新 lastFrameTime
    │
    └──▶ EOF:
            │
            ├──▶ 解码 EOF 数据
            │       │
            │       ├──▶ 提取总帧数
            │       │
            │       └──▶ 提取 SHA-256
            │
            ├──▶ 设置状态为 ASSEMBLING
            │
            └──▶ 启动重组线程

超时检测 (在分析线程中):

    if (currentTime - lastFrameTime > FRAME_TIMEOUT):
        │
        ├──▶ 显示警告 "接收超时"
        │
        └──▶ 继续等待 (不中断接收)
```

### 3.7 ASSEMBLING 状态运行逻辑

```
状态: ASSEMBLING
────────────────────────────────────

重组线程:

AssemblerTask.run():
    │
    ├──▶ 检查丢失帧
    │       │
    │       missingFrames = []
    │       for i in 0..totalFrames:
    │           if (!receivedFrames[i]):
    │               missingFrames.add(i)
    │       │
    │       └──▶ if (missingFrames.isNotEmpty())
    │               │
    │               ├──▶ 更新 UI "丢失帧: [列表]"
    │               │
    │               ├──▶ 设置状态为 ERROR
    │               │
    │               └──▶ return
    │
    ├──▶ 重组文件
    │       │
    │       outputFile = new File(savePath, fileName)
    │       outputStream = new FileOutputStream(outputFile)
    │       │
    │       for i in 0..totalFrames:
    │           │
    │           data = frameDataMap.get(i)
    │           outputStream.write(data)
    │           │
    │           └──▶ 更新 UI "重组进度 i/totalFrames"
    │       │
    │       outputStream.close()
    │
    ├──▶ 计算接收文件的 SHA-256
    │       │
    │       actualSha256 = calculateSha256(outputFile)
    │
    ├──▶ 验证校验和
    │       │
    │       if (actualSha256 != expectedSha256):
    │           │
    │           ├──▶ 更新 UI "校验失败"
    │           │       │
    │           │       ├──▶ 期望: expectedSha256
    │           │       └──▶ 实际: actualSha256
    │           │
    │           ├──▶ 设置状态为 ERROR
    │           │
    │           └──▶ return
    │
    ├──▶ 成功
    │       │
    │       ├──▶ 更新 UI "接收完成: fileName"
    │       │
    │       ├──▶ 清理缓存
    │       │       │
    │       │       frameDataMap.clear()
    │       │       receivedFrames = null
    │       │
    │       └──▶ 设置状态为 COMPLETE
    │
    └──▶ 等待用户确认
            │
            └──▶ 用户点击"继续" → 设置状态为 CONNECTED
```

---

## 4. 核心算法

### 4.1 帧检测算法 (FrameDetector)

```
输入: BufferedImage capturedFrame
输出: DetectionResult { isValid, bounds, offset }

detect(frame):
    │
    ├──▶ 定义预期角标位置 (基于标准 1080p)
    │       │
    │       expectedTopLeft  = (MARGIN, MARGIN)
    │       expectedTopRight = (WIDTH - MARGIN - CORNER_SIZE, MARGIN)
    │       expectedBotLeft  = (MARGIN, HEIGHT - MARGIN - CORNER_SIZE)
    │       expectedBotRight = (WIDTH - MARGIN - CORNER_SIZE, HEIGHT - MARGIN - CORNER_SIZE)
    │
    ├──▶ 搜索角标 (允许 ±8 像素偏移)
    │       │
    │       for dx in -8..8:
    │           for dy in -8..8:
    │               │
    │               topLeftMatch = checkBlackCorner(frame, expectedTopLeft + (dx, dy))
    │               topRightMatch = checkWhiteCorner(frame, expectedTopRight + (dx, dy))
    │               botLeftMatch = checkWhiteCorner(frame, expectedBotLeft + (dx, dy))
    │               botRightMatch = checkBlackCorner(frame, expectedBotRight + (dx, dy))
    │               │
    │               if (allMatch):
    │                   │
    │                   └──▶ return DetectionResult(true, computeBounds(dx, dy), (dx, dy))
    │
    └──▶ return DetectionResult(false, null, null)


checkBlackCorner(frame, position):
    │
    ├──▶ 采样角标区域中心 16x16 像素
    │
    ├──▶ 计算平均亮度
    │
    └──▶ return (avgBrightness < BLACK_THRESHOLD)  // BLACK_THRESHOLD = 64


checkWhiteCorner(frame, position):
    │
    ├──▶ 采样角标区域中心 16x16 像素
    │
    ├──▶ 计算平均亮度
    │
    └──▶ return (avgBrightness > WHITE_THRESHOLD)  // WHITE_THRESHOLD = 192
```

### 4.2 块编码算法 (BlockCodec)

```
编码 (bit → 像素块):

encodeBlock(bit):
    │
    ├──▶ 创建 8x8 像素块
    │
    └──▶ if (bit == 0):
            填充所有像素为 0 (黑)
         else:
            填充所有像素为 255 (白)


解码 (像素块 → bit):

decodeBlock(frame, blockX, blockY):
    │
    ├──▶ 计算块的像素坐标
    │       │
    │       pixelX = blockX * BLOCK_SIZE
    │       pixelY = blockY * BLOCK_SIZE
    │
    ├──▶ 采样块内所有像素的亮度
    │       │
    │       sum = 0
    │       for x in 0..BLOCK_SIZE:
    │           for y in 0..BLOCK_SIZE:
    │               pixel = frame.getRGB(pixelX + x, pixelY + y)
    │               brightness = (red(pixel) + green(pixel) + blue(pixel)) / 3
    │               sum += brightness
    │
    ├──▶ 计算平均亮度
    │       │
    │       avg = sum / (BLOCK_SIZE * BLOCK_SIZE)
    │
    └──▶ return (avg >= 128) ? 1 : 0
```

### 4.3 帧编码算法 (FrameCodec)

```
编码数据帧:

encodeDataFrame(frameIndex, payload):
    │
    ├──▶ 创建 1920x1080 BufferedImage
    │
    ├──▶ 填充安全边距 (灰色)
    │
    ├──▶ 绘制四角定位标
    │       │
    │       drawCorner(TOP_LEFT, BLACK)
    │       drawCorner(TOP_RIGHT, WHITE)
    │       drawCorner(BOT_LEFT, WHITE)
    │       drawCorner(BOT_RIGHT, BLACK)
    │
    ├──▶ 编码帧头
    │       │
    │       header = [MAGIC, TYPE_DATA, frameIndex, payload.length, 0]
    │       headerBits = bytesToBits(header)
    │       │
    │       for i, bit in headerBits:
    │           blockX = HEADER_START_X + (i % BLOCKS_PER_ROW)
    │           blockY = HEADER_START_Y + (i / BLOCKS_PER_ROW)
    │           encodeBlockAt(image, blockX, blockY, bit)
    │
    ├──▶ 编码数据区
    │       │
    │       dataBits = bytesToBits(payload)
    │       │
    │       for i, bit in dataBits:
    │           blockX = DATA_START_X + (i % DATA_BLOCKS_PER_ROW)
    │           blockY = DATA_START_Y + (i / DATA_BLOCKS_PER_ROW)
    │           encodeBlockAt(image, blockX, blockY, bit)
    │
    ├──▶ 计算并编码 CRC32
    │       │
    │       crc = CRC32(payload)
    │       crcBits = bytesToBits(crc)
    │       // 编码到校验区域
    │
    └──▶ return image


解码数据帧:

decodeDataFrame(frame, bounds):
    │
    ├──▶ 解码帧头
    │       │
    │       headerBits = []
    │       for i in 0..HEADER_BITS:
    │           blockX = HEADER_START_X + (i % BLOCKS_PER_ROW)
    │           blockY = HEADER_START_Y + (i / BLOCKS_PER_ROW)
    │           bit = decodeBlock(frame, blockX + bounds.offsetX, blockY + bounds.offsetY)
    │           headerBits.add(bit)
    │       │
    │       header = bitsToBytes(headerBits)
    │       │
    │       ├──▶ 验证魔数
    │       ├──▶ 提取帧类型
    │       ├──▶ 提取帧序号
    │       └──▶ 提取数据长度
    │
    ├──▶ 解码数据区
    │       │
    │       dataBits = []
    │       for i in 0..(dataLength * 8):
    │           // 类似帧头解码
    │       │
    │       data = bitsToBytes(dataBits)
    │
    ├──▶ 解码并验证 CRC32
    │       │
    │       expectedCrc = decodeCrc(frame, bounds)
    │       actualCrc = CRC32(data)
    │       │
    │       if (expectedCrc != actualCrc):
    │           throw CrcMismatchException
    │
    └──▶ return DataFrame(frameIndex, data)
```

---

## 5. 数据流向

### 5.1 发送端数据流

```
文件 (bytes)
    │
    ▼
FileChunker.chunk()
    │ 分成 ~2KB 块
    ▼
┌─────────────────────────────────────────┐
│ Chunk 0  │ Chunk 1  │ ... │ Chunk N-1  │
└─────────────────────────────────────────┘
    │
    │ 每块添加 RS 纠错码
    ▼
┌─────────────────────────────────────────┐
│ Chunk+RS │ Chunk+RS │ ... │ Chunk+RS   │
└─────────────────────────────────────────┘
    │
    ▼ FrameCodec.encode()
┌─────────────────────────────────────────┐
│ Frame 0  │ Frame 1  │ ... │ Frame N-1  │
│ (Image)  │ (Image)  │     │ (Image)    │
└─────────────────────────────────────────┘
    │
    │ 每帧重复 3 次显示
    ▼
屏幕输出 → HDMI
```

### 5.2 接收端数据流

```
HDMI → 采集卡
    │
    ▼
CaptureDevice.read()
    │ BufferedImage
    ▼
FrameDetector.detect()
    │ 检测有效帧
    ▼
FrameCodec.decode()
    │ 解码数据
    ▼
┌─────────────────────────────────────────┐
│ frameDataMap                            │
│ {0: data, 1: data, ..., N-1: data}     │
└─────────────────────────────────────────┘
    │
    │ RS 纠错解码
    ▼
FileAssembler.assemble()
    │ 按序号拼接
    ▼
文件 (bytes)
    │
    │ 验证 SHA-256
    ▼
写入磁盘
```

---

## 6. 错误处理

### 6.1 Hermes 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| 文件读取失败 | 显示错误消息，返回 IDLE |
| 文件过大 (超过帧数限制) | 提示用户，拒绝发送 |
| 编码异常 | 记录日志，尝试继续 |
| 用户取消 | 中断发送，返回 IDLE |

### 6.2 Argus 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| 采集设备打开失败 | 显示错误，提示检查设备 |
| 帧解码 CRC 失败 | 丢弃该帧，等待重复帧 |
| RS 纠错失败 | 丢弃该帧，等待重复帧 |
| 帧接收超时 (10s) | 显示警告，继续等待 |
| 连接超时 (60s) | 返回 SCANNING |
| 帧丢失 | 显示丢失帧列表，状态 ERROR |
| SHA-256 不匹配 | 显示校验失败，状态 ERROR |

---

## 7. 配置项

```java
public class Config {
    // 帧布局
    static final int FRAME_WIDTH = 1920;
    static final int FRAME_HEIGHT = 1080;
    static final int BLOCK_SIZE = 8;
    static final int SAFE_MARGIN = 16;
    static final int CORNER_SIZE = 32;

    // 协议
    static final byte[] MAGIC = {0x44, 0x56};  // "DV"
    static final int HEADER_SIZE = 10;

    // 发送参数
    static final int START_REPEAT = 5;
    static final int DATA_REPEAT = 3;
    static final int EOF_REPEAT = 5;
    static final int FRAME_INTERVAL_MS = 50;
    static final int IDLE_INTERVAL_MS = 200;

    // 接收参数
    static final int FRAME_TIMEOUT_SEC = 10;
    static final int CONNECTION_TIMEOUT_SEC = 60;
    static final int FRAME_QUEUE_SIZE = 10;

    // 检测阈值
    static final int BLACK_THRESHOLD = 64;
    static final int WHITE_THRESHOLD = 192;
    static final int CORNER_SEARCH_RANGE = 8;
}
```
