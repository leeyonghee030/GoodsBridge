package com.goodsbridge.chat;

import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 대화 로그(ChatLog)를 저장하고 읽는다.
 * ChatService(질문·답변 저장)와 AI 도구(실행 기록 저장)가 같이 쓰도록 따로 뺐다.
 */
@Service
@RequiredArgsConstructor
public class ChatLogService {

    private final ChatLogRepository chatLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void save(Long userId, ChatRole role, String content) {
        chatLogRepository.save(ChatLog.builder()
            // getReferenceById: User를 조회하지 않고 id만 가진 껍데기로 연결한다 (FK만 필요하므로)
            .user(userRepository.getReferenceById(userId))
            .role(role)
            .content(content)
            .build());
    }

    // 대화 기록 화면용: 본인 대화만, 오래된 것부터
    @Transactional(readOnly = true)
    public List<ChatLogResponse> findHistory(Long userId) {
        return chatLogRepository.findByUser_IdOrderByIdAsc(userId).stream()
            .map(ChatLogResponse::new)
            .toList();
    }

    // AI에게 앞선 대화를 알려 줄 때 쓰는 최근 대화 (USER·ASSISTANT만, 오래된 것부터)
    @Transactional(readOnly = true)
    public List<ChatLog> findRecentConversation(Long userId) {
        List<ChatLog> latestFirst = new ArrayList<>(
            chatLogRepository.findTop10ByUser_IdAndRoleInOrderByIdDesc(userId, List.of(ChatRole.USER, ChatRole.ASSISTANT)));
        Collections.reverse(latestFirst);   // 조회는 최신이 먼저라서 시간 순서로 뒤집는다
        return latestFirst;
    }
}
