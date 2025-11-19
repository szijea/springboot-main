# 前端样式本地化

已将 Tailwind CDN 替换为本地构建：

## 构建步骤
```powershell
npm install
npm run build:css
```
生成文件: `src/main/resources/static/css/tailwind.css`

更新后的 HTML 使用 `<link href="css/tailwind.css" rel="stylesheet">` 引入，不再依赖运行时 CDN 脚本。

## 继续优化
- 可以将内联的自定义样式迁移到 `tailwind-input.css` 并使用 @layer utilities。 
- 若需裁剪文件��积，确保 content 路径准确。

