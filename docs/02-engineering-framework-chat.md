# Engineering Framework - Chat


## 场景一：Maven 多模块骨架搭建
目标：从空目录到 Maven 多模块结构就绪，mvn compile 能通过。

### 第一步：确认 CLAUDE.md 已就绪

查看当前 CLAUDE.md，确认项目结构和模块划分部分是否清晰，如果有遗漏或不清晰的地方告诉我。

### 第二步：生成父 pom 和子模块结构

按照 CLAUDE.md 中定义的项目结构，创建 Zify 的 Maven 多模块工程骨架。
父 pom 声明所有子模块，dependencyManagement 统一管理版本。
只创建 pom 文件和目录结构，不需要任何 Java 代码。

### 第三步：验收

mvn compile


## 场景二：zify-common 公共基础设施
目标：五个子任务串行完成，每个任务做完编译验证，最后全部就绪。

### 子任务一：Result 和 PageResult

在 zify-common 中创建统一响应类 Result<T> 和分页响应类 PageResult<T>。
Result 包含 code、message、data，提供 ok() 和 fail() 静态方法。
PageResult 继承 Result，额外包含 total、page、size。
放在 com.zify.common.web 包下。

### 子任务二：ErrorCode 和 BizException

在 zify-common 中创建错误码枚举 ErrorCode 和业务异常类 BizException。
ErrorCode 包含 PARAM_ERROR、UNAUTHORIZED、NOT_FOUND、INTERNAL_ERROR 等常用错误码。
BizException 持有 ErrorCode，支持自定义 message 覆盖。
放在 com.zify.common.exception 包下。

### 子任务三：全局异常处理器

在 zify-common 中创建全局异常处理器 GlobalExceptionHandler（@RestControllerAdvice）。
捕获 BizException 返回对应 ErrorCode，
捕获 MethodArgumentNotValidException 返回 PARAM_ERROR 和具体校验信息，
兜底捕获 Exception 返回 INTERNAL_ERROR。
所有响应必须使用 Result.fail() + ErrorCode 枚举，禁止硬编码错误码。

### 子任务四：MyBatis-Plus 配置

在 zify-common 中创建 MyBatis-Plus 配置类。包含：分页插件、自动填充（createTime、updateTime）、逻辑删除配置。

### 任务五：Redis  配置

在 zify-common 中创建 Redis 配置类。包含：RedisTemplate 序列化配置（key 用 String，value 用 JSON）、基础的 RedisUtil 工具类（get/set/delete/expire）。


## 场景三：业务模块空壳与启动验证
目标：搭完所有业务模块空壳，Spring Boot 能启动，健康检查返回 200。

### 第一步：业务模块  package  结构

为 zify-provider、zify-agent、zify-chat、zify-mcp、zify-knowledge、zify-workflow
创建标准的 package 结构。
按 CLAUDE.md 代码组织规范，每个模块包含 web/api/domain/infra 四个包，
每个包里创建一个空的占位类（在类上加注释说明这个包的职责）。
不需要任何业务代码。

### 第二步：Spring Boot  启动类和配置文件

在 zify-app 中创建 Spring Boot 启动类 ZifyApplication。
@SpringBootApplication + @MapperScan("com.zify.**.mapper")。
创建 application.yml，配置数据库、Redis、MyBatis-Plus、端口 8080。
数据库连接信息用 localhost 本地开发配置。

### 第三步：健康检查接口  +  验收

在 zify-app 中创建 HealthController，
路径 GET /api/v1/health，返回 Result.ok("zify is running")。

mvn spring-boot:run -pl zify-app
curl http://localhost:8080/api/v1/health

## 场景四：前端工程搭建与前后端联通
目标：React 工程搭好，axios 封装完成，调健康检查接口能看到绿色“后端已连接”。

### 第一步：React 工程骨架

初始化 Zify 前端项目 zify-web。
技术栈：Vue 3 + TypeScript + Vite + Element Plus。
Vite 代理：/api 请求转发到 localhost:8080。
目录结构按 CLAUDE.md 中定义的前端结构来。

### 第二步：axios 统一请求层

在 zify-web/src/utils/ 下创建 request.ts，封装 axios 实例。
baseURL 设为 /api。
响应拦截器：code === 200 直接返回 data 字段（自动解包），
非 200 用 ElMessage.error 提示 message 并 reject。
导出 get、post、put、del 四个方法。

### 第三步：路由、页面空壳、前后端联通

配置 Vue Router，创建三个路由和对应空壳页面：
模型管理（/providers）、Agent 管理（/agents）、对话（/chat）。
App.vue：左侧 Element Plus 菜单栏，右侧 router-view。


## 场景五：UI 设计与基础组件封装

目标：从灰蒙蒙的 Element Plus 默认样式，变成有品牌感的界面；前端基础组件一条指令生成。

### 第一步：设计系统生成

Zify 是一个 AI Agent 开发平台，面向技术团队内部使用，用户是开发者。
管理后台为主——大量表格、表单、配置页，加一个对话页。
风格：浅底 + 科技感点缀。主色蓝紫系，辅色青色，
侧边栏深色底，按钮和关键元素用亮色。
参考 Linear、Supabase 的视觉风格——干净但不无聊。
帮我设计一套 CSS 变量设计系统：主色/背景色阶/文字色阶/圆角/阴影/过渡动效。

### 第二步：侧边栏改造演示
深色侧边栏  +  浅色内容区的层次感
选中态的左侧竖线设计
Zify 品牌名的渐变文字效果

### 第三步：前端基础组件一条指令生成

在 zify-web 中创建以下前端公共组件（src/components/）：
1. HifyTable.vue：通用列表表格，props 接收 columns 配置和 api 方法，
   内部管理 loading 和分页，暴露 refresh() 方法。
2. HifyFormDialog.vue：通用表单弹窗，v-model 控制显示，
   open(data?) 方法区分新增/编辑模式，提交触发 submit 事件。
3. useConfirm.ts：删除确认 composable，接收确认文案和 API 方法，
   一行代码完成确认→调接口→成功提示。
4. useRequest.ts：请求状态管理，返回 { data, loading, error, execute }。
5. notify.ts：统一通知封装，notifySuccess/notifyError/notifyWarning。
   所有组件 Vue 3 Composition API + TypeScript，泛型支持不同数据类型。

### 第四步：UI 打磨来回调的演示

第一轮：
ProviderList 的表格行高太高，改成 52px。
操作列的编辑和删除按钮间距太小，加 8px margin-left。
第二轮：
状态列的禁用标签用灰色，不要用红色。
禁用是正常状态，不是错误，不应该用 danger 色。
第三轮：
整体看一下 ProviderList：
1. 分页器居右对齐，上方加细分割线
2. 新增按钮加 Plus 图标
3. 操作列改成 text 类型按钮：编辑蓝色、删除红色














