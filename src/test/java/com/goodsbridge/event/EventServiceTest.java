package com.goodsbridge.event;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.event.dto.ShopEventRequest;
import com.goodsbridge.item.Category;
import com.goodsbridge.item.CategoryJpaRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.item.SaleType;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopStatus;
import com.goodsbridge.shop.ShopSubscriptionService;
import com.goodsbridge.user.Status;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이벤트 참가/취소/가게 취소/마감 정산
 *  - 서버가 참가를 거절하면 이미 승인된 PortOne 결제를 자동 취소
 *  - 가게가 이벤트를 취소하면 참가자 전원 환불
 *  - 참가 취소 시 자리 반납(인원 감소, 정원 마감 해제), 마감 후/취소된 이벤트는 참가 취소 불가
 *  - 마감시간이 지나면 남은 참가자 참가비를 가게 정산에 기록
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    @Mock ShopRepository shopRepository;
    @Mock ItemRepository itemRepository;
    @Mock ShopEventRepository shopEventRepository;
    @Mock EntityManager entityManager;
    @Mock EventParticipationRepository eventParticipationRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @Mock PaymentRepository paymentRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @Mock TransactionTemplate transactionTemplate;
    @Mock ShopSubscriptionService shopSubscriptionService;
    @Mock CategoryJpaRepository categoryRepository;
    @InjectMocks EventService eventService;

    private static final Long EVENT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final int FEE = 5_000;

    @BeforeEach
    void setUp() {
        // 실제 트랜잭션 없이 콜백만 바로 실행
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        // 기본은 구독 중인 가게 (구독 종료 케이스는 각 테스트에서 덮어씀)
        lenient().when(shopSubscriptionService.isSubscriptionActive(any())).thenReturn(true);
    }

    private ShopEvent event(int capacity, LocalDateTime deadline) {
        return event(capacity, deadline, 1);
    }

    private ShopEvent event(int capacity, LocalDateTime deadline, int maxPerUser) {
        return event(capacity, deadline, maxPerUser, null);
    }

    private ShopEvent event(int capacity, LocalDateTime deadline, int maxPerUser, java.time.LocalDate visitUntil) {
        ShopEvent shopEvent = ShopEvent.builder()
            .shop(mock(Shop.class)).item(mock(Item.class))
            .capacity(capacity).feeAmount(FEE).deadline(deadline).maxPerUser(maxPerUser).visitUntil(visitUntil)
            .build();
        ReflectionTestUtils.setField(shopEvent, "id", EVENT_ID);
        return shopEvent;
    }

    private User activeUser() {
        User user = mock(User.class);
        lenient().when(user.getStatus()).thenReturn(Status.ACTIVE);
        lenient().when(user.getId()).thenReturn(USER_ID);
        return user;
    }

    private EventParticipationRequest request(String paymentId) {
        EventParticipationRequest request = new EventParticipationRequest();
        ReflectionTestUtils.setField(request, "paymentId", paymentId);
        return request;
    }

    private EventParticipation participation(ShopEvent shopEvent, String paymentId) {
        return EventParticipation.builder()
            .shopEvent(shopEvent).user(activeUser()).paymentId(paymentId).ticketNumber(1)
            .build();
    }

    private void joinable(ShopEvent shopEvent) {
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    // ---------- A. 참가 거절 시 결제 자동 취소

    @Test
    @DisplayName("정원이 차서 참가가 거절되면 이미 승인된 결제를 자동 취소한다")
    void join_full_cancelsPayment() {
        ShopEvent shopEvent = event(1, LocalDateTime.now().plusDays(1));
        shopEvent.increaseCurrentCount(1);
        joinable(shopEvent);

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
    }

    @Test
    @DisplayName("1인 최대 수량(1개)을 이미 채운 사람이 또 구매하면 거절하고 결제를 자동 취소한다")
    void join_alreadyAtMax_cancelsPayment() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        joinable(shopEvent);
        EventParticipation before = participation(shopEvent, "pay-0");
        when(eventParticipationRepository.findJoinedByUserIdAndShopEventId(USER_ID, EVENT_ID))
            .thenReturn(List.of(before));

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
    }

    @Test
    @DisplayName("가게 주인은 자기 가게 이벤트에 참가할 수 없고, 이미 승인된 결제는 자동 취소한다")
    void join_ownShopEvent_rejectedAndPaymentCancelled() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(true);
        joinable(shopEvent);

        assertThrows(ForbiddenException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
        assertEquals(0, shopEvent.getCurrentCount());
    }

    @Test
    @DisplayName("가게 계정은 다른 가게 이벤트에도 참가할 수 없고, 이미 승인된 결제는 자동 취소한다")
    void join_otherShopOwner_rejectedAndPaymentCancelled() {
        joinable(event(5, LocalDateTime.now().plusDays(1)));
        when(shopRepository.existsByOwnerUserIdAndDeletedFalse(USER_ID)).thenReturn(true);

        assertThrows(ForbiddenException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
    }

    // ---------- 마감시간 체크

    @Test
    @DisplayName("마감시간이 지난 이벤트는 상태가 아직 OPEN이어도 참가할 수 없다")
    void join_afterDeadline_rejected() {
        joinable(event(5, LocalDateTime.now().minusMinutes(1)));

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
    }

    // ---------- 구독 종료된 가게의 이벤트

    @Test
    @DisplayName("가게 구독이 끊긴 이벤트는 신규 참가를 막고, 이미 승인된 결제는 자동 취소한다")
    void join_subscriptionEnded_rejectedAndPaymentCancelled() {
        joinable(event(5, LocalDateTime.now().plusDays(1)));
        when(shopSubscriptionService.isSubscriptionActive(any())).thenReturn(false);

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
    }

    @Test
    @DisplayName("구독 중이 아닌 가게는 이벤트를 열 수 없다")
    void createEvent_subscriptionInactive_rejected() {
        when(shopRepository.findById(2L)).thenReturn(Optional.of(approvedShop()));
        when(shopSubscriptionService.isSubscriptionActive(2L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> eventService.save(2L, USER_ID, createRequest()));

        verify(itemRepository, never()).save(any());
        verify(shopEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("이벤트를 열면 이벤트 상품(판매유형 EVENT)을 새로 등록하고 그 상품으로 이벤트를 만든다")
    void createEvent_registersNewEventItem() {
        when(shopRepository.findById(2L)).thenReturn(Optional.of(approvedShop()));
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(Category.builder().name("포토카드").build()));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shopEventRepository.save(any(ShopEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        eventService.save(2L, USER_ID, createRequest());

        ArgumentCaptor<Item> item = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(item.capture());
        assertEquals(SaleType.EVENT, item.getValue().getSaleType());
        assertEquals(ItemStatus.ON_SALE, item.getValue().getStatus());
        assertEquals(2L, item.getValue().getShopId());
        assertEquals(USER_ID, item.getValue().getSellerUserId());
        assertEquals("한정판 세트", item.getValue().getTitle());

        ArgumentCaptor<ShopEvent> event = ArgumentCaptor.forClass(ShopEvent.class);
        verify(shopEventRepository).save(event.capture());
        assertEquals(item.getValue(), event.getValue().getItem());
        assertEquals(10, event.getValue().getCapacity());
        assertEquals(FEE, event.getValue().getFeeAmount());
    }

    // ---------- 참가자 명단

    @Test
    @DisplayName("가게 주인은 참가자 명단을 볼 수 있다")
    void participants_owner_returnsList() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(true);
        when(shopEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        EventParticipation p = participation(shopEvent, "pay-1");
        when(eventParticipationRepository.findAllByShopEventId(EVENT_ID)).thenReturn(List.of(p));

        List<EventParticipationResponse> people = eventService.getParticipants(EVENT_ID, USER_ID);

        assertEquals(1, people.size());
        assertEquals(1, people.get(0).getTicketNumber());
    }

    @Test
    @DisplayName("가게 주인이 아니면 참가자 명단을 볼 수 없다")
    void participants_notOwner_forbidden() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(false);
        when(shopEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(shopEvent));

        assertThrows(ForbiddenException.class, () -> eventService.getParticipants(EVENT_ID, USER_ID));

        verify(eventParticipationRepository, never()).findAllByShopEventId(any());
    }

    private Shop approvedShop() {
        return Shop.builder().ownerUserId(USER_ID).name("가게").region("서울").address("주소").status(ShopStatus.APPROVED).build();
    }

    private ShopEventRequest createRequest() {
        ShopEventRequest request = new ShopEventRequest();
        ReflectionTestUtils.setField(request, "itemTitle", "한정판 세트");
        ReflectionTestUtils.setField(request, "categoryId", 4L);
        ReflectionTestUtils.setField(request, "itemPrice", 30000);
        ReflectionTestUtils.setField(request, "capacity", 10);
        ReflectionTestUtils.setField(request, "feeAmount", FEE);
        ReflectionTestUtils.setField(request, "deadline", LocalDateTime.now().plusDays(3));
        return request;
    }

    // ---------- C. 번호표

    @Test
    @DisplayName("번호표는 인원수가 아니라 참가중인 최대 번호 + 1이다 (참가중인 번호와 겹치지 않음)")
    void join_ticketNumber_isMaxPlusOne() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        shopEvent.increaseCurrentCount(1); // 현재 1명 (앞서 3명이 참가했고 2명이 취소한 상황)
        joinable(shopEvent);
        when(paymentService.verify("pay-1", FEE, PaymentPurpose.EVENT_FEE, USER_ID)).thenReturn("pay-1");
        when(eventParticipationRepository.findMaxTicketNumber(EVENT_ID)).thenReturn(3);

        EventParticipationResponse response = eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1"));

        assertEquals(4, response.getTicketNumber());
        assertEquals(2, shopEvent.getCurrentCount());
        verify(paymentService, never()).cancelUnrecorded(anyString(), anyString());
    }

    // ---------- 수량 선택 (1인 최대 10개)

    @Test
    @DisplayName("수량 3개로 참가하면 참가비 × 3을 검증하고 수량 3개를 차감한다")
    void join_quantity_chargesFeeTimesQuantity() {
        ShopEvent shopEvent = event(100, LocalDateTime.now().plusDays(1), 10);
        joinable(shopEvent);
        when(paymentService.verify("pay-1", FEE * 3, PaymentPurpose.EVENT_FEE, USER_ID)).thenReturn("pay-1");
        EventParticipationRequest request = request("pay-1");
        ReflectionTestUtils.setField(request, "quantity", 3);

        EventParticipationResponse response = eventService.saveEventParticipation(EVENT_ID, USER_ID, request);

        assertEquals(3, response.getQuantity());
        assertEquals(3, shopEvent.getCurrentCount());
    }

    @Test
    @DisplayName("남은 수량보다 많이 신청하면 거절하고 결제를 자동 취소한다")
    void join_quantityOverRemaining_rejected() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1), 10);
        shopEvent.increaseCurrentCount(3); // 남은 2개
        joinable(shopEvent);
        EventParticipationRequest request = request("pay-1");
        ReflectionTestUtils.setField(request, "quantity", 3);

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
        assertEquals(3, shopEvent.getCurrentCount());
    }

    @Test
    @DisplayName("가게가 정한 1인당 최대 수량(1개)보다 많이 신청하면 거절하고 결제를 자동 취소한다")
    void join_quantityOverMaxPerUser_rejected() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1), 1);
        joinable(shopEvent);
        EventParticipationRequest request = request("pay-1");
        ReflectionTestUtils.setField(request, "quantity", 2);

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
        assertEquals(0, shopEvent.getCurrentCount());
    }

    // ---------- 이벤트 수정 / 정산 보기

    private com.goodsbridge.event.dto.ShopEventUpdateRequest updateRequest(String field, Object value) {
        com.goodsbridge.event.dto.ShopEventUpdateRequest request = new com.goodsbridge.event.dto.ShopEventUpdateRequest();
        ReflectionTestUtils.setField(request, field, value);
        return request;
    }

    private ShopEvent editableEvent(int currentCount) {
        ShopEvent shopEvent = event(10, LocalDateTime.now().plusDays(2), 3);
        if (currentCount > 0) shopEvent.increaseCurrentCount(currentCount);
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(true);
        lenient().when(shopEvent.getItem().getPrice()).thenReturn(30000);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        return shopEvent;
    }

    @Test
    @DisplayName("참가자가 있어도 설명과 방문 날짜는 수정할 수 있다")
    void updateEvent_withParticipants_descriptionAndVisitOk() {
        ShopEvent shopEvent = editableEvent(2);
        com.goodsbridge.event.dto.ShopEventUpdateRequest request = updateRequest("visitUntil", LocalDateTime.now().plusDays(9).toLocalDate());
        ReflectionTestUtils.setField(request, "itemDescription", "수정한 설명");

        eventService.updateEvent(EVENT_ID, USER_ID, request);

        assertEquals(LocalDateTime.now().plusDays(9).toLocalDate(), shopEvent.getVisitUntil());
        verify(shopEvent.getItem()).update(any(), any(), any(), eq("수정한 설명"), eq(30000), any(), any(), any());
    }

    @Test
    @DisplayName("참가자가 있으면 수량, 가격, 참가비, 마감 시각은 수정할 수 없다")
    void updateEvent_withParticipants_lockedFieldsRejected() {
        ShopEvent shopEvent = editableEvent(2);

        assertThrows(BadRequestException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("capacity", 50)));
        assertThrows(BadRequestException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("feeAmount", 7000)));
        assertThrows(BadRequestException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("itemPrice", 50000)));
        assertEquals(10, shopEvent.getCapacity());
        assertEquals(FEE, shopEvent.getFeeAmount());
    }

    @Test
    @DisplayName("참가자가 없고 모집 마감 전이면 수량과 참가비도 수정할 수 있다")
    void updateEvent_noParticipants_conditionsOk() {
        ShopEvent shopEvent = editableEvent(0);

        eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("capacity", 50));

        assertEquals(50, shopEvent.getCapacity());
    }

    @Test
    @DisplayName("본인 가게 이벤트가 아니면 수정할 수 없다")
    void updateEvent_notOwner_forbidden() {
        ShopEvent shopEvent = event(10, LocalDateTime.now().plusDays(2));
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(false);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));

        assertThrows(ForbiddenException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("itemDescription", "x")));
    }

    @Test
    @DisplayName("취소된 이벤트는 수정할 수 없다")
    void updateEvent_cancelled_rejected() {
        ShopEvent shopEvent = editableEvent(0);
        shopEvent.updateStatus(ShopEventStatus.CANCELLED);

        assertThrows(BadRequestException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("itemDescription", "x")));
    }

    @Test
    @DisplayName("방문 날짜가 오늘인 이벤트는 수정할 수 없다")
    void updateEvent_visitToday_rejected() {
        ShopEvent shopEvent = event(10, LocalDateTime.now().minusHours(2), 3, java.time.LocalDate.now());
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(true);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));

        assertThrows(BadRequestException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("itemDescription", "x")));
    }

    @Test
    @DisplayName("방문 날짜가 모집 마감일보다 앞이면 수정할 수 없다")
    void updateEvent_visitBeforeDeadline_rejected() {
        editableEvent(0);

        assertThrows(BadRequestException.class,
            () -> eventService.updateEvent(EVENT_ID, USER_ID, updateRequest("visitUntil", LocalDateTime.now().plusDays(1).toLocalDate())));
    }

    @Test
    @DisplayName("정산 보기: 이벤트별로 참가중 / 손님 취소 / 가게 취소 금액을 나눠서 보여준다")
    void settlement_sumsByStatus() {
        ShopEvent shopEvent = event(10, LocalDateTime.now().plusDays(2));
        Shop shop = mock(Shop.class);
        when(shop.isOwner(USER_ID)).thenReturn(true);
        when(shopRepository.findById(2L)).thenReturn(Optional.of(shop));
        when(shopEventRepository.getShopEventAll(2L, null)).thenReturn(List.of(shopEvent));
        List<Object[]> rows = List.of(
            new Object[]{EVENT_ID, EventParticipationStatus.JOINED, 5L},
            new Object[]{EVENT_ID, EventParticipationStatus.CANCELLED, 2L},
            new Object[]{EVENT_ID, EventParticipationStatus.REFUNDED, 1L});
        when(eventParticipationRepository.sumQuantityByShop(2L)).thenReturn(rows);

        var result = eventService.getSettlement(2L, USER_ID);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getJoinedQuantity());
        assertEquals(5 * FEE, result.get(0).getJoinedAmount());
        assertEquals(2 * FEE, result.get(0).getCancelledAmount());
        assertEquals(1 * FEE, result.get(0).getRefundedAmount());
    }

    @Test
    @DisplayName("정산 내역은 본인 가게만 볼 수 있고, 가게 주인이면 정산된 구매 건을 그대로 돌려준다")
    void settlementRecords_ownerOnly() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(USER_ID)).thenReturn(true);
        when(shop.isOwner(99L)).thenReturn(false);
        when(shopRepository.findById(2L)).thenReturn(Optional.of(shop));
        var record = new com.goodsbridge.event.dto.EventSettlementRecordResponse(LocalDateTime.now(), 10000, EVENT_ID, "이벤트", "닉네임", 2);
        when(eventParticipationRepository.findSettlementRecords(2L)).thenReturn(List.of(record));

        assertEquals(1, eventService.getSettlementRecords(2L, USER_ID).size());
        assertThrows(ForbiddenException.class, () -> eventService.getSettlementRecords(2L, 99L));
    }

    @Test
    @DisplayName("정산 보기는 본인 가게만 볼 수 있다")
    void settlement_notOwner_forbidden() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(USER_ID)).thenReturn(false);
        when(shopRepository.findById(2L)).thenReturn(Optional.of(shop));

        assertThrows(ForbiddenException.class, () -> eventService.getSettlement(2L, USER_ID));
    }

    // ---------- 방문 마감일 (이 날이 지나야 종료)

    @Test
    @DisplayName("방문 마감일을 안 정하면 모집 마감일 + 7일이다")
    void visitUntil_defaultsToDeadlinePlus7Days() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(2);
        ShopEvent shopEvent = event(5, deadline);

        assertEquals(deadline.toLocalDate().plusDays(7), shopEvent.getVisitUntil());
    }

    @Test
    @DisplayName("방문 마감일이 모집 마감일보다 앞이면 이벤트를 열 수 없다")
    void createRequest_visitBeforeDeadline_rejected() {
        ShopEventRequest request = createRequest();
        ReflectionTestUtils.setField(request, "visitUntil", LocalDateTime.now().plusDays(1).toLocalDate());

        assertThrows(BadRequestException.class, request::validate);
    }

    // ---------- 추가 구매 (같은 번호표에 구매 건을 더 쌓는다)

    private EventParticipation boughtBefore(ShopEvent shopEvent, String paymentId, int ticket, int quantity) {
        EventParticipation p = EventParticipation.builder()
            .shopEvent(shopEvent).user(activeUser()).paymentId(paymentId).ticketNumber(ticket).quantity(quantity)
            .build();
        return p;
    }

    @Test
    @DisplayName("이미 5개 산 사람이 3개를 더 사면 기존 번호표를 그대로 쓰고, 새 결제는 3개분만 검증한다")
    void join_additional_keepsTicketNumber() {
        ShopEvent shopEvent = event(100, LocalDateTime.now().plusDays(1), 10);
        shopEvent.increaseCurrentCount(5);
        joinable(shopEvent);
        EventParticipation before = boughtBefore(shopEvent, "pay-0", 3, 5);
        when(eventParticipationRepository.findJoinedByUserIdAndShopEventId(USER_ID, EVENT_ID))
            .thenReturn(List.of(before));
        when(paymentService.verify("pay-1", FEE * 3, PaymentPurpose.EVENT_FEE, USER_ID)).thenReturn("pay-1");
        EventParticipationRequest request = request("pay-1");
        ReflectionTestUtils.setField(request, "quantity", 3);

        EventParticipationResponse response = eventService.saveEventParticipation(EVENT_ID, USER_ID, request);

        assertEquals(3, response.getTicketNumber());
        assertEquals(3, response.getQuantity());
        assertEquals(8, shopEvent.getCurrentCount());
        verify(eventParticipationRepository, never()).findMaxTicketNumber(any());
        verify(paymentService, never()).cancelUnrecorded(anyString(), anyString());
    }

    @Test
    @DisplayName("이미 산 수량과 합쳐 1인 최대 수량을 넘으면 거절하고 결제를 자동 취소한다")
    void join_additional_overMaxPerUser_rejected() {
        ShopEvent shopEvent = event(100, LocalDateTime.now().plusDays(1), 10);
        shopEvent.increaseCurrentCount(5);
        joinable(shopEvent);
        EventParticipation before = boughtBefore(shopEvent, "pay-0", 3, 5);
        when(eventParticipationRepository.findJoinedByUserIdAndShopEventId(USER_ID, EVENT_ID))
            .thenReturn(List.of(before));
        EventParticipationRequest request = request("pay-1");
        ReflectionTestUtils.setField(request, "quantity", 6); // 5 + 6 = 11개

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request));

        verify(paymentService, never()).verify(anyString(), anyInt(), any(), anyLong());
        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
        assertEquals(5, shopEvent.getCurrentCount());
    }

    @Test
    @DisplayName("구매 건 하나만 취소하면 그 건만 환불하고, 다른 구매 건은 그대로 둔다")
    void cancelJoin_onePurchaseOnly() {
        ShopEvent shopEvent = event(100, LocalDateTime.now().plusDays(1), 10);
        shopEvent.increaseCurrentCount(8);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User me = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(me));
        EventParticipation extra = boughtBefore(shopEvent, "pay-1", 3, 3);
        when(eventParticipationRepository.findById(200L)).thenReturn(Optional.of(extra));

        eventService.cancelEventParticipation(EVENT_ID, USER_ID, 200L);

        verify(paymentService).refund(eq("pay-1"), eq(FEE * 3), anyString());
        verify(paymentService, never()).refund(eq("pay-0"), anyInt(), anyString());
        assertEquals(EventParticipationStatus.CANCELLED, extra.getStatus());
        assertEquals(5, shopEvent.getCurrentCount());
    }

    @Test
    @DisplayName("다른 사람의 구매 건은 취소할 수 없다")
    void cancelJoin_otherUsersPurchase_notFound() {
        ShopEvent shopEvent = event(100, LocalDateTime.now().plusDays(1), 10);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User me = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(me));
        User other = mock(User.class);
        when(other.getId()).thenReturn(99L);
        EventParticipation theirs = EventParticipation.builder()
            .shopEvent(shopEvent).user(other).paymentId("pay-x").ticketNumber(2).quantity(1).build();
        when(eventParticipationRepository.findById(300L)).thenReturn(Optional.of(theirs));

        assertThrows(com.goodsbridge._core.exception.NotFoundException.class,
            () -> eventService.cancelEventParticipation(EVENT_ID, USER_ID, 300L));

        verify(paymentService, never()).refund(anyString(), anyInt(), anyString());
    }

    // ---------- C, D. 참가 취소

    @Test
    @DisplayName("참가 취소 시 환불하고 인원을 줄이며, 정원이 차서 마감됐던 이벤트는 다시 모집중이 된다")
    void cancelJoin_refundsAndReopens() {
        ShopEvent shopEvent = event(1, LocalDateTime.now().plusDays(1));
        shopEvent.increaseCurrentCount(1);
        shopEvent.updateStatus(ShopEventStatus.CLOSED);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        EventParticipation joined = participation(shopEvent, "pay-1");
        when(eventParticipationRepository.findById(100L)).thenReturn(Optional.of(joined));

        eventService.cancelEventParticipation(EVENT_ID, USER_ID, 100L);

        verify(paymentService).refund(eq("pay-1"), eq(FEE), anyString());
        assertEquals(EventParticipationStatus.CANCELLED, joined.getStatus());
        assertEquals(0, shopEvent.getCurrentCount());
        assertEquals(ShopEventStatus.OPEN, shopEvent.getStatus());
    }

    @Test
    @DisplayName("마감시간이 지난 이벤트는 참가 취소(환불)할 수 없다")
    void cancelJoin_afterDeadline_rejected() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().minusMinutes(1));
        shopEvent.updateStatus(ShopEventStatus.CLOSED);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> eventService.cancelEventParticipation(EVENT_ID, USER_ID, 100L));

        verify(paymentService, never()).refund(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("가게가 취소한 이벤트는 참가 취소로 다시 환불받을 수 없다")
    void cancelJoin_cancelledEvent_rejected() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        shopEvent.updateStatus(ShopEventStatus.CANCELLED);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> eventService.cancelEventParticipation(EVENT_ID, USER_ID, 100L));

        verify(paymentService, never()).refund(anyString(), anyInt(), anyString());
    }

    // ---------- B. 가게 이벤트 취소

    @Test
    @DisplayName("가게가 이벤트를 취소하면 참가자 전원 참가비를 환불한다 (정원이 차서 마감된 이벤트도 마감시간 전이면 취소 가능)")
    void cancelShopEvent_refundsAllParticipants() {
        ShopEvent shopEvent = event(2, LocalDateTime.now().plusDays(1));
        shopEvent.updateStatus(ShopEventStatus.CLOSED);
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(true);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        EventParticipation p1 = participation(shopEvent, "pay-1");
        EventParticipation p2 = participation(shopEvent, "pay-2");
        when(eventParticipationRepository.findJoinedByShopEventId(EVENT_ID)).thenReturn(List.of(p1, p2));

        eventService.cancelShopEvent(EVENT_ID, USER_ID);

        verify(paymentService).refund(eq("pay-1"), eq(FEE), anyString());
        verify(paymentService).refund(eq("pay-2"), eq(FEE), anyString());
        assertEquals(EventParticipationStatus.REFUNDED, p1.getStatus());
        assertEquals(EventParticipationStatus.REFUNDED, p2.getStatus());
        assertEquals(ShopEventStatus.CANCELLED, shopEvent.getStatus());
    }

    @Test
    @DisplayName("마감시간이 지난 이벤트는 가게가 취소할 수 없다")
    void cancelShopEvent_afterDeadline_rejected() {
        ShopEvent shopEvent = event(2, LocalDateTime.now().minusMinutes(1));
        when(shopEvent.getShop().isOwner(USER_ID)).thenReturn(true);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));

        assertThrows(ForbiddenException.class, () -> eventService.cancelShopEvent(EVENT_ID, USER_ID));

        verify(paymentService, never()).refund(anyString(), anyInt(), anyString());
    }

    // ---------- 마감 정산

    @Test
    @DisplayName("마감시간이 지나면 이벤트를 마감하고 남은 참가자 참가비를 가게 정산(EVENT_FEE)에 기록한다")
    void autoClose_settlesJoinedParticipants() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().minusMinutes(1));
        when(shopEventRepository.findSettleTargets(any(LocalDateTime.class))).thenReturn(List.of(shopEvent));
        List<EventParticipation> joined = List.of(participation(shopEvent, "pay-1"), participation(shopEvent, "pay-2"));
        when(eventParticipationRepository.findJoinedByShopEventId(EVENT_ID)).thenReturn(joined);
        Payment payment1 = mock(Payment.class);
        Payment payment2 = mock(Payment.class);
        when(paymentRepository.findByPaymentId("pay-1")).thenReturn(Optional.of(payment1));
        when(paymentRepository.findByPaymentId("pay-2")).thenReturn(Optional.of(payment2));

        eventService.autoCloseShopEvents();

        verify(settlementHistoryService).calculate(shopEvent.getShop(), payment1, FEE, SettlementHistoryType.EVENT_FEE);
        verify(settlementHistoryService).calculate(shopEvent.getShop(), payment2, FEE, SettlementHistoryType.EVENT_FEE);
        assertEquals(ShopEventStatus.CLOSED, shopEvent.getStatus());
        assertTrue(shopEvent.isSettled());
    }
}
