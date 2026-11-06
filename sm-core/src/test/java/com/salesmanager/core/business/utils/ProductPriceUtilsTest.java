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
 * Class nay khong co dependency duoc inject nen khong can Spring hay Mockito:
 * chi can "new ProductPriceUtils()" va dung entity bang setter.
 *
 * Dat file tai:
 * sm-core/src/test/java/com/salesmanager/core/business/utils/ProductPriceUtilsTest.java
 */
public class ProductPriceUtilsTest {

    private ProductPriceUtils utils;

    @BeforeEach
    void setUp() {
        utils = new ProductPriceUtils();
    }

    // ------------------------------------------------------------------
    // Helper dung du lieu test
    // ------------------------------------------------------------------

    private static Date daysFromNow(int days) {
        return new Date(System.currentTimeMillis() + days * 86_400_000L);
    }

    private static ProductPrice defaultPrice(String amount) {
        ProductPrice p = new ProductPrice();
        p.setDefaultPrice(true);
        p.setProductPriceAmount(new BigDecimal(amount));
        return p;
    }

    private static ProductPrice specialPrice(String amount, String special, Date start, Date end) {
        ProductPrice p = defaultPrice(amount);
        p.setProductPriceSpecialAmount(new BigDecimal(special));
        p.setProductPriceSpecialStartDate(start);
        p.setProductPriceSpecialEndDate(end);
        return p;
    }

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

    private Currency usd() {
        Currency c = new Currency();
        c.setCurrency(java.util.Currency.getInstance("USD"));
        return c;
    }

    // ==================================================================
    // 1. getAmount(String)
    // ==================================================================

    @Test
    @DisplayName("UT-PRC-01: chuoi gia co 2 chu so thap phan -> BigDecimal dung gia tri")
    void decimalString_returnsBigDecimal() throws Exception {
        assertThat(utils.getAmount("19.99")).isEqualByComparingTo("19.99");
    }

    @Test
    @DisplayName("UT-PRC-02: chuoi co dau phan cach hang nghin")
    void thousandSeparator_returnsBigDecimal() throws Exception {
        assertThat(utils.getAmount("1,234.50")).isEqualByComparingTo("1234.50");
    }

    @Test
    @DisplayName("UT-PRC-03: so nguyen duong (khong dau cham/phay)")
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
    @DisplayName("UT-PRC-05: so nguyen am bi tu choi")
    void negativeInteger_throwsNotPositive() {
        assertThatThrownBy(() -> utils.getAmount("-5"))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Not a positive integer");
    }

    @Test
    @DisplayName("UT-PRC-06: bien tren - 21474836.47 (= Integer.MAX_VALUE khi bo dau cham) van parse duoc")
    void maxIntBoundary_isAccepted() throws Exception {
        assertThat(utils.getAmount("21474836.47")).isEqualByComparingTo("21474836.47");
    }

    @Test
    @DisplayName("UT-PRC-07: vuot bien - 21474836.48 bi tu choi")
    void aboveMaxIntBoundary_isRejected() {
        assertThatThrownBy(() -> utils.getAmount("21474836.48"))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Cannot parse");
    }

    // ==================================================================
    // 2. hasDiscount(ProductPrice)
    // ==================================================================

    @Test
    @DisplayName("UT-PRC-08: hom nay nam giua ngay bat dau va ket thuc -> true")
    void withinPeriod_returnsTrue() {
        ProductPrice p = specialPrice("100.00", "80.00", daysFromNow(-1), daysFromNow(1));
        assertThat(utils.hasDiscount(p)).isTrue();
    }

    @Test
    @DisplayName("UT-PRC-09: chua toi ngay bat dau -> false")
    void notStarted_returnsFalse() {
        ProductPrice p = specialPrice("100.00", "80.00", daysFromNow(1), daysFromNow(5));
        assertThat(utils.hasDiscount(p)).isFalse();
    }

    @Test
    @DisplayName("UT-PRC-10: da het han -> false")
    void expired_returnsFalse() {
        ProductPrice p = specialPrice("100.00", "80.00", daysFromNow(-5), daysFromNow(-1));
        assertThat(utils.hasDiscount(p)).isFalse();
    }

    @Test
    @DisplayName("UT-PRC-11: thieu mot trong hai ngay -> false")
    void missingOneDate_returnsFalse() {
        assertThat(utils.hasDiscount(specialPrice("100.00", "80.00", null, daysFromNow(1)))).isFalse();
        assertThat(utils.hasDiscount(specialPrice("100.00", "80.00", daysFromNow(-1), null))).isFalse();
    }

    @Test
    @DisplayName("UT-PRC-12: khong co ngay nao -> false (du co gia dac biet)")
    void noDates_returnsFalse() {
        assertThat(utils.hasDiscount(specialPrice("100.00", "80.00", null, null))).isFalse();
    }

    // ==================================================================
    // 3. getFinalPrice(ProductAvailability)
    // ==================================================================

    @Test
    @DisplayName("UT-PRC-13: khong co gia dac biet -> gia cuoi = gia goc, khong giam")
    void noSpecialPrice_returnsOriginal() throws Exception {
        FinalPrice fp = utils.getFinalPrice(availability(defaultPrice("100.00")));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
        assertThat(fp.getOriginalPrice()).isEqualByComparingTo("100.00");
        assertThat(fp.isDiscounted()).isFalse();
        assertThat(fp.getStringPrice()).isEqualTo("100.00");
    }

    @Test
    @DisplayName("UT-PRC-14: gia dac biet con han -> dung gia dac biet, tinh % giam")
    void activeSpecialPrice_returnsDiscounted() throws Exception {
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
    @DisplayName("UT-PRC-15: gia dac biet da het han -> dung gia goc")
    void expiredSpecialPrice_returnsOriginal() throws Exception {
        ProductPrice p = specialPrice("100.00", "75.00", daysFromNow(-5), daysFromNow(-1));

        FinalPrice fp = utils.getFinalPrice(availability(p));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
        assertThat(fp.isDiscounted()).isFalse();
    }

    @Test
    @DisplayName("UT-PRC-16: gia dac biet chua toi ngay bat dau -> dung gia goc")
    void notStartedSpecialPrice_returnsOriginal() throws Exception {
        ProductPrice p = specialPrice("100.00", "75.00", daysFromNow(1), daysFromNow(5));

        FinalPrice fp = utils.getFinalPrice(availability(p));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
        assertThat(fp.isDiscounted()).isFalse();
    }

    @Test
    @DisplayName("UT-PRC-17: khong co ngay bat dau, ngay ket thuc o tuong lai -> van giam gia")
    void onlyFutureEndDate_returnsDiscounted() throws Exception {
        ProductPrice p = specialPrice("100.00", "75.00", null, daysFromNow(3));

        FinalPrice fp = utils.getFinalPrice(availability(p));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("75.00");
        assertThat(fp.isDiscounted()).isTrue();
    }

    @Test
    @DisplayName("UT-PRC-18: co gia dac biet > 0 nhung khong co ngay nao -> giam gia vo thoi han")
    void specialAmountWithoutDates_returnsDiscounted() throws Exception {
        ProductPrice p = specialPrice("100.00", "75.00", null, null);

        FinalPrice fp = utils.getFinalPrice(availability(p));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("75.00");
        assertThat(fp.isDiscounted()).isTrue();
    }

    @Test
    @DisplayName("UT-PRC-19: gia dac biet = 0 va khong co ngay -> khong giam")
    void zeroSpecialAmountWithoutDates_returnsOriginal() throws Exception {
        ProductPrice p = specialPrice("100.00", "0", null, null);

        FinalPrice fp = utils.getFinalPrice(availability(p));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
        assertThat(fp.isDiscounted()).isFalse();
    }

    @Test
    @DisplayName("UT-PRC-20: gia goc bang 0 -> gia cuoi bang 0, khong nem exception")
    void zeroPrice_returnsZero() throws Exception {
        FinalPrice fp = utils.getFinalPrice(availability(defaultPrice("0")));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("0");
        assertThat(fp.getStringPrice()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("UT-PRC-21: co gia mac dinh va gia phu -> gia phu nam trong additionalPrices")
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
    @DisplayName("UT-PRC-22: khong co gia mac dinh, chi co gia phu -> lay gia phu dau tien")
    void onlyNonDefaultPrice_usesFirstOther() throws Exception {
        ProductPrice other = new ProductPrice();
        other.setDefaultPrice(false);
        other.setProductPriceAmount(new BigDecimal("90.00"));

        FinalPrice fp = utils.getFinalPrice(availability(other));

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("UT-PRC-23: availability khong co gia nao -> ServiceException")
    void noPrices_throwsServiceException() {
        assertThatThrownBy(() -> utils.getFinalPrice(availability()))
            .isInstanceOf(ServiceException.class);
    }

    // ==================================================================
    // 4. getFinalPrice(Product) va getFinalPrice(Product, attributes)
    // ==================================================================

    @Test
    @DisplayName("UT-PRC-24: thuoc tinh mac dinh co gia duoc cong vao gia cuoi va gia goc")
    void defaultAttributePrice_isAdded() throws Exception {
        Product product = product(availability(defaultPrice("100.00")));
        product.setAttributes(new HashSet<>(Arrays.asList(attribute("10.00", true))));

        FinalPrice fp = utils.getFinalPrice(product);

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("110.00");
        assertThat(fp.getOriginalPrice()).isEqualByComparingTo("110.00");
        assertThat(fp.getStringPrice()).isEqualTo("110.00");
    }

    @Test
    @DisplayName("UT-PRC-25: thuoc tinh khong phai mac dinh hoac khong co gia thi khong cong")
    void nonDefaultOrNullPriceAttribute_isIgnored() throws Exception {
        Product product = product(availability(defaultPrice("100.00")));
        product.setAttributes(new HashSet<>(Arrays.asList(
            attribute("10.00", false),
            attribute(null, true))));

        FinalPrice fp = utils.getFinalPrice(product);

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("UT-PRC-26: san pham khong co availability -> ServiceException")
    void noAvailabilities_throwsServiceException() {
        assertThatThrownBy(() -> utils.getFinalPrice(new Product()))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("UT-PRC-27: availability co vung khac \"*\" bi bo qua -> ServiceException")
    void nonDefaultRegion_throwsServiceException() {
        ProductAvailability a = availability(defaultPrice("100.00"));
        a.setRegion("EU");

        assertThatThrownBy(() -> utils.getFinalPrice(product(a)))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("UT-PRC-28: getFinalPrice(product, attributes) cong gia thuoc tinh vao gia cuoi, gia goc va gia giam")
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
    @DisplayName("UT-PRC-29: getFinalPrice(product, null) -> giu nguyen gia")
    void nullAttributes_priceUnchanged() throws Exception {
        FinalPrice fp = utils.getFinalPrice(product(availability(defaultPrice("100.00"))), null);

        assertThat(fp.getFinalPrice()).isEqualByComparingTo("100.00");
    }

    // ==================================================================
    // 5. getPrice, getOrderProductTotalPrice
    // ==================================================================

    @Test
    @DisplayName("UT-PRC-30: getPrice tra ve gia mac dinh cua san pham")
    void getPrice_returnsDefaultPrice() {
        Product product = product(availability(defaultPrice("50.00")));

        assertThat(utils.getPrice(null, product, Locale.US)).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("UT-PRC-31: getPrice khi khong co availability -> 0")
    void getPrice_noAvailability_returnsZero() {
        assertThat(utils.getPrice(null, new Product(), Locale.US)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("UT-PRC-32: tong tien dong hang = don gia x so luong")
    void orderProductTotal_isChargeTimesQuantity() {
        OrderProduct op = new OrderProduct();
        op.setOneTimeCharge(new BigDecimal("10.50"));
        op.setProductQuantity(3);

        assertThat(utils.getOrderProductTotalPrice(null, op)).isEqualByComparingTo("31.50");
    }

    @Test
    @DisplayName("UT-PRC-33: so luong bang 0 -> tong bang 0")
    void orderProductTotal_zeroQuantity_isZero() {
        OrderProduct op = new OrderProduct();
        op.setOneTimeCharge(new BigDecimal("10.50"));
        op.setProductQuantity(0);

        assertThat(utils.getOrderProductTotalPrice(null, op)).isEqualByComparingTo("0");
    }

    // ==================================================================
    // 6. Dinh dang tien
    // ==================================================================

    @Test
    @DisplayName("UT-PRC-34: getStringAmount(null) -> chuoi rong")
    void getStringAmount_null_returnsEmpty() {
        assertThat(utils.getStringAmount(null)).isEmpty();
    }

    @Test
    @DisplayName("UT-PRC-35: getStringAmount luon co dung 2 chu so thap phan va lam tron")
    void getStringAmount_twoDecimals() {
        assertThat(utils.getStringAmount(new BigDecimal("1234.5"))).isEqualTo("1,234.50");
        assertThat(utils.getStringAmount(new BigDecimal("19.999"))).isEqualTo("20.00");
        assertThat(utils.getStringAmount(BigDecimal.ZERO)).isEqualTo("0.00");
    }

    @Test
    @DisplayName("UT-PRC-36: dinh dang theo locale en_US kem ky hieu tien te")
    void formatWithLocaleAndCurrency_usd() throws Exception {
        assertThat(utils.getFormatedAmountWithCurrency(Locale.US, usd(), new BigDecimal("1234.5")))
            .isEqualTo("$1,234.50");
    }

    @Test
    @DisplayName("UT-PRC-37: so tien null -> chuoi rong (ca hai overload)")
    void formatWithCurrency_nullAmount_returnsEmpty() throws Exception {
        assertThat(utils.getFormatedAmountWithCurrency(Locale.US, usd(), null)).isEmpty();
        assertThat(utils.getFormatedAmountWithCurrency(usd(), null)).isEmpty();
    }

    @Test
    @DisplayName("UT-PRC-38: overload (Currency, amount) -> 2 chu so thap phan, khong co ky hieu tien te")
    void formatWithCurrencyOnly_hasNoSymbol() throws Exception {
        assertThat(utils.getFormatedAmountWithCurrency(usd(), new BigDecimal("1234.5")))
            .isEqualTo("1,234.50");
    }

    @Test
    @DisplayName("UT-PRC-39: Currency chua gan java.util.Currency -> NullPointerException")
    void formatWithCurrencyOnly_missingJavaCurrency_throwsNpe() {
        assertThatThrownBy(() -> utils.getFormatedAmountWithCurrency(new Currency(), new BigDecimal("1")))
            .isInstanceOf(NullPointerException.class);
    }
}
