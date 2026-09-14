package com.company.orchestrator.skill.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipInputStream;

/**
 * 简历/文档文本抽取：PDF 用 PDFBox，DOCX 解 zip 内 word/document.xml，其余按 UTF-8 文本。
 * Resume/document text extraction: PDF via PDFBox, DOCX by unzipping
 * word/document.xml, anything else as UTF-8 plain text.
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {}

    /** 抽取文本上限（与识别入参一致）/ text cap, matching the extract request limit. */
    public static final int MAX_TEXT = 20000;

    /** 按扩展名抽取文本；未知格式按纯文本处理 / Extract text by extension; unknown types fall back to plain text. */
    public static String extract(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            String text = name.endsWith(".pdf") ? pdf(bytes) : name.endsWith(".docx") ? docx(bytes)
                    : new String(bytes, StandardCharsets.UTF_8);
            return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("文件解析失败，请上传 txt / md / docx / pdf 格式");
        }
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (var document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
    }

    private static String docx(byte[] bytes) throws IOException {
        String xml = null;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                if ("word/document.xml".equals(entry.getName())) { xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8); break; }
        }
        if (xml == null) throw new IllegalArgumentException("无效的 docx 文件");
        return xml.replaceAll("(?i)</w:p>", "\n").replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }
}
