-- develop 기준 테스트 fixture
-- 테스트가 고정 ID를 사용하므로 INSERT 순서를 유지한다.

-- 0. 카테고리: 1=굿즈/피규어, 2=의류, 3=도서, 4=가전
INSERT INTO category_tb (name) VALUES ('굿즈/피규어');
INSERT INTO category_tb (name) VALUES ('의류');
INSERT INTO category_tb (name) VALUES ('도서');
INSERT INTO category_tb (name) VALUES ('가전');

-- 1. 사용자: 1=가게 주인, 2=관리자/교환 테스트 판매자
INSERT INTO user_tb (email, password, nickname, role, status, login_type, created_at)
VALUES ('owner-test@test.com', 'test-password', '가게사장', 'USER', 'ACTIVE', 'EMAIL', NOW());

INSERT INTO user_tb (email, password, nickname, role, status, login_type, created_at)
VALUES ('admin-test@test.com', 'test-password', '관리자', 'ADMIN', 'ACTIVE', 'EMAIL', NOW());

-- 2. 가게: 1번은 승인된 가게, 2번은 별도 가게
INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '홍대 굿즈샵', '서울', '서울특별시 마포구 양화로', 'APPROVED', NOW());

INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '부산 피규어 창고', '부산', '부산광역시 해운대구', 'PENDING', NOW());

-- 3. 상품
-- 1, 2: develop 기본 상품
-- 3: 교환/경매 거부 테스트용(판매자 2, 2번 가게)
-- 4: develop 기본 종료 상품
-- 5: 경매 및 동시성 테스트용 진행 상품

INSERT INTO item_tb (
    seller_user_id, shop_id, category_id,
    title, description, price, sale_type,
    status, image_url, created_at
)
VALUES (
    1, 1, 1, '한정판 피규어', '미개봉 새상품입니다.', 45000,
    'NORMAL', 'ON_SALE', 'https://example.com/fig.jpg', NOW()
);

INSERT INTO item_tb (
    seller_user_id, shop_id, category_id,
    title, description, price, sale_type,
    status, image_url, created_at
)
VALUES (
    1, 1, 2, '후드티 A', '편안한 후드티', 30000,
    'NORMAL', 'ON_SALE', 'https://example.com/hoodie.jpg', NOW()
);

INSERT INTO item_tb (
    seller_user_id, shop_id, category_id,
    title, description, price, sale_type,
    status, image_url, created_at
)
VALUES (
    2, 2, 3, '교환용 도서', '교환 테스트용 상품입니다.', 20000,
    'NORMAL', 'ON_SALE', 'https://example.com/book.jpg', NOW()
);

INSERT INTO item_tb (
    seller_user_id, shop_id, category_id,
    title, description, price, sale_type,
    status, image_url, created_at
)
VALUES (
    1, 1, 1, '홍대 굿즈샵 한정 피규어',
    '가게 구독 상태에 따라 판매 상태가 연동되는 상품입니다.', 55000,
   'NORMAL', 'CLOSED', 'https://example.com/shop1_fig.jpg', NOW()
);

INSERT INTO item_tb (
    seller_user_id, shop_id, category_id,
    title, description, price, sale_type,
    status, image_url, created_at
)
VALUES (
    1, 1, 2, '경매용 시그니처 후드티',
    '경매 및 동시성 테스트용 상품입니다.', 35000,
   'AUCTION', 'ON_SALE', 'https://example.com/auction.jpg', NOW()
);

-- 4. 가게 구독
INSERT INTO shop_subscription_tb
    (shop_id, payment_id, setup_fee_paid, start_date, end_date, status, created_at)
VALUES
    (1, 'PAY_TEST_SUBSCRIPTION_001', TRUE, CURRENT_DATE(),
     DATEADD('DAY', 30, CURRENT_DATE()), 'ACTIVE', NOW());
