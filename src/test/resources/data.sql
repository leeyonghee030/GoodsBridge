-- ==========================================
-- 0. 카테고리 데이터 (공통)
-- ==========================================
INSERT INTO category_tb (name) VALUES ('굿즈/피규어');
INSERT INTO category_tb (name) VALUES ('의류');
INSERT INTO category_tb (name) VALUES ('도서');
INSERT INTO category_tb (name) VALUES ('가전');


-- ==========================================
-- 1. 유저 데이터 (공통)
-- ==========================================
INSERT INTO user_tb (email, password, nickname, role, status, login_type, created_at)
VALUES ('owner-test@test.com', 'test-password', '가게사장', 'USER', 'ACTIVE', 'EMAIL', NOW());

INSERT INTO user_tb (email, password, nickname, role, status, login_type, created_at)
VALUES ('admin-test@test.com', 'test-password', '관리자', 'ADMIN', 'ACTIVE', 'EMAIL', NOW());


-- ==========================================
-- 2. 가게 데이터 (Shop)
-- ==========================================
INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '홍대 굿즈샵', '서울', '서울특별시 마포구 양화로', 'APPROVED', NOW());

INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '부산 피규어 창고', '부산', '부산광역시 해운대구', 'PENDING', NOW());


-- ==========================================
-- 3. 아이템 데이터 (Item)
-- [수정] 모든 상품은 shop_id가 필수이므로 null 데이터를 모두 제거하고 유효한 샵에 매핑합니다.
-- ==========================================

-- [홍대 굿즈샵 소속 상품 (shop_id = 1)] - 활성 및 구독 연동 테스트용
INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 1L, '한정판 피규어', '미개봉 새상품입니다.', 45000, 'NORMAL', 'ON_SALE', 'https://example.com/fig.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 2L, '후드티 A', '편안한 후드티', 30000, 'NORMAL', 'ON_SALE', 'https://example.com/hoodie.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 1L, '홍대 굿즈샵 한정 피규어', '가게 구독 상태에 따라 판매 상태가 연동되는 상품입니다.', 55000, 'NORMAL', 'CLOSED', 'https://example.com/shop1_fig.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 2L, '홍대 굿즈샵 시그니처 후드티', '가게 활성 상태 상품입니다.', 35000, 'NORMAL', 'ON_SALE', 'https://example.com/shop1_hoodie.jpg', NOW());


-- ==========================================
-- 4. 가게 구독 데이터 (ShopSubscription)
-- ==========================================
INSERT INTO shop_subscription_tb
(shop_id, payment_id, setup_fee_paid, start_date, end_date, status, created_at)
VALUES
    (1, 'PAY_TEST_SUBSCRIPTION_001', true, CURRENT_DATE(),
     DATEADD('DAY', 30, CURRENT_DATE()), 'ACTIVE', NOW());
