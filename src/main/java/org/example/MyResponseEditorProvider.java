package org.example;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import burp.api.montoya.ui.Selection;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 1. 编辑器类：实现ExtensionProvidedHttpResponseEditor（实际的标签内容）
class MyResponseEditor implements ExtensionProvidedHttpResponseEditor {

    private final JTextArea decodedTextArea;
    private final JScrollPane scrollPane;
    private HttpResponse currentResponse; // 保存当前响应
    public MyResponseEditor() {
        decodedTextArea = new JTextArea();
        decodedTextArea.setLineWrap(true);
        decodedTextArea.setEditable(false);
        scrollPane = new JScrollPane(decodedTextArea);
    }

    @Override
    public HttpResponse getResponse() {
        return null;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse httpRequestResponse) {
        this.currentResponse = httpRequestResponse.response();
        //this.decodedTextArea.setText(currentResponse.bodyToString());
        String originalBody = currentResponse.bodyToString();
        // 2. 执行Unicode解码（核心功能）
        String decodedBody = decodeUnicode(originalBody);

        // 3. 将解码结果显示到文本框
        decodedTextArea.setText(decodedBody);
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
        return scrollPane;
    }

    @Override
    public Selection selectedData() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    private String decodeUnicode(String input) {
        // 正则
        Pattern pattern = Pattern.compile("(\\\\u[0-9a-fA-F]{4})+");
        Matcher matcher = pattern.matcher(input);
        // 存储结果
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String unicodeBlock = matcher.group(0);

            String decodedString = decodeSingleUnicodeBlock(unicodeBlock);

            result.append("Unicode编码: ").append(unicodeBlock).append("\n").append("Unicode解码: ").append(decodedString).append("\n\n");
        }

        if (result.isEmpty()){
            return "未匹配到Unicode编码段（格式：\\uXXXX\\uXXXX...）";
        }
        return result.toString();
    }

    private String decodeSingleUnicodeBlock(String unicodeBlock){
        Pattern singleUnicodePattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
        Matcher singleMatcher = singleUnicodePattern.matcher(unicodeBlock);
        StringBuffer decodedBuffer = new StringBuffer();
        while (singleMatcher.find()) {
            // 1. 提取十六进制部分（如"4e2d"）
            String hexCode = singleMatcher.group(1);

            // 解码unicode单个字符
            char decodedChar;
            try {
                decodedChar = (char) Integer.parseInt(hexCode, 16);
            } catch (NumberFormatException e) {
                // 处理无效编码（如非十六进制字符）
                decodedChar = '�';
            }

            // 4. 替换当前匹配的\\uXXXX为解码后的字符，追加到结果中
            singleMatcher.appendReplacement(decodedBuffer, String.valueOf(decodedChar));
        }
        return decodedBuffer.toString();
    }
}

// 2. 工厂类：实现HttpResponseEditorProvider（负责创建编辑器实例）
class MyResponseEditorProvider implements HttpResponseEditorProvider {
    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext context) {
        // 返回编辑器实例（正确的类型：ExtensionProvidedHttpResponseEditor）
        return new MyResponseEditor();
    }
}