package com.resolveai.chat;

public record ActionEvent(Long id, String toolName, String summary, boolean success) {}