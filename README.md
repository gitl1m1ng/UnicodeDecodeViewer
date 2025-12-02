# Burp Suite Unicode解码插件
一个Burp Suite扩展插件，用于在响应中提取整段Unicode编码并解码为中文，支持在Repeater/Proxy等模块中显示自定义标签。


## 功能
- 自动识别响应中的整段Unicode编码（`\uXXXX\uXXXX...`格式）
- 解码为中文并按“编码段→中文”格式展示
- 在Burp响应视图中新增“unicode解码”标签，直观查看结果
![img.png](img.png)

## 安装步骤
1. **准备Jython**：
   Burp Suite运行Java插件需依赖Jython（下载独立包：[Jython官网](https://www.jython.org/downloads.html)）。
   在Burp中配置Jython：`Extender → Options → Python Environment`选择Jython包。

2. **编译插件**：
   将`src/org/example/`下的Java文件编译为class文件：
   ```bash
   # 假设已下载Burp Montoya API包（burp-extender-api.jar）
   javac -cp burp-extender-api.jar src/org/example/*.java