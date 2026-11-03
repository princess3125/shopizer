package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("probe")
class CatalogCartProbeTest extends ApiBase {

    private Response post(String path, String body, String auth) {
        var req = given().contentType(ContentType.JSON).body(body);
        if (auth != null) {
            req = req.header("Authorization", auth);
        }
        return req.when().post(path);
    }

    private void show(String label, Response r) {
        System.out.println(">>> " + label + ": status=" + r.statusCode() + " body=" + preview(r.asString()));
    }

    private Long idOf(Response r) {
        try {
            return r.jsonPath().getLong("id");
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void createCategoryProductCartCheckout_thenCleanUp() {
        String auth = "Bearer " + adminToken();
        long ts = System.currentTimeMillis();
        String catCode = "TEST-CAT-" + ts;
        String sku = "TEST-SKU-" + ts;
        Long catId = null;
        Long prodId = null;
        String cartCode = null;
        try {
            String catBody = "{\"code\":\"" + catCode + "\",\"visible\":true,\"featured\":false,\"sortOrder\":1,"
                + "\"descriptions\":[{\"language\":\"en\",\"name\":\"Test Category " + ts + "\","
                + "\"friendlyUrl\":\"test-category-" + ts + "\",\"title\":\"Test Category\"}]}";
            Response cat = post("/api/v1/private/category", catBody, auth);
            show("create category", cat);
            catId = idOf(cat);

            String cats = catId == null ? "[]" : "[{\"id\":" + catId + ",\"code\":\"" + catCode + "\"}]";
            String prodBody = "{\"sku\":\"" + sku + "\",\"identifier\":\"" + sku + "\",\"price\":19.99,\"quantity\":10,"
                + "\"canBePurchased\":true,\"visible\":true,\"shipeable\":false,\"virtual\":false,\"sortOrder\":1,"
                + "\"dateAvailable\":\"" + LocalDate.now() + "\",\"categories\":" + cats + ","
                + "\"descriptions\":[{\"language\":\"en\",\"name\":\"Test Product " + ts + "\","
                + "\"friendlyUrl\":\"test-product-" + ts + "\",\"title\":\"Test Product\"}]}";
            Response prod = post("/api/v1/private/product", prodBody, auth);
            show("create product", prod);
            prodId = idOf(prod);

            show("products list", given().when().get("/api/v1/products"));
            show("product by sku (v2)", given().when().get("/api/v2/product/" + sku));

            Response cart = post("/api/v1/cart", "{\"product\":\"" + sku + "\",\"quantity\":2}", null);
            show("create cart", cart);
            try {
                cartCode = cart.jsonPath().getString("code");
            } catch (Exception e) {
                System.out.println(">>> no cart code found");
            }
            if (cartCode != null) {
                show("get cart", given().when().get("/api/v1/cart/" + cartCode));
                show("cart total", given().when().get("/api/v1/cart/" + cartCode + "/total"));
                String addr = "{\"firstName\":\"Test\",\"lastName\":\"User\",\"address\":\"1 Test Street\","
                    + "\"city\":\"New York\",\"country\":\"US\",\"zone\":\"NY\",\"postalCode\":\"10001\",\"phone\":\"5551234567\"}";
                String orderBody = "{\"currency\":\"USD\",\"customerAgreement\":true,\"comments\":\"api probe\","
                    + "\"customer\":{\"emailAddress\":\"probe" + ts + "@example.com\",\"firstName\":\"Test\","
                    + "\"lastName\":\"User\",\"language\":\"en\",\"billing\":" + addr + ",\"delivery\":" + addr + "},"
                    + "\"payment\":{\"paymentType\":\"MONEYORDER\",\"transactionType\":\"AUTHORIZE\",\"paymentModule\":\"moneyorder\"}}";
                show("checkout", post("/api/v1/cart/" + cartCode + "/checkout", orderBody, null));
            }
            assertThat(cat.statusCode()).isIn(200, 201);
        } finally {
            if (cartCode != null) {
                show("delete cart item", given().when().delete("/api/v1/cart/" + cartCode + "/product/" + sku));
            }
            if (prodId != null) {
                show("delete product", given().header("Authorization", auth).when().delete("/api/v1/private/product/" + prodId));
            }
            if (catId != null) {
                show("delete category", given().header("Authorization", auth).when().delete("/api/v1/private/category/" + catId));
            }
        }
    }
}