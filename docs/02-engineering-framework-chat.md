# Engineering Framework - Chat


## 场景一：Maven 多模块骨架搭建
目标：从空目录到 Maven 多模块结构就绪，mvn compile 能通过。

### 第一步：确认 CLAUDE.md 已就绪

查看当前 CLAUDE.md，确认项目结构和模块划分部分是否清晰，如果有遗漏或不清晰的地方告诉我。

### 第二步：生成父 pom 和子模块结构

按照 CLAUDE.md 中定义的项目结构，创建 Zify 后端的 Maven 多模块工程骨架。
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
技术栈：React 18 + TypeScript + Vite + React Router + React Flow + Zustand + Axios + Ant Design。
Vite 代理：/api 请求转发到 localhost:8080。
目录结构按 CLAUDE.md 中定义的前端结构来。

### 第二步：axios 统一请求层

使用 React 18 + TypeScript + Vite + Axios 封装前端请求工具。在 src/api/request.ts 中创建 Axios 实例，统一配置 baseURL: '/api' 和超时时间。
基于 types/api.ts 中的 ApiResponse<T> 类型封装 apiGet、apiPost、apiPut、apiDelete 方法，成功请求统一返回 response.data.data，让业务代码无需手动解包。
具体业务接口放在 api/{module}Api.ts 中，页面、组件和 Zustand Store 不直接调用 Axios，Store 只保存状态和 action，不发 HTTP 请求

### 第三步：路由、页面空壳、前后端联通

使用 React 18 + TypeScript + Vite + React Router + Ant Design 搭建前端基础页面骨架。
路由统一配置在 src/app/router.tsx，业务页面统一挂载到 MainLayout 下。
创建模型管理 /models、Agent 管理 /agents、对话默认页 / 对应的空壳页面，分别放在 pages/models/ModelPage.tsx、pages/agents/AgentListPage.tsx、pages/chat/ChatPage.tsx。
在 src/app/layouts/MainLayout.tsx 中使用 Ant Design 的 Layout 和 Menu 实现左侧导航，右侧通过 React Router 的 <Outlet /> 渲染当前页面内容。


## 场景五：UI 设计与基础组件封装

目标：从灰蒙蒙的 Element Plus 默认样式，变成有品牌感的界面；前端基础组件一条指令生成。

### 第一步：设计系统生成

Zify 是一个 AI Agent 开发平台，面向技术团队内部使用，用户是开发者。
管理后台为主——大量表格、表单、配置页，加一个对话页。
风格：浅底 + 科技感点缀。
调研 Linear、Supabase 的视觉风格.
帮我设计一套 CSS 变量设计系统：主色/背景色阶/文字色阶/圆角/阴影/过渡动效。

### 第二步：侧边栏改造演示
深色侧边栏  +  浅色内容区的层次感
选中态的左侧竖线设计
Zify 品牌名的渐变文字效果

### 第三步：前端基础组件一条指令生成

在 zify-web 中按需创建前端共享 UI 和 Hooks。
展示型公共组件放在 src/shared/ui/，例如通用表格壳、通用空状态、通用弹窗结构；
无业务 Hook 放在 src/shared/hooks/，例如 useConfirm、useRequest。
业务 API 调用统一写在 src/api/{module}Api.ts，页面或 features/*/hooks/ 负责组合请求、loading、分页、确认删除和通知提示。
公共组件不直接依赖业务 API、Zustand Store 或业务类型。

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

## 场景六：项目脚本

写一个 start 脚本，放在项目根目录。功能：检查 MySQL 和 Redis 是否可用，构建后端并后台启动，轮询等待后端健康检查通过，启动前端开发服务器。加上错误处理：任何一步失败就停止并提示。

写一个 stop 脚本，优雅停止后端和前端进程。按 PID 文件找进程，先 SIGTERM 再等待，超时 SIGKILL。

写一个 Makefile，包含以下 target：make start（启动）、make stop（停止）、make restart（重启）、make build（构建后端  +  前端）、make clean（清理构建产物）、make package（打包成可分发的  tar.gz）。














