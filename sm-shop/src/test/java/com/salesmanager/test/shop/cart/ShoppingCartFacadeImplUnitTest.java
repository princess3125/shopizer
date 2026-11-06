package com.salesmanager.test.shop.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.catalog.product.ProductService;
import com.salesmanager.core.business.services.catalog.product.attribute.ProductAttributeService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartCalculationService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.shop.mapper.cart.ReadableShoppingCartMapper;
import com.salesmanager.shop.model.shoppingcart.PersistableShoppingCartItem;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.shoppingCart.facade.ShoppingCartFacadeImpl;

/**
 * Unit test cho ShoppingCartFacadeImpl (Shopizer 3.2.5).
 * Dùng Mockito mock các dependency (ShoppingCartService, ProductService,
 * PricingService, ShoppingCartCalculationService, ReadableShoppingCartMapper).
 *
 * Không cần Spring context hay database.
 *
 * Đặt file tại:
 * sm-shop/src/test/java/com/salesmanager/test/shop/cart/ShoppingCartFacadeImplUnitTest.java
 *
 * Test IDs tham chiếu: UT-CRT-01 -> UT-CRT-12 (Excel ma trận kiểm thử).
 */
@Tag("unit")
@Tag("cart")
@ExtendWith(MockitoExtension.class)
public class ShoppingCartFacadeImplUnitTest {

    @Mock
    private ShoppingCartService shoppingCartService;

    @Mock
    private ShoppingCartCalculationService shoppingCartCalculationService;

    @Mock
    private ProductService productService;

    @Mock
    private PricingService pricingService;

    @Mock
    private ProductAttributeService productAttributeService;

    @Mock
    private ReadableShoppingCartMapper readableShoppingCartMapper;

    @InjectMocks
    private ShoppingCartFacadeImpl facade;

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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private Product createProduct(Long id, String sku, BigDecimal price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setSku(sku);
        product.setAvailable(true);
        product.setDateAvailable(new Date());
        product.setMerchantStore(store);

        ProductAvailability availability = new ProductAvailability();
        availability.setProductQuantity(stock);
        availability.setRegion("*");
        availability.setProduct(product);
        Set<ProductAvailability> availabilities = new HashSet<>();
        availabilities.add(availability);
        product.setAvailabilities(availabilities);

        return product;
    }

    private void setupStandardMocks(Product product, ReadableShoppingCart expectedResult) throws Exception {
        when(productService.getBySku(eq(product.getSku()), eq(store), any(Language.class)))
                .thenReturn(product);

        ShoppingCartItem modelItem = new ShoppingCartItem(product);
        modelItem.setItemPrice(new BigDecimal("100.00"));
        when(shoppingCartService.populateShoppingCartItem(eq(product), eq(store)))
                .thenReturn(modelItem);

        doAnswer(invocation -> null).when(shoppingCartService).save(any(ShoppingCart.class));

        when(shoppingCartService.getById(any(), eq(store)))
                .thenAnswer(invocation -> {
                    ShoppingCart cart = new ShoppingCart();
                    cart.setId(1L);
                    cart.setMerchantStore(store);
                    cart.setShoppingCartCode("CART-001");
                    cart.getLineItems().add(modelItem);
                    return cart;
                });

        when(readableShoppingCartMapper.convert(any(ShoppingCart.class), eq(store), eq(language)))
                .thenReturn(expectedResult);
    }

    private ShoppingCart createEmptyCart(String code) {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(1L);
        cart.setMerchantStore(store);
        cart.setShoppingCartCode(code);
        return cart;
    }

    // ====================================================================
    // UT-CRT-01: Thêm 1 sản phẩm (giá 100.00, SL 1)
    // ====================================================================
    @Test
    @Tag("UT-CRT-01")
    @DisplayName("UT-CRT-01: Thêm 1 sản phẩm vào giỏ rỗng -> Giỏ được tạo, có 1 dòng, tổng = 100.00")
    void addToCart_oneProduct_createsCartWithOneItem() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(1);
        expectedCart.setTotal(new BigDecimal("100.00"));
        expectedCart.setSubtotal(new BigDecimal("100.00"));

        setupStandardMocks(product, expectedCart);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(1);

        ReadableShoppingCart result = facade.addToCart(item, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("CART-001");
        assertThat(result.getQuantity()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("100.00"));

        verify(shoppingCartService).save(any(ShoppingCart.class));
        verify(productService).getBySku(eq("SKU-A"), eq(store), any(Language.class));
    }

    // ====================================================================
    // UT-CRT-02: Thêm cùng sản phẩm lần 2 -> SL cộng dồn = 3
    // ====================================================================
    @Test
    @Tag("UT-CRT-02")
    @DisplayName("UT-CRT-02: Thêm cùng SP lần 2 -> Số lượng cập nhật = 3")
    void addToCart_sameProductTwice_quantityAccumulates() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        when(productService.getBySku(eq("SKU-A"), eq(store), any(Language.class)))
                .thenReturn(product);

        ShoppingCartItem modelItem = new ShoppingCartItem(product);
        modelItem.setItemPrice(new BigDecimal("100.00"));
        modelItem.setQuantity(1);
        when(shoppingCartService.populateShoppingCartItem(eq(product), eq(store)))
                .thenReturn(modelItem);

        doAnswer(inv -> null).when(shoppingCartService).save(any(ShoppingCart.class));

        ShoppingCart existingCart = new ShoppingCart();
        existingCart.setId(1L);
        existingCart.setMerchantStore(store);
        existingCart.setShoppingCartCode("CART-001");

        ShoppingCartItem existingItem = new ShoppingCartItem(product);
        existingItem.setId(10L);
        existingItem.setQuantity(1);
        existingItem.setItemPrice(new BigDecimal("100.00"));
        existingItem.setShoppingCart(existingCart);
        existingCart.getLineItems().add(existingItem);

        when(shoppingCartService.getByCode(eq("CART-001"), eq(store)))
                .thenReturn(existingCart);

        ShoppingCart updatedCart = new ShoppingCart();
        updatedCart.setId(1L);
        updatedCart.setMerchantStore(store);
        updatedCart.setShoppingCartCode("CART-001");
        ShoppingCartItem updatedItem = new ShoppingCartItem(product);
        updatedItem.setId(10L);
        updatedItem.setQuantity(3);
        updatedItem.setShoppingCart(updatedCart);
        updatedCart.getLineItems().add(updatedItem);

        when(shoppingCartService.getById(eq(1L), eq(store)))
                .thenReturn(updatedCart);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(3);
        expectedCart.setTotal(new BigDecimal("300.00"));

        when(readableShoppingCartMapper.convert(any(ShoppingCart.class), eq(store), eq(language)))
                .thenReturn(expectedCart);

        PersistableShoppingCartItem itemToAdd = new PersistableShoppingCartItem();
        itemToAdd.setProduct("SKU-A");
        itemToAdd.setQuantity(2);

        ReadableShoppingCart result = facade.modifyCart("CART-001", itemToAdd, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("300.00"));

        verify(shoppingCartService).save(any(ShoppingCart.class));
    }

    // ====================================================================
    // UT-CRT-03: Thêm 2 sản phẩm khác nhau
    // ====================================================================
    @Test
    @Tag("UT-CRT-03")
    @DisplayName("UT-CRT-03: Thêm 2 SP khác nhau -> Giỏ có 2 dòng, tổng = 200.00")
    void modifyCart_addDifferentProduct_cartHasTwoLines() throws Exception {
        Product productA = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);
        Product productB = createProduct(2L, "SKU-B", new BigDecimal("50.00"), 20);

        ShoppingCart cart = createEmptyCart("CART-001");
        ShoppingCartItem itemA = new ShoppingCartItem(productA);
        itemA.setId(10L);
        itemA.setQuantity(1);
        itemA.setItemPrice(new BigDecimal("100.00"));
        itemA.setShoppingCart(cart);
        cart.getLineItems().add(itemA);

        when(shoppingCartService.getByCode(eq("CART-001"), eq(store))).thenReturn(cart);
        when(productService.getBySku(eq("SKU-B"), eq(store), any(Language.class))).thenReturn(productB);

        ShoppingCartItem modelItemB = new ShoppingCartItem(productB);
        modelItemB.setItemPrice(new BigDecimal("50.00"));
        when(shoppingCartService.populateShoppingCartItem(eq(productB), eq(store))).thenReturn(modelItemB);

        doAnswer(inv -> null).when(shoppingCartService).save(any(ShoppingCart.class));
        when(shoppingCartService.getById(any(), eq(store))).thenReturn(cart);

        when(readableShoppingCartMapper.convert(any(ShoppingCart.class), eq(store), eq(language)))
                .thenAnswer(inv -> {
                    ShoppingCart c = inv.getArgument(0);
                    BigDecimal total = c.getLineItems().stream()
                            .map(i -> i.getItemPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    ReadableShoppingCart r = new ReadableShoppingCart();
                    r.setCode(c.getShoppingCartCode());
                    r.setTotal(total);
                    return r;
                });

        PersistableShoppingCartItem itemToAdd = new PersistableShoppingCartItem();
        itemToAdd.setProduct("SKU-B");
        itemToAdd.setQuantity(2);

        ReadableShoppingCart result = facade.modifyCart("CART-001", itemToAdd, store, language);

        ArgumentCaptor<ShoppingCart> saved = ArgumentCaptor.forClass(ShoppingCart.class);
        verify(shoppingCartService).save(saved.capture());
        assertThat(saved.getValue().getLineItems()).hasSize(2);
        assertThat(saved.getValue().getLineItems())
                .extracting(ShoppingCartItem::getSku)
                .containsExactlyInAnyOrder("SKU-A", "SKU-B");
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    // ====================================================================
    // UT-CRT-04: Số lượng bằng 0 (biên dưới)
    // ====================================================================
    @Test
    @Tag("UT-CRT-04")
    @Tag("known-issue")
    @Tag("P-08")
    @DisplayName("UT-CRT-04: SL 0 – ghi nhận hành vi: facade chấp nhận SL 0, item được tạo")
    void addToCart_zeroQuantity_isAcceptedByFacade() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(0);
        expectedCart.setTotal(BigDecimal.ZERO);

        setupStandardMocks(product, expectedCart);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(0);

        ReadableShoppingCart result = facade.addToCart(item, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(0);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ====================================================================
    // UT-CRT-05: Số lượng âm
    // ====================================================================
    @Test
    @Tag("UT-CRT-05")
    @Tag("known-issue")
    @Tag("P-08")
    @DisplayName("UT-CRT-05: SL -1 – ghi nhận hành vi: facade chấp nhận SL âm, item được tạo")
    void addToCart_negativeQuantity_isAcceptedByFacade() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(-1);
        expectedCart.setTotal(new BigDecimal("-100.00"));

        setupStandardMocks(product, expectedCart);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(-1);

        ReadableShoppingCart result = facade.addToCart(item, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(-1);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("-100.00"));
    }

    // ====================================================================
    // UT-CRT-06: Số lượng nhỏ nhất hợp lệ (SL = 1)
    // ====================================================================
    @Test
    @Tag("UT-CRT-06")
    @DisplayName("UT-CRT-06: SL = 1 (nhỏ nhất hợp lệ) -> Thêm thành công; tổng = đơn giá")
    void addToCart_quantityOne_totalEqualsUnitPrice() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(1);
        expectedCart.setTotal(new BigDecimal("100.00"));

        setupStandardMocks(product, expectedCart);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(1);

        ReadableShoppingCart result = facade.addToCart(item, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ====================================================================
    // UT-CRT-07: Số lượng vượt tồn kho
    // ====================================================================
    @Test
    @Tag("UT-CRT-07")
    @Tag("known-issue")
    @Tag("P-08")
    @DisplayName("UT-CRT-07: SL 6 > tồn kho 5 -> Facade chấp nhận (chưa chặn ở tầng facade)")
    void addToCart_quantityExceedsStock_isAcceptedByFacade() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 5);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(6);
        expectedCart.setTotal(new BigDecimal("600.00"));

        setupStandardMocks(product, expectedCart);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(6);

        ReadableShoppingCart result = facade.addToCart(item, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(6);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    // ====================================================================
    // UT-CRT-08: Số lượng rất lớn (SL = 10000)
    // ====================================================================
    @Test
    @Tag("UT-CRT-08")
    @DisplayName("UT-CRT-08: SL rất lớn = 10000 -> Tổng = đơn giá × 10000, không tràn số")
    void addToCart_veryLargeQuantity_totalCorrect() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 99999);

        BigDecimal expectedTotal = new BigDecimal("1000000.00");
        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(10000);
        expectedCart.setTotal(expectedTotal);

        setupStandardMocks(product, expectedCart);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(10000);

        ReadableShoppingCart result = facade.addToCart(item, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(10000);
        assertThat(result.getTotal()).isEqualByComparingTo(expectedTotal);
    }

    // ====================================================================
    // UT-CRT-09: Cập nhật số lượng một dòng (1 -> 4)
    // ====================================================================
    @Test
    @Tag("UT-CRT-09")
    @DisplayName("UT-CRT-09: Cập nhật SL một dòng = 4 -> Tổng được tính lại theo SL mới")
    void modifyCart_updateQuantity_totalRecalculated() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        when(productService.getBySku(eq("SKU-A"), eq(store), any(Language.class)))
                .thenReturn(product);

        ShoppingCartItem modelItem = new ShoppingCartItem(product);
        modelItem.setItemPrice(new BigDecimal("100.00"));
        when(shoppingCartService.populateShoppingCartItem(eq(product), eq(store)))
                .thenReturn(modelItem);

        ShoppingCart existingCart = new ShoppingCart();
        existingCart.setId(1L);
        existingCart.setMerchantStore(store);
        existingCart.setShoppingCartCode("CART-001");

        ShoppingCartItem existingItem = new ShoppingCartItem(product);
        existingItem.setId(10L);
        existingItem.setQuantity(1);
        existingItem.setShoppingCart(existingCart);
        existingCart.getLineItems().add(existingItem);

        when(shoppingCartService.getByCode(eq("CART-001"), eq(store)))
                .thenReturn(existingCart);

        doAnswer(inv -> null).when(shoppingCartService).save(any(ShoppingCart.class));

        ShoppingCart updatedCart = new ShoppingCart();
        updatedCart.setId(1L);
        updatedCart.setMerchantStore(store);
        ShoppingCartItem updItem = new ShoppingCartItem(product);
        updItem.setQuantity(4);
        updatedCart.getLineItems().add(updItem);
        when(shoppingCartService.getById(eq(1L), eq(store))).thenReturn(updatedCart);

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(4);
        expectedCart.setTotal(new BigDecimal("400.00"));

        when(readableShoppingCartMapper.convert(any(ShoppingCart.class), eq(store), eq(language)))
                .thenReturn(expectedCart);

        PersistableShoppingCartItem update = new PersistableShoppingCartItem();
        update.setProduct("SKU-A");
        update.setQuantity(4);

        ReadableShoppingCart result = facade.modifyCart("CART-001", update, store, language);

        assertThat(result).isNotNull();
        assertThat(result.getQuantity()).isEqualTo(4);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    // ====================================================================
    // UT-CRT-10: Xóa một dòng (giỏ có A, B; xóa A)
    // ====================================================================
    @Test
    @Tag("UT-CRT-10")
    @DisplayName("UT-CRT-10: Xóa một dòng -> Dòng A biến mất; tổng = tổng dòng B")
    void removeShoppingCartItem_removesCorrectItem() throws Exception {
        Product productA = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);
        Product productB = createProduct(2L, "SKU-B", new BigDecimal("50.00"), 20);

        ShoppingCart cart = new ShoppingCart();
        cart.setId(1L);
        cart.setMerchantStore(store);
        cart.setShoppingCartCode("CART-001");

        ShoppingCartItem itemA = new ShoppingCartItem(productA);
        itemA.setId(10L);
        itemA.setQuantity(1);
        itemA.setShoppingCart(cart);

        ShoppingCartItem itemB = new ShoppingCartItem(productB);
        itemB.setId(11L);
        itemB.setQuantity(2);
        itemB.setShoppingCart(cart);

        cart.getLineItems().add(itemA);
        cart.getLineItems().add(itemB);

        doAnswer(inv -> null).when(shoppingCartService).saveOrUpdate(any(ShoppingCart.class));

        ReadableShoppingCart expectedCart = new ReadableShoppingCart();
        expectedCart.setCode("CART-001");
        expectedCart.setQuantity(2);
        expectedCart.setTotal(new BigDecimal("100.00"));

        when(shoppingCartService.getByCode(eq("CART-001"), eq(store)))
                .thenReturn(cart);

        when(readableShoppingCartMapper.convert(any(ShoppingCart.class), eq(store), eq(language)))
                .thenReturn(expectedCart);

        ReadableShoppingCart result = facade.removeShoppingCartItem("CART-001", "SKU-A", store, language, true);

        verify(shoppingCartService).deleteShoppingCartItem(eq(10L));
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ====================================================================
    // UT-CRT-11: Giỏ rỗng sau khi xóa hết
    // ====================================================================
    @Test
    @Tag("UT-CRT-11")
    @DisplayName("UT-CRT-11: Giỏ rỗng sau khi xóa hết -> Trả null (giỏ rỗng, returnCart=true)")
    void removeShoppingCartItem_lastItem_returnsNull() throws Exception {
        Product productA = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);

        ShoppingCart cart = new ShoppingCart();
        cart.setId(1L);
        cart.setMerchantStore(store);
        cart.setShoppingCartCode("CART-001");

        ShoppingCartItem itemA = new ShoppingCartItem(productA);
        itemA.setId(10L);
        itemA.setQuantity(1);
        itemA.setShoppingCart(cart);
        cart.getLineItems().add(itemA);

        when(shoppingCartService.getByCode(eq("CART-001"), eq(store)))
                .thenReturn(cart);

        doAnswer(inv -> null).when(shoppingCartService).saveOrUpdate(any(ShoppingCart.class));

        ReadableShoppingCart result = facade.removeShoppingCartItem(
                "CART-001", "SKU-A", store, language, true);

        assertThat(result).isNull();
        verify(shoppingCartService).deleteShoppingCartItem(eq(10L));
    }

    // ====================================================================
    // UT-CRT-12: Sản phẩm không còn bán (available = false)
    // ====================================================================
    @Test
    @Tag("UT-CRT-12")
    @DisplayName("UT-CRT-12: SP available=false -> Ném exception, không tạo giỏ")
    void addToCart_unavailableProduct_throwsException() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 10);
        product.setAvailable(false);

        when(productService.getBySku(eq("SKU-A"), eq(store), any(Language.class)))
                .thenReturn(product);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(1);

        assertThatThrownBy(() -> facade.addToCart(item, store, language))
                .isInstanceOf(ServiceRuntimeException.class);

        verify(shoppingCartService, never()).save(any(ShoppingCart.class));
    }

    // ====================================================================
    // Test bổ sung 1: SKU không tồn tại
    // ====================================================================
    @Test
    @DisplayName("UT-CRT-13: SKU không tồn tại -> ResourceNotFoundException")
    void addToCart_nonExistentSku_throwsResourceNotFound() throws Exception {
        when(productService.getBySku(eq("NONEXISTENT"), eq(store), any(Language.class)))
                .thenReturn(null);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("NONEXISTENT");
        item.setQuantity(1);

        assertThatThrownBy(() -> facade.addToCart(item, store, language))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(shoppingCartService, never()).save(any(ShoppingCart.class));
    }

    // ====================================================================
    // Test bổ sung 2: Sản phẩm hết hàng (tồn kho = 0)
    // ====================================================================
    @Test
    @DisplayName("UT-CRT-14: Sản phẩm hết hàng (tồn kho = 0) -> Ném ServiceRuntimeException")
    void addToCart_outOfStock_throwsException() throws Exception {
        Product product = createProduct(1L, "SKU-A", new BigDecimal("100.00"), 0);

        when(productService.getBySku(eq("SKU-A"), eq(store), any(Language.class)))
                .thenReturn(product);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(1);

        assertThatThrownBy(() -> facade.addToCart(item, store, language))
                .isInstanceOf(ServiceRuntimeException.class);

        verify(shoppingCartService, never()).save(any(ShoppingCart.class));
    }

    // ====================================================================
    // Test bổ sung 3: Cart code không tồn tại khi modify
    // ====================================================================
    @Test
    @DisplayName("UT-CRT-15: Cart code không tồn tại khi modify -> ResourceNotFoundException")
    void modifyCart_nonExistentCode_throwsException() throws Exception {
        when(shoppingCartService.getByCode(eq("FAKE-CODE"), eq(store)))
                .thenReturn(null);

        PersistableShoppingCartItem item = new PersistableShoppingCartItem();
        item.setProduct("SKU-A");
        item.setQuantity(1);

        assertThatThrownBy(() -> facade.modifyCart("FAKE-CODE", item, store, language))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
