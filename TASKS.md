# 社区团购自提项目任务与进度

状态说明：`未开始`、`进行中`、`已完成`、`等待检查`、`受阻`。每个任务完成后记录实际验证命令及结果；验证失败时先修复，再进入下一项。

| 编号 | 任务点 | 验收标准 | 状态 | 验证记录 |
| --- | --- | --- | --- | --- |
| 1 | Maven 父工程与公共模块 | Java 8 下公共模块编译通过；统一响应和令牌工具可用 | 已完成 | `mvn.cmd -f common/pom.xml -q -DskipTests compile` 通过；修复了缺少 `spring-context` 的依赖 |
| 2 | 用户服务 | 注册、登录、角色令牌与管理员初始化可用 | 已完成 | `mvn.cmd -f member-service/pom.xml -q test` 通过；3 项单元验证覆盖注册角色、错误密码和管理员初始化 |
| 3 | 商品库存服务 | 商品、自提点查询与管理；库存预占、释放幂等 | 已完成 | `mvn.cmd -f catalog-service/pom.xml -q test` 通过；4 项单元验证覆盖预占与释放分支 |
| 4 | 订单服务 | 下单、模拟支付、取消、超时处理与提货核销闭环 | 已完成 | `mvn.cmd -f order-service/pom.xml -q test` 通过；7 项单元验证覆盖主链路和补偿 |
| 5 | 网关与服务治理 | Nacos 注册和配置、网关路由、Sentinel 下单限流 | 已完成 | `mvn.cmd -f gateway-service/pom.xml -q test` 通过（4 项鉴权验证）；订单服务接入 Sentinel 后 7 项验证通过；Nacos 实际注册待任务 7 联调 |
| 6 | Vue 用户端与管理端 | 两类角色完成对应页面操作与权限隔离 | 已完成 | `npm.cmd run build` 通过；桌面/手机宽度浏览器截图无布局溢出；未登录访问 `/admin` 跳转登录页；`npm.cmd audit --omit=dev` 为 0 个漏洞 |
| 7 | 本机启动文档与整体联调 | 不使用 Docker，给出原生启动步骤和验收场景 | 等待检查 | `README.md`、`infra/init.sql` 和 Nacos 配置样例已完成；`mvn.cmd -q test` 全模块通过。MySQL/Nacos/Sentinel 本机未安装，端到端联调未执行。依用户要求暂停下载与后续工作；`.local` 中有未下载完成的文件，不可作为中间件使用。 |

## 环境记录

- 已检测：JDK 8、Maven 3.9.9、Node 24、npm.cmd 11.16.0。
- 未检测到：Docker、MySQL、Redis；本项目不使用 Docker 或 Redis。
- PowerShell 执行策略会拦截 `npm.ps1`，应使用 `npm.cmd`。
