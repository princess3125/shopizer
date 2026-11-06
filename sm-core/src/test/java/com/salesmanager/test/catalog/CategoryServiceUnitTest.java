package com.salesmanager.test.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.repositories.catalog.category.CategoryDescriptionRepository;
import com.salesmanager.core.business.repositories.catalog.category.CategoryRepository;
import com.salesmanager.core.business.repositories.catalog.category.PageableCategoryRepository;
import com.salesmanager.core.business.services.catalog.category.CategoryServiceImpl;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.model.catalog.category.Category;
import com.salesmanager.core.model.catalog.category.CategoryDescription;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;

/**
 * Unit Test độc lập cho CategoryService (CategoryServiceImpl) - Quản lý danh mục.
 * Sử dụng Mockito và JUnit 5 / AssertJ, không phụ thuộc Spring context hay DB thực.
 * Toàn bộ các @Test methods đặt ở top-level để tương thích tốt với Maven Surefire -Dtest.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("CategoryService - Quản lý danh mục Unit Tests")
public class CategoryServiceUnitTest {

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private ProductService productService;

	@Mock
	private PageableCategoryRepository pageableCategoryRepository;

	@Mock
	private CategoryDescriptionRepository categoryDescriptionRepository;

	private CategoryServiceImpl categoryService;

	private MerchantStore store;
	private Language languageEn;
	private Language languageFr;

	@BeforeEach
	void setUp() {
		categoryService = new CategoryServiceImpl(categoryRepository);

		// Inject các mock services qua reflection do CategoryServiceImpl kết hợp constructor và field injection
		setField(categoryService, "productService", productService);
		setField(categoryService, "pageableCategoryRepository", pageableCategoryRepository);
		setField(categoryService, "categoryDescriptionRepository", categoryDescriptionRepository);

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

	private Category createSampleCategory(Long id, String code) {
		Category category = new Category();
		category.setId(id);
		category.setCode(code);
		category.setMerchantStore(store);
		category.setDescriptions(new HashSet<>());
		return category;
	}

	// =========================================================================
	// NHÓM 1: CREATE & SAVEORUPDATE (TẠO MỚI & LƯU DANH MỤC)
	// =========================================================================

	@Test
	@DisplayName("1.1 create: Tạo mới danh mục gốc (root) khi parent là null")
	void testCreate_RootCategory_Success() throws ServiceException {
		Category category = createSampleCategory(10L, "ROOT_CAT");
		category.setParent(null);

		categoryService.create(category);

		assertThat(category.getDepth()).isEqualTo(0);
		assertThat(category.getLineage()).isEqualTo("/10/");
		verify(categoryRepository, times(2)).saveAndFlush(category);
	}

	@Test
	@DisplayName("1.2 create: Tạo mới danh mục con khi có parent hợp lệ")
	void testCreate_SubCategory_Success() throws ServiceException {
		Category parent = createSampleCategory(10L, "PARENT_CAT");
		parent.setLineage("/10/");
		parent.setDepth(0);

		Category child = createSampleCategory(20L, "CHILD_CAT");
		child.setParent(parent);

		when(categoryRepository.getOne(10L)).thenReturn(parent);

		categoryService.create(child);

		assertThat(child.getDepth()).isEqualTo(1);
		assertThat(child.getLineage()).isEqualTo("/10/20/");
		verify(categoryRepository, times(2)).saveAndFlush(child);
	}

	@Test
	@DisplayName("1.3 create: Xử lý parent có ID bằng 0 như danh mục gốc")
	void testCreate_ParentWithIdZero_TreatedAsRoot() throws ServiceException {
		Category parent = createSampleCategory(0L, "ZERO_PARENT");
		Category child = createSampleCategory(30L, "CAT_ZERO");
		child.setParent(parent);

		categoryService.create(child);

		assertThat(child.getDepth()).isEqualTo(0);
		assertThat(child.getLineage()).isEqualTo("/30/");
		verify(categoryRepository, times(2)).saveAndFlush(child);
	}

	@Test
	@DisplayName("1.4 saveOrUpdate: Gọi create khi danh mục có ID null")
	void testSaveOrUpdate_NewCategoryWithNullId_CallsCreate() throws ServiceException {
		Category category = createSampleCategory(null, "NEW_CAT");
		category.setParent(null);

		categoryService.saveOrUpdate(category);

		assertThat(category.getDepth()).isEqualTo(0);
		verify(categoryRepository, times(2)).saveAndFlush(category);
	}

	@Test
	@DisplayName("1.5 saveOrUpdate: Gọi create khi danh mục có ID bằng 0")
	void testSaveOrUpdate_NewCategoryWithZeroId_CallsCreate() throws ServiceException {
		Category category = createSampleCategory(0L, "ZERO_ID_CAT");
		category.setParent(null);

		categoryService.saveOrUpdate(category);

		assertThat(category.getDepth()).isEqualTo(0);
		verify(categoryRepository, times(2)).saveAndFlush(category);
	}

	@Test
	@DisplayName("1.6 saveOrUpdate: Gọi update khi danh mục có ID > 0")
	void testSaveOrUpdate_ExistingCategoryWithPositiveId_CallsUpdate() throws ServiceException {
		Category category = createSampleCategory(50L, "EXISTING_CAT");

		categoryService.saveOrUpdate(category);

		verify(categoryRepository, times(1)).saveAndFlush(category);
	}

	// =========================================================================
	// NHÓM 2: ADDCHILD HIERARCHY (QUẢN LÝ PHÂN CẤP CHA - CON)
	// =========================================================================

	@Test
	@DisplayName("2.1 addChild: Ném ServiceException khi child là null")
	void testAddChild_WhenChildIsNull_ThrowsServiceException() {
		assertThatThrownBy(() -> categoryService.addChild(null, null))
				.isInstanceOf(ServiceException.class)
				.satisfies(e -> {
					ServiceException se = (ServiceException) e;
					assertThat(se.getMessageCode()).isEqualTo("Child category and merchant store should not be null");
				});
	}

	@Test
	@DisplayName("2.2 addChild: Ném ServiceException khi merchantStore trong child là null")
	void testAddChild_WhenMerchantStoreIsNull_ThrowsServiceException() {
		Category child = new Category();
		child.setId(100L);
		child.setMerchantStore(null);

		assertThatThrownBy(() -> categoryService.addChild(null, child))
				.isInstanceOf(ServiceException.class)
				.satisfies(e -> {
					ServiceException se = (ServiceException) e;
					assertThat(se.getMessageCode()).isEqualTo("Child category and merchant store should not be null");
				});
	}

	@Test
	@DisplayName("2.3 addChild: Gán danh mục con thành danh mục gốc khi parent là null")
	void testAddChild_ParentIsNull_AssignsChildToRoot() throws ServiceException {
		Category child = createSampleCategory(101L, "CHILD_TO_ROOT");
		child.setParent(createSampleCategory(99L, "OLD_PARENT"));

		when(categoryRepository.findByLineage(eq(store.getId()), anyString()))
				.thenReturn(Collections.emptyList());

		categoryService.addChild(null, child);

		assertThat(child.getParent()).isNull();
		assertThat(child.getDepth()).isEqualTo(0);
		assertThat(child.getLineage()).isEqualTo("/101/");
		verify(categoryRepository, times(1)).saveAndFlush(child);
	}

	@Test
	@DisplayName("2.4 addChild: Cập nhật lineage và depth khi chuyển danh mục vào parent mới")
	void testAddChild_ParentNotNull_UpdatesLineageAndDepth() throws ServiceException {
		Category parent = createSampleCategory(10L, "PARENT_CAT");
		parent.setLineage("/10");
		parent.setDepth(0);

		Category child = createSampleCategory(20L, "CHILD_CAT");

		when(categoryRepository.findByIdAndStore(10L, store.getId())).thenReturn(parent);
		when(categoryDescriptionRepository.listByCategoryId(10L)).thenReturn(Collections.emptyList());
		when(categoryRepository.findByLineage(eq(store.getId()), anyString())).thenReturn(Collections.emptyList());

		categoryService.addChild(parent, child);

		assertThat(child.getParent()).isEqualTo(parent);
		assertThat(child.getDepth()).isEqualTo(1);
		assertThat(child.getLineage()).isEqualTo("/10/20/");
		verify(categoryRepository, times(1)).saveAndFlush(child);
	}

	@Test
	@DisplayName("2.5 addChild: Đệ quy cập nhật toàn bộ cây danh mục con bên dưới child")
	void testAddChild_WithSubcategories_CascadesRecursively() throws ServiceException {
		Category parent = createSampleCategory(10L, "PARENT_CAT");
		parent.setLineage("/10");
		parent.setDepth(0);

		Category child = createSampleCategory(20L, "CHILD_CAT");

		Category subChild = createSampleCategory(30L, "SUB_CHILD_CAT");
		subChild.setLineage("/old/30/");
		subChild.setDepth(5);

		when(categoryRepository.findByIdAndStore(10L, store.getId())).thenReturn(parent);
		when(categoryRepository.findByIdAndStore(20L, store.getId())).thenReturn(child);
		when(categoryDescriptionRepository.listByCategoryId(any())).thenReturn(Collections.emptyList());

		// Lần 1 trả về subChild, lần 2 trả về empty để kết thúc đệ quy
		when(categoryRepository.findByLineage(eq(store.getId()), anyString()))
				.thenReturn(Collections.singletonList(subChild), Collections.emptyList());

		categoryService.addChild(parent, child);

		assertThat(child.getLineage()).isEqualTo("/10/20/");
		assertThat(subChild.getLineage()).isEqualTo("/10/20//30/");
		assertThat(subChild.getDepth()).isEqualTo(2);
		verify(categoryRepository, times(2)).saveAndFlush(any(Category.class));
	}

	@Test
	@DisplayName("2.6 addChild: Ném ServiceException khi xảy ra ngoại lệ trong quá trình cập nhật")
	void testAddChild_WhenRepositoryFails_ThrowsServiceException() {
		Category parent = createSampleCategory(10L, "PARENT_CAT");
		Category child = createSampleCategory(20L, "CHILD_CAT");

		when(categoryRepository.findByIdAndStore(10L, store.getId()))
				.thenThrow(new RuntimeException("DB access error"));

		assertThatThrownBy(() -> categoryService.addChild(parent, child))
				.isInstanceOf(ServiceException.class);
	}

	// =========================================================================
	// NHÓM 3: DELETE CATEGORY (XÓA DANH MỤC & XỬ LÝ SẢN PHẨM LIÊN KẾT)
	// =========================================================================

	@Test
	@DisplayName("3.1 delete: Không thực hiện xóa khi không tìm thấy danh mục trong DB")
	void testDelete_CategoryNotFoundInDb_DoesNothing() throws ServiceException {
		Category category = createSampleCategory(201L, "CAT_NOT_FOUND");
		category.setLineage("/201/");

		when(categoryRepository.findByLineage(store.getId(), "/201/201/")).thenReturn(new ArrayList<>());
		when(categoryRepository.findByIdAndStore(201L, store.getId())).thenReturn(null);

		categoryService.delete(category);

		verify(categoryRepository, never()).delete(any());
		verify(productService, never()).delete(any());
	}

	@Test
	@DisplayName("3.2 delete: Sản phẩm liên kết có > 1 danh mục thì chỉ gỡ danh mục khỏi sản phẩm")
	void testDelete_ProductWithMultipleCategories_RemovesCategoryAndUpdatesProduct() throws ServiceException {
		Category category = createSampleCategory(202L, "CAT_MULTI");
		category.setLineage("/202/");

		Category dbCategory = createSampleCategory(202L, "CAT_MULTI");
		dbCategory.setLineage("/202/");

		Category anotherCategory = createSampleCategory(999L, "ANOTHER_CAT");

		Product product = new Product();
		product.setId(501L);
		product.setCategories(new HashSet<>(Collections.singletonList(anotherCategory)));

		Product dbProduct = new Product();
		dbProduct.setId(501L);
		Set<Category> productCategories = new HashSet<>(Arrays.asList(dbCategory, anotherCategory));
		dbProduct.setCategories(productCategories);

		when(categoryRepository.findByLineage(store.getId(), "/202/202/")).thenReturn(new ArrayList<>());
		when(categoryRepository.findByIdAndStore(202L, store.getId())).thenReturn(dbCategory);
		when(categoryDescriptionRepository.listByCategoryId(202L)).thenReturn(Collections.emptyList());
		when(productService.getProducts(Collections.singletonList(202L)))
				.thenReturn(Collections.singletonList(product));
		when(productService.getById(501L)).thenReturn(dbProduct);

		categoryService.delete(category);

		assertThat(dbProduct.getCategories()).doesNotContain(dbCategory);
		assertThat(dbProduct.getCategories()).contains(anotherCategory);
		verify(productService, times(1)).update(dbProduct);
		verify(productService, never()).delete(dbProduct);
		verify(categoryRepository, times(1)).delete(dbCategory);
	}

	@Test
	@DisplayName("3.3 delete: Sản phẩm chỉ thuộc danh mục bị xóa thì xóa luôn sản phẩm")
	void testDelete_ProductWithSingleCategory_DeletesProduct() throws ServiceException {
		Category category = createSampleCategory(203L, "CAT_SINGLE");
		category.setLineage("/203/");

		Category dbCategory = createSampleCategory(203L, "CAT_SINGLE");

		Product product = new Product();
		product.setId(502L);

		Product dbProduct = new Product();
		dbProduct.setId(502L);
		Set<Category> productCategories = new HashSet<>(Collections.singletonList(dbCategory));
		dbProduct.setCategories(productCategories);

		when(categoryRepository.findByLineage(store.getId(), "/203/203/")).thenReturn(new ArrayList<>());
		when(categoryRepository.findByIdAndStore(203L, store.getId())).thenReturn(dbCategory);
		when(categoryDescriptionRepository.listByCategoryId(203L)).thenReturn(Collections.emptyList());
		when(productService.getProducts(Collections.singletonList(203L)))
				.thenReturn(Collections.singletonList(product));
		when(productService.getById(502L)).thenReturn(dbProduct);

		categoryService.delete(category);

		verify(productService, times(1)).delete(dbProduct);
		verify(categoryRepository, times(1)).delete(dbCategory);
	}

	@Test
	@DisplayName("3.4 delete: Xóa thành công danh mục có danh mục con theo thứ tự đảo ngược")
	void testDelete_Success_DeletesCategoryAndCascadesSubcategories() throws ServiceException {
		Category category = createSampleCategory(204L, "PARENT_DEL");
		category.setLineage("/204/");

		Category subCategory = createSampleCategory(205L, "CHILD_DEL");
		subCategory.setLineage("/204/205/");

		Category dbCategory = createSampleCategory(204L, "PARENT_DEL");

		List<Category> subCategories = new ArrayList<>(Collections.singletonList(subCategory));

		when(categoryRepository.findByLineage(store.getId(), "/204/204/")).thenReturn(subCategories);
		when(categoryRepository.findByIdAndStore(204L, store.getId())).thenReturn(dbCategory);
		when(categoryDescriptionRepository.listByCategoryId(204L)).thenReturn(Collections.emptyList());
		when(productService.getProducts(anyList())).thenReturn(Collections.emptyList());

		categoryService.delete(category);

		verify(categoryRepository, times(1)).delete(dbCategory);
	}

	// =========================================================================
	// NHÓM 4: CATEGORY DESCRIPTIONS (QUẢN LÝ MÔ TẢ & ĐA NGÔN NGỮ)
	// =========================================================================

	@Test
	@DisplayName("4.1 addCategoryDescription: Thêm mô tả thành công và thiết lập liên kết")
	void testAddCategoryDescription_Success() throws ServiceException {
		Category category = createSampleCategory(301L, "CAT_DESC_01");

		CategoryDescription description = new CategoryDescription();
		description.setName("English Category");
		description.setLanguage(languageEn);

		categoryService.addCategoryDescription(category, description);

		assertThat(category.getDescriptions()).contains(description);
		assertThat(description.getCategory()).isEqualTo(category);
		verify(categoryRepository, times(1)).saveAndFlush(category);
	}

	@Test
	@DisplayName("4.2 addCategoryDescription: Ném ServiceException khi lưu thất bại")
	void testAddCategoryDescription_WhenUpdateFails_ThrowsServiceException() {
		Category category = createSampleCategory(302L, "CAT_DESC_ERR");

		CategoryDescription description = new CategoryDescription();
		description.setName("Fail Description");
		description.setLanguage(languageEn);

		doThrow(new RuntimeException("DB constraint error")).when(categoryRepository).saveAndFlush(category);

		assertThatThrownBy(() -> categoryService.addCategoryDescription(category, description))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("4.3 getDescription: Trả về đúng mô tả khi khớp với Language yêu cầu")
	void testGetDescription_WhenLanguageMatches_ReturnsDescription() {
		Category category = createSampleCategory(303L, "CAT_DESC_FIND");

		CategoryDescription descEn = new CategoryDescription();
		descEn.setName("English Name");
		descEn.setLanguage(languageEn);

		CategoryDescription descFr = new CategoryDescription();
		descFr.setName("Nom Francais");
		descFr.setLanguage(languageFr);

		category.getDescriptions().add(descEn);
		category.getDescriptions().add(descFr);

		CategoryDescription result = categoryService.getDescription(category, languageEn);

		assertThat(result).isNotNull();
		assertThat(result.getName()).isEqualTo("English Name");
		assertThat(result.getLanguage()).isEqualTo(languageEn);
	}

	@Test
	@DisplayName("4.4 getDescription: Trả về null khi không có mô tả nào khớp Language")
	void testGetDescription_WhenLanguageNotMatches_ReturnsNull() {
		Category category = createSampleCategory(304L, "CAT_DESC_NONE");

		CategoryDescription descEn = new CategoryDescription();
		descEn.setName("English Name");
		descEn.setLanguage(languageEn);
		category.getDescriptions().add(descEn);

		CategoryDescription result = categoryService.getDescription(category, languageFr);

		assertThat(result).isNull();
	}

	// =========================================================================
	// NHÓM 5: LINEAGE & PARENT QUERIES (TRUY VẤN THEO PHẢ HỆ & DANH MỤC CHA)
	// =========================================================================

	@Test
	@DisplayName("5.1 getListByLineage (với MerchantStore): Trả về danh sách danh mục theo lineage")
	void testGetListByLineage_WithStore_ReturnsCategories() throws ServiceException {
		Category cat = createSampleCategory(401L, "CAT_LIN_01");
		when(categoryRepository.findByLineage(store.getId(), "/10/401/"))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getListByLineage(store, "/10/401/");

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(401L);
	}

	@Test
	@DisplayName("5.2 getListByLineage (với MerchantStore): Ném ServiceException khi repository gặp lỗi")
	void testGetListByLineage_WithStore_Exception_ThrowsServiceException() {
		when(categoryRepository.findByLineage(store.getId(), "/err/"))
				.thenThrow(new RuntimeException("DB lineage lookup failure"));

		assertThatThrownBy(() -> categoryService.getListByLineage(store, "/err/"))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("5.3 getListByLineage (với storeCode): Trả về danh sách danh mục theo lineage")
	void testGetListByLineage_WithStoreCode_ReturnsCategories() throws ServiceException {
		Category cat = createSampleCategory(402L, "CAT_LIN_02");
		when(categoryRepository.findByLineage("DEFAULT", "/10/402/"))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getListByLineage("DEFAULT", "/10/402/");

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(402L);
	}

	@Test
	@DisplayName("5.4 getListByLineage (với storeCode): Ném ServiceException khi repository gặp lỗi")
	void testGetListByLineage_WithStoreCode_Exception_ThrowsServiceException() {
		when(categoryRepository.findByLineage("DEFAULT", "/err/"))
				.thenThrow(new RuntimeException("DB lineage lookup failure"));

		assertThatThrownBy(() -> categoryService.getListByLineage("DEFAULT", "/err/"))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("5.5 listByParent (không Store): Trả về danh sách danh mục con")
	void testListByParent_WithoutStore_ReturnsCategories() throws ServiceException {
		Category parent = createSampleCategory(403L, "PARENT_01");
		Category child = createSampleCategory(404L, "CHILD_01");

		when(categoryRepository.listByStoreAndParent(null, parent))
				.thenReturn(Collections.singletonList(child));

		List<Category> results = categoryService.listByParent(parent);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(404L);
	}

	@Test
	@DisplayName("5.6 listByParent (không Store): Ném ServiceException khi gặp lỗi")
	void testListByParent_WithoutStore_Exception_ThrowsServiceException() {
		Category parent = createSampleCategory(403L, "PARENT_ERR");
		when(categoryRepository.listByStoreAndParent(null, parent))
				.thenThrow(new RuntimeException("DB error"));

		assertThatThrownBy(() -> categoryService.listByParent(parent))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("5.7 listByStoreAndParent: Trả về danh mục con theo Store và Parent")
	void testListByStoreAndParent_Success() throws ServiceException {
		Category parent = createSampleCategory(405L, "PARENT_STORE");
		Category child = createSampleCategory(406L, "CHILD_STORE");

		when(categoryRepository.listByStoreAndParent(store, parent))
				.thenReturn(Collections.singletonList(child));

		List<Category> results = categoryService.listByStoreAndParent(store, parent);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(406L);
	}

	@Test
	@DisplayName("5.8 listByStoreAndParent: Ném ServiceException khi gặp lỗi")
	void testListByStoreAndParent_Exception_ThrowsServiceException() {
		Category parent = createSampleCategory(405L, "PARENT_ERR");
		when(categoryRepository.listByStoreAndParent(store, parent))
				.thenThrow(new RuntimeException("DB error"));

		assertThatThrownBy(() -> categoryService.listByStoreAndParent(store, parent))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("5.9 listByParent (với Language): Trả về danh mục con theo ngôn ngữ")
	void testListByParent_WithLanguage_Success() {
		Category parent = createSampleCategory(407L, "PARENT_LANG");
		Category child = createSampleCategory(408L, "CHILD_LANG");

		when(categoryRepository.findByParent(407L, languageEn.getId()))
				.thenReturn(Collections.singletonList(child));

		List<Category> results = categoryService.listByParent(parent, languageEn);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(408L);
	}

	@Test
	@DisplayName("5.10 listByParent (với Language): Ném IllegalArgumentException khi category là null")
	void testListByParent_WithLanguage_NullCategory_ThrowsIllegalArgumentException() {
		assertThatThrownBy(() -> categoryService.listByParent(null, languageEn))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Category cannot be null");
	}

	@Test
	@DisplayName("5.11 listByParent (với Language): Ném IllegalArgumentException khi language là null")
	void testListByParent_WithLanguage_NullLanguage_ThrowsIllegalArgumentException() {
		Category parent = createSampleCategory(409L, "PARENT_NOLANG");

		assertThatThrownBy(() -> categoryService.listByParent(parent, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Language cannot be null");
	}

	@Test
	@DisplayName("5.12 listByParent (với Language): Ném IllegalArgumentException khi merchantStore trong category là null")
	void testListByParent_WithLanguage_NullStore_ThrowsIllegalArgumentException() {
		Category parent = new Category();
		parent.setId(410L);
		parent.setMerchantStore(null);

		assertThatThrownBy(() -> categoryService.listByParent(parent, languageEn))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("category.merchantStore cannot be null");
	}

	// =========================================================================
	// NHÓM 6: CODES & FRIENDLY URLS (TRUY VẤN THEO CODE & URL THÂN THIỆN)
	// =========================================================================

	@Test
	@DisplayName("6.1 getByCode (với MerchantStore): Tìm thấy danh mục theo Store và Code")
	void testGetByCode_WithStore_Success() throws ServiceException {
		Category cat = createSampleCategory(501L, "CAT_CODE_01");
		when(categoryRepository.findByCode(store.getId(), "CAT_CODE_01")).thenReturn(cat);

		Category result = categoryService.getByCode(store, "CAT_CODE_01");

		assertThat(result).isNotNull();
		assertThat(result.getCode()).isEqualTo("CAT_CODE_01");
	}

	@Test
	@DisplayName("6.2 getByCode (với MerchantStore): Ném ServiceException khi repository gặp lỗi")
	void testGetByCode_WithStore_Exception_ThrowsServiceException() {
		when(categoryRepository.findByCode(store.getId(), "ERR_CODE"))
				.thenThrow(new RuntimeException("DB connection error"));

		assertThatThrownBy(() -> categoryService.getByCode(store, "ERR_CODE"))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("6.3 getByCode (với storeCode): Tìm thấy danh mục theo storeCode và Code")
	void testGetByCode_WithStoreCode_Success() throws ServiceException {
		Category cat = createSampleCategory(502L, "CAT_CODE_02");
		when(categoryRepository.findByCode("DEFAULT", "CAT_CODE_02")).thenReturn(cat);

		Category result = categoryService.getByCode("DEFAULT", "CAT_CODE_02");

		assertThat(result).isNotNull();
		assertThat(result.getCode()).isEqualTo("CAT_CODE_02");
	}

	@Test
	@DisplayName("6.4 getByCode (với storeCode): Ném ServiceException khi repository gặp lỗi")
	void testGetByCode_WithStoreCode_Exception_ThrowsServiceException() {
		when(categoryRepository.findByCode("DEFAULT", "ERR_CODE"))
				.thenThrow(new RuntimeException("DB connection error"));

		assertThatThrownBy(() -> categoryService.getByCode("DEFAULT", "ERR_CODE"))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("6.5 listByCodes: Trả về danh sách danh mục theo danh sách mã Code và Language")
	void testListByCodes_Success() {
		List<String> codes = Arrays.asList("CODE_1", "CODE_2");
		Category cat1 = createSampleCategory(503L, "CODE_1");
		Category cat2 = createSampleCategory(504L, "CODE_2");

		when(categoryRepository.findByCodes(store.getId(), codes, languageEn.getId()))
				.thenReturn(Arrays.asList(cat1, cat2));

		List<Category> results = categoryService.listByCodes(store, codes, languageEn);

		assertThat(results).hasSize(2);
		assertThat(results).extracting(Category::getCode).containsExactlyInAnyOrder("CODE_1", "CODE_2");
	}

	@Test
	@DisplayName("6.6 listBySeUrl: Trả về danh sách danh mục theo SE URL")
	void testListBySeUrl_Success() throws ServiceException {
		Category cat = createSampleCategory(505L, "CAT_SEURL_01");
		when(categoryRepository.listByFriendlyUrl(store.getId(), "electronics"))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.listBySeUrl(store, "electronics");

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(505L);
	}

	@Test
	@DisplayName("6.7 listBySeUrl: Ném ServiceException khi repository gặp lỗi")
	void testListBySeUrl_Exception_ThrowsServiceException() {
		when(categoryRepository.listByFriendlyUrl(store.getId(), "err-url"))
				.thenThrow(new RuntimeException("DB url lookup failure"));

		assertThatThrownBy(() -> categoryService.listBySeUrl(store, "err-url"))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("6.8 getBySeUrl (với Language): Tìm thấy danh mục theo SE URL và Language")
	void testGetBySeUrl_WithLanguage_Success() {
		Category cat = createSampleCategory(506L, "CAT_SEURL_LANG");
		when(categoryRepository.findByFriendlyUrl(store.getId(), "books", languageEn.getId()))
				.thenReturn(cat);

		Category result = categoryService.getBySeUrl(store, "books", languageEn);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(506L);
	}

	// =========================================================================
	// NHÓM 7: DEPTH, FEATURED & STORE QUERIES, PAGINATION (TRUY VẤN ĐỘ SÂU & PHÂN TRANG)
	// =========================================================================

	@Test
	@DisplayName("7.1 getListByDepth (Store & Depth): Trả về danh mục theo độ sâu phân cấp")
	void testGetListByDepth_StoreAndDepth_Success() {
		Category cat = createSampleCategory(601L, "CAT_DEPTH_0");
		when(categoryRepository.findByDepth(store.getId(), 0))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getListByDepth(store, 0);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(601L);
	}

	@Test
	@DisplayName("7.2 getListByDepth (Store, Depth & Language): Trả về danh mục kèm ngôn ngữ")
	void testGetListByDepth_StoreDepthAndLanguage_Success() {
		Category cat = createSampleCategory(602L, "CAT_DEPTH_LANG");
		when(categoryRepository.find(store.getId(), 1, languageEn.getId(), null))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getListByDepth(store, 1, languageEn);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(602L);
	}

	@Test
	@DisplayName("7.3 getListByDepthFilterByFeatured: Trả về danh mục nổi bật (featured)")
	void testGetListByDepthFilterByFeatured_Success() {
		Category cat = createSampleCategory(603L, "CAT_FEATURED");
		cat.setFeatured(true);

		when(categoryRepository.findByDepthFilterByFeatured(store.getId(), 0, languageEn.getId()))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getListByDepthFilterByFeatured(store, 0, languageEn);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).isFeatured()).isTrue();
	}

	@Test
	@DisplayName("7.4 getListByDepth (phân trang Page): Trả về Page<Category> chính xác")
	void testGetListByDepth_Paged_ReturnsPage() {
		Category cat = createSampleCategory(604L, "CAT_PAGE");
		Page<Category> expectedPage = new PageImpl<>(Collections.singletonList(cat), PageRequest.of(0, 10), 1);

		when(pageableCategoryRepository.listByStore(eq(store.getId()), eq(languageEn.getId()), eq("Tech"), any(Pageable.class)))
				.thenReturn(expectedPage);

		Page<Category> page = categoryService.getListByDepth(store, languageEn, "Tech", 0, 0, 10);

		assertThat(page).isNotNull();
		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getNumber()).isEqualTo(0);
		assertThat(page.getSize()).isEqualTo(10);
	}

	@Test
	@DisplayName("7.5 listByStore (không Language): Trả về tất cả danh mục của store")
	void testListByStore_WithoutLanguage_Success() throws ServiceException {
		Category cat = createSampleCategory(605L, "CAT_STORE_ALL");
		when(categoryRepository.findByStore(store.getId()))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.listByStore(store);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(605L);
	}

	@Test
	@DisplayName("7.6 listByStore (không Language): Ném ServiceException khi repository gặp lỗi")
	void testListByStore_WithoutLanguage_Exception_ThrowsServiceException() {
		when(categoryRepository.findByStore(store.getId()))
				.thenThrow(new RuntimeException("DB store lookup failure"));

		assertThatThrownBy(() -> categoryService.listByStore(store))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("7.7 listByStore (với Language): Trả về danh mục của store theo ngôn ngữ")
	void testListByStore_WithLanguage_Success() throws ServiceException {
		Category cat = createSampleCategory(606L, "CAT_STORE_LANG");
		when(categoryRepository.findByStore(store.getId(), languageEn.getId()))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.listByStore(store, languageEn);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(606L);
	}

	@Test
	@DisplayName("7.8 listByStore (với Language): Ném ServiceException khi repository gặp lỗi")
	void testListByStore_WithLanguage_Exception_ThrowsServiceException() {
		when(categoryRepository.findByStore(store.getId(), languageEn.getId()))
				.thenThrow(new RuntimeException("DB store lookup failure"));

		assertThatThrownBy(() -> categoryService.listByStore(store, languageEn))
				.isInstanceOf(ServiceException.class);
	}

	@Test
	@DisplayName("7.9 count (theo Store): Trả về tổng số lượng danh mục trong store")
	void testCount_ByStore_Success() {
		when(categoryRepository.count(store.getId())).thenReturn(15);

		int count = categoryService.count(store);

		assertThat(count).isEqualTo(15);
		verify(categoryRepository, times(1)).count(store.getId());
	}

	// =========================================================================
	// NHÓM 8: ID, PRODUCT & NAME QUERIES (TRUY VẤN THEO ID, SẢN PHẨM & TÊN)
	// =========================================================================

	@Test
	@DisplayName("8.1 getById (ID & MerchantID): Tìm thấy danh mục và tự động nạp descriptions")
	void testGetById_IdAndMerchantId_FoundWithDescriptions() {
		Category cat = createSampleCategory(701L, "CAT_WITH_DESCS");
		CategoryDescription desc = new CategoryDescription();
		desc.setName("Loaded Desc");
		desc.setLanguage(languageEn);

		when(categoryRepository.findByIdAndStore(701L, store.getId())).thenReturn(cat);
		when(categoryDescriptionRepository.listByCategoryId(701L))
				.thenReturn(Collections.singletonList(desc));

		Category result = categoryService.getById(701L, store.getId());

		assertThat(result).isNotNull();
		assertThat(result.getDescriptions()).contains(desc);
		verify(categoryDescriptionRepository, times(1)).listByCategoryId(701L);
	}

	@Test
	@DisplayName("8.2 getById (ID & MerchantID): Trả về null và không truy vấn descriptions khi không tìm thấy")
	void testGetById_IdAndMerchantId_NotFound_ReturnsNull() {
		when(categoryRepository.findByIdAndStore(999L, store.getId())).thenReturn(null);

		Category result = categoryService.getById(999L, store.getId());

		assertThat(result).isNull();
		verify(categoryDescriptionRepository, never()).listByCategoryId(any());
	}

	@Test
	@DisplayName("8.3 getById (MerchantStore & ID): Tìm thấy danh mục theo Store và ID")
	void testGetById_MerchantStoreAndId_Success() throws ServiceException {
		Category cat = createSampleCategory(702L, "CAT_STORE_ID");
		when(categoryRepository.findById(702L, store.getCode())).thenReturn(cat);

		Category result = categoryService.getById(store, 702L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(702L);
	}

	@Test
	@DisplayName("8.4 getById (CategoryID, MerchantID & Language): Tìm thấy danh mục")
	void testGetById_WithMerchantIdAndLanguage_Success() {
		Category cat = createSampleCategory(703L, "CAT_TRIPLE");
		when(categoryRepository.findById(store.getId(), 703L, languageEn.getId())).thenReturn(cat);

		Category result = categoryService.getById(703L, store.getId(), languageEn.getId());

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(703L);
	}

	@Test
	@DisplayName("8.5 findById: Trả về danh mục khi Optional tồn tại")
	void testFindById_Found_ReturnsCategory() {
		Category cat = createSampleCategory(704L, "CAT_FIND_ID");
		when(categoryRepository.findById(704L)).thenReturn(Optional.of(cat));

		Category result = categoryService.findById(704L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(704L);
	}

	@Test
	@DisplayName("8.6 findById: Trả về null khi Optional rỗng")
	void testFindById_NotFound_ReturnsNull() {
		when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

		Category result = categoryService.findById(999L);

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("8.7 listByIds: Trả về danh sách danh mục theo danh sách ID và Language")
	void testListByIds_Success() {
		List<Long> ids = Arrays.asList(705L, 706L);
		Category cat1 = createSampleCategory(705L, "CAT_705");
		Category cat2 = createSampleCategory(706L, "CAT_706");

		when(categoryRepository.findByIds(store.getId(), ids, languageEn.getId()))
				.thenReturn(Arrays.asList(cat1, cat2));

		List<Category> results = categoryService.listByIds(store, ids, languageEn);

		assertThat(results).hasSize(2);
		assertThat(results).extracting(Category::getId).containsExactlyInAnyOrder(705L, 706L);
	}

	@Test
	@DisplayName("8.8 getOneByLanguage: Trả về danh mục theo CategoryID và Language")
	void testGetOneByLanguage_Success() {
		Category cat = createSampleCategory(707L, "CAT_ONE_LANG");
		when(categoryRepository.findByIdAndLanguage(707L, languageEn.getId())).thenReturn(cat);

		Category result = categoryService.getOneByLanguage(707L, languageEn);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(707L);
	}

	@Test
	@DisplayName("8.9 getByProductId: Trả về danh mục liên kết với sản phẩm")
	void testGetByProductId_Success() {
		Category cat = createSampleCategory(708L, "CAT_PROD_LINK");
		when(categoryRepository.listByProduct(store, 888L))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getByProductId(888L, store);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(708L);
	}

	@Test
	@DisplayName("8.10 countProductsByCategories: Trả về thống kê số lượng sản phẩm theo danh mục")
	void testCountProductsByCategories_Success() throws ServiceException {
		List<Long> categoryIds = Collections.singletonList(709L);
		Object[] row = new Object[]{709L, 45L};
		List<Object[]> expectedCounts = Collections.singletonList(row);

		when(categoryRepository.countProductsByCategories(store, categoryIds))
				.thenReturn(expectedCounts);

		List<Object[]> results = categoryService.countProductsByCategories(store, categoryIds);

		assertThat(results).hasSize(1);
		assertThat(results.get(0)[0]).isEqualTo(709L);
		assertThat(results.get(0)[1]).isEqualTo(45L);
	}

	@Test
	@DisplayName("8.11 getByName: Tìm danh mục theo tên và ngôn ngữ")
	void testGetByName_Success() throws ServiceException {
		Category cat = createSampleCategory(710L, "CAT_BY_NAME");
		when(categoryRepository.findByName(store.getId(), "Fashion", languageEn.getId()))
				.thenReturn(Collections.singletonList(cat));

		List<Category> results = categoryService.getByName(store, "Fashion", languageEn);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(710L);
	}

	@Test
	@DisplayName("8.12 getByName: Ném ServiceException khi repository gặp lỗi")
	void testGetByName_Exception_ThrowsServiceException() {
		when(categoryRepository.findByName(store.getId(), "ErrName", languageEn.getId()))
				.thenThrow(new RuntimeException("DB lookup failure"));

		assertThatThrownBy(() -> categoryService.getByName(store, "ErrName", languageEn))
				.isInstanceOf(ServiceException.class);
	}

	// =========================================================================
	// NHÓM 9: GENERIC ENTITY SERVICE METHODS (KẾ THỪA TỪ SalesManagerEntityService)
	// =========================================================================

	@Test
	@DisplayName("9.1 getById: Gọi generic getById lấy thực thể từ repository qua getOne")
	void testGetById_GenericMethod_ReturnsCategory() {
		Category cat = createSampleCategory(801L, "CAT_GEN_ID");
		when(categoryRepository.getOne(801L)).thenReturn(cat);

		Category result = categoryService.getById(801L);

		assertThat(result).isNotNull();
		assertThat(result.getId()).isEqualTo(801L);
		verify(categoryRepository, times(1)).getOne(801L);
	}

	@Test
	@DisplayName("9.2 list: Gọi repository.findAll() trả về tất cả danh mục")
	void testList_ReturnsAllCategories() {
		Category cat1 = createSampleCategory(802L, "CAT_ALL_01");
		Category cat2 = createSampleCategory(803L, "CAT_ALL_02");
		when(categoryRepository.findAll()).thenReturn(Arrays.asList(cat1, cat2));

		List<Category> results = categoryService.list();

		assertThat(results).hasSize(2);
		verify(categoryRepository, times(1)).findAll();
	}

	@Test
	@DisplayName("9.3 count: Gọi repository.count() trả về tổng số danh mục")
	void testCount_ReturnsTotalCategoryCount() {
		when(categoryRepository.count()).thenReturn(88L);

		Long count = categoryService.count();

		assertThat(count).isEqualTo(88L);
		verify(categoryRepository, times(1)).count();
	}

	@Test
	@DisplayName("9.4 flush: Gọi repository.flush()")
	void testFlush_CallsRepositoryFlush() {
		categoryService.flush();

		verify(categoryRepository, times(1)).flush();
	}

	@Test
	@DisplayName("9.5 saveAll: Gọi repository.saveAll(...) cho danh sách danh mục")
	void testSaveAll_CallsRepositorySaveAll() throws ServiceException {
		List<Category> categories = Arrays.asList(
				createSampleCategory(804L, "CAT_BATCH_01"),
				createSampleCategory(805L, "CAT_BATCH_02")
		);

		categoryService.saveAll(categories);

		verify(categoryRepository, times(1)).saveAll(categories);
	}
}
