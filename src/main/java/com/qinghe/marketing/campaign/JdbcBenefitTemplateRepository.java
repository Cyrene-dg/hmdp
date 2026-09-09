package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcBenefitTemplateRepository implements BenefitTemplateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final BenefitTemplateSnapshotCodec codec;

    public JdbcBenefitTemplateRepository(JdbcTemplate jdbcTemplate, BenefitTemplateSnapshotCodec codec) {
        this.jdbcTemplate = jdbcTemplate;
        this.codec = codec;
    }

    @Override
    public BenefitTemplate create(String templateNo, BenefitTemplateDraft draft,
                                  String rulesSnapshotJson, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_benefit_template "
                            + "(template_no, type, title, rules_snapshot, validity_type, validity_value, "
                            + "status, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, templateNo);
            statement.setString(2, draft.benefitType().name());
            statement.setString(3, draft.title());
            statement.setString(4, rulesSnapshotJson);
            statement.setString(5, draft.validityType().name());
            statement.setInt(6, storedValidityValue(draft));
            statement.setObject(7, now);
            statement.setObject(8, now);
            return statement;
        }, keys);
        return new BenefitTemplate(keys.getKey().longValue(), templateNo, draft,
                BenefitTemplateStatus.ACTIVE, 0L);
    }

    @Override
    public Optional<BenefitTemplate> findByTemplateNo(String templateNo) {
        return find("template_no", templateNo);
    }

    @Override
    public Optional<BenefitTemplate> findById(long id) {
        return find("id", id);
    }

    @Override
    public List<BenefitTemplate> list(int offset, int limit) {
        return jdbcTemplate.query("SELECT id, template_no, rules_snapshot, status, version "
                        + "FROM qh_benefit_template ORDER BY id DESC LIMIT ? OFFSET ?",
                new Object[]{limit, offset}, mapper());
    }

    private Optional<BenefitTemplate> find(String column, Object value) {
        List<BenefitTemplate> rows = jdbcTemplate.query(
                "SELECT id, template_no, rules_snapshot, status, version "
                        + "FROM qh_benefit_template WHERE " + column + " = ?",
                new Object[]{value}, mapper());
        return rows.stream().findFirst();
    }

    @Override
    public BenefitTemplate update(String templateNo, BenefitTemplateDraft draft,
                                  String rulesSnapshotJson, long expectedVersion, LocalDateTime now) {
        int updated = jdbcTemplate.update("UPDATE qh_benefit_template SET type = ?, title = ?, "
                        + "rules_snapshot = ?, validity_type = ?, validity_value = ?, version = version + 1, "
                        + "updated_at = ? WHERE template_no = ? AND version = ?",
                draft.benefitType().name(), draft.title(), rulesSnapshotJson,
                draft.validityType().name(), storedValidityValue(draft), now, templateNo, expectedVersion);
        if (updated != 1) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "benefit template version changed");
        }
        return findByTemplateNo(templateNo).orElseThrow(() -> new IllegalStateException(
                "updated benefit template disappeared"));
    }

    @Override
    public boolean isLockedByPublishedCampaign(long templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM qh_campaign c JOIN qh_campaign_publication_snapshot p "
                        + "ON p.campaign_id = c.id WHERE c.template_id = ?",
                Integer.class, templateId);
        return count != null && count > 0;
    }

    private RowMapper<BenefitTemplate> mapper() {
        return (resultSet, rowNum) -> new BenefitTemplate(
                resultSet.getLong("id"), resultSet.getString("template_no"),
                codec.decode(resultSet.getString("rules_snapshot")),
                BenefitTemplateStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"));
    }

    private static int storedValidityValue(BenefitTemplateDraft draft) {
        return draft.validityType() == ValidityType.RELATIVE_DAYS ? draft.validityValue() : 0;
    }
}
