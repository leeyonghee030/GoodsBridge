package com.goodsbridge.event;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.event.dto.EventParticipationRequest;
import com.goodsbridge.event.dto.EventParticipationResponse;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.Status;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @InjectMocks EventService eventService;

    private static final Long EVENT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final int FEE = 5_000;

    @BeforeEach
    void setUp() {
        // 실제 트랜잭션 없이 콜백만 바로 실행
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    private ShopEvent event(int capacity, LocalDateTime deadline) {
        ShopEvent shopEvent = ShopEvent.builder()
            .shop(mock(Shop.class)).item(mock(Item.class))
            .capacity(capacity).feeAmount(FEE).deadline(deadline)
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
        shopEvent.increaseCurrentCount();
        joinable(shopEvent);

        assertThrows(BadRequestException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

        verify(paymentService).cancelUnrecorded(eq("pay-1"), anyString());
    }

    @Test
    @DisplayName("중복 참가로 거절돼도 결제를 자동 취소한다")
    void join_duplicate_cancelsPayment() {
        joinable(event(5, LocalDateTime.now().plusDays(1)));
        when(eventParticipationRepository.existsByShopEvent_IdAndUser_Id(USER_ID, EVENT_ID)).thenReturn(true);

        assertThrows(ForbiddenException.class,
            () -> eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1")));

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

    // ---------- C. 번호표

    @Test
    @DisplayName("번호표는 인원수가 아니라 지금까지 발급한 최대 번호 + 1이다 (취소 후에도 번호가 겹치지 않음)")
    void join_ticketNumber_isMaxPlusOne() {
        ShopEvent shopEvent = event(5, LocalDateTime.now().plusDays(1));
        shopEvent.increaseCurrentCount(); // 현재 1명 (앞서 3명이 참가했고 2명이 취소한 상황)
        joinable(shopEvent);
        when(paymentService.verify("pay-1", FEE, PaymentPurpose.EVENT_FEE, USER_ID)).thenReturn("pay-1");
        when(eventParticipationRepository.findMaxTicketNumber(EVENT_ID)).thenReturn(3);

        EventParticipationResponse response = eventService.saveEventParticipation(EVENT_ID, USER_ID, request("pay-1"));

        assertEquals(4, response.getTicketNumber());
        assertEquals(2, shopEvent.getCurrentCount());
        verify(paymentService, never()).cancelUnrecorded(anyString(), anyString());
    }

    // ---------- C, D. 참가 취소

    @Test
    @DisplayName("참가 취소 시 환불하고 인원을 줄이며, 정원이 차서 마감됐던 이벤트는 다시 모집중이 된다")
    void cancelJoin_refundsAndReopens() {
        ShopEvent shopEvent = event(1, LocalDateTime.now().plusDays(1));
        shopEvent.increaseCurrentCount();
        shopEvent.updateStatus(ShopEventStatus.CLOSED);
        when(shopEventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(shopEvent));
        User user = activeUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(eventParticipationRepository.existsByShopEvent_IdAndUser_Id(USER_ID, EVENT_ID)).thenReturn(true);
        EventParticipation joined = participation(shopEvent, "pay-1");
        when(eventParticipationRepository.findByUserIdAndShopEventId(USER_ID, EVENT_ID)).thenReturn(Optional.of(joined));

        eventService.cancelEventParticipation(EVENT_ID, USER_ID);

        verify(paymentService).refund(eq("pay-1"), eq(FEE), anyString());
        verify(eventParticipationRepository).cancelByUserIdAndShopEventId(USER_ID, EVENT_ID);
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

        assertThrows(BadRequestException.class, () -> eventService.cancelEventParticipation(EVENT_ID, USER_ID));

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

        assertThrows(BadRequestException.class, () -> eventService.cancelEventParticipation(EVENT_ID, USER_ID));

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
