# DOV — Data Over Video（通过画面传输数据）

DOV 是一个基于“画面像素”的文件传输工具：Sender 把文件编码为视频帧输出到屏幕/HDMI，Receiver 通过采集卡逐帧解码并还原文件。

## 特性

- 单向 HDMI 传输，不依赖回传通道
- 支持发送文件与文件夹（文件夹自动压缩为 zip，接收端自动解压）
- 接收端显示速率、剩余时间与丢失帧序号
- 手动补发指定帧序号，便于补齐丢失帧
- 参数统一从 `dov.properties` 读取，可外部覆盖

## 仓库结构

- `protocol/`：帧布局、编码/解码、文件分块与重组
- `sender/`：JavaFX 全屏发送端
- `receiver/`：JavaCV + Swing 接收端
- `doc/`：设计与逻辑文档

## 构建

```bash
mvn clean package
```

只构建单个模块：

```bash
mvn -pl sender -am package
mvn -pl receiver -am package
```

## 运行

```bash
java -jar sender/target/sender-1.0-shaded.jar
java -jar receiver/target/receiver-1.0-shaded.jar
```

## 使用说明

### Receiver

1. 选择采集设备并点击“开始接收”。
2. 选择保存目录，保持预览画面 1:1 像素显示。
3. 若显示“等待补帧”，按界面提示的丢失帧序号进行补发。

### Sender

1. 选择文件或文件夹后，会自动倒计时 2 秒开始发送。
2. 传输过程中按 `ESC` 显示控制面板，按 `Q` 退出。
3. 传输完成后进入“可补发”状态，在输入框中填入丢失帧序号（从 0 开始，如 `0,3,5-7`），点击“补发帧”。

## 配置

默认读取仓库根目录的 `dov.properties`，可用参数覆盖：

```bash
java -Ddov.config=/path/to/dov.properties -jar sender/target/sender-1.0-shaded.jar
```

常用参数示例：

- `dov.frameWidth` / `dov.frameHeight`：帧尺寸
- `dov.blockSize`：像素块大小（8 或 4）
- `dov.safeMargin` / `dov.cornerSize`：安全边距与角标尺寸
- `dov.headerRows` / `dov.checksumRows`：帧头/校验区行数
- `dov.targetFps`：发送帧率
- `dov.eofGraceMs`：EOF 后等待补齐时间
- `dov.tailFrames` / `dov.tailRepeat`：尾部加重发送参数

注意：Sender 与 Receiver 的配置必须一致，否则解码会失败或误码率升高。

## 传输提示

- 确保显示与采集分辨率/帧率一致，避免缩放
- 关闭 HDR、色彩增强、锐化等图像处理
- 如果丢帧严重，可降低帧率或增大 `dov.blockSize`

## 文档

- `doc/DESIGN.md`：协议与帧结构设计
- `doc/LOGIC.md`：运行逻辑与线程模型
