# NasDemoForTeacher

基于 Spring Boot 的多人共享 NAS 系统，供多个用户通过浏览器进行文件上传、下载与管理，支持通过 Sakura FRP 等内网穿透在公网访问。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.0.5 |
| 认证授权 | Spring Security |
| 数据库 | SQLite|
| 文件存储 | 本地磁盘，按用户隔离 |
| 内网穿透 | Sakura FRP |
| 构建工具 | Maven |
| 运行环境 | Java 17 |

## 功能
- 用户登录与权限认证（Spring Security）
- 文件上传 / 下载
- 文件列表浏览
- 按用户隔离的独立存储空间
- 支持 FRP 穿透，局域网外可访问

## 项目结构
NasDemoForTeacher/<br>
├── src/                        # 源码目录<br>
│   └── main/<br>
│       ├── java/               # 后端 Java 代码<br>
│       └── resources/          # 配置文件<br>
├── data/<br>
│   └── storage/<br>
│       └── users/              # 用户文件存储目录（按用户名隔离）<br>
│           └── root/<br>
├── nas.db                      # SQLite 数据库文件<br>
├── pom.xml                     # Maven 依赖配置<br>
└── README.md<br>

## 实现
<img width="622" height="251" alt="image" src="https://github.com/user-attachments/assets/5dc9e0a7-c44c-4062-bf86-11e9fdf43072" /><br>
- 出现该界面后表示程序正常进行<br>
<img width="592" height="582" alt="image" src="https://github.com/user-attachments/assets/a6471dec-53d1-46b1-a05c-8b7141a86a6a" /><br>
- 在输入账号密码后可以看见该用户的对应数据,并且可以进行增删改查的操作<br>
<img width="543" height="167" alt="image" src="https://github.com/user-attachments/assets/940a9a43-48cd-4ca3-b014-5ed0c9f82fbf" /><br>
- 在本地可见用户全部数据<br>

## 在上传前的优化:
<h3>1.优化客户端登录逻辑</h3>
<h4>细节: 多个用户同时登录时仅保留最后的设备登录;</h4>
<h4>解决数据下载失败</h4>
<h3>2.下载文件选项新增选项(文件夹)</h3>
<h3>3.通过压缩打包为zip实现上传(我的电脑压缩包快的你随便传)</h3>
<h3>UI界面优化,优化排列顺序</h3>

<h1>谢谢阅读!!!</h1>
<h5>没想到真有人看我的项目!!!</h5>
