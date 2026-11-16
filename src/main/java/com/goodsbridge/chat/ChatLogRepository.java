package com.goodsbridge.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {

    // 이 사용자의 최근 대화 50건 (최신이 먼저). 화면에 보여 줄 기록용이고, 서비스가 시간 순서로 뒤집는다.
    List<ChatLog> findTop50ByUser_IdOrderByIdDesc(Long userId);

    // 이 사용자의 최근 대화 10건 (최신이 먼저). AI에게 앞선 대화를 알려 줄 때 쓴다.
    // roles로 USER·ASSISTANT만 가져와서, 도구 실행 기록(TOOL)은 AI 대화 문맥에서 뺀다.
    List<ChatLog> findTop10ByUser_IdAndRoleInOrderByIdDesc(Long userId, Collection<ChatRole> roles);

    // 대화 초기화: 이 사용자의 대화를 모두 지운다 (건건이 읽지 않고 한 번에 지운다)
    @Modifying
    @Query("delete from ChatLog c where c.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    // 보관 기간이 지난 대화를 지운다 (모든 사용자)
    @Modifying
    @Query("delete from ChatLog c where c.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
