package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("probe")
class ProductProbeTest extends ApiBase {

    private final ObjectMapper mapper = new ObjectMapper();

    private Response post(String path, Object body, String auth) {
        var req = given().contentType(ContentType.JSON).body(body);
        if (auth != null) {
            req = req.header("Authorization", auth);
        }
        return req.when().post(path);
    }

    private Long idOf(Response r) {
        try {
            return r.jsonPath().getLong("id");
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void probeCreateProduct() throws Exception {
        String auth = "Bearer " + adminToken();
        long ts = System.currentTimeMillis();
        String catCode = "AUTOTEST-CAT-" + ts;
        String sku = "AUTOTEST-SKU-" + ts;
        Long catId = null;
        Long prodId = null;

        try {
            // 1. Tạo Category tiền đề
            Map<String, Object> catBody = new LinkedHashMap<>();
            catBody.put("code", catCode);
            catBody.put("visible", true);
            catBody.put("featured", false);
            catBody.put("sortOrder", 1);
            catBody.put("descriptions", List.of(Map.of(
                "language", "en",
                "name", "Autotest Category " + ts,
                "friendlyUrl", "autotest-cat-" + ts,
                "title", "Autotest Category"
            )));

            Response catResp = post("/api/v1/private/category", mapper.writeValueAsString(catBody), auth);
            System.out.println(">>> [PROBE] POST /api/v1/private/category: status=" + catResp.statusCode()
                    + " body=" + preview(catResp.asString()));
            catId = idOf(catResp);

            // 2. Tạo Product với đầy đủ các trường theo sm-shop-model
            List<Map<String, Object>> catRef = catId == null
                    ? List.of()
                    : List.of(Map.of("id", catId, "code", catCode));

            Map<String, Object> priceNode = new LinkedHashMap<>();
            priceNode.put("price", 19.99);
            priceNode.put("defaultPrice", true);

            Map<String, Object> inventoryNode = new LinkedHashMap<>();
            inventoryNode.put("sku", sku);
            inventoryNode.put("quantity", 10);
            inventoryNode.put("price", priceNode);

            Map<String, Object> prodBody = new LinkedHashMap<>();
            prodBody.put("sku", sku);
            prodBody.put("price", 19.99);
            prodBody.put("quantity", 10);
            prodBody.put("visible", true);
            prodBody.put("available", true);
            prodBody.put("productShipeable", false);
            prodBody.put("dateAvailable", LocalDate.now().toString());
            prodBody.put("sortOrder", 1);
            prodBody.put("type", "GENERAL");
            prodBody.put("categories", catRef);
            prodBody.put("descriptions", List.of(Map.of(
                "language", "en",
                "name", "Autotest Product " + ts,
                "friendlyUrl", "autotest-product-" + ts,
                "title", "Autotest Product"
            )));
            prodBody.put("inventory", inventoryNode);

            Response prodResp = post("/api/v1/private/product", mapper.writeValueAsString(prodBody), auth);
            System.out.println(">>> [PROBE] POST /api/v1/private/product: status=" + prodResp.statusCode()
                    + " body=" + preview(prodResp.asString()));

            prodId = idOf(prodResp);
            System.out.println(">>> [PROBE] Extracted prodId=" + prodId + ", sku=" + sku + ", catId=" + catId);

        } finally {
            // Cleanup: bọc try/catch, thứ tự xóa sản phẩm trước, rồi danh mục sau
            if (prodId != null) {
                try {
                    Response delProd = given().header("Authorization", auth)
                            .when().delete("/api/v1/private/product/" + prodId);
                    System.out.println(">>> [CLEANUP] DELETE /api/v1/private/product/" + prodId
                            + ": status=" + delProd.statusCode());
                } catch (Exception e) {
                    System.err.println(">>> [WARNING] Cleanup product " + prodId + " failed: " + e.getMessage());
                }
            }

            if (catId != null) {
                try {
                    Response delCat = given().header("Authorization", auth)
                            .when().delete("/api/v1/private/category/" + catId);
                    System.out.println(">>> [CLEANUP] DELETE /api/v1/private/category/" + catId
                            + ": status=" + delCat.statusCode());
                } catch (Exception e) {
                    System.err.println(">>> [WARNING] Cleanup category " + catId + " failed: " + e.getMessage());
                }
            }
        }
    }
}
