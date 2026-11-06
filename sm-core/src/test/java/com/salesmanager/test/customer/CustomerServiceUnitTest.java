package com.salesmanager.test.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.customer.CustomerRepository;
import com.salesmanager.core.business.repositories.customer.attribute.CustomerAttributeRepository;
import com.salesmanager.core.business.repositories.customer.optin.CustomerOptinRepository;
import com.salesmanager.core.business.repositories.customer.review.CustomerReviewRepository;
import com.salesmanager.core.business.services.customer.CustomerServiceImpl;
import com.salesmanager.core.business.services.customer.attribute.CustomerAttributeService;
import com.salesmanager.core.business.services.customer.attribute.CustomerAttributeServiceImpl;
import com.salesmanager.core.business.services.customer.optin.CustomerOptinServiceImpl;
import com.salesmanager.core.business.services.customer.review.CustomerReviewServiceImpl;
import com.salesmanager.core.model.common.Address;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.customer.CustomerCriteria;
import com.salesmanager.core.model.customer.CustomerList;
import com.salesmanager.core.model.customer.attribute.CustomerAttribute;
import com.salesmanager.core.model.customer.review.CustomerReview;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.system.optin.CustomerOptin;
import com.salesmanager.core.modules.utils.GeoLocation;

/**
 * Unit Test độc lập cho tầng Customer Service (sm-core).
 * Bao gồm CustomerServiceImpl, CustomerAttributeServiceImpl, CustomerReviewServiceImpl, CustomerOptinServiceImpl.
 * Sử dụng Mockito + JUnit 5, chạy hoàn toàn in-memory, không kết nối DB hay tải Spring Context.
 */
@ExtendWith(MockitoExtension.class)
public class CustomerServiceUnitTest {

	@Mock
	private CustomerRepository customerRepository;

	@Mock
	private CustomerAttributeRepository customerAttributeRepository;

	@Mock
	private CustomerReviewRepository customerReviewRepository;

	@Mock
	private CustomerOptinRepository customerOptinRepository;

	@Mock
	private CustomerAttributeService customerAttributeServiceMock;

	@Mock
	private GeoLocation geoLocationMock;

	private CustomerServiceImpl customerService;
	private CustomerAttributeServiceImpl customerAttributeService;
	private CustomerReviewServiceImpl customerReviewService;
	private CustomerOptinServiceImpl customerOptinService;

	private MerchantStore store;

	@BeforeEach
	void setUp() {
		// Khởi tạo CustomerServiceImpl và inject các dependency
		customerService = new CustomerServiceImpl(customerRepository);
		setField(customerService, "customerAttributeService", customerAttributeServiceMock);
		setField(customerService, "geoLocation", geoLocationMock);

		// Khởi tạo CustomerAttributeServiceImpl
		customerAttributeService = new CustomerAttributeServiceImpl(customerAttributeRepository);

		// Khởi tạo CustomerReviewServiceImpl và inject CustomerService
		customerReviewService = new CustomerReviewServiceImpl(customerReviewRepository);
		setField(customerReviewService, "customerService", customerService);

		// Khởi tạo CustomerOptinServiceImpl
		customerOptinService = new CustomerOptinServiceImpl(customerOptinRepository);

		// Dữ liệu mẫu MerchantStore
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

	private Customer createSampleCustomer(Long id, String nick, String email) {
		Customer customer = new Customer();
		customer.setId(id);
		customer.setNick(nick);
		customer.setEmailAddress(email);
		customer.setMerchantStore(store);
		return customer;
	}

	// =========================================================================
	// NHÓM 1: LƯU HOẶC CẬP NHẬT KHÁCH HÀNG (saveOrUpdate)
	// =========================================================================

	@Test
	@DisplayName("1.1 saveOrUpdate: Tạo mới khách hàng khi ID là null")
	void testSaveOrUpdate_NewCustomer_NullId_CallsCreate() throws ServiceException {
		Customer customer = createSampleCustomer(null, "john_doe", "john@example.com");

		customerService.saveOrUpdate(customer);

		verify(customerRepository, times(1)).saveAndFlush(customer);
	}

	@Test
	@DisplayName("1.2 saveOrUpdate: Tạo mới khách hàng khi ID bằng 0")
	void testSaveOrUpdate_NewCustomer_ZeroId_CallsCreate() throws ServiceException {
		Customer customer = createSampleCustomer(0L, "john_doe", "john@example.com");

		customerService.saveOrUpdate(customer);

		verify(customerRepository, times(1)).saveAndFlush(customer);
	}

	@Test
	@DisplayName("1.3 saveOrUpdate: Tạo mới khách hàng khi ID mang giá trị âm")
	void testSaveOrUpdate_NewCustomer_NegativeId_CallsCreate() throws ServiceException {
		Customer customer = createSampleCustomer(-5L, "john_doe", "john@example.com");

		customerService.saveOrUpdate(customer);

		verify(customerRepository, times(1)).saveAndFlush(customer);
	}

	@Test
	@DisplayName("1.4 saveOrUpdate: Cập nhật khách hàng khi ID > 0")
	void testSaveOrUpdate_ExistingCustomer_PositiveId_CallsUpdate() throws ServiceException {
		Customer customer = createSampleCustomer(10L, "john_doe", "john@example.com");

		customerService.saveOrUpdate(customer);

		verify(customerRepository, times(1)).saveAndFlush(customer);
	}

	@Test
	@DisplayName("1.5 saveOrUpdate: Ném NullPointerException khi Customer là null")
	void testSaveOrUpdate_NullCustomer_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerService.saveOrUpdate(null))
				.isInstanceOf(NullPointerException.class);

		verify(customerRepository, never()).saveAndFlush(any());
	}

	// =========================================================================
	// NHÓM 2: TRUY VẤN THEO ID, TÊN, NICK VÀ TOKEN
	// =========================================================================

	@Test
	@DisplayName("2.1 getById: Tìm thấy khách hàng theo ID")
	void testGetById_Found_ReturnsCustomer() {
		Customer customer = createSampleCustomer(1L, "john_doe", "john@example.com");
		when(customerRepository.findOne(1L)).thenReturn(customer);

		Customer result = customerService.getById(1L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(1L);
		assertThat(result.getNick()).isEqualTo("john_doe");
	}

	@Test
	@DisplayName("2.2 getById: Không tìm thấy khách hàng trả về null")
	void testGetById_NotFound_ReturnsNull() {
		when(customerRepository.findOne(999L)).thenReturn(null);

		Customer result = customerService.getById(999L);

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("2.3 getByName: Tìm thấy danh sách khách hàng theo tên")
	void testGetByName_Found_ReturnsList() {
		Customer customer = createSampleCustomer(1L, "john_doe", "john@example.com");
		when(customerRepository.findByName("John")).thenReturn(Collections.singletonList(customer));

		List<Customer> result = customerService.getByName("John");

		assertThat(result).isNotNull().hasSize(1);
		assertThat(result.get(0).getNick()).isEqualTo("john_doe");
	}

	@Test
	@DisplayName("2.4 getByName: Không tìm thấy khách hàng theo tên trả về danh sách rỗng")
	void testGetByName_NotFound_ReturnsEmptyList() {
		when(customerRepository.findByName("NonExistent")).thenReturn(Collections.emptyList());

		List<Customer> result = customerService.getByName("NonExistent");

		assertThat(result).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("2.5 getByNick: Tìm thấy khách hàng chỉ với tham số nick")
	void testGetByNick_SingleArg_Success() {
		Customer customer = createSampleCustomer(2L, "alice", "alice@example.com");
		when(customerRepository.findByNick("alice")).thenReturn(customer);

		Customer result = customerService.getByNick("alice");

		assertThat(result).isNotNull();
		assertThat(result.getNick()).isEqualTo("alice");
	}

	@Test
	@DisplayName("2.6 getByNick: Tìm thấy khách hàng với nick và storeId")
	void testGetByNick_WithStoreId_Success() {
		Customer customer = createSampleCustomer(2L, "alice", "alice@example.com");
		when(customerRepository.findByNick("alice", 1)).thenReturn(customer);

		Customer result = customerService.getByNick("alice", 1);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(2L);
	}

	@Test
	@DisplayName("2.7 getByNick: Tìm thấy khách hàng với nick và storeCode")
	void testGetByNick_WithStoreCode_Success() {
		Customer customer = createSampleCustomer(2L, "alice", "alice@example.com");
		when(customerRepository.findByNick("alice", "DEFAULT")).thenReturn(customer);

		Customer result = customerService.getByNick("alice", "DEFAULT");

		assertThat(result).isNotNull();
		assertThat(result.getEmailAddress()).isEqualTo("alice@example.com");
	}

	@Test
	@DisplayName("2.8 getByPasswordResetToken: Tìm thấy khách hàng theo token và storeCode")
	void testGetByPasswordResetToken_Found_ReturnsCustomer() {
		Customer customer = createSampleCustomer(3L, "bob", "bob@example.com");
		when(customerRepository.findByResetPasswordToken("reset-token-xyz", "DEFAULT")).thenReturn(customer);

		Customer result = customerService.getByPasswordResetToken("DEFAULT", "reset-token-xyz");

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(3L);
	}

	@Test
	@DisplayName("2.9 getByPasswordResetToken: Token không hợp lệ trả về null")
	void testGetByPasswordResetToken_NotFound_ReturnsNull() {
		when(customerRepository.findByResetPasswordToken("invalid-token", "DEFAULT")).thenReturn(null);

		Customer result = customerService.getByPasswordResetToken("DEFAULT", "invalid-token");

		assertThat(result).isNull();
	}

	// =========================================================================
	// NHÓM 3: TRUY VẤN THEO CỬA HÀNG VÀ TIÊU CHÍ (getListByStore)
	// =========================================================================

	@Test
	@DisplayName("3.1 getListByStore: Lấy danh sách khách hàng theo Store")
	void testGetListByStore_WithoutCriteria_ReturnsList() {
		Customer c1 = createSampleCustomer(1L, "user1", "user1@example.com");
		Customer c2 = createSampleCustomer(2L, "user2", "user2@example.com");
		when(customerRepository.findByStore(1)).thenReturn(Arrays.asList(c1, c2));

		List<Customer> result = customerService.getListByStore(store);

		assertThat(result).isNotNull().hasSize(2);
		verify(customerRepository, times(1)).findByStore(1);
	}

	@Test
	@DisplayName("3.2 getListByStore: Lấy danh sách khách hàng theo Store và CustomerCriteria")
	void testGetListByStore_WithCriteria_ReturnsCustomerList() {
		CustomerCriteria criteria = new CustomerCriteria();
		criteria.setName("user");
		criteria.setStartIndex(0);
		criteria.setMaxCount(10);

		CustomerList expectedList = new CustomerList();
		Customer c1 = createSampleCustomer(1L, "user1", "user1@example.com");
		expectedList.setCustomers(Collections.singletonList(c1));
		expectedList.setTotalCount(1);

		when(customerRepository.listByStore(store, criteria)).thenReturn(expectedList);

		CustomerList result = customerService.getListByStore(store, criteria);

		assertThat(result).isNotNull();
		assertThat(result.getCustomers()).hasSize(1);
		assertThat(result.getTotalCount()).isEqualTo(1);
		verify(customerRepository, times(1)).listByStore(store, criteria);
	}

	@Test
	@DisplayName("3.3 getListByStore: Ném NullPointerException khi MerchantStore là null")
	void testGetListByStore_NullStore_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerService.getListByStore(null))
				.isInstanceOf(NullPointerException.class);

		verify(customerRepository, never()).findByStore(anyInt());
	}

	// =========================================================================
	// NHÓM 4: XÓA KHÁCH HÀNG VÀ CASCADING ATTRIBUTES (delete)
	// =========================================================================

	@Test
	@DisplayName("4.1 delete: Xóa khách hàng có kèm xóa các CustomerAttribute liên quan")
	void testDelete_WithAttributes_DeletesAttributesThenCustomer() throws ServiceException {
		Customer customer = createSampleCustomer(10L, "john", "john@test.com");
		when(customerRepository.findOne(10L)).thenReturn(customer);

		CustomerAttribute attr1 = new CustomerAttribute();
		attr1.setId(101L);
		CustomerAttribute attr2 = new CustomerAttribute();
		attr2.setId(102L);
		List<CustomerAttribute> attributes = Arrays.asList(attr1, attr2);

		when(customerAttributeServiceMock.getByCustomer(store, customer)).thenReturn(attributes);

		customerService.delete(customer);

		verify(customerAttributeServiceMock, times(1)).delete(attr1);
		verify(customerAttributeServiceMock, times(1)).delete(attr2);
		verify(customerRepository, times(1)).delete(customer);
	}

	@Test
	@DisplayName("4.2 delete: Xóa khách hàng không có CustomerAttribute trực tiếp")
	void testDelete_WithoutAttributes_DeletesCustomerDirectly() throws ServiceException {
		Customer customer = createSampleCustomer(10L, "john", "john@test.com");
		when(customerRepository.findOne(10L)).thenReturn(customer);
		when(customerAttributeServiceMock.getByCustomer(store, customer)).thenReturn(null);

		customerService.delete(customer);

		verify(customerAttributeServiceMock, never()).delete(any());
		verify(customerRepository, times(1)).delete(customer);
	}

	@Test
	@DisplayName("4.3 delete: Ném NullPointerException khi không tìm thấy Customer theo ID trong DB")
	void testDelete_CustomerNotFoundInDb_ThrowsNullPointerException() {
		Customer customer = createSampleCustomer(999L, "ghost", "ghost@test.com");
		when(customerRepository.findOne(999L)).thenReturn(null);

		assertThatThrownBy(() -> customerService.delete(customer))
				.isInstanceOf(NullPointerException.class);

		verify(customerRepository, never()).delete(any(Customer.class));
	}

	// =========================================================================
	// NHÓM 5: ĐỊA CHỈ KHÁCH HÀNG TỪ IP QUA GEOLOCATION (getCustomerAddress)
	// =========================================================================

	@Test
	@DisplayName("5.1 getCustomerAddress: Trả về Address thành công từ IP")
	void testGetCustomerAddress_Success() throws Exception {
		Address address = new Address();
		address.setCity("Montreal");
		address.setCountry("CA");
		address.setPostalCode("H2Y");

		when(geoLocationMock.getAddress("192.168.1.1")).thenReturn(address);

		Address result = customerService.getCustomerAddress(store, "192.168.1.1");

		assertThat(result).isNotNull();
		assertThat(result.getCity()).isEqualTo("Montreal");
		assertThat(result.getCountry()).isEqualTo("CA");
	}

	@Test
	@DisplayName("5.2 getCustomerAddress: Ném ServiceException khi GeoLocation gặp lỗi")
	void testGetCustomerAddress_GeoLocationException_ThrowsServiceException() throws Exception {
		when(geoLocationMock.getAddress("invalid-ip")).thenThrow(new RuntimeException("Geo database unavailable"));

		assertThatThrownBy(() -> customerService.getCustomerAddress(store, "invalid-ip"))
				.isInstanceOf(ServiceException.class)
				.hasCauseInstanceOf(RuntimeException.class);
	}

	// =========================================================================
	// NHÓM 6: CÁC PHƯƠNG THỨC KẾ THỪA TỪ SalesManagerEntityServiceImpl
	// =========================================================================

	@Test
	@DisplayName("6.1 count: Trả về tổng số bản ghi khách hàng")
	void testCount_ReturnsTotalCount() {
		when(customerRepository.count()).thenReturn(25L);

		Long total = customerService.count();

		assertThat(total).isEqualTo(25L);
		verify(customerRepository, times(1)).count();
	}

	@Test
	@DisplayName("6.2 list: Trả về toàn bộ danh sách khách hàng")
	void testList_ReturnsAllCustomers() {
		Customer c1 = createSampleCustomer(1L, "u1", "u1@test.com");
		Customer c2 = createSampleCustomer(2L, "u2", "u2@test.com");
		when(customerRepository.findAll()).thenReturn(Arrays.asList(c1, c2));

		List<Customer> customers = customerService.list();

		assertThat(customers).hasSize(2);
		verify(customerRepository, times(1)).findAll();
	}

	@Test
	@DisplayName("6.3 flush: Thực hiện flush repository")
	void testFlush_CallsRepositoryFlush() {
		customerService.flush();

		verify(customerRepository, times(1)).flush();
	}

	@Test
	@DisplayName("6.4 saveAll: Lưu danh sách nhiều khách hàng")
	void testSaveAll_CallsRepositorySaveAll() throws ServiceException {
		List<Customer> list = Arrays.asList(
				createSampleCustomer(1L, "u1", "u1@test.com"),
				createSampleCustomer(2L, "u2", "u2@test.com")
		);

		customerService.saveAll(list);

		verify(customerRepository, times(1)).saveAll(list);
	}

	// =========================================================================
	// NHÓM 7: CUSTOMER ATTRIBUTE SERVICE (CustomerAttributeServiceImpl)
	// =========================================================================

	@Test
	@DisplayName("7.1 customerAttribute.saveOrUpdate: Lưu thuộc tính khách hàng")
	void testCustomerAttribute_SaveOrUpdate_Success() throws ServiceException {
		CustomerAttribute attr = new CustomerAttribute();
		attr.setId(1L);

		customerAttributeService.saveOrUpdate(attr);

		verify(customerAttributeRepository, times(1)).save(attr);
	}

	@Test
	@DisplayName("7.2 customerAttribute.delete: Nạp lại entity bằng getById trước khi xóa để tránh detached instance")
	void testCustomerAttribute_Delete_ReloadsEntityBeforeDelete() throws ServiceException {
		CustomerAttribute inputAttr = new CustomerAttribute();
		inputAttr.setId(10L);

		CustomerAttribute attachedAttr = new CustomerAttribute();
		attachedAttr.setId(10L);

		when(customerAttributeRepository.getOne(10L)).thenReturn(attachedAttr);

		customerAttributeService.delete(inputAttr);

		verify(customerAttributeRepository, times(1)).delete(attachedAttr);
	}

	@Test
	@DisplayName("7.3 customerAttribute.getByCustomerOptionId: Tìm kiếm thuộc tính theo Store, Customer, Option")
	void testCustomerAttribute_GetByCustomerOptionId() {
		CustomerAttribute attr = new CustomerAttribute();
		attr.setId(5L);
		when(customerAttributeRepository.findByOptionId(1, 100L, 200L)).thenReturn(attr);

		CustomerAttribute result = customerAttributeService.getByCustomerOptionId(store, 100L, 200L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(5L);
	}

	@Test
	@DisplayName("7.4 customerAttribute.getByCustomer: Lấy danh sách thuộc tính của Customer")
	void testCustomerAttribute_GetByCustomer() {
		Customer customer = createSampleCustomer(50L, "test", "test@test.com");
		when(customerAttributeRepository.findByCustomerId(1, 50L)).thenReturn(Collections.singletonList(new CustomerAttribute()));

		List<CustomerAttribute> result = customerAttributeService.getByCustomer(store, customer);

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("7.5 customerAttribute.getByCustomerOptionValueId: Tìm theo optionValueId")
	void testCustomerAttribute_GetByCustomerOptionValueId() {
		when(customerAttributeRepository.findByOptionValueId(1, 300L)).thenReturn(Collections.emptyList());

		List<CustomerAttribute> result = customerAttributeService.getByCustomerOptionValueId(store, 300L);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("7.6 customerAttribute.getByOptionId: Tìm theo optionId")
	void testCustomerAttribute_GetByOptionId() {
		when(customerAttributeRepository.findByOptionId(1, 400L)).thenReturn(Collections.emptyList());

		List<CustomerAttribute> result = customerAttributeService.getByOptionId(store, 400L);

		assertThat(result).isEmpty();
	}

	// =========================================================================
	// NHÓM 8: CUSTOMER REVIEW SERVICE (CustomerReviewServiceImpl)
	// =========================================================================

	@Test
	@DisplayName("8.1 customerReview.create: Đánh giá lần đầu - tính toán số lượng = 1 và điểm trung bình chính xác")
	void testCustomerReview_Create_FirstReview_CalculatesAverageAndCount() throws ServiceException {
		Customer reviewer = createSampleCustomer(1L, "reviewer", "rev@test.com");
		Customer reviewed = createSampleCustomer(2L, "reviewed", "target@test.com");
		reviewed.setCustomerReviewCount(null);
		reviewed.setCustomerReviewAvg(null);

		CustomerReview review = new CustomerReview();
		review.setCustomer(reviewer);
		review.setReviewedCustomer(reviewed);
		review.setReviewRating(4.0);

		when(customerRepository.findOne(2L)).thenReturn(reviewed);

		customerReviewService.create(review);

		assertThat(reviewed.getCustomerReviewCount()).isEqualTo(1);
		assertThat(reviewed.getCustomerReviewAvg()).isEqualByComparingTo(new BigDecimal("4.0"));

		verify(customerReviewRepository, times(1)).saveAndFlush(review);
		verify(customerRepository, times(1)).saveAndFlush(reviewed);
	}

	@Test
	@DisplayName("8.2 customerReview.create: Đánh giá tiếp theo - tính lại điểm trung bình cộng dồn")
	void testCustomerReview_Create_SubsequentReview_RecalculatesAverage() throws ServiceException {
		Customer reviewer = createSampleCustomer(1L, "reviewer", "rev@test.com");
		Customer reviewed = createSampleCustomer(2L, "reviewed", "target@test.com");
		reviewed.setCustomerReviewCount(2);
		reviewed.setCustomerReviewAvg(new BigDecimal("3.0")); // Tổng điểm cũ: 3.0 * 2 = 6.0

		CustomerReview review = new CustomerReview();
		review.setCustomer(reviewer);
		review.setReviewedCustomer(reviewed);
		review.setReviewRating(6.0); // Điểm mới: 6.0 -> Tổng: 12.0 / 3 = 4.0

		when(customerRepository.findOne(2L)).thenReturn(reviewed);

		customerReviewService.create(review);

		assertThat(reviewed.getCustomerReviewCount()).isEqualTo(3);
		assertThat(reviewed.getCustomerReviewAvg()).isEqualByComparingTo(new BigDecimal("4.0"));
		verify(customerReviewRepository, times(1)).saveAndFlush(review);
	}

	@Test
	@DisplayName("8.3 customerReview.create: Ném NullPointerException khi review là null")
	void testCustomerReview_Create_NullReview_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerReviewService.create(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("CustomerReview cannot be null");
	}

	@Test
	@DisplayName("8.4 customerReview.create: Ném NullPointerException khi review.customer là null")
	void testCustomerReview_Create_NullCustomer_ThrowsNullPointerException() {
		CustomerReview review = new CustomerReview();
		review.setCustomer(null);
		review.setReviewedCustomer(createSampleCustomer(2L, "target", "target@test.com"));

		assertThatThrownBy(() -> customerReviewService.create(review))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("CustomerReview.customer cannot be null");
	}

	@Test
	@DisplayName("8.5 customerReview.create: Ném NullPointerException khi review.reviewedCustomer là null")
	void testCustomerReview_Create_NullReviewedCustomer_ThrowsNullPointerException() {
		CustomerReview review = new CustomerReview();
		review.setCustomer(createSampleCustomer(1L, "reviewer", "rev@test.com"));
		review.setReviewedCustomer(null);

		assertThatThrownBy(() -> customerReviewService.create(review))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("CustomerReview.reviewedCustomer cannot be null");
	}

	@Test
	@DisplayName("8.6 customerReview.getByCustomer: Lấy danh sách review theo người viết đánh giá")
	void testCustomerReview_GetByCustomer_Success() {
		Customer customer = createSampleCustomer(10L, "rev", "rev@test.com");
		when(customerReviewRepository.findByReviewer(10L)).thenReturn(Collections.singletonList(new CustomerReview()));

		List<CustomerReview> reviews = customerReviewService.getByCustomer(customer);

		assertThat(reviews).hasSize(1);
		verify(customerReviewRepository, times(1)).findByReviewer(10L);
	}

	@Test
	@DisplayName("8.7 customerReview.getByCustomer: Ném NullPointerException khi customer là null")
	void testCustomerReview_GetByCustomer_NullCustomer_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerReviewService.getByCustomer(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Customer cannot be null");
	}

	@Test
	@DisplayName("8.8 customerReview.getByReviewedCustomer: Lấy danh sách review theo người được đánh giá")
	void testCustomerReview_GetByReviewedCustomer_Success() {
		Customer customer = createSampleCustomer(20L, "target", "target@test.com");
		when(customerReviewRepository.findByReviewed(20L)).thenReturn(Collections.emptyList());

		List<CustomerReview> reviews = customerReviewService.getByReviewedCustomer(customer);

		assertThat(reviews).isEmpty();
		verify(customerReviewRepository, times(1)).findByReviewed(20L);
	}

	@Test
	@DisplayName("8.9 customerReview.getByReviewedCustomer: Ném NullPointerException khi customer là null")
	void testCustomerReview_GetByReviewedCustomer_NullCustomer_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerReviewService.getByReviewedCustomer(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Customer cannot be null");
	}

	// =========================================================================
	// NHÓM 9: CUSTOMER OPTIN SERVICE (CustomerOptinServiceImpl)
	// =========================================================================

	@Test
	@DisplayName("9.1 customerOptin.optinCumtomer: Đăng ký Optin thành công")
	void testCustomerOptin_OptinSuccess() throws ServiceException {
		CustomerOptin optin = new CustomerOptin();

		customerOptinService.optinCumtomer(optin);

		verify(customerOptinRepository, times(1)).save(optin);
	}

	@Test
	@DisplayName("9.2 customerOptin.optinCumtomer: Ném NullPointerException khi optin là null")
	void testCustomerOptin_OptinNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerOptinService.optinCumtomer(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("CustomerOptin must not be null");
	}

	@Test
	@DisplayName("9.3 customerOptin.optoutCumtomer: Hủy đăng ký Optin thành công")
	void testCustomerOptin_OptoutSuccess() throws ServiceException {
		CustomerOptin optin = new CustomerOptin();

		customerOptinService.optoutCumtomer(optin);

		verify(customerOptinRepository, times(1)).delete(optin);
	}

	@Test
	@DisplayName("9.4 customerOptin.optoutCumtomer: Ném NullPointerException khi optin là null")
	void testCustomerOptin_OptoutNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> customerOptinService.optoutCumtomer(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("CustomerOptin must not be null");
	}

	@Test
	@DisplayName("9.5 customerOptin.findByEmailAddress: Tìm kiếm Optin theo Store, email và code")
	void testCustomerOptin_FindByEmailAddress_Success() throws ServiceException {
		CustomerOptin optin = new CustomerOptin();
		when(customerOptinRepository.findByMerchantAndCodeAndEmail(1, "NEWSLETTER", "user@test.com"))
				.thenReturn(optin);

		CustomerOptin result = customerOptinService.findByEmailAddress(store, "user@test.com", "NEWSLETTER");

		assertThat(result).isNotNull();
		verify(customerOptinRepository, times(1))
				.findByMerchantAndCodeAndEmail(1, "NEWSLETTER", "user@test.com");
	}
}
