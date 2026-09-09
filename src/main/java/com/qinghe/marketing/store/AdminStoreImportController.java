package com.qinghe.marketing.store;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/store-imports")
public class AdminStoreImportController {

    static final String STORE_IMPORT_PERMISSION = "store:import";
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;

    private final StoreImportService importService;
    private final AdminAuthorizer adminAuthorizer;

    public AdminStoreImportController(StoreImportService importService, AdminAuthorizer adminAuthorizer) {
        this.importService = importService;
        this.adminAuthorizer = adminAuthorizer;
    }

    @PostMapping("/preview")
    public QingheApiResponse<StoreImportData> preview(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal = adminAuthorizer.require(authorization, STORE_IMPORT_PERMISSION);
        String requestId = QingheWebRequest.requireRequestId(request);
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "store import file must contain 1 to 5242880 bytes");
        }
        try {
            StoreImportPreview result = importService.preview(file.getBytes(), principal.operatorId());
            return QingheApiResponse.ok("preview completed", requestId,
                    new StoreImportData(result));
        } catch (IOException exception) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "store import file could not be read");
        }
    }

    @PostMapping("/{importNo}/commit")
    public QingheApiResponse<StoreImportData> commit(
            @PathVariable String importNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        adminAuthorizer.require(authorization, STORE_IMPORT_PERMISSION);
        String requestId = QingheWebRequest.requireRequestId(request);
        return QingheApiResponse.ok("import committed", requestId,
                new StoreImportData(importService.commit(importNo)));
    }

    @GetMapping("/{importNo}")
    public QingheApiResponse<StoreImportData> get(
            @PathVariable String importNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        adminAuthorizer.require(authorization, STORE_IMPORT_PERMISSION);
        String requestId = QingheWebRequest.requireRequestId(request);
        return QingheApiResponse.ok("success", requestId,
                new StoreImportData(importService.get(importNo)));
    }

    public static final class StoreImportData {
        private final String importNo;
        private final String status;
        private final int totalRows;
        private final int validRows;
        private final int errorRows;
        private final String sourceVersion;
        private final List<StoreImportErrorData> errors;

        private StoreImportData(StoreImportPreview preview) {
            this.importNo = preview.importNo();
            this.status = preview.status().name();
            this.totalRows = preview.totalRows();
            this.validRows = preview.validRows();
            this.errorRows = preview.errorRows();
            this.sourceVersion = preview.sourceVersion();
            this.errors = new ArrayList<StoreImportErrorData>();
            for (StoreImportRow row : preview.errors()) {
                this.errors.add(new StoreImportErrorData(row));
            }
        }

        public String getImportNo() { return importNo; }
        public String getStatus() { return status; }
        public int getTotalRows() { return totalRows; }
        public int getValidRows() { return validRows; }
        public int getErrorRows() { return errorRows; }
        public String getSourceVersion() { return sourceVersion; }
        public List<StoreImportErrorData> getErrors() { return errors; }
    }

    public static final class StoreImportErrorData {
        private final int rowNumber;
        private final String storeCode;
        private final String reason;

        private StoreImportErrorData(StoreImportRow row) {
            this.rowNumber = row.rowNumber();
            this.storeCode = row.externalStoreCode();
            this.reason = row.validationError();
        }

        public int getRowNumber() { return rowNumber; }
        public String getStoreCode() { return storeCode; }
        public String getReason() { return reason; }
    }
}
