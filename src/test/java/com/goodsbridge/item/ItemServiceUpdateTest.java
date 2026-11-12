
package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.shop.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceUpdateTest {

    @InjectMocks
    private ItemService itemService;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CategoryJpaRepository categoryRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private ShopSubscriptionRepository shopSubscriptionRepository;

    // 승인된 가게 생성
    private Shop createApprovedShop(Long ownerUserId) {
        return Shop.builder()
            .ownerUserId(ownerUserId)
            .name("테스트 샵")
            .region("서울")
            .status(ShopStatus.APPROVED)
            .build();
    }

    // 유효한 구독 여부 Mock 설정
    private void mockActiveSubscription(boolean active) {
        given(shopSubscriptionRepository
            .existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                eq(10L),
                eq(ShopSubscriptionStatus.ACTIVE),
                any(LocalDate.class)
            ))
            .willReturn(active);
    }

    @Test
    @DisplayName("성공: 본인 소유의 승인된 가게이고 구독이 활성화된 경우 상품 등록 성공")
    void createItem_Success() {

        // given
        Long userId = 1L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop mockShop = createApprovedShop(userId);

        Category mockCategory = Category.builder()
            .name("굿즈")
            .build();

        given(shopRepository.findByIdWithLock(10L))
            .willReturn(Optional.of(mockShop));

        mockActiveSubscription(true);

        given(categoryRepository.findById(1L))
            .willReturn(Optional.of(mockCategory));

        given(itemRepository.save(any(Item.class)))
            .willReturn(
                Item.builder()
                    .sellerUserId(userId)
                    .shopId(10L)
                    .category(mockCategory)
                    .title("테스트 상품")
                    .price(10000)
                    .saleType(SaleType.NORMAL)
                    .build()
            );

        // when
        itemService.create(userId, request);

        // then
        verify(itemRepository).save(any(Item.class));
    }

    @Test
    @DisplayName("실패: shopId가 누락된 경우 BadRequestException 발생")
    void createItem_Fail_NullShopId() {

        // given
        Long userId = 1L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(null);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        // when & then
        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("유효한 가게 ID를 입력해주세요.");

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    @DisplayName("실패: 가게 소유자가 아닌 경우 ForbiddenException 발생")
    void createItem_Fail_NotOwner() {

        // given
        Long loginUserId = 1L;
        Long otherOwnerId = 2L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop mockShop = createApprovedShop(otherOwnerId);

        given(shopRepository.findByIdWithLock(10L))
            .willReturn(Optional.of(mockShop));

        // when & then
        assertThatThrownBy(() -> itemService.create(loginUserId,request ))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("본인 소유의 가게에서만 상품을 등록하거나 수정할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    @DisplayName("실패: 가게 구독이 활성화되지 않은 경우 ForbiddenException 발생")
    void createItem_Fail_InactiveSubscription() {

        // given
        Long userId = 1L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop mockShop = createApprovedShop(userId);

        given(shopRepository.findByIdWithLock(10L))
            .willReturn(Optional.of(mockShop));

        mockActiveSubscription(false);

        // when & then
        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    @DisplayName("성공: 본인 소유의 구독 중인 가게 상품 정보 수정 성공")
    void updateItem_Success() {

        // given
        Long userId = 1L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("수정된 상품 제목");
        request.setDescription("수정된 설명");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        Category mockCategory = Category.builder()
            .name("기존")
            .build();

        Item mockItem = Item.builder()
            .sellerUserId(userId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();

        Shop mockShop = createApprovedShop(userId);

        given(itemRepository.findByIdAndDeletedFalse(itemId))
            .willReturn(Optional.of(mockItem));

        given(shopRepository.findByIdWithLock(10L))
            .willReturn(Optional.of(mockShop));

        mockActiveSubscription(true);

        given(categoryRepository.findById(1L))
            .willReturn(Optional.of(mockCategory));

        // when
        itemService.update(userId, itemId, request);

        // then
        assertThat(mockItem.getTitle()).isEqualTo("수정된 상품 제목");
        assertThat(mockItem.getPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("실패: 상품 소유자가 아닌 유저가 수정 시도 시 ForbiddenException 발생")
    void updateItem_Fail_NotItemOwner() {

        // given
        Long loginUserId = 1L;
        Long itemOwnerId = 2L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("수정 시도");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        Category mockCategory = Category.builder()
            .name("기존")
            .build();

        Item mockItem = Item.builder()
            .sellerUserId(itemOwnerId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .build();

        given(itemRepository.findByIdAndDeletedFalse(itemId))
            .willReturn(Optional.of(mockItem));

        // when & then
        assertThatThrownBy(() ->
            itemService.update(loginUserId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("해당 상품에 대한 권한이 없습니다.");
    }

    @Test
    @DisplayName("실패: 상품 수정 시 shopId가 누락된 경우 BadRequestException 발생")
    void updateItem_Fail_NullShopId() {

        // given
        Long userId = 1L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(null);
        request.setCategoryId(1L);
        request.setTitle("수정 시도");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        // when & then
        assertThatThrownBy(() ->
            itemService.update(userId, itemId, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("유효한 가게 ID를 입력해주세요.");
    }

    @Test
    @DisplayName("실패: 수정하려는 가게의 소유자가 아닌 경우 ForbiddenException 발생")
    void updateItem_Fail_NotShopOwner() {

        // given
        Long loginUserId = 1L;
        Long otherShopOwnerId = 2L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("수정 시도");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        Category mockCategory = Category.builder()
            .name("기존")
            .build();

        Item mockItem = Item.builder()
            .sellerUserId(loginUserId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .build();

        Shop mockShop = createApprovedShop(otherShopOwnerId);

        given(itemRepository.findByIdAndDeletedFalse(itemId))
            .willReturn(Optional.of(mockItem));

        given(shopRepository.findByIdWithLock(10L))
            .willReturn(Optional.of(mockShop));

        // when & then
        assertThatThrownBy(() -> itemService.update(loginUserId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("본인 소유의 가게에서만 상품을 등록하거나 수정할 수 있습니다.");
    }

    @Test
    @DisplayName("실패: 수정하려는 가게의 구독이 비활성화된 경우 ForbiddenException 발생")
    void updateItem_Fail_InactiveSubscription() {

        // given
        Long userId = 1L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("수정 시도");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        Category mockCategory = Category.builder()
            .name("기존")
            .build();

        Item mockItem = Item.builder()
            .sellerUserId(userId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .build();

        Shop mockShop = createApprovedShop(userId);

        given(itemRepository.findByIdAndDeletedFalse(itemId))
            .willReturn(Optional.of(mockItem));

        given(shopRepository.findByIdWithLock(10L))
            .willReturn(Optional.of(mockShop));

        mockActiveSubscription(false);

        // when & then
        assertThatThrownBy(() ->
            itemService.update(userId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("구독이 활성화된 가게의 상품만 수정할 수 있습니다.");
    }
    @Test
    @DisplayName("구독 이력이 없는 가게는 상품을 등록할 수 없다")
    void create_withoutSubscription_fail() {
        // given
        Long userId = 1L;
        Long shopId = 10L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(shopId);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop shop = createApprovedShop(userId);

        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        when(shopSubscriptionRepository
            .existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                eq(shopId),
                eq(ShopSubscriptionStatus.ACTIVE),
                any(LocalDate.class)
            ))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }


    @Test
    @DisplayName("ACTIVE 상태라도 구독 만료일이 지났으면 상품을 등록할 수 없다")
    void create_expiredActiveSubscription_fail() {
        // given
        Long userId = 1L;
        Long shopId = 10L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(shopId);
        request.setCategoryId(1L);
        request.setTitle("만료된 구독 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop shop = createApprovedShop(userId);

        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        // 만료된 ACTIVE 구독은 유효한 구독으로 조회되지 않는다.
        when(shopSubscriptionRepository
            .existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                eq(shopId),
                eq(ShopSubscriptionStatus.ACTIVE),
                any(LocalDate.class)
            ))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }
    @Test
    @DisplayName("구독 만료 스케줄러 실행 전이라도 만료된 구독은 상품을 수정할 수 없다")
    void update_expiredActiveSubscription_fail() {
        // given
        Long userId = 1L;
        Long shopId = 10L;
        Long itemId = 100L;

        Shop shop = createApprovedShop(userId);

        Category category = Category.builder()
            .name("테스트 카테고리")
            .build();

        Item item = Item.builder()
            .sellerUserId(userId)
            .shopId(shopId)
            .category(category)
            .title("기존 상품")
            .price(10000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(shopId);
        request.setCategoryId(1L);
        request.setTitle("수정할 상품");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        when(itemRepository.findByIdAndDeletedFalse(itemId))
            .thenReturn(Optional.of(item));

        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        when(shopSubscriptionRepository
            .existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                eq(shopId),
                eq(ShopSubscriptionStatus.ACTIVE),
                any(LocalDate.class)
            ))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> itemService.update(userId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessage("구독이 활성화된 가게의 상품만 수정할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }
}
