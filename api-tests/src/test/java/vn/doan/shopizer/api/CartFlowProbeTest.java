package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("probe")
class CartFlowProbeTest extends ApiBase {

    private final ObjectMapper mapper = new ObjectMapper();

    private void show(String label, Response r) {
        System.out.println(">>> [" + label + "] status=" + r.statusCode() + " body=" + r.asString());
    }

    private Response postCart(Object body) {
        try {
            String json = (body instanceof String) ? (String) body : mapper.writeValueAsString(body);
            return given()
                .contentType(ContentType.JSON)
                .body(json)
            .when()
                .post("/api/v1/cart");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void probeCartOperations() {
        CatalogFixture fixture = new CatalogFixture(adminToken());
        String validSku = null;
        String cartCode = null;

        try {
            // 1. Tạo Category và Product tiền đề: giá 25.50, tồn kho 10
            var cat = fixture.createCategory();
            var prod = fixture.createProduct(cat.id, cat.code, BigDecimal.valueOf(25.50), 10);
            validSku = prod.sku;
            System.out.println(">>> [SETUP] Created product SKU=" + validSku + " (price=25.50, stock=10)");

            // 2. Thăm dò ca hợp lệ: thêm vào giỏ số lượng 2
            Map<String, Object> validItem = new LinkedHashMap<>();
            validItem.put("product", validSku);
            validItem.put("quantity", 2);

            Response addValidResp = postCart(validItem);
            show("PROBE 1 - ADD_CART_QTY_2", addValidResp);

            try {
                cartCode = addValidResp.jsonPath().getString("code");
                if (cartCode != null) {
                    // Đăng ký dòng giỏ hàng vào fixture để cleanup
                    fixture.registerCartItem(cartCode, validSku);

                    Response getCartResp = given().when().get("/api/v1/cart/" + cartCode);
                    show("PROBE 1 - GET_CART", getCartResp);

                    Response getCartTotalResp = given().when().get("/api/v1/cart/" + cartCode + "/total");
                    show("PROBE 1 - GET_CART_TOTAL", getCartTotalResp);
                }
            } catch (Exception e) {
                System.err.println(">>> [WARNING] Không trích xuất được cart code: " + e.getMessage());
            }

            // 3. Thăm dò ca tiêu cực: số lượng 0
            Map<String, Object> zeroQtyItem = new LinkedHashMap<>();
            zeroQtyItem.put("product", validSku);
            zeroQtyItem.put("quantity", 0);

            Response zeroQtyResp = postCart(zeroQtyItem);
            show("PROBE 2 - ZERO_QUANTITY", zeroQtyResp);

            // 4. Thăm dò ca tiêu cực: số lượng âm (-1)
            Map<String, Object> negQtyItem = new LinkedHashMap<>();
            negQtyItem.put("product", validSku);
            negQtyItem.put("quantity", -1);

            Response negQtyResp = postCart(negQtyItem);
            show("PROBE 3 - NEGATIVE_QUANTITY", negQtyResp);

            // 5. Thăm dò ca tiêu cực: SKU không tồn tại
            Map<String, Object> nonExistSkuItem = new LinkedHashMap<>();
            nonExistSkuItem.put("product", "AUTOTEST-NONEXISTENT-SKU");
            nonExistSkuItem.put("quantity", 1);

            Response nonExistSkuResp = postCart(nonExistSkuItem);
            show("PROBE 4 - NON_EXISTENT_SKU", nonExistSkuResp);

        } finally {
            // Dọn dẹp an toàn LIFO: Cart item -> Product -> Category
            fixture.cleanup();
            if (!fixture.leftovers().isEmpty()) {
                System.err.println(">>> [WARNING] Leftovers sau probe: " + fixture.leftovers());
            }
        }
    }
}
