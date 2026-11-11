package com.goodsbridge.exchange;

import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.checkerframework.common.returnsreceiver.qual.This;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchangeRequest_tb")
@NoArgsConstructor
public class ExchangeRequest {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchangeItemAId", nullable = false)
    private ExchangeItem exchangeItemA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemBId", nullable = false)
    private Item itemB;

    private String message; // B가 A에게 보내는 짧은 제안 메시지

    private String paymentIdA; // B 보증금 결제 식별자 (환불 시 사용)
    @Column(nullable = false)
    private String paymentIdB; // B 보증금 결제 식별자 (환불 시 사용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopId", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeRequestStatus status;

    //보증금 금액
    private Integer depositAmountA;
    @Column(nullable = false)
    private Integer depositAmountB;

    //교환 당일 가게에 실제로 나타났는지 체크
    @Column(nullable = false)
    private Boolean userAShowed;
    @Column(nullable = false)
    private Boolean userBShowed;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // 만날 시간: 한쪽이 제안하고 상대가 확정하면 고정된다 (기존 행은 null이므로 Boolean)
    private LocalDateTime meetingAt;
    private Long meetingProposerId;
    private Boolean meetingConfirmed;

    // 가게가 두 사람 모두 방문 체크한 시각. 이 시각부터 정산 유예 시간(5분)이 지나면 완료 처리/정산한다. 그 전에는 체크를 되돌릴 수 있다.
    private LocalDateTime bothCheckedAt;

    @Builder
    public ExchangeRequest(ExchangeItem exchangeItemA, Item itemB, String message, String paymentIdB, Shop shop, Integer depositAmountB) {

        this.exchangeItemA = exchangeItemA;
        this.itemB = itemB;
        this.message = message;
        this.paymentIdB = paymentIdB;
        this.shop = shop;
        this.status = ExchangeRequestStatus.PENDING;
        this.depositAmountB = depositAmountB < 2000 ? 2000 : depositAmountB;
        this.userAShowed = false;
        this.userBShowed = false;
        this.deadline = LocalDateTime.now().plusDays(3);
    }

    //편의메서드

    // 게시글 주인(A)인지 확인
    public boolean isOwner(Long userId) {
        return this.exchangeItemA.getUser().getId().equals(userId);
    }

    // 제안자(B)인지 확인
    public boolean isProposer(Long userId) {
        return this.itemB.getSellerUserId().equals(userId);
    }

    // 교환 수락시 depositAmountA 보증금 업데이트
    public void updateDepositAmountA(Integer depositAmountA) {
        this.depositAmountA = depositAmountA < 2000 ? 2000 : depositAmountA;
    }

    // 수락(accept) 시 deadline을 "가게 방문 기한"(+5일)으로 덮어씀
    public void updateDeadlineForVisit() {
        this.deadline = LocalDateTime.now().plusDays(5);
    }

    // 가게 체크인 시 출석여부 기록
    public void checkInA(boolean userAShowed) {
        this.userAShowed = userAShowed;
    }

    public void checkInB(boolean userBShowed) {
        this.userBShowed = userBShowed;
    }

    // 교환 당사자(게시글 주인 A 또는 제안자 B)인지
    public boolean isParticipant(Long userId) {
        return isOwner(userId) || isProposer(userId);
    }

    public boolean isMeetingConfirmed() {
        return Boolean.TRUE.equals(this.meetingConfirmed);
    }

    // 만날 시간 제안 (확정 전에는 누구나 다시 제안해서 덮어쓸 수 있다)
    public void proposeMeeting(Long proposerId, LocalDateTime meetingAt) {
        this.meetingAt = meetingAt;
        this.meetingProposerId = proposerId;
        this.meetingConfirmed = false;
    }

    public void confirmMeeting() {
        this.meetingConfirmed = true;
    }

    // 상태 변경
    public void updateStatus(ExchangeRequestStatus status) {
        this.status = status;
    }

    // 데드라인 지났는지
    public boolean isDeadlinePassed() {
        return LocalDateTime.now().isAfter(this.deadline);
    }

    // 상태가 대기중인지?
    public boolean isPending() {
        return this.status == ExchangeRequestStatus.PENDING;
    }

    // 상태가 확정됨인지?
    public boolean isConfirmed() {
        return this.status == ExchangeRequestStatus.CONFIRMED;
    }

    // 상태가 끝났는지?
    public boolean isDone() {
        return this.status == ExchangeRequestStatus.COMPLETED
            || this.status == ExchangeRequestStatus.EXPIRED_PARTIAL
            || this.status == ExchangeRequestStatus.EXPIRED_BOTH;
    }

    //스케줄러가 데드라인 지난 뒤, a,b가 왔었는지 보고 최종 상태값 바꾸는 메서드
    public void isUserShowed() {
        if (userAShowed && userBShowed) {
            this.status = ExchangeRequestStatus.COMPLETED;
        } else if (userAShowed || userBShowed) {
            this.status = ExchangeRequestStatus.EXPIRED_PARTIAL;
        } else {
            this.status = ExchangeRequestStatus.EXPIRED_BOTH;
        }
    }

    // 둘 다 체크되면 유예 시작 시각을 기록하고, 하나라도 해제되면 지운다
    public void refreshBothChecked() {
        if (isBothShowed()) {
            if (this.bothCheckedAt == null) this.bothCheckedAt = LocalDateTime.now();
        } else {
            this.bothCheckedAt = null;
        }
    }

    // 체크인 시점에 둘 다 왔는지 (데드라인 기다릴 필요 없이 바로 완료 처리할지 판단용)
    public boolean isBothShowed() {
        return userAShowed && userBShowed;
    }

    public void updatePaymentIdA(String paymentIdA){
        this.paymentIdA = paymentIdA;
    }

}
