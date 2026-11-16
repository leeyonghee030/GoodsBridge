package com.goodsbridge.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodsbridge.exchange.ExchangeItem;
import com.goodsbridge.exchange.ExchangeItemRepository;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 시연 계정 외에 목록 화면(가게·상품·교환 게시판·문의)을 채우는 샘플 데이터.
// 어느 컴퓨터에서 켜도 목록이 똑같이 길게 보이도록 resources/demo/sample-data.json 에서 읽어 온다.
// 이미 있는 데이터는 건드리지 않고 없는 것만 만든다 (서버를 켤 때마다·초기화할 때마다 호출돼도 안전하다).
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSampleDataService {

    private static final String SAMPLE_FILE = "demo/sample-data.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ExchangeItemRepository exchangeItemRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ShopRepository shopRepository;

    public void seed() {
        JsonNode data = load();
        Map<String, Long> userIds = new HashMap<>();
        for (JsonNode u : data.get("users")) {
            userIds.put(u.get("email").asText(), ensureUser(u.get("email").asText(), u.get("nickname").asText()));
        }

        Map<String, Long> shopIds = new HashMap<>();
        for (JsonNode s : data.get("shops")) {
            long owner = userIds.get(s.get("owner").asText());
            shopIds.put(s.get("name").asText(), ensureShop(s.get("name").asText(), s.get("region").asText(), s.get("address").asText(), owner));
        }

        Map<String, Long> categoryIds = new HashMap<>();
        Map<Integer, Long> itemIds = new HashMap<>();
        for (JsonNode i : data.get("items")) {
            String categoryName = i.get("category").asText();
            Long categoryId = categoryIds.computeIfAbsent(categoryName, this::categoryId);
            if (categoryId == null) {
                continue; // 시연 카테고리가 없으면(가능성 낮음) 그 상품은 건너뛴다
            }
            Long shopId = i.get("shop").isNull() ? null : shopIds.get(i.get("shop").asText());
            long seller = userIds.get(i.get("seller").asText());
            itemIds.put(i.get("key").asInt(), ensureItem(i, seller, shopId, categoryId));
        }

        for (JsonNode e : data.get("exchanges")) {
            Long itemId = itemIds.get(e.get("item").asInt());
            if (itemId != null) {
                ensureExchange(itemId, userIds.get(e.get("owner").asText()), shopIds.get(e.get("shop").asText()), e.get("want").asText());
            }
        }

        Map<Integer, Long> boardIds = new HashMap<>();
        for (JsonNode b : data.get("boards")) {
            Long itemId = b.get("item").isNull() ? null : itemIds.get(b.get("item").asInt());
            boardIds.put(b.get("key").asInt(), ensureBoard(userIds.get(b.get("author").asText()), b.get("title").asText(),
                b.get("content").asText(), itemId, b.get("secret").asBoolean(), b.get("status").asText()));
        }
        for (JsonNode r : data.get("replies")) {
            ensureReply(boardIds.get(r.get("board").asInt()), userIds.get(r.get("author").asText()), r.get("content").asText());
        }
    }

    private JsonNode load() {
        try (InputStream in = new ClassPathResource(SAMPLE_FILE).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("샘플 데이터 파일을 읽지 못했습니다: " + SAMPLE_FILE, e);
        }
    }

    private Long first(String sql, Object... args) {
        List<Long> ids = jdbc.queryForList(sql, Long.class, args);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long categoryId(String name) {
        return first("select min(id) from category_tb where name = ?", name);
    }

    private long ensureUser(String email, String nickname) {
        Long id = first("select id from user_tb where email = ?", email);
        if (id != null) {
            return id;
        }
        jdbc.update("insert into user_tb (created_at, email, login_type, nickname, password, role, status) values (now(6), ?, 'EMAIL', ?, ?, 'USER', 'ACTIVE')",
            email, nickname, passwordEncoder.encode("sample-" + UUID.randomUUID()));
        return first("select id from user_tb where email = ?", email);
    }

    private long ensureShop(String name, String region, String address, long ownerId) {
        Long id = first("select id from shop_tb where name = ? and owner_user_id = ? and deleted = 0", name, ownerId);
        if (id != null) {
            return id;
        }
        jdbc.update("insert into shop_tb (address, created_at, deleted, name, owner_user_id, region, status, version) values (?, now(6), 0, ?, ?, ?, 'APPROVED', 0)",
            address, name, ownerId, region);
        return first("select id from shop_tb where name = ? and owner_user_id = ? and deleted = 0", name, ownerId);
    }

    private long ensureItem(JsonNode i, long sellerId, Long shopId, long categoryId) {
        String title = i.get("title").asText();
        // 판매자가 달라도(내 실제 계정이 만든 같은 상품) 같은 제목·같은 가게면 이미 있는 것으로 본다
        String find = "select min(id) from item_tb where title = ? and shop_id <=> ? and deleted = 0";
        Long id = first(find, title, shopId);
        if (id != null) {
            return id;
        }
        jdbc.update("insert into item_tb (created_at, deleted, description, image_url, price, sale_type, seller_user_id, shop_id, status, title, version, category_id, paused_by_subscription) "
                + "values (now(6), 0, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0)",
            text(i, "description"), text(i, "image"), i.get("price").asInt(), i.get("saleType").asText(), sellerId, shopId,
            i.get("status").asText(), title, categoryId);
        return first(find, title, shopId);
    }

    // 교환 게시글은 id 를 시퀀스로 만들기 때문에 SQL 이 아니라 엔티티로 저장한다
    private void ensureExchange(long itemId, long ownerId, long shopId, String want) {
        if (first("select min(id) from exchange_item_tb where item_id = ?", itemId) != null) {
            return;
        }
        exchangeItemRepository.save(ExchangeItem.builder()
            .user(userRepository.getReferenceById(ownerId))
            .item(itemRepository.getReferenceById(itemId))
            .shop(shopRepository.getReferenceById(shopId))
            .wantDescription(want)
            .build());
    }

    private long ensureBoard(long authorId, String title, String content, Long itemId, boolean secret, String status) {
        String find = "select min(id) from board_tb where title = ? and content = ?";
        Long id = first(find, title, content);
        if (id != null) {
            return id;
        }
        jdbc.update("insert into board_tb (author_id, content, created_at, is_secret, item_id, status, title) values (?, ?, now(6), ?, ?, ?, ?)",
            authorId, content, secret, itemId, status, title);
        return first(find, title, content);
    }

    private void ensureReply(long boardId, long authorId, String content) {
        if (first("select min(id) from reply_tb where board_id = ? and content = ?", boardId, content) != null) {
            return;
        }
        jdbc.update("insert into reply_tb (author_id, content, created_at, board_id) values (?, ?, now(6), ?)", authorId, content, boardId);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
