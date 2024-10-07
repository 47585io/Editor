package com.network.server.base;

public class CompletionItem
{
    public String text;        // 补全建议文本
    public String description; // 补全建议的描述
    public int type;        // 补全项的类型（例如，函数、变量、关键字等）

    public CompletionItem(String text, String description, int type) {
        this.text = text;
        this.description = description;
        this.type = type;
    }
}
