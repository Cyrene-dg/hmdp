package com.hmdp.service;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 只验证事务方法的关键边界：唯一索引冲突不能在事务内被吞掉，
 * 这样 Spring 才能回滚此前执行的数据库库存扣减。
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderTransactionServiceTest {

    @Mock
    private VoucherOrderMapper voucherOrderMapper;
    @Mock
    private ISeckillVoucherService seckillVoucherService;
    @Mock
    private UpdateChainWrapper<SeckillVoucher> updateChain;

    private VoucherOrderTransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new VoucherOrderTransactionService();
        ReflectionTestUtils.setField(transactionService, "voucherOrderMapper", voucherOrderMapper);
        ReflectionTestUtils.setField(transactionService, "seckillVoucherService", seckillVoucherService);
    }

    @Test
    void persist_shouldReturnAlreadyExistsBeforeDecrementingStock() {
        VoucherOrder order = order();
        when(voucherOrderMapper.selectCount(any())).thenReturn(1L);

        VoucherOrderTransactionService.PersistResult result = transactionService.persist(order);

        assertEquals(VoucherOrderTransactionService.PersistResult.ALREADY_EXISTS, result);
        verifyNoInteractions(seckillVoucherService);
        verify(voucherOrderMapper, never()).insert(any(VoucherOrder.class));
    }

    @Test
    void persist_shouldLetDuplicateKeyEscapeSoTransactionCanRollbackStock() {
        VoucherOrder order = order();
        when(voucherOrderMapper.selectCount(any())).thenReturn(0L);
        when(seckillVoucherService.update()).thenReturn(updateChain);
        when(updateChain.setSql(eq("stock = stock - 1"))).thenReturn(updateChain);
        when(updateChain.eq(eq("voucher_id"), eq(order.getVoucherId()))).thenReturn(updateChain);
        when(updateChain.gt(eq("stock"), eq(0))).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        when(voucherOrderMapper.insert(order)).thenThrow(new DuplicateKeyException("duplicate order"));

        assertThrows(DuplicateKeyException.class, () -> transactionService.persist(order));
        verify(voucherOrderMapper).insert(order);
    }

    private VoucherOrder order() {
        VoucherOrder order = new VoucherOrder();
        order.setId(1001L);
        order.setUserId(2001L);
        order.setVoucherId(3001L);
        return order;
    }
}
