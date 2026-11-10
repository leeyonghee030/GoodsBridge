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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
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
    private ShopSubscriptionRepository shopSubscriptionRepository; // 💡 ShopSubscriptionService 대신 Repository Mock 주입

    @Test
    @DisplayName("성공: 유효한 가게 ID, 소유자 일치, 구독 활성화 상태인 경우 상품 등록 성공")
    void createItem_Success() {
        // given
        Long userId = 1L;
        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop mockShop = Shop.builder()
            .ownerUserId(userId)
            .name("테스트 샵")
            .region("서울")
            .build();

        Category mockCategory = Category.builder().name("굿즈").build();

        // 💡 활성 구독 엔티티 스텁 설정 (status: ACTIVE, endDate: 미래)
        ShopSubscription activeSubscription = ShopSubscription.builder()
            .shopId(10L)
            .status(ShopSubscriptionStatus.ACTIVE)
            .startDate(LocalDate.now().minusDays(5))
            .endDate(LocalDate.now().plusDays(25))
            .build();

        given(shopRepository.findByIdWithLock(anyLong())).willReturn(Optional.of(mockShop));
        given(shopSubscriptionRepository.findByShopId(anyLong())).willReturn(Optional.of(activeSubscription));
        given(categoryRepository.findById(anyLong())).willReturn(Optional.of(mockCategory));
        given(itemRepository.save(any(Item.class))).willReturn(
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
    @DisplayName("실패: shopId가 누락(null)된 경우 BadRequestException 발생")
    void createItem_Fail_NullShopId() {
        Long userId = 1L;
        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(null);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("상품을 등록하려면 가게 선택이 필수입니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    @DisplayName("실패: 요청한 가게의 소유자(owner)가 아닌 경우 ForbiddenException 발생")
    void createItem_Fail_NotOwner() {
        Long loginUserId = 1L;
        Long otherOwnerId = 2L;

        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop mockShop = Shop.builder()
            .ownerUserId(otherOwnerId)
            .name("타인 샵")
            .region("부산")
            .build();

        given(shopRepository.findByIdWithLock(anyLong())).willReturn(Optional.of(mockShop));

        assertThatThrownBy(() -> itemService.create(loginUserId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("본인 소유의 가게에만 상품을 등록할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    @DisplayName("실패: 가게 구독이 활성화되지 않은 경우 ForbiddenException 발생")
    void createItem_Fail_InactiveSubscription() {
        Long userId = 1L;
        ItemCreateRequest request = new ItemCreateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("테스트 상품");
        request.setPrice(10000);
        request.setSaleType(SaleType.NORMAL);

        Shop mockShop = Shop.builder()
            .ownerUserId(userId)
            .name("테스트 샵")
            .region("서울")
            .build();

        given(shopRepository.findByIdWithLock(anyLong())).willReturn(Optional.of(mockShop));
        given(shopSubscriptionRepository.findByShopId(anyLong())).willReturn(Optional.empty()); // 💡 구독 정보 없음 (비활성)

        assertThatThrownBy(() -> itemService.create(userId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    @DisplayName("성공: 유효한 요청으로 본인 소유 및 구독 중인 샵의 상품 정보 수정 성공")
    void updateItem_Success() {
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

        Category mockCategory = Category.builder().name("기존").build();

        Item mockItem = Item.builder()
            .sellerUserId(userId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build();

        Shop mockShop = Shop.builder()
            .ownerUserId(userId)
            .name("테스트 샵")
            .region("서울")
            .build();

        ShopSubscription activeSubscription = ShopSubscription.builder()
            .shopId(10L)
            .status(ShopSubscriptionStatus.ACTIVE)
            .startDate(LocalDate.now().minusDays(5))
            .endDate(LocalDate.now().plusDays(25))
            .build();

        given(itemRepository.findByIdAndDeletedFalse(itemId)).willReturn(Optional.of(mockItem));
        given(shopRepository.findByIdWithLock(anyLong())).willReturn(Optional.of(mockShop));
        given(shopSubscriptionRepository.findByShopId(anyLong())).willReturn(Optional.of(activeSubscription));
        given(categoryRepository.findById(anyLong())).willReturn(Optional.of(mockCategory));

        itemService.update(userId, itemId, request);

        assertThat(mockItem.getTitle()).isEqualTo("수정된 상품 제목");
        assertThat(mockItem.getPrice()).isEqualTo(20000);
    }

    @Test
    @DisplayName("실패: 상품 소유자가 아닌 유저가 수정 시도 시 ForbiddenException 발생")
    void updateItem_Fail_NotItemOwner() {
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

        Category mockCategory = Category.builder().name("기존").build();

        Item mockItem = Item.builder()
            .sellerUserId(itemOwnerId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .build();

        given(itemRepository.findByIdAndDeletedFalse(itemId)).willReturn(Optional.of(mockItem));

        assertThatThrownBy(() -> itemService.update(loginUserId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("해당 상품에 대한 권한이 없습니다.");
    }

    @Test
    @DisplayName("실패: 상품 수정 시 shopId가 누락(null)된 경우 BadRequestException 발생")
    void updateItem_Fail_NullShopId() {
        Long userId = 1L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(null);
        request.setCategoryId(1L);
        request.setTitle("수정 시도");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        assertThatThrownBy(() -> itemService.update(userId, itemId, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("상품 수정 시 가게 선택은 필수입니다.");
    }

    @Test
    @DisplayName("실패: 수정하려는 샵의 소유자가 아닌 경우 ForbiddenException 발생")
    void updateItem_Fail_NotShopOwner() {
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

        Category mockCategory = Category.builder().name("기존").build();

        Item mockItem = Item.builder()
            .sellerUserId(loginUserId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .build();

        Shop mockShop = Shop.builder()
            .ownerUserId(otherShopOwnerId)
            .name("타인 샵")
            .build();

        given(itemRepository.findByIdAndDeletedFalse(itemId)).willReturn(Optional.of(mockItem));
        given(shopRepository.findByIdWithLock(anyLong())).willReturn(Optional.of(mockShop));

        assertThatThrownBy(() -> itemService.update(loginUserId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("본인 소유의 가게로만 상품을 지정하거나 수정할 수 있습니다.");
    }

    @Test
    @DisplayName("실패: 수정하려는 샵의 구독이 만료되었거나 활성화되지 않은 경우 ForbiddenException 발생")
    void updateItem_Fail_InactiveSubscription() {
        Long userId = 1L;
        Long itemId = 100L;

        ItemUpdateRequest request = new ItemUpdateRequest();
        request.setShopId(10L);
        request.setCategoryId(1L);
        request.setTitle("수정 시도");
        request.setPrice(20000);
        request.setSaleType(SaleType.NORMAL);
        request.setStatus(ItemStatus.ON_SALE);

        Category mockCategory = Category.builder().name("기존").build();

        Item mockItem = Item.builder()
            .sellerUserId(userId)
            .shopId(10L)
            .category(mockCategory)
            .title("기존 제목")
            .price(15000)
            .build();

        Shop mockShop = Shop.builder()
            .ownerUserId(userId)
            .name("테스트 샵")
            .build();

        given(itemRepository.findByIdAndDeletedFalse(itemId)).willReturn(Optional.of(mockItem));
        given(shopRepository.findByIdWithLock(anyLong())).willReturn(Optional.of(mockShop));
        given(shopSubscriptionRepository.findByShopId(anyLong())).willReturn(Optional.empty()); // 💡 구독 비활성 스텁

        assertThatThrownBy(() -> itemService.update(userId, itemId, request))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("구독이 활성화된 가게의 상품만 수정할 수 있습니다.");
    }
}
