
package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.shop.*;
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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository shopSubscriptionRepository;

    @Transactional
    public Long createBoard(BoardRequestDto requestDto, Long loginUserId) {

        if (loginUserId == null) {
            throw new ForbiddenException("로그인이 필요한 서비스입니다.");
        }

        // 상품 리뷰 작성 권한 검증
        if (requestDto.getBoardType() == BoardType.PRODUCT_REVIEW) {

            if (requestDto.getItemId() == null) {
                throw new BadRequestException("상품 리뷰에는 상품 ID가 필요합니다.");
            }

            boolean hasPurchased = purchaseRepository.existsByUserIdAndItemId(
                loginUserId,
                requestDto.getItemId()
            );

            if (!hasPurchased) {
                throw new ForbiddenException("해당 상품을 구매한 사용자만 리뷰를 작성할 수 있습니다.");
            }
        }

        // MARKET 판매 게시글 작성 권한 검증
        if (requestDto.getBoardType() == BoardType.MARKET) {

            if (requestDto.getItemId() == null) {
                throw new BadRequestException("판매 게시글에는 상품 ID가 필요합니다.");
            }

            validateMarketBoardPermission(
                requestDto.getItemId(),
                loginUserId
            );
        }

        Board board = Board.builder()
            .title(requestDto.getTitle())
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .itemId(requestDto.getItemId())
            .boardType(requestDto.getBoardType())
            .isSecret(
                requestDto.getBoardType() == BoardType.MARKET
                    ? false
                    : requestDto.getIsSecret()
            )
            .build();

        return boardRepository.save(board).getId();
    }

    // 게시글 상세 조회

    @Transactional(readOnly = true)
    public BoardResponseDto getBoard(Long id, Long loginUserId) {

        Board board = boardRepository.findByIdWithReplies(id)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        ItemStatus itemStatus = null;

        // 1. 상품 문의
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

            // 2. 고객센터 문의
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

            // 3. 중고거래 게시글
        } else if (board.getBoardType() == BoardType.MARKET) {

            itemStatus = validateMarketBoardVisibility(board.getItemId());

            // 4. 일반 비밀글
        } else if (board.getBoardType() != BoardType.PRODUCT_REVIEW
            && Boolean.TRUE.equals(board.getIsSecret())) {

            if (!Objects.equals(board.getAuthorId(), loginUserId)) {
                throw new ForbiddenException(
                    "비밀글은 작성자 본인만 조회할 수 있습니다."
                );
            }
        }

        return BoardResponseDto.from(
            board,
            collectNicknames(List.of(board)),
            itemStatus
        );
    }


    // 댓글 작성
    @Transactional
    public Long createReply(
        Long boardId,
        ReplyRequestDto requestDto,
        Long loginUserId
    ) {
        if (loginUserId == null) {
            throw new ForbiddenException(
                "댓글을 작성하려면 로그인이 필요합니다."
            );
        }

        Board board = boardRepository.findById(boardId)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다."));

        // 1. 상품 문의 댓글 권한 검증
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
                    "상품 문의 댓글은 작성자 또는 해당 상품의 판매자만 작성할 수 있습니다."
                );
            }

            // 2. 고객센터 답변은 관리자만 작성 가능
        } else if (board.getBoardType() == BoardType.CUSTOMER_SERVICE) {

            if (!isAdmin()) {
                throw new ForbiddenException(
                    "고객센터 문의에 답변할 권한이 없습니다."
                );
            }

            // 3. 그 외 비밀글은 작성자 본인만 댓글 작성 가능
        } else if (Boolean.TRUE.equals(board.getIsSecret())) {

            if (!Objects.equals(board.getAuthorId(), loginUserId)) {
                throw new ForbiddenException(
                    "비밀글에 댓글을 작성할 권한이 없습니다."
                );
            }
        }

        Reply reply = Reply.builder()
            .board(board)
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .build();

        // 양방향 연관관계 설정
        board.addReply(reply);

        Long replyId = replyRepository.save(reply).getId();

        // 게시판 유형과 댓글 작성자에 따라 게시글 상태 변경
        if (board.getBoardType() == BoardType.PRODUCT_INQUIRY) {

            if (Objects.equals(board.getAuthorId(), loginUserId)) {
                // 문의 작성자가 추가 질문을 작성하면 답변 대기
                board.pending();
            } else {
                // 상품 판매자가 답변하면 답변 완료
                board.complete();
            }

        } else if (board.getBoardType() == BoardType.CUSTOMER_SERVICE) {

            // 관리자가 답변하면 답변 완료
            board.complete();
        }

        return replyId;
    }

    // 댓글 삭제

    @Transactional
    public void deleteReply(Long replyId, Long loginUserId) {
        Reply reply = replyRepository.findById(replyId)
            .orElseThrow(() ->
                new NotFoundException("댓글을 찾을 수 없습니다."));

        if (!Objects.equals(reply.getAuthorId(), loginUserId)) {
            throw new ForbiddenException(
                "댓글을 삭제할 권한이 없습니다."
            );
        }

        Board board = reply.getBoard();

        // 부모 엔티티 컬렉션에서도 댓글 제거
        board.removeReply(reply);

        // DB에서 댓글 삭제
        replyRepository.delete(reply);
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

        // 게시글 작성자 본인인지 확인
        if (!Objects.equals(board.getAuthorId(), loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        // MARKET 게시글은 현재 상품 상태도 확인
        if (board.getBoardType() == BoardType.MARKET) {
            validateMarketBoardEditable(board.getItemId());
        }

        Boolean isSecret = requestDto.getIsSecret();

        // MARKET 게시글은 항상 공개 상태 유지
        if (board.getBoardType() == BoardType.MARKET) {
            isSecret = false;
        }

        board.update(
            requestDto.getTitle(),
            requestDto.getContent(),
            isSecret
        );
    }

    // 게시글 삭제
    @Transactional
    public void deleteBoard(Long id, Long loginUserId) {
        Board board = boardRepository.findById(id)
            .orElseThrow(() ->
                new NotFoundException("게시글을 찾을 수 없습니다."));

        if (!Objects.equals(board.getAuthorId(), loginUserId)) {
            throw new ForbiddenException("게시글 권한이 없습니다.");
        }

        boardRepository.delete(board);
    }

    // 공개 게시글 목록 조회 및 검색

    public Page<BoardResponseDto> getBoards(
        String keyword,
        Pageable pageable,
        Long loginUserId
    ) {
        List<BoardType> publicBoardTypes = List.of(
            BoardType.PRODUCT_REVIEW,
            BoardType.COMMUNITY,
            BoardType.MARKET
        );

        List<ItemStatus> visibleItemStatuses = List.of(
            ItemStatus.ON_SALE,
            ItemStatus.SOLD
        );

        Page<Board> boardPage;

        String searchKeyword = keyword == null ? "" : keyword.trim();

        if (searchKeyword.isEmpty()) {
            boardPage = boardRepository.findPublicBoards(
                publicBoardTypes,
                BoardType.MARKET,
                visibleItemStatuses,
                pageable
            );
        } else {
            boardPage = boardRepository.findPublicBoardsByKeyword(
                searchKeyword,
                publicBoardTypes,
                BoardType.MARKET,
                visibleItemStatuses,
                pageable
            );
        }

        List<Board> boards = boardPage.getContent();

        Map<Long, String> nicknames = collectBoardNicknames(boards);

        // 현재 페이지의 MARKET 게시글 상품 상태를 한 번에 조회
        Map<Long, ItemStatus> itemStatuses =
            collectMarketItemStatuses(boards);

        boolean admin = isAdmin();

        return boardPage.map(board ->
            BoardResponseDto.fromForList(
                board,
                nicknames,
                loginUserId,
                admin,
                itemStatuses
            )
        );
    }


    // 상품별 문의 목록 조회: 작성자 본인 문의만 조회
    public Page<BoardResponseDto> getProductInquiries(
        Long itemId,
        Pageable pageable,
        Long loginUserId
    ) {
        if (loginUserId == null) {
            throw new ForbiddenException(
                "상품 문의를 조회하려면 로그인이 필요합니다."
            );
        }

        Page<Board> boardPage =
            boardRepository.findByItemIdAndBoardTypeAndAuthorId(
                itemId,
                BoardType.PRODUCT_INQUIRY,
                loginUserId,
                pageable
            );

        Map<Long, String> nicknames =
            collectBoardNicknames(boardPage.getContent());

        return boardPage.map(board ->
            BoardResponseDto.fromForList(
                board,
                nicknames,
                loginUserId,
                false
            )
        );
    }

    // 고객센터 문의 목록 조회: 작성자 본인 문의만 조회
    public Page<BoardResponseDto> getMyCustomerServiceInquiries(
        Pageable pageable,
        Long loginUserId
    ) {
        if (loginUserId == null) {
            throw new ForbiddenException(
                "고객센터 문의를 조회하려면 로그인이 필요합니다."
            );
        }

        Page<Board> boardPage =
            boardRepository.findByBoardTypeAndAuthorId(
                BoardType.CUSTOMER_SERVICE,
                loginUserId,
                pageable
            );

        Map<Long, String> nicknames =
            collectBoardNicknames(boardPage.getContent());

        return boardPage.map(board ->
            BoardResponseDto.fromForList(
                board,
                nicknames,
                loginUserId,
                false
            )
        );
    }

    // 관리자 전용: 전체 고객센터 문의 목록 조회
    public Page<BoardResponseDto> getAllCustomerServiceInquiries(
        Pageable pageable
    ) {
        if (!isAdmin()) {
            throw new ForbiddenException(
                "고객센터 전체 문의를 조회할 권한이 없습니다."
            );
        }

        Page<Board> boardPage =
            boardRepository.findByBoardType(
                BoardType.CUSTOMER_SERVICE,
                pageable
            );

        Map<Long, String> nicknames =
            collectBoardNicknames(boardPage.getContent());

        return boardPage.map(board ->
            BoardResponseDto.fromForList(
                board,
                nicknames,
                null,
                true
            )
        );
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

    private void validateMarketBoardPermission(Long itemId, Long loginUserId) {

        // 1. 삭제되지 않은 상품 조회
        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));

        // 2. 가게 상품인지 확인
        if (item.getShopId() == null) {
            throw new BadRequestException("가게에 등록된 상품만 판매 게시글을 작성할 수 있습니다.");
        }

        // 3. 상품 판매 가능 상태 확인
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BadRequestException("판매 중인 상품만 판매 게시글을 작성할 수 있습니다.");
        }

        // 4. 가게 조회
        Shop shop = shopRepository.findById(item.getShopId())
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));

        // 5. 가게 삭제 여부 확인
        if (shop.isDeleted()) {
            throw new ForbiddenException("삭제된 가게의 상품은 판매할 수 없습니다.");
        }

        // 6. 가게 승인 여부 확인
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new ForbiddenException("승인된 가게만 상품을 판매할 수 있습니다.");
        }

        // 7. 상품 판매자와 가게 소유자 일치 여부 확인
        if (!Objects.equals(item.getSellerUserId(), shop.getOwnerUserId())) {
            throw new ForbiddenException("상품 판매자와 가게 소유자가 일치하지 않습니다.");
        }

        // 8. 로그인 사용자가 가게 소유자인지 확인
        if (!shop.isOwner(loginUserId)) {
            throw new ForbiddenException("본인 가게의 상품만 판매 게시글을 작성할 수 있습니다.");
        }

        // 9. 가장 최근의 구독 이력 조회
        ShopSubscription subscription = shopSubscriptionRepository
            .findFirstByShopIdAndDeletedAtIsNullOrderByIdDesc(shop.getId())
            .orElseThrow(() -> new ForbiddenException("가게 구독이 필요합니다."));

        // 10. 현재 구독 상태 및 만료일 확인
        if (subscription.getStatus() != ShopSubscriptionStatus.ACTIVE
            || subscription.getEndDate().isBefore(LocalDate.now())) {
            throw new ForbiddenException("구독이 활성화된 가게만 상품을 판매할 수 있습니다.");
        }
    }

    /**
     * MARKET 게시글 조회 가능 여부 검증
     *
     * 판매 중(ON_SALE)이거나 판매 완료(SOLD)된 상품만 조회할 수 있다.
     * 판매 중지(CLOSED) 또는 삭제된 상품은 조회할 수 없다.
     */

    private ItemStatus validateMarketBoardVisibility(Long itemId) {
        if (itemId == null) {
            throw new NotFoundException("판매 게시글의 상품 정보를 찾을 수 없습니다.");
        }

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));

        if (item.getStatus() != ItemStatus.ON_SALE
            && item.getStatus() != ItemStatus.SOLD) {
            throw new NotFoundException("조회할 수 없는 판매 게시글입니다.");
        }

        return item.getStatus();
    }


    /**
     * MARKET 게시글 수정 가능 여부 검증
     *
     * 판매 중인 상품과 판매 완료된 상품만 수정할 수 있다.
     * 판매 중지 또는 삭제된 상품은 수정할 수 없다.
     */
    private void validateMarketBoardEditable(Long itemId) {
        if (itemId == null) {
            throw new ForbiddenException("상품 정보가 없어 게시글을 수정할 수 없습니다.");
        }

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() ->
                new ForbiddenException("삭제된 상품의 판매 게시글은 수정할 수 없습니다.")
            );

        if (item.getStatus() != ItemStatus.ON_SALE
            && item.getStatus() != ItemStatus.SOLD) {
            throw new ForbiddenException("현재 상품 상태에서는 게시글을 수정할 수 없습니다.");
        }
    }

    private Map<Long, ItemStatus> collectMarketItemStatuses(
        List<Board> boards
    ) {
        List<Long> itemIds = boards.stream()
            .filter(board -> board.getBoardType() == BoardType.MARKET)
            .map(Board::getItemId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (itemIds.isEmpty()) {
            return Map.of();
        }

        return itemRepository.findAllById(itemIds).stream()
            .filter(item -> !item.isDeleted())
            .filter(item ->
                item.getStatus() == ItemStatus.ON_SALE
                    || item.getStatus() == ItemStatus.SOLD
            )
            .collect(Collectors.toMap(
                Item::getId,
                Item::getStatus
            ));
    }

    // 상품별 문의 목록 조회: 해당 상품 판매자 전용
    public Page<BoardResponseDto> getSellerProductInquiries(
        Long itemId,
        Pageable pageable,
        Long loginUserId
    ) {
        // 1. 로그인 여부 확인
        if (loginUserId == null) {
            throw new ForbiddenException(
                "상품 문의를 조회하려면 로그인이 필요합니다."
            );
        }

        // 2. 상품 조회
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() ->
                new NotFoundException("상품을 찾을 수 없습니다.")
            );

        // 3. 상품 판매자 본인인지 확인
        if (!Objects.equals(item.getSellerUserId(), loginUserId)) {
            throw new ForbiddenException(
                "해당 상품의 판매자만 문의 목록을 조회할 수 있습니다."
            );
        }

        // 4. 해당 상품의 상품 문의 목록 조회
        Page<Board> boardPage =
            boardRepository.findByItemIdAndBoardType(
                itemId,
                BoardType.PRODUCT_INQUIRY,
                pageable
            );

        // 5. 게시글 작성자 닉네임 조회
        Map<Long, String> nicknames =
            collectBoardNicknames(boardPage.getContent());

        // 6. 판매자 전용 DTO 변환
        return boardPage.map(board ->
            BoardResponseDto.fromForSellerInquiryList(
                board,
                nicknames
            )
        );
    }


}
