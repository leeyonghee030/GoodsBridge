package com.goodsbridge.chat;

import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 대화 로그(ChatLog)를 저장하고 읽고 지운다.
 *
 * <p>[기록 정책] 대화 기록은 편의 기능일 뿐이다. 환불·취소 같은 중요한 내역은 교환 내역(ExchangeRequest)에 따로 남으므로
 * 대화 기록은 오래 보관하지 않는다.
 * <ul>
 *   <li>화면에는 최근 {@value #HISTORY_LIMIT}건만 보여 준다.</li>
 *   <li>대화 기록은 중요한 정보가 아니므로 하루 단위로 정리한다. 매일 새벽에 어제까지의 대화를 지우고 오늘 대화만 남긴다
 *       ({@link ChatLogCleanupScheduler}).</li>
 *   <li>사용자가 "새 대화"로 직접 지울 수 있다.</li>
 *   <li>AI에게 답을 못 받은 질문은 저장하지 않는다 (질문·도구 기록·답변을 답을 받았을 때 한꺼번에 저장).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChatLogService {

    static final int HISTORY_LIMIT = 50;     // 화면에 보여 주는 최근 대화 수 (ChatLogRepository.findTop50...와 같아야 한다)

    private final ChatLogRepository chatLogRepository;
    private final UserRepository userRepository;

    /**
     * 한 번의 질문-답변을 저장한다: 질문(USER) → AI가 실행한 도구 기록(TOOL) → 답변(ASSISTANT) 순서.
     * 한 트랜잭션이라 중간에 실패하면 아무것도 남지 않고, 시간 순서(id 순서)가 보장된다.
     */
    @Transactional
    public void saveConversation(Long userId, String userMessage, List<String> toolNotes, String reply) {
        // getReferenceById: User를 조회하지 않고 id만 가진 껍데기로 연결한다 (FK만 필요하므로)
        User user = userRepository.getReferenceById(userId);
        List<ChatLog> logs = new ArrayList<>();
        logs.add(log(user, ChatRole.USER, userMessage));
        toolNotes.forEach(note -> logs.add(log(user, ChatRole.TOOL, note)));
        logs.add(log(user, ChatRole.ASSISTANT, reply));
        chatLogRepository.saveAll(logs);
    }

    // 대화 기록 화면용: 본인 대화 중 최근 50건을 오래된 것부터
    @Transactional(readOnly = true)
    public List<ChatLogResponse> findHistory(Long userId) {
        List<ChatLog> latestFirst = new ArrayList<>(chatLogRepository.findTop50ByUser_IdOrderByIdDesc(userId));
        Collections.reverse(latestFirst);   // 조회는 최신이 먼저라서 시간 순서로 뒤집는다
        return latestFirst.stream().map(ChatLogResponse::new).toList();
    }

    // AI에게 앞선 대화를 알려 줄 때 쓰는 최근 대화 (USER·ASSISTANT만, 오래된 것부터)
    @Transactional(readOnly = true)
    public List<ChatLog> findRecentConversation(Long userId) {
        List<ChatLog> latestFirst = new ArrayList<>(
            chatLogRepository.findTop10ByUser_IdAndRoleInOrderByIdDesc(userId, List.of(ChatRole.USER, ChatRole.ASSISTANT)));
        Collections.reverse(latestFirst);
        return latestFirst;
    }

    // 대화 초기화: 이 사용자의 대화를 모두 지운다. 지운 개수를 돌려준다.
    @Transactional
    public int deleteAll(Long userId) {
        return chatLogRepository.deleteAllByUserId(userId);
    }

    // 오늘 0시 이전에 만든 대화를 모든 사용자에서 지운다 (어제까지의 대화). 지운 개수를 돌려준다.
    @Transactional
    public int deleteExpired() {
        return chatLogRepository.deleteOlderThan(LocalDate.now().atStartOfDay());
    }

    private ChatLog log(User user, ChatRole role, String content) {
        return ChatLog.builder().user(user).role(role).content(content).build();
    }
}
