-- ==============================================================================
-- SHOPIZER TEST DATA VERIFICATION SCRIPT
-- Purpose: Kiểm tra tính toàn vẹn và hợp lệ của bộ dữ liệu kiểm thử vừa thêm
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. BÁO CÁO TỔNG SỐ LƯỢNG BẢN GHI THEO TỪNG BẢNG
-- Kì vọng:
-- CATEGORY: 5 | PRODUCT: 20 | PRODUCT_AVAILABILITY: 20 | CUSTOMER: 3 | ORDERS: 5
-- ------------------------------------------------------------------------------
SELECT 'CATEGORY' AS EntityName, COUNT(*) AS TotalCount, 5 AS ExpectedCount FROM CATEGORY WHERE CATEGORY_ID BETWEEN 90001 AND 90005
UNION ALL
SELECT 'CATEGORY_DESCRIPTION', COUNT(*), 5 FROM CATEGORY_DESCRIPTION WHERE DESCRIPTION_ID BETWEEN 90001 AND 90005
UNION ALL
SELECT 'PRODUCT', COUNT(*), 20 FROM PRODUCT WHERE PRODUCT_ID BETWEEN 90101 AND 90120
UNION ALL
SELECT 'PRODUCT_DESCRIPTION', COUNT(*), 20 FROM PRODUCT_DESCRIPTION WHERE DESCRIPTION_ID BETWEEN 90201 AND 90220
UNION ALL
SELECT 'PRODUCT_CATEGORY', COUNT(*), 20 FROM PRODUCT_CATEGORY WHERE PRODUCT_ID BETWEEN 90101 AND 90120
UNION ALL
SELECT 'PRODUCT_AVAILABILITY (Inventory)', COUNT(*), 20 FROM PRODUCT_AVAILABILITY WHERE PRODUCT_AVAIL_ID BETWEEN 90301 AND 90320
UNION ALL
SELECT 'PRODUCT_PRICE', COUNT(*), 20 FROM PRODUCT_PRICE WHERE PRODUCT_PRICE_ID BETWEEN 90401 AND 90420
UNION ALL
SELECT 'CUSTOMER', COUNT(*), 3 FROM CUSTOMER WHERE CUSTOMER_ID BETWEEN 90001 AND 90003
UNION ALL
SELECT 'CUSTOMER_GROUP', COUNT(*), 3 FROM CUSTOMER_GROUP WHERE CUSTOMER_ID BETWEEN 90001 AND 90003
UNION ALL
SELECT 'ORDERS', COUNT(*), 5 FROM ORDERS WHERE ORDER_ID BETWEEN 90001 AND 90005
UNION ALL
SELECT 'ORDER_PRODUCT', COUNT(*), 6 FROM ORDER_PRODUCT WHERE ORDER_PRODUCT_ID BETWEEN 90101 AND 90106
UNION ALL
SELECT 'ORDER_TOTAL', COUNT(*), 20 FROM ORDER_TOTAL WHERE ORDER_ACCOUNT_ID BETWEEN 90301 AND 90320
UNION ALL
SELECT 'ORDER_STATUS_HISTORY', COUNT(*), 10 FROM ORDER_STATUS_HISTORY WHERE ORDER_STATUS_HISTORY_ID BETWEEN 90401 AND 90410;


-- ------------------------------------------------------------------------------
-- 2. KIỂM TRA TÍNH TOÀN VẸN CỦA DANH MỤC VÀ CÂY PHÂN CẤP (Category Hierarchy)
-- Kì vọng:
-- 4 danh mục gốc (PARENT_ID IS NULL, DEPTH=0)
-- 1 subcategory 90003 có PARENT_ID = 90001, DEPTH=1, LINEAGE = '/90001/90003/'
-- ------------------------------------------------------------------------------
SELECT c.CATEGORY_ID, c.CODE, cd.NAME, c.DEPTH, c.LINEAGE, c.PARENT_ID, 
       pcd.NAME AS ParentName, c.VISIBLE, c.CATEGORY_STATUS
FROM CATEGORY c
JOIN CATEGORY_DESCRIPTION cd ON c.CATEGORY_ID = cd.CATEGORY_ID
LEFT JOIN CATEGORY p ON c.PARENT_ID = p.CATEGORY_ID
LEFT JOIN CATEGORY_DESCRIPTION pcd ON p.CATEGORY_ID = pcd.CATEGORY_ID
WHERE c.CATEGORY_ID BETWEEN 90001 AND 90005
ORDER BY c.CATEGORY_ID;


-- ------------------------------------------------------------------------------
-- 3. KIỂM TRA TOÀN DIỆN SẢN PHẨM, TỒN KHO VÀ GIÁ BÁN (Product - Inventory - Price)
-- Kì vọng:
-- 20 sản phẩm đều có đầy đủ Category, Tồn kho (Quantity 15..120), Giá niêm yết
-- 4 sản phẩm có giá khuyến mãi (Special Price: 90102, 90106, 90110, 90117)
-- ------------------------------------------------------------------------------
SELECT p.PRODUCT_ID, p.SKU, cd.NAME AS CategoryName, pd.NAME AS ProductName,
       pa.QUANTITY AS StockQuantity, pa.STATUS AS AvailableForOrder,
       pp.PRODUCT_PRICE_AMOUNT AS RegularPrice,
       pp.PRODUCT_PRICE_SPECIAL_AMOUNT AS DiscountPrice,
       CASE WHEN pp.PRODUCT_PRICE_SPECIAL_AMOUNT IS NOT NULL THEN 'SALE' ELSE 'NORMAL' END AS PriceType
FROM PRODUCT p
JOIN PRODUCT_DESCRIPTION pd ON p.PRODUCT_ID = pd.PRODUCT_ID
JOIN PRODUCT_CATEGORY pc ON p.PRODUCT_ID = pc.PRODUCT_ID
JOIN CATEGORY_DESCRIPTION cd ON pc.CATEGORY_ID = cd.CATEGORY_ID
JOIN PRODUCT_AVAILABILITY pa ON p.PRODUCT_ID = pa.PRODUCT_ID
JOIN PRODUCT_PRICE pp ON pa.PRODUCT_AVAIL_ID = pp.PRODUCT_AVAIL_ID
WHERE p.PRODUCT_ID BETWEEN 90101 AND 90120
ORDER BY p.PRODUCT_ID;


-- ------------------------------------------------------------------------------
-- 4. KIỂM TRA THÔNG TIN KHÁCH HÀNG VÀ ĐỊA CHỈ THANH TOÁN (Customer Integrity)
-- ------------------------------------------------------------------------------
SELECT c.CUSTOMER_ID, c.CUSTOMER_NICK, c.CUSTOMER_EMAIL_ADDRESS,
       c.BILLING_FIRST_NAME, c.BILLING_LAST_NAME, c.BILLING_CITY, c.BILLING_STATE,
       co.COUNTRY_ISOCODE, g.GROUP_NAME
FROM CUSTOMER c
JOIN COUNTRY co ON c.BILLING_COUNTRY_ID = co.COUNTRY_ID
JOIN CUSTOMER_GROUP cg ON c.CUSTOMER_ID = cg.CUSTOMER_ID
JOIN SM_GROUP g ON cg.GROUP_ID = g.GROUP_ID
WHERE c.CUSTOMER_ID BETWEEN 90001 AND 90003
ORDER BY c.CUSTOMER_ID;


-- ------------------------------------------------------------------------------
-- 5. KIỂM TRA LOGIC TÍNH TỔNG TIỀN VÀ TRẠNG THÁI ĐƠN HÀNG (Order & Totals Validation)
-- Kì vọng:
-- Tổng ORDER_TOTAL = Subtotal + Shipping + Tax
-- Trạng thái đơn đa dạng: ORDERED, PROCESSED, DELIVERED, CANCELED
-- ------------------------------------------------------------------------------
SELECT o.ORDER_ID, o.CUSTOMER_EMAIL_ADDRESS, o.ORDER_STATUS, o.PAYMENT_TYPE, o.CHANNEL,
       o.ORDER_TOTAL AS StoredTotal,
       SUM(CASE WHEN ot.CODE = 'ot_subtotal' THEN ot.VALUE ELSE 0 END) AS CalcSubtotal,
       SUM(CASE WHEN ot.CODE = 'ot_shipping' THEN ot.VALUE ELSE 0 END) AS CalcShipping,
       SUM(CASE WHEN ot.CODE = 'ot_tax' THEN ot.VALUE ELSE 0 END) AS CalcTax,
       (SUM(CASE WHEN ot.CODE = 'ot_subtotal' THEN ot.VALUE ELSE 0 END) +
        SUM(CASE WHEN ot.CODE = 'ot_shipping' THEN ot.VALUE ELSE 0 END) +
        SUM(CASE WHEN ot.CODE = 'ot_tax' THEN ot.VALUE ELSE 0 END)) AS CalculatedTotal,
       CASE 
           WHEN ABS(o.ORDER_TOTAL - (SUM(CASE WHEN ot.CODE = 'ot_subtotal' THEN ot.VALUE ELSE 0 END) +
                                    SUM(CASE WHEN ot.CODE = 'ot_shipping' THEN ot.VALUE ELSE 0 END) +
                                    SUM(CASE WHEN ot.CODE = 'ot_tax' THEN ot.VALUE ELSE 0 END))) < 0.01 
           THEN 'VALID' 
           ELSE 'INVALID' 
       END AS TotalIntegrityCheck
FROM ORDERS o
JOIN ORDER_TOTAL ot ON o.ORDER_ID = ot.ORDER_ID
WHERE o.ORDER_ID BETWEEN 90001 AND 90005
GROUP BY o.ORDER_ID, o.CUSTOMER_EMAIL_ADDRESS, o.ORDER_STATUS, o.PAYMENT_TYPE, o.CHANNEL, o.ORDER_TOTAL
ORDER BY o.ORDER_ID;


-- ------------------------------------------------------------------------------
-- 6. KIỂM TRA LỊCH SỬ TRẠNG THÁI ĐƠN HÀNG (Order Lifecycle Timeline)
-- ------------------------------------------------------------------------------
SELECT osh.ORDER_ID, osh.STATUS, osh.DATE_ADDED, osh.CUSTOMER_NOTIFIED, osh.COMMENTS
FROM ORDER_STATUS_HISTORY osh
WHERE osh.ORDER_ID BETWEEN 90001 AND 90005
ORDER BY osh.ORDER_ID, osh.ORDER_STATUS_HISTORY_ID;
