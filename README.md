# DOV — Data Over Video（通过画面传输数据）

DOV 是一个“基于画面”的文件传输协议与原型实现：
- 在发送端（A 电脑）把文件内容编码到每一帧图像的像素中，通过 HDMI 输出；
- 在接收端（B 电脑）用采集卡接收 A 的画面，逐帧解析像素，恢复出原始文件。

本仓库当前包含：
- 发送端 sender：基于 LWJGL/OpenGL 的原型，已能把文件映射成像素并渲染到窗口；
- 接收端 receiver：尚未实现（计划基于视频采集流逐帧解码）。

---

## 工作原理（原型版）

- 分辨率与帧率：默认 1920×1080 @ 60fps（见 `ProtocolConfig.defaults()`）。
- 映射关系：
  - 每个像素承载 1 字节有效载荷；该字节以灰度形式写入三个通道，即 RGB = (b, b, b)。
  - 一帧的有效载荷上限为 `width * height` 字节（1080p ≈ 2,073,600 字节/帧）。
  - 未填满的尾部像素使用中灰 0x80 填充，以降低压缩/量化导致的极端黑白失真。
- 渲染：
  - 使用 OpenGL 生成 2D 纹理，将编码后的像素缓冲区上传并全屏绘制。
  - 关闭 VSYNC（`glfwSwapInterval(0)`），以自定义帧率同步（`FrameRateController`）。
- FEC：占位（`FECEncoder` 当前为直通）。后续将引入跨帧的 Reed–Solomon 纠删码以增强鲁棒性。

重要说明：当前原型未注入“文件头/帧头/校验/EOF 标志”。发送端按顺序把文件切成若干帧后即退出窗口。这意味着接收端在实现时需要在协议中加入明确的帧结构与校验机制（见下文“协议草案 v0.1”）。

---

## 仓库结构

- `pom.xml`：父工程（Maven 多模块）。
- `sender/`：发送端原型（Java 21 + LWJGL 3）。
  - `AppMain`：入口；
  - `FileSender`：主控循环（读文件→分帧→编码→渲染→限速）；
  - `FrameEncoder`：把每帧有效载荷编码为 RGB 像素缓冲区；
  - `GLRenderer`：GLFW/OpenGL 渲染；
  - `FrameRateController`：帧率控制；
  - `FECEncoder`：FEC 占位；
  - `ProtocolConfig`：分辨率、FPS、每帧载荷大小等协议参数。
- `receiver/`：接收端占位（待实现）。

---

## 快速开始（发送端）

前置条件：
- JDK 21
- Maven 3.9+
- 图形环境可创建 OpenGL 上下文
- 采集链路建议：A（发送端）HDMI 输出 → 采集卡 → B（接收端）

构建：

```bash
# 仅构建 sender 模块
mvn -q -pl sender -am package
```

运行（不修改 POM 的方式，直接用 exec 插件坐标）：

```bash
# 通过 Maven 运行主类（无需事先在 POM 中声明插件）
mvn -q -pl sender org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.cheng.dovsender.AppMain \
  -Dexec.args="/path/to/file/to/send"
```

运行后会弹出一个 `1920×1080` 的窗口，窗口画面即为编码后的数据帧。将该窗口拖到连接采集卡的显示输出，确保 1:1 像素显示（详见下方“显示与采集注意事项”）。

提示：`sender/pom.xml` 里默认的 LWJGL 原生库为 `natives-macos-arm64`。如果在其它平台运行，需要将属性 `lwjgl.natives` 调整为对应值，例如：
- Windows x86_64：`natives-windows` 或 `natives-windows-x86_64`
- Linux x86_64：`natives-linux` 或 `natives-linux-x86_64`

---

## 协议草案 v0.1（稳定优先的最小可用版）

- 目标与取舍：
  - 简易优先：发送端/接收端一周内可实现端到端。
  - 稳定为先：在常见采集链路（YUV、限幅、轻度压缩、轻微模糊）下尽量零误码。
  - 可演进：预留字段便于后续加入跨帧 FEC、花屏检测与几何校准。

- 帧级布局：
  - 顶部“帧头区”+ 其下“负载区”。
  - 像素即字节：基础映射为“1 像素承载 1 字节”，以灰度写入 `R=G=B`。
  - 安全灰度 LUT：`enc = 16 + round(byte * 219/255)`；解码：`dec = clamp(round((y-16) * 255/219))`。
  - 冗余：帧头区“横向×2、纵向×3”重复，多数投票恢复；负载区默认“横向×1”。

- 帧头结构（定长 64 字节，Big‑Endian）：
  - `MAGIC[4]`：ASCII `"DOV1"`。
  - `VER[1]`：协议版本，`0x01`。
  - `FLAGS[1]`：bit0=EOF，bit1=FIRST，其余保留 0。
  - `SESSION_ID[8]`：会话 ID（随机）。
  - `FRAME_IDX[4]`：当前帧序号（从 0 起）。
  - `TOTAL_FRAMES[4]`：总帧数（未知时置 0；FIRST/EOF 必填）。
  - `PAYLOAD_LEN[4]`：本帧有效负载字节数。
  - `FILE_SIZE[8]`：文件总字节数（FIRST/EOF 必填）。
  - `FILE_NAME_HASH[8]`：文件名/路径哈希（SHA-256 截断 8 字节）。
  - `PAYLOAD_CRC32C[4]`：本帧负载 CRC32C。
  - `RESERVED[6]`：保留 0。
  - `HDR_CRC16[2]`：以上 62 字节的 CRC16-CCITT。
  - 放置：64B 头按“横向×2”写满一行，再纵向复制 3 行（共 3×64×2 像素）。解码端先对横向副本投票，再对 3 行投票，最后做 `HDR_CRC16` 校验。

- 负载区映射：
  - 行优先，逐像素写入 `R=G=B=enc(byte)`。
  - 可选“横向×2”模式：同一字节在相邻 2 像素重复；解码以多数投票恢复。
  - 每帧负载上限：`max_payload = (width * (height - header_rows)) / payload_hrep`。
  - 未填满部分：使用 LUT 后的中灰（对应原值 0x80）填充。

- 文件/会话流转：
  - FIRST 帧：`FLAGS.FIRST=1`，携带 `FILE_SIZE/TOTAL_FRAMES/FILE_NAME_HASH`。
  - 正常帧：填写 `FRAME_IDX/PAYLOAD_LEN/PAYLOAD_CRC32C`。
  - EOF：`FLAGS.EOF=1`，可为最后一帧或独立结束帧（`PAYLOAD_LEN=0`），重复携带 `FILE_SIZE`。
  - 去重与补洞：以 `SESSION_ID + FRAME_IDX` 去重；CRC 失败的帧丢弃并等待轮播补全。

- 误码与稳健性：
  - LUT 将有效值压入视频级安全范围 16–235；解码时反量化并 clamp。
  - 多数投票分别对“横向复制/纵向复制”应用；最终以 CRC 校验落判。
  - 建议 1:1 像素显示，关闭缩放/HDR/自动增强；压缩采集时尽量使用低滤镜、恒定质量模式。

- 时序与节流：
  - 帧率与输出刷新率一致（60/30fps）。
  - 允许发送端“顺序一次 + 轮播若干次”以弥补无回传的掉帧/误码。

- 接收端状态机（最小实现）：
  - 搜索同步：在顶部 3 行中通过多数投票找 `MAGIC` 且 `HDR_CRC16` 正确即锁定。
  - 解析头部：读取字段并建立/确认会话。
  - 还原负载：按横向复制策略逆映射，计算 CRC32C 验证。
  - 重组文件：按 `FRAME_IDX` 写入；洞位等待轮播补齐。
  - 结束：收到 `EOF` 且所有帧有效后，校验 `FILE_SIZE` 并落盘。

- 参数建议（默认值，可由 `ProtocolConfig` 注入）：
  - `width/height/fps`：1920×1080×60
  - `header_rows`：3
  - `header_hrep`：2
  - `payload_hrep`：1（遇到缩放/压缩可改为 2）
  - `crc_payload`：CRC32C；`crc_header`：CRC16-CCITT
  - `endianness`：Big‑Endian
  - `magic`：`"DOV1"`

- 向后兼容与扩展位：
  - 版本字段 `VER` 保证升级空间。
  - `FLAGS` 预留位：窗口化 FEC、重传轮次标识、加密标志等。
  - `RESERVED` 保留字段用于未来携带 `WINDOW_ID/K/R`（RS 参数）等。

- 后续 v0.2 方向（不阻塞 v0.1）：
  - 跨帧 FEC：GF(256) 的 RS(K=20, R=4) 窗口编码；头部携带 `WINDOW_ID/K/R` 与窗内索引。
  - 校准图样：角标/定位条纹以检测缩放/旋转并做亚像素对齐。
  - 安全灰度子集：将 0..255 压至 32..223 并避开“易失真值”，提升压缩稳健性。

---

## 显示与采集注意事项

要想让“像素即数据”的方案可靠，链路上的任何缩放、色彩/动态范围调整都会引入误码。请尽量保证：
- 分辨率 1:1：显示器/采集卡输入分辨率与发送端窗口完全一致（默认 1920×1080），避免缩放。
- 关闭 HDR、原彩/夜览、系统 Gamma/色彩管理；采集端设置为全范围或与发送端编码范围一致。
- 发送端窗口尽量使用无边框全屏或确保其像素不被系统缩放；
- 如遇掉帧，可开启 VSYNC 并把 `fps` 配到 30/60 与链路刷新率一致；
- 采集卡如果只支持压缩（H.264/MJPEG），请优先选择“恒定质量/低滤镜/去噪关闭”的模式。

---

## 接收端实现计划（概览）

- 捕获：从采集卡读取视频帧（建议：OpenCV/JavaCV，或 FFmpeg 绑定）。
- 预处理：可选的去 Gamma、去色彩矩阵、限制范围映射回线性灰度；
- 解析帧头：检测 `MAGIC`，读取帧索引/长度/校验；
- 还原负载：把像素灰度反量化为字节，按帧顺序写入文件；
- FEC 恢复：对损坏/丢失帧用校验帧做恢复（v0.2+）。
- 校验收敛：收到 `EOF` 帧后校验整文件摘要并完成写入。

---

## 已知限制（原型阶段）

- 当前代码未实现帧头/校验/EOF，接收端暂不可用；
- 颜色与压缩通道的失真可能导致误码，需通过鲁棒编码和 FEC 解决；
- 目前 `sender/pom.xml` 绑定了 macOS ARM64 原生库，其他平台需改配置；
- 跨平台运行需要正确的 OpenGL 支持与原生依赖。

---

## 开发与调试建议

- 回环测试：在同一台机器上用“屏幕捕获”替代采集卡进行早期验证（接收端需具备从屏幕读取帧的路径）。
- 可视化：在发送端为帧头/校验区着色，便于肉眼检查是否被缩放/模糊。
- 速率：1080p 一帧≈2MB，理论吞吐极高，但实际受采集压缩、USB 带宽、CPU/GPU 编解码限制。

---

## 运行示例

```bash
# 构建 sender
mvn -q -pl sender -am package

# 运行（通过 Maven Exec 插件）
mvn -q -pl sender org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.cheng.dovsender.AppMain \
  -Dexec.args="/Users/you/Downloads/bigfile.bin"
```

窗口关闭即表示发送完成。当前版本不会额外发 EOF 帧。

---

## 贡献

欢迎就协议设计、鲁棒性编码、接收端实现提出建议或 PR。接收端计划优先实现：帧头结构、基础校验、无 FEC 的端到端打通，其后加入 RS 纠删码与更稳健的像素编码。

