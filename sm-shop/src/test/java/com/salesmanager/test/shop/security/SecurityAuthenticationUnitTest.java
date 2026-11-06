package com.salesmanager.test.shop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.api.v1.user.AuthenticateUserApi;
import com.salesmanager.shop.store.controller.security.facade.SecurityFacadeImpl;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.store.security.AuthenticationRequest;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.admin.JWTAdminAuthenticationManager;
import com.salesmanager.shop.store.security.common.CustomAuthenticationException;
import com.salesmanager.shop.store.security.customer.JWTCustomerAuthenticationManager;
import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.shop.utils.AuthorizationUtils;

import io.jsonwebtoken.JwtException;

/**
 * Unit Test độc lập cho chức năng Authentication / Security (Shopizer 3.2.5).
 * Bao phủ các thành phần:
 * 1. JWTTokenUtil: Vòng đời token, claims, hạn dùng, xác thực token, refresh token.
 * 2. SecurityFacadeImpl: Chính sách mật khẩu (regex), mã hóa, so khớp mật khẩu.
 * 3. JWTCustomerAuthenticationManager & JWTAdminAuthenticationManager: Kiểm tra header Bearer token và SecurityContext.
 * 4. AuthenticateUserApi: Endpoint xác thực đăng nhập và cấp lại token.
 * 5. AuthorizationUtils: Kiểm tra quyền hạn và vai trò người dùng (RBAC, SuperAdmin).
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("security")
@DisplayName("Authentication & Security Unit Tests")
public class SecurityAuthenticationUnitTest {

	private static final String TEST_SECRET = "ShopizerSecretKeyForUnitTestingAuthenticationAndSecurityPurposesOnly1234567890abcdefghijklmnopqrstuvwxyz";
	private static final Long TEST_EXPIRATION = 3600L; // 1 giờ

	private JWTTokenUtil jwtTokenUtil;
	private SecurityFacadeImpl securityFacade;
	private JWTCustomerAuthenticationManager customerAuthManager;
	private JWTAdminAuthenticationManager adminAuthManager;
	private AuthenticateUserApi authenticateUserApi;
	private AuthorizationUtils authorizationUtils;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private PermissionService permissionService;

	@Mock
	private GroupService groupService;

	@Mock
	private UserDetailsService jwtCustomerDetailsService;

	@Mock
	private UserDetailsService jwtAdminDetailsService;

	@Mock
	private AuthenticationManager jwtAdminAuthenticationManager;

	@Mock
	private UserFacade userFacade;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();

		// 1. Cấu hình JWTTokenUtil
		jwtTokenUtil = new JWTTokenUtil();
		setField(jwtTokenUtil, "secret", TEST_SECRET);
		setField(jwtTokenUtil, "expiration", TEST_EXPIRATION);

		// 2. Cấu hình SecurityFacadeImpl
		securityFacade = new SecurityFacadeImpl();
		setField(securityFacade, "passwordEncoder", passwordEncoder);
		setField(securityFacade, "permissionService", permissionService);
		setField(securityFacade, "groupService", groupService);

		// 3. Cấu hình JWTCustomerAuthenticationManager
		customerAuthManager = new JWTCustomerAuthenticationManager();
		setField(customerAuthManager, "tokenHeader", "Authorization");
		setField(customerAuthManager, "jwtTokenUtil", jwtTokenUtil);
		setField(customerAuthManager, "jwtCustomerDetailsService", jwtCustomerDetailsService);

		// 4. Cấu hình JWTAdminAuthenticationManager
		adminAuthManager = new JWTAdminAuthenticationManager();
		setField(adminAuthManager, "tokenHeader", "Authorization");
		setField(adminAuthManager, "jwtTokenUtil", jwtTokenUtil);
		setField(adminAuthManager, "jwtAdminDetailsService", jwtAdminDetailsService);

		// 5. Cấu hình AuthenticateUserApi
		authenticateUserApi = new AuthenticateUserApi();
		setField(authenticateUserApi, "tokenHeader", "Authorization");
		setField(authenticateUserApi, "jwtAdminAuthenticationManager", jwtAdminAuthenticationManager);
		setField(authenticateUserApi, "jwtAdminDetailsService", jwtAdminDetailsService);
		setField(authenticateUserApi, "jwtTokenUtil", jwtTokenUtil);

		// 6. Cấu hình AuthorizationUtils
		authorizationUtils = new AuthorizationUtils();
		setField(authorizationUtils, "userFacade", userFacade);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private static void setField(Object target, String fieldName, Object value) {
		try {
			Class<?> clazz = target.getClass();
			Field field = null;
			while (clazz != null && field == null) {
				try {
					field = clazz.getDeclaredField(fieldName);
				} catch (NoSuchFieldException e) {
					clazz = clazz.getSuperclass();
				}
			}
			if (field == null) {
				throw new NoSuchFieldException(fieldName);
			}
			field.setAccessible(true);
			field.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException("Lỗi gán reflection field: " + fieldName, e);
		}
	}

	private JWTUser createSampleUser(Long id, String username, Date lastPasswordResetDate) {
		return new JWTUser(
				id,
				username,
				"First",
				"Last",
				username + "@test.com",
				"encodedPassword",
				Collections.singletonList(new SimpleGrantedAuthority("AUTH_CUSTOMER")),
				true,
				lastPasswordResetDate
		);
	}

	// =========================================================================
	// NHÓM 1: JWT TOKEN MANAGEMENT (JWTTokenUtil)
	// =========================================================================

	@Test
	@DisplayName("1.1 JWT: Tạo token thành công chứa đúng username, audience và hạn dùng")
	void testGenerateToken_Success_ContainsExpectedClaims() {
		JWTUser user = createSampleUser(1L, "john_doe", new Date());

		String token = jwtTokenUtil.generateToken(user);

		assertThat(token).isNotNull().isNotEmpty();
		assertThat(jwtTokenUtil.getUsernameFromToken(token)).isEqualTo("john_doe");
		assertThat(jwtTokenUtil.getAudienceFromToken(token)).isEqualTo("api");
		assertThat(jwtTokenUtil.getIssuedAtDateFromToken(token)).isNotNull();
		assertThat(jwtTokenUtil.getExpirationDateFromToken(token)).isAfter(new Date());
	}

	@Test
	@DisplayName("1.2 JWT: Xác thực token hợp lệ trả về true")
	void testValidateToken_ValidTokenAndUser_ReturnsTrue() {
		Date pastDate = new Date(System.currentTimeMillis() - 60000);
		JWTUser user = createSampleUser(2L, "alice", pastDate);
		String token = jwtTokenUtil.generateToken(user);

		Boolean isValid = jwtTokenUtil.validateToken(token, user);

		assertThat(isValid).isTrue();
	}

	@Test
	@DisplayName("1.3 JWT: Xác thực token thất bại khi username không khớp")
	void testValidateToken_UsernameMismatch_ReturnsFalse() {
		JWTUser userAlice = createSampleUser(3L, "alice", new Date());
		JWTUser userBob = createSampleUser(4L, "bob", new Date());
		String tokenAlice = jwtTokenUtil.generateToken(userAlice);

		Boolean isValid = jwtTokenUtil.validateToken(tokenAlice, userBob);

		assertThat(isValid).isFalse();
	}

	@Test
	@DisplayName("1.4 JWT: Xác thực token thất bại khi token được tạo trước lần reset mật khẩu")
	void testValidateToken_WhenTokenCreatedBeforePasswordReset_ReturnsFalse() {
		Date resetDate = new Date(System.currentTimeMillis() + 100000); // Reset mật khẩu ở tương lai
		JWTUser user = createSampleUser(5L, "charlie", resetDate);
		String token = jwtTokenUtil.generateToken(user);

		Boolean isValid = jwtTokenUtil.validateToken(token, user);

		assertThat(isValid).isFalse();
	}

	@Test
	@DisplayName("1.5 JWT: canTokenBeRefreshed trả về true cho token hợp lệ")
	void testCanTokenBeRefreshed_WhenNotExpiredAndNotReset_ReturnsTrue() {
		Date pastDate = new Date(System.currentTimeMillis() - 50000);
		JWTUser user = createSampleUser(6L, "david", pastDate);
		String token = jwtTokenUtil.generateToken(user);

		Boolean canRefresh = jwtTokenUtil.canTokenBeRefreshed(token, pastDate);

		assertThat(canRefresh).isTrue();
	}

	@Test
	@DisplayName("1.6 JWT: canTokenBeRefreshed trả về false khi token tạo trước lần reset mật khẩu")
	void testCanTokenBeRefreshed_WhenCreatedBeforePasswordReset_ReturnsFalse() {
		JWTUser user = createSampleUser(7L, "emma", null);
		String token = jwtTokenUtil.generateToken(user);
		Date futureReset = new Date(System.currentTimeMillis() + 100000);

		Boolean canRefresh = jwtTokenUtil.canTokenBeRefreshed(token, futureReset);

		assertThat(canRefresh).isFalse();
	}

	@Test
	@DisplayName("1.7 JWT: canTokenBeRefreshedWithGrace luôn trả về true")
	void testCanTokenBeRefreshedWithGrace_ReturnsTrue() {
		JWTUser user = createSampleUser(8L, "frank", new Date());
		String token = jwtTokenUtil.generateToken(user);

		Boolean canRefreshGrace = jwtTokenUtil.canTokenBeRefreshedWithGrace(token, new Date());

		assertThat(canRefreshGrace).isTrue();
	}

	@Test
	@DisplayName("1.8 JWT: refreshToken tạo ra token mới giữ nguyên username và cập nhật thời gian")
	void testRefreshToken_GeneratesNewTokenWithUpdatedDates() {
		JWTUser user = createSampleUser(9L, "grace", new Date());
		String oldToken = jwtTokenUtil.generateToken(user);

		String newToken = jwtTokenUtil.refreshToken(oldToken);

		assertThat(newToken).isNotNull().isNotEmpty();
		assertThat(jwtTokenUtil.getUsernameFromToken(newToken)).isEqualTo("grace");
		assertThat(jwtTokenUtil.getExpirationDateFromToken(newToken)).isAfter(new Date());
	}

	@Test
	@DisplayName("1.9 JWT: Ném JwtException khi phân tích token bị sai cấu trúc hoặc không hợp lệ")
	void testExtractClaims_FromMalformedToken_ThrowsException() {
		assertThatThrownBy(() -> jwtTokenUtil.getUsernameFromToken("invalid.malformed.token"))
				.isInstanceOf(JwtException.class);
	}

	// =========================================================================
	// NHÓM 2: PASSWORD POLICY & SECURITY FACADE (SecurityFacadeImpl)
	// =========================================================================

	@Test
	@DisplayName("2.1 Password: Mật khẩu hợp lệ (chữ hoa, chữ thường, số, độ dài 6-12)")
	void testValidateUserPassword_ValidPasswords_ReturnTrue() {
		assertThat(securityFacade.validateUserPassword("Password1")).isTrue();
		assertThat(securityFacade.validateUserPassword("Abc12345")).isTrue();
		assertThat(securityFacade.validateUserPassword("Secret99")).isTrue();
		assertThat(securityFacade.validateUserPassword("Admin2026")).isTrue();
	}

	@Test
	@DisplayName("2.2 Password: Mật khẩu quá ngắn (< 6 ký tự) trả về false")
	void testValidateUserPassword_TooShort_ReturnsFalse() {
		assertThat(securityFacade.validateUserPassword("Ab1")).isFalse();
		assertThat(securityFacade.validateUserPassword("Pass1")).isFalse();
	}

	@Test
	@DisplayName("2.3 Password: Mật khẩu quá dài (> 12 ký tự) trả về false")
	void testValidateUserPassword_TooLong_ReturnsFalse() {
		assertThat(securityFacade.validateUserPassword("Password1234567")).isFalse();
		assertThat(securityFacade.validateUserPassword("VeryLongPass12345")).isFalse();
	}

	@Test
	@DisplayName("2.4 Password: Mật khẩu thiếu số trả về false")
	void testValidateUserPassword_NoDigit_ReturnsFalse() {
		assertThat(securityFacade.validateUserPassword("Password")).isFalse();
		assertThat(securityFacade.validateUserPassword("Abcdefgh")).isFalse();
	}

	@Test
	@DisplayName("2.5 Password: Mật khẩu thiếu chữ hoa trả về false")
	void testValidateUserPassword_NoUpperCase_ReturnsFalse() {
		assertThat(securityFacade.validateUserPassword("password12")).isFalse();
		assertThat(securityFacade.validateUserPassword("secret123")).isFalse();
	}

	@Test
	@DisplayName("2.6 Password: Mật khẩu thiếu chữ thường trả về false")
	void testValidateUserPassword_NoLowerCase_ReturnsFalse() {
		assertThat(securityFacade.validateUserPassword("PASSWORD12")).isFalse();
		assertThat(securityFacade.validateUserPassword("SECRET123")).isFalse();
	}

	@Test
	@DisplayName("2.7 encodePassword: Gọi PasswordEncoder.encode")
	void testEncodePassword_DelegatesToPasswordEncoder() {
		when(passwordEncoder.encode("plainText")).thenReturn("$2a$10$hashedPassword");

		String encoded = securityFacade.encodePassword("plainText");

		assertThat(encoded).isEqualTo("$2a$10$hashedPassword");
		verify(passwordEncoder, times(1)).encode("plainText");
	}

	@Test
	@DisplayName("2.8 matchPassword: Gọi PasswordEncoder.matches")
	void testMatchPassword_DelegatesToPasswordEncoder() {
		when(passwordEncoder.matches("rawPassword", "$2a$10$hashedPassword")).thenReturn(true);

		boolean matches = securityFacade.matchPassword("$2a$10$hashedPassword", "rawPassword");

		assertThat(matches).isTrue();
		verify(passwordEncoder, times(1)).matches("rawPassword", "$2a$10$hashedPassword");
	}

	@Test
	@DisplayName("2.9 matchRawPasswords: So khớp 2 mật khẩu thuần bằng nhau trả về true")
	void testMatchRawPasswords_EqualPasswords_ReturnsTrue() {
		assertThat(securityFacade.matchRawPasswords("Secret123", "Secret123")).isTrue();
	}

	@Test
	@DisplayName("2.10 matchRawPasswords: So khớp 2 mật khẩu thuần khác nhau trả về false")
	void testMatchRawPasswords_DifferentPasswords_ReturnsFalse() {
		assertThat(securityFacade.matchRawPasswords("Secret123", "Other456")).isFalse();
	}

	@Test
	@DisplayName("2.11 matchRawPasswords: Ném IllegalArgumentException khi mật khẩu đầu tiên là null")
	void testMatchRawPasswords_NullPassword_ThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> securityFacade.matchRawPasswords(null, "Secret123"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("password is null");
	}

	@Test
	@DisplayName("2.12 matchRawPasswords: Ném IllegalArgumentException khi repeatPassword là null")
	void testMatchRawPasswords_NullRepeatPassword_ThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> securityFacade.matchRawPasswords("Secret123", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("repeat password is null");
	}

	@Test
	@DisplayName("2.13 getPermissions: Ném ServiceRuntimeException với thông điệp Not implemented")
	void testGetPermissions_ThrowsServiceRuntimeException() {
		assertThatThrownBy(() -> securityFacade.getPermissions(Collections.singletonList("ADMIN")))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Not implemented");
				});
	}

	// =========================================================================
	// NHÓM 3: AUTHENTICATION REQUEST PROCESSING (Customer & Admin Managers)
	// =========================================================================

	@Test
	@DisplayName("3.1 CustomerAuth: Ném CustomAuthenticationException khi thiếu header Authorization")
	void testCustomerAttemptAuthentication_MissingHeader_ThrowsCustomAuthenticationException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("Authorization")).thenReturn(null);

		assertThatThrownBy(() -> customerAuthManager.attemptAuthentication(request, response))
				.isInstanceOf(CustomAuthenticationException.class)
				.hasMessageContaining("No Bearer token found in the request");
	}

	@Test
	@DisplayName("3.2 CustomerAuth: Ném CustomAuthenticationException khi header không bắt đầu bằng Bearer")
	void testCustomerAttemptAuthentication_HeaderNotStartingWithBearer_ThrowsCustomAuthenticationException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

		assertThatThrownBy(() -> customerAuthManager.attemptAuthentication(request, response))
				.isInstanceOf(CustomAuthenticationException.class)
				.hasMessageContaining("No Bearer token found in the request");
	}

	@Test
	@DisplayName("3.3 CustomerAuth: Xác thực thành công khi có Bearer token hợp lệ")
	void testCustomerAttemptAuthentication_ValidBearerToken_ReturnsAuthentication() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		JWTUser user = createSampleUser(10L, "customer_user", new Date(System.currentTimeMillis() - 10000));
		String token = jwtTokenUtil.generateToken(user);

		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		when(jwtCustomerDetailsService.loadUserByUsername("customer_user")).thenReturn(user);

		Authentication authentication = customerAuthManager.attemptAuthentication(request, response);

		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(user);
		assertThat(authentication.isAuthenticated()).isTrue();
	}

	@Test
	@DisplayName("3.4 CustomerAuth: Bỏ qua nạp lại khi người dùng đã được xác thực trong SecurityContext")
	void testCustomerAttemptAuthentication_UserAlreadyInSecurityContext_SkipsAuthentication() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		JWTUser user = createSampleUser(11L, "existing_user", new Date());
		String token = jwtTokenUtil.generateToken(user);
		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

		// Giả lập đã có Authentication trong SecurityContextHolder
		Authentication existingAuth = new UsernamePasswordAuthenticationToken("existing_user", null);
		SecurityContextHolder.getContext().setAuthentication(existingAuth);

		Authentication result = customerAuthManager.attemptAuthentication(request, response);

		assertThat(result).isNull();
		verify(jwtCustomerDetailsService, never()).loadUserByUsername(any());
	}

	@Test
	@DisplayName("3.5 CustomerAuth: Trả về null khi nạp UserDetails ra null")
	void testCustomerAttemptAuthentication_NullUserDetails_ReturnsNull() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		JWTUser user = createSampleUser(12L, "not_found_user", new Date());
		String token = jwtTokenUtil.generateToken(user);

		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		when(jwtCustomerDetailsService.loadUserByUsername("not_found_user")).thenReturn(null);

		Authentication authentication = customerAuthManager.attemptAuthentication(request, response);

		assertThat(authentication).isNull();
	}

	@Test
	@DisplayName("3.6 AdminAuth: Ném CustomAuthenticationException khi thiếu token")
	void testAdminAttemptAuthentication_MissingToken_ThrowsCustomAuthenticationException() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("Authorization")).thenReturn(null);

		assertThatThrownBy(() -> adminAuthManager.attemptAuthentication(request, response))
				.isInstanceOf(CustomAuthenticationException.class)
				.hasMessageContaining("Missing Authentication Token");
	}

	@Test
	@DisplayName("3.7 AdminAuth: Xác thực thành công khi có Bearer token của Admin")
	void testAdminAttemptAuthentication_ValidBearerToken_ReturnsAuthentication() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		JWTUser admin = createSampleUser(20L, "admin_user", new Date(System.currentTimeMillis() - 10000));
		String token = jwtTokenUtil.generateToken(admin);

		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		when(jwtAdminDetailsService.loadUserByUsername("admin_user")).thenReturn(admin);

		Authentication authentication = adminAuthManager.attemptAuthentication(request, response);

		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(admin);
	}

	@Test
	@DisplayName("3.8 authenticateRequest: Gán SecurityContextHolder khi xác thực thành công")
	void testCustomAuthenticationManager_AuthenticateRequest_SuccessSetsSecurityContext() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);

		JWTUser user = createSampleUser(30L, "context_user", new Date(System.currentTimeMillis() - 10000));
		String token = jwtTokenUtil.generateToken(user);

		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		when(jwtCustomerDetailsService.loadUserByUsername("context_user")).thenReturn(user);

		customerAuthManager.authenticateRequest(request, response);

		Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(currentAuth).isNotNull();
		assertThat(currentAuth.getPrincipal()).isEqualTo(user);
	}

	@Test
	@DisplayName("3.9 authenticateRequest: Xóa SecurityContextHolder khi xác thực gặp lỗi")
	void testCustomAuthenticationManager_AuthenticateRequest_AuthenticationFailedClearsSecurityContext() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getHeader("Authorization")).thenReturn(null); // Gây ra CustomAuthenticationException

		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("oldUser", null));

		customerAuthManager.authenticateRequest(request, response);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	// =========================================================================
	// NHÓM 4: API AUTHENTICATION ENDPOINTS (AuthenticateUserApi)
	// =========================================================================

	@Test
	@DisplayName("4.1 Login: Đăng nhập thành công trả về 200 OK với AuthenticationResponse chứa token")
	void testAuthenticateUser_ValidCredentials_Returns200WithToken() throws Exception {
		AuthenticationRequest req = new AuthenticationRequest("admin", "Admin123");
		JWTUser admin = createSampleUser(1L, "admin", new Date());

		Authentication auth = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
		when(jwtAdminAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(auth);
		when(jwtAdminDetailsService.loadUserByUsername("admin")).thenReturn(admin);

		ResponseEntity<?> response = authenticateUserApi.authenticate(req);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isInstanceOf(AuthenticationResponse.class);
		AuthenticationResponse authRes = (AuthenticationResponse) response.getBody();
		assertThat(authRes.getToken()).isNotNull().isNotEmpty();
		assertThat(authRes.getId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("4.2 Login: Sai mật khẩu trả về 401 UNAUTHORIZED với message Bad credentials")
	void testAuthenticateUser_BadCredentials_Returns401Unauthorized() throws Exception {
		AuthenticationRequest req = new AuthenticationRequest("admin", "WrongPass");

		when(jwtAdminAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		ResponseEntity<?> response = authenticateUserApi.authenticate(req);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).isEqualTo("{\"message\":\"Bad credentials\"}");
	}

	@Test
	@DisplayName("4.3 Login: Xác thực trả về null trả về 404 NOT_FOUND")
	void testAuthenticateUser_AuthenticationReturnsNull_Returns404NotFound() throws Exception {
		AuthenticationRequest req = new AuthenticationRequest("unknown", "Pass123");

		when(jwtAdminAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(null);

		ResponseEntity<?> response = authenticateUserApi.authenticate(req);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("4.4 RefreshToken: Cấp lại token thành công trả về 200 OK với token mới")
	void testRefreshAndGetAuthenticationToken_ValidToken_Returns200WithRefreshedToken() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		JWTUser admin = createSampleUser(1L, "admin", new Date());
		String token = jwtTokenUtil.generateToken(admin);

		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
		when(jwtAdminDetailsService.loadUserByUsername("admin")).thenReturn(admin);

		ResponseEntity<AuthenticationResponse> response = authenticateUserApi.refreshAndGetAuthenticationToken(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getToken()).isNotEmpty();
	}

	// =========================================================================
	// NHÓM 5: AUTHORIZATION & PERMISSION CHECKS (AuthorizationUtils)
	// =========================================================================

	@Test
	@DisplayName("5.1 authenticatedUser: Lấy username thành công khi người dùng đã đăng nhập")
	void testAuthenticatedUser_Success() {
		when(userFacade.authenticatedUser()).thenReturn("current_user");

		String username = authorizationUtils.authenticatedUser();

		assertThat(username).isEqualTo("current_user");
	}

	@Test
	@DisplayName("5.2 authenticatedUser: Ném UnauthorizedException khi chưa có người dùng đăng nhập")
	void testAuthenticatedUser_Null_ThrowsUnauthorizedException() {
		when(userFacade.authenticatedUser()).thenReturn(null);

		assertThatThrownBy(() -> authorizationUtils.authenticatedUser())
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	@DisplayName("5.3 authorizeUser: Người dùng có vai trò SUPERADMIN được miễn kiểm tra cửa hàng")
	void testAuthorizeUser_SuperAdmin_BypassesStoreCheck() {
		MerchantStore store = new MerchantStore();
		store.setCode("STORE1");
		List<String> roles = Collections.singletonList("ROLE_ADMIN");

		when(userFacade.userInRoles("superadmin", Collections.singletonList(Constants.GROUP_SUPERADMIN)))
				.thenReturn(true);

		// Không ném exception và không cần kiểm tra authorizedStore
		authorizationUtils.authorizeUser("superadmin", roles, store);

		verify(userFacade, times(1)).authorizedGroup("superadmin", roles);
		verify(userFacade, never()).authorizedStore(any(), any());
	}

	@Test
	@DisplayName("5.4 authorizeUser: Người dùng thông thường được cấp quyền vào store thành công")
	void testAuthorizeUser_NonSuperAdmin_AuthorizedStore_Success() {
		MerchantStore store = new MerchantStore();
		store.setCode("STORE2");
		List<String> roles = Collections.singletonList("ROLE_USER");

		when(userFacade.userInRoles("regular_user", Collections.singletonList(Constants.GROUP_SUPERADMIN)))
				.thenReturn(false);
		when(userFacade.authorizedStore("regular_user", "STORE2")).thenReturn(true);

		authorizationUtils.authorizeUser("regular_user", roles, store);

		verify(userFacade, times(1)).authorizedGroup("regular_user", roles);
		verify(userFacade, times(1)).authorizedStore("regular_user", "STORE2");
	}

	@Test
	@DisplayName("5.5 authorizeUser: Người dùng thông thường không có quyền vào store bị ném UnauthorizedException")
	void testAuthorizeUser_NonSuperAdmin_UnauthorizedStore_ThrowsUnauthorizedException() {
		MerchantStore store = new MerchantStore();
		store.setCode("FORBIDDEN_STORE");
		List<String> roles = Collections.singletonList("ROLE_USER");

		when(userFacade.userInRoles("regular_user", Collections.singletonList(Constants.GROUP_SUPERADMIN)))
				.thenReturn(false);
		when(userFacade.authorizedStore("regular_user", "FORBIDDEN_STORE")).thenReturn(false);

		assertThatThrownBy(() -> authorizationUtils.authorizeUser("regular_user", roles, store))
				.isInstanceOf(UnauthorizedException.class)
				.satisfies(e -> {
					UnauthorizedException ue = (UnauthorizedException) e;
					assertThat(ue.getErrorMessage())
							.isEqualTo("Operation unauthorized for user [regular_user] and store [FORBIDDEN_STORE]");
				});
	}
}
