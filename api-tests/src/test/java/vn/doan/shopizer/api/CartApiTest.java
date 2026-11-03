package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("api")
@Tag("cart")
class CartApiTest extends ApiBase {

    private Response postCart(String sku, int quantity) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of("product", sku, "quantity", quantity))
        .when()
            .post("/api/v1/cart");
    }

    @Test
    @Tag("AT-CRT-01")
    @DisplayName("AT-CRT-01: Thêm sản phẩm hợp lệ vào giỏ hàng, kiểm tra tổng tiền và số lượng")
    void addToCart_validProduct_createsCartWithCorrectTotal() {
        CatalogFixture fixture = new CatalogFixture(adminToken());
        try {
            var cat = fixture.createCategory();
            var prod = fixture.createProduct(cat.id, cat.code, BigDecimal.valueOf(25.50), 10);

            Response resp = postCart(prod.sku, 2);
            assertThat(resp.statusCode()).isEqualTo(201);

            String cartCode = resp.jsonPath().getString("code");
            assertThat(cartCode).isNotNull().isNotBlank();
            fixture.registerCartItem(cartCode, prod.sku);

            BigDecimal total = new BigDecimal(resp.jsonPath().getString("total"));
            assertThat(total)
                .as("Tổng tiền giỏ hàng phải bằng 25.50 x 2 = 51.00")
                .isEqualByComparingTo(BigDecimal.valueOf(51.00));

            int quantity = resp.jsonPath().getInt("quantity");
            assertThat(quantity).isEqualTo(2);

            Response getCartResp = given().when().get("/api/v1/cart/" + cartCode);
            assertThat(getCartResp.statusCode()).isEqualTo(200);

            BigDecimal getTotal = new BigDecimal(getCartResp.jsonPath().getString("total"));
            assertThat(getTotal)
                .as("Tổng tiền giỏ hàng khi GET lại phải khớp 51.00")
                .isEqualByComparingTo(BigDecimal.valueOf(51.00));

            assertThat(getCartResp.jsonPath().getInt("quantity")).isEqualTo(2);
        } finally {
            fixture.cleanup();
            assertThat(fixture.leftovers())
                .as("Dọn dẹp giỏ hàng, sản phẩm và danh mục phải thành công không còn sót")
                .isEmpty();
        }
    }

    @Test
    @Tag("AT-CRT-02")
    @Tag("negative")
    @DisplayName("AT-CRT-02: Thêm sản phẩm với SKU không tồn tại vào giỏ hàng")
    void addToCart_nonExistentSku_returns400() {
        Response resp = postCart("AUTOTEST-NONEXISTENT-SKU", 1);
        assertThat(resp.statusCode()).isEqualTo(400);

        String message = resp.jsonPath().getString("message");
        assertThat(message)
            .as("Thông báo lỗi phải nêu rõ không tìm thấy SKU")
            .contains("Cannot get product with sku [AUTOTEST-NONEXISTENT-SKU]");
    }

    /**
     * AT-CRT-03: Thêm sản phẩm với số lượng bằng 0 vào giỏ hàng.
     * Kỳ vọng đúng theo đặc tả nghiệp vụ là hệ thống từ chối (HTTP 4xx).
     * Đây là hành vi thực tế hiện tại của Shopizer 3.2.5: chấp nhận số lượng 0 và tạo giỏ hàng có tổng tiền 0.00.
     * Điểm quan sát P-08 (mức độ chưa xác minh: CHƯA XÁC MINH checkout có từ chối hay không).
     */
    @Test
    @Tag("AT-CRT-03")
    @Tag("known-issue")
    @Tag("P-08")
    @DisplayName("AT-CRT-03: Thêm sản phẩm với số lượng 0 (Điểm quan sát P-08)")
    void cart_withZeroQuantity_isAcceptedKnownIssue() {
        CatalogFixture fixture = new CatalogFixture(adminToken());
        try {
            var cat = fixture.createCategory();
            var prod = fixture.createProduct(cat.id, cat.code, BigDecimal.valueOf(25.50), 10);

            Response resp = postCart(prod.sku, 0);

            String cartCode = resp.jsonPath().getString("code");
            if (cartCode != null) {
                fixture.registerCartItem(cartCode, prod.sku);
            }

            assertThat(resp.statusCode())
                .as("KNOWN ISSUE P-08 - cập nhật khi Shopizer sửa lỗi: mã phản hồi khi thêm SL 0")
                .isEqualTo(201);

            BigDecimal total = new BigDecimal(resp.jsonPath().getString("total"));
            assertThat(total)
                .as("KNOWN ISSUE P-08 - cập nhật khi Shopizer sửa lỗi: tổng tiền giỏ hàng khi thêm SL 0")
                .isEqualByComparingTo(BigDecimal.ZERO);

            int quantity = resp.jsonPath().getInt("quantity");
            assertThat(quantity)
                .as("KNOWN ISSUE P-08 - cập nhật khi Shopizer sửa lỗi: tổng số lượng giỏ hàng khi thêm SL 0")
                .isEqualTo(0);
        } finally {
            fixture.cleanup();
            assertThat(fixture.leftovers())
                .as("Dọn dẹp giỏ hàng SL 0, sản phẩm và danh mục phải thành công không còn sót")
                .isEmpty();
        }
    }

    /**
     * AT-CRT-04: Thêm sản phẩm với số lượng âm vào giỏ hàng.
     * Kỳ vọng đúng theo đặc tả nghiệp vụ là hệ thống từ chối (HTTP 4xx).
     * Đây là hành vi thực tế hiện tại của Shopizer 3.2.5: chấp nhận số lượng âm (-1) và tạo giỏ hàng có tổng tiền âm (-25.50).
     * Điểm quan sát P-08 (mức độ chưa xác minh: CHƯA XÁC MINH checkout có từ chối hay không).
     */
    @Test
    @Tag("AT-CRT-04")
    @Tag("known-issue")
    @Tag("P-08")
    @DisplayName("AT-CRT-04: Thêm sản phẩm với số lượng âm (Điểm quan sát P-08)")
    void cart_withNegativeQuantity_isAcceptedKnownIssue() {
        CatalogFixture fixture = new CatalogFixture(adminToken());
        try {
            var cat = fixture.createCategory();
            var prod = fixture.createProduct(cat.id, cat.code, BigDecimal.valueOf(25.50), 10);

            Response resp = postCart(prod.sku, -1);

            String cartCode = resp.jsonPath().getString("code");
            if (cartCode != null) {
                fixture.registerCartItem(cartCode, prod.sku);
            }

            assertThat(resp.statusCode())
                .as("KNOWN ISSUE P-08 - cập nhật khi Shopizer sửa lỗi: mã phản hồi khi thêm SL âm")
                .isEqualTo(201);

            BigDecimal total = new BigDecimal(resp.jsonPath().getString("total"));
            assertThat(total)
                .as("KNOWN ISSUE P-08 - cập nhật khi Shopizer sửa lỗi: tổng tiền giỏ hàng khi thêm SL âm")
                .isEqualByComparingTo(BigDecimal.valueOf(-25.50));

            int quantity = resp.jsonPath().getInt("quantity");
            assertThat(quantity)
                .as("KNOWN ISSUE P-08 - cập nhật khi Shopizer sửa lỗi: tổng số lượng giỏ hàng khi thêm SL âm")
                .isEqualTo(-1);
        } finally {
            fixture.cleanup();
            assertThat(fixture.leftovers())
                .as("Dọn dẹp giỏ hàng SL âm, sản phẩm và danh mục phải thành công không còn sót")
                .isEmpty();
        }
    }
}
