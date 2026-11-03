package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixture quản lý vòng đời tạo và dọn dẹp dữ liệu Danh mục, Sản phẩm và Giỏ hàng.
 * Đảm bảo tính độc lập giữa các test case và không làm bẩn CSDL.
 *
 * Lưu ý: Giỏ hàng tạo ra không bị xóa (chưa thấy API hỗ trợ xóa giỏ hàng),
 * chỉ xóa các dòng sản phẩm trong giỏ hàng thông qua DELETE /api/v1/cart/{code}/product/{sku}.
 */
public class CatalogFixture implements AutoCloseable {

    private static final AtomicLong SEQ = new AtomicLong(0);
    private static final BigDecimal DEFAULT_PRICE = BigDecimal.valueOf(19.99);
    private static final int DEFAULT_STOCK = 10;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String authHeader;

    // Quản lý thứ tự xóa (LIFO): Cart Item -> Product -> Category
    private final List<CartItemRef> createdCartItems = new ArrayList<>();
    private final Deque<Long> createdProductIds = new ArrayDeque<>();
    private final Deque<Long> createdCategoryIds = new ArrayDeque<>();
    private final List<String> leftovers = new ArrayList<>();

    public static class TestCategory {
        public final Long id;
        public final String code;

        public TestCategory(Long id, String code) {
            this.id = id;
            this.code = code;
        }
    }

    public static class TestProduct {
        public final Long id;
        public final String sku;
        public final Long categoryId;
        public final BigDecimal price;
        public final int stock;

        public TestProduct(Long id, String sku, Long categoryId, BigDecimal price, int stock) {
            this.id = id;
            this.sku = sku;
            this.categoryId = categoryId;
            this.price = price;
            this.stock = stock;
        }
    }

    private static class CartItemRef {
        final String cartCode;
        final String sku;

        CartItemRef(String cartCode, String sku) {
            this.cartCode = cartCode;
            this.sku = sku;
        }
    }

    public CatalogFixture(String adminToken) {
        ensureBaseUri();
        this.authHeader = "Bearer " + adminToken;
    }

    private static void ensureBaseUri() {
        if (RestAssured.baseURI == null || "http://localhost".equals(RestAssured.baseURI)) {
            RestAssured.baseURI = System.getProperty("api.baseUrl", "http://localhost:8080");
        }
    }

    private String generateSuffix() {
        return Long.toString(System.currentTimeMillis(), 36) + "-" + SEQ.incrementAndGet();
    }

    public TestCategory createCategory() {
        return createCategory(null);
    }

    public TestCategory createCategory(String customSuffix) {
        String suffix = (customSuffix != null && !customSuffix.isBlank()) ? customSuffix : generateSuffix();
        String code = "AUTOTEST-CAT-" + suffix;
        String friendlyUrl = ("autotest-cat-" + suffix).toLowerCase(Locale.ROOT);

        Map<String, Object> catBody = new LinkedHashMap<>();
        catBody.put("code", code);
        catBody.put("visible", true);
        catBody.put("featured", false);
        catBody.put("sortOrder", 1);
        catBody.put("descriptions", List.of(Map.of(
            "language", "en",
            "name", "Autotest Category " + suffix,
            "friendlyUrl", friendlyUrl,
            "title", "Autotest Category"
        )));

        Response resp = given()
            .contentType(ContentType.JSON)
            .header("Authorization", authHeader)
            .body(toJson(catBody))
        .when()
            .post("/api/v1/private/category");

        Long catId = null;
        try {
            catId = resp.jsonPath().getLong("id");
        } catch (Exception ignored) {
        }

        if (catId == null || catId <= 0 || (resp.statusCode() != 200 && resp.statusCode() != 201)) {
            throw new IllegalStateException("Failed to create category (status=" + resp.statusCode()
                    + ", id=" + catId + "): body=" + resp.asString());
        }

        // Ghi nhận ID ngay sau khi tạo
        createdCategoryIds.push(catId);

        return new TestCategory(catId, code);
    }

    public TestProduct createProduct(Long categoryId, String categoryCode) {
        return createProduct(categoryId, categoryCode, DEFAULT_PRICE, DEFAULT_STOCK, null);
    }

    public TestProduct createProduct(Long categoryId, String categoryCode, BigDecimal price, int stock) {
        return createProduct(categoryId, categoryCode, price, stock, null);
    }

    public TestProduct createProduct(Long categoryId, String categoryCode, BigDecimal price, int stock, String customSuffix) {
        String suffix = (customSuffix != null && !customSuffix.isBlank()) ? customSuffix : generateSuffix();
        String sku = "AUTOTEST-SKU-" + suffix;
        String friendlyUrl = ("autotest-product-" + suffix).toLowerCase(Locale.ROOT);

        List<Map<String, Object>> catRef = (categoryId == null)
            ? List.of()
            : List.of(Map.of("id", categoryId, "code", categoryCode != null ? categoryCode : ""));

        Map<String, Object> priceNode = new LinkedHashMap<>();
        priceNode.put("price", price);
        priceNode.put("defaultPrice", true);

        Map<String, Object> inventoryNode = new LinkedHashMap<>();
        inventoryNode.put("sku", sku);
        inventoryNode.put("quantity", stock);
        inventoryNode.put("price", priceNode);

        Map<String, Object> prodBody = new LinkedHashMap<>();
        prodBody.put("sku", sku);
        prodBody.put("price", price);
        prodBody.put("quantity", stock);
        prodBody.put("visible", true);
        prodBody.put("available", true);
        prodBody.put("productShipeable", false);
        // Dùng ngày hôm qua để tránh sai lệch múi giờ giữa máy client và server
        prodBody.put("dateAvailable", LocalDate.now().minusDays(1).toString());
        prodBody.put("sortOrder", 1);
        prodBody.put("type", "GENERAL");
        prodBody.put("categories", catRef);
        prodBody.put("descriptions", List.of(Map.of(
            "language", "en",
            "name", "Autotest Product " + suffix,
            "friendlyUrl", friendlyUrl,
            "title", "Autotest Product"
        )));
        prodBody.put("inventory", inventoryNode);

        Response resp = given()
            .contentType(ContentType.JSON)
            .header("Authorization", authHeader)
            .body(toJson(prodBody))
        .when()
            .post("/api/v1/private/product");

        Long prodId = null;
        try {
            prodId = resp.jsonPath().getLong("id");
        } catch (Exception ignored) {
        }

        if (prodId == null || prodId <= 0 || (resp.statusCode() != 200 && resp.statusCode() != 201)) {
            throw new IllegalStateException("Failed to create product (status=" + resp.statusCode()
                    + ", id=" + prodId + "): body=" + resp.asString());
        }

        // Ghi nhận ID ngay sau khi tạo
        createdProductIds.push(prodId);

        return new TestProduct(prodId, sku, categoryId, price, stock);
    }

    /**
     * Đăng ký dòng giỏ hàng để dọn dẹp trước khi xóa sản phẩm.
     * Lưu ý: Giỏ hàng tạo ra không bị xóa (chưa có API), chỉ xóa dòng trong giỏ.
     */
    public void registerCartItem(String cartCode, String sku) {
        if (cartCode != null && sku != null) {
            createdCartItems.add(new CartItemRef(cartCode, sku));
        }
    }

    /**
     * Dọn dẹp dữ liệu theo thứ tự LIFO: Dòng giỏ hàng -> Sản phẩm -> Danh mục.
     * Không ném ngoại lệ làm test fail.
     * Ghi nhận kết quả bất thường vào danh sách leftovers.
     */
    public void cleanup() {
        // 1. Thứ tự 1: Xóa dòng giỏ hàng
        for (CartItemRef item : createdCartItems) {
            try {
                Response r = given().when().delete("/api/v1/cart/" + item.cartCode + "/product/" + item.sku);
                if (r.statusCode() != 200 && r.statusCode() != 204) {
                    leftovers.add("cart item " + item.cartCode + " sku " + item.sku + " -> " + r.statusCode());
                }
            } catch (Exception e) {
                leftovers.add("cart item " + item.cartCode + " sku " + item.sku + " -> exception: " + e.getMessage());
                System.err.println(">>> [WARNING] Cleanup cart item failed: " + e.getMessage());
            }
        }
        createdCartItems.clear();

        // 2. Thứ tự 2: Xóa sản phẩm
        while (!createdProductIds.isEmpty()) {
            Long prodId = createdProductIds.pop();
            try {
                Response r = given().header("Authorization", authHeader).when().delete("/api/v1/private/product/" + prodId);
                if (r.statusCode() != 200 && r.statusCode() != 204) {
                    leftovers.add("product " + prodId + " -> " + r.statusCode());
                }
            } catch (Exception e) {
                leftovers.add("product " + prodId + " -> exception: " + e.getMessage());
                System.err.println(">>> [WARNING] Cleanup product " + prodId + " failed: " + e.getMessage());
            }
        }

        // 3. Thứ tự 3: Xóa danh mục
        while (!createdCategoryIds.isEmpty()) {
            Long catId = createdCategoryIds.pop();
            try {
                Response r = given().header("Authorization", authHeader).when().delete("/api/v1/private/category/" + catId);
                if (r.statusCode() != 200 && r.statusCode() != 204) {
                    leftovers.add("category " + catId + " -> " + r.statusCode());
                }
            } catch (Exception e) {
                leftovers.add("category " + catId + " -> exception: " + e.getMessage());
                System.err.println(">>> [WARNING] Cleanup category " + catId + " failed: " + e.getMessage());
            }
        }
    }

    public List<String> leftovers() {
        return Collections.unmodifiableList(new ArrayList<>(leftovers));
    }

    @Override
    public void close() {
        cleanup();
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization error", e);
        }
    }
}
