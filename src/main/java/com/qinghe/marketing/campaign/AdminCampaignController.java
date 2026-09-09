package com.qinghe.marketing.campaign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCampaignController {

    private static final String CAMPAIGN_EDIT = "campaign:edit";
    private static final String CAMPAIGN_REVIEW = "campaign:review";
    private static final String INVENTORY_ADJUST = "inventory:adjust";
    private static final String INVENTORY_REVIEW = "inventory:review";

    private final BenefitTemplateService templateService;
    private final CampaignService campaignService;
    private final InventoryAdjustmentService inventoryService;
    private final AdminAuthorizer authorizer;
    private final ObjectMapper objectMapper;

    public AdminCampaignController(BenefitTemplateService templateService,
                                   CampaignService campaignService,
                                   InventoryAdjustmentService inventoryService,
                                   AdminAuthorizer authorizer,
                                   ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.campaignService = campaignService;
        this.inventoryService = inventoryService;
        this.authorizer = authorizer;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/benefit-templates")
    public QingheApiResponse<BenefitTemplateData> createTemplate(
            @RequestBody BenefitTemplateRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, CAMPAIGN_EDIT);
        String requestId = QingheWebRequest.requireRequestId(request);
        return QingheApiResponse.ok("benefit template created", requestId,
                new BenefitTemplateData(templateService.create(templateDraft(body))));
    }

    @PutMapping("/benefit-templates/{templateNo}")
    public QingheApiResponse<BenefitTemplateData> updateTemplate(
            @PathVariable String templateNo, @RequestBody BenefitTemplateRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, CAMPAIGN_EDIT);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        return QingheApiResponse.ok("benefit template updated", requestId,
                new BenefitTemplateData(templateService.update(
                        templateNo, templateDraft(body), requiredVersion(body.getExpectedVersion()))));
    }

    @GetMapping("/benefit-templates/{templateNo}")
    public QingheApiResponse<BenefitTemplateData> getTemplate(
            @PathVariable String templateNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, CAMPAIGN_EDIT);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                new BenefitTemplateData(templateService.require(templateNo)));
    }

    @GetMapping("/benefit-templates")
    public QingheApiResponse<PageData<BenefitTemplateData>> listTemplates(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, CAMPAIGN_EDIT);
        List<BenefitTemplateData> data = new ArrayList<BenefitTemplateData>();
        for (BenefitTemplate template : templateService.list(pageNo, pageSize)) {
            data.add(new BenefitTemplateData(template));
        }
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                new PageData<BenefitTemplateData>(pageNo, pageSize, data));
    }

    @PostMapping("/campaigns")
    public QingheApiResponse<CampaignData> createCampaign(
            @RequestBody CampaignRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, CAMPAIGN_EDIT);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        Campaign campaign = campaignService.createDraft(new CampaignDraftCommand(
                body.getName(), body.getDescription(), body.getTemplateNo(),
                dateTime(body.getClaimBeginAt()), dateTime(body.getClaimEndAt()),
                body.getInitialStock(), body.getMemberClaimLimit(), body.getStoreCodes(),
                body.getFranchiseSubsidyFen()), principal.operatorId());
        return QingheApiResponse.ok("campaign draft created", requestId, new CampaignData(campaign));
    }

    @PutMapping("/campaigns/{campaignNo}")
    public QingheApiResponse<CampaignData> updateCampaign(
            @PathVariable String campaignNo, @RequestBody CampaignRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, CAMPAIGN_EDIT);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        CampaignDraftCommand command = new CampaignDraftCommand(
                body.getName(), body.getDescription(), body.getTemplateNo(),
                dateTime(body.getClaimBeginAt()), dateTime(body.getClaimEndAt()),
                body.getInitialStock(), body.getMemberClaimLimit(), body.getStoreCodes(),
                body.getFranchiseSubsidyFen());
        Campaign campaign = campaignService.updateDraft(campaignNo, command,
                requiredVersion(body.getExpectedVersion()), principal.operatorId());
        return QingheApiResponse.ok("campaign draft updated", requestId, new CampaignData(campaign));
    }

    @PostMapping("/campaigns/{campaignNo}/submit")
    public QingheApiResponse<CampaignData> submitCampaign(
            @PathVariable String campaignNo, @RequestBody VersionCommentRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, CAMPAIGN_EDIT);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        return QingheApiResponse.ok("campaign submitted", requestId,
                new CampaignData(campaignService.submit(campaignNo,
                        requiredVersion(body.getExpectedVersion()), principal.operatorId())));
    }

    @PostMapping("/campaigns/{campaignNo}/reviews")
    public QingheApiResponse<CampaignData> reviewCampaign(
            @PathVariable String campaignNo, @RequestBody ReviewRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, CAMPAIGN_REVIEW);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        return QingheApiResponse.ok("campaign reviewed", requestId,
                new CampaignData(campaignService.review(campaignNo, decision(body.getDecision()),
                        requiredVersion(body.getExpectedVersion()), principal.operatorId(), body.getComment())));
    }

    @PostMapping("/campaigns/{campaignNo}/terminate")
    public QingheApiResponse<CampaignData> terminateCampaign(
            @PathVariable String campaignNo, @RequestBody VersionCommentRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, CAMPAIGN_REVIEW);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        return QingheApiResponse.ok("campaign terminated", requestId,
                new CampaignData(campaignService.terminate(campaignNo,
                        requiredVersion(body.getExpectedVersion()), principal.operatorId(), body.getComment())));
    }

    @GetMapping("/campaigns/{campaignNo}")
    public QingheApiResponse<CampaignData> getCampaign(
            @PathVariable String campaignNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, CAMPAIGN_EDIT);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                new CampaignData(campaignService.require(campaignNo)));
    }

    @GetMapping("/campaigns")
    public QingheApiResponse<PageData<CampaignData>> listCampaigns(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, CAMPAIGN_EDIT);
        List<CampaignData> data = new ArrayList<CampaignData>();
        for (Campaign campaign : campaignService.list(pageNo, pageSize)) {
            data.add(new CampaignData(campaign));
        }
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                new PageData<CampaignData>(pageNo, pageSize, data));
    }

    @PostMapping("/campaigns/{campaignNo}/inventory-adjustments")
    public QingheApiResponse<InventoryAdjustmentData> createInventoryAdjustment(
            @PathVariable String campaignNo, @RequestBody InventoryCreateRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, INVENTORY_ADJUST);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        InventoryAdjustment result = inventoryService.create(campaignNo, body.getIncrementStock(),
                body.getReason(), requiredVersion(body.getExpectedCampaignVersion()), principal.operatorId());
        return QingheApiResponse.ok("inventory adjustment created", requestId,
                new InventoryAdjustmentData(result, null, null));
    }

    @PostMapping("/inventory-adjustments/{adjustmentNo}/submit")
    public QingheApiResponse<InventoryAdjustmentData> submitInventoryAdjustment(
            @PathVariable String adjustmentNo, @RequestBody VersionCommentRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, INVENTORY_ADJUST);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        InventoryAdjustment result = inventoryService.submit(adjustmentNo,
                requiredVersion(body.getExpectedVersion()), principal.operatorId());
        return QingheApiResponse.ok("inventory adjustment submitted", requestId,
                new InventoryAdjustmentData(result, null, null));
    }

    @PostMapping("/inventory-adjustments/{adjustmentNo}/reviews")
    public QingheApiResponse<InventoryAdjustmentData> reviewInventoryAdjustment(
            @PathVariable String adjustmentNo, @RequestBody ReviewRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = authorizer.require(authorization, INVENTORY_REVIEW);
        String requestId = QingheWebRequest.requireRequestId(request);
        requireBody(body);
        InventoryAdjustmentResult result = inventoryService.review(adjustmentNo,
                decision(body.getDecision()), requiredVersion(body.getExpectedVersion()),
                principal.operatorId(), body.getComment());
        return QingheApiResponse.ok("inventory adjustment reviewed", requestId,
                new InventoryAdjustmentData(result.adjustment(),
                        result.beforeTotalStock(), result.afterTotalStock()));
    }

    @GetMapping("/inventory-adjustments/{adjustmentNo}")
    public QingheApiResponse<InventoryAdjustmentData> getInventoryAdjustment(
            @PathVariable String adjustmentNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, INVENTORY_ADJUST);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                new InventoryAdjustmentData(inventoryService.require(adjustmentNo), null, null));
    }

    private BenefitTemplateDraft templateDraft(BenefitTemplateRequest body) {
        requireBody(body);
        try {
            return new BenefitTemplateDraft(body.getTemplateName(), benefitType(body.getBenefitType()),
                    body.getTitle(), body.getDescription(), body.getProductCode(), body.getBenefitValueFen(),
                    validityType(body.getValidityType()), body.getValidityValue(),
                    nullableDateTime(body.getValidFrom()), nullableDateTime(body.getValidUntil()),
                    objectMapper.writeValueAsString(body.getUsageRules()));
        } catch (JsonProcessingException exception) {
            throw invalid("usageRules cannot be serialized");
        }
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(BusinessClock.BUSINESS_ZONE).toLocalDateTime();
        } catch (RuntimeException invalid) {
            throw invalid("date-time must be ISO 8601 with an offset");
        }
    }

    private static LocalDateTime nullableDateTime(String value) {
        return value == null || value.trim().isEmpty() ? null : dateTime(value);
    }

    private static BenefitType benefitType(String value) {
        try {
            return value == null ? null : BenefitType.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw invalid("unsupported benefitType");
        }
    }

    private static ValidityType validityType(String value) {
        try {
            return value == null ? null : ValidityType.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw invalid("unsupported validityType");
        }
    }

    private static ReviewDecision decision(String value) {
        try {
            return value == null ? null : ReviewDecision.valueOf(value);
        } catch (IllegalArgumentException invalid) {
            throw invalid("decision must be APPROVE or REJECT");
        }
    }

    private static long requiredVersion(Long version) {
        if (version == null || version < 0) {
            throw invalid("expectedVersion is required and cannot be negative");
        }
        return version;
    }

    private static void requireBody(Object body) {
        if (body == null) {
            throw invalid("request body is required");
        }
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }

    public static final class BenefitTemplateRequest {
        private String templateName;
        private String benefitType;
        private String title;
        private String description;
        private String productCode;
        private Long benefitValueFen;
        private String validityType;
        private Integer validityValue;
        private String validFrom;
        private String validUntil;
        private JsonNode usageRules;
        private Long expectedVersion;
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String value) { this.templateName = value; }
        public String getBenefitType() { return benefitType; }
        public void setBenefitType(String value) { this.benefitType = value; }
        public String getTitle() { return title; }
        public void setTitle(String value) { this.title = value; }
        public String getDescription() { return description; }
        public void setDescription(String value) { this.description = value; }
        public String getProductCode() { return productCode; }
        public void setProductCode(String value) { this.productCode = value; }
        public Long getBenefitValueFen() { return benefitValueFen; }
        public void setBenefitValueFen(Long value) { this.benefitValueFen = value; }
        public String getValidityType() { return validityType; }
        public void setValidityType(String value) { this.validityType = value; }
        public Integer getValidityValue() { return validityValue; }
        public void setValidityValue(Integer value) { this.validityValue = value; }
        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String value) { this.validFrom = value; }
        public String getValidUntil() { return validUntil; }
        public void setValidUntil(String value) { this.validUntil = value; }
        public JsonNode getUsageRules() { return usageRules; }
        public void setUsageRules(JsonNode value) { this.usageRules = value; }
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { this.expectedVersion = value; }
    }

    public static final class CampaignRequest {
        private String name;
        private String description;
        private String templateNo;
        private String claimBeginAt;
        private String claimEndAt;
        private long initialStock;
        private int memberClaimLimit;
        private List<String> storeCodes;
        private Long franchiseSubsidyFen;
        private Long expectedVersion;
        public String getName() { return name; }
        public void setName(String value) { this.name = value; }
        public String getDescription() { return description; }
        public void setDescription(String value) { this.description = value; }
        public String getTemplateNo() { return templateNo; }
        public void setTemplateNo(String value) { this.templateNo = value; }
        public String getClaimBeginAt() { return claimBeginAt; }
        public void setClaimBeginAt(String value) { this.claimBeginAt = value; }
        public String getClaimEndAt() { return claimEndAt; }
        public void setClaimEndAt(String value) { this.claimEndAt = value; }
        public long getInitialStock() { return initialStock; }
        public void setInitialStock(long value) { this.initialStock = value; }
        public int getMemberClaimLimit() { return memberClaimLimit; }
        public void setMemberClaimLimit(int value) { this.memberClaimLimit = value; }
        public List<String> getStoreCodes() { return storeCodes; }
        public void setStoreCodes(List<String> value) { this.storeCodes = value; }
        public Long getFranchiseSubsidyFen() { return franchiseSubsidyFen; }
        public void setFranchiseSubsidyFen(Long value) { this.franchiseSubsidyFen = value; }
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { this.expectedVersion = value; }
    }

    public static class VersionCommentRequest {
        private Long expectedVersion;
        private String comment;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { this.expectedVersion = value; }
        public String getComment() { return comment; }
        public void setComment(String value) { this.comment = value; }
    }

    public static final class ReviewRequest extends VersionCommentRequest {
        private String decision;
        public String getDecision() { return decision; }
        public void setDecision(String value) { this.decision = value; }
    }

    public static final class InventoryCreateRequest {
        private long incrementStock;
        private String reason;
        private Long expectedCampaignVersion;
        public long getIncrementStock() { return incrementStock; }
        public void setIncrementStock(long value) { this.incrementStock = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { this.reason = value; }
        public Long getExpectedCampaignVersion() { return expectedCampaignVersion; }
        public void setExpectedCampaignVersion(Long value) { this.expectedCampaignVersion = value; }
    }

    public static final class BenefitTemplateData {
        private final String templateNo;
        private final String status;
        private final long version;
        private final String title;
        private BenefitTemplateData(BenefitTemplate template) {
            this.templateNo = template.templateNo();
            this.status = template.status().name();
            this.version = template.version();
            this.title = template.draft().title();
        }
        public String getTemplateNo() { return templateNo; }
        public String getStatus() { return status; }
        public long getVersion() { return version; }
        public String getTitle() { return title; }
    }

    public static final class CampaignData {
        private final String campaignNo;
        private final String status;
        private final long version;
        private final String name;
        private final LocalDateTime claimBeginAt;
        private final LocalDateTime claimEndAt;
        private CampaignData(Campaign campaign) {
            this.campaignNo = campaign.campaignNo();
            this.status = campaign.status().name();
            this.version = campaign.version();
            this.name = campaign.name();
            this.claimBeginAt = campaign.beginAt();
            this.claimEndAt = campaign.endAt();
        }
        public String getCampaignNo() { return campaignNo; }
        public String getStatus() { return status; }
        public long getVersion() { return version; }
        public String getName() { return name; }
        public LocalDateTime getClaimBeginAt() { return claimBeginAt; }
        public LocalDateTime getClaimEndAt() { return claimEndAt; }
    }

    public static final class InventoryAdjustmentData {
        private final String adjustmentNo;
        private final String status;
        private final long incrementStock;
        private final long version;
        private final Long beforeTotalStock;
        private final Long afterTotalStock;
        private InventoryAdjustmentData(InventoryAdjustment adjustment,
                                        Long beforeTotalStock, Long afterTotalStock) {
            this.adjustmentNo = adjustment.adjustmentNo();
            this.status = adjustment.status().name();
            this.incrementStock = adjustment.incrementStock();
            this.version = adjustment.version();
            this.beforeTotalStock = beforeTotalStock;
            this.afterTotalStock = afterTotalStock;
        }
        public String getAdjustmentNo() { return adjustmentNo; }
        public String getStatus() { return status; }
        public long getIncrementStock() { return incrementStock; }
        public long getVersion() { return version; }
        public Long getBeforeTotalStock() { return beforeTotalStock; }
        public Long getAfterTotalStock() { return afterTotalStock; }
    }

    public static final class PageData<T> {
        private final int pageNo;
        private final int pageSize;
        private final List<T> items;
        private PageData(int pageNo, int pageSize, List<T> items) {
            this.pageNo = pageNo;
            this.pageSize = pageSize;
            this.items = items;
        }
        public int getPageNo() { return pageNo; }
        public int getPageSize() { return pageSize; }
        public List<T> getItems() { return items; }
    }
}
