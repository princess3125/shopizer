package com.salesmanager.test.shop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.modules.email.Email;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.business.services.system.EmailService;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.CredentialsReset;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl;
import com.salesmanager.shop.utils.EmailUtils;
import com.salesmanager.shop.utils.FilePathUtils;
import com.salesmanager.shop.utils.ImageFilePath;
import com.salesmanager.shop.utils.LabelUtils;

/**
 * Unit Test độc lập cho CustomerFacade trong module sm-shop.
 * Bao gồm com.salesmanager.shop.store.facade.customer.CustomerFacadeImpl (V1)
 * và com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl.
 * Chạy độc lập hoàn toàn bằng Mockito, không khởi động Spring Context.
 */
@ExtendWith(MockitoExtension.class)
public class CustomerFacadeImplUnitTest {

	@Mock
	private CustomerService customerService;

	@Mock
	private FilePathUtils filePathUtils;

	@Mock
	private LanguageService languageService;

	@Mock
	private EmailUtils emailUtils;

	@Mock
	private EmailService emailService;

	@Mock
	private ImageFilePath imageUtils;

	@Mock
	private LabelUtils messages;

	@Mock
	private PasswordEncoder passwordEncoder;

	private CustomerFacadeImpl customerFacade;
	private com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl customerControllerFacade;

	private MerchantStore store;
	private Language language;

	@BeforeEach
	void setUp() {
		// Khởi tạo V1 CustomerFacadeImpl
		customerFacade = new CustomerFacadeImpl();
		setField(customerFacade, "customerService", customerService);
		setField(customerFacade, "filePathUtils", filePathUtils);
		setField(customerFacade, "lamguageService", languageService); // Chú ý tên biến lamguageService trong mã nguồn
		setField(customerFacade, "emailUtils", emailUtils);
		setField(customerFacade, "emailService", emailService);
		setField(customerFacade, "imageUtils", imageUtils);
		setField(customerFacade, "messages", messages);
		setField(customerFacade, "passwordEncoder", passwordEncoder);

		// Khởi tạo Controller CustomerFacadeImpl
		customerControllerFacade = new com.salesmanager.shop.store.controller.customer.facade.CustomerFacadeImpl();
		setField(customerControllerFacade, "customerService", customerService);
		setField(customerControllerFacade, "passwordEncoder", passwordEncoder);

		// Dữ liệu mẫu Store & Language
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
		store.setStorename("Shopizer Default Store");
		store.setStoreEmailAddress("store@shopizer.com");

		language = new Language();
		language.setId(1);
		language.setCode("en");
	}

	private static void setField(Object target, String fieldName, Object value) {
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
			throw new RuntimeException("Field [" + fieldName + "] not found on " + target.getClass());
		}
		field.setAccessible(true);
		try {
			field.set(target, value);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Failed to set field [" + fieldName + "]", e);
		}
	}

	private Customer createSampleCustomer(Long id, String nick, String email) {
		Customer customer = new Customer();
		customer.setId(id);
		customer.setNick(nick);
		customer.setEmailAddress(email);
		customer.setMerchantStore(store);

		Billing billing = new Billing();
		billing.setFirstName("John");
		billing.setLastName("Doe");
		customer.setBilling(billing);

		return customer;
	}

	// =========================================================================
	// NHÓM 1: PHÂN QUYỀN VÀ XÁC THỰC (authorize)
	// =========================================================================

	@Test
	@DisplayName("1.1 authorize: Thành công khi tên của Principal khớp với nick của Customer")
	void testAuthorize_Success_WhenPrincipalMatchesNick() {
		Customer customer = createSampleCustomer(10L, "john_doe", "john@test.com");
		Principal principal = () -> "john_doe";

		customerFacade.authorize(customer, principal);
		// Không có ngoại lệ xảy ra -> Đạt
	}

	@Test
	@DisplayName("1.2 authorize: Ném IllegalArgumentException khi Customer là null")
	void testAuthorize_CustomerNull_ThrowsIllegalArgumentException() {
		Principal principal = () -> "john_doe";

		assertThatThrownBy(() -> customerFacade.authorize(null, principal))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Customer cannot be null");
	}

	@Test
	@DisplayName("1.3 authorize: Ném IllegalArgumentException khi Principal là null")
	void testAuthorize_PrincipalNull_ThrowsIllegalArgumentException() {
		Customer customer = createSampleCustomer(10L, "john_doe", "john@test.com");

		assertThatThrownBy(() -> customerFacade.authorize(customer, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Principal cannot be null");
	}

	@Test
	@DisplayName("1.4 authorize: Ném UnauthorizedException khi tên Principal không khớp với nick của Customer")
	void testAuthorize_PrincipalMismatch_ThrowsUnauthorizedException() {
		Customer customer = createSampleCustomer(10L, "john_doe", "john@test.com");
		Principal principal = () -> "hacker_user";

		assertThatThrownBy(() -> customerFacade.authorize(customer, principal))
				.isInstanceOf(UnauthorizedException.class)
				.satisfies(e -> {
					UnauthorizedException ue = (UnauthorizedException) e;
					assertThat(ue.getErrorMessage()).contains("unauthorized for customer [10]");
				});
	}

	// =========================================================================
	// NHÓM 2: KIỂM TRA TỒN TẠI KHÁCH HÀNG (customerExists)
	// =========================================================================

	@Test
	@DisplayName("2.1 customerExists: Trả về true khi tìm thấy khách hàng theo userName và storeId")
	void testCustomerExists_Found_ReturnsTrue() {
		Customer customer = createSampleCustomer(1L, "existing_user", "exist@test.com");
		when(customerService.getByNick("existing_user", 1)).thenReturn(customer);

		boolean exists = customerFacade.customerExists("existing_user", store);

		assertThat(exists).isTrue();
		verify(customerService, times(1)).getByNick("existing_user", 1);
	}

	@Test
	@DisplayName("2.2 customerExists: Trả về false khi không tìm thấy khách hàng")
	void testCustomerExists_NotFound_ReturnsFalse() {
		when(customerService.getByNick("unknown_user", 1)).thenReturn(null);

		boolean exists = customerFacade.customerExists("unknown_user", store);

		assertThat(exists).isFalse();
	}

	// =========================================================================
	// NHÓM 3: XÁC MINH TOKEN ĐẶT LẠI MẬT KHẨU (verifyPasswordRequestToken)
	// =========================================================================

	@Test
	@DisplayName("3.1 verifyPasswordRequestToken: Xác minh thành công khi token hợp lệ và chưa hết hạn")
	void testVerifyPasswordRequestToken_Success_ValidTokenAndNotExpired() {
		Customer customer = createSampleCustomer(5L, "alice", "alice@test.com");
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("valid-token-123");

		// Đặt hạn sử dụng là ngày mai
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 1);
		reset.setCredentialsRequestExpiry(cal.getTime());
		customer.setCredentialsResetRequest(reset);

		when(customerService.getByPasswordResetToken("DEFAULT", "valid-token-123")).thenReturn(customer);

		customerFacade.verifyPasswordRequestToken("valid-token-123", "DEFAULT");
		// Không có exception -> Thành công
	}

	@Test
	@DisplayName("3.2 verifyPasswordRequestToken: Ném IllegalArgumentException khi token là null")
	void testVerifyPasswordRequestToken_TokenNull_ThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> customerFacade.verifyPasswordRequestToken(null, "DEFAULT"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ResetPassword token cannot be null");
	}

	@Test
	@DisplayName("3.3 verifyPasswordRequestToken: Ném IllegalArgumentException khi store code là null")
	void testVerifyPasswordRequestToken_StoreNull_ThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> customerFacade.verifyPasswordRequestToken("some-token", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Store code cannot be null");
	}

	@Test
	@DisplayName("3.4 verifyPasswordRequestToken: Ném ServiceRuntimeException khi không tìm thấy khách hàng theo token")
	void testVerifyPasswordRequestToken_CustomerNotFound_ThrowsServiceRuntimeException() {
		when(customerService.getByPasswordResetToken("DEFAULT", "non-existent-token")).thenReturn(null);

		assertThatThrownBy(() -> customerFacade.verifyPasswordRequestToken("non-existent-token", "DEFAULT"))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Cannot verify customer token");
					assertThat(sre.getCause()).isInstanceOf(ResourceNotFoundException.class);
				});
	}

	@Test
	@DisplayName("3.5 verifyPasswordRequestToken: Ném GenericRuntimeException khi token chưa cấu hình ngày hết hạn")
	void testVerifyPasswordRequestToken_ExpiryNull_ThrowsGenericRuntimeException() {
		Customer customer = createSampleCustomer(5L, "alice", "alice@test.com");
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("token-no-expiry");
		reset.setCredentialsRequestExpiry(null); // Không có hạn
		customer.setCredentialsResetRequest(reset);

		when(customerService.getByPasswordResetToken("DEFAULT", "token-no-expiry")).thenReturn(customer);

		assertThatThrownBy(() -> customerFacade.verifyPasswordRequestToken("token-no-expiry", "DEFAULT"))
				.isInstanceOf(GenericRuntimeException.class)
				.satisfies(e -> {
					GenericRuntimeException gre = (GenericRuntimeException) e;
					assertThat(gre.getErrorMessage()).contains("No expiry date configured for token");
				});
	}

	@Test
	@DisplayName("3.6 verifyPasswordRequestToken: Ném GenericRuntimeException khi token đã hết hạn")
	void testVerifyPasswordRequestToken_ExpiredToken_ThrowsGenericRuntimeException() {
		Customer customer = createSampleCustomer(5L, "alice", "alice@test.com");
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("expired-token");

		// Hạn là 2 ngày trước
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, -2);
		reset.setCredentialsRequestExpiry(cal.getTime());
		customer.setCredentialsResetRequest(reset);

		when(customerService.getByPasswordResetToken("DEFAULT", "expired-token")).thenReturn(customer);

		assertThatThrownBy(() -> customerFacade.verifyPasswordRequestToken("expired-token", "DEFAULT"))
				.isInstanceOf(GenericRuntimeException.class)
				.satisfies(e -> {
					GenericRuntimeException gre = (GenericRuntimeException) e;
					assertThat(gre.getErrorMessage()).contains("has expired");
				});
	}

	// =========================================================================
	// NHÓM 4: ĐẶT LẠI MẬT KHẨU (resetPassword)
	// =========================================================================

	@Test
	@DisplayName("4.1 resetPassword: Thành công mã hóa mật khẩu mới và lưu Customer")
	void testResetPassword_Success_EncodesPasswordAndSaves() throws ServiceException {
		Customer customer = createSampleCustomer(5L, "alice", "alice@test.com");
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("valid-token");

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 1);
		reset.setCredentialsRequestExpiry(cal.getTime());
		customer.setCredentialsResetRequest(reset);

		when(customerService.getByPasswordResetToken("DEFAULT", "valid-token")).thenReturn(customer);
		when(passwordEncoder.encode("newPassword123")).thenReturn("encodedSecretHash");

		customerFacade.resetPassword("newPassword123", "valid-token", "DEFAULT");

		assertThat(customer.getPassword()).isEqualTo("encodedSecretHash");
		verify(passwordEncoder, times(1)).encode("newPassword123");
		verify(customerService, times(1)).save(customer);
	}

	@Test
	@DisplayName("4.2 resetPassword: Ném IllegalArgumentException khi mật khẩu mới là null")
	void testResetPassword_PasswordNull_ThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> customerFacade.resetPassword(null, "valid-token", "DEFAULT"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("New password cannot be null");
	}

	@Test
	@DisplayName("4.3 resetPassword: Ném ServiceRuntimeException khi customerService.save ném ServiceException")
	void testResetPassword_SaveFailure_ThrowsServiceRuntimeException() throws ServiceException {
		Customer customer = createSampleCustomer(5L, "alice", "alice@test.com");
		CredentialsReset reset = new CredentialsReset();
		reset.setCredentialsRequest("valid-token");

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 1);
		reset.setCredentialsRequestExpiry(cal.getTime());
		customer.setCredentialsResetRequest(reset);

		when(customerService.getByPasswordResetToken("DEFAULT", "valid-token")).thenReturn(customer);
		when(passwordEncoder.encode("newPassword123")).thenReturn("encodedSecretHash");
		doThrow(new ServiceException("DB constraint error")).when(customerService).save(customer);

		assertThatThrownBy(() -> customerFacade.resetPassword("newPassword123", "valid-token", "DEFAULT"))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Error while saving customer");
					assertThat(sre.getCause()).isInstanceOf(ServiceException.class);
				});
	}

	// =========================================================================
	// NHÓM 5: YÊU CẦU ĐẶT LẠI MẬT KHẨU (requestPasswordReset)
	// =========================================================================

	@Test
	@DisplayName("5.1 requestPasswordReset: Tạo token, lưu Customer và gửi email thành công")
	void testRequestPasswordReset_Success_GeneratesTokenAndSendsEmail() throws Exception {
		Customer customer = createSampleCustomer(8L, "john_request", "john@test.com");
		when(customerService.getByNick("john_request", 1)).thenReturn(customer);

		when(filePathUtils.buildBaseUrl("/shop", store)).thenReturn("http://localhost:8080/shop");
		when(languageService.toLocale(language, store)).thenReturn(Locale.ENGLISH);

		Map<String, String> emailObjectsMap = new HashMap<>();
		when(emailUtils.createEmailObjectsMap(any(), eq(store), eq(messages), eq(Locale.ENGLISH)))
				.thenReturn(emailObjectsMap);
		when(messages.getMessage(anyString(), any(Locale.class))).thenReturn("Sample Label");
		when(messages.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Sample Text With Param");
		when(imageUtils.getContextPath()).thenReturn("/static");

		customerFacade.requestPasswordReset("john_request", "/shop", store, language);

		// Xác minh lưu token vào Customer
		verify(customerService, times(1)).saveOrUpdate(customer);
		assertThat(customer.getCredentialsResetRequest()).isNotNull();
		assertThat(customer.getCredentialsResetRequest().getCredentialsRequest()).isNotEmpty();
		assertThat(customer.getCredentialsResetRequest().getCredentialsRequestExpiry()).isAfter(new Date());

		// Xác minh gửi email thành công
		verify(emailService, times(1)).sendHtmlEmail(eq(store), any(Email.class));
	}

	@Test
	@DisplayName("5.2 requestPasswordReset: Ném ServiceRuntimeException khi khách hàng không tồn tại")
	void testRequestPasswordReset_CustomerNotFound_ThrowsServiceRuntimeException() throws Exception {
		when(customerService.getByNick("unknown", 1)).thenReturn(null);

		assertThatThrownBy(() -> customerFacade.requestPasswordReset("unknown", "/shop", store, language))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Error while executing resetPassword request");
					assertThat(sre.getCause()).isInstanceOf(ResourceNotFoundException.class);
				});

		verify(emailService, never()).sendHtmlEmail(any(), any());
	}

	// =========================================================================
	// NHÓM 6: CÁC TIỆN ÍCH TRONG CONTROLLER CUSTOMER FACADE (CustomerFacadeImpl v0)
	// =========================================================================

	@Test
	@DisplayName("6.1 passwordMatch: Trả về true khi mật khẩu thô khớp với hash đã mã hóa")
	void testPasswordMatch_Matches_ReturnsTrue() {
		Customer customer = createSampleCustomer(1L, "user1", "user1@test.com");
		customer.setPassword("hashedPassword");

		when(passwordEncoder.matches("rawPass123", "hashedPassword")).thenReturn(true);

		boolean matches = customerControllerFacade.passwordMatch("rawPass123", customer);

		assertThat(matches).isTrue();
	}

	@Test
	@DisplayName("6.2 passwordMatch: Trả về false khi mật khẩu thô không khớp")
	void testPasswordMatch_DoesNotMatch_ReturnsFalse() {
		Customer customer = createSampleCustomer(1L, "user1", "user1@test.com");
		customer.setPassword("hashedPassword");

		when(passwordEncoder.matches("wrongPass", "hashedPassword")).thenReturn(false);

		boolean matches = customerControllerFacade.passwordMatch("wrongPass", customer);

		assertThat(matches).isFalse();
	}

	@Test
	@DisplayName("6.3 changePassword: Mã hóa mật khẩu mới và gọi customerService.update")
	void testChangePassword_Success() throws ServiceException {
		Customer customer = createSampleCustomer(1L, "user1", "user1@test.com");

		when(passwordEncoder.encode("brandNewSecret")).thenReturn("newHashedSecret");

		customerControllerFacade.changePassword(customer, "brandNewSecret");

		assertThat(customer.getPassword()).isEqualTo("newHashedSecret");
		verify(customerService, times(1)).update(customer);
	}

	@Test
	@DisplayName("6.4 changePassword: Ném ServiceRuntimeException khi customerService.update ném ServiceException")
	void testChangePassword_ServiceException_ThrowsServiceRuntimeException() throws ServiceException {
		Customer customer = createSampleCustomer(1L, "user1", "user1@test.com");

		when(passwordEncoder.encode("brandNewSecret")).thenReturn("newHashedSecret");
		doThrow(new ServiceException("Update failed")).when(customerService).update(customer);

		assertThatThrownBy(() -> customerControllerFacade.changePassword(customer, "brandNewSecret"))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Exception while changing password");
					assertThat(sre.getCause()).isInstanceOf(ServiceException.class);
				});
	}

	@Test
	@DisplayName("6.5 deleteById: Tìm thấy khách hàng và xóa thành công")
	void testDeleteById_Success() throws ServiceException {
		Customer customer = createSampleCustomer(20L, "to_delete", "delete@test.com");
		when(customerService.getById(20L)).thenReturn(customer);

		customerControllerFacade.deleteById(20L);

		verify(customerService, times(1)).delete(customer);
	}

	@Test
	@DisplayName("6.6 deleteById: Ném ResourceNotFoundException khi không tìm thấy Customer theo ID")
	void testDeleteById_NotFound_ThrowsResourceNotFoundException() {
		when(customerService.getById(999L)).thenReturn(null);

		assertThatThrownBy(() -> customerControllerFacade.deleteById(999L))
				.isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> {
					ResourceNotFoundException rnfe = (ResourceNotFoundException) e;
					assertThat(rnfe.getErrorMessage()).contains("Customer id 999 does not exists");
				});
	}

	@Test
	@DisplayName("6.7 deleteByNick: Tìm thấy khách hàng theo username và xóa thành công")
	void testDeleteByNick_Success() throws ServiceException {
		Customer customer = createSampleCustomer(30L, "nick_to_delete", "nick@test.com");
		when(customerService.getByNick("nick_to_delete")).thenReturn(customer);

		customerControllerFacade.deleteByNick("nick_to_delete");

		verify(customerService, times(1)).delete(customer);
	}

	@Test
	@DisplayName("6.8 deleteByNick: Ném ResourceNotFoundException khi không tìm thấy Customer theo username")
	void testDeleteByNick_NotFound_ThrowsResourceNotFoundException() {
		when(customerService.getByNick("ghost")).thenReturn(null);

		assertThatThrownBy(() -> customerControllerFacade.deleteByNick("ghost"))
				.isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> {
					ResourceNotFoundException rnfe = (ResourceNotFoundException) e;
					assertThat(rnfe.getErrorMessage()).contains("No Customer found for ID : ghost");
				});
	}
}
