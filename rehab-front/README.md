# 康复课程预约系统前端

这是一个独立静态前端项目，不依赖原来的 `TakeOut/nginx-1.20.2` 代码。

## 目录说明

- `index.html`：学生端/教师端单页入口
- `assets/config.js`：接口与 WebSocket 配置
- `assets/api.js`：后端接口封装
- `assets/app.js`：页面交互逻辑
- `assets/styles.css`：页面样式
- `nginx.rehab.conf.example`：Nginx 部署示例

## 本地使用

推荐通过 Nginx 部署，避免浏览器跨域问题。

1. 启动后端服务，默认端口为 `8080`。
2. 将 `rehab-front` 目录复制到 Nginx 的 `html` 目录下，例如：`html/rehab-front`。
3. 参考 `nginx.rehab.conf.example` 配置静态资源和后端代理。
4. 打开 `http://localhost/rehab/`。

## 测试账号

教师端可使用 SQL 初始账号：

- 手机号：`13800000001`
- 密码：`123456`

学生端输入新手机号和密码会自动注册。

## 如果不用 Nginx 代理

可以修改 `assets/config.js`：

```js
window.REHAB_CONFIG = {
  apiBase: 'http://localhost:8080',
  wsPath: '/ws/appointment'
};
```

但后端需要允许跨域，否则浏览器会拦截请求。
