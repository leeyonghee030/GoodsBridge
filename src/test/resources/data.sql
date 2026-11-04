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

-- 3. 가게 데이터 삽입
INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '홍대 굿즈샵', '서울', '서울특별시 마포구 양화로', 'APPROVED', NOW());

INSERT INTO shop_tb (owner_user_id, name, region, address, status, created_at)
VALUES (1, '부산 피규어 창고', '부산', '부산광역시 해운대구', 'PENDING', NOW());
