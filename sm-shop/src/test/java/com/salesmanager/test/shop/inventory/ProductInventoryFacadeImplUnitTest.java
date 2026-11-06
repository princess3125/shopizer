package com.salesmanager.test.shop.inventory;

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

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.catalog.product.variant.ProductVariantService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.mapper.inventory.PersistableInventoryMapper;
import com.salesmanager.shop.mapper.inventory.ReadableInventoryMapper;
import com.salesmanager.shop.model.catalog.product.inventory.PersistableInventory;
import com.salesmanager.shop.model.catalog.product.inventory.ReadableInventory;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.facade.product.ProductInventoryFacadeImpl;

/**
 * Unit Test độc lập cho ProductInventoryFacadeImpl (Tầng Facade quản lý tồn kho trong sm-shop).
 * Sử dụng Mockito cô lập hoàn toàn các service và mapper, không cần DB hay Spring Context.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("inventory")
@DisplayName("ProductInventoryFacade - Tầng Facade Quản lý tồn kho Unit Tests")
public class ProductInventoryFacadeImplUnitTest {

	@Mock
	private ProductAvailabilityService productAvailabilityService;

	@Mock
	private ProductService productService;

	@Mock
	private ProductVariantService productVariantService;

	@Mock
	private ReadableInventoryMapper readableInventoryMapper;

	@Mock
	private PersistableInventoryMapper productInventoryMapper;

	@InjectMocks
	private ProductInventoryFacadeImpl inventoryFacade;

	private MerchantStore store;
	private Language language;

	@BeforeEach
	void setUp() {
		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");

		language = new Language();
		language.setId(1);
		language.setCode("en");
		store.setDefaultLanguage(language);
	}

	private ProductAvailability createSampleAvailability(Long id, Product product, int quantity) {
		ProductAvailability availability = new ProductAvailability();
		availability.setId(id);
		availability.setProduct(product);
		availability.setProductQuantity(quantity);
		availability.setRegion(Constants.ALL_REGIONS);
		availability.setMerchantStore(store);
		return availability;
	}

	// =========================================================================
	// NHÓM 1: THÊM TỒN KHO MỚI (add)
	// =========================================================================

	@Test
	@DisplayName("1.1 add: Thêm tồn kho thành công và chuyển đổi sang ReadableInventory")
	void testAdd_Success() throws ServiceException {
		PersistableInventory persistable = new PersistableInventory();
		persistable.setQuantity(50);

		ProductAvailability availability = createSampleAvailability(10L, new Product(), 50);
		ReadableInventory readable = new ReadableInventory();
		readable.setId(10L);
		readable.setQuantity(50);

		when(productInventoryMapper.convert(persistable, store, language)).thenReturn(availability);
		when(productAvailabilityService.saveOrUpdate(availability)).thenReturn(availability);
		when(readableInventoryMapper.convert(availability, store, language)).thenReturn(readable);

		ReadableInventory result = inventoryFacade.add(persistable, store, language);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(10L);
		assertThat(result.getQuantity()).isEqualTo(50);
		verify(productAvailabilityService, times(1)).saveOrUpdate(availability);
	}

	@Test
	@DisplayName("1.2 add: Ném NullPointerException khi MerchantStore là null")
	void testAdd_StoreNull_ThrowsNullPointerException() {
		PersistableInventory persistable = new PersistableInventory();

		assertThatThrownBy(() -> inventoryFacade.add(persistable, null, language))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MerchantStore cannot be null");
	}

	@Test
	@DisplayName("1.3 add: Ném ServiceRuntimeException khi saveOrUpdate gặp ServiceException")
	void testAdd_ServiceException_ThrowsServiceRuntimeException() throws ServiceException {
		PersistableInventory persistable = new PersistableInventory();
		ProductAvailability availability = createSampleAvailability(null, new Product(), 10);

		when(productInventoryMapper.convert(persistable, store, language)).thenReturn(availability);
		doThrow(new ServiceException("DB error")).when(productAvailabilityService).saveOrUpdate(availability);

		assertThatThrownBy(() -> inventoryFacade.add(persistable, store, language))
				.isInstanceOf(ServiceRuntimeException.class)
				.satisfies(e -> {
					ServiceRuntimeException sre = (ServiceRuntimeException) e;
					assertThat(sre.getErrorMessage()).isEqualTo("Cannot create Inventory");
					assertThat(sre.getCause()).isInstanceOf(ServiceException.class);
				});
	}

	// =========================================================================
	// NHÓM 2: LẤY TỒN KHO THEO ID (get)
	// =========================================================================

	@Test
	@DisplayName("2.1 get(inventoryId): Tìm thấy tồn kho trả về ReadableInventory")
	void testGetById_Success() {
		ProductAvailability availability = createSampleAvailability(20L, new Product(), 100);
		ReadableInventory readable = new ReadableInventory();
		readable.setId(20L);

		when(productAvailabilityService.getById(20L, store)).thenReturn(Optional.of(availability));
		when(readableInventoryMapper.convert(availability, store, language)).thenReturn(readable);

		ReadableInventory result = inventoryFacade.get(20L, store, language);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(20L);
	}

	@Test
	@DisplayName("2.2 get(inventoryId): Ném ResourceNotFoundException khi không tìm thấy")
	void testGetById_NotFound_ThrowsResourceNotFoundException() {
		when(productAvailabilityService.getById(999L, store)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> inventoryFacade.get(999L, store, language))
				.isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> {
					ResourceNotFoundException rnfe = (ResourceNotFoundException) e;
					assertThat(rnfe.getErrorMessage()).contains("Inventory with id [999] not found");
				});
	}

	// =========================================================================
	// NHÓM 3: XÓA TỒN KHO (delete)
	// =========================================================================

	@Test
	@DisplayName("3.1 delete: Xóa tồn kho thành công khi productId và inventoryId khớp")
	void testDelete_Success() throws ServiceException {
		Long productId = 50L;
		Product product = new Product();
		product.setId(productId);

		ProductAvailability availability = createSampleAvailability(30L, product, 10);

		when(productAvailabilityService.getById(30L, store)).thenReturn(Optional.of(availability));

		inventoryFacade.delete(productId, 30L, store);

		verify(productAvailabilityService, times(1)).delete(availability);
	}

	@Test
	@DisplayName("3.2 delete: Ném ResourceNotFoundException khi inventoryId không tồn tại")
	void testDelete_NotFound_ThrowsResourceNotFoundException() throws Exception {
		when(productAvailabilityService.getById(30L, store)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> inventoryFacade.delete(50L, 30L, store))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(productAvailabilityService, never()).delete(any());
	}

	@Test
	@DisplayName("3.3 delete: Ném ResourceNotFoundException khi productId không khớp với bản ghi tồn kho")
	void testDelete_ProductIdMismatch_ThrowsResourceNotFoundException() throws Exception {
		Product product = new Product();
		product.setId(50L);

		ProductAvailability availability = createSampleAvailability(30L, product, 10);

		when(productAvailabilityService.getById(30L, store)).thenReturn(Optional.of(availability));

		// Gọi xóa với productId = 60L (khác 50L)
		assertThatThrownBy(() -> inventoryFacade.delete(60L, 30L, store))
				.isInstanceOf(ResourceNotFoundException.class);

		verify(productAvailabilityService, never()).delete(any());
	}

	// =========================================================================
	// NHÓM 4: TRUY VẤN THEO SKU (get)
	// =========================================================================

	@Test
	@DisplayName("4.1 get(sku): Ném NullPointerException khi SKU là null")
	void testGetBySku_SkuNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> inventoryFacade.get((String) null, store, language, 0, 10))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Product sku cannot be null");
	}

	@Test
	@DisplayName("4.2 get(sku): Ném NullPointerException khi MerchantStore là null")
	void testGetBySku_StoreNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> inventoryFacade.get("SKU-1", null, language, 0, 10))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MerchantStore code cannot be null");
	}

	@Test
	@DisplayName("4.3 get(sku): Trả về ReadableEntityList khi tìm thấy tồn kho theo SKU")
	void testGetBySku_Success() {
		Product product = new Product();
		product.setId(100L);
		ProductAvailability avail = createSampleAvailability(40L, product, 15);
		Page<ProductAvailability> page = new PageImpl<>(Collections.singletonList(avail));

		ReadableInventory readable = new ReadableInventory();
		readable.setId(40L);

		when(productAvailabilityService.getBySku("SKU-1", 0, 10)).thenReturn(page);
		when(readableInventoryMapper.convert(avail, store, language)).thenReturn(readable);

		ReadableEntityList<ReadableInventory> results = inventoryFacade.get("SKU-1", store, language, 0, 10);

		assertThat(results).isNotNull();
		assertThat(results.getItems()).hasSize(1);
		assertThat(results.getItems().get(0).getId()).isEqualTo(40L);
	}

	@Test
	@DisplayName("4.4 get(sku): Tự động fallback lấy tồn kho của Product khi biến thể chưa có tồn kho riêng")
	void testGetBySku_EmptyAvailabilities_FallsBackToProductAvailabilities() throws ServiceException {
		Page<ProductAvailability> emptyPage = new PageImpl<>(Collections.emptyList());
		when(productAvailabilityService.getBySku("SKU-VAR", 0, 10)).thenReturn(emptyPage);

		Product parentProduct = new Product();
		parentProduct.setId(101L);
		ProductAvailability parentAvail = createSampleAvailability(41L, parentProduct, 80);
		parentProduct.setAvailabilities(new HashSet<>(Collections.singletonList(parentAvail)));

		when(productService.getBySku("SKU-VAR", store)).thenReturn(parentProduct);

		ReadableInventory readable = new ReadableInventory();
		readable.setId(41L);
		when(readableInventoryMapper.convert(parentAvail, store, language)).thenReturn(readable);

		ReadableEntityList<ReadableInventory> results = inventoryFacade.get("SKU-VAR", store, language, 0, 10);

		assertThat(results).isNotNull();
		assertThat(results.getItems()).hasSize(1);
		assertThat(results.getItems().get(0).getId()).isEqualTo(41L);
	}

	// =========================================================================
	// NHÓM 5: TRUY VẤN THEO PRODUCT ID (get)
	// =========================================================================

	@Test
	@DisplayName("5.1 get(productId): Ném NullPointerException khi productId là null")
	void testGetByProductId_ProductIdNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> inventoryFacade.get((Long) null, store, language, 0, 10))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Product id cannot be null");
	}

	@Test
	@DisplayName("5.2 get(productId): Trả về danh sách tồn kho phân trang theo Product ID")
	void testGetByProductId_Success() {
		Product product = new Product();
		product.setId(200L);
		ProductAvailability avail = createSampleAvailability(50L, product, 30);
		Page<ProductAvailability> page = new PageImpl<>(Collections.singletonList(avail));

		ReadableInventory readable = new ReadableInventory();
		readable.setId(50L);

		when(productAvailabilityService.listByProduct(200L, store, 0, 10)).thenReturn(page);
		when(readableInventoryMapper.convert(avail, store, language)).thenReturn(readable);

		ReadableEntityList<ReadableInventory> results = inventoryFacade.get(200L, store, language, 0, 10);

		assertThat(results).isNotNull();
		assertThat(results.getItems()).hasSize(1);
		assertThat(results.getItems().get(0).getId()).isEqualTo(50L);
	}

	// =========================================================================
	// NHÓM 6: CẬP NHẬT TỒN KHO (update)
	// =========================================================================

	@Test
	@DisplayName("6.1 update: Cập nhật thành công tồn kho cho Product")
	void testUpdate_Success_ForProduct() throws ServiceException {
		PersistableInventory persistable = new PersistableInventory();
		persistable.setId(60L);
		persistable.setProductId(700L);
		persistable.setQuantity(65);

		Product product = new Product();
		product.setId(700L);
		ProductAvailability existingAvail = createSampleAvailability(60L, product, 20);
		Set<ProductAvailability> availabilities = new HashSet<>(Collections.singletonList(existingAvail));
		product.setAvailabilities(availabilities);

		when(productService.retrieveById(700L, store)).thenReturn(Optional.of(product));
		when(productInventoryMapper.merge(persistable, existingAvail, store, language)).thenReturn(existingAvail);

		inventoryFacade.update(persistable, store, language);

		verify(productAvailabilityService, times(1)).saveOrUpdate(existingAvail);
	}

	@Test
	@DisplayName("6.2 update: Ném NullPointerException khi inventory là null")
	void testUpdate_InventoryNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> inventoryFacade.update(null, store, language))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Inventory cannot be null");
	}

	@Test
	@DisplayName("6.3 update: Ném ResourceNotFoundException khi không tìm thấy bản ghi tồn kho cần cập nhật")
	void testUpdate_AvailabilityNotFound_ThrowsResourceNotFoundException() {
		PersistableInventory persistable = new PersistableInventory();
		persistable.setId(999L); // ID không khớp
		persistable.setProductId(700L);

		Product product = new Product();
		product.setId(700L);
		ProductAvailability existingAvail = createSampleAvailability(60L, product, 20);
		product.setAvailabilities(new HashSet<>(Collections.singletonList(existingAvail)));

		when(productService.retrieveById(700L, store)).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> inventoryFacade.update(persistable, store, language))
				.isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> {
					ResourceNotFoundException rnfe = (ResourceNotFoundException) e;
					assertThat(rnfe.getErrorMessage()).contains("Inventory with id [999] not found");
				});
	}
}
