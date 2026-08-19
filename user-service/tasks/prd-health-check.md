# PRD: user-service 健康检查接口

## Introduction

为 `dating-server/user-service`（Spring Boot 3 微服务）新增 `GET /health` 探活接口。它服务于容器健康检查（K8s liveness/readiness probe）、负载均衡探活和运维排障：进程启动后，探测方通过一个轻量 HTTP 请求即可判断该实例是否可用。健康检查同时验证 MySQL 与 Redis 连接是否正常——依赖不可用时返回 `DOWN`，避免流量被路由到"进程活着但实际不可服务"的实例上。

## Goals

- 提供 `GET /health` 探活接口，进程启动后即可访问
- 同时作为 **存活 + 就绪** 探针：不仅证明进程活着，还证明核心依赖（MySQL、Redis）可用
- 复用现有 `Result<T>` 统一返回体，保持项目风格一致
- 依赖不可用时正确返回 `DOWN`，而非 500 或超时挂起
- 不引入额外依赖（如 Spring Boot Actuator），改动最小

## User Stories

### US-001: 新增 HealthController 提供 GET /health 接口
**Description:** As a 运维/平台工程师, I want 调用 `GET /health` 即可获得当前实例的健康状态，以便进行容器与负载均衡探活。

**Acceptance Criteria:**
- [ ] 新增 `com.dating.user.controller.HealthController`，映射 `GET /health`
- [ ] 返回体复用 `Result<T>`（`Result.ok(...)`），HTTP 200
- [ ] 响应包含 `status`、`service`（服务名）、`version`、`timestamp` 字段
- [ ] 正常时 `status = "UP"`
- [ ] 编译通过（`mvn -q compile` 无错误）

### US-002: 依赖健康检查（MySQL + Redis）
**Description:** As a 运维/平台工程师, I want 健康检查同时验证 MySQL 与 Redis 连接，以便识别"进程活着但依赖不可用"的实例。

**Acceptance Criteria:**
- [ ] 通过轻量 SQL（`SELECT 1`，JdbcTemplate）验证 MySQL 连接
- [ ] 通过 Redis `PING` 验证连接（StringRedisTemplate 或 RedisConnectionFactory）
- [ ] 任一依赖检查失败时，响应 `status = "DOWN"`
- [ ] 每个依赖检查设有超时上限（≤2s），任一依赖不可用时**快速失败**而非挂起
- [ ] 编译通过（`mvn -q compile` 无错误）

### US-003: 单元测试覆盖 HealthController
**Description:** As a 开发工程师, I want HealthController 有单元测试覆盖，以便验证各分支（正常/依赖失败）行为正确。

**Acceptance Criteria:**
- [ ] 存在 `HealthControllerTest`（或等价测试类）
- [ ] 用例覆盖：全部依赖正常 → `UP`
- [ ] 用例覆盖：MySQL 检查失败 → `DOWN`
- [ ] 用例覆盖：Redis 检查失败 → `DOWN`
- [ ] `mvn -q test` 相关测试通过

### US-004: End-to-end test of /health flow
**Description:** As a QA 工程师, I want 一个自动化集成测试真实启动 Spring 上下文并请求 `GET /health`，以便端到端验证整条探活链路。

**Acceptance Criteria:**
- [ ] 集成测试启动 Spring 上下文（`@SpringBootTest`），通过 `MockMvc` 请求 `GET /health`
- [ ] 依赖可用时断言 HTTP 200 且 body 中 `status == "UP"`
- [ ] 覆盖失败路径：模拟 Redis 不可用 → 断言 `status == "DOWN"`
- [ ] 测试可独立重复运行（不依赖真实外部 DB/Redis 时使用 mock，不污染共享环境）
- [ ] `mvn -q test` 集成测试通过

## Functional Requirements

- FR-1: 系统必须提供 `GET /health` 接口，任何健康状态下均返回 HTTP 200（不返回 500）
- FR-2: 系统必须返回统一 `Result<T>` 结构，其中 `data` 含 `status`、`service`、`version`、`timestamp` 字段
- FR-3: 系统必须通过 `SELECT 1`（JdbcTemplate）验证 MySQL 连接是否可用
- FR-4: 系统必须通过 Redis `PING` 验证连接是否可用
- FR-5: 系统必须在任一依赖检查失败时将 `status` 置为 `DOWN`
- FR-6: 系统必须为每个依赖检查设置 ≤2s 的超时，依赖不可用时快速失败，不得挂起请求
- FR-7: `/health` 不得要求认证（不依赖 `X-User-Id` 请求头）

## Non-Goals

- 不提供每个依赖的**逐项明细**健康报告（仅聚合后的 UP/DOWN）
- 不引入 Spring Boot Actuator 依赖
- 不检查 CPU / 内存 / JVM 指标
- 不检查 gRPC 服务端健康状态
- 不做健康状态的缓存与降级（每次请求实时探测）

## Design Considerations

- 复用现有 `Result<T>` 统一返回体（`com.dating.user.vo.Result`），与 `UserController` 风格一致
- 新增独立 `HealthController`（`com.dating.user.controller` 包），不修改现有 `UserController`
- 使用 Lombok `@RestController` / `@Slf4j` 风格，与现有 Controller 保持一致

## Technical Considerations

- MySQL 探测：`JdbcTemplate.queryForObject("SELECT 1", Integer.class)`（MyBatis-Plus 已引入 spring-boot-starter-jdbc）
- Redis 探测：`StringRedisTemplate` / `RedisConnectionFactory` 执行 `PING`
- 版本号来源：优先从 `application.yml` 的 `spring.application.name` 取服务名；`version` 用常量或构建信息（实现时确认，无构建信息则用常量，如 `1.0.0`）
- 超时控制：每个依赖探测使用带超时的连接获取方式（如 `RedisConnectionFactory.getConnection()` + 命令超时配置，或 `@Transactional(timeout=...)` 不适用于探测，采用 try/catch + 快速失败）
- 并发量极低（探活请求），无需缓存或限流

## Success Metrics

- 依赖正常时 `GET /health` 响应时间 < 200ms
- 停掉 Redis 后重新探测，`/health` 返回 `status=DOWN`（可人工/脚本验证）
- 现有业务接口（`/v1/users/**`）无任何回归
- 集成测试与单元测试全部通过（`mvn test` 绿色）

## Open Questions

- `version` 字段的值来源：使用常量还是从 Maven build-info 注入？（实现时采用常量 `1.0.0`，如需动态版本后续再改）
- 依赖探测失败时是否需要记录日志（`log.warn`）以便排查？（实现时建议记录，属合理默认）
