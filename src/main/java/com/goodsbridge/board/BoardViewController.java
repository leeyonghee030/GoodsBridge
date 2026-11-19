package com.goodsbridge.board;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.shop.ShopRepository;
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
    private final ShopRepository shopRepository;

    @GetMapping("/test/board")
    public String index(
        @RequestParam(defaultValue = "COMMUNITY") BoardType boardType,
        @RequestParam(required = false) String keyword,
        Model model
    ) {
        // 상품 리뷰와 상품 문의는 게시판 화면에 보이지 않는다 (상품·이벤트·경매 상세 화면과 마이페이지에서 본다)
        if (boardType == BoardType.PRODUCT_REVIEW || boardType == BoardType.PRODUCT_INQUIRY) {
            return "redirect:/test/board";
        }

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
        // 고객센터는 작성자와 관리자만 볼 수 있다 (안내 표시용). 비밀글 선택지는 없다 (고객센터는 항상 비공개)
        model.addAttribute("csBoard", boardType == BoardType.CUSTOMER_SERVICE);
        model.addAttribute("userId", userId);

        return "board/index";
    }

    @GetMapping("/test/board/{id}")
    public String detail(@PathVariable Long id, @RequestParam(required = false) String from, Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        BoardResponseDto board = boardService.getBoard(id, userId);
        model.addAttribute("board", board);
        // 들어온 화면(from)이 있으면 "목록으로"와 삭제 후 이동이 그 화면으로 돌아간다 (구매 내역, 내 문의, 문의 관리 등)
        model.addAttribute("backUrl", isLocalPath(from) ? from : backUrl(board));
        model.addAttribute("userId", userId);
        // 상품 리뷰에는 그 상품의 판매자만 댓글을 단다 (나머지에게는 댓글 입력창 대신 안내를 보여준다)
        model.addAttribute("replyLocked", board.getBoardType() == BoardType.PRODUCT_REVIEW
            && !boardService.isItemSeller(board.getItemId(), userId));
        return "board/detail";
    }

    // 마이페이지 "내 문의": 내가 쓴 상품·이벤트·경매 문의
    @GetMapping("/my/inquiries")
    public String myInquiries(Model model) {
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        model.addAttribute("inquiryScope", "mine");
        return "board/my-inquiries";
    }

    // 판매자 "문의 관리": 내 상품·이벤트·경매에 온 문의 (가게 사장님이 아니면 내 정보로 보낸다)
    @GetMapping("/shop/inquiries")
    public String shopInquiries(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        if (!isShopOwner(userId)) {
            return "redirect:/update";
        }
        model.addAttribute("userId", userId);
        model.addAttribute("inquiryScope", "seller");
        return "board/shop-inquiries";
    }

    // 판매자 "리뷰 관리": 내 상품의 리뷰를 비공개로 바꾸거나 삭제
    @GetMapping("/shop/reviews")
    public String shopReviews(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        if (!isShopOwner(userId)) {
            return "redirect:/update";
        }
        model.addAttribute("userId", userId);
        return "board/shop-reviews";
    }

    private boolean isShopOwner(Long userId) {
        return userId != null && shopRepository.existsByOwnerUserIdAndDeletedFalse(userId);
    }

    // 화면에서 고를 수 있는 게시판 유형 (LEGACY 는 기존 글 보존용이라 제외)
    // 상품 리뷰는 게시판이 아니라 각 상품 상세 화면에서만 보여주므로 탭에 없다.
    // 상품 문의는 상품·이벤트·경매 상세 화면과 마이페이지의 "내 문의"/"문의 관리"에서 다루므로 게시판 탭에 없다.
    private static final List<BoardType> TAB_TYPES = List.of(
        BoardType.COMMUNITY, BoardType.CUSTOMER_SERVICE);

    private List<Map<String, Object>> boardTabs(BoardType current) {
        return TAB_TYPES.stream()
            .map(type -> Map.<String, Object>of(
                "type", type.name(),
                "label", label(type),
                "active", type == current))
            .toList();
    }

    // 이 사이트 안의 경로만 돌아갈 곳으로 받는다 (외부 주소나 HTML 을 깨는 문자는 막는다)
    private static final java.util.regex.Pattern LOCAL_PATH = java.util.regex.Pattern.compile("^/(?!/)[A-Za-z0-9/_?=&#.%-]*$");

    private static boolean isLocalPath(String path) {
        return path != null && LOCAL_PATH.matcher(path).matches();
    }

    // 상세 화면의 "목록으로" 링크: 리뷰·이벤트/경매 문의는 글이 달린 화면으로, 나머지는 그 유형의 목록으로
    private String backUrl(BoardResponseDto board) {
        if (board.getBoardType() == BoardType.PRODUCT_REVIEW && board.getItemId() != null) {
            return "/test/item/" + board.getItemId() + "#review";
        }
        // 대상 정보 없이 쓰인 옛 상품 문의는 그 상품 화면으로
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY && board.getTargetType() == null && board.getItemId() != null) {
            return "/test/item/" + board.getItemId() + "#inquiry";
        }
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY && board.getTargetType() != null) {
            switch (board.getTargetType()) {
                case EVENT:
                    return "/test/event/" + board.getTargetId() + "#inquiry";
                case AUCTION:
                    return "/test/auction/" + board.getTargetId() + "#inquiry";
                case ITEM:
                    return "/test/item/" + board.getItemId() + "#inquiry";
            }
        }
        return "/test/board?boardType=" + board.getBoardType();
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
