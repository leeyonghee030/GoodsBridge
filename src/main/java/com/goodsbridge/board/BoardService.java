
package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final ReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final ItemRepository itemRepository;

    // 게시글 작성
    @Transactional
    public Long createBoard(BoardRequestDto requestDto, Long loginUserId) {

        if (loginUserId == null) {
            throw new ForbiddenException("로그인이 필요한 서비스입니다.");
        }

        // 기존 게시글 유형은 신규 작성 불가
        if (requestDto.getBoardType() == BoardType.LEGACY) {
            throw new BadRequestException(
                "기존 게시글 유형은 새 게시글 작성에 사용할 수 없습니다."
            );
        }

        // 상품 리뷰 작성 권한 검증
        if (requestDto.getBoardType() == BoardType.PRODUCT_REVIEW) {

            if (requestDto.getItemId() == null) {
                throw new BadRequestException("상품 리뷰에는 상품 ID가 필요합니다.");
            }

            // 상품 존재 여부 확인
            itemRepository.findById(requestDto.getItemId())
                .orElseThrow(() ->
                    new NotFoundException("상품을 찾을 수 없습니다.")
                );

            // 구매 이력 확인
            boolean hasPurchased = purchaseRepository.existsByUserIdAndItemId(
                loginUserId,
                requestDto.getItemId()
            );

            if (!hasPurchased) {
                throw new ForbiddenException(
                    "해당 상품을 구매한 사용자만 리뷰를 작성할 수 있습니다."
                );
            }
        }

        // 상품 문의 작성 시 상품 정보 검증
        if (requestDto.getBoardType() == BoardType.PRODUCT_INQUIRY) {

            if (requestDto.getItemId() == null) {
                throw new BadRequestException("상품 문의에는 상품 ID가 필요합니다.");
            }

            itemRepository.findById(requestDto.getItemId())
                .orElseThrow(() ->
                    new NotFoundException("상품을 찾을 수 없습니다.")
                );
        }

        Board board = Board.builder()
            .title(requestDto.getTitle())
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .itemId(requestDto.getItemId())
            .boardType(requestDto.getBoardType())
            .isSecret(requestDto.getIsSecret())
            .build();

        return boardRepository.save(board).getId();
    }

    // 게시글 상세 조회
    @Transactional(readOnly = true)
    public BoardResponseDto getBoard(Long id, Long loginUserId) {

        Board board = boardRepository.findByIdWithReplies(id)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다.")
            );

        // 기존 게시글 유형은 일반 상세 조회에서 제외
        if (board.getBoardType() == BoardType.LEGACY) {
            throw new NotFoundException("게시글을 찾을 수 없습니다.");
        }

        // 상품 문의
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY) {

            boolean isAuthor = Objects.equals(
                board.getAuthorId(),
                loginUserId
            );

            boolean isSeller = itemRepository.findById(board.getItemId())
                .map(item -> Objects.equals(
                    item.getSellerUserId(),
                    loginUserId
                ))
                .orElse(false);

            if (!isAuthor && !isSeller) {
                throw new ForbiddenException(
                    "상품 문의는 작성자 또는 해당 상품의 판매자만 조회할 수 있습니다."
                );
            }

            // 고객센터 문의
        } else if (board.getBoardType() == BoardType.CUSTOMER_SERVICE) {

            boolean isAuthor = Objects.equals(
                board.getAuthorId(),
                loginUserId
            );

            if (!isAuthor && !isAdmin()) {
                throw new ForbiddenException(
                    "고객센터 문의는 작성자 또는 관리자만 조회할 수 있습니다."
                );
            }
        }

        return BoardResponseDto.from(
            board,
            collectNicknames(List.of(board))
        );
    }

    // 댓글 작성

    @Transactional
    public Long createReply(
        Long boardId,
        ReplyRequestDto requestDto,
        Long loginUserId
    ) {
        // 로그인 여부 확인
        if (loginUserId == null) {
            throw new ForbiddenException(
                "댓글을 작성하려면 로그인이 필요합니다."
            );
        }

        Board board = boardRepository.findById(boardId)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다.")
            );

        // 기존 게시글에는 댓글 작성 불가
        if (board.getBoardType() == BoardType.LEGACY) {
            throw new NotFoundException("게시글을 찾을 수 없습니다.");
        }

        // 게시글 작성자 여부 확인
        boolean isAuthor = Objects.equals(
            board.getAuthorId(),
            loginUserId
        );

        // 상품 문의 댓글 권한 검증
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY) {

            boolean isSeller = itemRepository.findById(board.getItemId())
                .map(item -> Objects.equals(
                    item.getSellerUserId(),
                    loginUserId
                ))
                .orElse(false);

            if (!isAuthor && !isSeller) {
                throw new ForbiddenException(
                    "상품 문의 댓글은 작성자 또는 해당 상품의 판매자만 작성할 수 있습니다."
                );
            }

            // 고객센터 댓글 권한 검증
        } else if (board.getBoardType() == BoardType.CUSTOMER_SERVICE) {

            if (!isAuthor && !isAdmin()) {
                throw new ForbiddenException(
                    "고객센터 문의 댓글은 작성자 또는 관리자만 작성할 수 있습니다."
                );
            }

            // 그 외 비밀글은 작성자 본인만 댓글 작성 가능
        } else if (Boolean.TRUE.equals(board.getIsSecret())) {

            if (!isAuthor) {
                throw new ForbiddenException(
                    "비밀글에 댓글을 작성할 권한이 없습니다."
                );
            }
        }

        // 댓글 생성
        Reply reply = Reply.builder()
            .board(board)
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .build();

        // 양방향 연관관계 설정
        board.addReply(reply);

        Long replyId = replyRepository.save(reply).getId();

        // 게시판 유형과 댓글 작성자에 따라 게시글 상태 변경
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY
            || board.getBoardType() == BoardType.CUSTOMER_SERVICE) {

            if (isAuthor) {
                // 작성자가 추가 질문을 작성하면 답변 대기
                board.pending();
            } else {
                // 판매자 또는 관리자가 답변하면 답변 완료
                board.complete();
            }
        }

        return replyId;
    }


    // 댓글 삭제

    @Transactional
    public void deleteReply(Long replyId, Long loginUserId) {

        Reply reply = replyRepository.findById(replyId)
            .orElseThrow(() ->
                new NotFoundException("댓글을 찾을 수 없습니다.")
            );

        Board board = reply.getBoard();

        // 기존 게시글의 댓글은 삭제 불가
        if (board.getBoardType() == BoardType.LEGACY) {
            throw new NotFoundException("댓글을 찾을 수 없습니다.");
        }

        // 댓글 작성자 본인만 삭제 가능
        if (!Objects.equals(reply.getAuthorId(), loginUserId)) {
            throw new ForbiddenException("댓글을 삭제할 권한이 없습니다.");
        }

        // 삭제 전 댓글 개수 확인
        long replyCount = replyRepository.countByBoardId(board.getId());

        // DB에서 댓글 직접 삭제
        int deletedCount = replyRepository.deleteByIdInBulk(replyId);

        if (deletedCount == 0) {
            throw new NotFoundException("댓글을 찾을 수 없습니다.");
        }

        // 마지막 댓글이 삭제되면 답변 대기 상태로 변경
        if (replyCount == 1) {
            board.pending();
        }
    }

    // 게시글 수정
    @Transactional
    public void updateBoard(
        Long id,
        BoardUpdateRequestDto requestDto,
        Long loginUserId
    ) {
        Board board = boardRepository.findById(id)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다.")
            );

        // 기존 게시글 유형은 수정 불가
        if (board.getBoardType() == BoardType.LEGACY) {
            throw new NotFoundException("게시글을 찾을 수 없습니다.");
        }

        // 작성자 권한 확인
        if (!Objects.equals(board.getAuthorId(), loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        board.update(
            requestDto.getTitle(),
            requestDto.getContent(),
            requestDto.getIsSecret()
        );
    }

    // 게시글 삭제
    @Transactional
    public void deleteBoard(Long id, Long loginUserId) {

        Board board = boardRepository.findById(id)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다.")
            );

        // 기존 게시글은 삭제 불가
        if (board.getBoardType() == BoardType.LEGACY) {
            throw new NotFoundException("게시글을 찾을 수 없습니다.");
        }

        if (!Objects.equals(board.getAuthorId(), loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        boardRepository.delete(board);
    }

    // 공개 게시글 목록 조회 및 검색
    @Transactional(readOnly = true)
    public Page<BoardResponseDto> getBoards(
        BoardType boardType,
        String keyword,
        Pageable pageable,
        Long loginUserId
    ) {
        // 현재 게시판 화면에서 지원하는 유형만 허용
        if (boardType == null || boardType == BoardType.LEGACY) {
            throw new BadRequestException("지원하지 않는 게시판 유형입니다.");
        }

        String searchKeyword = keyword == null
            ? ""
            : keyword.trim();

        // 선택한 게시판 유형의 게시글만 조회
        Page<Board> boardPage;

        if (searchKeyword.isBlank()) {
            boardPage = boardRepository.findByBoardType(
                boardType,
                pageable
            );
        } else {
            boardPage = boardRepository.findByKeyword(
                searchKeyword,
                List.of(boardType),
                pageable
            );
        }

        List<Board> boards = boardPage.getContent();

        // 게시글 작성자 닉네임 조회
        Map<Long, String> nicknames = collectBoardNicknames(boards);

        // 상품 문의 게시글의 상품 판매자 정보를 한 번에 조회
        // 게시글마다 itemRepository를 호출하는 N+1 문제를 방지한다.
        Map<Long, Long> itemSellerIds = new HashMap<>();

        if (boardType == BoardType.PRODUCT_INQUIRY) {

            List<Long> itemIds = boards.stream()
                .map(Board::getItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

            itemRepository.findAllById(itemIds)
                .forEach(item -> itemSellerIds.put(
                    item.getId(),
                    item.getSellerUserId()
                ));
        }

        boolean admin = isAdmin();

        return boardPage.map(board -> {

            boolean canRead = canReadBoard(
                board,
                loginUserId,
                admin,
                itemSellerIds
            );

            return BoardResponseDto.fromForList(
                board,
                nicknames,
                canRead
            );
        });
    }

    // 게시글과 댓글 작성자의 닉네임 조회
    private Map<Long, String> collectNicknames(List<Board> boards) {

        Set<Long> authorIds = new HashSet<>();

        for (Board board : boards) {
            authorIds.add(board.getAuthorId());

            for (Reply reply : board.getReplies()) {
                authorIds.add(reply.getAuthorId());
            }
        }

        if (authorIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(authorIds).stream()
            .collect(Collectors.toMap(
                User::getId,
                User::getNickname
            ));
    }

    // 게시글 작성자의 닉네임만 조회
    private Map<Long, String> collectBoardNicknames(
        List<Board> boards
    ) {
        Set<Long> authorIds = boards.stream()
            .map(Board::getAuthorId)
            .collect(Collectors.toSet());

        if (authorIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(authorIds).stream()
            .collect(Collectors.toMap(
                User::getId,
                User::getNickname
            ));
    }

    // 현재 로그인 사용자가 관리자인지 확인
    private boolean isAdmin() {

        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
            !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }

    // 게시글 목록 조회 권한 확인
    private boolean canReadBoard(
        Board board,
        Long loginUserId,
        boolean admin,
        Map<Long, Long> itemSellerIds
    ) {
        // 커뮤니티와 상품 리뷰는 누구나 조회 가능
        if (board.getBoardType() == BoardType.COMMUNITY
            || board.getBoardType() == BoardType.PRODUCT_REVIEW) {
            return true;
        }

        // 로그인하지 않은 사용자는 비공개 게시글 조회 불가
        if (loginUserId == null) {
            return false;
        }

        // 상품 문의: 작성자 또는 해당 상품 판매자
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY) {

            boolean isAuthor = Objects.equals(
                board.getAuthorId(),
                loginUserId
            );

            Long sellerUserId = board.getItemId() == null
                ? null
                : itemSellerIds.get(board.getItemId());

            boolean isSeller = Objects.equals(
                sellerUserId,
                loginUserId
            );

            return isAuthor || isSeller;
        }

        // 고객센터: 작성자 또는 관리자
        if (board.getBoardType() == BoardType.CUSTOMER_SERVICE) {

            boolean isAuthor = Objects.equals(
                board.getAuthorId(),
                loginUserId
            );

            return isAuthor || admin;
        }

        return false;
    }
}
