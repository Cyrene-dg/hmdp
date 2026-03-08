USE hmdp;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tb_blog' AND index_name = 'idx_blog_liked'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE tb_blog ADD INDEX idx_blog_liked (liked)',
    'SELECT ''idx_blog_liked already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tb_follow' AND index_name = 'idx_follow_user_follow'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE tb_follow ADD INDEX idx_follow_user_follow (user_id, follow_user_id)',
    'SELECT ''idx_follow_user_follow already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tb_voucher_order' AND index_name = 'idx_voucher_order_user_voucher'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE tb_voucher_order ADD INDEX idx_voucher_order_user_voucher (user_id, voucher_id)',
    'SELECT ''idx_voucher_order_user_voucher already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
