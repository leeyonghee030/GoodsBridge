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

@Controller
@RequiredArgsConstructor
public class BoardViewController {

    private final BoardService boardService;

    @GetMapping("/test/board")
    public String index(@RequestParam(required = false) String keyword, Model model) {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));
        Page<BoardResponseDto> page = boardService.getBoards(keyword, pageable);
        model.addAttribute("boards", page.getContent());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "board/index";
    }

    @GetMapping("/test/board/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        model.addAttribute("board", boardService.getBoard(id, userId));
        model.addAttribute("userId", userId);
        return "board/detail";
    }
}
