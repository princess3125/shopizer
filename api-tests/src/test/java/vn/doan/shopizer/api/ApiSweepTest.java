package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@Tag("sweep")
class ApiSweepTest extends ApiBase {

    /**
     * Danh sách lỗi đã biết đối với các endpoint secured khi gọi không có token (docs/FACTS.md).
     * Endpoint -> Mã HTTP thực tế hiện tại.
     * Khi hệ thống được sửa, test sẽ fail để nhắc lập trình viên xóa khỏi danh sách lỗi đã biết.
     */
    private static final Map<String, Integer> KNOWN_SECURED_NO_TOKEN_ISSUES = Map.of(
        "/api/v1/sec/private/groups", 200,
        "/api/v1/sec/private/permissions", 200,
        "/api/v1/auth/refresh", 500
    );

    /**
     * Danh sách endpoint gặp lỗi 500 đã biết (docs/FACTS.md).
     */
    private static final Set<String> KNOWN_5XX_ENDPOINTS = Set.of(
        "/api/v1/content/boxes",
        "/api/v1/content/pages",
        "/api/v1/private/content/boxes",
        "/api/v1/private/content/pages"
    );

    private static List<String> readOnlyPaths() {
        Map<String, Object> paths = given().when().get("/v2/api-docs")
            .then().statusCode(200).extract().jsonPath().getMap("paths");
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : paths.entrySet()) {
            if (e.getKey().contains("{")) {
                continue;
            }
            if (e.getValue() instanceof Map && ((Map<?, ?>) e.getValue()).containsKey("get")) {
                out.add(e.getKey());
            }
        }
        Collections.sort(out);
        return out;
    }

    private static boolean secured(String p) {
        return p.contains("/private/") || p.contains("/auth/");
    }

    @TestFactory
    Stream<DynamicTest> securedGetEndpoints_rejectRequestsWithoutToken() {
        return readOnlyPaths().stream().filter(ApiSweepTest::secured)
            .map(p -> dynamicTest("GET " + p + " without token", () -> {
                int status = given().when().get(p).statusCode();
                if (KNOWN_SECURED_NO_TOKEN_ISSUES.containsKey(p)) {
                    int expectedStatus = KNOWN_SECURED_NO_TOKEN_ISSUES.get(p);
                    assertThat(status)
                        .as("KNOWN ISSUE - xóa khỏi danh sách khi đã sửa: GET %s without token", p)
                        .isEqualTo(expectedStatus);
                } else {
                    assertThat(status)
                        .as("GET %s without token phải bị từ chối 401 hoặc 403", p)
                        .isIn(401, 403);
                }
            }));
    }

    @Test
    void sweepReadOnlyEndpoints_writesReport() throws IOException {
        String bearer = "Bearer " + adminToken();
        List<String> paths = readOnlyPaths();
        List<String> rows = new ArrayList<>();
        rows.add("path,secured,status_no_token,status_admin_token");
        List<String> serverErrors = new ArrayList<>();
        List<String> unexpectedServerErrors = new ArrayList<>();
        int ok = 0;
        int client = 0;
        int server = 0;

        for (String p : paths) {
            int noTok = given().when().get(p).statusCode();
            int withTok = given().header("Authorization", bearer).when().get(p).statusCode();
            int main = secured(p) ? withTok : noTok;
            if (main >= 500) {
                server++;
                serverErrors.add("GET " + p + " -> " + main);
                if (!KNOWN_5XX_ENDPOINTS.contains(p)) {
                    unexpectedServerErrors.add("GET " + p + " -> " + main);
                }
            } else if (main >= 400) {
                client++;
            } else {
                ok++;
            }
            rows.add(p + "," + secured(p) + "," + noTok + "," + withTok);
        }

        Path file = Paths.get("target", "api-sweep.csv");
        Files.createDirectories(file.getParent());
        Files.write(file, rows, StandardCharsets.UTF_8);
        System.out.println(">>> sweep: total=" + paths.size() + " ok=" + ok + " client4xx=" + client + " server5xx=" + server);
        for (String s : serverErrors) {
            System.out.println(">>> 5xx: " + s);
        }

        assertThat(paths).isNotEmpty();

        // 1. Khẳng định đúng hành vi các lỗi 500 đã biết (docs/FACTS.md)
        for (String known500 : KNOWN_5XX_ENDPOINTS) {
            if (paths.contains(known500)) {
                int code = secured(known500)
                    ? given().header("Authorization", bearer).when().get(known500).statusCode()
                    : given().when().get(known500).statusCode();
                assertThat(code)
                    .as("KNOWN ISSUE - xóa khỏi danh sách khi đã sửa: GET %s", known500)
                    .isEqualTo(500);
            }
        }

        // 2. Bất kỳ lỗi 5xx MỚI nào ngoài baseline sẽ làm test fail
        assertThat(unexpectedServerErrors)
            .as("Phát hiện lỗi 5xx MỚI không có trong docs/FACTS.md: %s", unexpectedServerErrors)
            .isEmpty();
    }
}
