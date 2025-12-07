# 黑马点评项目架构文档

## 📋 目录
- [整体架构](#整体架构)
- [技术栈](#技术栈)
- [分层架构](#分层架构)
- [核心模块](#核心模块)
- [设计模式](#设计模式)
- [缓存策略](#缓存策略)
- [安全机制](#安全机制)
- [数据流](#数据流)

---

## 🏗️ 整体架构

### 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端层                                │
│                    (Web/移动端)                               │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP/HTTPS
┌──────────────────────▼──────────────────────────────────────┐
│                      Controller 层                           │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│  │  User    │  Shop    │  Blog    │ Voucher │  Follow  │  │
│  │Controller│Controller│Controller│Controller│Controller│  │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘  │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                      Service 层                              │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐  │
│  │  User    │  Shop    │  Blog    │ Voucher │  Follow  │  │
│  │ Service  │ Service  │ Service  │ Service │ Service  │  │
│  └──────────┴──────────┴──────────┴──────────┴──────────┘  │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
┌───────▼──────┐ ┌─────▼─────┐ ┌─────▼─────┐
│   Mapper 层  │ │ Redis缓存  │ │  工具类    │
│  (MyBatis)   │ │  (Lettuce) │ │  (Utils)  │
└───────┬──────┘ └────────────┘ └───────────┘
        │
┌───────▼──────┐
│   MySQL 数据库 │
└──────────────┘
```

### 架构特点

1. **经典三层架构**：Controller → Service → Mapper
2. **缓存层**：Redis 作为缓存中间层，提升性能
3. **工具层**：封装通用功能，提高代码复用性
4. **配置层**：统一管理配置，支持灵活扩展

---

## 🛠️ 技术栈

### 后端框架
- **Spring Boot 2.3.12**：核心框架
- **Spring MVC**：Web 层框架
- **Spring AOP**：面向切面编程（已启用代理暴露）

### 数据持久化
- **MyBatis-Plus 3.4.3**：ORM 框架
- **MySQL 8.0+**：关系型数据库
- **分页插件**：MyBatis-Plus 分页拦截器

### 缓存与分布式
- **Redis 6.2+**：缓存数据库（支持 GEO 搜索）
- **Spring Data Redis 2.6.2**：Redis 操作封装
- **Lettuce 6.1.6**：Redis 客户端（异步非阻塞）
- **Redisson 3.16.8**：分布式锁、分布式对象

### 工具库
- **Lombok 1.18.32**：简化 Java 代码
- **Hutool 5.7.17**：Java 工具类库
- **Jackson**：JSON 序列化/反序列化

---

## 📦 分层架构

### 1. Controller 层（控制层）

**职责**：处理 HTTP 请求，参数校验，调用 Service，返回响应

**主要控制器**：
- `UserController`：用户相关（登录、注册、信息查询）
- `ShopController`：店铺相关（查询、详情、附近店铺）
- `BlogController`：博客相关（发布、查询、点赞）
- `VoucherController`：优惠券相关（查询、领取）
- `VoucherOrderController`：秒杀订单相关
- `FollowController`：关注相关（关注、取消关注）
- `ShopTypeController`：店铺类型相关
- `BlogCommentsController`：博客评论相关
- `UploadController`：文件上传相关

**特点**：
- 统一返回 `Result` 对象
- 使用 `@RestController` 注解
- 通过拦截器进行权限控制

### 2. Service 层（业务层）

**职责**：业务逻辑处理，事务管理，调用 Mapper 和缓存

**设计模式**：
- **接口 + 实现类**：`IService` 接口 + `ServiceImpl` 实现
- **依赖注入**：使用 `@Autowired` 或 `@Resource`

**主要服务**：
- `IUserService` / `UserServiceImpl`
- `IShopService` / `ShopServiceImpl`
- `IBlogService` / `BlogServiceImpl`
- `IVoucherService` / `VoucherServiceImpl`
- `IVoucherOrderService` / `VoucherOrderServiceImpl`
- `IFollowService` / `FollowServiceImpl`
- `IShopTypeService` / `ShopTypeServiceImpl`
- `IBlogCommentsService` / `BlogCommentsServiceImpl`
- `IUserInfoService` / `UserInfoServiceImpl`
- `ISeckillVoucherService` / `SeckillVoucherServiceImpl`

### 3. Mapper 层（数据访问层）

**职责**：数据库操作，SQL 映射

**技术**：
- MyBatis-Plus：提供基础 CRUD
- 自定义 Mapper XML：复杂查询

**主要 Mapper**：
- `UserMapper`、`ShopMapper`、`BlogMapper` 等
- 对应实体类的数据访问接口

### 4. Entity 层（实体层）

**职责**：数据库表映射，业务对象定义

**主要实体**：
- `User`：用户表
- `UserInfo`：用户信息表
- `Shop`：店铺表
- `ShopType`：店铺类型表
- `Blog`：博客表
- `BlogComments`：博客评论表
- `Voucher`：优惠券表
- `SeckillVoucher`：秒杀优惠券表
- `VoucherOrder`：优惠券订单表
- `Follow`：关注关系表

### 5. DTO 层（数据传输对象）

**职责**：不同层之间的数据传输

**主要 DTO**：
- `Result`：统一响应结果封装
- `UserDTO`：用户信息传输对象
- `LoginFormDTO`：登录表单对象
- `ScrollResult`：滚动分页结果

### 6. Config 层（配置层）

**职责**：系统配置，Bean 定义，拦截器配置

**主要配置类**：
- `RedisConfig`：Redis 连接配置（Lettuce 连接工厂）
- `MybatisConfig`：MyBatis-Plus 分页配置
- `MvcConfig`：拦截器配置（登录拦截、Token 刷新）
- `BloomFilterInit`：布隆过滤器初始化
- `WebExceptionAdvice`：全局异常处理

### 7. Utils 层（工具层）

**职责**：通用工具类，提高代码复用

**主要工具类**：
- `CacheClient`：缓存客户端（缓存穿透、击穿、雪崩解决方案）
- `UserHolder`：用户信息持有者（ThreadLocal）
- `RedisIdWoker`：Redis 分布式 ID 生成器
- `BloomFilterUtil`：布隆过滤器工具
- `SimplyRedisLock`：简单 Redis 锁实现
- `ILock`：分布式锁接口
- `RedisConstants`：Redis 常量定义
- `SystemConstants`：系统常量
- `LoginInterceptor`：登录拦截器
- `RefreshTokenInterceptor`：Token 刷新拦截器
- `PasswordEncoder`：密码加密工具
- `RegexUtils`：正则表达式工具

---

## 🎯 核心模块

### 1. 用户模块（User）

**功能**：
- 用户登录（手机号 + 验证码）
- 用户信息查询
- Token 刷新机制

**技术要点**：
- 使用 Redis 存储 Token
- ThreadLocal 存储用户信息
- 双重拦截器：Token 刷新 + 登录验证

### 2. 店铺模块（Shop）

**功能**：
- 店铺查询（支持缓存）
- 店铺详情（缓存穿透、击穿、雪崩解决方案）
- 附近店铺查询（Redis GEO）

**技术要点**：
- 缓存穿透：布隆过滤器 + 空值缓存
- 缓存击穿：互斥锁
- 缓存雪崩：逻辑过期时间
- GEO 搜索：基于地理位置查询

### 3. 博客模块（Blog）

**功能**：
- 博客发布
- 博客查询（分页、滚动分页）
- 博客点赞（使用 Set 去重）
- 博客评论

**技术要点**：
- 滚动分页（基于时间戳）
- Redis Set 实现点赞去重
- Feed 流推送（关注用户发布博客）

### 4. 优惠券模块（Voucher）

**功能**：
- 普通优惠券查询
- 秒杀优惠券查询
- 优惠券领取

**技术要点**：
- 库存扣减（Redis）
- 异步下单（消息队列思想）

### 5. 秒杀模块（Seckill）

**功能**：
- 秒杀下单
- 库存扣减
- 一人一单限制

**技术要点**：
- Lua 脚本保证原子性
- 分布式锁（Redisson）
- 异步下单处理

### 6. 关注模块（Follow）

**功能**：
- 关注/取消关注
- 共同关注（Set 交集）
- Feed 流推送

**技术要点**：
- Redis Set 存储关注关系
- Set 交集计算共同关注
- 推拉结合模式

---

## 🎨 设计模式

### 1. 单例模式
- `UserHolder`：使用 ThreadLocal 实现线程单例

### 2. 策略模式
- `CacheClient`：不同的缓存策略（穿透、击穿、雪崩）

### 3. 模板方法模式
- `CacheClient.queryWithPassThrow()`：缓存查询模板

### 4. 代理模式
- Spring AOP：`@EnableAspectJAutoProxy(exposeProxy = true)`

### 5. 工厂模式
- Spring Bean 工厂：`@Bean` 注解

---

## 💾 缓存策略

### 1. 缓存穿透解决方案

**问题**：查询不存在的数据，绕过缓存直接查询数据库

**解决方案**：
- **布隆过滤器**：在查询前判断 ID 是否存在
- **空值缓存**：将空结果也缓存到 Redis，设置较短过期时间

**实现**：
```java
CacheClient.queryWithBloomFilter()  // 带布隆过滤器
CacheClient.queryWithPassThrow()    // 不带布隆过滤器（空值缓存）
```

### 2. 缓存击穿解决方案

**问题**：热点数据过期，大量请求同时访问数据库

**解决方案**：
- **互斥锁**：使用 Redis 的 `SETNX` 实现分布式锁
- **逻辑过期**：不设置 TTL，在数据中存储过期时间

**实现**：
```java
CacheClient.queryWithLogicExpire()  // 逻辑过期 + 互斥锁
```

### 3. 缓存雪崩解决方案

**问题**：大量缓存同时过期，导致数据库压力激增

**解决方案**：
- **随机过期时间**：避免同时过期
- **逻辑过期**：不依赖 Redis TTL
- **多级缓存**：本地缓存 + Redis 缓存

### 4. 缓存更新策略

**策略**：
- **先更新数据库，再删除缓存**（Cache Aside）
- **逻辑过期**：异步更新，不阻塞查询

---

## 🔒 安全机制

### 1. 认证机制

**Token 认证**：
- 登录成功后生成 Token
- Token 存储在 Redis 中
- 使用 `RefreshTokenInterceptor` 自动刷新 Token

**拦截器链**：
1. `RefreshTokenInterceptor`（order=0）：所有请求都经过，刷新 Token
2. `LoginInterceptor`（order=1）：需要登录的接口进行验证

### 2. 权限控制

**路径排除**：
- `/shop/**`：店铺查询（公开）
- `/voucher/**`：优惠券查询（公开）
- `/user/code`、`/user/login`：登录相关（公开）
- 其他接口需要登录

### 3. 分布式锁

**应用场景**：
- 秒杀下单（防止超卖）
- 缓存重建（防止缓存击穿）

**实现方式**：
- **Redisson**：成熟的分布式锁框架
- **简单实现**：`SimplyRedisLock`（基于 SETNX）

### 4. 密码加密

**工具类**：`PasswordEncoder`
- 使用加密算法保护用户密码

---

## 🔄 数据流

### 1. 用户登录流程

```
客户端 → UserController.login()
       → UserService.login()
       → 验证码校验（Redis）
       → 查询用户（数据库）
       → 生成 Token（UUID）
       → 存储 Token（Redis，Key: login:token:xxx）
       → 返回 Token 给客户端
```

### 2. 店铺查询流程（带缓存）

```
客户端 → ShopController.queryById()
       → ShopService.queryById()
       → CacheClient.queryWithBloomFilter()
       → 1. 查询 Redis 缓存
       → 2. 缓存未命中，布隆过滤器判断
       → 3. 查询数据库
       → 4. 写入缓存
       → 返回结果
```

### 3. 秒杀下单流程

```
客户端 → VoucherOrderController.seckillVoucher()
       → VoucherOrderService.seckillVoucher()
       → 1. 判断库存（Redis）
       → 2. 判断一人一单（Redis Set）
       → 3. 获取分布式锁（Redisson）
       → 4. 扣减库存（Lua 脚本）
       → 5. 创建订单（数据库）
       → 6. 释放锁
       → 返回结果
```

### 4. Token 刷新流程

```
请求 → RefreshTokenInterceptor.preHandle()
     → 1. 获取 Token（请求头）
     → 2. 查询 Redis 获取用户信息
     → 3. 刷新 Token 过期时间
     → 4. 存储用户信息到 ThreadLocal
     → 继续执行后续拦截器和控制器
```

---

## 📊 数据库设计

### 核心表结构

1. **tb_user**：用户表
2. **tb_user_info**：用户信息表
3. **tb_shop**：店铺表
4. **tb_shop_type**：店铺类型表
5. **tb_blog**：博客表
6. **tb_blog_comments**：博客评论表
7. **tb_voucher**：优惠券表
8. **tb_seckill_voucher**：秒杀优惠券表
9. **tb_voucher_order**：优惠券订单表
10. **tb_follow**：关注关系表

### Redis 数据结构

- **String**：Token、缓存数据、分布式锁
- **Hash**：用户信息、店铺信息
- **Set**：点赞、关注关系、一人一单限制
- **Sorted Set**：排行榜、Feed 流
- **GEO**：附近店铺查询
- **BitMap**：签到功能

---

## 🚀 性能优化

### 1. 缓存优化
- 多级缓存策略
- 缓存预热
- 缓存更新策略

### 2. 数据库优化
- MyBatis-Plus 分页
- 索引优化
- 连接池配置

### 3. Redis 优化
- 连接池配置（Lettuce）
- 序列化优化（String 序列化）
- 批量操作

### 4. 并发优化
- 分布式锁
- 异步处理
- 线程池管理

---

## 📝 总结

### 架构优势

1. **分层清晰**：Controller → Service → Mapper，职责明确
2. **缓存完善**：解决了穿透、击穿、雪崩三大问题
3. **安全可靠**：Token 认证 + 分布式锁
4. **性能优化**：Redis 缓存 + GEO 搜索 + 异步处理
5. **扩展性强**：接口 + 实现类，易于扩展

### 技术亮点

1. **布隆过滤器**：防止缓存穿透
2. **逻辑过期**：解决缓存击穿和雪崩
3. **分布式锁**：保证并发安全
4. **GEO 搜索**：基于地理位置查询
5. **滚动分页**：Feed 流实现
6. **Lua 脚本**：保证原子性操作

---

## 📚 相关文档

- [项目导入指南](README.md)
- [数据库脚本](src/main/resources/db/hmdp.sql)
- [配置文件](src/main/resources/application.yaml)

