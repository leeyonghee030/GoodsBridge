-- 1. 카테고리 데이터 삽입
INSERT INTO category_tb (name) VALUES ('굿즈/피규어');
INSERT INTO category_tb (name) VALUES ('의류');
INSERT INTO category_tb (name) VALUES ('도서');
INSERT INTO category_tb (name) VALUES ('가전');

-- 2. 아이템 데이터 삽입 (created_at에 NOW() 추가)
INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, null, 1L, '한정판 피규어', '미개봉 새상품입니다.', 45000, 'NORMAL', 'ON_SALE', 'https://example.com/fig.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, null, 2L, '후드티 A', '편안한 후드티', 30000, 'NORMAL', 'ON_SALE', 'https://example.com/hoodie.jpg', NOW());

INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (2L, null, 3L, '자바의 정석', '상태 깨끗합니다.', 30000, 'NORMAL', 'ON_SALE', 'https://example.com/book.jpg', NOW());

-- 3. 유저 데이터 삽입 (가게 소유주/관리자 권한 테스트용)
INSERT INTO user_tb (kakao_id, nickname, role, status, created_at)
VALUES ('owner-test-kakao-id', '가게사장', 'USER', 'ACTIVE', NOW());

INSERT INTO user_tb (kakao_id, nickname, role, status, created_at)
VALUES ('admin-test-kakao-id', '관리자', 'ADMIN', 'ACTIVE', NOW());

-- 4. 가게 데이터 삽입
INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '홍대 굿즈샵', '서울', '서울특별시 마포구 양화로', 'APPROVED', NOW());

INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '부산 피규어 창고', '부산', '부산광역시 해운대구', 'PENDING', NOW());

-- ==========================================
-- 가게(Shop) 및 상품(Item) 연동 테스트 데이터 추가
-- ==========================================

-- 기존 아이템 데이터(item_tb)의 shop_id를 1번('홍대 굿즈샵')으로 매핑하여 가게-상품 관계 형성
-- (참고: 기존 1, 2번 아이템의 shop_id를 null에서 1로 업데이트하거나, 아래와 같이 구독 만료/복구 테스트용 아이템을 추가합니다)

-- 구독이 만료되었을 때 CLOSED 상태가 되는 것을 테스트하기 위한 '홍대 굿즈샵' 소속 상품
INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 1L, '홍대 굿즈샵 한정 피규어', '가게 구독 상태에 따라 판매 상태가 연동되는 상품입니다.', 55000, 'NORMAL', 'CLOSED', 'https://example.com/shop1_fig.jpg', NOW());

-- '홍대 굿즈샵'의 정상 판매 중인 상품
INSERT INTO item_tb (seller_user_id, shop_id, category_id, title, description, price, sale_type, status, image_url, created_at)
VALUES (1L, 1L, 2L, '홍대 굿즈샵 시그니처 후드티', '가게 활성 상태 상품입니다.', 35000, 'NORMAL', 'ON_SALE', 'https://example.com/shop1_hoodie.jpg', NOW());

-- ==========================================
-- 가게 구독(ShopSubscription) 테스트 데이터 추가
-- ==========================================

-- '홍대 굿즈샵'(shop_id = 1)의 초기 구독 데이터 (ACTIVE 상태)
INSERT INTO shop_subscription_tb (shop_id, setup_fee_paid, start_date, end_date, status, created_at)
VALUES (1, true, CURRENT_DATE(), DATEADD('DAY', 30, CURRENT_DATE()), 'ACTIVE', NOW());
