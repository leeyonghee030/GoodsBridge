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
INSERT INTO user_tb (kakao_id, nickname, role, status, created_at)
VALUES ('owner-test-kakao-id', '가게사장', 'USER', 'ACTIVE', NOW());

INSERT INTO user_tb (kakao_id, nickname, role, status, created_at)
VALUES ('admin-test-kakao-id', '관리자', 'ADMIN', 'ACTIVE', NOW());


-- ==========================================
-- 2. 가게 데이터 (Shop)
-- ==========================================
INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '홍대 굿즈샵', '서울', '서울특별시 마포구 양화로', 'APPROVED', NOW());

INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '부산 피규어 창고', '부산', '부산광역시 해운대구', 'PENDING', NOW());


-- ==========================================
-- 3. 아이템 데이터 (Item)
-- [주의] ItemRepositoryTest / ItemServiceTest의 조회 검증과 연동됩니다.
-- ==========================================

-- [개인 판매 상품 (shop_id = null)] - 단건 조회 및 기본 필터링 테스트용
INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, null, 1L, '한정판 피규어', '미개봉 새상품입니다.', 45000, 'NORMAL', 'ON_SALE', 'https://example.com/fig.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, null, 2L, '후드티 A', '편안한 후드티', 30000, 'NORMAL', 'ON_SALE', 'https://example.com/hoodie.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (2L, null, 3L, '자바의 정석', '상태 깨끗합니다.', 30000, 'NORMAL', 'ON_SALE', 'https://example.com/book.jpg', NOW());


-- [가게 소속 상품 (shop_id = 1, 홍대 굿즈샵)] - 구독 만료/복구 연동 테스트용
INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 1L, '홍대 굿즈샵 한정 피규어', '가게 구독 상태에 따라 판매 상태가 연동되는 상품입니다.', 55000, 'NORMAL', 'CLOSED', 'https://example.com/shop1_fig.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 2L, '홍대 굿즈샵 시그니처 후드티', '가게 활성 상태 상품입니다.', 35000, 'NORMAL', 'ON_SALE', 'https://example.com/shop1_hoodie.jpg', NOW());


-- ==========================================
-- 4. 가게 구독 데이터 (ShopSubscription)
-- ==========================================
INSERT INTO shop_subscription_tb (shop_id, setup_fee_paid, start_date, end_date, status, created_at)
VALUES (1, true, CURRENT_DATE(), DATEADD('DAY', 30, CURRENT_DATE()), 'ACTIVE', NOW());
