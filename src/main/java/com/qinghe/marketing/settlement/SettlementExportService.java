package com.qinghe.marketing.settlement;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SettlementExportService {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final SettlementRepository repository;
    private final BusinessClock clock;

    public SettlementExportService(SettlementRepository repository, BusinessClock clock) {
        this.repository = repository; this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public SettlementExport export(String settlementBatchNo, String operatorId,
                                   String requestId) {
        if (blank(operatorId) || blank(requestId)) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "settlement export identity is invalid");
        }
        SettlementBatch batch = repository.findByBatchNoForUpdate(settlementBatchNo)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND,
                        "settlement batch does not exist"));
        List<SettlementExportRow> rows = repository.exportRows(batch.id());
        StringBuilder csv = new StringBuilder("settlement_batch_no,business_date,campaign_no,"
                + "store_code,redemption_no,pos_request_no,subsidy_fen,reconciliation_status,"
                + "confirmation_status\r\n");
        for (SettlementExportRow row : rows) {
            append(csv, row.settlementBatchNo(), row.businessDate().toString(), row.campaignNo(),
                    row.storeCode(), row.redemptionNo(), row.posRequestNo(),
                    String.valueOf(row.subsidyFen()), row.reconciliationStatus(),
                    row.confirmationStatus());
        }
        LocalDateTime now = clock.dateTime();
        repository.insertAudit(operatorId, "SETTLEMENT_EXPORT", "SETTLEMENT_BATCH",
                batch.batchNo(), batch.status().name(), batch.status().name(),
                "export review details; confirmation does not mean payment", "SUCCESS",
                requestId, now);
        return new SettlementExport("QH_SETTLEMENT_" + batch.batchNo() + "_"
                + FILE_TIME.format(now) + ".csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void append(StringBuilder output, String... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) output.append(',');
            output.append(escape(values[index]));
        }
        output.append("\r\n");
    }
    private static String escape(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
