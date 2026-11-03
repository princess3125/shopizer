package com.salesmanager.core.business.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.model.catalog.product.Product;
import com.salesmanager.core.model.catalog.product.attribute.ProductAttribute;
import com.salesmanager.core.model.catalog.product.availability.ProductAvailability;
import com.salesmanager.core.model.catalog.product.price.FinalPrice;
import com.salesmanager.core.model.catalog.product.price.ProductPrice;
import com.salesmanager.core.model.order.orderproduct.OrderProduct;
import com.salesmanager.core.model.reference.currency.Currency;

/**
 * Unit test cho ProductPriceUtils (Shopizer 3.2.5).
 * Class này không có dependency được inject nên không cần Spring hay Mockito:
 * chỉ cần "new ProductPriceUtils()" và dựng entity bằng setter.
 *
 * Đặt file tại:
 * sm-core/src/test/java/com/salesmanager/core/business/utils/ProductPriceUtilsTest.java
 */
class ProductPriceUtilsTest {

    private ProductPriceUtils utils;

    @BeforeEach
    void setUp() {
        utils = new ProductPriceUtils();
    }

    // ------------------------------------------------------------------
    // Helper dựng dữ liệu test
    // ------------------------------------------------------------------

    /** Ngày tương đối so với hiện tại (code gốc dùng new Date() nên không hard-code ngày). */
    private static Date daysFromNow(int days) {
        return new Date(System.currentTimeMillis() + days * 86_400_000L);
    }

    /** Giá mặc định, không có giá đặc biệt. */
    private static ProductPrice defaultPrice(String amount) {
        ProductPrice p = new ProductPrice();
        p.setDefaultPrice(true);
        p.setProductPriceAmount(new BigDecimal(amount));
        return p;
    }

    /** Giá mặc định có giá đặc biệt và khoảng ngày (start/end có thể null). */
    private static ProductPrice specialPrice(String amount, String special, Date start, Date end) {
        ProductPrice p = defaultPrice(amount);
        p.setProductPriceSpecialAmount(new BigDecimal(special));
        p.setProductPriceSpecialStartDate(start);
        p.setProductPriceSpecialEndDate(end);
        return p;
    }

    /** Availability vùng "*" (vùng duy nhất mà code chấp nhận). */
    private static ProductAvailability availability(ProductPrice... prices) {
        ProductAvailability a = new ProductAvailability();
        a.setRegion("*");
        a.setPrices(new LinkedHashSet<>(Arrays.asList(prices)));
        return a;
    }

    private static Product product(ProductAvailability... availabilities) {
        Product p = new Product();
        p.setAvailabilities(new HashSet<>(Arrays.asList(availabilities)));
        return p;
    }

    private static ProductAttribute attribute(String price, boolean isDefault) {
        ProductAttribute a = new ProductAttribute();
        a.setProductAttributePrice(price == null ? null : new BigDecimal(price));
        a.setAttributeDefault(isDefault);
        return a;
    }

    // ------------------------------------------------------------------
    // getAmount(String)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getAmount(String)")
    class GetAmount {

        @Test
        @DisplayName("UT-PRC-01: chuỗi giá có 2 chữ số thập phân -> BigDecimal đúng giá trị")
        void decimalString_returnsBigDecimal() throws Exception {
            assertThat(utils.getAmount("19.99")).isEqualByComparingTo("19.99");
        }

        @Test
        @DisplayName("UT-PRC-02: chuỗi có dấu phân cách hàng nghìn")
        void thousandSeparator_returnsBigDecimal() throws Exception {
            assertThat(utils.getAmount("1,234.50")).isEqualByComparingTo("1234.50");
        }

        @Test
        @DisplayName("UT-PRC-03: số nguyên dương (không dấu chấm/phẩy)")
        void positiveInteger_returnsBigDecimal() throws Exception {
            assertThat(utils.getAmount("100")).isEqualByComparingTo("100");
        }

        @ParameterizedTest(name = "UT-PRC-04: \"{0}\" -> Cannot parse")
        @ValueSource(strings = { "abc", "", "12a", "1.234", "1.2.3" })
        void unparsableString_throwsCannotParse(String input) {
            assertThatThrownBy(() -> utils.getAmount(input))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Cannot parse");
        }

        @Test
        @DisplayName("UT-PRC-05: số nguyên âm bị từ chối")
        void negativeInteger_throwsNotPositive() {
            assertThatThrownBy(() -> utils.getAmount("-5"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Not a positive integer");
        }

        @Test
        @DisplayName("UT-PRC-06: biên trên - 21474836.47 (= Integer.MAX_VALUE khi bỏ dấu chấm) vẫn parse được")
        void maxIntBoundary_isAccepted() throws Exception {
            assertThat(utils.getAmount("21474836.47")).isEqualByComparingTo("21474836.47");
        }

        @Test
        @DisplayName("UT-PRC-07: vượt biên - 21474836.48 bị từ chối (hạn chế do Integer.parseInt trong code gốc)")
        void aboveMaxIntBoundary_isRejected() {
            assertThatThrownBy(() -> utils.getAmount("21474836.48"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Cannot parse");
        }
    }

    // ------------------------------------------------------------------
    // hasDiscount(ProductPrice)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("hasDiscount(ProductPrice)")
    class HasDiscount {

        @Test
        @DisplayName("UT-PRC-08: hôm nay nằm giữa ngày bắt đầu và kết thúc -> true")
        void withinPeriod_returnsTrue() {
            ProductPrice p = specialPrice("100.00", "80.00", daysFromNow(-1), daysFromNow(1));
            assertThat(utils.hasDiscount(p)).isTrue();
        }

        @Test
        @DisplayName("UT-PRC-09: chưa tới ngày bắt đầu -> false")
        void notStarted_returnsFalse() {
            ProductPrice p = specialPrice("100.00", "80.00", daysFromNow(1), daysFromNow(5));
            assertThat(utils.hasDiscount(p)).isFalse();
        }

        @Test
        @DisplayName("UT-PRC-10: đã hết hạn -> false")
        void expired_returnsFalse() {
            ProductPrice p = specialPrice("100.00", "80.00", daysFromNow(-5), daysFromNow(-1));
            assertThat(utils.hasDiscount(p)).isFalse();
        }

        @Test
        @DisplayName("UT-PRC-11: thiếu một trong hai ngày -> false")
        void missingOneDate_returnsFalse() {
            assertThat(utils.hasDiscount(specialPrice("100.00", "80.00", null, daysFromNow(1)))).isFalse();
            assertThat(utils.hasDiscount(specialPrice("100.00", "80.00", daysFromNow(-1), null))).isFalse();
        }

        @Test
        @DisplayName("UT-PRC-12: không có ngày nào -> false (dù có giá đặc biệt)")
        void noDates_returnsFalse() {
            assertThat(utils.hasDiscount(specialPrice("100.00", "80.00", null, null))).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // getFinalPrice(ProductAvailability): logic giá giảm nằm ở đây (finalPrice() private)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFinalPrice(ProductAvailability)")
    class FinalPriceByAvailability {

        @Test
        @DisplayName("UT-PRC-13: không có giá đặc biệt -> giá cuối = giá gốc, không giảm")
        void noSpecialPrice_returnsOriginal() throws Exception {
            FinalPrice fp = utils.getFinalPrice(availability(defaultPrice("100.00")));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.getOriginalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.isDiscounted()).isFalse();
            assertThat(fp.getStringPrice()).isEqualTo("100.00");
        }

        @Test
        @DisplayName("UT-PRC-14: giá đặc biệt còn hạn -> dùng giá đặc biệt, tính % giảm")
        void activeSpecialPrice_returnsDiscounted() throws Exception {
            // 75/100 = 0.75 (biểu diễn chính xác trong double) -> giảm đúng 25%
            ProductPrice p = specialPrice("100.00", "75.00", daysFromNow(-1), daysFromNow(1));

            FinalPrice fp = utils.getFinalPrice(availability(p));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("75.00");
            assertThat(fp.getOriginalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.isDiscounted()).isTrue();
            assertThat(fp.getDiscountPercent()).isEqualTo(25);
            assertThat(fp.getDiscountedPrice()).isEqualByComparingTo("75.00");
            assertThat(fp.getStringDiscountedPrice()).isEqualTo("75.00");
            assertThat(fp.getDiscountEndDate()).isEqualTo(p.getProductPriceSpecialEndDate());
        }

        @Test
        @DisplayName("UT-PRC-15: giá đặc biệt đã hết hạn -> dùng giá gốc")
        void expiredSpecialPrice_returnsOriginal() throws Exception {
            ProductPrice p = specialPrice("100.00", "75.00", daysFromNow(-5), daysFromNow(-1));

            FinalPrice fp = utils.getFinalPrice(availability(p));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.isDiscounted()).isFalse();
        }

        @Test
        @DisplayName("UT-PRC-16: giá đặc biệt chưa tới ngày bắt đầu -> dùng giá gốc")
        void notStartedSpecialPrice_returnsOriginal() throws Exception {
            ProductPrice p = specialPrice("100.00", "75.00", daysFromNow(1), daysFromNow(5));

            FinalPrice fp = utils.getFinalPrice(availability(p));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.isDiscounted()).isFalse();
        }

        @Test
        @DisplayName("UT-PRC-17: không có ngày bắt đầu, ngày kết thúc ở tương lai -> vẫn giảm giá")
        void onlyFutureEndDate_returnsDiscounted() throws Exception {
            ProductPrice p = specialPrice("100.00", "75.00", null, daysFromNow(3));

            FinalPrice fp = utils.getFinalPrice(availability(p));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("75.00");
            assertThat(fp.isDiscounted()).isTrue();
        }

        @Test
        @DisplayName("UT-PRC-18: có giá đặc biệt > 0 nhưng không có ngày nào -> giảm giá vô thời hạn")
        void specialAmountWithoutDates_returnsDiscounted() throws Exception {
            ProductPrice p = specialPrice("100.00", "75.00", null, null);

            FinalPrice fp = utils.getFinalPrice(availability(p));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("75.00");
            assertThat(fp.isDiscounted()).isTrue();
        }

        @Test
        @DisplayName("UT-PRC-19: giá đặc biệt = 0 và không có ngày -> không giảm")
        void zeroSpecialAmountWithoutDates_returnsOriginal() throws Exception {
            ProductPrice p = specialPrice("100.00", "0", null, null);

            FinalPrice fp = utils.getFinalPrice(availability(p));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.isDiscounted()).isFalse();
        }

        @Test
        @DisplayName("UT-PRC-20: giá gốc bằng 0 -> giá cuối bằng 0, không ném exception")
        void zeroPrice_returnsZero() throws Exception {
            FinalPrice fp = utils.getFinalPrice(availability(defaultPrice("0")));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("0");
            assertThat(fp.getStringPrice()).isEqualTo("0.00");
        }

        @Test
        @DisplayName("UT-PRC-21: có giá mặc định và giá phụ -> giá phụ nằm trong additionalPrices")
        void defaultAndOtherPrice_otherGoesToAdditional() throws Exception {
            ProductPrice other = new ProductPrice();
            other.setDefaultPrice(false);
            other.setProductPriceAmount(new BigDecimal("90.00"));

            FinalPrice fp = utils.getFinalPrice(availability(defaultPrice("100.00"), other));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
            assertThat(fp.getAdditionalPrices()).hasSize(1);
            assertThat(fp.getAdditionalPrices().get(0).getFinalPrice()).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("UT-PRC-22: không có giá mặc định, chỉ có giá phụ -> lấy giá phụ đầu tiên")
        void onlyNonDefaultPrice_usesFirstOther() throws Exception {
            ProductPrice other = new ProductPrice();
            other.setDefaultPrice(false);
            other.setProductPriceAmount(new BigDecimal("90.00"));

            FinalPrice fp = utils.getFinalPrice(availability(other));

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("UT-PRC-23: availability không có giá nào -> ServiceException")
        void noPrices_throwsServiceException() {
            assertThatThrownBy(() -> utils.getFinalPrice(availability()))
                .isInstanceOf(ServiceException.class);
        }
    }

    // ------------------------------------------------------------------
    // getFinalPrice(Product) và getFinalPrice(Product, attributes)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFinalPrice(Product ...)")
    class FinalPriceByProduct {

        @Test
        @DisplayName("UT-PRC-24: thuộc tính mặc định có giá được cộng vào giá cuối và giá gốc")
        void defaultAttributePrice_isAdded() throws Exception {
            Product product = product(availability(defaultPrice("100.00")));
            product.setAttributes(new HashSet<>(Arrays.asList(attribute("10.00", true))));

            FinalPrice fp = utils.getFinalPrice(product);

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("110.00");
            assertThat(fp.getOriginalPrice()).isEqualByComparingTo("110.00");
            assertThat(fp.getStringPrice()).isEqualTo("110.00");
        }

        @Test
        @DisplayName("UT-PRC-25: thuộc tính không phải mặc định hoặc không có giá thì không cộng")
        void nonDefaultOrNullPriceAttribute_isIgnored() throws Exception {
            Product product = product(availability(defaultPrice("100.00")));
            product.setAttributes(new HashSet<>(Arrays.asList(
                attribute("10.00", false),   // không mặc định
                attribute(null, true))));    // mặc định nhưng không có giá

            FinalPrice fp = utils.getFinalPrice(product);

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("UT-PRC-26: sản phẩm không có availability -> ServiceException")
        void noAvailabilities_throwsServiceException() {
            assertThatThrownBy(() -> utils.getFinalPrice(new Product()))
                .isInstanceOf(ServiceException.class);
        }

        @Test
        @DisplayName("UT-PRC-27: availability có vùng khác \"*\" bị bỏ qua -> ServiceException")
        void nonDefaultRegion_throwsServiceException() {
            ProductAvailability a = availability(defaultPrice("100.00"));
            a.setRegion("EU");

            assertThatThrownBy(() -> utils.getFinalPrice(product(a)))
                .isInstanceOf(ServiceException.class);
        }

        @Test
        @DisplayName("UT-PRC-28: getFinalPrice(product, attributes) cộng giá thuộc tính vào giá cuối, giá gốc và giá giảm")
        void attributesList_addedToFinalOriginalAndDiscounted() throws Exception {
            ProductPrice p = specialPrice("100.00", "75.00", daysFromNow(-1), daysFromNow(1));
            Product product = product(availability(p));
            List<ProductAttribute> attrs = Arrays.asList(attribute("5.00", false), attribute("5.00", false));

            FinalPrice fp = utils.getFinalPrice(product, attrs);

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("85.00");
            assertThat(fp.getOriginalPrice()).isEqualByComparingTo("110.00");
            assertThat(fp.getDiscountedPrice()).isEqualByComparingTo("85.00");
        }

        @Test
        @DisplayName("UT-PRC-29: getFinalPrice(product, null) -> giữ nguyên giá")
        void nullAttributes_priceUnchanged() throws Exception {
            FinalPrice fp = utils.getFinalPrice(product(availability(defaultPrice("100.00"))), null);

            assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
        }
    }

    // ------------------------------------------------------------------
    // getPrice, getOrderProductTotalPrice
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getPrice / getOrderProductTotalPrice")
    class OtherPriceMethods {

        @Test
        @DisplayName("UT-PRC-30: getPrice trả về giá mặc định của sản phẩm")
        void getPrice_returnsDefaultPrice() {
            Product product = product(availability(defaultPrice("50.00")));

            assertThat(utils.getPrice(null, product, Locale.US)).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("UT-PRC-31: getPrice khi không có availability -> 0")
        void getPrice_noAvailability_returnsZero() {
            assertThat(utils.getPrice(null, new Product(), Locale.US)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("UT-PRC-32: tổng tiền dòng hàng = đơn giá x số lượng")
        void orderProductTotal_isChargeTimesQuantity() {
            OrderProduct op = new OrderProduct();
            op.setOneTimeCharge(new BigDecimal("10.50"));
            op.setProductQuantity(3);

            assertThat(utils.getOrderProductTotalPrice(null, op)).isEqualByComparingTo("31.50");
        }

        @Test
        @DisplayName("UT-PRC-33: số lượng bằng 0 -> tổng bằng 0")
        void orderProductTotal_zeroQuantity_isZero() {
            OrderProduct op = new OrderProduct();
            op.setOneTimeCharge(new BigDecimal("10.50"));
            op.setProductQuantity(0);

            assertThat(utils.getOrderProductTotalPrice(null, op)).isEqualByComparingTo("0");
        }
    }

    // ------------------------------------------------------------------
    // Định dạng tiền
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Định dạng tiền")
    class Formatting {

        private Currency usd() {
            Currency c = new Currency();
            c.setCurrency(java.util.Currency.getInstance("USD"));
            return c;
        }

        @Test
        @DisplayName("UT-PRC-34: getStringAmount(null) -> chuỗi rỗng")
        void getStringAmount_null_returnsEmpty() {
            assertThat(utils.getStringAmount(null)).isEmpty();
        }

        @Test
        @DisplayName("UT-PRC-35: getStringAmount luôn có đúng 2 chữ số thập phân và làm tròn")
        void getStringAmount_twoDecimals() {
            assertThat(utils.getStringAmount(new BigDecimal("1234.5"))).isEqualTo("1,234.50");
            assertThat(utils.getStringAmount(new BigDecimal("19.999"))).isEqualTo("20.00");
            assertThat(utils.getStringAmount(BigDecimal.ZERO)).isEqualTo("0.00");
        }

        @Test
        @DisplayName("UT-PRC-36: định dạng theo locale en_US kèm ký hiệu tiền tệ")
        void formatWithLocaleAndCurrency_usd() throws Exception {
            assertThat(utils.getFormatedAmountWithCurrency(Locale.US, usd(), new BigDecimal("1234.5")))
                .isEqualTo("$1,234.50");
        }

        @Test
        @DisplayName("UT-PRC-37: số tiền null -> chuỗi rỗng (cả hai overload)")
        void formatWithCurrency_nullAmount_returnsEmpty() throws Exception {
            assertThat(utils.getFormatedAmountWithCurrency(Locale.US, usd(), null)).isEmpty();
            assertThat(utils.getFormatedAmountWithCurrency(usd(), null)).isEmpty();
        }

        @Test
        @DisplayName("UT-PRC-38: overload (Currency, amount) -> 2 chữ số thập phân, không có ký hiệu tiền tệ")
        void formatWithCurrencyOnly_hasNoSymbol() throws Exception {
            assertThat(utils.getFormatedAmountWithCurrency(usd(), new BigDecimal("1234.5")))
                .isEqualTo("1,234.50");
        }

        @Test
        @DisplayName("UT-PRC-39: Currency chưa gán java.util.Currency -> NullPointerException")
        void formatWithCurrencyOnly_missingJavaCurrency_throwsNpe() {
            assertThatThrownBy(() -> utils.getFormatedAmountWithCurrency(new Currency(), new BigDecimal("1")))
                .isInstanceOf(NullPointerException.class);
        }
    }
}