package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("api")
@Tag("fixture")
class CatalogFixtureTest extends ApiBase {

    private static class IntendedTestException extends RuntimeException {
        IntendedTestException(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("a) Xác nhận danh mục và sản phẩm có mặt trước cleanup, vắng mặt sau cleanup (tìm theo code và sku cụ thể)")
    void createAndCleanup_verifiesPresenceBeforeAndAbsenceAfter() {
        CatalogFixture fixture = new CatalogFixture(adminToken());

        var cat = fixture.createCategory();
        assertThat(cat.id).isNotNull().isGreaterThan(0L);
        assertThat(cat.code).startsWith("AUTOTEST-CAT-");

        var prod = fixture.createProduct(cat.id, cat.code, BigDecimal.valueOf(25.50), 15);
        assertThat(prod.id).isNotNull().isGreaterThan(0L);
        assertThat(prod.sku).startsWith("AUTOTEST-SKU-");
        assertThat(prod.price).isEqualByComparingTo(BigDecimal.valueOf(25.50));
        assertThat(prod.stock).isEqualTo(15);
        assertThat(prod.categoryId).isEqualTo(cat.id);

        // 1. KIỂM TRA TRƯỚC CLEANUP: Bắt buộc phải có mặt cụ thể trong danh sách.
        // Nếu fail, in nguyên văn phản hồi (rút gọn 700 ký tự đầu qua preview()) để phân biệt phân trang / đệm / lỗi thật.
        Response catBefore = given().when().get("/api/v1/category");
        assertThat(catBefore.statusCode()).isEqualTo(200);
        List<String> catCodesBefore = catBefore.jsonPath().getList("categories.code");
        assertThat(catCodesBefore)
            .as("Danh mục '%s' phải có mặt TRƯỚC cleanup. Phản hồi server: %s", cat.code, preview(catBefore.asString()))
            .isNotNull()
            .contains(cat.code);

        Response prodBefore = given().when().get("/api/v1/products");
        assertThat(prodBefore.statusCode()).isEqualTo(200);
        List<String> prodSkusBefore = prodBefore.jsonPath().getList("products.sku");
        assertThat(prodSkusBefore)
            .as("Sản phẩm SKU '%s' phải có mặt TRƯỚC cleanup. Phản hồi server: %s", prod.sku, preview(prodBefore.asString()))
            .isNotNull()
            .contains(prod.sku);

        // 2. THỰC HIỆN DỌN DẸP
        fixture.cleanup();

        // Xác nhận không có lỗi dọn dẹp nào bị sót lại
        assertThat(fixture.leftovers())
            .as("Danh sách leftovers sau khi cleanup phải rỗng")
            .isEmpty();

        // 3. KIỂM TRA SAU CLEANUP: Bắt buộc isNotNull() rồi doesNotContain(...), không dùng if != null
        Response catAfter = given().when().get("/api/v1/category");
        assertThat(catAfter.statusCode()).isEqualTo(200);
        List<String> catCodesAfter = catAfter.jsonPath().getList("categories.code");
        assertThat(catCodesAfter)
            .as("Danh sách categories.code không được null sau cleanup")
            .isNotNull()
            .doesNotContain(cat.code);

        Response prodAfter = given().when().get("/api/v1/products");
        assertThat(prodAfter.statusCode()).isEqualTo(200);
        List<String> prodSkusAfter = prodAfter.jsonPath().getList("products.sku");
        assertThat(prodSkusAfter)
            .as("Danh sách products.sku không được null sau cleanup")
            .isNotNull()
            .doesNotContain(prod.sku);
    }

    @Test
    @DisplayName("b) Tạo hai sản phẩm liên tiếp không truyền hậu tố, xác nhận cả hai tạo được với hậu tố duy nhất")
    void createConsecutiveProducts_withoutSuffix_bothCreatedSuccessfully() {
        try (CatalogFixture fixture = new CatalogFixture(adminToken())) {
            var cat = fixture.createCategory();

            var prod1 = fixture.createProduct(cat.id, cat.code);
            var prod2 = fixture.createProduct(cat.id, cat.code);

            assertThat(prod1.id).isNotNull().isGreaterThan(0L);
            assertThat(prod2.id).isNotNull().isGreaterThan(0L);
            assertThat(prod1.id).isNotEqualTo(prod2.id);

            assertThat(prod1.sku).startsWith("AUTOTEST-SKU-");
            assertThat(prod2.sku).startsWith("AUTOTEST-SKU-");
            assertThat(prod1.sku).isNotEqualTo(prod2.sku);

            // Kiểm tra giá và tồn kho mặc định
            assertThat(prod1.price).isEqualByComparingTo(BigDecimal.valueOf(19.99));
            assertThat(prod1.stock).isEqualTo(10);
            assertThat(prod2.price).isEqualByComparingTo(BigDecimal.valueOf(19.99));
            assertThat(prod2.stock).isEqualTo(10);

            fixture.cleanup();
            assertThat(fixture.leftovers()).isEmpty();
        }
    }

    @Test
    @DisplayName("c) Tình huống lỗi: tạo sản phẩm rồi ném ngoại lệ trong try-with-resources, bắt ngoại lệ rồi xác nhận dữ liệu vẫn được dọn")
    void errorScenario_exceptionInTryWithResources_cleansUpDataProperly() {
        String expectedMessage = "Simulated mid-test failure in scenario c";
        boolean exceptionCaught = false;
        String catCode = null;
        String prodSku = null;

        try (CatalogFixture fixture = new CatalogFixture(adminToken())) {
            var cat = fixture.createCategory();
            catCode = cat.code;
            var prod = fixture.createProduct(cat.id, cat.code);
            prodSku = prod.sku;

            assertThat(prod.id).isNotNull().isGreaterThan(0L);

            // Ném ngoại lệ có chủ đích giữa chừng
            throw new IntendedTestException(expectedMessage);
        } catch (IntendedTestException e) {
            // Bắt ngoại lệ có chủ đích
            assertThat(e.getMessage()).isEqualTo(expectedMessage);
            exceptionCaught = true;
        }

        // Đảm bảo ngoại lệ đã thực sự xảy ra và được bắt trước khi assert cleanup
        assertThat(exceptionCaught)
            .as("Ngoại lệ IntendedTestException có chủ đích phải được bắt thành công")
            .isTrue();

        // Sau khi thoát khỏi khối try-with-resources, xác nhận dữ liệu đã được dọn sạch hoàn toàn (isNotNull rồi doesNotContain)
        Response catResp = given().when().get("/api/v1/category");
        assertThat(catResp.statusCode()).isEqualTo(200);
        List<String> catCodes = catResp.jsonPath().getList("categories.code");
        assertThat(catCodes)
            .as("Danh sách categories.code không được null sau cleanup")
            .isNotNull()
            .doesNotContain(catCode);

        Response prodResp = given().when().get("/api/v1/products");
        assertThat(prodResp.statusCode()).isEqualTo(200);
        List<String> prodSkus = prodResp.jsonPath().getList("products.sku");
        assertThat(prodSkus)
            .as("Danh sách products.sku không được null sau cleanup")
            .isNotNull()
            .doesNotContain(prodSku);
    }
}
