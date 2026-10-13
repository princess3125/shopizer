package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("api")
class PrivateAccessApiTest extends ApiBase {

    private static final String PRIVATE_API = "/api/v1/private/product/types";

    @Test
    void privateApi_withoutToken_isRejected() {
        Response r = given().when().get(PRIVATE_API);
        System.out.println(">>> private no token: status=" + r.statusCode() + " body=" + preview(r.asString()));
        assertThat(r.statusCode()).isEqualTo(401);
    }

    @Test
    void privateApi_withValidToken_returns200() {
        Response r = given()
            .header("Authorization", "Bearer " + adminToken())
        .when()
            .get(PRIVATE_API);
        System.out.println(">>> private with token: status=" + r.statusCode() + " body=" + preview(r.asString()));
        assertThat(r.statusCode()).isEqualTo(200);
    }
}