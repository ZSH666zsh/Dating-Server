# Dating Server

一套可跑通、可部署的**恋爱社交 App 后端**（形态对标陌陌 / 探探 / Tinder），采用 **Java 21 + Spring Boot 3.3.5** 微服务架构，覆盖用户、匹配、内容、IM、支付、AI 陪聊 6 大领域：7 个 Java 微服务 + 1 个 Python AI 服务。服务间全内网 **gRPC** 通信，共享中间件，统一红线约束。

## 微服务清单

| 服务 | 语言 | 职责 | 对标能力 |
|---|---|---|---|
| **mobile-gateway** | Java | 对外 BFF：REST → 内部 gRPC，JWT 鉴权 / 限流 / 聚合 | App 唯一入口 |
| **user-service** | Java | 用户档案、设备识别、推荐召回 | 注册登录、个人资料 |
| **match-service** | Java | 左右滑、超级喜欢、双向匹配、每日配额 | 探探刷卡配对 |
| **post-service** | Java | 动态发布、Feed 流、点赞、评论、写扩散 | 朋友圈 / 动态 |
| **im-service** | Java | 封装 OpenIM + LiveKit 的消息 / 音视频能力中枢 | 私聊、1v1 通话 |
| **payment-service** | Java | 金币、订阅、PayPal Webhook、订单 | 充值付费 |
| **ai-chat** | Python | LangChain / LangGraph 数字人陪聊 + 图像理解 | AI 虚拟对象 |

## 整体架构

```
                    ┌─────────────┐
   App / H5  ──REST─▶│mobile-gateway│  (BFF：鉴权/限流/协议转换)
                    └──────┬──────┘
              内网 gRPC（Nacos 服务发现 discovery:///xxx）
     ┌────────────┬────────┼────────┬───────────┬──────────┐
     ▼            ▼        ▼        ▼           ▼          ▼
 user-service match-svc post-svc  im-service payment-svc  ai-chat(Py)
     │            │        │        │           │          │
     │            │        │        ▼           ▼          │
     │            │        │   OpenIM/LiveKit  PayPal      │
     └──────┬─────┴────┬───┴──────┬─────────────┬──────────┘
            ▼          ▼          ▼             ▼
         PostgreSQL  Redis    RocketMQ       MinIO
        (唯一关系库) (缓存)   (异步/写扩散)  (对象存储)
                         全部经 Nacos 统一配置 + 服务发现
```

**一次请求流转**（以"刷到 B 并右滑喜欢"为例）：

```
App ──REST──▶ gateway ──gRPC──▶ match-service
   ├─ 记录 A→B 的 like
   ├─ 查 B 是否已经 like 过 A（双向判定）
   └─ 若互相喜欢 ⇒ 配对成功 ⇒ 调 im-service gRPC 建会话 / 发系统消息
```

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 框架 | Java 21 / Spring Boot 3.3.5 |
| ORM | MyBatis-Plus（单表 CRUD，禁多表 JOIN） |
| 关系库 | PostgreSQL 16（唯一关系库） |
| 缓存 | Redis 7 |
| 对象存储 | MinIO（S3 兼容） |
| 配置 / 注册 | Nacos 2.4 |
| RPC | gRPC 1.68 + Protobuf（服务间禁 HTTP） |
| MQ | RocketMQ 5.3 |
| IM / RTC | OpenIM + LiveKit |
| AI | Python 3.13 + LangChain / LangGraph |

## 核心设计原则（8 条红线）

1. **持久层禁多表 JOIN**，跨表在 service 层内存拼装——为分库分表留退路
2. **服务间禁 HTTP，只用 gRPC**——强契约 + 高性能 + 跨语言
3. **跨服务禁直连别人的库 / Redis / 对象桶**——数据所有权，要数据只能调其 gRPC
4. **时间一律 UTC**（DB `TIMESTAMPTZ`）——海外多时区业务的命门
5. **业务不直连 OpenIM / LiveKit，统一经 im-service**——收口第三方依赖
6. **禁跨服务分布式事务**——用消息 + 重试 + 对账做最终一致性
7. **.proto 走 Nexus 包、版本锁死**——接口契约唯一 source of truth
8. **不擅自引入中间件**——控制架构熵增

**分层调用方向严格单向**：`controller / grpc → service → manager → mapper`。事务边界在 service 层，一个 Mapper 只服务一张表。

## 核心域亮点

- **match（配对）**：Swipe 单向动作流；双向匹配 = 读时反查 + `match` 表 `(low,high)` 规范化主键 + 唯一索引兜底幂等；配额纯 Redis 原子先加后判。
- **post（Feed）**：点赞走"写合并"（Redis INCR + 60s 批量刷盘 Lua 原子取走），爆款帖不锁库；Feed 三路池（热门 pull / 好友 push / 冷启动）任一路挂降级。
- **im（消息中枢）**：provider 抽象隔离引擎，换 IM 引擎只改一处；扣费异步化（只读预检 + 异步扣减）不卡死发消息链路；AI 拟真回复三件套（阅读延迟 / 正在输入 / 分段发送）。
- **payment（支付）**：订单状态机当幂等锚点，任意顺序重复回调只发一次奖；金额 `NUMERIC(16,4)` + BigDecimal；账户扣减乐观锁 + append-only 流水账本。
- **gateway（BFF）**：虚拟线程（Loom）不选 WebFlux；RS256 非对称 JWT，鉴权全内聚网关；限流三档（Nginx / Resilience4j / Redis 滑动窗口）。
- **ai-chat（数字人）**：单 Agent + 动态 Prompt 服务全量用户；意图两级识别（关键词快路 + LLM 门控）；多模型路由熔断降级。

## 快速开始

```bash
# 本地基础设施（PG / Redis / Nacos / MinIO / RocketMQ）
# 见 docs/0本地Docke开发指导文档local-infra-setup.md（docs 不入库，本地保留）

# 各服务为独立 Spring Boot 应用，本地以对应 profile 启动：
cd user-service && mvn spring-boot:run
cd match-service && mvn spring-boot:run
# ...（其余服务同理）

# ai-chat（Python）
cd ../ai-chat && uv sync && python -m server
```

## 目录结构

```
dating-server/
├── mobile-gateway/    # 对外 BFF 网关
├── user-service/      # 用户 / 召回
├── match-service/     # 滑卡 / 配对
├── post-service/      # 动态 / Feed
├── im-service/        # IM / 音视频中枢
├── payment-service/   # 金币 / 支付
└── docs/              # 设计文档（内部，不入库）
```

## 补充说明

- `ai-chat`（Python 数字人服务）与 Java 服务同仓库 `../ai-chat` 独立管理，通过 gRPC 接入本体系。
- 各服务通过 Nacos 注册发现，跨服务调用一律 `discovery:///service-name`，禁写死 host:port。
