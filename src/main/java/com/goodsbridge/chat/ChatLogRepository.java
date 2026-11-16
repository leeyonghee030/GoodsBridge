package com.goodsbridge.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    // 이 사용자의 대화 전체 (오래된 것부터). 대화 기록 화면용
    List<ChatLog> findByUser_IdOrderByIdAsc(Long userId);

    // 이 사용자의 최근 대화 10건 (최신이 먼저). AI에게 앞선 대화를 알려 줄 때 쓴다.
    // roles로 USER·ASSISTANT만 가져와서, 도구 실행 기록(TOOL)은 AI 대화 문맥에서 뺀다.
    List<ChatLog> findTop10ByUser_IdAndRoleInOrderByIdDesc(Long userId, Collection<ChatRole> roles);
}
