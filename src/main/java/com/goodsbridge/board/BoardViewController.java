package com.goodsbridge.board;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class BoardViewController {

    private final BoardService boardService;

    @GetMapping("/test/board")
    public String index(
        @RequestParam(defaultValue = "COMMUNITY") BoardType boardType,
        @RequestParam(required = false) String keyword,
        Model model
    ) {
        Pageable pageable = PageRequest.of(
            0,
            10,
            Sort.by(Sort.Direction.DESC, "id")
        );

        Long userId = SecurityUtil.getCurrentUserIdOrNull();

        Page<BoardResponseDto> page =
            boardService.getBoards(boardType, keyword, pageable, userId);

        model.addAttribute("boards", page.getContent());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("boardType", boardType);
        model.addAttribute("boardTabs", boardTabs(boardType));
        model.addAttribute("boardTypeLabel", label(boardType));
        // 상품 문의·리뷰는 상품이 필수이고, 커뮤니티·고객센터는 상품을 연결할 수 없다 (Board 의 유형별 규칙)
        model.addAttribute("needsItem",
            boardType == BoardType.PRODUCT_INQUIRY || boardType == BoardType.PRODUCT_REVIEW);
        // 커뮤니티와 상품 리뷰는 항상 공개 글이라 비밀글 선택지를 보여주지 않는다
        model.addAttribute("canSecret",
            boardType != BoardType.COMMUNITY && boardType != BoardType.PRODUCT_REVIEW);
        model.addAttribute("userId", userId);

        return "board/index";
    }

    @GetMapping("/test/board/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        BoardResponseDto board = boardService.getBoard(id, userId);
        model.addAttribute("board", board);
        // 커뮤니티와 상품 리뷰는 항상 공개 글이라 수정 폼에 비밀글 선택지를 보여주지 않는다
        model.addAttribute("canSecret",
            board.getBoardType() != BoardType.COMMUNITY && board.getBoardType() != BoardType.PRODUCT_REVIEW);
        model.addAttribute("userId", userId);
        return "board/detail";
    }

    // 화면에서 고를 수 있는 게시판 유형 (LEGACY 는 기존 글 보존용이라 제외)
    private static final List<BoardType> TAB_TYPES = List.of(
        BoardType.COMMUNITY, BoardType.PRODUCT_REVIEW, BoardType.PRODUCT_INQUIRY, BoardType.CUSTOMER_SERVICE);

    private List<Map<String, Object>> boardTabs(BoardType current) {
        return TAB_TYPES.stream()
            .map(type -> Map.<String, Object>of(
                "type", type.name(),
                "label", label(type),
                "active", type == current))
            .toList();
    }

    private String label(BoardType type) {
        return switch (type) {
            case COMMUNITY -> "커뮤니티";
            case PRODUCT_REVIEW -> "상품 리뷰";
            case PRODUCT_INQUIRY -> "상품 문의";
            case CUSTOMER_SERVICE -> "고객센터";
            case LEGACY -> "기존 글";
        };
    }
}
