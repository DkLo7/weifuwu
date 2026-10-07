# 邻里集：社区团购自提

基于 Spring Cloud Alibaba 的本地学习项目。用户可注册、下单、模拟支付并获取提货码；管理员可管理商品库存、查询订单和核销提货码。所有进程都在 Windows 本机原生启动，不使用 Docker。

## 模块与端口

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| gateway-service | 8080 | 统一 API 入口、路由和登录角色校验 |
| member-service | 8081 | 注册、登录、管理员账号 |
| catalog-service | 8082 | 商品、自提点、库存预占与释放 |
| order-service | 8083 | 订单状态、模拟支付、取消、核销与超时补偿 |
| frontend | 5173 | Vue 用户端 `/shop` 与管理端 `/admin` |
| Nacos 2.2.0 | 8848 | 服务注册发现及配置 |
| Sentinel 控制台 1.8.6 | 8858 | 查看下单资源及限流规则 |
| MySQL 8.0 | 3306 | 三个服务各自使用一个数据库 |

版本固定为 JDK 8、Spring Boot 2.6.13、Spring Cloud 2021.0.5 和 Spring Cloud Alibaba 2021.0.6.0。组件配套见[官方版本说明](https://github.com/spring-cloud-alibaba-group/spring-cloud-alibaba-group.github.io/blob/main/src/content/docs/2021/en/overview/version-explain.md)。

## 本机准备

1. 安装 [MySQL Community Server 8.0](https://dev.mysql.com/downloads/mysql/)；启动 Windows 服务或通过 `mysqld.exe --console` 启动。使用 MySQL Workbench 执行 `infra/init.sql`，创建三个数据库。应用首次启动会自动执行各自的 `schema.sql`。
2. 下载并解压 [Nacos 2.2.0](https://github.com/alibaba/nacos/releases/tag/2.2.0)，在其 `bin` 目录运行 `startup.cmd -m standalone`。确认 `http://127.0.0.1:8848/nacos` 可访问。可在 Nacos 配置管理中创建 Data ID `order-service.yaml`、Group `DEFAULT_GROUP`，内容使用 `infra/nacos/order-service.yaml`；不创建时订单超时默认 15 分钟。
3. 下载 [Sentinel Dashboard 1.8.6](https://github.com/alibaba/Sentinel/releases/tag/1.8.6)，在 JAR 所在目录运行 `java -Dserver.port=8858 -Dcsp.sentinel.dashboard.server=127.0.0.1:8858 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard-1.8.6.jar`。订单服务内置 `createOrder` 每秒 5 次的限流规则；控制台在访问下单接口后可看到该资源。
4. 确保 `java -version` 显示 Java 8，`mvn.cmd -version` 和 `node --version` 可用。PowerShell 会拦截本机的 `npm.ps1`，本项目使用 `npm.cmd`。

在启动服务的各个终端设置相同的密钥和数据库凭据，例如：

```powershell
$env:DB_USER = 'root'
$env:DB_PASSWORD = '你的MySQL密码'
$env:APP_JWT_SECRET = '换成一段至少32位的本机随机字符串'
$env:APP_INTERNAL_KEY = '另一段至少32位的本机随机字符串'
$env:APP_ADMIN_PASSWORD = '你的管理员密码'
```

仅用户服务读取 `APP_ADMIN_PASSWORD`。未设置时使用本地演示默认值，管理员账号为 `admin / Admin@123456`。服务只监听 `127.0.0.1`；若将服务部署到其他机器，先替换默认密钥并调整地址配置。

## 原生启动

先在项目根目录安装 Maven 模块：

```powershell
mvn.cmd -q -DskipTests install
```

保持 MySQL 与 Nacos 运行，分别打开四个 PowerShell 窗口并从项目根目录执行：

```powershell
mvn.cmd -f member-service/pom.xml spring-boot:run
mvn.cmd -f catalog-service/pom.xml spring-boot:run
mvn.cmd -f order-service/pom.xml spring-boot:run
mvn.cmd -f gateway-service/pom.xml spring-boot:run
```

前端在第五个窗口执行：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问 `http://127.0.0.1:5173/`。Vite 将 `/api` 代理到网关 8080，浏览器无需直连业务服务端口。

## 手工验收

1. 用管理员登录 `/admin`，创建商品并设置库存；在 `/admin/orders` 查看订单和核销入口。
2. 注册普通用户，在 `/shop` 选择商品、自提点和数量；在“我的订单”模拟支付并获取提货码。
3. 管理员输入提货码核销，订单变为“已完成”。重复核销仍只保留一条已完成订单。
4. 再创建未支付订单并取消，商品库存应恢复。库存不足、重复请求、越权请求和下单限流应返回明确错误。

后端验证：`mvn.cmd test`。前端构建：在 `frontend` 目录运行 `npm.cmd run build`。实施进度与每项验证结果记录在 [TASKS.md](TASKS.md)。

## 接口摘要

用户接口：`/api/auth/register`、`/api/auth/login`、`/api/products`、`/api/pickup-points`、`/api/orders`、`/api/orders/{orderNo}/pay`、`/api/orders/{orderNo}/cancel`。管理接口：`/api/admin/products`、`/api/admin/products/{id}/stock`、`/api/admin/orders`、`/api/admin/pickups`。服务间库存接口位于商品服务 `/internal/reservations/**`，仅接受共享内部密钥且不经网关路由。
