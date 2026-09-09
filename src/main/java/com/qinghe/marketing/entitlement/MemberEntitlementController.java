package com.qinghe.marketing.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.identity.AuthenticatedMember;
import com.qinghe.marketing.identity.MemberAuthorizer;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/member/entitlements")
public class MemberEntitlementController {

    private final MemberEntitlementService service;
    private final MemberAuthorizer authorizer;
    private final ObjectMapper objectMapper;

    public MemberEntitlementController(MemberEntitlementService service,
                                       MemberAuthorizer authorizer,
                                       ObjectMapper objectMapper) {
        this.service = service;
        this.authorizer = authorizer;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public QingheApiResponse<PageData> list(
            @RequestParam(required = false) EntitlementStatus status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AuthenticatedMember member = authorizer.require(authorization);
        EntitlementPage page = service.list(member.memberId(), status, pageNo, pageSize);
        List<SummaryData> items = new ArrayList<SummaryData>();
        for (EntitlementView view : page.items()) {
            items.add(new SummaryData(view));
        }
        return QingheApiResponse.ok("success", QingheWebRequest.requireRequestId(request),
                new PageData(items, page.pageNo(), page.pageSize(), page.total()));
    }

    @GetMapping("/{entitlementNo}")
    public QingheApiResponse<DetailData> detail(
            @PathVariable String entitlementNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AuthenticatedMember member = authorizer.require(authorization);
        EntitlementView view = service.requireOwned(entitlementNo, member.memberId());
        return QingheApiResponse.ok("success", QingheWebRequest.requireRequestId(request),
                new DetailData(view, service.protectedRightCode(view), rules(view.usageRulesJson())));
    }

    private JsonNode rules(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception invalid) {
            throw new IllegalStateException("stored usage rules are invalid", invalid);
        }
    }

    public static class SummaryData {
        private final String entitlementNo;
        private final String title;
        private final String benefitType;
        private final String status;
        private final LocalDateTime validFrom;
        private final LocalDateTime validUntil;
        private final Map<String, Object> applicableStoreSummary;

        SummaryData(EntitlementView view) {
            this.entitlementNo = view.entitlementNo();
            this.title = view.title();
            this.benefitType = view.benefitType().name();
            this.status = view.status().name();
            this.validFrom = view.validFrom();
            this.validUntil = view.validUntil();
            this.applicableStoreSummary = new LinkedHashMap<String, Object>();
            this.applicableStoreSummary.put("scope", "CAMPAIGN_STORES");
            this.applicableStoreSummary.put("storeCount", view.applicableStoreCount());
        }

        public String getEntitlementNo() { return entitlementNo; }
        public String getTitle() { return title; }
        public String getBenefitType() { return benefitType; }
        public String getStatus() { return status; }
        public LocalDateTime getValidFrom() { return validFrom; }
        public LocalDateTime getValidUntil() { return validUntil; }
        public Map<String, Object> getApplicableStoreSummary() { return applicableStoreSummary; }
    }

    public static final class DetailData extends SummaryData {
        private final String protectedRightCode;
        private final JsonNode usageRules;
        private final Object redemptionSummary;

        DetailData(EntitlementView view, String protectedRightCode, JsonNode usageRules) {
            super(view);
            this.protectedRightCode = protectedRightCode;
            this.usageRules = usageRules;
            this.redemptionSummary = null;
        }

        public String getProtectedRightCode() { return protectedRightCode; }
        public JsonNode getUsageRules() { return usageRules; }
        public Object getRedemptionSummary() { return redemptionSummary; }
    }

    public static final class PageData {
        private final List<SummaryData> items;
        private final int pageNo;
        private final int pageSize;
        private final long total;

        PageData(List<SummaryData> items, int pageNo, int pageSize, long total) {
            this.items = items;
            this.pageNo = pageNo;
            this.pageSize = pageSize;
            this.total = total;
        }

        public List<SummaryData> getItems() { return items; }
        public int getPageNo() { return pageNo; }
        public int getPageSize() { return pageSize; }
        public long getTotal() { return total; }
    }
}
