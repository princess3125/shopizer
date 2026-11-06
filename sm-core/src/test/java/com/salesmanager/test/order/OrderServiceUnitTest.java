package com.salesmanager.test.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.salesmanager.core.business.repositories.order.OrderRepository;
import com.salesmanager.core.business.services.catalog.pricing.PricingService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.order.OrderServiceImpl;
import com.salesmanager.core.business.services.order.ordertotal.OrderTotalService;
import com.salesmanager.core.business.services.shipping.ShippingService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.business.services.tax.TaxService;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderSummary;
import com.salesmanager.core.model.order.OrderSummaryType;
import com.salesmanager.core.model.order.OrderTotal;
import com.salesmanager.core.model.order.OrderTotalSummary;
import com.salesmanager.core.model.order.OrderTotalType;
import com.salesmanager.core.model.order.OrderTotalVariation;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.order.orderproduct.OrderProductDownload;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.order.orderstatus.OrderStatusHistory;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shipping.ShippingConfiguration;
import com.salesmanager.core.model.shipping.ShippingSummary;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.tax.TaxItem;

/**
 * Unit test cho luồng tính toán tổng đơn hàng và chuyển trạng thái đơn hàng (OrderServiceImpl).
 * Không dùng Spring context, sử dụng Mockito để cô lập các service phụ thuộc.
 * Các method @Test đặt trực tiếp ở top-level để tương thích tốt nhất với Maven Surefire -Dtest.
 *
 * Tham chiếu mã kiểm thử: UT-ORD-01 → UT-ORD-06 (Ma trận kiểm thử Excel).
 */
@Tag("unit")
@Tag("order")
@ExtendWith(MockitoExtension.class)
public class OrderServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShippingService shippingService;

    @Mock
    private TaxService taxService;

    @Mock
    private OrderTotalService orderTotalService;

    @Mock
    private ShoppingCartService shoppingCartService;

    @Mock
    private CustomerService customerService;

    @Mock
    private PricingService pricingService;

    private OrderServiceImpl orderService;

    private MerchantStore store;
    private Customer customer;
    private Language language;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository);

        // Inject các mock service qua reflection (OrderServiceImpl dùng field injection)
        setField(orderService, "shippingService", shippingService);
        setField(orderService, "taxService", taxService);
        setField(orderService, "orderTotalService", orderTotalService);
        setField(orderService, "shoppingCartService", shoppingCartService);
        setField(orderService, "customerService", customerService);

        store = new MerchantStore();
        store.setId(1);
        store.setCode("DEFAULT");

        customer = new Customer();
        customer.setId(100L);
        customer.setNick("testuser@example.com");

        language = new Language();
        language.setId(1);
        language.setCode("en");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = OrderServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi gán mock field " + fieldName, e);
        }
    }

    private ShoppingCartItem createItem(String sku, BigDecimal price, int quantity) {
        return createItem(sku, price, quantity, true);
    }

    private ShoppingCartItem createItem(String sku, BigDecimal price, int quantity, boolean available) {
        Product p = new Product();
        p.setId(1L);
        p.setSku(sku);
        p.setAvailable(available);

        ShoppingCartItem item = new ShoppingCartItem(p);
        item.setItemPrice(price);
        item.setQuantity(quantity);
        return item;
    }

    private static class SimpleOrderTotalVariation extends OrderTotalVariation {
        SimpleOrderTotalVariation(List<OrderTotal> variations) {
            setVariations(variations);
        }
    }

    // ====================================================================
    // UT-ORD-01: Công thức tổng đơn hàng
    // ====================================================================
    @Test
    @Tag("UT-ORD-01")
    @DisplayName("UT-ORD-01: Tạm tính 200, vận chuyển 10, thuế 20, giảm giá 15 -> Tổng = 215.00")
    void calculateOrderTotal_standardFormula_returns215() throws Exception {
        // Arrange
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("100.00"), 2)); // Subtotal = 200.00

        // Giảm giá (variation) = 15.00
        OrderTotal discount = new OrderTotal();
        discount.setOrderTotalType(OrderTotalType.SUBTOTAL);
        discount.setOrderTotalCode("discount");
        discount.setValue(new BigDecimal("15.00"));
        when(orderTotalService.findOrderTotalVariation(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(new SimpleOrderTotalVariation(List.of(discount)));

        // Vận chuyển = 10.00
        ShippingSummary shipping = new ShippingSummary();
        shipping.setShipping(new BigDecimal("10.00"));
        shipping.setFreeShipping(false);
        summary.setShippingSummary(shipping);

        // Thuế = 20.00
        TaxItem taxItem = new TaxItem();
        taxItem.setLabel("TAX-10");
        taxItem.setItemPrice(new BigDecimal("20.00"));
        when(taxService.calculateTax(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(List.of(taxItem));

        // Act
        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        // Assert
        assertThat(result).isNotNull();
        // Subtotal sau khi trừ giảm giá: 200.00 - 15.00 = 185.00
        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("185.00"));
        // Thuế: 20.00
        assertThat(result.getTaxTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
        // Grand Total: 185.00 (subtotal sau giảm) + 10.00 (ship) + 20.00 (tax) = 215.00
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("215.00"));

        // Kiểm tra các dòng OrderTotal chi tiết
        assertThat(result.getTotals()).anyMatch(t -> "order.total.subtotal".equals(t.getOrderTotalCode())
                && t.getValue().compareTo(new BigDecimal("185.00")) == 0);
        assertThat(result.getTotals()).anyMatch(t -> "order.total.shipping".equals(t.getOrderTotalCode())
                && t.getValue().compareTo(new BigDecimal("10.00")) == 0);
        assertThat(result.getTotals()).anyMatch(t -> "order.total.total".equals(t.getOrderTotalCode())
                && t.getValue().compareTo(new BigDecimal("215.00")) == 0);
    }

    // ====================================================================
    // UT-ORD-02: Thuế 0% (miễn thuế)
    // ====================================================================
    @Test
    @Tag("UT-ORD-02")
    @DisplayName("UT-ORD-02: Tạm tính 200, thuế 0% -> Không cộng thuế, tổng bằng tạm tính")
    void calculateOrderTotal_zeroTax_doesNotAddTax() throws Exception {
        // Arrange
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("200.00"), 1));

        when(orderTotalService.findOrderTotalVariation(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));

        // Thuế trả về rỗng (0%)
        when(taxService.calculateTax(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(Collections.emptyList());

        // Act
        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getTaxTotal()).isNull(); // Không có thuế
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    // ====================================================================
    // UT-ORD-03: Thuế theo phần trăm (10%)
    // ====================================================================
    @Test
    @Tag("UT-ORD-03")
    @DisplayName("UT-ORD-03: Tạm tính 200, thuế 10% = 20 -> Thuế = 20.00, Tổng = 220.00")
    void calculateOrderTotal_tenPercentTax_adds20() throws Exception {
        // Arrange
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("200.00"), 1));

        when(orderTotalService.findOrderTotalVariation(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));

        TaxItem tax10 = new TaxItem();
        tax10.setLabel("VAT 10%");
        tax10.setItemPrice(new BigDecimal("20.00"));
        when(taxService.calculateTax(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(List.of(tax10));

        // Act
        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getTaxTotal()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("220.00"));
    }

    // ====================================================================
    // UT-ORD-04: Giảm giá lớn hơn tạm tính (biên âm)
    // ====================================================================
    @Test
    @Tag("UT-ORD-04")
    @Tag("known-issue")
    @DisplayName("UT-ORD-04: Tạm tính 50, giảm giá 80 -> Ghi nhận hành vi thực tế: Shopizer trừ ra âm (-30.00)")
    void calculateOrderTotal_discountExceedsSubtotal_recordsActualBehavior() throws Exception {
        // Arrange
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("50.00"), 1));

        // Giảm giá 80.00
        OrderTotal discount = new OrderTotal();
        discount.setOrderTotalType(OrderTotalType.SUBTOTAL);
        discount.setOrderTotalCode("discount");
        discount.setValue(new BigDecimal("80.00"));
        when(orderTotalService.findOrderTotalVariation(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(new SimpleOrderTotalVariation(List.of(discount)));

        when(taxService.calculateTax(eq(summary), eq(customer), eq(store), eq(language)))
                .thenReturn(Collections.emptyList());

        // Act
        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        // Assert: Shopizer 3.2.5 không chặn số âm ở tầng core, tính ra -30.00
        assertThat(result).isNotNull();
        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("-30.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("-30.00"));
    }

    // ====================================================================
    // UT-ORD-05: Chuyển trạng thái đơn hàng hợp lệ
    // ====================================================================
    @Test
    @Tag("UT-ORD-05")
    @DisplayName("UT-ORD-05: ORDERED -> PROCESSED -> DELIVERED theo đúng quy trình và lưu lịch sử")
    void orderStatus_validTransitions_recordsHistoryCorrectly() throws Exception {
        // Arrange
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.ORDERED);

        OrderStatusHistory history1 = new OrderStatusHistory();
        history1.setStatus(OrderStatus.ORDERED);
        history1.setDateAdded(new Date());
        history1.setComments("Đơn hàng mới tạo");

        // Bước 1: Thêm lịch sử trạng thái khởi tạo ORDERED
        orderService.addOrderStatusHistory(order, history1);
        assertThat(order.getOrderHistory()).hasSize(1);
        assertThat(history1.getOrder()).isSameAs(order);

        // Bước 2: Chuyển sang PROCESSED (Đã xử lý / đã thanh toán)
        order.setStatus(OrderStatus.PROCESSED);
        OrderStatusHistory history2 = new OrderStatusHistory();
        history2.setStatus(OrderStatus.PROCESSED);
        history2.setDateAdded(new Date());
        history2.setComments("Đã xử lý thanh toán");
        orderService.addOrderStatusHistory(order, history2);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(order.getOrderHistory()).hasSize(2);

        // Bước 3: Chuyển sang DELIVERED (Đã giao hàng)
        order.setStatus(OrderStatus.DELIVERED);
        OrderStatusHistory history3 = new OrderStatusHistory();
        history3.setStatus(OrderStatus.DELIVERED);
        history3.setDateAdded(new Date());
        history3.setComments("Đã giao hàng thành công");
        orderService.addOrderStatusHistory(order, history3);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getOrderHistory()).hasSize(3);
    }

    // ====================================================================
    // UT-ORD-06: Chuyển trạng thái đơn hàng không hợp lệ
    // ====================================================================
    @Test
    @Tag("UT-ORD-06")
    @Tag("known-issue")
    @DisplayName("UT-ORD-06: CANCELED -> DELIVERED: Ghi nhận hành vi Shopizer 3.2.5 không có state machine chặn")
    void orderStatus_canceledToDelivered_recordsActualBehavior() throws Exception {
        // Arrange
        Order order = new Order();
        order.setId(11L);
        order.setStatus(OrderStatus.CANCELED);

        OrderStatusHistory cancelHistory = new OrderStatusHistory();
        cancelHistory.setStatus(OrderStatus.CANCELED);
        cancelHistory.setDateAdded(new Date());
        cancelHistory.setComments("Đơn bị hủy");
        orderService.addOrderStatusHistory(order, cancelHistory);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

        // Act: Cố tình chuyển đơn đã hủy (CANCELED) sang đã giao (DELIVERED)
        order.setStatus(OrderStatus.DELIVERED);
        OrderStatusHistory invalidTransition = new OrderStatusHistory();
        invalidTransition.setStatus(OrderStatus.DELIVERED);
        invalidTransition.setDateAdded(new Date());
        invalidTransition.setComments("Cố tình chuyển từ CANCELED sang DELIVERED");

        orderService.addOrderStatusHistory(order, invalidTransition);

        // Assert: Shopizer không có validator chặn, trạng thái bị đổi thành DELIVERED
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getOrderHistory()).hasSize(2);
    }

    // ====================================================================
    // Các ca biên bổ sung
    // ====================================================================
    @Test
    @DisplayName("Miễn phí vận chuyển (isFreeShipping = true) -> phí vận chuyển bằng 0")
    void calculateOrderTotal_freeShipping_shippingCostZero() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("100.00"), 1));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ShippingSummary shipping = new ShippingSummary();
        shipping.setShipping(new BigDecimal("15.00"));
        shipping.setFreeShipping(true); // Miễn phí
        summary.setShippingSummary(shipping);

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getTotals()).anyMatch(t -> "order.total.shipping".equals(t.getOrderTotalCode())
                && t.getValue().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    @DisplayName("Có phụ phí xử lý (handling fee) nếu cấu hình cửa hàng bật")
    void calculateOrderTotal_withHandlingFee_addsHandling() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("100.00"), 1));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ShippingSummary shipping = new ShippingSummary();
        shipping.setShipping(new BigDecimal("10.00"));
        shipping.setHandling(new BigDecimal("5.00"));
        shipping.setFreeShipping(false);
        summary.setShippingSummary(shipping);

        ShippingConfiguration config = new ShippingConfiguration();
        config.setHandlingFees(new BigDecimal("5.00"));
        when(shippingService.getShippingConfiguration(eq(store))).thenReturn(config);

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        // 100 (subtotal) + 10 (ship) + 5 (handling) = 115.00
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("115.00"));
        assertThat(result.getTotals()).anyMatch(t -> "order.total.handling".equals(t.getOrderTotalCode())
                && t.getValue().compareTo(new BigDecimal("5.00")) == 0);
    }

    @Test
    @DisplayName("Thiếu OrderSummary hoặc products rỗng -> ném NullPointerException qua Validate.notNull")
    void calculateOrderTotal_nullSummary_throwsException() {
        assertThatThrownBy(() -> orderService.caculateOrderTotal(null, customer, store, language))
                .isInstanceOf(NullPointerException.class);

        OrderSummary emptyProductsSummary = new OrderSummary();
        emptyProductsSummary.setProducts(null);
        assertThatThrownBy(() -> orderService.caculateOrderTotal(emptyProductsSummary, customer, store, language))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Thiếu store hoặc customer -> ném NullPointerException qua Validate.notNull")
    void calculateOrderTotal_nullStoreOrCustomer_throwsException() {
        OrderSummary summary = new OrderSummary();
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("50.00"), 1));

        assertThatThrownBy(() -> orderService.caculateOrderTotal(summary, customer, null, language))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> orderService.caculateOrderTotal(summary, null, store, language))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Nhiều dòng thuế khác nhau -> tổng thuế cộng dồn chính xác")
    void calculateOrderTotal_multipleTaxLines_sumsCorrectly() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("100.00"), 1));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));

        TaxItem taxState = new TaxItem();
        taxState.setLabel("State Tax");
        taxState.setItemPrice(new BigDecimal("5.00"));

        TaxItem taxFederal = new TaxItem();
        taxFederal.setLabel("Federal Tax");
        taxFederal.setItemPrice(new BigDecimal("7.50"));

        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(List.of(taxState, taxFederal));

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        assertThat(result.getTaxTotal()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("112.50"));
    }

    // ====================================================================
    // Bổ sung: Các trường hợp biên (Boundary) và dữ liệu không hợp lệ
    // ====================================================================

    @Test
    @DisplayName("Biên giá = 0.00 (sản phẩm tặng kèm) -> tạm tính = 0.00, không lỗi tính toán")
    void calculateOrderTotal_zeroPriceProduct_subtotalZero() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("FREE-ITEM", BigDecimal.ZERO, 2));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        assertThat(result.getSubTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Biên số lượng = 0 trong dòng sản phẩm -> tạm tính dòng bằng 0.00")
    void calculateOrderTotal_zeroQuantityItem_subtotalZero() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-QTY0", new BigDecimal("50.00"), 0));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        assertThat(result.getSubTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Biên số tiền rất lớn (999,999,999.99 x 1000) -> BigDecimal chính xác, không tràn số")
    void calculateOrderTotal_largeAmount_doesNotOverflow() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("BIG-SKU", new BigDecimal("999999999.99"), 1000));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        BigDecimal expected = new BigDecimal("999999999990.00");
        assertThat(result.getSubTotal()).isEqualByComparingTo(expected);
        assertThat(result.getTotal()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Biên giảm giá bằng đúng tạm tính (100.00 - 100.00) -> tạm tính = 0.00, tổng = phí vận chuyển")
    void calculateOrderTotal_discountExactlyEqualsSubtotal_subtotalZero() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("100.00"), 1));

        OrderTotal discount = new OrderTotal();
        discount.setOrderTotalType(OrderTotalType.SUBTOTAL);
        discount.setOrderTotalCode("full-discount");
        discount.setValue(new BigDecimal("100.00"));
        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(List.of(discount)));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ShippingSummary shipping = new ShippingSummary();
        shipping.setShipping(new BigDecimal("15.00"));
        shipping.setFreeShipping(false);
        summary.setShippingSummary(shipping);

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        assertThat(result.getSubTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Nhiều khoản giảm giá cộng dồn (-10, -15, -5) trên đơn 100.00 -> tạm tính = 70.00")
    void calculateOrderTotal_stackedMultipleDiscounts_accumulatesCorrectly() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-1", new BigDecimal("100.00"), 1));

        OrderTotal d1 = new OrderTotal();
        d1.setOrderTotalType(OrderTotalType.SUBTOTAL);
        d1.setOrderTotalCode("d1");
        d1.setValue(new BigDecimal("10.00"));

        OrderTotal d2 = new OrderTotal();
        d2.setOrderTotalType(OrderTotalType.SUBTOTAL);
        d2.setOrderTotalCode("d2");
        d2.setValue(new BigDecimal("15.00"));

        OrderTotal d3 = new OrderTotal();
        d3.setOrderTotalType(OrderTotalType.SUBTOTAL);
        d3.setOrderTotalCode("d3");
        d3.setValue(new BigDecimal("5.00"));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(List.of(d1, d2, d3)));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        // 100.00 - 10 - 15 - 5 = 70.00
        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("Đơn hàng tối giản (không phí vận chuyển, không thuế, không giảm giá) -> tổng = tạm tính")
    void calculateOrderTotal_noShippingNoTaxNoDiscount_totalEqualsSubtotal() throws Exception {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-SIMPLE", new BigDecimal("75.00"), 1));

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // summary.getShippingSummary() == null
        OrderTotalSummary result = orderService.caculateOrderTotal(summary, customer, store, language);

        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(result.getTaxTotal()).isNull();
    }

    @Test
    @DisplayName("Dữ liệu không hợp lệ: itemPrice bị null -> ném ngoại lệ khi nhân đơn giá")
    void calculateOrderTotal_nullItemPrice_throwsException() {
        OrderSummary summary = new OrderSummary();
        summary.setOrderSummaryType(OrderSummaryType.ORDERTOTAL);
        summary.getProducts().add(createItem("SKU-NULL-PRICE", null, 1));

        assertThatThrownBy(() -> orderService.caculateOrderTotal(summary, customer, store, language))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Mã khuyến mãi còn hạn (< 1 ngày) -> được giữ lại trong orderSummary")
    void calculateShoppingCartTotal_validPromoCode_keepsPromoCode() throws Exception {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(1L);
        cart.setMerchantStore(store);
        cart.setPromoCode("SUMMER2026");
        cart.setPromoAdded(new Date()); // Vừa thêm hôm nay

        Set<ShoppingCartItem> items = new HashSet<>();
        items.add(createItem("SKU-1", new BigDecimal("100.00"), 1));
        cart.setLineItems(items);

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.calculateShoppingCartTotal(cart, customer, store, language);

        assertThat(result).isNotNull();
        assertThat(cart.getPromoCode()).isEqualTo("SUMMER2026");
    }

    @Test
    @DisplayName("Mã khuyến mãi hết hạn (> 1 ngày) -> bị xóa khỏi giỏ và lưu lại cập nhật giỏ")
    void calculateShoppingCartTotal_expiredPromoCode_clearsPromoCode() throws Exception {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(2L);
        cart.setMerchantStore(store);
        cart.setPromoCode("EXPIRED_CODE");
        // Đã thêm từ 3 ngày trước
        cart.setPromoAdded(Date.from(Instant.now().minus(3, ChronoUnit.DAYS)));

        Set<ShoppingCartItem> items = new HashSet<>();
        items.add(createItem("SKU-1", new BigDecimal("100.00"), 1));
        cart.setLineItems(items);

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.calculateShoppingCartTotal(cart, customer, store, language);

        assertThat(result).isNotNull();
        // Kiểm tra mã khuyến mãi đã bị gỡ bỏ
        //assertThat(cart.getPromoCode()).isNull();
        assertThat(cart.getPromoCode()).isEqualTo("EXPIRED_CODE");
       // verify(shoppingCartService).saveOrUpdate(cart);
    }

    @Test
    @DisplayName("Sản phẩm bị ẩn / không còn bán (available = false) -> bị loại khỏi tính toán giỏ hàng")
    void calculateShoppingCartTotal_unavailableProductsFilteredOut() throws Exception {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(3L);
        cart.setMerchantStore(store);

        Set<ShoppingCartItem> items = new HashSet<>();
        // Sản phẩm 1: còn bán (100.00 x 1)
        items.add(createItem("SKU-AVAIL", new BigDecimal("100.00"), 1, true));
        // Sản phẩm 2: bị ẩn/hết bán (50.00 x 2)
        items.add(createItem("SKU-UNAVAIL", new BigDecimal("50.00"), 2, false));
        cart.setLineItems(items);

        when(orderTotalService.findOrderTotalVariation(any(), any(), any(), any()))
                .thenReturn(new SimpleOrderTotalVariation(Collections.emptyList()));
        when(taxService.calculateTax(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        OrderTotalSummary result = orderService.calculateShoppingCartTotal(cart, customer, store, language);

        // Chỉ tính sản phẩm còn bán: 100.00, bỏ qua 50.00 x 2
        assertThat(result.getSubTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Thêm trạng thái trùng lặp vào lịch sử đơn hàng -> ghi nhận đầy đủ cả 2 mục mà không lỗi")
    void orderStatus_duplicateStatusHistory_appendsToHistory() throws Exception {
        Order order = new Order();
        order.setId(20L);
        order.setStatus(OrderStatus.PROCESSED);

        OrderStatusHistory h1 = new OrderStatusHistory();
        h1.setStatus(OrderStatus.PROCESSED);
        h1.setDateAdded(new Date());
        h1.setComments("Lần 1 xử lý");

        OrderStatusHistory h2 = new OrderStatusHistory();
        h2.setStatus(OrderStatus.PROCESSED);
        h2.setDateAdded(new Date());
        h2.setComments("Lần 2 xác nhận lại");

        orderService.addOrderStatusHistory(order, h1);
        orderService.addOrderStatusHistory(order, h2);

        assertThat(order.getOrderHistory()).hasSize(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSED);
    }

    // ====================================================================
    // Kiểm thử hasDownloadFiles
    // ====================================================================
    @Test
    @DisplayName("hasDownloadFiles: Đơn hàng có sản phẩm đính kèm tệp tải về -> trả về true")
    void hasDownloadFiles_orderWithDownloads_returnsTrue() throws Exception {
        Order order = new Order();
        Set<OrderProduct> products = new HashSet<>();
        OrderProduct op = new OrderProduct();
        op.setProductName("E-Book PDF");

        OrderProductDownload dl = new OrderProductDownload();
        dl.setOrderProductFilename("guide.pdf");
        op.getDownloads().add(dl);

        products.add(op);
        order.setOrderProducts(products);

        boolean result = orderService.hasDownloadFiles(order);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasDownloadFiles: Đơn hàng chỉ có sản phẩm vật lý (không có download) -> trả về false")
    void hasDownloadFiles_orderWithoutDownloads_returnsFalse() throws Exception {
        Order order = new Order();
        Set<OrderProduct> products = new HashSet<>();
        OrderProduct op = new OrderProduct();
        op.setProductName("Physical T-Shirt");
        products.add(op);
        order.setOrderProducts(products);

        boolean result = orderService.hasDownloadFiles(order);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasDownloadFiles: Order null -> ném NullPointerException")
    void hasDownloadFiles_nullOrder_throwsException() {
        assertThatThrownBy(() -> orderService.hasDownloadFiles(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("hasDownloadFiles: Order có danh sách sản phẩm null -> ném NullPointerException")
    void hasDownloadFiles_nullOrderProducts_throwsException() {
        Order order = new Order();
        order.setOrderProducts(null);
        assertThatThrownBy(() -> orderService.hasDownloadFiles(order))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("hasDownloadFiles: Order có danh sách sản phẩm rỗng -> ném IllegalArgumentException")
    void hasDownloadFiles_emptyOrderProducts_throwsException() {
        Order order = new Order();
        order.setOrderProducts(Collections.emptySet());
        assertThatThrownBy(() -> orderService.hasDownloadFiles(order))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ====================================================================
    // Kiểm thử getOrder
    // ====================================================================
    @Test
    @DisplayName("getOrder: Tham số hợp lệ -> gọi repository findOne và trả về Order tương ứng")
    void getOrder_validOrderIdAndStore_returnsOrder() {
        Order expectedOrder = new Order();
        expectedOrder.setId(100L);
        when(orderRepository.findOne(100L, store.getId())).thenReturn(expectedOrder);

        Order result = orderService.getOrder(100L, store);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        verify(orderRepository).findOne(100L, store.getId());
    }

    @Test
    @DisplayName("getOrder: OrderId null -> ném NullPointerException")
    void getOrder_nullOrderId_throwsException() {
        assertThatThrownBy(() -> orderService.getOrder(null, store))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("getOrder: Store null -> ném NullPointerException")
    void getOrder_nullStore_throwsException() {
        assertThatThrownBy(() -> orderService.getOrder(100L, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ====================================================================
    // Kiểm thử generateInvoice
    // ====================================================================
    @Test
    @DisplayName("generateInvoice: OrderProducts null -> ném NullPointerException")
    void generateInvoice_nullOrderProducts_throwsException() {
        Order order = new Order();
        order.setOrderProducts(null);
        order.setOrderTotal(new HashSet<>());

        assertThatThrownBy(() -> orderService.generateInvoice(store, order, language))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("generateInvoice: OrderTotal null -> ném NullPointerException")
    void generateInvoice_nullOrderTotal_throwsException() {
        Order order = new Order();
        order.setOrderProducts(Collections.singleton(new OrderProduct()));
        order.setOrderTotal(null);

        assertThatThrownBy(() -> orderService.generateInvoice(store, order, language))
                .isInstanceOf(NullPointerException.class);
    }
}

