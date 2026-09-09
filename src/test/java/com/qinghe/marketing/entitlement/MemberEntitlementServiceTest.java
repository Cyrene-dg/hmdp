package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberEntitlementServiceTest {

    @Test
    void shouldApplyMemberScopeAndBoundPagination() {
        MemberEntitlementRepository repository = mock(MemberEntitlementRepository.class);
        RightCodeProtector protector = mock(RightCodeProtector.class);
        when(repository.listViewsByMemberId(20L, EntitlementStatus.AVAILABLE, 20, 20))
                .thenReturn(Collections.emptyList());
        when(repository.countByMemberId(20L, EntitlementStatus.AVAILABLE)).thenReturn(21L);
        when(repository.findViewByNoAndMemberId("ENT-OTHER", 20L)).thenReturn(Optional.empty());
        MemberEntitlementService service = new MemberEntitlementService(repository, protector);

        EntitlementPage page = service.list(20L, EntitlementStatus.AVAILABLE, 2, 20);

        assertEquals(21L, page.total());
        assertThrows(QingheBusinessException.class,
                () -> service.requireOwned("ENT-OTHER", 20L));
        assertThrows(QingheBusinessException.class,
                () -> service.list(20L, null, 1, 101));
    }
}
