package org.example;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class UnicodeDecodeExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 设置扩展名称
        montoyaApi.extension().setName("Unicode Decode Viewer");

        // 注册响应编辑器提供者（用于添加自定义标签），传入 api 以便创建 Burp 原生编辑器
        montoyaApi.userInterface().registerHttpResponseEditorProvider(new MyResponseEditorProvider(montoyaApi));

        montoyaApi.logging().logToOutput("Unicode Decode插件加载成功！");
    }

}