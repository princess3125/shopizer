package com.salesmanager.test.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.payments.PaymentServiceImpl;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.business.services.system.MerchantConfigurationService;
import com.salesmanager.core.business.services.system.ModuleConfigurationService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalType;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.order.orderstatus.OrderStatusHistory;
import com.salesmanager.core.model.payments.CreditCardPayment;
import com.salesmanager.core.model.payments.CreditCardType;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentMethod;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.country.Country;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.model.system.MerchantConfiguration;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.salesmanager.core.modules.utils.Encryption;

/**
 * Unit Test độc lập cho PaymentService (sm-core).
 * Kiểm thử toàn diện các luồng nghiệp vụ thanh toán:
 * - Lọc và phát hiện phương thức thanh toán theo Quốc gia / Store
 * - Cấu hình tích hợp module thanh toán (JSON load, mã hóa / giải mã)
 * - Xử lý thanh toán (Authorize, AuthorizeCapture, Init)
 * - Capture thanh toán cho đơn hàng đã Authorize trước đó
 * - Hoàn tiền (Refund) toàn phần và một phần
 * - Khởi tạo giao dịch (initTransaction)
 * - Kiểm tra tính hợp lệ thẻ tín dụng & thuật toán Luhn
 */
@ExtendWith(MockitoExtension.class)
public class PaymentServiceUnitTest {

	@Mock
	private MerchantConfigurationService merchantConfigurationService;

	@Mock
	private ModuleConfigurationService moduleConfigurationService;

	@Mock
	private TransactionService transactionService;

	@Mock
	private OrderService orderService;

	@Mock
	private CoreConfiguration coreConfiguration;

	@Mock
	private Encryption encryption;

	@Mock
	private PaymentModule mockPaymentModule;

	private Map<String, PaymentModule> paymentModulesMap;
	private PaymentServiceImpl paymentService;

	private MerchantStore store;
	private Customer customer;
	private Order order;

	@BeforeEach
	void setUp() {
		paymentService = new PaymentServiceImpl();
		paymentModulesMap = new HashMap<>();
		paymentModulesMap.put("paypal", mockPaymentModule);
		paymentModulesMap.put("stripe", mockPaymentModule);
		paymentModulesMap.put("moneyorder", mockPaymentModule);

		setField(paymentService, "merchantConfigurationService", merchantConfigurationService);
		setField(paymentService, "moduleConfigurationService", moduleConfigurationService);
		setField(paymentService, "transactionService", transactionService);
		setField(paymentService, "orderService", orderService);
		setField(paymentService, "coreConfiguration", coreConfiguration);
		setField(paymentService, "paymentModules", paymentModulesMap);
		setField(paymentService, "encryption", encryption);

		// Dữ liệu mẫu Store
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
		Country country = new Country("CA");
		country.setIsoCode("CA");
		store.setCountry(country);
		Currency currency = new Currency();
		currency.setCurrency(java.util.Currency.getInstance("USD"));
		store.setCurrency(currency);

		// Dữ liệu mẫu Customer
		customer = new Customer();
		customer.setId(100L);
		customer.setNick("test_customer");

		// Dữ liệu mẫu Order
		order = new Order();
		order.setId(500L);
		order.setTotal(new BigDecimal("100.00"));
		order.setPaymentModuleCode("paypal");
		Set<OrderTotal> orderTotals = new HashSet<>();
		OrderTotal total = new OrderTotal();
		total.setModule(Constants.OT_TOTAL_MODULE_CODE);
		total.setValue(new BigDecimal("100.00"));
		orderTotals.add(total);
		order.setOrderTotal(orderTotals);
		order.setOrderHistory(new HashSet<>());
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

	private IntegrationModule createIntegrationModule(String code, String type, String... regions) {
		IntegrationModule module = new IntegrationModule();
		module.setCode(code);
		module.setModule(type);
		module.setType(type);
		Set<String> regionSet = new HashSet<>();
		Collections.addAll(regionSet, regions);
		module.setRegionsSet(regionSet);
		return module;
	}

	private IntegrationConfiguration createIntegrationConfig(String moduleCode, boolean active) {
		IntegrationConfiguration config = new IntegrationConfiguration();
		config.setModuleCode(moduleCode);
		config.setActive(active);
		Map<String, String> keys = new HashMap<>();
		keys.put("transaction", "AUTHORIZECAPTURE");
		config.setIntegrationKeys(keys);
		return config;
	}

	private void assertServiceExceptionMessage(Throwable throwable, String expectedSnippet) {
		assertThat(throwable).isInstanceOf(ServiceException.class);
		ServiceException se = (ServiceException) throwable;
		String msg = se.getMessage() != null ? se.getMessage() : se.getMessageCode();
		assertThat(msg).contains(expectedSnippet);
	}

	// =========================================================================
	// NHÓM 1: PHÁT HIỆN & LỌC PHƯƠNG THỨC THANH TOÁN (getPaymentMethods)
	// =========================================================================

	@Test
	@DisplayName("1.1 getPaymentMethods: Lọc chính xác các module theo mã quốc gia của Store và wildcard (*)")
	void testGetPaymentMethods_FiltersByCountryAndWildcard() throws ServiceException {
		IntegrationModule mCanada = createIntegrationModule("beanstream", "PAYMENT", "CA", "US");
		IntegrationModule mGlobal = createIntegrationModule("paypal", "PAYMENT", "*");
		IntegrationModule mFrance = createIntegrationModule("atos", "PAYMENT", "FR");

		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES))
				.thenReturn(List.of(mCanada, mGlobal, mFrance));

		List<IntegrationModule> methods = paymentService.getPaymentMethods(store);

		// Store quốc gia là CA -> nhận mCanada và mGlobal, loại mFrance
		assertThat(methods).hasSize(2);
		assertThat(methods).extracting(IntegrationModule::getCode).containsExactlyInAnyOrder("beanstream", "paypal");
	}

	@Test
	@DisplayName("1.2 getPaymentMethodByType: Tìm thấy module theo loại thanh toán")
	void testGetPaymentMethodByType_Found() throws ServiceException {
		IntegrationModule m1 = createIntegrationModule("paypal", "PAYPAL", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES))
				.thenReturn(List.of(m1));

		IntegrationModule result = paymentService.getPaymentMethodByType(store, "PAYPAL");

		assertThat(result).isNotNull();
		assertThat(result.getCode()).isEqualTo("paypal");
	}

	@Test
	@DisplayName("1.3 getPaymentMethodByType: Không tìm thấy module trả về null")
	void testGetPaymentMethodByType_NotFound_ReturnsNull() throws ServiceException {
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES))
				.thenReturn(Collections.emptyList());

		IntegrationModule result = paymentService.getPaymentMethodByType(store, "CREDITCARD");

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("1.4 getPaymentMethodByCode: Tìm thấy module theo mã định danh code")
	void testGetPaymentMethodByCode_Found() throws ServiceException {
		IntegrationModule m1 = createIntegrationModule("stripe", "CREDITCARD", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES))
				.thenReturn(List.of(m1));

		IntegrationModule result = paymentService.getPaymentMethodByCode(store, "stripe");

		assertThat(result).isNotNull();
		assertThat(result.getCode()).isEqualTo("stripe");
	}

	@Test
	@DisplayName("1.5 getPaymentMethodByCode: Không tìm thấy trả về null")
	void testGetPaymentMethodByCode_NotFound_ReturnsNull() throws ServiceException {
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES))
				.thenReturn(Collections.emptyList());

		IntegrationModule result = paymentService.getPaymentMethodByCode(store, "unknown_code");

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("1.6 getAcceptedPaymentMethods: Chỉ trả về các phương thức thanh toán đang kích hoạt (active)")
	void testGetAcceptedPaymentMethods_ReturnsOnlyActiveMethods() throws Exception {
		// Mock merchant config chứa 2 module: paypal (active) và stripe (inactive)
		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("encrypted_json");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("encrypted_json")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true},{\"moduleCode\":\"stripe\",\"active\":false}]");

		IntegrationModule paypalModule = createIntegrationModule("paypal", "paypal", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES))
				.thenReturn(List.of(paypalModule));

		List<PaymentMethod> accepted = paymentService.getAcceptedPaymentMethods(store);

		assertThat(accepted).hasSize(1);
		assertThat(accepted.get(0).getPaymentMethodCode()).isEqualTo("paypal");
		assertThat(accepted.get(0).getPaymentType()).isEqualTo(PaymentType.PAYPAL);
	}

	// =========================================================================
	// NHÓM 2: CẤU HÌNH MODULE THANH TOÁN (getPaymentConfiguration, save, remove)
	// =========================================================================

	@Test
	@DisplayName("2.1 getPaymentConfiguration: Lấy cấu hình thành công theo moduleCode (không phân biệt hoa thường)")
	void testGetPaymentConfiguration_Success() throws Exception {
		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("encrypted_json");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("encrypted_json")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		IntegrationConfiguration config = paymentService.getPaymentConfiguration("PAYPAL", store);

		assertThat(config).isNotNull();
		assertThat(config.getModuleCode()).isEqualTo("paypal");
		assertThat(config.isActive()).isTrue();
	}

	@Test
	@DisplayName("2.2 getPaymentConfiguration: Ném NullPointerException khi moduleCode là null")
	void testGetPaymentConfiguration_NullModuleCode_ThrowsNullPointerException() {
		assertThatThrownBy(() -> paymentService.getPaymentConfiguration(null, store))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("2.3 getPaymentConfiguration: Ném NullPointerException khi store là null")
	void testGetPaymentConfiguration_NullStore_ThrowsNullPointerException() {
		assertThatThrownBy(() -> paymentService.getPaymentConfiguration("paypal", null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("2.4 getPaymentModulesConfigured: Trả về Map rỗng khi MerchantConfiguration rỗng hoặc chưa cấu hình")
	void testGetPaymentModulesConfigured_EmptyWhenNotConfigured() throws ServiceException {
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(null);

		Map<String, IntegrationConfiguration> map = paymentService.getPaymentModulesConfigured(store);

		assertThat(map).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("2.5 savePaymentModuleConfiguration: Ném ServiceException khi moduleCode không tồn tại trong hệ thống")
	void testSavePaymentModuleConfiguration_ModuleNotExist_ThrowsServiceException() {
		IntegrationConfiguration config = new IntegrationConfiguration();
		config.setModuleCode("non_existent_module");

		assertThatThrownBy(() -> paymentService.savePaymentModuleConfiguration(config, store))
				.satisfies(e -> assertServiceExceptionMessage(e, "Payment module non_existent_module does not exist"));
	}

	@Test
	@DisplayName("2.6 savePaymentModuleConfiguration: Thành công xác thực module, mã hóa JSON và lưu vào DB")
	void testSavePaymentModuleConfiguration_Success() throws Exception {
		IntegrationConfiguration config = createIntegrationConfig("paypal", true);

		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(null);
		when(encryption.encrypt(anyString())).thenReturn("new_encrypted_payload");

		paymentService.savePaymentModuleConfiguration(config, store);

		verify(mockPaymentModule, times(1)).validateModuleConfiguration(config, store);
		verify(encryption, times(1)).encrypt(anyString());
		verify(merchantConfigurationService, times(1)).saveOrUpdate(any(MerchantConfiguration.class));
	}

	@Test
	@DisplayName("2.7 removePaymentModuleConfiguration: Xóa cấu hình module và cập nhật bản ghi merchant")
	void testRemovePaymentModuleConfiguration_Success() throws Exception {
		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("encrypted_json");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("encrypted_json")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");
		when(encryption.encrypt(anyString())).thenReturn("updated_encrypted_json");

		paymentService.removePaymentModuleConfiguration("paypal", store);

		verify(merchantConfigurationService, times(1)).saveOrUpdate(mc);
	}

	// =========================================================================
	// NHÓM 3: XỬ LÝ THANH TOÁN (processPayment)
	// =========================================================================

	@Test
	@DisplayName("3.1 processPayment: AUTHORIZECAPTURE thành công với thẻ/PayPal -> set trạng thái đơn hàng PROCESSED")
	void testProcessPayment_AuthorizeCapture_Success_SetsOrderProcessed() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("paypal");
		payment.setPaymentType(PaymentType.PAYPAL);

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true,\"integrationKeys\":{\"transaction\":\"AUTHORIZECAPTURE\"}}]");

		IntegrationModule integrationModule = createIntegrationModule("paypal", "paypal", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES)).thenReturn(List.of(integrationModule));

		Transaction expectedTrx = new Transaction();
		expectedTrx.setAmount(new BigDecimal("100.00"));
		expectedTrx.setTransactionType(TransactionType.AUTHORIZECAPTURE);
		when(mockPaymentModule.authorizeAndCapture(eq(store), eq(customer), any(), eq(new BigDecimal("100.00")), eq(payment), any(), eq(integrationModule)))
				.thenReturn(expectedTrx);

		Transaction resultTrx = paymentService.processPayment(customer, store, payment, Collections.emptyList(), order);

		assertThat(resultTrx).isNotNull();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
		verify(transactionService, times(1)).create(expectedTrx);
	}

	@Test
	@DisplayName("3.2 processPayment: AUTHORIZECAPTURE với MONEYORDER -> giữ trạng thái đơn hàng ORDERED")
	void testProcessPayment_AuthorizeCapture_MoneyOrder_SetsOrderOrdered() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("moneyorder");
		payment.setPaymentType(PaymentType.MONEYORDER);

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"moneyorder\",\"active\":true,\"integrationKeys\":{\"transaction\":\"AUTHORIZECAPTURE\"}}]");

		IntegrationModule integrationModule = createIntegrationModule("moneyorder", "moneyorder", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES)).thenReturn(List.of(integrationModule));

		Transaction expectedTrx = new Transaction();
		when(mockPaymentModule.authorizeAndCapture(any(), any(), any(), any(), any(), any(), any())).thenReturn(expectedTrx);

		paymentService.processPayment(customer, store, payment, Collections.emptyList(), order);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.ORDERED);
	}

	@Test
	@DisplayName("3.3 processPayment: Giao dịch AUTHORIZE chỉ gọi authorize mà không cập nhật đơn hàng sang PROCESSED")
	void testProcessPayment_AuthorizeOnly_Success() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("paypal");
		payment.setPaymentType(PaymentType.PAYPAL);

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true,\"integrationKeys\":{\"transaction\":\"AUTHORIZE\"}}]");

		IntegrationModule integrationModule = createIntegrationModule("paypal", "paypal", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES)).thenReturn(List.of(integrationModule));

		Transaction expectedTrx = new Transaction();
		when(mockPaymentModule.authorize(any(), any(), any(), any(), any(), any(), any())).thenReturn(expectedTrx);

		Transaction result = paymentService.processPayment(customer, store, payment, Collections.emptyList(), order);

		assertThat(result).isNotNull();
		assertThat(order.getStatus()).isNull(); // Không tự động đổi sang PROCESSED
		verify(transactionService, times(1)).create(expectedTrx);
	}

	@Test
	@DisplayName("3.4 processPayment: Giao dịch INIT không gọi transactionService.create")
	void testProcessPayment_InitTransaction_DoesNotCreateTransactionRecord() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("paypal");
		payment.setPaymentType(PaymentType.PAYPAL);

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true,\"integrationKeys\":{\"transaction\":\"INIT\"}}]");

		IntegrationModule integrationModule = createIntegrationModule("paypal", "paypal", "*");
		when(moduleConfigurationService.getIntegrationModules(Constants.PAYMENT_MODULES)).thenReturn(List.of(integrationModule));

		Transaction expectedTrx = new Transaction();
		when(mockPaymentModule.initTransaction(any(), any(), any(), any(), any(), any())).thenReturn(expectedTrx);

		paymentService.processPayment(customer, store, payment, Collections.emptyList(), order);

		verify(transactionService, never()).create(any());
	}

	@Test
	@DisplayName("3.5 processPayment: Ném NullPointerException khi các tham số bắt buộc là null")
	void testProcessPayment_NullInputs_ThrowsNullPointerException() {
		Payment payment = new Payment();

		assertThatThrownBy(() -> paymentService.processPayment(null, store, payment, Collections.emptyList(), order))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> paymentService.processPayment(customer, null, payment, Collections.emptyList(), order))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> paymentService.processPayment(customer, store, null, Collections.emptyList(), order))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> paymentService.processPayment(customer, store, payment, Collections.emptyList(), null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("3.6 processPayment: Ném ServiceException khi module thanh toán chưa được cấu hình")
	void testProcessPayment_ModuleNotConfigured_ThrowsServiceException() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("unknown_module");

		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(null);

		assertThatThrownBy(() -> paymentService.processPayment(customer, store, payment, Collections.emptyList(), order))
				.satisfies(e -> assertServiceExceptionMessage(e, "is not configured"));
	}

	@Test
	@DisplayName("3.7 processPayment: Ném ServiceException khi module thanh toán ở trạng thái không kích hoạt (inactive)")
	void testProcessPayment_ModuleInactive_ThrowsServiceException() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("paypal");

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":false}]");

		assertThatThrownBy(() -> paymentService.processPayment(customer, store, payment, Collections.emptyList(), order))
				.satisfies(e -> assertServiceExceptionMessage(e, "Payment module paypal is not active"));
	}

	// =========================================================================
	// NHÓM 4: CAPTURE THANH TOÁN (processCapturePayment)
	// =========================================================================

	@Test
	@DisplayName("4.1 processCapturePayment: Capture thành công giao dịch trước đó và cập nhật Order sang PROCESSED")
	void testProcessCapturePayment_Success() throws Exception {
		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		Transaction capturableTrx = new Transaction();
		capturableTrx.setId(99L);
		when(transactionService.getCapturableTransaction(order)).thenReturn(capturableTrx);

		Transaction capturedTrx = new Transaction();
		capturedTrx.setId(100L);
		when(mockPaymentModule.capture(eq(store), eq(customer), eq(order), eq(capturableTrx), any(), any()))
				.thenReturn(capturedTrx);

		Transaction result = paymentService.processCapturePayment(order, customer, store);

		assertThat(result).isNotNull();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
		verify(transactionService, times(1)).create(capturedTrx);
		verify(orderService, times(1)).addOrderStatusHistory(eq(order), any(OrderStatusHistory.class));
		verify(orderService, times(1)).saveOrUpdate(order);
	}

	@Test
	@DisplayName("4.2 processCapturePayment: Ném ServiceException khi không tìm thấy giao dịch có thể capture")
	void testProcessCapturePayment_NoCapturableTransaction_ThrowsServiceException() throws Exception {
		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		when(transactionService.getCapturableTransaction(order)).thenReturn(null);

		assertThatThrownBy(() -> paymentService.processCapturePayment(order, customer, store))
				.satisfies(e -> assertServiceExceptionMessage(e, "No capturable transaction for order id 500"));
	}

	// =========================================================================
	// NHÓM 5: HOÀN TIỀN (processRefund)
	// =========================================================================

	@Test
	@DisplayName("5.1 processRefund: Hoàn tiền toàn phần thành công (partial = false), cập nhật tổng tiền và trạng thái")
	void testProcessRefund_FullRefund_Success() throws Exception {
		BigDecimal refundAmount = new BigDecimal("100.00");

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		Transaction refundableTrx = new Transaction();
		when(transactionService.getRefundableTransaction(order)).thenReturn(refundableTrx);

		Transaction refundTrx = new Transaction();
		when(mockPaymentModule.refund(eq(false), eq(store), eq(refundableTrx), eq(order), eq(refundAmount), any(), any()))
				.thenReturn(refundTrx);

		Transaction result = paymentService.processRefund(order, customer, store, refundAmount);

		assertThat(result).isNotNull();
		assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
		verify(transactionService, times(1)).create(refundTrx);
		verify(orderService, times(1)).saveOrUpdate(order);
	}

	@Test
	@DisplayName("5.2 processRefund: Hoàn tiền một phần (partial = true) cập nhật trừ lùi số tiền đơn hàng")
	void testProcessRefund_PartialRefund_Success() throws Exception {
		BigDecimal refundAmount = new BigDecimal("35.00");

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		Transaction refundableTrx = new Transaction();
		when(transactionService.getRefundableTransaction(order)).thenReturn(refundableTrx);

		Transaction refundTrx = new Transaction();
		when(mockPaymentModule.refund(eq(true), eq(store), eq(refundableTrx), eq(order), eq(refundAmount), any(), any()))
				.thenReturn(refundTrx);

		paymentService.processRefund(order, customer, store, refundAmount);

		// 100.00 - 35.00 = 65.00
		assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("65.00"));
		assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
	}

	@Test
	@DisplayName("5.3 processRefund: Ném ServiceException khi số tiền hoàn trả vượt quá tổng tiền của đơn hàng")
	void testProcessRefund_AmountGreaterThanTotal_ThrowsServiceException() {
		BigDecimal invalidRefundAmount = new BigDecimal("150.00");

		assertThatThrownBy(() -> paymentService.processRefund(order, customer, store, invalidRefundAmount))
				.satisfies(e -> assertServiceExceptionMessage(e, "the refunded amount is greater than the total allowed"));
	}

	@Test
	@DisplayName("5.4 processRefund: Ném ServiceException khi không tìm thấy giao dịch có thể hoàn tiền")
	void testProcessRefund_NoRefundableTransaction_ThrowsServiceException() throws Exception {
		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		when(transactionService.getRefundableTransaction(order)).thenReturn(null);

		assertThatThrownBy(() -> paymentService.processRefund(order, customer, store, new BigDecimal("50.00")))
				.satisfies(e -> assertServiceExceptionMessage(e, "No refundable transaction for this order"));
	}

	// =========================================================================
	// NHÓM 6: KHỞI TẠO GIAO DỊCH (initTransaction)
	// =========================================================================

	@Test
	@DisplayName("6.1 initTransaction (với Order): Khởi tạo giao dịch thành công")
	void testInitTransaction_WithOrder_Success() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("paypal");

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		Transaction initTrx = new Transaction();
		when(mockPaymentModule.initTransaction(any(), any(), any(), any(), any(), any())).thenReturn(initTrx);

		Transaction result = paymentService.initTransaction(order, customer, payment, store);

		assertThat(result).isNotNull();
		assertThat(payment.getCurrency().getCode()).isEqualTo("USD");
	}

	@Test
	@DisplayName("6.2 initTransaction (không Order): Khởi tạo và lưu Transaction thành công")
	void testInitTransaction_WithoutOrder_Success() throws Exception {
		Payment payment = new Payment();
		payment.setModuleName("paypal");
		payment.setAmount(new BigDecimal("75.00"));

		MerchantConfiguration mc = new MerchantConfiguration();
		mc.setValue("enc");
		when(merchantConfigurationService.getMerchantConfiguration(Constants.PAYMENT_MODULES, store)).thenReturn(mc);
		when(encryption.decrypt("enc")).thenReturn("[{\"moduleCode\":\"paypal\",\"active\":true}]");

		Transaction initTrx = new Transaction();
		when(mockPaymentModule.initTransaction(any(), any(), any(), any(), any(), any())).thenReturn(initTrx);

		Transaction result = paymentService.initTransaction(customer, payment, store);

		assertThat(result).isNotNull();
		verify(transactionService, times(1)).save(initTrx);
	}

	@Test
	@DisplayName("6.3 initTransaction (không Order): Ném NullPointerException khi payment.amount là null")
	void testInitTransaction_WithoutOrder_NullAmount_ThrowsNullPointerException() {
		Payment payment = new Payment();
		payment.setModuleName("paypal");
		payment.setAmount(null);

		assertThatThrownBy(() -> paymentService.initTransaction(customer, payment, store))
				.isInstanceOf(NullPointerException.class);
	}

	// =========================================================================
	// NHÓM 7: KIỂM TRA HỢP LỆ THẺ TÍN DỤNG & THUẬT TOÁN LUHN (validateCreditCard)
	// =========================================================================

	@Test
	@DisplayName("7.1 validateCreditCard: Thẻ VISA hợp lệ, ngày hết hạn trong tương lai vượt qua kiểm tra")
	void testValidateCreditCard_Visa_Valid_Success() throws ServiceException {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 3;
		// 4111111111111111 là thẻ Visa 16 số chuẩn thỏa mãn thuật toán Luhn
		paymentService.validateCreditCard("4111111111111111", CreditCardType.VISA, "12", String.valueOf(futureYear));
	}

	@Test
	@DisplayName("7.2 validateCreditCard: Thẻ MASTERCARD hợp lệ vượt qua kiểm tra")
	void testValidateCreditCard_Mastercard_Valid_Success() throws ServiceException {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 3;
		// 5500000000000004 bắt đầu bằng 55, 16 số, thỏa mãn Luhn: 1 + 4 + 5 = 10
		paymentService.validateCreditCard("5500000000000004", CreditCardType.MASTERCARD, "06", String.valueOf(futureYear));
	}

	@Test
	@DisplayName("7.3 validateCreditCard: Thẻ AMEX hợp lệ (15 số, bắt đầu bằng 37) vượt qua kiểm tra")
	void testValidateCreditCard_Amex_Valid_Success() throws ServiceException {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 2;
		// 370000000000002 bắt đầu bằng 37, 15 số, thỏa mãn Luhn
		paymentService.validateCreditCard("370000000000002", CreditCardType.AMEX, "08", String.valueOf(futureYear));
	}

	@Test
	@DisplayName("7.4 validateCreditCard: Ném ServiceException khi ngày hết hạn ở trong quá khứ")
	void testValidateCreditCard_ExpiredDate_ThrowsServiceException() {
		assertThatThrownBy(() -> paymentService.validateCreditCard("4111111111111111", CreditCardType.VISA, "01", "2020"))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid date format"));
	}

	@Test
	@DisplayName("7.5 validateCreditCard: Ném ServiceException khi định dạng tháng/năm không phải là số")
	void testValidateCreditCard_NonNumericMonth_ThrowsServiceException() {
		assertThatThrownBy(() -> paymentService.validateCreditCard("4111111111111111", CreditCardType.VISA, "abc", "2028"))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid date format"));
	}

	@Test
	@DisplayName("7.6 validateCreditCard: Ném ServiceException khi số thẻ để trống")
	void testValidateCreditCard_BlankNumber_ThrowsServiceException() {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 2;
		assertThatThrownBy(() -> paymentService.validateCreditCard("   ", CreditCardType.VISA, "10", String.valueOf(futureYear)))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid card number"));
	}

	@Test
	@DisplayName("7.7 validateCreditCard: Ném ServiceException khi số thẻ chứa ký tự không hợp lệ")
	void testValidateCreditCard_NonDigitCharacters_ThrowsServiceException() {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 2;
		assertThatThrownBy(() -> paymentService.validateCreditCard("411111111111ABCD", CreditCardType.VISA, "10", String.valueOf(futureYear)))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid card number"));
	}

	@Test
	@DisplayName("7.8 validateCreditCard: Ném ServiceException khi sai thuật toán Luhn (MOD 10)")
	void testValidateCreditCard_LuhnAlgorithmFailure_ThrowsServiceException() {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 2;
		// 4111111111111112 có số kiểm tra sai -> trượt thuật toán Luhn
		assertThatThrownBy(() -> paymentService.validateCreditCard("4111111111111112", CreditCardType.VISA, "10", String.valueOf(futureYear)))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid card number"));
	}

	@Test
	@DisplayName("7.9 validateCreditCard: Ném ServiceException khi thẻ VISA không bắt đầu bằng số 4")
	void testValidateCreditCard_Visa_InvalidPrefix_ThrowsServiceException() {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 2;
		// Bắt đầu bằng 5 nhưng khai báo VISA
		assertThatThrownBy(() -> paymentService.validateCreditCard("5111111111111111", CreditCardType.VISA, "10", String.valueOf(futureYear)))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid card number"));
	}

	@Test
	@DisplayName("7.10 validateCreditCard: Ném ServiceException khi thẻ MASTERCARD có 2 số đầu ngoài khoảng 51..55")
	void testValidateCreditCard_Mastercard_InvalidPrefix_ThrowsServiceException() {
		int futureYear = Calendar.getInstance().get(Calendar.YEAR) + 2;
		// 6000000000000004 bắt đầu bằng 60
		assertThatThrownBy(() -> paymentService.validateCreditCard("6000000000000004", CreditCardType.MASTERCARD, "10", String.valueOf(futureYear)))
				.satisfies(e -> assertServiceExceptionMessage(e, "Invalid card number"));
	}
}
