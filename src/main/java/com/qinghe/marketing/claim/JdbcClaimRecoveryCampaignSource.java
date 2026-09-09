package com.qinghe.marketing.claim;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcClaimRecoveryCampaignSource implements ClaimRecoveryCampaignSource {

    private final JdbcTemplate jdbcTemplate;

    public JdbcClaimRecoveryCampaignSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Long> findCampaignIds(int offset, int limit) {
        return jdbcTemplate.query("SELECT id FROM qh_campaign WHERE status IN "
                        + "('ACTIVE', 'ENDED', 'TERMINATED') ORDER BY id LIMIT ? OFFSET ?",
                new Object[]{limit, offset}, (resultSet, rowNum) -> resultSet.getLong("id"));
    }
}
