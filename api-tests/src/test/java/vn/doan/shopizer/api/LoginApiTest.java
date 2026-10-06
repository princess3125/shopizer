package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("api")
class LoginApiTest {

    private static final String LOGIN_PATH = "/api/v1/private/login";

    // Override when running: -Dapi.baseUrl=... -Dapi.user=... -Dapi.password=...
    private static final String USER = System.getProperty("api.user", "admin@shopizer.com");
    private static final String PASSWORD = System.getProperty("api.password", "password");

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = System.getProperty("api.baseUrl", "http://localhost:8080");
    }

    private Response login(Object body) {
        return given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post(LOGIN_PATH);
    }

    @Test
    void login_withValidAdmin_returns200AndToken() {
        login(Map.of("username", USER, "password", PASSWORD))
        .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("token", notNullValue())
            .body("token", not(emptyString()));
    }

    @Test
    void login_withWrongPassword_isRejected() {
        Response r = login(Map.of("username", USER, "password", "wrong-password"));

        System.out.println(">>> wrong password: status=" + r.statusCode());
        // asString() does not parse the body, so a wrong content-type is not a problem
        String body = r.asString();

        assertThat(r.statusCode()).isIn(400, 401, 403);
        assertThat(body).contains("Bad credentials");
        assertThat(body).doesNotContain("token");
    }

    @Test
    void login_withUnknownUser_isRejected() {
        Response r = login(Map.of("username", "not-exist@example.com", "password", PASSWORD));

        System.out.println(">>> unknown user: status=" + r.statusCode() + " body=" + r.asString());

        assertThat(r.statusCode()).isIn(400, 401, 403);
        assertThat(r.asString()).doesNotContain("token");
    }

    @Test
    void login_withEmptyBody_isRejected() {
        Response r = login("{}");

        System.out.println(">>> empty body: status=" + r.statusCode() + " body=" + r.asString());

        assertThat(r.statusCode()).isIn(400, 401, 403, 500);
    }
}