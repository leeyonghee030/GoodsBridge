-- Test fixture shared by integration tests.
-- Keep insertion order stable because several tests intentionally use these IDs.

-- Categories: 1=굿즈/피규어, 2=의류, 3=도서, 4=가전
INSERT INTO category_tb (name) VALUES ('굿즈/피규어');
INSERT INTO category_tb (name) VALUES ('의류');
INSERT INTO category_tb (name) VALUES ('도서');
INSERT INTO category_tb (name) VALUES ('가전');

-- Users: 1=shop owner, 2=admin and second seller used by exchange tests
INSERT INTO user_tb
    (email, password, nickname, role, status, login_type, created_at)
VALUES
    ('owner-test@test.com', 'test-password', '가게사장', 'USER', 'ACTIVE', 'EMAIL', NOW());
INSERT INTO user_tb
    (email, password, nickname, role, status, login_type, created_at)
VALUES
    ('admin-test@test.com', 'test-password', '관리자', 'ADMIN', 'ACTIVE', 'EMAIL', NOW());

-- Shops: 1 is approved and used by auction/item tests.
INSERT INTO shop_tb
    (owner_user_id, name, region, address, status, created_at)
VALUES
    (1, '홍대 굿즈샵', '서울', '서울특별시 마포구 양화로', 'APPROVED', NOW());
INSERT INTO shop_tb
    (owner_user_id, name, region, address, status, created_at)
VALUES
    (1, '부산 피규어 창고', '부산', '부산광역시 해운대구', 'PENDING', NOW());

-- Items: IDs 1..5 are intentional.
-- 3 is seller 2/shop 2 for exchange and auction rejection tests.
-- 4 is closed; 5 is the active auction/concurrency fixture.
INSERT INTO item_tb
    (seller_user_id, shop_id, category_id, title, description, price,
     sale_type, status, image_url, created_at)
VALUES
    (1, 1, 1, '한정판 피규어', '미개봉 새상품입니다.', 45000,
     'NORMAL', 'ON_SALE', 'https://example.com/fig.jpg', NOW());
INSERT INTO item_tb
    (seller_user_id, shop_id, category_id, title, description, price,
     sale_type, status, image_url, created_at)
VALUES
    (1, 1, 2, '후드티 A', '편안한 후드티', 30000,
     'NORMAL', 'ON_SALE', 'https://example.com/hoodie.jpg', NOW());
INSERT INTO item_tb
    (seller_user_id, shop_id, category_id, title, description, price,
     sale_type, status, image_url, created_at)
VALUES
    (2, 2, 3, '교환용 도서', '교환 테스트용 상품입니다.', 20000,
     'NORMAL', 'ON_SALE', 'https://example.com/book.jpg', NOW());
INSERT INTO item_tb
    (seller_user_id, shop_id, category_id, title, description, price,
     sale_type, status, image_url, created_at)
VALUES
    (1, 1, 1, '홍대 굿즈샵 한정 피규어', '판매 종료 테스트용 상품입니다.', 55000,
     'NORMAL', 'CLOSED', 'https://example.com/closed.jpg', NOW());
INSERT INTO item_tb
    (seller_user_id, shop_id, category_id, title, description, price,
     sale_type, status, image_url, created_at)
VALUES
    (1, 1, 2, '경매용 시그니처 후드티', '경매 및 동시성 테스트용 상품입니다.', 35000,
     'AUCTION', 'ON_SALE', 'https://example.com/auction.jpg', NOW());

-- One active subscription for the approved seed shop.
INSERT INTO shop_subscription_tb
    (shop_id, payment_id, setup_fee_paid, start_date, end_date, status, created_at)
VALUES
    (1, 'PAY_TEST_SUBSCRIPTION_001', TRUE, CURRENT_DATE(),
     DATEADD('DAY', 30, CURRENT_DATE()), 'ACTIVE', NOW());
