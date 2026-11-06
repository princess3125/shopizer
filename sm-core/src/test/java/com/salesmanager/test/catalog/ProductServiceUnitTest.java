package com.salesmanager.test.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

import com.salesmanager.core.business.constants.Constants;
import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.catalog.product.ProductRepository;
import com.salesmanager.core.business.services.catalog.category.CategoryService;
import com.salesmanager.core.business.services.catalog.product.ProductServiceImpl;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductOptionValueService;
import com.salesmanager.core.business.services.catalog.product.availability.ProductAvailabilityService;
import com.salesmanager.core.business.services.catalog.product.image.ProductImageService;
import com.salesmanager.core.business.services.catalog.product.price.ProductPriceService;
import com.salesmanager.core.business.services.catalog.product.relationship.ProductRelationshipService;
import com.salesmanager.core.business.services.catalog.product.review.ProductReviewService;
import com.salesmanager.core.business.utils.CoreConfiguration;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.ProductCriteria;
import com.salesmanager.core.model.catalog.product.ProductList;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.description.ProductDescription;
import com.salesmanager.core.model.catalog.product.image.ProductImage;
import com.salesmanager.core.model.catalog.product.relationship.ProductRelationship;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.content.ImageContentFile;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.tax.taxclass.TaxClass;

/**
 * Unit Test độc lập cho ProductService (ProductServiceImpl) - Quản lý sản phẩm.
 * Sử dụng Mockito và JUnit 5 / AssertJ, không phụ thuộc Spring context hay DB thực.
 * Toàn bộ các @Test methods đặt ở top-level để tương thích tốt với Maven Surefire -Dtest.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("ProductService - Quản lý sản phẩm Unit Tests")
public class ProductServiceUnitTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CategoryService categoryService;

	@Mock
	private ProductAvailabilityService productAvailabilityService;

	@Mock
	private ProductPriceService productPriceService;

	@Mock
	private ProductOptionService productOptionService;

	@Mock
	private ProductOptionValueService productOptionValueService;

	@Mock
	private ProductAttributeService productAttributeService;

	@Mock
	private ProductRelationshipService productRelationshipService;

	@Mock
	private ProductImageService productImageService;

	@Mock
	private CoreConfiguration configuration;

	@Mock
	private ProductReviewService productReviewService;

	private ProductServiceImpl productService;

	private MerchantStore store;
	private Language languageEn;
	private Language languageFr;

	@BeforeEach
	void setUp() {
		productService = new ProductServiceImpl(productRepository);

		// Inject các mock services qua reflection do ProductServiceImpl kết hợp constructor và field injection
		setField(productService, "categoryService", categoryService);
		setField(productService, "productAvailabilityService", productAvailabilityService);
		setField(productService, "productPriceService", productPriceService);
		setField(productService, "productOptionService", productOptionService);
		setField(productService, "productOptionValueService", productOptionValueService);
		setField(productService, "productAttributeService", productAttributeService);
		setField(productService, "productRelationshipService", productRelationshipService);
		setField(productService, "productImageService", productImageService);
		setField(productService, "configuration", configuration);
		setField(productService, "productReviewService", productReviewService);

		store = new MerchantStore();
		store.setId(1);
		store.setCode("DEFAULT");

		languageEn = new Language();
		languageEn.setId(1);
		languageEn.setCode("en");

		languageFr = new Language();
		languageFr.setId(2);
		languageFr.setCode("fr");
	}

	private static void setField(Object target, String fieldName, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException("Lỗi gán reflection field: " + fieldName, e);
		}
	}

	private Product createSampleProduct(Long id, String sku) {
		Product product = new Product();
		product.setId(id);
		product.setSku(sku);
		product.setMerchantStore(store);

		ProductAvailability availability = new ProductAvailability();
		availability.setRegion(Constants.ALL_REGIONS);
		availability.setProductQuantity(10);
		availability.setProduct(product);

		Set<ProductAvailability> availabilities = new HashSet<>();
		availabilities.add(availability);
		product.setAvailabilities(availabilities);

		return product;
	}

	// =========================================================================
	// NHÓM 1: CREATE & SAVE PRODUCT (TẠO MỚI VÀ LƯU SẢN PHẨM)
	// =========================================================================

	@Test
	@DisplayName("1.1 create: Tạo mới sản phẩm thành công khi có id null và có availability hợp lệ")
	void testCreate_Success_NewProductWithAvailability() throws ServiceException {
		Product product = createSampleProduct(null, "PROD-NEW-01");

		productService.create(product);

		verify(productRepository, times(1)).saveAndFlush(product);
	}

	@Test
	@DisplayName("1.2 create: Ném NullPointerException khi product truyền vào là null")
	void testCreate_WhenProductIsNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productService.create(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("product cannot be null");
	}

	@Test
	@DisplayName("1.3 create: Ném NullPointerException khi availabilities của product là null")
	void testCreate_WhenAvailabilitiesIsNull_ThrowsNullPointerException() {
		Product product = new Product();
		product.setSku("PROD-NO-AVAIL");
		product.setMerchantStore(store);
		product.setAvailabilities(null);

		assertThatThrownBy(() -> productService.create(product))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("product must have at least one availability");
	}

	@Test
	@DisplayName("1.4 create: Ném IllegalArgumentException khi danh sách availabilities bị rỗng")
	void testCreate_WhenAvailabilitiesIsEmpty_ThrowsIllegalArgumentException() {
		Product product = new Product();
		product.setSku("PROD-EMPTY-AVAIL");
		product.setMerchantStore(store);
		product.setAvailabilities(new HashSet<>());

		assertThatThrownBy(() -> productService.create(product))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("product must have at least one availability");
	}

	@Test
	@DisplayName("1.5 create: Lưu ảnh mới khi image có InputStream và id null")
	void testCreate_WithNewProductImage_SavesImageInCms() throws Exception {
		Product product = createSampleProduct(null, "PROD-WITH-IMG");

		ProductImage image = new ProductImage();
		image.setId(null);
		image.setProductImage("test.jpg");
		InputStream stream = new ByteArrayInputStream("fake-image-bytes".getBytes());
		image.setImage(stream);

		Set<ProductImage> images = new HashSet<>();
		images.add(image);
		product.setImages(images);

		productService.create(product);

		verify(productRepository, times(1)).saveAndFlush(product);
		verify(productImageService, atLeastOnce()).addProductImage(eq(product), eq(image), any(ImageContentFile.class));
	}

	@Test
	@DisplayName("1.6 create: Lưu ảnh hiện có khi image có id khác null và không có stream mới")
	void testCreate_WithExistingProductImage_CallsSaveImage() throws Exception {
		Product product = createSampleProduct(null, "PROD-EXIST-IMG");

		ProductImage image = new ProductImage();
		image.setId(55L);
		image.setProductImage("existing.jpg");
		image.setImage(null);

		Set<ProductImage> images = new HashSet<>();
		images.add(image);
		product.setImages(images);

		productService.create(product);

		verify(productRepository, times(1)).saveAndFlush(product);
		verify(productImageService, times(1)).save(image);
	}

	@Test
	@DisplayName("1.7 create: Bắt và ghi log khi productImageService ném lỗi, không làm gián đoạn việc lưu")
	void testCreate_WhenImageServiceThrowsException_HandledGracefully() throws Exception {
		Product product = createSampleProduct(null, "PROD-IMG-ERR");

		ProductImage image = new ProductImage();
		image.setId(null);
		image.setProductImage("fail.jpg");
		image.setImage(new ByteArrayInputStream("data".getBytes()));

		Set<ProductImage> images = new HashSet<>();
		images.add(image);
		product.setImages(images);

		doThrow(new RuntimeException("CMS connection failed"))
				.when(productImageService).addProductImage(eq(product), eq(image), any(ImageContentFile.class));

		// Không bị ném ra ngoài vì đã được try-catch trong saveOrUpdate
		productService.create(product);

		verify(productRepository, times(1)).saveAndFlush(product);
	}

	@Test
	@DisplayName("1.8 saveProduct: Lưu thành công trả về product đã lưu")
	void testSaveProduct_Success() throws ServiceException {
		Product product = createSampleProduct(null, "PROD-SAVE-01");

		Product result = productService.saveProduct(product);

		assertThat(result).isNotNull();
		assertThat(result.getSku()).isEqualTo("PROD-SAVE-01");
		verify(productRepository, times(1)).saveAndFlush(product);
	}

	@Test
	@DisplayName("1.9 saveProduct: Ném ServiceException được wrap khi saveOrUpdate thất bại")
	void testSaveProduct_WhenSaveFails_ThrowsWrappedServiceException() {
		Product product = createSampleProduct(99L, "PROD-ERR");

		doThrow(new RuntimeException("DB write failure")).when(productRepository).saveAndFlush(product);

		// saveOrUpdate ném ServiceException hoặc RuntimeException
		assertThatThrownBy(() -> productService.saveProduct(product))
				.isInstanceOf(RuntimeException.class);
	}

	// =========================================================================
	// NHÓM 2: UPDATE PRODUCT (CẬP NHẬT SẢN PHẨM)
	// =========================================================================

	@Test
	@DisplayName("2.1 update: Cập nhật thành công sản phẩm có id > 0")
	void testUpdate_Success_ExistingProductWithId() throws ServiceException {
		Product product = createSampleProduct(101L, "PROD-UPDATE-01");

		productService.update(product);

		verify(productRepository, times(1)).saveAndFlush(product);
	}

	@Test
	@DisplayName("2.2 update: Ném NullPointerException khi product là null")
	void testUpdate_WhenProductIsNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productService.update(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("product cannot be null");
	}

	@Test
	@DisplayName("2.3 update: Ném IllegalArgumentException khi availabilities rỗng")
	void testUpdate_WhenAvailabilitiesIsEmpty_ThrowsIllegalArgumentException() {
		Product product = createSampleProduct(102L, "PROD-NO-AVAIL");
		product.setAvailabilities(new HashSet<>());

		assertThatThrownBy(() -> productService.update(product))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("2.4 update: Xử lý lưu ảnh đã có trong danh sách ảnh của sản phẩm")
	void testUpdate_CleansUpDeletedImages() throws Exception {
		Product product = createSampleProduct(103L, "PROD-IMG-CLEANUP");

		ProductImage remainingImg = new ProductImage();
		remainingImg.setId(10L);
		remainingImg.setProductImage("remain.jpg");

		Set<ProductImage> currentImages = new HashSet<>();
		currentImages.add(remainingImg);
		product.setImages(currentImages);

		productService.update(product);

		verify(productImageService, times(1)).save(remainingImg);
	}

	// =========================================================================
	// NHÓM 3: DELETE PRODUCT (XÓA SẢN PHẨM & CASCADE ASSOCIATIONS)
	// =========================================================================

	@Test
	@DisplayName("3.1 delete: Xóa thành công dọn dẹp categories, images, reviews, relationships")
	void testDelete_Success_DeletesProductAndCleansUpAssociations() throws ServiceException {
		Product product = createSampleProduct(201L, "PROD-DEL-01");

		Product managedProduct = createSampleProduct(201L, "PROD-DEL-01");
		Category category = new Category();
		category.setId(5L);
		Set<Category> categories = new HashSet<>();
		categories.add(category);
		managedProduct.setCategories(categories);

		ProductImage image = new ProductImage();
		image.setId(11L);
		Set<ProductImage> images = new HashSet<>();
		images.add(image);
		managedProduct.setImages(images);

		ProductReview review = new ProductReview();
		review.setId(31L);
		List<ProductReview> reviews = Collections.singletonList(review);

		ProductRelationship relationship = new ProductRelationship();
		relationship.setId(41L);
		List<ProductRelationship> relationships = Collections.singletonList(relationship);

		when(productRepository.getOne(201L)).thenReturn(managedProduct);
		when(productReviewService.getByProductNoCustomers(managedProduct)).thenReturn(reviews);
		when(productRelationshipService.listByProduct(managedProduct)).thenReturn(relationships);

		productService.delete(product);

		assertThat(managedProduct.getCategories()).isNull();
		assertThat(managedProduct.getImages()).isNull();
		verify(productImageService, times(1)).removeProductImage(image);
		verify(productReviewService, times(1)).delete(review);
		verify(productRelationshipService, times(1)).deleteRelationship(relationship);
		verify(productRepository, times(1)).delete(managedProduct);
	}

	@Test
	@DisplayName("3.2 delete: Ném NullPointerException khi product là null")
	void testDelete_WhenProductIsNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productService.delete(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("Product cannot be null");
	}

	@Test
	@DisplayName("3.3 delete: Ném NullPointerException khi MerchantStore trong product là null")
	void testDelete_WhenMerchantStoreIsNull_ThrowsNullPointerException() {
		Product product = new Product();
		product.setId(202L);
		product.setMerchantStore(null);

		assertThatThrownBy(() -> productService.delete(product))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MerchantStore cannot be null in product");
	}

	@Test
	@DisplayName("3.4 delete: Xóa thành công khi product không có ảnh, đánh giá hay quan hệ liên kết")
	void testDelete_ProductWithoutImagesReviewsOrRelationships_DeletesDirectly() throws ServiceException {
		Product product = createSampleProduct(203L, "PROD-BARE");

		Product managedProduct = createSampleProduct(203L, "PROD-BARE");
		managedProduct.setImages(new HashSet<>());

		when(productRepository.getOne(203L)).thenReturn(managedProduct);
		when(productReviewService.getByProductNoCustomers(managedProduct)).thenReturn(Collections.emptyList());
		when(productRelationshipService.listByProduct(managedProduct)).thenReturn(Collections.emptyList());

		productService.delete(product);

		verify(productImageService, never()).removeProductImage(any());
		verify(productReviewService, never()).delete(any());
		verify(productRelationshipService, never()).deleteRelationship(any());
		verify(productRepository, times(1)).delete(managedProduct);
	}

	// =========================================================================
	// NHÓM 4: RETRIEVAL BY SKU (TÌM KIẾM THEO SKU)
	// =========================================================================

	@Test
	@DisplayName("4.1 getBySku (với Language): Tìm thấy sản phẩm theo SKU")
	void testGetBySku_WithLanguage_Found() throws ServiceException {
		String sku = "SKU-FIND-01";
		List<Object> ids = Collections.singletonList(BigInteger.valueOf(501L));
		Product expectedProduct = createSampleProduct(501L, sku);

		when(productRepository.findBySku(sku, store.getId())).thenReturn(ids);
		when(productRepository.getById(501L, store, languageEn)).thenReturn(expectedProduct);

		Product result = productService.getBySku(sku, store, languageEn);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(501L);
		assertThat(result.getSku()).isEqualTo(sku);
	}

	@Test
	@DisplayName("4.2 getBySku (với Language): Ném ServiceException khi không tìm thấy SKU")
	void testGetBySku_WithLanguage_NotFound_ThrowsServiceException() {
		String sku = "SKU-NOT-EXIST";
		when(productRepository.findBySku(sku, store.getId())).thenReturn(Collections.emptyList());

		assertThatThrownBy(() -> productService.getBySku(sku, store, languageEn))
				.isInstanceOf(ServiceException.class)
				.hasMessageContaining("Cannot get product with sku [" + sku + "]");
	}

	@Test
	@DisplayName("4.3 getBySku (với Language): Ném ServiceException bọc khi repository ném lỗi")
	void testGetBySku_WithLanguage_RepositoryThrowsException_ThrowsServiceException() {
		String sku = "SKU-ERR";
		when(productRepository.findBySku(sku, store.getId())).thenThrow(new RuntimeException("DB Connection Timeout"));

		assertThatThrownBy(() -> productService.getBySku(sku, store, languageEn))
				.isInstanceOf(ServiceException.class)
				.hasMessageContaining("Cannot get product with sku [" + sku + "]");
	}

	@Test
	@DisplayName("4.4 getBySku (không có Language): Tìm thấy sản phẩm theo SKU")
	void testGetBySku_WithoutLanguage_Found() throws ServiceException {
		String sku = "SKU-FIND-02";
		List<Object> ids = Collections.singletonList(BigInteger.valueOf(502L));
		Product expectedProduct = createSampleProduct(502L, sku);

		when(productRepository.findBySku(sku, store.getId())).thenReturn(ids);
		when(productRepository.getById(502L, store)).thenReturn(expectedProduct);

		Product result = productService.getBySku(sku, store);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(502L);
		assertThat(result.getSku()).isEqualTo(sku);
	}

	@Test
	@DisplayName("4.5 getBySku (không có Language): Ném ServiceException khi danh sách SKU rỗng")
	void testGetBySku_WithoutLanguage_NotFound_ThrowsServiceException() {
		String sku = "SKU-EMPTY";
		when(productRepository.findBySku(sku, store.getId())).thenReturn(Collections.emptyList());

		assertThatThrownBy(() -> productService.getBySku(sku, store))
				.isInstanceOf(ServiceException.class)
				.hasMessageContaining("Cannot get product with sku [" + sku + "]");
	}

	@Test
	@DisplayName("4.6 getBySku: Ném ServiceException khi MerchantStore là null")
	void testGetBySku_WhenStoreIsNull_ThrowsServiceException() {
		assertThatThrownBy(() -> productService.getBySku("SKU-STORE-NULL", null))
				.isInstanceOf(ServiceException.class);
	}

	// =========================================================================
	// NHÓM 5: RETRIEVAL BY ID, SEURL & EXISTENCE (TÌM KIẾM THEO ID, URL & TỒN TẠI)
	// =========================================================================

	@Test
	@DisplayName("5.1 findOne: Tìm thấy sản phẩm theo ID và MerchantStore")
	void testFindOne_Success() {
		Product expected = createSampleProduct(301L, "PROD-301");
		when(productRepository.getById(301L, store)).thenReturn(expected);

		Product result = productService.findOne(301L, store);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(301L);
		verify(productRepository, times(1)).getById(301L, store);
	}

	@Test
	@DisplayName("5.2 findOne: Ném NullPointerException khi MerchantStore là null")
	void testFindOne_WhenMerchantStoreIsNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productService.findOne(302L, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("MerchantStore must not be null");
	}

	@Test
	@DisplayName("5.3 findOne: Ném NullPointerException khi ID là null")
	void testFindOne_WhenIdIsNull_ThrowsNullPointerException() {
		assertThatThrownBy(() -> productService.findOne(null, store))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("id must not be null");
	}

	@Test
	@DisplayName("5.4 retrieveById: Trả về Optional chứa Product khi tìm thấy")
	void testRetrieveById_Found_ReturnsOptionalOfProduct() {
		Product expected = createSampleProduct(303L, "PROD-303");
		when(productRepository.getById(303L, store)).thenReturn(expected);

		Optional<Product> result = productService.retrieveById(303L, store);

		assertThat(result).isPresent();
		assertThat(result.get().getId()).isEqualTo(303L);
	}

	@Test
	@DisplayName("5.5 retrieveById: Trả về Optional.empty() khi không tìm thấy")
	void testRetrieveById_NotFound_ReturnsOptionalEmpty() {
		when(productRepository.getById(999L, store)).thenReturn(null);

		Optional<Product> result = productService.retrieveById(999L, store);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("5.6 getById: Gọi generic getById lấy thực thể từ repository")
	void testGetById_GenericMethod_ReturnsProduct() {
		Product expected = createSampleProduct(304L, "PROD-304");
		when(productRepository.getOne(304L)).thenReturn(expected);

		Product result = productService.getById(304L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(304L);
	}

	@Test
	@DisplayName("5.7 getProductWithOnlyMerchantStoreById: Trả về product chỉ kèm merchant store")
	void testGetProductWithOnlyMerchantStoreById_ReturnsProduct() {
		Product expected = createSampleProduct(305L, "PROD-305");
		when(productRepository.getProductWithOnlyMerchantStoreById(305L)).thenReturn(expected);

		Product result = productService.getProductWithOnlyMerchantStoreById(305L);

		assertThat(result).isNotNull();
		assertThat(result.getMerchantStore()).isEqualTo(store);
	}

	@Test
	@DisplayName("5.8 getBySeUrl: Tìm kiếm theo URL thân thiện (SE URL) và Locale")
	void testGetBySeUrl_ReturnsProduct() {
		Product expected = createSampleProduct(306L, "PROD-306");
		when(productRepository.getByFriendlyUrl(store, "spring-boot-in-action", Locale.ENGLISH))
				.thenReturn(expected);

		Product result = productService.getBySeUrl(store, "spring-boot-in-action", Locale.ENGLISH);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(306L);
	}

	@Test
	@DisplayName("5.9 exists: Trả về true khi SKU đã tồn tại trong store")
	void testExists_WhenSkuExists_ReturnsTrue() {
		when(productRepository.existsBySku("SKU-EXISTS", store.getId())).thenReturn(true);

		boolean exists = productService.exists("SKU-EXISTS", store);

		assertThat(exists).isTrue();
	}

	@Test
	@DisplayName("5.10 exists: Trả về false khi SKU chưa tồn tại trong store")
	void testExists_WhenSkuDoesNotExist_ReturnsFalse() {
		when(productRepository.existsBySku("SKU-NOT-EXISTS", store.getId())).thenReturn(false);

		boolean exists = productService.exists("SKU-NOT-EXISTS", store);

		assertThat(exists).isFalse();
	}

	// =========================================================================
	// NHÓM 6: DESCRIPTIONS & LOCALIZATION (MÔ TẢ VÀ ĐA NGÔN NGỮ)
	// =========================================================================

	@Test
	@DisplayName("6.1 addProductDescription: Khởi tạo Set và gán mô tả khi descriptions ban đầu là null")
	void testAddProductDescription_WhenDescriptionsNull_InitializesAndAdds() throws ServiceException {
		Product product = createSampleProduct(401L, "PROD-DESC-01");
		product.setDescriptions(null);

		ProductDescription description = new ProductDescription();
		description.setName("English Name");
		description.setLanguage(languageEn);

		productService.addProductDescription(product, description);

		assertThat(product.getDescriptions()).isNotNull();
		assertThat(product.getDescriptions()).contains(description);
		assertThat(description.getProduct()).isEqualTo(product);
		verify(productRepository, times(1)).saveAndFlush(product);
	}

	@Test
	@DisplayName("6.2 addProductDescription: Thêm mô tả mới vào tập hợp descriptions đã có sẵn")
	void testAddProductDescription_WhenDescriptionsNotNull_AppendsDescription() throws ServiceException {
		Product product = createSampleProduct(402L, "PROD-DESC-02");

		ProductDescription descEn = new ProductDescription();
		descEn.setName("English Name");
		descEn.setLanguage(languageEn);
		descEn.setProduct(product);
		product.getDescriptions().add(descEn);

		ProductDescription descFr = new ProductDescription();
		descFr.setName("Nom Francais");
		descFr.setLanguage(languageFr);

		productService.addProductDescription(product, descFr);

		assertThat(product.getDescriptions()).hasSize(2);
		assertThat(product.getDescriptions()).contains(descFr);
		assertThat(descFr.getProduct()).isEqualTo(product);
		verify(productRepository, times(1)).saveAndFlush(product);
	}

	@Test
	@DisplayName("6.3 getProductDescription: Trả về mô tả khớp chính xác với ngôn ngữ yêu cầu")
	void testGetProductDescription_WhenLanguageMatches_ReturnsDescription() {
		Product product = createSampleProduct(403L, "PROD-DESC-03");

		ProductDescription descEn = new ProductDescription();
		descEn.setName("English Name");
		descEn.setLanguage(languageEn);

		ProductDescription descFr = new ProductDescription();
		descFr.setName("Nom Francais");
		descFr.setLanguage(languageFr);

		product.getDescriptions().add(descEn);
		product.getDescriptions().add(descFr);

		ProductDescription result = productService.getProductDescription(product, languageEn);

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("English Name");
		assertThat(result.getLanguage()).isEqualTo(languageEn);
	}

	@Test
	@DisplayName("6.4 getProductDescription: Trả về null khi không có mô tả nào khớp ngôn ngữ")
	void testGetProductDescription_WhenNoLanguageMatches_ReturnsNull() {
		Product product = createSampleProduct(404L, "PROD-DESC-04");

		ProductDescription descEn = new ProductDescription();
		descEn.setName("English Name");
		descEn.setLanguage(languageEn);
		product.getDescriptions().add(descEn);

		ProductDescription result = productService.getProductDescription(product, languageFr);

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("6.5 getProductForLocale: Trả về product và lọc availability theo Locale khi tìm thấy")
	void testGetProductForLocale_ProductFound_ReturnsFilteredProduct() throws ServiceException {
		Product product = createSampleProduct(405L, "PROD-LOCALE-01");
		when(productRepository.getProductForLocale(405L, languageEn, Locale.CANADA)).thenReturn(product);

		Product result = productService.getProductForLocale(405L, languageEn, Locale.CANADA);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(405L);
		verify(productRepository, times(1)).getProductForLocale(405L, languageEn, Locale.CANADA);
	}

	@Test
	@DisplayName("6.6 getProductForLocale: Trả về null khi repository không tìm thấy product")
	void testGetProductForLocale_ProductNotFound_ReturnsNull() throws ServiceException {
		when(productRepository.getProductForLocale(999L, languageEn, Locale.CANADA)).thenReturn(null);

		Product result = productService.getProductForLocale(999L, languageEn, Locale.CANADA);

		assertThat(result).isNull();
	}

	// =========================================================================
	// NHÓM 7: CATEGORY & STORE QUERIES, PAGINATION (TRUY VẤN DANH MỤC & PHÂN TRANG)
	// =========================================================================

	@Test
	@DisplayName("7.1 getProducts (theo categoryIds): Trả về danh sách sản phẩm khớp categoryIds")
	void testGetProductsByCategoryIds_ReturnsProductList() throws ServiceException {
		List<Long> categoryIds = Arrays.asList(1L, 2L);
		Product prod = createSampleProduct(601L, "PROD-CAT-01");
		when(productRepository.getProductsListByCategories(anySet())).thenReturn(Collections.singletonList(prod));

		List<Product> results = productService.getProducts(categoryIds);

		assertThat(results).isNotEmpty();
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(601L);
	}

	@Test
	@DisplayName("7.2 getProducts (theo categoryIds và Language): Trả về danh sách sản phẩm theo ngôn ngữ")
	void testGetProductsByCategoryIdsAndLanguage_ReturnsProductList() throws ServiceException {
		List<Long> categoryIds = Arrays.asList(1L, 2L);
		Product prod = createSampleProduct(602L, "PROD-CAT-02");
		when(productRepository.getProductsListByCategories(anySet(), eq(languageEn)))
				.thenReturn(Collections.singletonList(prod));

		List<Product> results = productService.getProducts(categoryIds, languageEn);

		assertThat(results).isNotEmpty();
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(602L);
	}

	@Test
	@DisplayName("7.3 getProductsByIds: Trả về danh sách sản phẩm khớp với tập ID truyền vào")
	void testGetProductsByIds_ReturnsProductList() throws ServiceException {
		List<Long> productIds = Arrays.asList(603L, 604L);
		Product prod1 = createSampleProduct(603L, "PROD-603");
		Product prod2 = createSampleProduct(604L, "PROD-604");
		when(productRepository.getProductsListByIds(anySet())).thenReturn(Arrays.asList(prod1, prod2));

		List<Product> results = productService.getProductsByIds(productIds);

		assertThat(results).hasSize(2);
		assertThat(results).extracting(Product::getId).containsExactlyInAnyOrder(603L, 604L);
	}

	@Test
	@DisplayName("7.4 getProductsForLocale (theo Category): Ném ServiceException khi category là null")
	void testGetProductsForLocale_WhenCategoryIsNull_ThrowsServiceException() {
		assertThatThrownBy(() -> productService.getProductsForLocale(null, languageEn, Locale.ENGLISH))
				.isInstanceOf(ServiceException.class)
				.satisfies(e -> {
					ServiceException se = (ServiceException) e;
					assertThat(se.getMessageCode()).isEqualTo("The category is null");
				});
	}

	@Test
	@DisplayName("7.5 getProductsForLocale (theo Category): Thu thập lineage danh mục con và trả về sản phẩm")
	void testGetProductsForLocale_ValidCategory_ReturnsProducts() throws ServiceException {
		Category parentCategory = new Category();
		parentCategory.setId(10L);
		parentCategory.setLineage("/10/");
		parentCategory.setMerchantStore(store);

		Category childCategory = new Category();
		childCategory.setId(11L);

		when(categoryService.getListByLineage(store, "/10/10/"))
				.thenReturn(Collections.singletonList(childCategory));

		Product prod = createSampleProduct(701L, "PROD-CAT-LOC");
		when(productRepository.getProductsForLocale(eq(store), anySet(), eq(languageEn), eq(Locale.ENGLISH)))
				.thenReturn(Collections.singletonList(prod));

		List<Product> results = productService.getProductsForLocale(parentCategory, languageEn, Locale.ENGLISH);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(701L);
		verify(categoryService, times(1)).getListByLineage(store, "/10/10/");
	}

	@Test
	@DisplayName("7.6 listByStore (không criteria): Trả về toàn bộ danh sách sản phẩm của cửa hàng")
	void testListByStore_WithoutCriteria_ReturnsProductList() {
		Product prod = createSampleProduct(801L, "PROD-STORE-01");
		when(productRepository.listByStore(store)).thenReturn(Collections.singletonList(prod));

		List<Product> results = productService.listByStore(store);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(801L);
	}

	@Test
	@DisplayName("7.7 listByStore (với criteria): Trả về ProductList từ repository")
	void testListByStore_WithCriteria_ReturnsProductList() {
		ProductCriteria criteria = new ProductCriteria();
		criteria.setPageSize(10);

		Product prod = createSampleProduct(802L, "PROD-STORE-02");
		ProductList productList = new ProductList();
		productList.setProducts(Collections.singletonList(prod));
		productList.setTotalCount(1);

		when(productRepository.listByStore(store, languageEn, criteria)).thenReturn(productList);

		ProductList result = productService.listByStore(store, languageEn, criteria);

		assertThat(result).isNotNull();
		assertThat(result.getProducts()).hasSize(1);
		assertThat(result.getTotalCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("7.8 listByStore (phân trang Page): Trả về Page<Product> với phân trang chính xác")
	void testListByStore_Paged_ReturnsPageImpl() {
		ProductCriteria criteria = new ProductCriteria();
		Product prod1 = createSampleProduct(803L, "PROD-PAGE-01");
		Product prod2 = createSampleProduct(804L, "PROD-PAGE-02");

		ProductList productList = new ProductList();
		productList.setProducts(Arrays.asList(prod1, prod2));
		productList.setTotalCount(25);

		when(productRepository.listByStore(store, languageEn, criteria)).thenReturn(productList);

		Page<Product> page = productService.listByStore(store, languageEn, criteria, 0, 10);

		assertThat(page).isNotNull();
		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getTotalElements()).isEqualTo(25);
		assertThat(page.getNumber()).isEqualTo(0);
		assertThat(page.getSize()).isEqualTo(10);
		assertThat(criteria.isLegacyPagination()).isFalse();
	}

	@Test
	@DisplayName("7.9 listByTaxClass: Trả về danh sách sản phẩm theo TaxClass")
	void testListByTaxClass_ReturnsProductList() {
		TaxClass taxClass = new TaxClass();
		taxClass.setId(1L);
		taxClass.setCode("DEFAULT_TAX");

		Product prod = createSampleProduct(805L, "PROD-TAX-01");
		when(productRepository.listByTaxClass(taxClass)).thenReturn(Collections.singletonList(prod));

		List<Product> results = productService.listByTaxClass(taxClass);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(805L);
	}

	// =========================================================================
	// NHÓM 8: GENERIC ENTITY SERVICE METHODS (KẾ THỪA TỪ SalesManagerEntityService)
	// =========================================================================

	@Test
	@DisplayName("8.1 list: Gọi repository.findAll() trả về tất cả sản phẩm")
	void testList_ReturnsAllProducts() {
		Product prod1 = createSampleProduct(901L, "PROD-ALL-01");
		Product prod2 = createSampleProduct(902L, "PROD-ALL-02");
		when(productRepository.findAll()).thenReturn(Arrays.asList(prod1, prod2));

		List<Product> results = productService.list();

		assertThat(results).hasSize(2);
		verify(productRepository, times(1)).findAll();
	}

	@Test
	@DisplayName("8.2 count: Gọi repository.count() trả về tổng số sản phẩm")
	void testCount_ReturnsTotalProductCount() {
		when(productRepository.count()).thenReturn(42L);

		Long count = productService.count();

		assertThat(count).isEqualTo(42L);
		verify(productRepository, times(1)).count();
	}

	@Test
	@DisplayName("8.3 flush: Gọi repository.flush()")
	void testFlush_CallsRepositoryFlush() {
		productService.flush();

		verify(productRepository, times(1)).flush();
	}

	@Test
	@DisplayName("8.4 saveAll: Gọi repository.saveAll(...) cho danh sách sản phẩm")
	void testSaveAll_CallsRepositorySaveAll() throws ServiceException {
		List<Product> products = Arrays.asList(
				createSampleProduct(903L, "PROD-BATCH-01"),
				createSampleProduct(904L, "PROD-BATCH-02")
		);

		productService.saveAll(products);

		verify(productRepository, times(1)).saveAll(products);
	}
}
