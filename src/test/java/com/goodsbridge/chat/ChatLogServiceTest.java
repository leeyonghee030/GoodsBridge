package com.goodsbridge.chat;

import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatLogServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ChatLogRepository chatLogRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private ChatLogService service;

    private ChatLog log(ChatRole role, String content) {
        return ChatLog.builder().role(role).content(content).build();
    }

    @Test
    void saveConversation_savesQuestionThenToolRecordsThenReplyInOrderInOneBatch() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));

        service.saveConversation(USER_ID, "내 교환 보여줘", List.of("교환 신청 조회: 2건"), "2건이 있어요.");

        ArgumentCaptor<List<ChatLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatLogRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ChatLog::getRole)
            .containsExactly(ChatRole.USER, ChatRole.TOOL, ChatRole.ASSISTANT);
        assertThat(captor.getValue()).extracting(ChatLog::getContent)
            .containsExactly("내 교환 보여줘", "교환 신청 조회: 2건", "2건이 있어요.");
    }

    @Test
    void saveConversation_withoutToolRecordsSavesJustQuestionAndReply() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));

        service.saveConversation(USER_ID, "안녕", List.of(), "안녕하세요");

        ArgumentCaptor<List<ChatLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatLogRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ChatLog::getRole).containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
    }

    @Test
    void findHistory_returnsTheLatestLogsOldestFirst() {
        // 저장소는 최신이 먼저 오고, 화면에는 시간 순서(오래된 것부터)로 보여 준다
        when(chatLogRepository.findTop50ByUser_IdOrderByIdDesc(USER_ID)).thenReturn(List.of(
            log(ChatRole.ASSISTANT, "세 번째"), log(ChatRole.USER, "두 번째"), log(ChatRole.USER, "첫 번째")));

        List<ChatLogResponse> history = service.findHistory(USER_ID);

        assertThat(history).extracting(ChatLogResponse::getContent).containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    void historyLimitMatchesTheRepositoryQuery() {
        // 화면에 보여 주는 개수(50)는 저장소 메서드 이름(findTop50...)과 같아야 한다. 바꿀 때 둘을 같이 바꾼다.
        assertThat(ChatLogService.HISTORY_LIMIT).isEqualTo(50);
    }

    @Test
    void findRecentConversation_returnsOldestFirstForTheAiContext() {
        when(chatLogRepository.findTop10ByUser_IdAndRoleInOrderByIdDesc(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(log(ChatRole.ASSISTANT, "답"), log(ChatRole.USER, "질문")));

        assertThat(service.findRecentConversation(USER_ID)).extracting(ChatLog::getContent).containsExactly("질문", "답");
    }

    @Test
    void deleteAll_deletesThatUsersLogsAndReturnsTheCount() {
        when(chatLogRepository.deleteAllByUserId(USER_ID)).thenReturn(12);

        assertThat(service.deleteAll(USER_ID)).isEqualTo(12);
    }

    @Test
    void deleteExpired_deletesLogsOlderThanThirtyDays() {
        when(chatLogRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any())).thenReturn(3);

        int deleted = service.deleteExpired();

        assertThat(deleted).isEqualTo(3);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatLogRepository).deleteOlderThan(cutoff.capture());
        // 기준 시각은 지금부터 30일 전 (실행 시간 차이를 감안해 1분 오차를 둔다)
        assertThat(cutoff.getValue()).isBetween(
            LocalDateTime.now().minusDays(30).minusMinutes(1), LocalDateTime.now().minusDays(30).plusMinutes(1));
        assertThat(ChatLogService.RETENTION_DAYS).isEqualTo(30);
    }
}
