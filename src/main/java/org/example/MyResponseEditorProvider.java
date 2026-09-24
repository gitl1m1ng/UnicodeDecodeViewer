package org.example;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 1. 编辑器类：实现ExtensionProvidedHttpResponseEditor（实际的标签内容）
class MyResponseEditor implements ExtensionProvidedHttpResponseEditor {

    // 匹配字面量的 \\uXXXX（反斜杠 + u + 4位十六进制）
    private static final Pattern UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    // 从 Content-Type 中提取 charset 参数
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*\"?([\\w.-]+)\"?", Pattern.CASE_INSENSITIVE);
    // 标签面板由 Burp 异步构建，裁剪高度需在多个时间点重试测量（早期密集）
    private static final int[] MEASURE_DELAYS_MS = {0, 100, 300, 700, 1500};

    private final HttpResponseEditor responseEditor;
    // 用裁剪面板包住原生编辑器：把顶部标签栏平移出可视区域，内容区不受影响
    private final CroppedPanel view;

    public MyResponseEditor(MontoyaApi api) {
        // 复用 Burp 原生响应编辑器：字体、主题、语法高亮等设置全部跟随 Burp
        responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        view = new CroppedPanel(responseEditor.uiComponent());
    }

    @Override
    public HttpResponse getResponse() {
        return null;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse httpRequestResponse) {
        HttpResponse response = httpRequestResponse.response();
        if (response == null) {
            return;
        }

        String body = response.bodyToString();
        if (hasUnicodeEscape(body)) {
            // 解码后按报文声明的字符集写回，避免非UTF-8响应出现乱码
            Charset charset = bodyCharset(response);
            response = response.withBody(ByteArray.byteArray(decodeUnicode(body).getBytes(charset)));
        }
        // 无论是否解码，都展示完整报文（状态行 + 响应头 + 响应体），无编码时即原始报文
        responseEditor.setResponse(response);

        for (int delay : MEASURE_DELAYS_MS) {
            if (delay == 0) {
                measureAndApplyCrop();
            } else {
                Timer timer = new Timer(delay, event -> measureAndApplyCrop());
                timer.setRepeats(false);
                timer.start();
            }
        }
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse httpRequestResponse) {
        return true;
    }

    @Override
    public String caption() {
        return "unicode解码";
    }

    @Override
    public Component uiComponent() {
        return view;
    }

    @Override
    public Selection selectedData() {
        // 将标签页内的选取透传给 Burp（右键“发送到”等操作可用）
        return responseEditor.selection().orElse(null);
    }

    @Override
    public boolean isModified() {
        return false;
    }

    // 测量标签栏在编辑器组件内的底部位置，作为裁剪高度；测量不出则保持原样（带栏展示）
    private void measureAndApplyCrop() {
        try {
            Component root = responseEditor.uiComponent();
            JTabbedPane pane = findTabbedPane(root);
            if (pane == null || pane.getTabCount() == 0) {
                return;
            }
            Rectangle tabBounds = pane.getUI().getTabBounds(pane, 0);
            if (tabBounds == null || tabBounds.height <= 0) {
                return;
            }
            // 从标签面板顶部到第一个标签底部的区域即标题栏
            Rectangle stripInRoot = SwingUtilities.convertRectangle(
                    pane, new Rectangle(0, 0, pane.getWidth(), tabBounds.y + tabBounds.height), root);
            int crop = stripInRoot.y + stripInRoot.height;
            if (crop > 0 && crop < root.getHeight() / 2) {
                view.updateCrop(crop);
            }
        } catch (RuntimeException ignored) {
            // 测量失败不影响展示
        }
    }

    private JTabbedPane findTabbedPane(Component root) {
        if (root instanceof JTabbedPane pane) {
            return pane;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTabbedPane found = findTabbedPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean hasUnicodeEscape(String input) {
        return UNICODE_ESCAPE_PATTERN.matcher(input).find();
    }

    private String decodeUnicode(String input) {
        Matcher matcher = UNICODE_ESCAPE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder(input.length());
        while (matcher.find()) {
            // 正则已保证为4位十六进制，直接按进制转换即可
            char decodedChar = (char) Integer.parseInt(matcher.group(1), 16);
            // 解码结果可能是 $ 或 \，需转义以免被 appendReplacement 当作引用符号
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(decodedChar)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Charset bodyCharset(HttpResponse response) {
        String contentType = response.headerValue("Content-Type");
        if (contentType != null) {
            Matcher matcher = CHARSET_PATTERN.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (IllegalArgumentException ignored) {
                    // 无法识别的字符集名，回退UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}

// 裁剪面板：内容组件整体上移 cropTop 像素，顶部区域被面板边界裁掉不可见。
// 组件树保持原样，报文更新、交互均由 Burp 原生逻辑处理
class CroppedPanel extends JPanel {

    private final Component content;
    private int cropTop = 0;

    CroppedPanel(Component content) {
        super(null);
        this.content = content;
        add(content);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                relayout();
            }
        });
    }

    void updateCrop(int crop) {
        if (crop != cropTop) {
            cropTop = crop;
            relayout();
        }
    }

    private void relayout() {
        content.setBounds(0, -cropTop, getWidth(), getHeight() + cropTop);
        revalidate();
        repaint();
    }
}

// 2. 工厂类：实现HttpResponseEditorProvider（负责创建编辑器实例）
class MyResponseEditorProvider implements HttpResponseEditorProvider {

    private final MontoyaApi api;

    public MyResponseEditorProvider(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext context) {
        return new MyResponseEditor(api);
    }
}
