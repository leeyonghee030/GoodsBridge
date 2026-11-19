
package com.goodsbridge.board;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.Item;

import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.purchase.PurchaseStatus;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final ApplicationEventPublisher publisher;
    private final PurchaseRepository purchaseRepository;
    private final ItemRepository itemRepository;
    private final ShopEventRepository shopEventRepository;
    private final AuctionRepository auctionRepository;

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

        Long itemId = requestDto.getItemId();
        InquiryTargetType targetType = null;
        Long targetId = null;
        Long purchaseId = null;

        // 상품 리뷰: 구매확정한 구매에만 쓸 수 있고, 구매 1건당 1개다
        if (requestDto.getBoardType() == BoardType.PRODUCT_REVIEW) {
            Purchase purchase = findReviewablePurchase(requestDto, loginUserId);
            itemId = purchase.getItem().getId();
            purchaseId = purchase.getId();
        }

        // 상품 문의: 상품·이벤트·경매 중 한 곳에 남긴다
        if (requestDto.getBoardType() == BoardType.PRODUCT_INQUIRY) {
            targetType = requestDto.getTargetType() == null
                ? InquiryTargetType.ITEM
                : requestDto.getTargetType();
            targetId = requestDto.getTargetId();

            switch (targetType) {
                case ITEM -> {
                    // targetId 만 보낸 경우에도 상품 id 로 쓴다
                    if (itemId == null) {
                        itemId = targetId;
                    }
                    if (itemId == null) {
                        throw new BadRequestException("상품 문의에는 상품 ID가 필요합니다.");
                    }
                    itemRepository.findById(itemId)
                        .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
                    targetId = itemId;
                }
                case EVENT -> {
                    requireTargetId(targetId);
                    ShopEvent event = shopEventRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("이벤트를 찾을 수 없습니다."));
                    itemId = event.getItem().getId();
                }
                case AUCTION -> {
                    requireTargetId(targetId);
                    Auction auction = auctionRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("경매를 찾을 수 없습니다."));
                    if (auction.getItem() == null) {
                        throw new BadRequestException("상품이 연결되지 않은 경매에는 문의할 수 없습니다.");
                    }
                    itemId = auction.getItem().getId();
                }
            }
        }

        Board board = Board.builder()
            .title(requestDto.getTitle())
            .content(requestDto.getContent())
            .authorId(loginUserId)
            .itemId(itemId)
            .boardType(requestDto.getBoardType())
            .isSecret(requestDto.getIsSecret())
            .targetType(targetType)
            .targetId(targetId)
            .purchaseId(purchaseId)
            .build();

        try {
            return boardRepository.save(board).getId();
        } catch (DataIntegrityViolationException e) {
            // 같은 구매에 리뷰를 동시에 두 번 보낸 경우 (purchase_id 유니크 제약)
            throw new BadRequestException("이미 리뷰를 작성한 구매입니다.");
        }
    }

    private void requireTargetId(Long targetId) {
        if (targetId == null) {
            throw new BadRequestException("문의 대상(targetId)은 필수 입력값입니다.");
        }
    }

    // 리뷰를 쓸 구매를 찾는다. 구매확정(CONFIRMED)한 내 구매여야 하고, 구매 1건당 리뷰는 1개다.
    private Purchase findReviewablePurchase(BoardRequestDto requestDto, Long loginUserId) {

        if (requestDto.getPurchaseId() != null) {
            Purchase purchase = purchaseRepository.findById(requestDto.getPurchaseId())
                .orElseThrow(() -> new NotFoundException("구매 내역을 찾을 수 없습니다."));

            if (!Objects.equals(purchase.getUser().getId(), loginUserId)) {
                throw new ForbiddenException("본인이 구매한 상품에만 리뷰를 작성할 수 있습니다.");
            }
            if (requestDto.getItemId() != null
                && !Objects.equals(purchase.getItem().getId(), requestDto.getItemId())) {
                throw new BadRequestException("구매한 상품과 리뷰 대상 상품이 다릅니다.");
            }
            if (purchase.getStatus() != PurchaseStatus.CONFIRMED) {
                throw new ForbiddenException("구매확정한 상품만 리뷰를 작성할 수 있습니다.");
            }
            if (boardRepository.existsByPurchaseId(purchase.getId())) {
                throw new BadRequestException("이미 리뷰를 작성한 구매입니다.");
            }
            return purchase;
        }

        // 구매를 지정하지 않으면 이 상품에서 리뷰를 쓸 수 있는 내 구매 중 가장 오래된 것을 쓴다
        if (requestDto.getItemId() == null) {
            throw new BadRequestException("상품 리뷰에는 상품 ID가 필요합니다.");
        }
        itemRepository.findById(requestDto.getItemId())
            .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));

        return purchaseRepository.findReviewablePurchases(loginUserId, requestDto.getItemId())
            .stream()
            .findFirst()
            .orElseThrow(() -> new ForbiddenException(
                "구매확정한 상품만 리뷰를 작성할 수 있고, 구매 1건당 리뷰는 1개만 쓸 수 있습니다."
            ));
    }

    // 이 상품에 지금 리뷰를 쓸 수 있는지 (구매확정했고 아직 리뷰를 쓰지 않은 구매가 있는지)
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getReviewEligibility(Long itemId, Long loginUserId) {
        if (loginUserId == null || itemId == null) {
            return ReviewEligibilityResponse.no();
        }
        return purchaseRepository.findReviewablePurchases(loginUserId, itemId)
            .stream()
            .findFirst()
            .map(purchase -> new ReviewEligibilityResponse(true, purchase.getId()))
            .orElse(ReviewEligibilityResponse.no());
    }

    // 내가 쓴 리뷰: 구매 id -> 게시글 id
    @Transactional(readOnly = true)
    public Map<Long, Long> getMyReviewIds(Long loginUserId) {
        if (loginUserId == null) {
            return Map.of();
        }
        return boardRepository.findMyReviewIds(loginUserId).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1],
                (first, second) -> first
            ));
    }

    private boolean isSellerOf(Board board, Long userId) {
        if (userId == null || board.getItemId() == null) {
            return false;
        }
        return itemRepository.findById(board.getItemId())
            .map(item -> Objects.equals(item.getSellerUserId(), userId))
            .orElse(false);
    }

    private void requireLogin(Long loginUserId) {
        if (loginUserId == null) {
            throw new ForbiddenException("로그인이 필요한 서비스입니다.");
        }
    }

    // 마이페이지 "내 문의": 내가 쓴 상품 문의 (targetType 을 주면 상품/이벤트/경매 중 그 대상만)
    @Transactional(readOnly = true)
    public Page<BoardManageResponse> getMyInquiries(
        Long loginUserId,
        InquiryTargetType targetType,
        Pageable pageable
    ) {
        requireLogin(loginUserId);
        Page<Board> page;
        if (targetType == null) {
            page = boardRepository.findMyInquiries(loginUserId, pageable);
        } else if (targetType == InquiryTargetType.ITEM) {
            page = boardRepository.findMyItemInquiries(loginUserId, pageable);
        } else {
            page = boardRepository.findMyInquiriesByTarget(loginUserId, targetType, pageable);
        }
        return toManageResponses(page);
    }

    // 판매자 "문의 관리": 내 상품·이벤트·경매에 온 문의
    @Transactional(readOnly = true)
    public Page<BoardManageResponse> getSellerInquiries(
        Long loginUserId,
        InquiryTargetType targetType,
        Pageable pageable
    ) {
        requireLogin(loginUserId);
        Page<Board> page;
        if (targetType == null) {
            page = boardRepository.findSellerInquiries(loginUserId, pageable);
        } else if (targetType == InquiryTargetType.ITEM) {
            page = boardRepository.findSellerItemInquiries(loginUserId, pageable);
        } else {
            page = boardRepository.findSellerInquiriesByTarget(loginUserId, targetType, pageable);
        }
        return toManageResponses(page);
    }

    // 판매자 "리뷰 관리": 내 상품의 리뷰 (비공개로 바꾼 것도 포함)
    @Transactional(readOnly = true)
    public Page<BoardManageResponse> getSellerReviews(Long loginUserId, Pageable pageable) {
        requireLogin(loginUserId);
        return toManageResponses(boardRepository.findReviewsBySeller(loginUserId, pageable));
    }

    // 문의 탭의 개수: 전체/상품/이벤트/경매. scope 가 seller 면 내 상품에 온 문의, 아니면 내가 쓴 문의
    @Transactional(readOnly = true)
    public Map<String, Long> getInquiryCounts(Long loginUserId, boolean seller) {
        requireLogin(loginUserId);
        PageRequest one = PageRequest.of(0, 1);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", (seller
            ? getSellerInquiries(loginUserId, null, one)
            : getMyInquiries(loginUserId, null, one)).getTotalElements());
        for (InquiryTargetType type : InquiryTargetType.values()) {
            counts.put(type.name(), (seller
                ? getSellerInquiries(loginUserId, type, one)
                : getMyInquiries(loginUserId, type, one)).getTotalElements());
        }
        return counts;
    }

    // 판매자가 자기 상품의 리뷰를 비공개로 바꾸거나 다시 공개한다
    @Transactional
    public void setReviewHidden(Long boardId, boolean hidden, Long loginUserId) {
        requireLogin(loginUserId);
        Board board = boardRepository.findById(boardId)
            .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        if (board.getBoardType() != BoardType.PRODUCT_REVIEW) {
            throw new BadRequestException("상품 리뷰만 비공개로 바꿀 수 있습니다.");
        }
        if (!isSellerOf(board, loginUserId)) {
            throw new ForbiddenException("해당 상품의 판매자만 리뷰를 관리할 수 있습니다.");
        }

        if (hidden) {
            board.hideBySeller();
        } else {
            board.showBySeller();
        }
    }

    private Page<BoardManageResponse> toManageResponses(Page<Board> page) {
        List<Board> boards = page.getContent();
        Map<Long, String> nicknames = collectBoardNicknames(boards);
        List<Long> itemIds = boards.stream()
            .map(Board::getItemId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, String> itemTitles = itemIds.isEmpty()
            ? Map.of()
            : itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Item::getTitle, (a, b) -> a));

        return page.map(board -> new BoardManageResponse(
            board.getId(),
            board.getBoardType(),
            board.getTitle(),
            board.getContent(),
            nicknames.getOrDefault(board.getAuthorId(), "알 수 없음"),
            board.getItemId(),
            itemTitles.get(board.getItemId()),
            board.getTargetType(),
            board.getTargetId(),
            board.getBoardType() == BoardType.PRODUCT_INQUIRY ? board.getStatus() : null,
            board.isHiddenBySeller(),
            board.getCreatedAt()
        ));
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

        // 판매자가 비공개로 바꾼 리뷰는 작성자 본인과 그 상품의 판매자만 볼 수 있다
        if (board.getBoardType() == BoardType.PRODUCT_REVIEW
            && board.isHiddenBySeller()
            && !Objects.equals(board.getAuthorId(), loginUserId)
            && !isSellerOf(board, loginUserId)) {
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

        // 작성자 본인이 단 댓글이 아니면 게시글 작성자에게 알린다
        if (!board.getAuthorId().equals(loginUserId)) {
            publisher.publishEvent(
                new NotificationEvent(
                    board.getAuthorId(),
                    Notification.Type.BOARD_REPLY,
                    "[새로운 댓글]",
                    "{" + board.getTitle() + "}에 새로운 댓글이 달렸습니다",
                    Notification.ReferenceType.BOARD,
                    board.getId()
                )
            );
        }

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

        boolean isAuthor = Objects.equals(board.getAuthorId(), loginUserId);
        // 판매자는 자기 상품의 리뷰를 삭제할 수 있다 (리뷰 관리)
        boolean isReviewSeller = board.getBoardType() == BoardType.PRODUCT_REVIEW
            && isSellerOf(board, loginUserId);

        if (!isAuthor && !isReviewSeller) {
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
        return getBoards(boardType, keyword, null, null, null, pageable, loginUserId);
    }

    // 게시글 목록 조회 (상품 상세 화면용 필터 포함)
    // - 상품 리뷰 + itemId: 그 상품의 리뷰
    // - 상품 문의 + targetType/targetId (또는 itemId): 그 상품·이벤트·경매의 문의
    @Transactional(readOnly = true)
    public Page<BoardResponseDto> getBoards(
        BoardType boardType,
        String keyword,
        Long itemId,
        InquiryTargetType targetType,
        Long targetId,
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

        if (boardType == BoardType.PRODUCT_REVIEW && itemId != null) {
            boardPage = boardRepository.findReviewsByItemId(itemId, loginUserId, pageable);
        } else if (boardType == BoardType.PRODUCT_INQUIRY
            && (targetType != null || itemId != null)) {
            InquiryTargetType type = targetType == null ? InquiryTargetType.ITEM : targetType;
            Long id = targetId != null ? targetId : itemId;
            if (id == null) {
                throw new BadRequestException("문의 대상(targetId)은 필수 입력값입니다.");
            }
            boardPage = type == InquiryTargetType.ITEM
                ? boardRepository.findItemInquiries(id, pageable)
                : boardRepository.findInquiriesByTarget(type, id, pageable);
        } else if (searchKeyword.isBlank()) {
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
        // 판매자가 비공개로 바꾼 리뷰는 작성자 본인에게만 보인다
        if (board.getBoardType() == BoardType.PRODUCT_REVIEW && board.isHiddenBySeller()) {
            return loginUserId != null && Objects.equals(board.getAuthorId(), loginUserId);
        }

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
