package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("api")
class CatalogApiTest extends ApiBase {

    @Test
    void categoryList_returns200AndListStructure() {
        Response r = given().when().get("/api/v1/category");
        System.out.println(">>> category: status=" + r.statusCode() + " body=" + preview(r.asString()));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.contentType()).contains("application/json");
        assertThat(r.jsonPath().getList("categories")).isNotNull();
        assertThat(r.jsonPath().getInt("recordsTotal")).isGreaterThanOrEqualTo(0);
    }

    @Test
    void productList_returns200AndListStructure() {
        Response r = given().when().get("/api/v1/products");
        System.out.println(">>> products: status=" + r.statusCode() + " body=" + preview(r.asString()));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.contentType()).contains("application/json");
        assertThat(r.jsonPath().getList("products")).isNotNull();
        assertThat(r.jsonPath().getInt("recordsTotal")).isGreaterThanOrEqualTo(0);
    }
}