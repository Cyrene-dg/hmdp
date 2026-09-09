package com.qinghe.marketing.campaign;

public final class BenefitTemplate {

    private final long id;
    private final String templateNo;
    private final BenefitTemplateDraft draft;
    private final BenefitTemplateStatus status;
    private final long version;

    public BenefitTemplate(long id, String templateNo, BenefitTemplateDraft draft,
                           BenefitTemplateStatus status, long version) {
        this.id = id;
        this.templateNo = templateNo;
        this.draft = draft;
        this.status = status;
        this.version = version;
    }

    public long id() { return id; }
    public String templateNo() { return templateNo; }
    public BenefitTemplateDraft draft() { return draft; }
    public BenefitTemplateStatus status() { return status; }
    public long version() { return version; }
}
