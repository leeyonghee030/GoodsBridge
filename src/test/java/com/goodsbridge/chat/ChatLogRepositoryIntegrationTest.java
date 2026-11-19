package com.goodsbridge.chat;

import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 삭제 쿼리와 "최근 50건" 조회를 실제 DB(H2)로 확인한다. 단위 테스트의 목은 JPQL이 맞는지 알려 주지 않는다.
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatLogRepositoryIntegrationTest {

    @Autowired
    private ChatLogRepository chatLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.builder().email("chat-me@test.com").password("pw").nickname("나")
            .loginType(LoginType.EMAIL).build());
        other = userRepository.save(User.builder().email("chat-other@test.com").password("pw").nickname("남")
            .loginType(LoginType.EMAIL).build());
    }

    private void saveLogs(User user, int count) {
        List<ChatLog> logs = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            logs.add(ChatLog.builder().user(user).role(ChatRole.USER).content("메시지 " + i).build());
        }
        chatLogRepository.saveAll(logs);
        entityManager.flush();
        entityManager.clear();   // 이후 조회가 영속성 컨텍스트가 아니라 DB를 보게 한다
    }

    @Test
    void findTop50_returnsOnlyTheLatestFiftyOfThatUserNewestFirst() {
        saveLogs(me, 55);
        saveLogs(other, 3);

        List<ChatLog> latest = chatLogRepository.findTop50ByUser_IdOrderByIdDesc(me.getId());

        assertThat(latest).hasSize(50);
        assertThat(latest.get(0).getContent()).isEqualTo("메시지 55");      // 가장 최근
        assertThat(latest.get(49).getContent()).isEqualTo("메시지 6");      // 가장 오래된 5건(1~5)은 빠진다
    }

    @Test
    void deleteAllByUserId_deletesOnlyThatUsersLogs() {
        saveLogs(me, 5);
        saveLogs(other, 3);

        int deleted = chatLogRepository.deleteAllByUserId(me.getId());
        entityManager.clear();

        assertThat(deleted).isEqualTo(5);
        assertThat(chatLogRepository.findTop50ByUser_IdOrderByIdDesc(me.getId())).isEmpty();
        assertThat(chatLogRepository.findTop50ByUser_IdOrderByIdDesc(other.getId())).hasSize(3);   // 남의 대화는 그대로
    }

    @Test
    void deleteOlderThan_deletesOnlyLogsCreatedBeforeTheCutoff() {
        saveLogs(me, 4);
        saveLogs(other, 2);

        // 방금 만든 기록은 "내일"보다 오래된 것이니 지워지고, "어제"보다는 새것이니 남는다
        assertThat(chatLogRepository.deleteOlderThan(LocalDateTime.now().minusDays(1))).isZero();
        assertThat(chatLogRepository.deleteOlderThan(LocalDateTime.now().plusDays(1))).isEqualTo(6);
        entityManager.clear();
        assertThat(chatLogRepository.findTop50ByUser_IdOrderByIdDesc(me.getId())).isEmpty();
        assertThat(chatLogRepository.findTop50ByUser_IdOrderByIdDesc(other.getId())).isEmpty();
    }

    @Test
    void saveAll_keepsQuestionToolRecordAndReplyInTimeOrder() {
        // 한 번에 저장한 질문 → 도구 기록 → 답변이 id(시간) 순서를 지킨다
        chatLogRepository.saveAll(List.of(
            ChatLog.builder().user(me).role(ChatRole.USER).content("질문").build(),
            ChatLog.builder().user(me).role(ChatRole.TOOL).content("도구").build(),
            ChatLog.builder().user(me).role(ChatRole.ASSISTANT).content("답").build()));
        entityManager.flush();
        entityManager.clear();

        List<ChatLog> latestFirst = chatLogRepository.findTop50ByUser_IdOrderByIdDesc(me.getId());

        assertThat(latestFirst).extracting(ChatLog::getRole)
            .containsExactly(ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.USER);
    }
}
