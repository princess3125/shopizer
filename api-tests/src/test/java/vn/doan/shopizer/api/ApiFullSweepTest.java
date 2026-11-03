package vn.doan.shopizer.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@Tag("sweep")
class ApiFullSweepTest extends ApiBase {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Pattern PARAM = Pattern.compile("\\{([^}]+)\\}");

    private static final Map<String, Integer> KNOWN_NO_TOKEN = new HashMap<>();
    private static final Map<String, Integer> KNOWN_5XX = new HashMap<>();

    static {
        loadKnownIssues();
    }

    private static void loadKnownIssues() {
        try (InputStream in = ApiFullSweepTest.class.getResourceAsStream("/known-issues.csv")) {
            if (in == null) {
                throw new IllegalStateException("Không tìm thấy tệp classpath /known-issues.csv");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                boolean header = true;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (header) {
                        header = false;
                        continue;
                    }
                    String[] parts = line.split(",", -1);
                    if (parts.length >= 4) {
                        String method = parts[0].trim();
                        String path = parts[1].trim();
                        String scenario = parts[2].trim();
                        int expectedStatus = Integer.parseInt(parts[3].trim());
                        String key = method + " " + path;

                        if ("no_token".equalsIgnoreCase(scenario)) {
                            KNOWN_NO_TOKEN.put(key, expectedStatus);
                        } else if ("admin_bogus".equalsIgnoreCase(scenario) || "public_bogus".equalsIgnoreCase(scenario)) {
                            KNOWN_5XX.put(key, expectedStatus);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi đọc tệp known-issues.csv", e);
        }
    }

    private static final class Endpoint {
        final String method;
        final String path;
        final Map<String, String> paramTypes;

        Endpoint(String method, String path, Map<String, String> paramTypes) {
            this.method = method;
            this.path = path;
            this.paramTypes = paramTypes;
        }

        String key() {
            return method + " " + path;
        }

        boolean secured() {
            return path.contains("/private/") || path.contains("/auth/");
        }

        boolean hasPathParam() {
            return PARAM.matcher(path).find();
        }

        String url() {
            Matcher m = PARAM.matcher(path);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String type = paramTypes.getOrDefault(m.group(1), "string");
                String v = ("integer".equals(type) || "number".equals(type)) ? "999999999" : "autotest-none";
                m.appendReplacement(sb, v);
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Endpoint> endpoints() {
        Map<String, Object> paths = given().when().get("/v2/api-docs")
            .then().statusCode(200).extract().jsonPath().getMap("paths");
        List<Endpoint> out = new ArrayList<>();
        for (Map.Entry<String, Object> pe : paths.entrySet()) {
            Map<String, Object> ops = (Map<String, Object>) pe.getValue();
            for (Map.Entry<String, Object> oe : ops.entrySet()) {
                if (!HTTP_METHODS.contains(oe.getKey())) {
                    continue;
                }
                Map<String, Object> op = (Map<String, Object>) oe.getValue();
                Map<String, String> types = new HashMap<>();
                Object params = op.get("parameters");
                if (params instanceof List) {
                    for (Object po : (List<Object>) params) {
                        Map<String, Object> p = (Map<String, Object>) po;
                        if ("path".equals(p.get("in"))) {
                            Object t = p.get("type");
                            types.put(String.valueOf(p.get("name")), t == null ? "string" : String.valueOf(t));
                        }
                    }
                }
                out.add(new Endpoint(oe.getKey().toUpperCase(Locale.ROOT), pe.getKey(), types));
            }
        }
        out.sort(Comparator.comparing(Endpoint::key));
        return out;
    }

    private static Response call(Endpoint e, String bearer) {
        RequestSpecification r = given().contentType(ContentType.JSON);
        if (bearer != null) {
            r = r.header("Authorization", bearer);
        }
        if (BODY_METHODS.contains(e.method)) {
            r = r.body("{}");
        }
        return r.when().request(e.method, e.url());
    }

    @TestFactory
    Stream<DynamicTest> securedEndpoints_rejectRequestsWithoutToken() {
        return endpoints().stream().filter(Endpoint::secured)
            .map(e -> dynamicTest(e.key() + " without token", () -> {
                int status = call(e, null).statusCode();
                Integer known = KNOWN_NO_TOKEN.get(e.key());
                if (known != null) {
                    assertThat(status)
                        .as("KNOWN ISSUE - xóa khỏi known-issues.csv khi đã sửa: %s without token", e.key())
                        .isEqualTo(known);
                } else {
                    assertThat(status)
                        .as("%s without token phải bị từ chối 401 hoặc 403", e.key())
                        .isIn(401, 403);
                }
            }));
    }

    @Test
    void fullSweep_writesCoverageReport() throws IOException {
        String bearer = "Bearer " + adminToken();
        List<Endpoint> all = endpoints();
        List<String> rows = new ArrayList<>();
        rows.add("method,path,secured,status_no_token,status_admin_bogus");
        List<String> serverErrors = new ArrayList<>();
        List<String> unexpectedServerErrors = new ArrayList<>();
        Set<String> observed5xx = new HashSet<>();
        int secured = 0;
        int publicGet = 0;
        int publicNotCalled = 0;
        int adminTested = 0;

        for (Endpoint e : all) {
            String sNo = "";
            String sAdm = "";
            if (e.secured()) {
                secured++;
                sNo = String.valueOf(call(e, null).statusCode());
                if (e.path.contains("/private/") && e.hasPathParam()) {
                    adminTested++;
                    int code = call(e, bearer).statusCode();
                    sAdm = String.valueOf(code);
                    if (code >= 500) {
                        serverErrors.add(e.key() + " -> " + code);
                        Integer expected = KNOWN_5XX.get(e.key());
                        if (expected == null || expected != code) {
                            unexpectedServerErrors.add(e.key() + " -> " + code + " (kỳ vọng: " + expected + ")");
                        } else {
                            observed5xx.add(e.key());
                        }
                    }
                }
            } else if ("GET".equals(e.method)) {
                publicGet++;
                int code = call(e, null).statusCode();
                sNo = String.valueOf(code);
                if (code >= 500) {
                    serverErrors.add(e.key() + " -> " + code);
                    Integer expected = KNOWN_5XX.get(e.key());
                    if (expected == null || expected != code) {
                        unexpectedServerErrors.add(e.key() + " -> " + code + " (kỳ vọng: " + expected + ")");
                    } else {
                        observed5xx.add(e.key());
                    }
                }
            } else {
                publicNotCalled++;
            }
            rows.add(e.method + "," + e.path + "," + e.secured() + "," + sNo + "," + sAdm);
        }

        Path file = Paths.get("target", "api-full-sweep.csv");
        Files.createDirectories(file.getParent());
        Files.write(file, rows, StandardCharsets.UTF_8);
        System.out.println(">>> full-sweep: total=" + all.size() + " secured=" + secured
            + " publicGetTested=" + publicGet + " publicOtherNotCalled=" + publicNotCalled
            + " adminBogusTested=" + adminTested + " server5xx=" + serverErrors.size());
        for (String s : serverErrors) {
            System.out.println(">>> 5xx: " + s);
        }

        assertThat(all).isNotEmpty();

        // 1. Khẳng định không có lỗi 5xx mới hoặc sai mã trạng thái so với known-issues.csv
        assertThat(unexpectedServerErrors)
            .as("Phát hiện lỗi 5xx mới hoặc mã lỗi khác với known-issues.csv: %s", unexpectedServerErrors)
            .isEmpty();

        // 2. Khẳng định toàn bộ các lỗi 5xx đã biết vẫn xảy ra đúng dự kiến (nếu lỗi được sửa, test đỏ để nhắc cập nhật baseline)
        assertThat(observed5xx)
            .as("Các lỗi 5xx đã biết phải xảy ra đúng dự kiến (nếu đã sửa hãy xóa khỏi known-issues.csv)")
            .containsExactlyInAnyOrderElementsOf(KNOWN_5XX.keySet());
    }
}
