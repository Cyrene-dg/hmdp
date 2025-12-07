# 黑马点评项目导入指南

## 项目简介
这是一个基于 Spring Boot 2.3.12 的点评系统项目，使用了 Redis、MySQL、MyBatis-Plus 等技术栈。

## 环境要求
- JDK 1.8 或更高版本
- Maven 3.6+
- MySQL 8.0+
- Redis 6.2+（支持 GEO 搜索功能）
- IDE：IntelliJ IDEA 或 Eclipse

## 导入步骤

### 方式一：IntelliJ IDEA 导入

1. **打开 IntelliJ IDEA**
   - 选择 `File` → `Open`（或 `Open or Import`）

2. **选择项目目录**
   - 浏览到项目根目录：`E:\hwimadianping2025\hm-dianping`
   - 选择包含 `pom.xml` 的文件夹
   - 点击 `OK`

3. **等待 Maven 导入**
   - IDEA 会自动检测到 Maven 项目
   - 右下角会弹出提示，选择 `Import Maven Project`
   - 等待依赖下载完成（首次导入可能需要几分钟）

4. **配置 JDK**
   - `File` → `Project Structure` → `Project`
   - 设置 `Project SDK` 为 JDK 1.8
   - 设置 `Project language level` 为 8

5. **配置 Maven**
   - `File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven`
   - 确认 Maven 配置正确（使用 IDEA 内置或本地 Maven）

6. **启用 Lombok 插件**
   - `File` → `Settings` → `Plugins`
   - 搜索并安装 `Lombok` 插件
   - 重启 IDEA

7. **启用注解处理**
   - `File` → `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`
   - 勾选 `Enable annotation processing`

### 方式二：Eclipse 导入

1. **打开 Eclipse**
   - 选择 `File` → `Import`

2. **选择导入类型**
   - 展开 `Maven` 文件夹
   - 选择 `Existing Maven Projects`
   - 点击 `Next`

3. **选择项目目录**
   - 点击 `Browse`，选择项目根目录
   - 确认 `pom.xml` 被选中
   - 点击 `Finish`

4. **等待 Maven 构建**
   - Eclipse 会自动下载依赖
   - 查看 `Console` 窗口确认构建成功

5. **安装 Lombok**
   - 下载 Lombok jar 包
   - 双击运行，选择 Eclipse 安装目录
   - 重启 Eclipse

## 数据库配置

1. **创建数据库**
   ```sql
   CREATE DATABASE hmdp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **导入数据库脚本**
   - 执行 `src/main/resources/db/hmdp.sql` 文件
   - 或在 MySQL 客户端中运行该脚本

3. **修改数据库配置**
   - 编辑 `src/main/resources/application.yaml`
   - 修改数据库连接信息：
     ```yaml
     spring:
       datasource:
         url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
         username: root  # 修改为你的数据库用户名
         password: 1234   # 修改为你的数据库密码
     ```

## Redis 配置

1. **启动 Redis 服务**
   - 确保 Redis 6.2+ 已安装并运行
   - 默认端口：6379

2. **修改 Redis 配置（如需要）**
   - 编辑 `src/main/resources/application.yaml`
   - 修改 Redis 连接信息：
     ```yaml
     spring:
       redis:
         host: 127.0.0.1  # Redis 服务器地址
         port: 6379       # Redis 端口
         password:        # 如果有密码，取消注释并填写
         database: 1      # 使用的数据库编号
     ```

## 运行项目

### 方式一：IDE 运行
1. 找到主类：`com.hmdp.HmDianPingApplication`
2. 右键 → `Run 'HmDianPingApplication'`

### 方式二：Maven 命令运行
```bash
mvn spring-boot:run
```

### 方式三：打包运行
```bash
# 打包
mvn clean package

# 运行
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

## 验证项目

项目启动后，访问：
- 服务端口：`http://localhost:8081`
- 查看控制台日志确认启动成功

## 常见问题

### 1. Maven 依赖下载失败
- 检查网络连接
- 配置 Maven 镜像源（推荐使用阿里云镜像）
- 在 `settings.xml` 中添加：
  ```xml
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
  ```

### 2. Lombok 注解不生效
- 确认已安装 Lombok 插件
- 确认已启用注解处理
- 重新构建项目：`Build` → `Rebuild Project`

### 3. 数据库连接失败
- 确认 MySQL 服务已启动
- 检查数据库用户名和密码是否正确
- 确认数据库 `hmdp` 已创建

### 4. Redis 连接失败
- 确认 Redis 服务已启动
- 检查 Redis 配置是否正确
- 测试 Redis 连接：`redis-cli ping`

### 5. 端口被占用
- 修改 `application.yaml` 中的端口号
- 或关闭占用 8081 端口的其他程序

## 项目结构说明

```
hm-dianping/
├── src/
│   ├── main/
│   │   ├── java/com/hmdp/
│   │   │   ├── config/          # 配置类
│   │   │   ├── controller/      # 控制器
│   │   │   ├── dto/             # 数据传输对象
│   │   │   ├── entity/          # 实体类
│   │   │   ├── mapper/          # MyBatis Mapper 接口
│   │   │   ├── service/         # 业务逻辑层
│   │   │   └── utils/           # 工具类
│   │   └── resources/
│   │       ├── application.yaml # 配置文件
│   │       ├── db/              # 数据库脚本
│   │       └── mapper/          # MyBatis XML 映射文件
│   └── test/                    # 测试代码
└── pom.xml                      # Maven 配置文件
```

## 技术栈

- **框架**：Spring Boot 2.3.12
- **数据库**：MySQL 8.0 + MyBatis-Plus 3.4.3
- **缓存**：Redis 6.2+ + Spring Data Redis 2.6.2
- **工具**：Lombok、Hutool
- **分布式锁**：Redisson 3.16.8

## 注意事项

1. 确保 JDK 版本为 1.8 或更高
2. Redis 版本需要 6.2+ 以支持 GEO 搜索功能
3. 首次运行前需要先创建数据库并导入 SQL 脚本
4. 建议使用 IntelliJ IDEA 进行开发，对 Spring Boot 支持更好


