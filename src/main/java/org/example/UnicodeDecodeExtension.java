package org.example;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;


import javax.swing.*;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnicodeDecodeExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 设置扩展名称
        montoyaApi.extension().setName("Unicode Decode Viewer");

        // 注册响应编辑器提供者（用于添加自定义标签）
        montoyaApi.userInterface().registerHttpResponseEditorProvider(new MyResponseEditorProvider());

        montoyaApi.logging().logToOutput("Unicode Decode插件加载成功！");
    }

}