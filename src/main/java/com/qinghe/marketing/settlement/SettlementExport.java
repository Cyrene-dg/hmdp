package com.qinghe.marketing.settlement;

public final class SettlementExport {
    private final String fileName;
    private final byte[] content;

    public SettlementExport(String fileName, byte[] content) {
        this.fileName = fileName; this.content = content.clone();
    }
    public String fileName() { return fileName; }
    public byte[] content() { return content.clone(); }
}
