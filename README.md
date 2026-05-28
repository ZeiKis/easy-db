# easy-db

简易键值数据库 —— 基于 Java 实现的轻量级 KV 存储引擎。

## 特性

- **KV 存储**：支持 SET / GET / RM 基本操作，Value 支持泛型（List、Map 等数据类型）
- **Append-Log 持久化**：参考 WAL 和 MySQL RedoLog 设计，所有写操作以追加方式写入磁盘
- **内存索引**：基于 HashMap 的哈希索引，O(1) 查询
- **MemTable 缓存**：TreeMap 内存写缓冲，达到阈值后批量刷盘
- **WAL 日志**：Write-Ahead Log 保证崩溃恢复，启动时自动回放
- **文件轮转与压缩**：数据文件超过阈值自动轮转，后台异步压缩去重
- **C/S 架构**：基于 Java Socket 的网络通信，支持多客户端并发
- **主从复制**：Master-Slave 集群模式，Master 转发写操作到 Slave
- **交互式 Shell**：基于 JLine 的命令行终端，支持 readline
- **认证机制**：客户端登录认证
- **并发安全**：ReentrantReadWriteLock 读写锁，线程安全

## 快速开始

### 环境要求

- JDK 11+
- Maven 3.6+

### 构建

```bash
mvn clean package -DskipTests
```

### 启动服务端

```bash
# 启动 Master 节点（端口 12345，转发到 Slave 12346）
java -cp target/easy-db-0.0.1-SNAPSHOT.jar example.SocketServerUsage

# 启动 Slave 节点（端口 12346）
java -cp target/easy-db-0.0.1-SNAPSHOT.jar example.SlaveSocketServerUsage
```

### 启动交互式 Shell

```bash
java -jar target/easy-db-0.0.1-SNAPSHOT.jar
```

Shell 命令：

```
set <key> <value>   写入键值
get <key>           查询键值
rm <key>            删除键值
help                查看帮助
quit / exit         退出
```

### 命令行客户端

```bash
# 单条命令模式
java -cp target/easy-db-0.0.1-SNAPSHOT.jar client.CliClient set mykey myvalue
java -cp target/easy-db-0.0.1-SNAPSHOT.jar client.CliClient get mykey

# 交互模式
java -cp target/easy-db-0.0.1-SNAPSHOT.jar client.CliClient
```

### 编程式调用

```java
// 直接使用 Store
Store store = new NormalStore("data");
store.set("key1", "value1");
String value = store.get("key1", String.class);
store.rm("key1");

// 通过 Socket 客户端
SocketClient client = new SocketClient("localhost", 12345);
client.login("admin", "admin123");
client.set("key1", "value1");
String val = client.get("key1");
client.rm("key1");
```

## 整体架构

```
┌──────────────┐     ┌──────────────────────────────────┐
│  EasyDBShell │────▶│  SocketServerController (Master) │
│  (JLine)     │     │         port: 12345              │
└──────────────┘     └──────────┬───────────────────────┘
                                │ 转发写操作
                    ┌───────────▼───────────────────────┐
                    │  SlaveSocketServerController      │
                    │         port: 12346               │
                    └──────────┬───────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   SetActionHandler    GetActionHandler    RmActionHandler
          │                    │                    │
          └────────────────────┼────────────────────┘
                               ▼
                    ┌──────────────────┐
                    │   NormalStore    │
                    │  (存储引擎核心)   │
                    ├──────────────────┤
                    │  MemTable (缓存) │
                    │  Index (索引)    │
                    │  WAL (预写日志)  │
                    └──────────────────┘
```

## 项目结构

```
src/main/java/
├── client/                  # 客户端
│   ├── Client.java          # 客户端接口
│   ├── SocketClient.java    # Socket 客户端实现
│   └── CliClient.java       # 命令行客户端
├── controller/              # 服务端控制器
│   ├── SocketServerController.java    # Master 控制器
│   ├── SocketServerHandler.java       # Master 请求处理
│   ├── SlaveSocketServerController.java  # Slave 控制器
│   └── SlaveSocketServerHandler.java     # Slave 请求处理
├── dto/                     # 数据传输对象
│   ├── ActionDTO.java       # 请求体
│   ├── RespDTO.java         # 响应体
│   ├── ActionTypeEnum.java  # 操作类型枚举
│   └── RespStatusTypeEnum.java  # 响应状态枚举
├── handler/action/          # 操作处理器（策略模式）
│   ├── ActionHandler.java   # 策略接口
│   ├── ActionHandlerFactory.java  # 处理器工厂
│   ├── SetActionHandler.java
│   ├── GetActionHandler.java
│   ├── RmActionHandler.java
│   └── LoginActionHandler.java
├── model/command/           # 数据命令模型
│   ├── Command.java         # 命令接口
│   ├── AbstractCommand.java # 抽象基类
│   ├── SetCommand.java      # SET 命令
│   ├── RmCommand.java       # RM 命令（标记删除）
│   ├── CommandPos.java      # 索引条目（偏移量+长度）
│   └── CommandTypeEnum.java
├── service/                 # 存储引擎
│   ├── Store.java           # 存储接口
│   ├── NormalStore.java     # 存储引擎实现（Master）
│   ├── SlaveNormalStore.java # 存储引擎实现（Slave）
│   └── WalLog.java          # WAL 预写日志
├── shell/
│   └── EasyDBShell.java     # 交互式 Shell 入口
├── utils/
│   ├── RandomAccessFileUtil.java  # 随机文件读写工具
│   ├── CommandUtil.java           # 命令序列化工具
│   ├── CompressorUtil.java        # 文件压缩工具
│   └── LoggerUtil.java            # 日志工具
└── example/
    ├── SocketServerUsage.java       # 启动 Master
    ├── SlaveSocketServerUsage.java  # 启动 Slave
    ├── SocketClientUsage.java       # 客户端示例
    └── StoreUsage.java              # 直接使用 Store
```

## 存储设计

### 磁盘格式

数据文件采用长度前缀 + JSON 的方式存储：

```
[4字节 int: 命令长度][N字节 JSON: 命令内容]
```

### 索引

内存中维护 `HashMap<String, CommandPos>`，记录每个 key 对应的文件偏移量和长度，实现 O(1) 查询。

### 写流程

1. 将 SET/RM 命令写入 WAL 日志
2. 写入 MemTable（TreeMap 内存缓存）
3. 更新 HashMap 索引
4. MemTable 达到阈值（1000 条）后批量刷入 `.table` 数据文件
5. 数据文件超过大小限制后自动轮转，旧文件异步压缩

### 删除策略

删除操作通过追加一条 `RmCommand` 实现标记删除，查询时若最新命令类型为 RM 则返回 null。文件压缩时自动清理已删除的键。

### 冷启动

服务重启时通过读取所有 `.table` 文件和回放 WAL 日志重建内存索引。

## 认证

默认账户：

| 用户名 | 密码 |
|--------|------|
| admin  | admin123 |
| user   | user123 |

## 许可证

MIT License
