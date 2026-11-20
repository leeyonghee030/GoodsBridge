package com.goodsbridge.demo;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.sql.DataSource;

// 시연용 화면 (상단 탭으로 오간다). app.demo.enabled=true 일 때만 존재한다.
@Controller
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoViewController {

    private final DataSource dataSource;

    // 선착순 이벤트 동시성 시연 화면 (입력 최대값은 서버 검증과 같은 값을 쓴다)
    @GetMapping("/demo/concurrency")
    public String concurrency(Model model) {
        model.addAttribute("tabEvent", true);
        model.addAttribute("maxCapacity", DemoConcurrencyService.MAX_CAPACITY);
        model.addAttribute("maxUsers", DemoConcurrencyService.MAX_USERS);
        addPoolSize(model);
        return "demo/concurrency";
    }

    // 경매 동시 입찰 시연 화면
    @GetMapping("/demo/auction")
    public String auction(Model model) {
        model.addAttribute("tabAuction", true);
        model.addAttribute("maxUsers", DemoConcurrencyService.MAX_USERS);
        addPoolSize(model);
        return "demo/auction";
    }

    // 교환 노쇼 정산 시연 화면
    @GetMapping("/demo/settlement")
    public String settlement(Model model) {
        model.addAttribute("tabSettlement", true);
        model.addAttribute("maxPrice", DemoSettlementService.MAX_PRICE);
        return "demo/settlement";
    }

    // 마감 스케줄러 빨리감기 시연 화면
    @GetMapping("/demo/timeline")
    public String timeline(Model model) {
        model.addAttribute("tabTimeline", true);
        return "demo/timeline";
    }

    // 락 없음 결과(DB 기록 ≈ 신청 인원 ÷ DB 연결 수)를 설명할 때 쓰는 DB 연결 풀 크기
    private void addPoolSize(Model model) {
        if (dataSource instanceof HikariDataSource hikari) {
            model.addAttribute("dbPoolSize", hikari.getMaximumPoolSize());
        }
    }
}
