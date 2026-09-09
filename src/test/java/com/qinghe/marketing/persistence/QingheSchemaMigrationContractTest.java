package com.qinghe.marketing.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QingheSchemaMigrationContractTest {

    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "qh_store", "qh_member_mapping", "qh_benefit_template", "qh_campaign",
            "qh_campaign_inventory", "qh_inventory_adjustment", "qh_campaign_store",
            "qh_claim_request", "qh_member_entitlement", "qh_redemption",
            "qh_redemption_reversal", "qh_subsidy_candidate", "qh_recon_batch",
            "qh_recon_record", "qh_settlement_batch", "qh_settlement_detail",
            "qh_outbox_event", "qh_operation_log"
    );

    @Test
    void migrationMustBeAdditiveAndContainReviewedCoreTables() throws IOException {
        String sql = resource("db/qinghe/migration/V001__create_qinghe_core.sql");
        for (String table : REQUIRED_TABLES) {
            assertTrue(sql.contains("CREATE TABLE " + table), "missing " + table);
        }
        assertFalse(sql.matches("(?is).*(ALTER|DROP|TRUNCATE)\\s+TABLE\\s+tb_.*"),
                "Qinghe migration must not mutate legacy tb_ tables");
        assertTrue(sql.contains("subsidy_fen BIGINT"));
        assertTrue(sql.contains("request_digest CHAR(64)"));
        assertTrue(sql.contains("trace_id VARCHAR(64)"));
    }

    @Test
    void identityAndStoreMigrationMustKeepCredentialsByReferenceAndPersistReplayGuard() throws IOException {
        String sql = resource("db/qinghe/migration/V002__create_identity_and_store_support.sql");

        assertTrue(sql.contains("CREATE TABLE qh_platform_session"));
        assertTrue(sql.contains("CREATE TABLE qh_store_import_batch"));
        assertTrue(sql.contains("CREATE TABLE qh_store_import_row"));
        assertTrue(sql.contains("CREATE TABLE qh_pos_credential"));
        assertTrue(sql.contains("CREATE TABLE qh_pos_nonce"));
        assertTrue(sql.contains("secret_reference VARCHAR(255)"));
        assertTrue(sql.contains("UNIQUE KEY uq_qh_pos_nonce_client_value"));
        assertFalse(sql.matches("(?is).*(ALTER|DROP|TRUNCATE)\\s+TABLE\\s+tb_.*"),
                "WP-02 migration must not mutate legacy tb_ tables");
    }

    @Test
    void campaignMigrationMustPersistIndependentReviewAndImmutablePublicationSnapshot() throws IOException {
        String sql = resource("db/qinghe/migration/V003__add_campaign_publication_and_reviews.sql");

        assertTrue(sql.contains("CREATE TABLE qh_campaign_review"));
        assertTrue(sql.contains("CREATE TABLE qh_campaign_publication_snapshot"));
        assertTrue(sql.contains("UNIQUE KEY uq_qh_campaign_publication_campaign"));
        assertTrue(sql.contains("member_claim_limit INT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("review_comment VARCHAR(512)"));
        assertFalse(sql.matches("(?is).*(ALTER|DROP|TRUNCATE)\\s+TABLE\\s+tb_.*"),
                "WP-03 migration must not mutate legacy tb_ tables");
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("resource not found: " + name);
            }
            byte[] bytes = new byte[16384];
            int count;
            StringBuilder result = new StringBuilder();
            while ((count = input.read(bytes)) >= 0) {
                result.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
            }
            return result.toString();
        }
    }
}
