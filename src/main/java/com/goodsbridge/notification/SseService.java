
package com.goodsbridge.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseService {

    // 사용자 ID별 SSE 연결 목록
    private final Map<Long, List<SseEmitter>> emitterMap
        = new ConcurrentHashMap<>();

    // 연결 유지 시간 (10분)
    private static final long TIMEOUT = 10 * 60 * 1000L;

    // SSE 연결
    public SseEmitter connect(Long userId) {

        SseEmitter emitter = new SseEmitter(TIMEOUT);

        // 해당 사용자의 연결 목록을 가져오거나 새로 생성
        List<SseEmitter> emitters = emitterMap.computeIfAbsent(
            userId,
            key -> new CopyOnWriteArrayList<>()
        );

        // 연결 목록에 emitter 추가
        emitters.add(emitter);

        // 연결 종료 시 목록에서 제거
        emitter.onCompletion(() -> removeEmitter(userId, emitter));

        // 연결 시간 초과 시 목록에서 제거하고 종료
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            emitter.complete();
        });

        // 네트워크 오류 등으로 연결이 끊기면 제거
        emitter.onError(e -> removeEmitter(userId, emitter));

        // 최초 연결 확인 이벤트 전송
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connect")
                    .data("연결 성공")
            );
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        log.info("SSE 연결 추가 - 사용자 ID: {}, 현재 연결 수: {}",
            userId, emitters.size());

        return emitter;
    }

    // 특정 사용자의 모든 SSE 연결에 알림 전송
    public void send(
        Long userId,
        NotificationResponse.NotificationDto notification
    ) {

        List<SseEmitter> emitters = emitterMap.get(userId);

        // 해당 사용자가 연결되어 있지 않으면 전송하지 않음
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("notification")
                        .data(notification)
                );
            } catch (IOException e) {
                // 전송에 실패한 연결 제거
                removeEmitter(userId, emitter);
            }
        }
    }

    // 연결 목록에서 특정 emitter 제거
    private void removeEmitter(Long userId, SseEmitter emitter) {

        List<SseEmitter> emitters = emitterMap.get(userId);

        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);

        // 해당 사용자의 연결이 모두 종료되었다면 Map에서도 제거
        if (emitters.isEmpty()) {
            emitterMap.remove(userId, emitters);
        }
    }
}
