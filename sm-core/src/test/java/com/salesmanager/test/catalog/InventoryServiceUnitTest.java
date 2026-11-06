package com.salesmanager.test.catalog;

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
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.catalog.product.availability.PageableProductAvailabilityRepository;
import com.salesmanager.core.business.repositories.catalog.product.availability.ProductAvailabilityRepository;
import com.salesmanager.core.business.services.catalog.inventory.ProductInventoryServiceImpl;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityServiceImpl;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.inventory.ProductInventory;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.catalog.product.variant.ProductVariant;
import com.salesmanager.core.model.merchant.MerchantStore;

/**
 * Unit Test độc lập cho chức năng Inventory Service - Quản lý tồn kho trong sm-core.
 * Bao phủ:
 * 1. ProductAvailabilityService (ProductAvailabilityServiceImpl): CRUD tồn kho, phân trang,
 *    truy vấn theo SKU, Product ID, Store và các ràng buộc dữ liệu đầu vào.
 * 2. ProductInventoryService (ProductInventoryServiceImpl): Tính toán số lượng tồn kho và
 *    giá thành phẩm khả dụng theo vùng (region) cho Product và ProductVariant.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("inventory")
@DisplayName("Inventory Service - Quản lý tồn kho Unit Tests")
public class InventoryServiceUnitTest {

	@Mock
	private ProductAvailabilityRepository productAvailabilityRepository;

	@Mock
	private PageableProductAvailabilityRepository pageableProductAvailabilityRepository;

	@Mock
	private PricingService pricingService;

	private ProductAvailabilityServiceImpl productAvailabilityService;
	private ProductInventoryServiceImpl inventoryService;

	private MerchantStore store;

	@BeforeEach
	void setUp() {
		productAvailabilityService = new ProductAvailabilityServiceImpl(productAvailabilityRepository);
		setField(productAvailabilityService, "pageableProductAvailabilityRepository", pageableProductAvailabilityRepository);

		inventoryService = new ProductInventoryServiceImpl();
		setField(inventoryService, "pricingService", pricingService);

		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");
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

	private ProductAvailability createSampleAvailability(Long id, int quantity, String region) {
		ProductAvailability availability = new ProductAvailability();
		availability.setId(id);
		availability.setProductQuantity(quantity);
		availability.setRegion(region);
		availability.setMerchantStore(store);
		return availability;
	}

	private FinalPrice createSamplePrice(BigDecimal amount) {
		FinalPrice finalPrice = new FinalPrice();
		finalPrice.setFinalPrice(amount);
		finalPrice.setOriginalPrice(amount);
		return finalPrice;
	}

	// =========================================================================
	// NHÓM 1: LƯU & CẬP NHẬT TỒN KHO (ProductAvailabilityService.saveOrUpdate)
	// =========================================================================

	@Test
	@DisplayName("1.1 saveOrUpdate: Tạo mới bản ghi tồn kho khi id là null (calls create)")
	void testSaveOrUpdate_NewAvailability_NullId_CallsCreate() throws ServiceException {
		ProductAvailability availability = createSampleAvailability(null, 25, Constants.ALL_REGIONS);

		ProductAvailability result = productAvailabilityService.saveOrUpdate(availability);

		assertThat(result).isNotNull();
		verify(productAvailabilityRepository, times(1)).saveAndFlush(availability);
	}

	@Test
	@DisplayName("1.2 saveOrUpdate: Tạo mới bản ghi tồn kho khi id bằng 0 (calls create)")
	void testSaveOrUpdate_NewAvailability_ZeroId_CallsCreate() throws ServiceException {
		ProductAvailability availability = createSampleAvailability(0L, 10, Constants.ALL_REGIONS);

		ProductAvailability result = productAvailabilityService.saveOrUpdate(availability);

		assertThat(result).isNotNull();
		verify(productAvailabilityRepository, times(1)).saveAndFlush(availability);
	}

	@Test
	@DisplayName("1.3 saveOrUpdate: Cập nhật bản ghi tồn kho khi id > 0 (calls update)")
	void testSaveOrUpdate_ExistingAvailability_PositiveId_CallsUpdate() throws ServiceException {
		ProductAvailability availability = createSampleAvailability(100L, 50, Constants.ALL_REGIONS);

		ProductAvailability result = productAvailabilityService.saveOrUpdate(availability);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(100L);
		verify(productAvailabilityRepository, times(1)).saveAndFlush(availability);
	}

	@Test
	@DisplayName("1.4 saveOrUpdate: Xử lý id âm như bản ghi mới (calls create)")
	void testSaveOrUpdate_NegativeId_TreatedAsNewEntity() throws ServiceException {
		ProductAvailability availability = createSampleAvailability(-1L, 5, Constants.ALL_REGIONS);

		productAvailabilityService.saveOrUpdate(availability);

		verify(productAvailabilityRepository, times(1)).saveAndFlush(availability);
	}

	// =========================================================================
	// NHÓM 2: TRUY VẤN THEO ID & STORE (ProductAvailabilityService.getById)
	// =========================================================================

	@Test
	@DisplayName("2.1 getById: Ném NullPointerException khi MerchantStore là null")
	void testGetById_StoreNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productAvailabilityService.getById(1L, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Merchant must not be null");
	}

	@Test
	@DisplayName("2.2 getById: Tìm thấy bản ghi tồn kho trả về Optional chứa ProductAvailability")
	void testGetById_AvailabilityFound_ReturnsOptionalOfAvailability() {
		ProductAvailability expected = createSampleAvailability(10L, 100, Constants.ALL_REGIONS);
		when(productAvailabilityRepository.getById(10L)).thenReturn(expected);

		Optional<ProductAvailability> result = productAvailabilityService.getById(10L, store);

		assertThat(result).isPresent();
		assertThat(result.get().getId()).isEqualTo(10L);
		assertThat(result.get().getProductQuantity()).isEqualTo(100);
	}

	@Test
	@DisplayName("2.3 getById: Không tìm thấy bản ghi tồn kho trả về Optional.empty()")
	void testGetById_AvailabilityNotFound_ReturnsOptionalEmpty() {
		when(productAvailabilityRepository.getById(999L)).thenReturn(null);

		Optional<ProductAvailability> result = productAvailabilityService.getById(999L, store);

		assertThat(result).isEmpty();
	}

	// =========================================================================
	// NHÓM 3: TRUY VẤN THEO PRODUCT ID & PHÂN TRANG (listByProduct)
	// =========================================================================

	@Test
	@DisplayName("3.1 listByProduct: Ném NullPointerException khi productId là null")
	void testListByProduct_ProductIdNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productAvailabilityService.listByProduct(null, store, 0, 10))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Product cannot be null");
	}

	@Test
	@DisplayName("3.2 listByProduct: Ném NullPointerException khi MerchantStore là null")
	void testListByProduct_StoreNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productAvailabilityService.listByProduct(1L, null, 0, 10))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MercantStore cannot be null");
	}

	@Test
	@DisplayName("3.3 listByProduct: Trả về Page<ProductAvailability> phân trang hợp lệ")
	void testListByProduct_ValidInputs_ReturnsPage() {
		ProductAvailability avail = createSampleAvailability(201L, 40, Constants.ALL_REGIONS);
		Page<ProductAvailability> pageResult = new PageImpl<>(Collections.singletonList(avail), PageRequest.of(0, 10), 1);

		when(pageableProductAvailabilityRepository.getByProductId(eq(501L), eq(store.getCode()), any(Pageable.class)))
				.thenReturn(pageResult);

		Page<ProductAvailability> result = productAvailabilityService.listByProduct(501L, store, 0, 10);

		assertThat(result).isNotNull();
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getId()).isEqualTo(201L);
		assertThat(result.getTotalElements()).isEqualTo(1);
	}

	// =========================================================================
	// NHÓM 4: TRUY VẤN TỒN KHO THEO SKU (getBySku)
	// =========================================================================

	@Test
	@DisplayName("4.1 getBySku (Store & Paging): Ném NullPointerException khi store là null")
	void testGetBySku_StoreAndPaging_StoreNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productAvailabilityService.getBySku("SKU-TEST", null, 0, 10))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MerchantStore cannot be null");
	}

	@Test
	@DisplayName("4.2 getBySku (Store & Paging): Trả về Page phân trang chính xác")
	void testGetBySku_StoreAndPaging_ValidInputs_ReturnsPage() {
		ProductAvailability avail = createSampleAvailability(301L, 15, Constants.ALL_REGIONS);
		Page<ProductAvailability> pageResult = new PageImpl<>(Collections.singletonList(avail), PageRequest.of(0, 5), 1);

		when(pageableProductAvailabilityRepository.getBySku(eq("SKU-100"), eq(store.getCode()), any(Pageable.class)))
				.thenReturn(pageResult);

		Page<ProductAvailability> result = productAvailabilityService.getBySku("SKU-100", store, 0, 5);

		assertThat(result).isNotNull();
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getProductQuantity()).isEqualTo(15);
	}

	@Test
	@DisplayName("4.3 getBySku (Chỉ Paging): Trả về Page không lọc theo store")
	void testGetBySku_OnlyPaging_ReturnsPage() {
		ProductAvailability avail = createSampleAvailability(302L, 80, Constants.ALL_REGIONS);
		Page<ProductAvailability> pageResult = new PageImpl<>(Collections.singletonList(avail), PageRequest.of(0, 10), 1);

		when(pageableProductAvailabilityRepository.getBySku(eq("SKU-GLOBAL"), any(Pageable.class)))
				.thenReturn(pageResult);

		Page<ProductAvailability> result = productAvailabilityService.getBySku("SKU-GLOBAL", 0, 10);

		assertThat(result).isNotNull();
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getId()).isEqualTo(302L);
	}

	@Test
	@DisplayName("4.4 getBySku (List & Store): Ném NullPointerException khi store là null")
	void testGetBySku_StoreWithoutPaging_StoreNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productAvailabilityService.getBySku("SKU-NO-STORE", (MerchantStore) null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MerchantStore cannot be null");
	}

	@Test
	@DisplayName("4.5 getBySku (List & Store): Trả về danh sách tồn kho khớp SKU")
	void testGetBySku_StoreWithoutPaging_ValidInputs_ReturnsList() {
		ProductAvailability avail1 = createSampleAvailability(303L, 30, "VN");
		ProductAvailability avail2 = createSampleAvailability(304L, 50, Constants.ALL_REGIONS);

		when(productAvailabilityRepository.getBySku("SKU-LIST", store.getCode()))
				.thenReturn(Arrays.asList(avail1, avail2));

		List<ProductAvailability> results = productAvailabilityService.getBySku("SKU-LIST", store);

		assertThat(results).hasSize(2);
		assertThat(results).extracting(ProductAvailability::getId).containsExactlyInAnyOrder(303L, 304L);
	}

	// =========================================================================
	// NHÓM 5: GENERIC ENTITY SERVICE (SalesManagerEntityService)
	// =========================================================================

	@Test
	@DisplayName("5.1 getById: Lấy thực thể tồn kho theo getOne")
	void testGetById_GenericMethod_ReturnsAvailability() {
		ProductAvailability avail = createSampleAvailability(401L, 60, Constants.ALL_REGIONS);
		when(productAvailabilityRepository.getOne(401L)).thenReturn(avail);

		ProductAvailability result = productAvailabilityService.getById(401L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(401L);
		verify(productAvailabilityRepository, times(1)).getOne(401L);
	}

	@Test
	@DisplayName("5.2 delete: Xóa bản ghi tồn kho qua repository")
	void testDelete_CallsRepositoryDelete() throws ServiceException {
		ProductAvailability avail = createSampleAvailability(402L, 10, Constants.ALL_REGIONS);

		productAvailabilityService.delete(avail);

		verify(productAvailabilityRepository, times(1)).delete(avail);
	}

	@Test
	@DisplayName("5.3 list: Lấy toàn bộ danh sách tồn kho")
	void testList_ReturnsAllAvailabilities() {
		ProductAvailability a1 = createSampleAvailability(403L, 10, Constants.ALL_REGIONS);
		ProductAvailability a2 = createSampleAvailability(404L, 20, Constants.ALL_REGIONS);
		when(productAvailabilityRepository.findAll()).thenReturn(Arrays.asList(a1, a2));

		List<ProductAvailability> results = productAvailabilityService.list();

		assertThat(results).hasSize(2);
		verify(productAvailabilityRepository, times(1)).findAll();
	}

	@Test
	@DisplayName("5.4 count: Đếm tổng số lượng bản ghi tồn kho")
	void testCount_ReturnsTotalCount() {
		when(productAvailabilityRepository.count()).thenReturn(120L);

		Long count = productAvailabilityService.count();

		assertThat(count).isEqualTo(120L);
		verify(productAvailabilityRepository, times(1)).count();
	}

	@Test
	@DisplayName("5.5 flush: Đồng bộ cache xuống DB")
	void testFlush_CallsRepositoryFlush() {
		productAvailabilityService.flush();

		verify(productAvailabilityRepository, times(1)).flush();
	}

	@Test
	@DisplayName("5.6 saveAll: Lưu hàng loạt các bản ghi tồn kho")
	void testSaveAll_CallsRepositorySaveAll() throws ServiceException {
		List<ProductAvailability> list = Arrays.asList(
				createSampleAvailability(405L, 5, Constants.ALL_REGIONS),
				createSampleAvailability(406L, 15, Constants.ALL_REGIONS)
		);

		productAvailabilityService.saveAll(list);

		verify(productAvailabilityRepository, times(1)).saveAll(list);
	}

	// =========================================================================
	// NHÓM 6: TÍNH TOÁN TỒN KHO SẢN PHẨM ĐƠN LẺ (ProductInventoryService.inventory)
	// =========================================================================

	@Test
	@DisplayName("6.1 inventory(Product): Ném IllegalArgumentException khi availabilities là null")
	void testProductInventory_AvailabilitiesNull_ThrowsIllegalArgumentException() {
		Product product = new Product();
		product.setSku("PROD-NO-AVAIL");
		product.setAvailabilities(null);

		assertThatThrownBy(() -> inventoryService.inventory(product))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("6.2 inventory(Product): Ưu tiên chọn availability có region ALL_REGIONS (*)")
	void testProductInventory_ProductWithAllRegions_PicksAllRegionsAvailability() throws ServiceException {
		Product product = new Product();
		product.setSku("PROD-MULTI-REG");

		ProductAvailability caAvail = createSampleAvailability(1L, 5, "CA");
		ProductAvailability allAvail = createSampleAvailability(2L, 25, Constants.ALL_REGIONS);

		Set<ProductAvailability> availabilities = new HashSet<>(Arrays.asList(caAvail, allAvail));
		product.setAvailabilities(availabilities);

		FinalPrice price = createSamplePrice(new BigDecimal("99.99"));
		when(pricingService.calculateProductPrice(product)).thenReturn(price);

		ProductInventory inventory = inventoryService.inventory(product);

		assertThat(inventory).isNotNull();
		assertThat(inventory.getSku()).isEqualTo("PROD-MULTI-REG");
		assertThat(inventory.getQuantity()).isEqualTo(25); // Chọn ALL_REGIONS
		assertThat(inventory.getPrice()).isEqualTo(price);
	}

	@Test
	@DisplayName("6.3 inventory(Product): Chọn availability đầu tiên khi không có ALL_REGIONS")
	void testProductInventory_ProductWithSpecificRegionOnly_PicksFirstAvailability() throws ServiceException {
		Product product = new Product();
		product.setSku("PROD-SPECIFIC-REG");

		ProductAvailability vnAvail = createSampleAvailability(3L, 70, "VN");
		Set<ProductAvailability> availabilities = new HashSet<>(Collections.singletonList(vnAvail));
		product.setAvailabilities(availabilities);

		FinalPrice price = createSamplePrice(new BigDecimal("49.00"));
		when(pricingService.calculateProductPrice(product)).thenReturn(price);

		ProductInventory inventory = inventoryService.inventory(product);

		assertThat(inventory).isNotNull();
		assertThat(inventory.getSku()).isEqualTo("PROD-SPECIFIC-REG");
		assertThat(inventory.getQuantity()).isEqualTo(70);
		assertThat(inventory.getPrice()).isEqualTo(price);
	}

	@Test
	@DisplayName("6.4 inventory(Product): Trả về số lượng 0 khi sản phẩm hết hàng")
	void testProductInventory_ZeroQuantity_ReturnsInventoryWithZeroQuantity() throws ServiceException {
		Product product = new Product();
		product.setSku("PROD-OUT-OF-STOCK");

		ProductAvailability avail = createSampleAvailability(4L, 0, Constants.ALL_REGIONS);
		product.setAvailabilities(new HashSet<>(Collections.singletonList(avail)));

		FinalPrice price = createSamplePrice(new BigDecimal("19.99"));
		when(pricingService.calculateProductPrice(product)).thenReturn(price);

		ProductInventory inventory = inventoryService.inventory(product);

		assertThat(inventory).isNotNull();
		assertThat(inventory.getQuantity()).isEqualTo(0);
	}

	// =========================================================================
	// NHÓM 7: TÍNH TOÁN TỒN KHO BIẾN THỂ SẢN PHẨM (inventory(ProductVariant))
	// =========================================================================

	@Test
	@DisplayName("7.1 inventory(Variant): Ném IllegalArgumentException khi availabilities của variant là null")
	void testVariantInventory_VariantAvailabilitiesNull_ThrowsIllegalArgumentException() {
		ProductVariant variant = new ProductVariant();
		variant.setAvailabilities(null);
		variant.setProduct(new Product());

		assertThatThrownBy(() -> inventoryService.inventory(variant))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("7.2 inventory(Variant): Ném IllegalArgumentException khi product cha của variant là null")
	void testVariantInventory_VariantProductNull_ThrowsIllegalArgumentException() {
		ProductVariant variant = new ProductVariant();
		variant.setAvailabilities(new HashSet<>());
		variant.setProduct(null);

		assertThatThrownBy(() -> inventoryService.inventory(variant))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("7.3 inventory(Variant): Sử dụng availability riêng của variant khi có tồn kho riêng")
	void testVariantInventory_VariantHasSpecificAvailabilities_UsesVariantAvailability() throws ServiceException {
		Product parentProduct = new Product();
		parentProduct.setSku("PARENT-PROD");

		ProductVariant variant = new ProductVariant();
		variant.setSku("VAR-RED-XL");
		variant.setProduct(parentProduct);

		ProductAvailability varAvail = createSampleAvailability(11L, 18, Constants.ALL_REGIONS);
		variant.setAvailabilities(new HashSet<>(Collections.singletonList(varAvail)));

		FinalPrice variantPrice = createSamplePrice(new BigDecimal("129.00"));
		when(pricingService.calculateProductPrice(variant)).thenReturn(variantPrice);

		ProductInventory inventory = inventoryService.inventory(variant);

		assertThat(inventory).isNotNull();
		assertThat(inventory.getSku()).isEqualTo("VAR-RED-XL");
		assertThat(inventory.getQuantity()).isEqualTo(18);
		assertThat(inventory.getPrice()).isEqualTo(variantPrice);
	}

	@Test
	@DisplayName("7.4 inventory(Variant): Tự động fallback về availability của Product cha khi variant không có tồn kho riêng")
	void testVariantInventory_VariantAvailabilitiesEmpty_FallsBackToParentProductAvailabilities() throws ServiceException {
		Product parentProduct = new Product();
		parentProduct.setSku("PARENT-PROD");
		ProductAvailability parentAvail = createSampleAvailability(12L, 55, Constants.ALL_REGIONS);
		parentProduct.setAvailabilities(new HashSet<>(Collections.singletonList(parentAvail)));

		ProductVariant variant = new ProductVariant();
		variant.setSku("VAR-BLUE-M");
		variant.setProduct(parentProduct);
		variant.setAvailabilities(new HashSet<>()); // Rỗng

		FinalPrice variantPrice = createSamplePrice(new BigDecimal("119.00"));
		when(pricingService.calculateProductPrice(variant)).thenReturn(variantPrice);

		ProductInventory inventory = inventoryService.inventory(variant);

		assertThat(inventory).isNotNull();
		assertThat(inventory.getSku()).isEqualTo("VAR-BLUE-M");
		assertThat(inventory.getQuantity()).isEqualTo(55); // Lấy từ parent
		assertThat(inventory.getPrice()).isEqualTo(variantPrice);
	}

	@Test
	@DisplayName("7.5 inventory(Variant): Fallback về giá của Product cha khi calculateProductPrice(variant) trả về null")
	void testVariantInventory_VariantPriceNull_FallsBackToParentProductPrice() throws ServiceException {
		Product parentProduct = new Product();
		parentProduct.setSku("PARENT-PROD");
		ProductAvailability parentAvail = createSampleAvailability(13L, 30, Constants.ALL_REGIONS);
		parentProduct.setAvailabilities(new HashSet<>(Collections.singletonList(parentAvail)));

		ProductVariant variant = new ProductVariant();
		variant.setSku("VAR-GREEN-S");
		variant.setProduct(parentProduct);
		variant.setAvailabilities(new HashSet<>());

		FinalPrice parentPrice = createSamplePrice(new BigDecimal("89.00"));
		when(pricingService.calculateProductPrice(variant)).thenReturn(null);
		when(pricingService.calculateProductPrice(parentProduct)).thenReturn(parentPrice);

		ProductInventory inventory = inventoryService.inventory(variant);

		assertThat(inventory).isNotNull();
		assertThat(inventory.getSku()).isEqualTo("VAR-GREEN-S");
		assertThat(inventory.getQuantity()).isEqualTo(30);
		assertThat(inventory.getPrice()).isEqualTo(parentPrice); // Lấy giá từ parent
	}
}
