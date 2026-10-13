package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;

abstract class ApiBase {

    static final String USER = System.getProperty("api.user", "admin@shopizer.com");
    static final String PASSWORD = System.getProperty("api.password", "password");
    private static String token;

    static {
        RestAssured.baseURI = System.getProperty("api.baseUrl", "http://localhost:8080");
    }

    static synchronized String adminToken() {
        if (token == null) {
            token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USER, "password", PASSWORD))
            .when()
                .post("/api/v1/private/login")
            .then()
                .statusCode(200)
                .extract().path("token");
        }
        return token;
    }

    static String preview(String body) {
        if (body == null) return "null";
        String s = body.replace("\n", " ");
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}