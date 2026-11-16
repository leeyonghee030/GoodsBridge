package com.goodsbridge.exchange;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.exchange.dto.ExchangeMessageResponse;
import com.goodsbridge.exchange.dto.ExchangeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 교환이 확정(수락)된 뒤 A, B가 채팅으로 만날 시간을 정하는 기능 (채팅 + 만날 시간 제안/확정)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeChatService {

    private static final int MAX_CONTENT_LENGTH = 500;

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final ExchangeMessageRepository exchangeMessageRepository;

    // WebSocket 구독 권한 확인용
    public boolean isParticipant(Long requestId, Long userId) {
        return exchangeRequestRepository.findById(requestId)
            .map(request -> request.isParticipant(userId))
            .orElse(false);
    }

    public List<ExchangeMessageResponse> getMessages(Long requestId, Long userId) {
        getParticipantRequest(requestId, userId);
        return exchangeMessageRepository.findByRequestId(requestId).stream()
            .map(ExchangeMessageResponse::from).toList();
    }

    @Transactional
    public ExchangeMessageResponse sendMessage(Long requestId, Long userId, String content) {
        ExchangeRequest request = getParticipantRequest(requestId, userId);
        if (!request.isConfirmed()) {
            throw new BadRequestException("교환이 확정된 뒤에 채팅할 수 있습니다.");
        }
        if (content == null || content.isBlank()) {
            throw new BadRequestException("메시지를 입력해주세요.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("메시지는 " + MAX_CONTENT_LENGTH + "자 이내여야 합니다.");
        }
        ExchangeMessage saved = exchangeMessageRepository.save(ExchangeMessage.builder()
            .exchangeRequest(request)
            .senderUserId(userId)
            .content(trimmed)
            .build());
        return ExchangeMessageResponse.from(saved);
    }

    // 만날 시간 제안 - 확정 전에는 누구든 다시 제안해서 덮어쓸 수 있다
    @Transactional
    public ExchangeRequestResponse proposeMeeting(Long requestId, Long userId, LocalDateTime meetingAt) {
        ExchangeRequest request = getParticipantRequest(requestId, userId);
        requireConfirmedAndNotFixed(request);
        if (!meetingAt.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("지금보다 이후 시간을 선택해주세요.");
        }
        if (meetingAt.isAfter(request.getDeadline())) {
            throw new BadRequestException("방문 기한(" + request.getDeadline().toLocalDate() + ") 안의 시간만 정할 수 있습니다.");
        }
        request.proposeMeeting(userId, meetingAt);
        return ExchangeRequestResponse.from(request);
    }

    // 상대가 제안한 시간을 확정 - 제안한 본인은 확정할 수 없다
    @Transactional
    public ExchangeRequestResponse confirmMeeting(Long requestId, Long userId) {
        ExchangeRequest request = getParticipantRequest(requestId, userId);
        requireConfirmedAndNotFixed(request);
        if (request.getMeetingAt() == null) {
            throw new BadRequestException("제안된 시간이 없습니다.");
        }
        if (userId.equals(request.getMeetingProposerId())) {
            throw new BadRequestException("상대방이 제안한 시간만 확정할 수 있습니다.");
        }
        request.confirmMeeting();
        return ExchangeRequestResponse.from(request);
    }

    private ExchangeRequest getParticipantRequest(Long requestId, Long userId) {
        ExchangeRequest request = exchangeRequestRepository.findById(requestId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 교환 신청입니다."));
        if (!request.isParticipant(userId)) {
            throw new ForbiddenException("교환 당사자만 이용할 수 있습니다.");
        }
        return request;
    }

    private void requireConfirmedAndNotFixed(ExchangeRequest request) {
        if (!request.isConfirmed()) {
            throw new BadRequestException("교환이 확정된 뒤에 만날 시간을 정할 수 있습니다.");
        }
        if (request.isMeetingConfirmed()) {
            throw new BadRequestException("이미 확정된 만날 시간은 변경할 수 없습니다.");
        }
    }
}
