package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 경매 검색 서비스: 허용 목록에 없는 탭 · 정렬 · 분류는 쿼리를 만들기 전에 거절한다 (쿼리 문자열에 사용자 입력이 섞이지 않는다는 보장).
 * 실제 조회 결과(탭 안에서만 검색, 페이징, 건수)는 DB가 필요해서 로컬 서버에서 API로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuctionSearchServiceTest {
    @Mock EntityManager em;
    @Mock BidRepository bidRepository;
    @InjectMocks AuctionSearchService service;

    @Test
    void rejectsUnknownTabOrSort() {
        assertThrows(BadRequestException.class, () -> service.searchPublic(1L, "DROP", null, "DEADLINE", null, 0, 12));
        assertThrows(BadRequestException.class, () -> service.searchPublic(1L, "ACTIVE", null, "id; delete", null, 0, 12));
        assertThrows(BadRequestException.class, () -> service.searchMine(1L, "NOPE", null, 0, 12));
        assertThrows(BadRequestException.class, () -> service.searchShop(1L, "ENDED", "NOPE", null, 0, 12));
        assertThrows(BadRequestException.class, () -> service.searchShop(1L, "NOPE", "ALL", null, 0, 12));
        verifyNoInteractions(em);
    }

    @Test
    void myBidTabIsEmptyWithoutLoginAndDoesNotQuery() {
        var page = service.searchPublic(null, "MINE", null, "DEADLINE", null, 0, 12);

        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
        verifyNoInteractions(em);
    }
}
