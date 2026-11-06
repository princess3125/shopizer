package com.salesmanager.test.shop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.payments.PaymentMethod;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.shop.model.configuration.PersistableConfiguration;
import com.salesmanager.shop.model.configuration.ReadableConfiguration;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.payment.PaymentConfigurationFacadeImpl;

/**
 * Unit Test độc lập cho PaymentConfigurationFacadeImpl trong module sm-shop.
 * Kiểm thử việc chuyển đổi DTO cấu hình thanh toán và xử lý ngoại lệ tầng facade.
 */
@ExtendWith(MockitoExtension.class)
public class PaymentFacadeImplUnitTest {

	@Mock
	private PaymentService paymentService;

	private PaymentConfigurationFacadeImpl paymentFacade;
	private MerchantStore store;

	@BeforeEach
	void setUp() {
		paymentFacade = new PaymentConfigurationFacadeImpl();
		setField(paymentFacade, "paymentService", paymentService);

		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
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

	private PaymentMethod createSamplePaymentMethod(String moduleCode, boolean active) {
		PaymentMethod pm = new PaymentMethod();
		pm.setPaymentMethodCode(moduleCode);

		IntegrationModule module = new IntegrationModule();
		module.setCode(moduleCode);
		pm.setModule(module);

		IntegrationConfiguration config = new IntegrationConfiguration();
		config.setModuleCode(moduleCode);
		config.setActive(active);
		Map<String, String> keys = new HashMap<>();
		keys.put("client_id", "test_id_123");
		config.setIntegrationKeys(keys);
		pm.setInformations(config);

		return pm;
	}

	@Test
	@DisplayName("1. configurations: Trả về danh sách cấu hình ReadableConfiguration đầy đủ")
	void testConfigurations_Success() throws ServiceException {
		PaymentMethod pm1 = createSamplePaymentMethod("paypal", true);
		PaymentMethod pm2 = createSamplePaymentMethod("stripe", false);
		when(paymentService.getAcceptedPaymentMethods(store)).thenReturn(Arrays.asList(pm1, pm2));

		List<ReadableConfiguration> configs = paymentFacade.configurations(store);

		assertThat(configs).hasSize(2);
		assertThat(configs.get(0).getCode()).isEqualTo("paypal");
		assertThat(configs.get(0).isActive()).isTrue();
		assertThat(configs.get(0).getKeys()).containsEntry("client_id", "test_id_123");

		assertThat(configs.get(1).getCode()).isEqualTo("stripe");
		assertThat(configs.get(1).isActive()).isFalse();
	}

	@Test
	@DisplayName("2. configurations: Ném ServiceRuntimeException khi paymentService gặp lỗi")
	void testConfigurations_ServiceException_ThrowsServiceRuntimeException() throws ServiceException {
		when(paymentService.getAcceptedPaymentMethods(store)).thenThrow(new ServiceException("Database down"));

		assertThatThrownBy(() -> paymentFacade.configurations(store))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Error while getting payment configurations");
					assertThat(sre.getCause()).isInstanceOf(ServiceException.class);
				});
	}

	@Test
	@DisplayName("3. configuration(module): Tìm thấy cấu hình theo mã module cụ thể")
	void testConfigurationByModule_Found_ReturnsConfig() throws ServiceException {
		PaymentMethod pm1 = createSamplePaymentMethod("paypal", true);
		PaymentMethod pm2 = createSamplePaymentMethod("stripe", true);
		when(paymentService.getAcceptedPaymentMethods(store)).thenReturn(Arrays.asList(pm1, pm2));

		ReadableConfiguration config = paymentFacade.configuration("stripe", store);

		assertThat(config).isNotNull();
		assertThat(config.getCode()).isEqualTo("stripe");
		assertThat(config.isActive()).isTrue();
	}

	@Test
	@DisplayName("4. configuration(module): Không tìm thấy cấu hình trả về null")
	void testConfigurationByModule_NotFound_ReturnsNull() throws ServiceException {
		when(paymentService.getAcceptedPaymentMethods(store)).thenReturn(Collections.emptyList());

		ReadableConfiguration config = paymentFacade.configuration("unknown_module", store);

		assertThat(config).isNull();
	}

	@Test
	@DisplayName("5. configuration(module): Ném ServiceRuntimeException khi truy vấn gặp ServiceException")
	void testConfigurationByModule_ServiceException_ThrowsServiceRuntimeException() throws ServiceException {
		when(paymentService.getAcceptedPaymentMethods(store)).thenThrow(new ServiceException("Error loading module"));

		assertThatThrownBy(() -> paymentFacade.configuration("paypal", store))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).contains("Error while getting payment configuration [paypal]");
					assertThat(sre.getCause()).isInstanceOf(ServiceException.class);
				});
	}

	@Test
	@DisplayName("6. saveConfiguration: Gọi hàm lưu cấu hình không ném ngoại lệ")
	void testSaveConfiguration_DoesNotThrow() {
		paymentFacade.saveConfiguration(new PersistableConfiguration(), store);
	}

	@Test
	@DisplayName("7. deleteConfiguration: Gọi hàm xóa cấu hình không ném ngoại lệ")
	void testDeleteConfiguration_DoesNotThrow() {
		paymentFacade.deleteConfiguration("paypal", store);
	}
}
