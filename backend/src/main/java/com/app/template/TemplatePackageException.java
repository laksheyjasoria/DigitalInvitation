package com.app.template;

public class TemplatePackageException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public TemplatePackageException(String message) { super(message); }
    public TemplatePackageException(String message, Throwable cause) { super(message, cause); }
}